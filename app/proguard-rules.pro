# Add project specific ProGuard rules here.
-keepattributes *Annotation*
-keep class de.mybudgets.app.data.model.** { *; }
-keep class de.mybudgets.app.data.api.dto.** { *; }

# Keep all hbci4java classes - the library loads passport implementations via
# Class.forName() (e.g. HBCIPassportPinTan), so they must not be stripped by R8.
-keep class org.kapott.hbci.** { *; }
-dontwarn org.kapott.hbci.**

# NonValidatingDocumentBuilderFactory is registered as the XML parser factory at runtime
# via System.setProperty("javax.xml.parsers.DocumentBuilderFactory", <className>).
# DocumentBuilderFactory.newInstance() then instantiates it reflectively via
# Class.forName(name).newInstance(), which requires a public no-arg constructor.
# Without this rule R8 removes the constructor, causing InstantiationException.
-keep class de.mybudgets.app.data.banking.NonValidatingDocumentBuilderFactory {
    public <init>();
}
