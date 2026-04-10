# BBBank Sync E2E Test

Dieser Ablauf testet den echten FinTS-Zugriff (BBBank), den Abruf der Buchungen und das Einspielen in die App.

## Ziel

- Echter Login gegen BBBank
- Kontoauszug wird abgerufen
- Neue Buchungen werden lokal gespeichert
- Ergebnis ist in UI und Log nachvollziehbar

## Live-Nachweis (was als "bestanden" zählt)

Ein Live-Test gilt nur dann als bestanden, wenn alle Punkte erfüllt sind:

- PIN/TAN oder Decoupled-Bestätigung wurde tatsächlich ausgelöst
- Snackbar zeigt ein technisches Ergebnis (z. B. "X neue Buchungen importiert")
- In der Kontoansicht sind neue oder erwartete Buchungen sichtbar
- Im Fehlerprotokoll gibt es korrespondierende Einträge für:
  - Start des Syncs
  - Bankabruf erfolgreich/fehlgeschlagen
  - Importanzahl

Optional zusätzlicher Nachweis via Logcat:

```bash
adb logcat -v time | grep -E "FintsService|AccountViewModel|BankSync"
```

## Live-Test ohne App-Start (Terminal)

Für einen direkten Test der Kette Verbindung -> Sync -> Import ohne App-UI:

```bash
./scripts/run-live-bbbank-sync-test.sh
```

Das Skript fragt relevante Daten im Terminal ab (IBAN, Nutzerkennung, PIN, optional TAN/TAN-Methode) und startet danach gezielt den Live-Test:

- Testklasse: `AccountViewModelLiveBankSyncTest`
- Kein Start der Android-App-Oberfläche
- Echter FinTS-Abruf gegen reales Konto
- Importpfad über `AccountViewModel.syncBankTransactions`

Hinweis:

- Für Decoupled-Verfahren (z. B. BBBank Secure Go) kann die Wartezeit im Skript gesetzt werden.
- Bei klassischer TAN kann einmalig eine TAN im Prompt gesetzt werden.

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
