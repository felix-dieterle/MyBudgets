# Code-Sync-Verifikation: Java-Sync vs. App

Dieser Check vergleicht die kritischen Banking-Code-Stellen zwischen Java-Sync (Referenz) und App.

## Kritische Code-Stellen

### 1. HBCI-Version Strategie

**Java-Sync (`BbbankSync.java` Zeile 126-130):**
```java
HBCIHandler handler = null;
try {
    handler = new HBCIHandler("220", passport);  // HBCI 2.2 ZUERST
} catch (Exception e) {
    System.out.println("HBCI 2.2 fehlgeschlagen, versuche 3.0: " + e.getMessage());
    handler = new HBCIHandler("300", passport);  // Fallback auf 3.0
}
```

**App (`FintsService.kt` Zeile 450-465):**
```kotlin
var handler: HBCIHandler? = null
try {
    handler = HBCIHandler("220", passport)  // ✅ HBCI 2.2 ZUERST
    AppLogger.i(TAG, "openSession: HBCI-Handler bereit – HBCI=220")
} catch (e220: Exception) {
    AppLogger.w(TAG, "HBCI 2.2 fehlgeschlagen, versuche 3.0: ${e220.message}")
    handler = HBCIHandler("300", passport)  // ✅ Fallback auf 3.0
    AppLogger.i(TAG, "openSession: HBCI-Handler bereit – HBCI=300")
}
```

**Status:** ✅ **SYNCHRON**

---

### 2. Job-Liste (MT940 ohne CAMT)

**Java-Sync (`BbbankSync.java` Zeile 142):**
```java
String[] jobTypes = {"KUmsZeitSEPA", "KUmsAll", "KUmsNew"};  // KUmsAllCamt entfernt!
```

**App (`FintsService.kt` Zeile 274-284):**
```kotlin
val jobAttempts = if (fromDate != null) listOf(
    // JobAttempt("KUmsAllCamt", fromDate),  // DEAKTIVIERT
    JobAttempt("KUmsZeitSEPA", fromDate),  // ✅ 1. KUmsZeitSEPA
    JobAttempt("KUmsAll"),                  // ✅ 2. KUmsAll
    JobAttempt("KUmsNew"),                  // ✅ 3. KUmsNew
) else listOf(
    // JobAttempt("KUmsAllCamt"),  // DEAKTIVIERT
    JobAttempt("KUmsZeitSEPA", Date(0)),   // ✅ 1. KUmsZeitSEPA (Epoch-Date)
    JobAttempt("KUmsAll"),                  // ✅ 2. KUmsAll
    JobAttempt("KUmsNew"),                  // ✅ 3. KUmsNew
)
```

**Status:** ✅ **SYNCHRON** (gleiche Reihenfolge, kein CAMT)

---

### 3. Job-Parameter (Konto, Startdatum)

**Java-Sync (`BbbankSync.java` Zeile 151-162):**
```java
Konto k = new Konto();
k.iban = iban;
k.blz = blz;
k.curr = "EUR";
k.number = accountNumberFromIban(iban);
job.setParam("my", k);

if (jobType.equals("KUmsAllCamt") || jobType.equals("KUmsZeitSEPA")) {
    job.setParam("startdate", sdf.format(startDate));
    job.setParam("enddate", sdf.format(endDate));
}
```

**App (`FintsService.kt` Zeile 295-297):**
```kotlin
val j = handler.newJob(attempt.name)
j.setParam("my", buildKonto(account, bic, passport))  // ✅ Konto-Objekt
attempt.startDate?.let { j.setParam("startdate", sdf.format(it)) }  // ✅ Startdatum nur für KUmsZeitSEPA
```

**Status:** ✅ **SYNCHRON** (buildKonto erstellt gleichwertiges Konto-Objekt)

---

### 4. Handler Execute & Result Parsing

**Java-Sync (`BbbankSync.java` Zeile 182-196):**
```java
HBCIExecStatus status = handler.execute();
if (!status.isOK()) {
    System.err.println("Fehler: Bank-Antwort nicht OK: " + status);
    System.exit(1);
}

GVRKUms result = (GVRKUms) job.getJobResult();
if (result == null) {
    System.err.println("Fehler: Kein Ergebnis vom Job");
    System.exit(1);
}
```

**App (`FintsService.kt` Zeile 337-351):**
```kotlin
val status = handler.execute()

if (!status.isOK) {
    AppLogger.e(TAG, "[$syncPhase/4-exec] Bank returned non-OK status: $status")
    error("Kontoauszug fehlgeschlagen: $status")
}

val result = job.jobResult as? GVRKUms
    ?: error("Unerwartetes Ergebnis vom Kontoauszug-Job")
```

**Status:** ✅ **SYNCHRON**

---

## Verifikations-Checkliste

Vor jedem App-Build überprüfen:

- [ ] **HBCI-Version:** App versucht zuerst "220", dann "300" (wie Java-Sync)
- [ ] **Job-Liste:** `KUmsZeitSEPA → KUmsAll → KUmsNew` (ohne CAMT)
- [ ] **Job-Parameter:** `my` (Konto), `startdate` nur für KUmsZeitSEPA/CAMT
- [ ] **Kein CAMT:** `KUmsAllCamt` auskommentiert/entfernt
- [ ] **Java-Sync funktioniert:** `scripts\qt.cmd` erfolgreich

## Automatisierter Check (TODO)

```powershell
# scripts/verify-sync.ps1
# Prüft automatisch ob Java-Sync und App synchron sind

# 1. Check HBCI Version Pattern
$appCode = Get-Content "app/src/main/java/de/mybudgets/app/data/banking/FintsService.kt" -Raw
if ($appCode -match 'HBCIHandler\("220"') {
    Write-Host "✅ App nutzt HBCI 2.2 zuerst"
} else {
    Write-Host "❌ App nutzt NICHT HBCI 2.2 zuerst!"
}

# 2. Check Job-Liste
if ($appCode -match 'KUmsZeitSEPA.*KUmsAll.*KUmsNew' -and $appCode -notmatch 'KUmsAllCamt[^/]') {
    Write-Host "✅ Job-Liste korrekt (ohne CAMT)"
} else {
    Write-Host "❌ Job-Liste inkorrekt!"
}

# 3. Run Java-Sync Test
Write-Host "`n=== Running Java-Sync Test ==="
& scripts\qt.cmd
if ($LASTEXITCODE -eq 0) {
    Write-Host "✅ Java-Sync erfolgreich"
} else {
    Write-Host "❌ Java-Sync fehlgeschlagen!"
}
```

## Bei Änderungen

**Workflow:**
1. Änderungen zuerst in `scripts/java-sync/BbbankSync.java` machen
2. Java-Sync testen: `scripts\qt.cmd`
3. Wenn erfolgreich: Änderungen in `app/.../FintsService.kt` übertragen
4. Diese Datei aktualisieren (Code-Snippets anpassen)
5. App bauen & testen

---

**Zuletzt aktualisiert:** 2026-05-08
**Sync-Status:** ✅ SYNCHRON
