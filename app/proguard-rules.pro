# Add project specific ProGuard rules here.
-keepattributes *Annotation*
-keep class de.mybudgets.app.data.model.** { *; }
-keep class de.mybudgets.app.data.api.dto.** { *; }

# Keep all hbci4java classes - the library loads passport implementations via
# Class.forName() (e.g. HBCIPassportPinTan), so they must not be stripped by R8.
-keep class org.kapott.hbci.** { *; }
-dontwarn org.kapott.hbci.**

# hbci4java uses JAXB (jaxb-runtime:2.3.1 / jaxb-api:2.3.1) to parse CAMT XML.
# Two distinct failure modes require preserving the full JAXB classes (names + members):
#
# 1. ResourceBundle lookup failure (class-rename issue):
#    JAXB Messages Enum classes (e.g. com.sun.xml.bind.v2.runtime.Messages) load their
#    localised strings via:
#      static { rb = ResourceBundle.getBundle(Messages.class.getName()); }
#    When R8 renames Messages → e.g. j2.g, Class.getName() returns "j2.g".
#    ResourceBundle.getBundle("j2.g") then:
#      a. Finds the class j2.g (the Enum itself) – not a ResourceBundle subclass
#         → ClassCastException (caught internally by Java 17's ResourceBundle)
#      b. Falls back to j2/g.properties – which does NOT exist (R8 only renamed
#         the .class file, not the companion .properties file)
#      → MissingResourceException → hbci4java "Error parsing CAMT document"
#
# 2. Reflection-called method stripped (NoSuchMethodException):
#    javax.xml.bind.ContextFinder calls
#      ContextFactory.class.getMethod("createContext", Class[].class, Map.class)
#    via reflection to instantiate the JAXB context.  R8 with "-keepnames" preserves
#    class names but still strips methods that are not directly referenced in app code.
#    Result: NoSuchMethodException → javax.xml.bind.JAXBException → CAMT parse failure.
#
# Fix: use "-keep" (not "-keepnames") to preserve both the original class names AND all
# members (methods + fields) of com.sun.xml.bind and javax.xml.bind, so that:
#  • Class.getName() returns the fully-qualified original name (ResourceBundle path works)
#  • Class.getMethod("createContext", ...) succeeds (method is not stripped by R8)
-keep class com.sun.xml.bind.** { *; }
-keep class javax.xml.bind.** { *; }

# NonValidatingDocumentBuilderFactory is registered as the XML parser factory at runtime
# via System.setProperty("javax.xml.parsers.DocumentBuilderFactory", <className>).
# DocumentBuilderFactory.newInstance() then instantiates it reflectively via
# Class.forName(name).newInstance(), which requires a public no-arg constructor.
# Without this rule R8 removes the constructor, causing InstantiationException.
-keep class de.mybudgets.app.data.banking.NonValidatingDocumentBuilderFactory {
    public <init>();
}
