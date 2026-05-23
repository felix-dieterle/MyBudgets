# Analyse des MyBudgets Sync-/Lifecycle-Problems

Export analysiert am: 23.05.2026

---

# Beobachtetes Verhalten

- Erster Sync läuft erfolgreich durch.
- Erwarteter automatischer Folgesync startet nicht sichtbar.
- Nach Wechsel in Einstellungen erscheint Fehler bzgl. Fragment/Pattern Matching.
- Nutzer hat den Eindruck, dass im Hintergrund noch Prozesse laufen.

---

# Wichtigste Erkenntnis

Ja — nach dem ersten Sync laufen noch umfangreiche Hintergrundprozesse.

Die Logs zeigen deutlich:

1. Historical-Sync Verarbeitung
2. RecurringPattern-Erkennung
3. UI/Dialog-Logik
4. Berechnung nächster Sync-Intervalle

Der eigentliche Bank-Sync scheint also nicht das Ende der gesamten Verarbeitung zu sein.

---

# Wahrscheinlichster Hauptfehler

## Lifecycle Crash beim Anzeigen eines Dialogs

Fehler:

```text
java.lang.IllegalStateException:
Can not perform this action after onSaveInstanceState
```

Stacktrace:

```text
AccountDetailFragment.checkForRecurringPatterns()
→ DialogFragment.show()
→ onDestroyView()
```

---

# Interpretation

Folgende Sequenz ist sehr wahrscheinlich:

1. Sync beendet sich
2. RecurringPatternDetector startet Analyse
3. Analyse läuft relativ lange
4. Nutzer verlässt Fragment / öffnet Einstellungen
5. Fragment wird zerstört (`onDestroyView`)
6. Danach versucht App noch einen Dialog zu öffnen
7. Android verhindert das
8. Crash unterbricht weitere Verarbeitung

---

# Hinweis aus dem Log

Kurz vor dem Crash:

```text
→ Zeige RecurringPatternDialog
→ patterns.size=1
```

Das bestätigt:
Die Pattern-Erkennung war noch aktiv und wollte UI anzeigen.

---

# Warum der zweite Sync vermutlich nicht startet

Der Crash passiert wahrscheinlich mitten in der Sync-Folgepipeline.

Dadurch könnten folgende Dinge ausfallen:

- Scheduling des nächsten Historical Sync
- Übergang Historical → Normal Sync
- UI-Refresh
- Queue-Fortsetzung

---

# Hinweise auf längere Hintergrundarbeit

Die Pattern-Erkennung verarbeitet sehr viele Gruppen:

```text
Gruppe 41 ...
Gruppe 42 ...
...
Gruppe 100 ...
```

Das ist keine kleine Nebenaufgabe.

Gerade bei vielen Transaktionen kann das:
- CPU-intensiv
- datenbanklastig
- mehrere Sekunden bis Minuten lang sein

Der Nutzer sieht davon offenbar keinen sichtbaren Progress.

---

# Verdächtige Logik im SyncIntervalRepo

Mehrfach:

```text
Kein Normal-Sync, daysSinceNewest=242 Tage
```

und:

```text
newestHistorical.endDate=23.09.2025
```

Heute ist aber Mai 2026.

Das bedeutet:
- Es existiert noch ein großer Gap bis heute.
- Trotzdem werden keine normalen Sync-Intervalle erzeugt.

---

# Besonders verdächtige Stelle

```text
→ RETURN 24.09.2025 (Noch nicht bei heute)
```

Das sieht so aus, als würde immer derselbe nächste Startpunkt zurückgegeben.

Mögliche Folge:
- Sync bleibt logisch "hängen"
- Kein Fortschreiten bis heute
- Kein Folgesync

---

# Weitere Auffälligkeit: Duplicate Historical Intervals

Mehrfach identische Intervalle:

```text
[3] 23.05.2024 bis 15.07.2024
[4] 23.05.2024 bis 15.07.2024
[5] 23.05.2024 bis 15.07.2024
```

Das deutet auf mögliche Probleme hin bei:

- Speicherung
- Merge-Logik
- Retry-Logik
- fehlender Deduplication

---

# Wahrscheinlichste Ursachen (geordnet)

## 1. Lifecycle-Crash unterbricht Pipeline
Sehr wahrscheinlich.

## 2. Historical → Normal Sync Übergang fehlerhaft
Ebenfalls wahrscheinlich.

## 3. Pattern-Erkennung blockiert lange
Wahrscheinlich.

## 4. Duplicate Interval Handling fehlerhaft
Möglich.

## 5. Scheduler wartet auf erfolgreichen UI-Flow
Möglich.

---

# Konkreter technischer Fix-Vorschlag

Vor Dialoganzeige prüfen:

```kotlin
if (!parentFragmentManager.isStateSaved && isAdded) {
    dialog.show(parentFragmentManager, TAG)
}
```

Besser langfristig:
- Pattern-Ergebnis über ViewModel/EventFlow senden
- Dialog erst in RESUMED State anzeigen

---

# Weitere sinnvolle Debug-Logs

Empfohlen:

```kotlin
Log.i(TAG, "Historical sync completed")
Log.i(TAG, "Scheduling next sync")
Log.i(TAG, "Pattern detection started")
Log.i(TAG, "Pattern detection finished")
Log.i(TAG, "Next interval=$interval")
Log.i(TAG, "Queue continues")
```

Zusätzlich hilfreich:

```kotlin
Log.i(TAG, "Fragment stateSaved=${parentFragmentManager.isStateSaved}")
```

---

# Zusammenfassung

Die Logs sprechen stark dafür, dass:

- nach dem ersten Sync noch erhebliche Hintergrundarbeit läuft,
- die App dabei in eine Lifecycle-Situation gerät,
- ein Dialog zu spät geöffnet wird,
- dadurch ein Crash entsteht,
- und die weitere Sync-Kette vermutlich abbricht.

Zusätzlich existieren Hinweise auf:
- fehlerhafte Interval-Übergänge,
- mögliche Endlosschleifen,
- und doppelte Historical-Einträge.
