Plan: HBCI Response Interception für CAMT Bypass
Ziel: Raw CAMT XML vor JAXB-Parsing abfangen → direkt an CustomCamtParser
Schritte:
1. 
Callback erweitern (HbciCallback.log, Zeile 868-881)
- 
Wenn msg CAMT XML enthält (<?xml, camt.052/camt.053)
- 
XML extrahieren → in var interceptedCamtXml: String? speichern
- 
Log: "Intercepted CAMT: X chars"
2. 
Nach Job-Execute prüfen (fetchAccountStatement, nach Zeile 375)
- 
Falls hasCamtParsingError UND callback.interceptedCamtXml != null
- 
Log: "Using intercepted CAMT (X chars) instead of result.getFlatData()"
- 
Parse mit CustomCamtParser.parse(interceptedCamtXml)
- 
Nutze diese Transaktionen statt result.getFlatData()
3. 
Cleanup 
- 
callback.interceptedCamtXml = null nach jedem Job
Erwartung:
- 
Log zeigt: "Intercepted CAMT: ~171416 chars" (oder mehr?)
- 
Wenn BBBank wirklich nur 150 TX liefert → gleiches Ergebnis
- 
Wenn BBBank mehr liefert, JAXB aber abbricht → mehr TX!
Risk: CAMT XML könnte nicht in log() auftauchen (je nach hbci4java Log-Level)
Test: APK v314 bauen, Logs prüfen auf "Intercepted CAMT"