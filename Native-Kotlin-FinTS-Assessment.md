# Native Kotlin FinTS - Feasibility Assessment

## Why Consider This?

**Problem:** hbci4java blocks full CAMT access due to JAXB/Android incompatibility
**Current:** Max 150 TX per request from BBBank (CustomCamtParser fallback)
**Goal:** Access full CAMT XML before any parsing → potentially 1000+ TX

---

## Scope

### Minimal Viable Implementation (Read-Only)

**Target:** BBBank account statement retrieval only

**Required Components:**
1. **HTTPS Client** - Retrofit/OkHttp (already in project)
2. **FinTS Message Builder** - Generate FinTS 3.0 messages (XML-based)
3. **FinTS Message Parser** - Extract segments from response
4. **Encryption** - RSA for key exchange, AES for message encryption
5. **TAN Handling** - SecureGo Plus (decoupled, method 946)
6. **CAMT Extraction** - Raw XML before parsing (CustomCamtParser reuse)

**NOT Required (initially):**
- SEPA transfers
- Multi-bank support
- MT940 parsing
- Legacy HBCI 2.2

---

## Technical Assessment

### 1. FinTS Protocol Complexity

**Dialog Flow (KUmsAllCamt):**
```
1. DialogInit        → Bank returns session ID, encryption params
2. Identification    → User ID authentication
3. TAN Request       → Trigger SecureGo Plus notification
4. TAN Response      → Send confirmed TAN
5. KUmsAllCamt Job   → Request CAMT data
6. DialogEnd         → Close session
```

**Each message:**
- Signed with user credentials
- Encrypted (if bank requires)
- Contains multiple segments (header, auth, job, footer)

**Complexity:** Medium (protocol is documented, but verbose)

### 2. Encryption Requirements

**FinTS 3.0:**
- **PIN/TAN:** Usually no encryption (HTTPS sufficient)
- **If required:** RSA for key exchange, AES-256-CBC for messages

**BBBank:** Likely PIN/TAN without encryption (most modern banks)

**Complexity:** Low-Medium (can skip initially, add if needed)

### 3. TAN Handling (SecureGo Plus)

**Current hbci4java flow:**
```kotlin
// Request TAN
NEED_PT_DECOUPLED → Show "Bitte in SecureGo bestätigen"
NEED_PT_DECOUPLED_RETRY → Poll bank until confirmed
```

**Native Kotlin:**
```kotlin
suspend fun waitForDecoupledTan(challenge: String): Boolean {
    tanProvider.invoke(challenge) // Show UI
    while (true) {
        delay(2000) // Poll every 2s
        val status = checkTanStatus()
        if (status == CONFIRMED) return true
        if (status == TIMEOUT) return false
    }
}
```

**Complexity:** Low (same logic as hbci4java)

### 4. CAMT Extraction (KEY ADVANTAGE)

**Native Kotlin:**
```kotlin
val response = httpClient.post(bankUrl) { body = fintsMessage }
val camt = extractSegment(response, "HKCAM") // Raw XML!
val parsed = CustomCamtParser.parse(camt)    // Full control
```

**No JAXB, no Android conflicts!**

**Complexity:** Low (CustomCamtParser already works)

---

## Effort Estimate

### Phase 1: Proof of Concept (1 week)
- FinTS message builder (DialogInit, Identification, DialogEnd)
- HTTPS client with bank URL
- Parse simple response (session ID extraction)
- **Goal:** Successfully open/close FinTS dialog

### Phase 2: Authentication (1 week)
- PIN/TAN authentication
- SecureGo Plus TAN handling
- **Goal:** Authenticated session with BBBank

### Phase 3: CAMT Retrieval (1-2 weeks)
- KUmsAllCamt job message
- CAMT segment extraction from response
- CustomCamtParser integration
- **Goal:** Retrieve full CAMT XML, parse all transactions

### Phase 4: Polish (3-5 days)
- Error handling
- BPD/UPD caching (bank parameter data)
- Passport persistence (session reuse)
- **Goal:** Production-ready for BBBank

**Total:** 3-4 weeks (one developer, part-time)

---

## Pros

✅ **Full CAMT Access:** No JAXB blocking
✅ **Android-Native:** No SAX parser conflicts
✅ **Debugging:** Full visibility into requests/responses
✅ **Lightweight:** Only implement what we need
✅ **Modern:** Kotlin coroutines, Retrofit patterns

## Cons

❌ **High Initial Effort:** 3-4 weeks vs. accepting 150 TX limit
❌ **Maintenance:** Must follow FinTS spec updates
❌ **Bank-Specific:** Testing limited to BBBank initially
❌ **Security Risk:** Must implement encryption correctly
❌ **No Multi-Bank:** Needs per-bank testing/adjustments

---

## Decision Criteria

### Implement Native Kotlin FinTS IF:
1. BBBank 150 TX limit is confirmed blocker for user
2. Multi-TAN sync is unacceptable UX
3. Team has 3-4 weeks bandwidth
4. BBBank is primary/only bank target

### Stay with hbci4java IF:
1. 150 TX per sync acceptable (with multi-sync for history)
2. Multi-bank support important
3. Limited dev time
4. Proven library stability valued

---

## Recommended Next Steps

### Option A: Accept Limit (Recommended)
1. **Document:** BBBank max 150 TX per request (this file)
2. **Workaround:** Multi-sync intervals (multiple TANs OK)
3. **Future:** Revisit if user reports blocker

### Option B: Prototype Native FinTS
1. **Spike:** 2-3 days proof-of-concept (DialogInit/End only)
2. **Evaluate:** Complexity vs. benefit after spike
3. **Decide:** Continue or abort based on results

---

## References

**FinTS Specification:**
- FinTS 3.0: https://www.hbci-zka.de/dokumente/spezifikation_deutsch/fintsv3/
- FinTS 4.0: https://www.hbci-zka.de/dokumente/spezifikation_deutsch/fintsv4/

**Existing Implementations:**
- hbci4java: https://github.com/hbci4j/hbci4java (Java, reference)
- Hibiscus: https://github.com/willuhn/hibiscus (Java, full client)
- pyFinTS: https://github.com/raphaelm/python-fints (Python, modern)

**Our Assets:**
- `CustomCamtParser.kt`: 757 lines, working CAMT.052 parser
- `HbciCamtPatcher.kt`: XML repair for broken bank responses
- BBBank knowledge: TAN methods, HBCI versions, quirks

---

**Created:** 2026-05-22
**Author:** OpenCode AI + Felix
**Status:** Feasibility assessment, no implementation yet
