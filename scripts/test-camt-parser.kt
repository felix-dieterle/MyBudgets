#!/usr/bin/env kotlin

/**
 * Test-Script für den Custom CAMT-Parser
 * 
 * Testet den CustomCamtParser mit einem minimal CAMT.052-XML
 * ohne kompletten App-Build-Roundtrip.
 * 
 * Usage:
 *   kotlinc -script test-camt-parser.kt
 * 
 * Oder in Android Studio:
 *   Run as Kotlin Script
 */

// Minimal CAMT.052 XML für BBBank-Tests
val testCamtXml = """
<?xml version="1.0" encoding="UTF-8"?>
<Document xmlns="urn:iso:std:iso:20022:tech:xsd:camt.052.001.02" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
  <BkToCstmrAcctRpt>
    <Rpt>
      <Acct>
        <Id>
          <IBAN>DE91660908001410118</IBAN>
        </Id>
        <Ccy>EUR</Ccy>
      </Acct>
      <Bal>
        <Tp>
          <CdOrPrtry>
            <Cd>CLBD</Cd>
          </CdOrPrtry>
        </Tp>
        <Amt Ccy="EUR">1234.56</Amt>
        <CdtDbtInd>CRDT</CdtDbtInd>
        <Dt>
          <Dt>2024-06-27</Dt>
        </Dt>
      </Bal>
      <Ntry>
        <Amt Ccy="EUR">50.00</Amt>
        <CdtDbtInd>DBIT</CdtDbtInd>
        <Sts>
          <Cd>BOOK</Cd>
        </Sts>
        <BookgDt>
          <Dt>2024-06-27</Dt>
        </BookgDt>
        <ValDt>
          <Dt>2024-06-27</Dt>
        </ValDt>
        <BkTxCd>
          <Prtry>
            <Cd>SEPA</Cd>
          </Prtry>
        </BkTxCd>
        <NtryDtls>
          <TxDtls>
            <Refs>
              <EndToEndId>NOTPROVIDED</EndToEndId>
              <MndtId>MANDATE123</MndtId>
            </Refs>
            <RltdPties>
              <Dbtr>
                <Nm>Test Debitor</Nm>
              </Dbtr>
              <DbtrAcct>
                <Id>
                  <IBAN>DE89370400440532013000</IBAN>
                </Id>
              </DbtrAcct>
              <Cdtr>
                <Nm>Test Creditor</Nm>
              </Cdtr>
              <CdtrAcct>
                <Id>
                  <IBAN>DE91660908001410118</IBAN>
                </Id>
              </CdtrAcct>
            </RltdPties>
            <RltdAgts>
              <DbtrAgt>
                <FinInstnId>
                  <BIC>COBADEFFXXX</BIC>
                </FinInstnId>
              </DbtrAgt>
            </RltdAgts>
            <RmtInf>
              <Ustrd>Test Verwendungszweck Zeile 1</Ustrd>
              <Ustrd>Test Verwendungszweck Zeile 2</Ustrd>
            </RmtInf>
          </TxDtls>
        </NtryDtls>
      </Ntry>
      <Ntry>
        <Amt Ccy="EUR">100.00</Amt>
        <CdtDbtInd>CRDT</CdtDbtInd>
        <Sts>
          <Cd>BOOK</Cd>
        </Sts>
        <BookgDt>
          <Dt>2024-06-28</Dt>
        </BookgDt>
        <ValDt>
          <Dt>2024-06-28</Dt>
        </ValDt>
        <BkTxCd>
          <Prtry>
            <Cd>SEPA</Cd>
          </Prtry>
        </BkTxCd>
        <NtryDtls>
          <TxDtls>
            <Refs>
              <EndToEndId>NOTPROVIDED</EndToEndId>
            </Refs>
            <RltdPties>
              <Dbtr>
                <Nm>Einzahler Name</Nm>
              </Dbtr>
              <DbtrAcct>
                <Id>
                  <IBAN>DE12345678901234567890</IBAN>
                </Id>
              </DbtrAcct>
            </RltdPties>
            <RmtInf>
              <Ustrd>Gehalt Juni 2024</Ustrd>
            </RmtInf>
          </TxDtls>
        </NtryDtls>
      </Ntry>
    </Rpt>
  </BkToCstmrAcctRpt>
</Document>
""".trimIndent()

println("=" * 80)
println("CAMT Parser Test Script")
println("=" * 80)
println()
println("Test-XML length: ${testCamtXml.length} bytes")
println()

// Hinweis: Dieser Script ist ein Proof-of-Concept
// Für echtes Testen müssen wir ihn in Android Studio/Gradle ausführen
// weil der CustomCamtParser Android-APIs verwendet (XmlPullParser)

println("⚠️  WICHTIG:")
println("Dieser Script kann NICHT standalone ausgeführt werden!")
println("Er benötigt die Android-Build-Umgebung.")
println()
println("Nächste Schritte:")
println("1. JUnit-Test in app/src/test/ erstellen")
println("2. Oder: Android Instrumented Test in app/src/androidTest/")
println("3. Oder: Standalone Kotlin-App mit hbci4java + XmlPullParser")
println()
println("Empfehlung: JUnit-Test für schnelles Feedback")
