# Add project specific ProGuard rules here.
-keepattributes *Annotation*
-keep class de.mybudgets.app.data.model.** { *; }
-keep class de.mybudgets.app.data.api.dto.** { *; }

# Keep all hbci4java classes - the library loads passport implementations via
# Class.forName() (e.g. HBCIPassportPinTan), so they must not be stripped by R8.
-keep class org.kapott.hbci.** { *; }
-dontwarn org.kapott.hbci.**

# hbci4java ships pre-obfuscated using short package names (j2, F2, C0, G2, N2, U1, q2, …).
# The library's CAMT parser resolves localisation resources via ResourceBundle.getBundle("j2.g").
# If R8 strips the original j2.g ResourceBundle subclass (not seeing it used by name) and then
# reuses the freed name "j2.g" for another, unrelated class, Android's ResourceBundle lookup
# finds that non-ResourceBundle class and throws:
#   ClassCastException: j2.g cannot be cast to ResourceBundle  →  Error parsing CAMT document
# Fix: keep every class in these pre-obfuscated packages so R8 preserves their names and
# cannot reassign those package/class names to renamed app or library classes.
-keep class j2.** { *; }
-keep class F2.** { *; }
-keep class C0.** { *; }
-keep class G2.** { *; }
-keep class N2.** { *; }
-keep class U1.** { *; }
-keep class q2.** { *; }

# NonValidatingDocumentBuilderFactory is registered as the XML parser factory at runtime
# via System.setProperty("javax.xml.parsers.DocumentBuilderFactory", <className>).
# DocumentBuilderFactory.newInstance() then instantiates it reflectively via
# Class.forName(name).newInstance(), which requires a public no-arg constructor.
# Without this rule R8 removes the constructor, causing InstantiationException.
-keep class de.mybudgets.app.data.banking.NonValidatingDocumentBuilderFactory {
    public <init>();
}
