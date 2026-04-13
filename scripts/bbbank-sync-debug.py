#!/usr/bin/env python3
"""
BBBank FinTS Sync Debug Script
================================
Replikation des Ablaufs connect → auth (Secure Go / Decoupled-TAN) → Transaktionen abrufen
aus der MyBudgets App – als schlankes, eigenständiges Python-Skript.

Ziel: AI-gestützte Fehleranalyse anhand von Logs bis mindestens 1–2 Buchungen empfangen
und geparst wurden.  Das Skript ist bewusst minimal gehalten und gibt jeden Schritt des
FinTS-Protokolls in lesbarer Form aus.

Voraussetzungen:
  pip install fints

Verwendung (interaktiv – Pflichtfelder werden abgefragt):
  python3 scripts/bbbank-sync-debug.py

Verwendung mit Argumenten:
  python3 scripts/bbbank-sync-debug.py \\
      --iban   "DE89 3704 0044 0532 0130 00" \\
      --user   NUTZERKENNUNG \\
      --pin    GEHEIMPIN \\
      --tan-method 900 \\
      --server https://finanzportal.bbbank.de/banking \\
      --debug

Tipps:
  • --debug          – vollständiges FinTS-Wire-Level-Logging
  • --tan-method 900 – BBBank Secure Go / BestSign (Decoupled)
  • --days-back 90   – mehr History abrufen
  • --max-entries 0  – alle empfangenen Buchungen ausgeben
  • --server URL     – FinTS-Endpoint explizit setzen; URL in hbci4java-Logs
                       (run-live-bbbank-sync-test.sh, HBCI-Log-Level 4) unter 'host=' finden
"""

from __future__ import annotations

import argparse
import getpass
import logging
import sys
import time
from datetime import date, timedelta
from typing import Any, List, Optional

# ── Dependency check ──────────────────────────────────────────────────────────

try:
    from fints.client import FinTS3PinTanClient, NeedTANResponse
    from fints.models import SEPAAccount
except ImportError:
    print(
        "FEHLER: python-fints ist nicht installiert.\n"
        "Bitte installieren mit:  pip install fints\n",
        file=sys.stderr,
    )
    sys.exit(1)

# ── Logging ───────────────────────────────────────────────────────────────────

LOG = logging.getLogger("bbbank")


def _setup_logging(verbose: bool) -> None:
    fmt = "%(asctime)s.%(msecs)03d [%(levelname)-5s] %(name)s: %(message)s"
    datefmt = "%H:%M:%S"
    logging.basicConfig(
        level=logging.DEBUG if verbose else logging.INFO,
        format=fmt,
        datefmt=datefmt,
        stream=sys.stdout,
    )
    # python-fints internal wire logging (very verbose – only with --debug)
    logging.getLogger("fints").setLevel(logging.DEBUG if verbose else logging.INFO)


# ── Helpers ───────────────────────────────────────────────────────────────────

def _blz_from_iban(iban: str) -> str:
    """Leitet die 8-stellige BLZ aus einer deutschen IBAN ab (Pos. 4–11)."""
    norm = iban.replace(" ", "").upper()
    if len(norm) == 22 and norm.startswith("DE"):
        return norm[4:12]
    raise ValueError(f"Ungültige deutsche IBAN: '{iban}' – erwartet DE + 22 Zeichen")


def _iban_last4(iban: str) -> str:
    norm = iban.replace(" ", "").upper()
    return f"***{norm[-4:]}" if len(norm) >= 4 else norm


def _sep(title: str = "") -> None:
    width = 60
    if title:
        pad = max(0, width - len(title) - 4)
        print(f"\n{'─' * 2} {title} {'─' * pad}")
    else:
        print("─" * width)


# ── Decoupled / TAN flow ──────────────────────────────────────────────────────

def _decoupled_label(mech: Any) -> str:
    """Returns '← Decoupled' if the TAN method supports decoupled (Secure Go / BestSign)."""
    try:
        val = getattr(mech, "decoupled_max_poll_number", None)
        if val is not None and int(val) > 0:
            return "  ← Decoupled (Secure Go / BestSign)"
    except (TypeError, ValueError):
        pass
    return ""


def _wait_for_user_confirm(challenge: str, decoupled_wait: int) -> None:
    """Prompts the user to confirm in the banking app for decoupled TAN."""
    _sep("SECURE GO BESTÄTIGUNG")
    if challenge:
        print(f"  Challenge: {challenge}")
    print(f"\n  ➜ Bitte jetzt in der BBBank Secure Go (oder BestSign) App bestätigen.")
    print(f"  Das Skript wartet auf deine Bestätigung (max. {decoupled_wait}s Timeout).\n")
    input("  [Enter drücken, wenn die App-Bestätigung erfolgt ist] ")
    _sep()


def _prompt_classic_tan(challenge: str) -> str:
    """Prompts the user to enter a classic TAN (SMS, ChipTAN …)."""
    _sep("TAN ERFORDERLICH")
    if challenge:
        print(f"  Challenge: {challenge}")
    print()
    return getpass.getpass("  TAN eingeben: ").strip()


def _resolve_tan_response(
    client: FinTS3PinTanClient,
    response: NeedTANResponse,
    decoupled_wait: int,
    poll_interval: int = 3,
) -> Any:
    """
    Handles a NeedTANResponse by either prompting for a TAN (classic) or
    waiting for decoupled confirmation (Secure Go / BestSign) and polling
    the bank until the operation completes.

    Returns the final non-NeedTANResponse result.
    """
    challenge = response.challenge or response.challenge_hhduc or ""

    if response.decoupled:
        # Secure Go / BestSign: wait for the user to confirm in the banking app once
        LOG.info("[TAN] Decoupled-Verfahren (Secure Go) – warte auf App-Bestätigung …")
        _wait_for_user_confirm(challenge, decoupled_wait)
        LOG.info("[TAN] Nutzer hat bestätigt – poll Bank …")

        # Poll the bank (HKTAN process=S) until it acknowledges the confirmation.
        # Code 3956 means "not yet confirmed" (NEED_PT_DECOUPLED_RETRY in hbci4java).
        deadline = time.monotonic() + decoupled_wait
        result = client.send_tan(response, "")
        while isinstance(result, NeedTANResponse) and result.decoupled:
            if time.monotonic() > deadline:
                raise TimeoutError(
                    f"Decoupled-Bestätigung nicht innerhalb von {decoupled_wait}s "
                    "empfangen. Timeout."
                )
            LOG.info(
                "[TAN] Bank wartet noch auf Decoupled-Bestätigung – erneuter Poll in %ds …",
                poll_interval,
            )
            time.sleep(poll_interval)
            result = client.send_tan(result, "")
        LOG.info("[TAN] Decoupled-Bestätigung erfolgreich.")
        return result
    else:
        # Classic TAN: prompt the user
        LOG.info("[TAN] Klassische TAN-Eingabe …")
        tan = _prompt_classic_tan(challenge)
        return client.send_tan(response, tan)


# ── Transaction display ───────────────────────────────────────────────────────

def _print_transaction(idx: int, tx: Any) -> None:
    """Gibt eine geparste Transaktion (MT940 oder CAMT) lesbar aus."""
    print(f"\n  Buchung {idx + 1}:")
    try:
        data: dict = tx.data if isinstance(tx.data, dict) else {}
        amount_obj = data.get("amount", "?")
        if hasattr(amount_obj, "amount") and hasattr(amount_obj, "currency"):
            amount_str = f"{amount_obj.amount} {amount_obj.currency}"
        else:
            amount_str = str(amount_obj)
        # Prefer value date (Wertstellungsdatum) over booking date; guessed_entry_date
        # is a python-fints fallback that copies BookingDate when ValueDate is absent.
        tx_date = data.get("date") or data.get("entry_date") or data.get("guessed_entry_date", "?")
        purpose_raw = data.get("purpose") or data.get("transaction_details", "")
        purpose_str = (
            " ".join(purpose_raw) if isinstance(purpose_raw, list) else str(purpose_raw)
        ).strip()
        name = (
            data.get("applicant_name")
            or data.get("creditor_name")
            or data.get("debtor_name")
            or ""
        )
        print(f"    Datum     : {tx_date}")
        print(f"    Betrag    : {amount_str}")
        if purpose_str:
            print(f"    Verwendung: {purpose_str[:100]}")
        if name:
            print(f"    Name      : {name}")
    except Exception as exc:
        LOG.debug("Formatierungsfehler für Buchung %d: %s", idx + 1, exc)
        print(f"    (raw) {tx!r}")


# ── Main flow ─────────────────────────────────────────────────────────────────

def run(args: argparse.Namespace) -> int:  # noqa: C901
    iban = args.iban.replace(" ", "").upper()
    user = args.user.strip()

    # Derive BLZ from IBAN
    try:
        blz = args.blz.strip() if args.blz else _blz_from_iban(iban)
    except ValueError as exc:
        LOG.error("BLZ: %s", exc)
        return 2

    # PIN (prompt if not supplied via argument)
    pin: str = args.pin or ""
    if not pin:
        try:
            pin = getpass.getpass(f"PIN für Nutzerkennung '{user}': ")
        except (EOFError, KeyboardInterrupt):
            print()
            LOG.error("PIN-Eingabe abgebrochen.")
            return 130
    if not pin:
        LOG.error("PIN darf nicht leer sein.")
        return 2

    start_date = date.today() - timedelta(days=args.days_back)
    end_date = date.today()

    _sep("BBBank FinTS Sync Debug")
    LOG.info("IBAN         : %s  (BLZ %s)", _iban_last4(iban), blz)
    LOG.info("Nutzerkennung: %s", user)
    LOG.info("TAN-Methode  : %s", args.tan_method or "(auto)")
    LOG.info("Zeitraum     : %s → %s", start_date, end_date)
    LOG.info("FinTS-Server : %s", args.server or "(aus BLZ-Liste / Bankdaten)")
    print()

    # ── Phase 1: Verbindungsaufbau ─────────────────────────────────────────────
    LOG.info("[1/5] Verbindungsaufbau …")

    server: Optional[str] = args.server.strip() if args.server else None

    if not server:
        # python-fints requires a server URL.  Abort early with a helpful message
        # rather than letting requests raise a cryptic MissingSchema error later.
        LOG.error(
            "[1/5] FinTS-Server-URL ist erforderlich. "
            "Bitte mit --server angeben. "
            "Tipp: URL aus hbci4java-Logs (run-live-bbbank-sync-test.sh, Log-Level 4) "
            "unter 'host=' ablesen."
        )
        return 2

    try:
        client = FinTS3PinTanClient(
            bank_identifier=blz,
            user_id=user,
            pin=pin,
            server=server,
            product_id="MyBudgets",
        )
    except Exception as exc:
        LOG.error("[1/5] Client-Initialisierung fehlgeschlagen: %s", exc)
        if args.debug:
            LOG.exception("Stack trace:")
        return 1

    LOG.info("[1/5] Client initialisiert – öffne Dialog …")

    try:
        with client:
            # ── Phase 2: TAN-Methode wählen ──────────────────────────────────
            LOG.info("[2/5] Verfügbare TAN-Methoden abfragen …")
            try:
                tan_mechanisms = client.get_tan_mechanisms()
            except Exception as exc:
                LOG.warning("[2/5] TAN-Methoden konnten nicht abgefragt werden: %s", exc)
                tan_mechanisms = {}

            if tan_mechanisms:
                LOG.info("[2/5] TAN-Methoden (%d):", len(tan_mechanisms))
                for code, mech in tan_mechanisms.items():
                    mname = getattr(mech, "name", "?") or "?"
                    LOG.info("       %s : %s%s", code, mname, _decoupled_label(mech))
            else:
                LOG.info("[2/5] Keine TAN-Methoden-Liste in BPD – nutze Bank-Standard")

            if args.tan_method:
                if args.tan_method in (tan_mechanisms or {}):
                    LOG.info("[2/5] Wähle TAN-Methode '%s'", args.tan_method)
                    try:
                        client.set_tan_mechanism(args.tan_method)
                    except Exception as exc:
                        LOG.warning("[2/5] set_tan_mechanism fehlgeschlagen: %s", exc)
                else:
                    LOG.warning(
                        "[2/5] TAN-Methode '%s' nicht in BPD. "
                        "Verfügbare Codes: %s",
                        args.tan_method,
                        list((tan_mechanisms or {}).keys()),
                    )

            # ── Phase 3: Konten / BIC-Lookup ─────────────────────────────────
            LOG.info("[3/5] Konten abrufen (BIC-Lookup) …")
            accounts_result = client.get_sepa_accounts()
            if isinstance(accounts_result, NeedTANResponse):
                LOG.info("[3/5] Bank verlangt TAN für Kontenabfrage")
                accounts_result = _resolve_tan_response(
                    client, accounts_result, args.decoupled_wait
                )
            accounts: List[SEPAAccount] = accounts_result or []

            LOG.info("[3/5] %d Konto/Konten empfangen:", len(accounts))
            target: Optional[SEPAAccount] = None
            for acc in accounts:
                acc_iban = (acc.iban or "").replace(" ", "").upper()
                acc_bic = acc.bic or "?"
                is_target = acc_iban == iban
                marker = " ← Zielkonto" if is_target else ""
                LOG.info("       IBAN %s  BIC %s%s", _iban_last4(acc_iban), acc_bic, marker)
                if is_target:
                    target = acc

            if target is None:
                LOG.error(
                    "[3/5] Zielkonto %s nicht in der Kontoliste gefunden! "
                    "Gefundene IBANs: %s",
                    _iban_last4(iban),
                    [_iban_last4((a.iban or "").replace(" ", "").upper()) for a in accounts],
                )
                return 1

            LOG.info("[3/5] Zielkonto: IBAN %s  BIC %s", _iban_last4(iban), target.bic or "?")

            # ── Phase 4: Transaktionen abrufen (HKCAZ / CAMT.052 oder MT940) ─
            LOG.info("[4/5] Transaktionen abrufen (HKCAZ) …")
            LOG.info("      Zeitraum: %s → %s", start_date, end_date)

            tx_result = client.get_transactions(
                target,
                start_date=start_date,
                end_date=end_date,
            )

            if isinstance(tx_result, NeedTANResponse):
                LOG.info("[4/5] Bank verlangt TAN/Decoupled-Bestätigung für Transaktionsabruf")
                tx_result = _resolve_tan_response(
                    client, tx_result, args.decoupled_wait
                )

            # ── Phase 5: Ergebnis parsen ──────────────────────────────────────
            LOG.info("[5/5] Ergebnis parsen …")
            transactions = list(tx_result) if tx_result is not None else []
            LOG.info("[5/5] %d Buchung(en) empfangen und geparst", len(transactions))

            if not transactions:
                LOG.warning(
                    "Keine Buchungen im Zeitraum %s – %s erhalten.",
                    start_date,
                    end_date,
                )
                LOG.info("Tipp: --days-back erhöhen (z.B. --days-back 90)")
            else:
                n = (
                    len(transactions)
                    if args.max_entries <= 0
                    else min(args.max_entries, len(transactions))
                )
                _sep(f"✓ {len(transactions)} Buchung(en) – zeige {n}")
                for i, tx in enumerate(transactions[:n]):
                    _print_transaction(i, tx)
                if n < len(transactions):
                    remaining = len(transactions) - n
                    print(f"\n  … und {remaining} weitere (--max-entries 0 für alle)")

            _sep()
            LOG.info("✓ SYNC ERFOLGREICH – %d Buchung(en) geparst", len(transactions))

    except TimeoutError as exc:
        LOG.error("[Timeout] %s", exc)
        return 1
    except Exception as exc:
        LOG.error("Fehler in FinTS-Kommunikation: %s", exc)
        if args.debug:
            LOG.exception("Stack trace:")
        else:
            LOG.info("Tipp: --debug für vollständiges FinTS-Wire-Logging")
        return 1

    return 0


# ── CLI entry point ───────────────────────────────────────────────────────────

def main() -> int:
    parser = argparse.ArgumentParser(
        description="BBBank FinTS Sync Debug – Replikation des App-Sync-Ablaufs",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=(
            "Beispiele:\n"
            "  python3 scripts/bbbank-sync-debug.py\n"
            "  python3 scripts/bbbank-sync-debug.py \\\n"
            "      --iban DE89370400440532013000 --user meinLogin \\\n"
            "      --tan-method 900 \\\n"
            "      --server https://finanzportal.bbbank.de/banking \\\n"
            "      --debug\n"
        ),
    )
    parser.add_argument(
        "--iban", default="",
        help="IBAN des Kontos (mit oder ohne Leerzeichen); wird abgefragt wenn leer",
    )
    parser.add_argument(
        "--user", default="",
        help="Nutzerkennung (Online-Banking Login); wird abgefragt wenn leer",
    )
    parser.add_argument(
        "--pin", default="",
        help="PIN – NICHT empfohlen als CLI-Argument (Prozessliste!); sicherer: weglassen",
    )
    parser.add_argument(
        "--blz", default="",
        help="BLZ (optional – wird normalerweise aus der IBAN abgeleitet)",
    )
    parser.add_argument(
        "--server", default="",
        help=(
            "FinTS-Server URL. Für BBBank z.B. https://finanzportal.bbbank.de/banking. "
            "Die korrekte URL findet sich in den hbci4java-Logs (Log-Level 4) unter 'host='."
        ),
    )
    parser.add_argument(
        "--tan-method", default="", metavar="CODE",
        help="TAN-Methoden-Code (z.B. '900' für BBBank Secure Go / BestSign)",
    )
    parser.add_argument(
        "--decoupled-wait", type=int, default=60, metavar="SEK",
        help="Max. Wartezeit in Sekunden für Secure Go Bestätigung (Default: 60)",
    )
    parser.add_argument(
        "--days-back", type=int, default=30, metavar="TAGE",
        help="Buchungen der letzten N Tage abrufen (Default: 30)",
    )
    parser.add_argument(
        "--max-entries", type=int, default=2, metavar="N",
        help="Max. Buchungen ausgeben (Default: 2; 0 = alle)",
    )
    parser.add_argument(
        "--debug", action="store_true",
        help="Ausführliches FinTS-Wire-Level-Logging (zeigt alle Segmente)",
    )
    args = parser.parse_args()

    _setup_logging(args.debug)

    # Interactive prompts for required fields
    if not args.iban:
        try:
            args.iban = input("IBAN (z.B. DE89 3704 0044 0532 0130 00): ").strip()
        except (EOFError, KeyboardInterrupt):
            print()
            return 130
    if not args.user:
        try:
            args.user = input("Nutzerkennung (Online-Banking Login): ").strip()
        except (EOFError, KeyboardInterrupt):
            print()
            return 130

    if not args.iban:
        LOG.error("IBAN ist erforderlich.")
        return 2
    if not args.user:
        LOG.error("Nutzerkennung ist erforderlich.")
        return 2

    try:
        return run(args)
    except KeyboardInterrupt:
        print()
        LOG.info("Abgebrochen.")
        return 130
    except Exception as exc:
        LOG.error("Unerwarteter Fehler: %s", exc)
        if args.debug:
            LOG.exception("Stack trace:")
        return 1


if __name__ == "__main__":
    sys.exit(main())
