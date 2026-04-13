# BBBank Sync E2E Test

Dieser Ablauf testet den echten FinTS-Zugriff (BBBank), den Abruf der Buchungen und das Einspielen in die App.

## Ziel

- Echter Login gegen BBBank
- Kontoauszug wird abgerufen
- Neue Buchungen werden lokal gespeichert
- Ergebnis ist in UI und Log nachvollziehbar

## Sync-Phasen (für Debugging)

Jeder Sync durchläuft folgende überwachte Phasen:

1. **Session-Setup** — HBCI-Handler und Passport initialisieren, Verbindung zum Bankserver aufbauen
2. **BIC-Lookup** — BIC aus Passport-UPD für die Bank abrufen (notwendig für SEPA/CAMT)
3. **Job-Auswahl** — Fallback-Sequenz versuchen: KUmsAllCamt → KUmsZeitSEPA → KUmsAll → KUmsNew
4. **Bank-Anfrage** — Kontoauszug-Request senden, auf Response warten
5. **Daten-Verarbeitung** — Transaktionen aus HBCI-Response extrahieren
6. **Import lokal** — Duplikate filtern, neue Transaktionen in DB speichern

Falls ein Fehler auftritt, zeigt `bankSyncState.Error` die Phase an, in der das Fehler vorkam.

## Live-Nachweis (was als "bestanden" zählt)

Ein Live-Test gilt nur dann als bestanden, wenn alle Punkte erfüllt sind:

- PIN/TAN oder Decoupled-Bestätigung wurde tatsächlich ausgelöst
- Snackbar zeigt ein technisches Ergebnis (z. B. "X neue Buchungen importiert")
- In der Kontoansicht sind neue oder erwartete Buchungen sichtbar
- Im Fehlerprotokoll gibt es korrespondierende Einträge für:
  - Start des Syncs mit Phase-Information
  - Bankabruf erfolgreich/fehlgeschlagen mit Phase-Info
  - Importanzahl

Optional zusätzlicher Nachweis via Logcat (Phase-Tracking):

```bash
adb logcat -v time | grep -E "\[fetchAccountStatement/\]|BankSyncState|ERROR|FAILED"
```

## Python-Skript (eigenständig, kein Gradle erforderlich)

Für schnelle Iteration und AI-gestütztes Debugging gibt es ein **eigenständiges Python-Skript**
das den vollständigen FinTS-Ablauf (connect → auth Secure Go → Transaktionen) **ohne**
Android-Build oder Gradle repliziert:

```bash
# Einmalig: Abhängigkeit installieren
pip install fints

# Skript starten (interaktiv – fragt IBAN, Nutzerkennung, PIN ab)
python3 scripts/bbbank-sync-debug.py

# Oder mit Argumenten (für BBBank Secure Go):
python3 scripts/bbbank-sync-debug.py \
    --iban   "DE89 3704 0044 0532 0130 00" \
    --user   NUTZERKENNUNG \
    --tan-method 900 \
    --server https://finanzportal.bbbank.de/banking \
    --debug
```

### Parameter

| Parameter | Bedeutung | Default |
|-----------|-----------|---------|
| `--iban` | Konto-IBAN | (Prompt) |
| `--user` | Nutzerkennung (Online-Banking Login) | (Prompt) |
| `--pin` | PIN – besser weglassen (Sicherheit) | (Prompt) |
| `--server` | FinTS-Server-URL (**Pflichtfeld**) | — |
| `--tan-method` | TAN-Methoden-Code, z.B. `900` für Secure Go | (auto) |
| `--decoupled-wait` | Wartezeit für App-Bestätigung in Sek. | `60` |
| `--days-back` | Buchungshistorie in Tagen | `30` |
| `--max-entries` | Max. angezeigte Buchungen (0 = alle) | `2` |
| `--debug` | Vollständiges FinTS-Wire-Logging | `false` |

### FinTS-Server-URL für BBBank ermitteln

Die Server-URL ist bankspezifisch und wird **nicht** aus der IBAN abgeleitet.
Drei Wege zum richtigen Wert:

1. **Aus hbci4java-Logs** (zuverlässigste Methode):
   `./scripts/run-live-bbbank-sync-test.sh` mit HBCI-Log-Level 4 starten,
   in der Ausgabe nach `host=` oder `HBCI url` suchen.

2. **BBBank Online-Portal**: Unter „Mein Konto / Technische Einstellungen" oder
   via Support direkt erfragen.

3. **Bekannte BBBank-URLs** (ohne Garantie auf Aktualität):
   `https://finanzportal.bbbank.de/banking`

### Ablauf des Skripts (Phasen)

```
[1/5] Verbindungsaufbau – Client initialisieren, Dialog öffnen
[2/5] TAN-Methoden – verfügbare Verfahren aus BPD auflisten, Methode wählen
[3/5] Konten – SEPA-Konten und BIC aus UPD abrufen, Zielkonto identifizieren
[4/5] Transaktionen – HKCAZ senden (CAMT.052 oder MT940), Decoupled-TAN behandeln
[5/5] Parsen – Buchungen extrahieren und anzeigen
```

### Secure Go / Decoupled-TAN Ablauf

```
[4/5] Transaktionen abrufen (HKCAZ) …
── SECURE GO BESTÄTIGUNG ──────────────────────────────────────
  Challenge: Bitte bestätigen Sie den Auftrag in Ihrer Secure-Go-App (Beispiel-Challenge)
  ➜ Bitte jetzt in der BBBank Secure Go App bestätigen.
[Enter drücken, wenn die App-Bestätigung erfolgt ist]
[TAN] Nutzer hat bestätigt – poll Bank …
[TAN] Decoupled-Bestätigung erfolgreich.
[5/5] Ergebnis parsen …
[5/5] 3 Buchung(en) empfangen und geparst
── ✓ 3 Buchung(en) – zeige 2 ──────────────────────────────────

  Buchung 1:
    Datum     : 2026-04-10
    Betrag    : -45.00 EUR
    Verwendung: Lastschrift Supermarkt Berlin
    Name      : REWE GmbH

  Buchung 2:
    ...
✓ SYNC ERFOLGREICH – 3 Buchung(en) geparst
```

### Zweck dieses Skripts vs. Gradle-Test

| Kriterium | Python-Skript | Gradle-Test (`run-live-bbbank-sync-test.sh`) |
|-----------|---------------|----------------------------------------------|
| Abhängigkeit | `pip install fints` | Gradle, Android SDK, Java 21 |
| Startzeit | ~2 Sekunden | ~60–120 Sekunden |
| Bibliothek | python-fints | hbci4java (gleich wie App) |
| Debugging | Wire-Level-Log mit `--debug` | JUnit-Logs, AppLogger-Export |
| Zweck | Protokoll-Diagnose, Schnelltest | Exakte App-Verhalten-Prüfung |

Wenn das Python-Skript Buchungen empfängt, aber der Gradle-Test hängt → Problem liegt
in hbci4java oder der Android-Integration, nicht im Bank-Endpoint.

---

## Live-Test ohne App-Start (Terminal, Gradle-basiert)

Für einen direkten Test der Kette Verbindung → Sync → Import ohne App-UI:

```bash
./scripts/run-live-bbbank-sync-test.sh
```

Das Skript fragt relevante Daten im Terminal ab und startet danach gezielt den Live-Test:

- **Testklasse**: `AccountViewModelLiveBankSyncTest`
- **Ablauf**: Kein App-Start, echter FinTS-Abruf gegen echtes Konto
- **Logging**: Konsolausgabe mit Phase-Transitions und Fehlerdetails
- **Debug-Level**: 1-5 stufen (Default 4 = Trace)

### Interaktive Eingaben

Das Skript führt dich Schritt-für-Schritt durch:

1. **IBAN** — dein Konto (z.B. `DE89 3704 0044 0532 0130 00`)
2. **Nutzerkennung** — Online-Banking-Login bei der Bank
3. **PIN** — Online-Banking-PIN (wird NICHT gelogged)
4. **BLZ** (optional) — falls nicht aus IBAN ableitbar
5. **TAN-Methode** (optional) — für explizite Auswahl (z.B. `900` für BBBank Secure Go)
6. **Decoupled-Wartezeit** (optional) — Sek. für Secure Go-Bestätigung (Default: 30s)
7. **Decoupled-Retry-Wartezeit** (optional) — Millisekunden zwischen Status-Checks nach Freigabe (Default: 2000ms)
8. **Test-Timeout** (optional) — Gesamtzeit für Test (Default: 420s = 7min)
9. **TAN** (optional) — falls klassische mTAN/SMS-TAN verlangt wird
10. **Debug-Level** (optional) — Logging-Detail (1-5, Default: 4)

## Neue Erkenntnis aus Run 2

- Authentifizierung inkl. Decoupled-Freigabe funktioniert (Bank antwortet mit `3955::Sicherheitsfreigabe erfolgt über anderen Kanal`)
- Hänger trat **danach** auf: Timeout mit Final-State `BIC_LOOKUP` nach 420s
- Ursache war ein wahrscheinlicher Wait-Overhead im Callback bei `NEED_PT_DECOUPLED_RETRY`

Umsetzung für Run 3:

- `NEED_PT_DECOUPLED`: weiterhin ein UI-gebundener Bestätigungs-Wait
- `NEED_PT_DECOUPLED_RETRY`: nur noch kurzer technischer Wait (Default 2000ms), **kein erneuter UI-Dialog**
- Konfigurierbar via JVM-Property `mybudgets.decoupled.retry.wait.millis`

## Neue Erkenntnis aus Run 3

- Fix für `NEED_PT_DECOUPLED_RETRY` ist aktiv, aber Timeout tritt weiterhin auf.
- Laufzeit: ~8 Minuten, danach `IllegalStateException` mit `Live-Sync Timeout`.
- Symptom bleibt: Test hängt nach erfolgreichem Sicherheitskanal-Hinweis (`3955`), bevor ein finaler Success/Error-State erreicht wird.

Empfehlung für nächsten Lauf:

- `Gesamt-Test-Timeout`: 900 Sekunden
- `Decoupled-Retry-Wartezeit`: 1000-2000 ms
- Monitoring über Markerdatei `.test-loop/.run-finished` (bleibt nun nach Lauf erhalten)

### Fehleranalyse im Terminal

Das Skript gibt nach Test-Ende detaillierte Hilfe:

```
✗ Test FAILED (Exit Code: 1)

=== Häufige Fehlerursachen & Lösungen ===

PIN/Nutzerkennung ungültig:
  → Überprüfe Nutzerkennung und PIN im Online-Banking der Bank
  → Test nochmal starten und Daten erneut eingeben

TAN-Fehler (falscher TAN, falsches Verfahren):
  → Nutze den korrekten TAN aus deiner mTAN/pushTAN-App
  → Oder teste ohne TAN-Eingabe (für Secure Go automatisch funktionieren lässt)

...
```

### Beispiel: Secure Go / Decoupled-TAN

```bash
$ ./scripts/run-live-bbbank-sync-test.sh
...
TAN-Methode: 900
Decoupled-Wartezeit Sekunden: 30
...
⏳ Syncing Phase: BIC-Abfrage
⏳ Syncing Phase: Job-Auswahl
⏳ Syncing Phase: Bank-Anfrage — Decoupled-Bestätigung erforderlich (Secure Go)
  [Jetzt kannst du in deiner Banking-App bestätigen, das Skript wartet max. 30 Sekunden]
⏳ Syncing Phase: Daten-Verarbeitung
✓ Live-Sync SUCCESS: 2 Buchungen importiert
```

### Beispiel: Fehlerfall (Phase-spezifischer Error)

```bash
⏳ Syncing Phase: Session-Setup
⏳ Syncing Phase: BIC-Lookup
✗ Live-Sync ERROR in phase Job-Auswahl: ALL job attempts failed. Last error: Property my.bic wurde nicht gestellt
  Recent logs:
    ...
    [fetchAccountStatement/3-job] ALL job attempts failed. Last error: Property my.bic wurde nicht gesetzt

Live-Sync fehlgeschlagen in Phase Job-Auswahl: ...
```

Hinweise:

- Für Decoupled-Verfahren (z. B. BBBank Secure Go) kann die Wartezeit im Skript gesetzt werden.
- Bei klassischer TAN kann eine TAN im Prompt gesetzt werden.
- Debug-Level 5 gibt maximales HBCI-Logging für tiefe Fehleranalyse.

## Vorbedingungen

- Internetverbindung aktiv
- In den Kontobewegungen sind idealerweise 1-2 neue Buchungen vorhanden, die noch nicht importiert sind

## Testfall A: Normaler Sync

1. App starten.
2. Konto öffnen.
3. Button "Kontoauszug synchronisieren" tippen.
4. Falls Dialog erscheint: PIN eingeben.
5. Falls TAN/Decoupled erscheint: in BBBank Secure Go bestätigen und danach in der App mit OK fortfahren.
6. Auf Snackbar-Ergebnis warten.

Erwartung:

- Snackbar zeigt "X neue Buchungen importiert" (X >= 0)
- In der Kontoliste erscheinen neue Buchungen
- App bleibt stabil (kein Crash)
- Im Protokoll ist der Pfad Start -> Bankabruf -> Import nachvollziehbar

## Testfall B: Historischer Sync

1. Konto öffnen.
2. "Historischen Verlauf importieren" tippen.
3. Startdatum in der Vergangenheit wählen (z. B. 12 Monate zurück).
4. PIN/TAN/Decoupled wie oben bestätigen.

Erwartung:

- Snackbar mit Anzahl importierter Buchungen
- Bei bereits importierten Buchungen keine Duplikate

## Negativtest 1: Fehlende Nutzerkennung

1. Im Konto die Nutzerkennung entfernen.
2. Erneut "Kontoauszug synchronisieren" ausführen.

Erwartung:

- Sofortige Meldung: fehlende Nutzerkennung
- Kein Bankzugriff wird gestartet

## Negativtest 2: Falsche PIN

1. Sync starten.
2. Falsche PIN eingeben.

Erwartung:

- Verständliche Fehlermeldung (PIN/Nutzerkennung ungültig)
- Kein App-Absturz

## Log-Auswertung (wichtig für Diagnose)

1. In Einstellungen den Punkt "Fehlerprotokoll anzeigen" öffnen.
2. Filter auf "Fehler" oder "Warn+" setzen.
3. Relevante Tags prüfen:
   - FintsService
   - AccountViewModel
4. Bei Fehlern: "Export" nutzen und Protokoll teilen.

## Ergebnisvorlage (zum Zurückmelden)

Bitte nach dem Live-Durchlauf genau diese Felder ausfüllen:

- Konto: BBBank / Kontobezeichnung
- Testfall: A oder B
- PIN/TAN/Decoupled angezeigt: ja/nein
- Snackbar-Text: ...
- Importierte Anzahl: ...
- Sichtbare neue Buchungen in App: ja/nein
- Fehlerprotokoll exportiert: ja/nein
- Auffälligkeiten: ...

## Abnahmekriterien

- Testfall A erfolgreich
- Testfall B erfolgreich
- Kein Duplikatimport bei erneutem Lauf
- Negativtests zeigen verständliche Fehlermeldung
- Log zeigt nachvollziehbaren Pfad ohne unerwartete Exceptions

## Bisherige Erkenntnisse (Stand 2026-04-10)

### Technische Ursache des ursprünglichen Sync-Problems

- Der frühere WorkManager-Pfad (`BankSyncWorker`) lief ohne verlässlich gesetzte PIN/TAN-Provider.
- Fix: Bank-Sync läuft jetzt direkt über `AccountViewModel.syncBankTransactions`, während die Fragment-UI aktiv ist.

### Test-Infrastruktur und Live-Test-Pfad

- Es gibt jetzt einen echten Live-Test gegen BBBank: `AccountViewModelLiveBankSyncTest`.
- Start über Script: `./scripts/run-live-bbbank-sync-test.sh`.
- Das Script übergibt Werte per JVM-Properties (`-Dmybudgets.*`) statt nur per Env-Variablen.

### Wichtige Befunde aus den letzten Läufen

- Ein Lauf wurde zunächst als `skipped` erkannt, weil der Test-JVM die Env-Variablen nicht zuverlässig gesehen hat.
- Danach konnte die Secure-Go-Freigabe wiederholt erfolgreich ausgelöst werden.
- In einzelnen Läufen gab es danach entweder:
  - Timeout beim Abruf (lange Serverantwort nach Freigabe), oder
  - sofortigen Login-Fehler durch fehlerhafte Eingabe (z. B. PIN mit führendem Leerzeichen).
- Ein aktueller Live-Lauf zeigt jetzt deutlich mehr:
  - Authentifizierung gegen BBBank funktioniert.
  - Secure-Go / Direktfreigabe wird korrekt angefordert.
  - Die Bank liefert die Kontoliste erfolgreich zurück (`IBAN/BIC für 3 Konten empfangen`).
  - Das getestete Konto wird serverseitig korrekt erkannt.
  - Der eigentliche Hänger sitzt danach im Umsatzabruf-Job `HKCAZ` / `KUmsZeitCamt1`.
  - Der Lauf endet nicht mit einem Bankfehler, sondern mit einem Timeout nach erfolgreicher Freigabe.

### Aktuell wahrscheinlichste Fehlerstelle

- Problem liegt nach heutigem Stand nicht mehr bei Login, PIN oder Secure-Go.
- Problem liegt sehr wahrscheinlich im CAMT-/HKCAZ-Abrufpfad nach der Decoupled-Freigabe.
- Der Report spricht dafür, dass BBBank den Abruf startet, aber keine verwertbare Abschlussantwort für den Umsatzdownload zurückkommt oder hbci4java in diesem Schritt hängen bleibt.

### Bereits umgesetzte Robustheitsmaßnahmen

- Konfigurierbares Gesamt-Timeout im Script (Default 420s).
- Erhöhtes HBCI-Loglevel für Live-Läufe (`-Dmybudgets.hbci.logLevel=4`).
- Erweiterte Fehlerinfos im Live-Test: Bei Fehler/Timeout wird ein App-Log-Dump ausgegeben.
- Eingabehärtung im Script: Trimmen von IBAN, Nutzerkennung und PIN, einfache Plausibilitätschecks.

### Aktueller Status

- Authentifizierung inkl. Secure-Go kann grundsätzlich funktionieren.
- Der Download/Import-Pfad wird weiterhin live verifiziert; bei erneutem Fehlschlag bitte die komplette Test-Fehlermeldung inkl. "Letzte App-Logs" verwenden.
