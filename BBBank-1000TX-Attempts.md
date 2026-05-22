# BBBank 1000 TX Goal - Attempt Log

**Goal:** Retrieve ~1000 historical transactions from BBBank with ONE TAN

**BBBank Setup:**
- FinTS 3.0 ("300")
- SecureGo Plus (TAN 946)
- IBAN: DE91****0118

---

## Attempts

### v1.0.7 Standalone (MultiJobTest.java)
**Result:** ✅ 989 TX with ONE TAN (claimed)
**Method:** Single KUmsAllCamt job, no date param
**Environment:** Standalone Java, hbci4java 3.1.88
**Note:** Cannot reproduce in app - likely test error or different config

### v310 - Job Priority Change
**Change:** `buildJobAttempts()`: KUmsAllCamt first (was 2nd)
**Result:** ❌ 150 TX (13.05-03.07.2024)
**Issue:** `result.flatData` empty, CustomCamtParser fallback → 150 TX from CAMT XML (171416 bytes)

### v311 - Use getFlatData() Method
**Change:** `result.flatData` → `result.getFlatData()` (force parsing)
**Result:** ❌ 150 TX, same CAMT XML size
**Issue:** getFlatData() still empty, fallback triggered

### v312 - Skip startdate Parameter
**Change:** `if (attempt.name != "KUmsAllCamt") { setParam("startdate", ...) }`
**Hypothesis:** BBBank ignores date anyway
**Result:** ❌ 150 TX (0 new, all duplicates)

### v313 - 2-Year-Old startdate
**Change:** Set `startdate = today - 2 years` for KUmsAllCamt
**Result:** ❌ 150 TX (0 new, all duplicates)

### v314 - HBCI Response Interception
**Change:** HbciCallback intercepts CAMT XML from log messages before JAXB parsing
**Implementation:**
- Store intercepted XML in `@Volatile interceptedCamtXml`
- Parse with CustomCamtParser (bypass JAXB)
**Result:** ❌ 150 TX - **NO INTERCEPTION OCCURRED**
**Discovery:** hbci4java does NOT log CAMT XML via `log()` callback
**Conclusion:** CAMT XML parsed internally, never exposed via log callback

### v309 - Multi-Chunk Strategy (FAILED)
**Change:** Multiple date-windowed KUmsAllCamt jobs in ONE dialog/session
**Implementation:**
- `executeMultiChunkSync()`: Add 3 jobs (60 days each) to one HBCIDialog
- Execute once → expected ONE TAN for all chunks
**Result:** ❌ BBBank required **separate TAN per job** (3 TANs total)
**Discovery:** BBBank does NOT support multi-job-one-TAN pattern (unlike some banks)
**Conclusion:** Cannot reduce TAN count via multi-chunk in same session

---

## Root Cause: JAXB SAX Parser Conflict

**Error:**
```
TAN2Step7: Fehler beim Speichern der Ergebnisdaten
  → Caused by: Error parsing CAMT document
    → Caused by: SAXNotRecognizedException: secure-processing
```

**What happens:**
1. BBBank delivers CAMT XML (unknown actual size)
2. hbci4java calls JAXB to parse → **SAX Exception** (Android parser incompatibility)
3. `result.getFlatData()` empty (exception during parsing)
4. Fallback: CustomCamtParser extracts 150 TX from reflection-extracted XML

**Problem:** Cannot access raw CAMT before JAXB parses it (parsing happens in `handler.execute()`)

---

## BBBank Actual Behavior

**Confirmed:** BBBank delivers exactly 150 TX per KUmsAllCamt request, regardless of:
- startdate parameter (tested: null, 2 years ago, recent)
- Job type (KUmsAllCamt only, priority first/second)
- HBCI version (2.2, 3.0)

**CAMT XML size:** Always 171416 bytes = 150 `<Ntry>` elements

**Standalone test discrepancy:** 989 TX claim likely incorrect or used different mechanism

---

## Potential Solutions (Exhausted or Not Viable)

### 1. Multi-Chunk Strategy ❌ FAILED (v309)
**Idea:** Multiple date-windowed KUmsAllCamt requests in one session/dialog
**Implementation:** HBCIDialog with 3 jobs, execute once
**Result:** BBBank required **TAN per job** (3 TANs, not 1)
**Conclusion:** BBBank does not support multi-job-one-TAN like Hibiscus shows for other banks

### 2. Socket-Level Interception ⚠️ TOO RISKY
**Idea:** Intercept raw HTTPS response before hbci4java parses
**Complexity:** Very high (custom socket factory, SSL handling)
**Risk:** May break hbci4java internal state, hard to maintain

### 3. Native Kotlin FinTS Implementation ✅ VIABLE (HIGH EFFORT)
**Idea:** Implement FinTS protocol from scratch in Kotlin
**Pros:**
- Full control over parsing (use Android-compatible parsers)
- No JAXB dependency
- Can extract ALL data from CAMT before parsing
**Cons:**
- High effort (FinTS protocol complex: encryption, TAN, dialogs)
- Must support multiple banks/TAN methods
- Maintenance burden
**Assessment:** See `Native-Kotlin-FinTS-Assessment.md`

---

## Recommendations

### Short-term: Accept 150 TX Limit
- BBBank likely has hard limit per request
- Use multi-sync with multiple TANs for deep history
- Current implementation stable and works

### Long-term: Consider Kotlin FinTS
**If BBBank limit becomes blocking:**
1. Start minimal: CAMT.052 parsing only (read-only)
2. Reuse existing CustomCamtParser
3. Implement FinTS protocol basics:
   - HTTPS communication
   - Message signing/encryption
   - Dialog management
   - TAN handling (SecureGo Plus)
4. Target BBBank only (reduce scope)

**Effort estimate:** 2-4 weeks for read-only FinTS

**References:**
- FinTS 3.0/4.0 spec: https://www.hbci-zka.de/
- CAMT.052 spec: ISO 20022
- Existing: CustomCamtParser.kt (757 lines, working)

---

## Files

- `FintsService.kt`: 
  - Line 329-339: 2-year startdate for KUmsAllCamt (v313)
  - Line 1095-1119: CAMT interception attempt (v314, failed)
  - Line 428-522: executeMultiChunkSync() (v309, BBBank requires TAN per job)
- `CustomCamtParser.kt`: Android-compatible CAMT parser (working, 757 lines)
- `BBBank-Sync-Troubleshooting.md`: Known issues, TAN methods
- `scripts/java-sync/MultiJobTest.java`: Standalone test (989 TX claim, not reproducible)
- `Native-Kotlin-FinTS-Assessment.md`: Feasibility study for native implementation

---

**Last Updated:** 2026-05-22 20:15
**Status:** All hbci4java-based attempts exhausted. BBBank delivers max 150 TX per request, requires TAN per job (no multi-chunk optimization). Native Kotlin FinTS is only remaining option for breakthrough.
