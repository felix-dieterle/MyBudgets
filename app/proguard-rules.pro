# Add project specific ProGuard rules here.
-keepattributes *Annotation*
-keep class de.mybudgets.app.data.model.** { *; }
-keep class de.mybudgets.app.data.api.dto.** { *; }

# Keep all hbci4java classes - the library loads passport implementations via
# Class.forName() (e.g. HBCIPassportPinTan), so they must not be stripped by R8.
-keep class org.kapott.hbci.** { *; }
-dontwarn org.kapott.hbci.**

# hbci4java uses JAXB (jaxb-runtime:2.3.1 / jaxb-api:2.3.1) to parse CAMT XML.
# The JAXB runtime contains several Messages Enum classes (e.g.
# com.sun.xml.bind.v2.runtime.Messages) that load their localised strings from a companion
# .properties file using the pattern:
#
#   static { rb = ResourceBundle.getBundle(Messages.class.getName()); }
#
# When R8 renames Messages → e.g. j2.g, Class.getName() returns "j2.g".
# ResourceBundle.getBundle("j2.g") then:
#   1. Finds the class j2.g (the Enum itself) – which is NOT a ResourceBundle subclass
#      → ClassCastException (caught internally by Java 17's ResourceBundle)
#   2. Falls back to looking for j2/g.properties – which does NOT exist (R8 only renamed
#      the .class file, not the companion .properties file)
#   → MissingResourceException wrapping the ClassCastException
#   → hbci4java HBCI_Exception: Error parsing CAMT document
#
# Fix: keep the original names AND all members of all com.sun.xml.bind and javax.xml.bind
# classes so that:
# 1. Class.getName() still returns the fully-qualified original name, allowing ResourceBundle
#    to fall back to the companion .properties file at its original path (e.g.
#    com/sun/xml/bind/v2/runtime/Messages.properties), which IS packaged in the APK.
# 2. Methods called via reflection (e.g. ContextFactory.createContext, invoked by
#    javax.xml.bind.ContextFinder via Class.getMethod()) are not removed by R8.
#    Without this, JAXB.unmarshal() throws:
#    NoSuchMethodException: com.sun.xml.bind.v2.ContextFactory.createContext(...)
-keep class com.sun.xml.bind.** { *; }
-keep class javax.xml.bind.** { *; }

# javax.activation (Jakarta Activation Framework / JAF) is used by hbci4java / jaxb-runtime
# for MIME-type handling (MailcapCommandMap etc.). The implementation class
# com.sun.activation.registries.LogSupport is NOT shipped with Android, so R8 reports it as
# a missing class. Suppress the warning since the code path that references it is never
# executed on Android (no desktop file-type registry is needed).
-dontwarn com.sun.activation.**
-dontwarn javax.activation.**

# java.awt.* (AWT) is not available on Android. jaxb-runtime references it in
# RuntimeBuiltinLeafInfoImpl for desktop image/rendering support that is never
# reached on Android.
#
# RuntimeBuiltinLeafInfoImpl's *static initializer* contains a direct class literal
# "Image.class", which is resolved at class-loading time (not lazily at method call
# time). Without java.awt.Image being resolvable, loading the class throws:
#   java.lang.NoClassDefFoundError: Failed resolution of: Ljava/awt/Image;
#
# Fix: app/libs/java-awt-stub.jar provides a minimal pre-compiled stub so ART can
# resolve the class literal at load time. Android ART looks up missing classes in the
# app DEX after failing to find them in the boot classpath (java.awt is absent from
# Android's boot classpath entirely, so there is no split-package conflict at runtime).
# The stub is pre-compiled (javac --release 8) rather than kept as a .java source file
# to avoid the Java-17 module-system compile error "package exists in another module:
# java.desktop" that kapt raises when it finds source in the java.awt package.
#
# Other java.awt.* references (BufferedImage, Component, MediaTracker, Graphics, …)
# live only in method bodies that are never called during CAMT XML parsing; ART's soft
# verification allows those methods to be loaded without error as long as they are not
# invoked. Suppress the R8 missing-class warnings for those remaining references.
-dontwarn java.awt.**

# java.beans.* is a Java SE desktop API not available on Android. jaxb-runtime
# references java.beans.Introspector in GetterSetterPropertySeed. Never executed on Android.
-dontwarn java.beans.**

# javax.imageio.* is a Java SE desktop image I/O API not available on Android.
# jaxb-runtime references it in RuntimeBuiltinLeafInfoImpl for image serialization.
# That code path is never reached on Android.
-dontwarn javax.imageio.**

# javax.lang.model.* is a Java compiler API not available on Android. jaxb-runtime
# references javax.lang.model.SourceVersion in NameConverter. Never executed on Android.
-dontwarn javax.lang.model.**

# javax.xml.stream.* (StAX API) is not available on Android. jaxb-runtime references
# StAX classes (XMLStreamReader, XMLStreamWriter, XMLEventReader, XMLEventWriter, etc.)
# for streaming XML processing. The HKCAZ CAMT parser in hbci4java uses DOM, not StAX,
# so these code paths are never reached on Android.
-dontwarn javax.xml.stream.**

# NonValidatingDocumentBuilderFactory is registered as the XML parser factory at runtime
# via System.setProperty("javax.xml.parsers.DocumentBuilderFactory", <className>).
# DocumentBuilderFactory.newInstance() then instantiates it reflectively via
# Class.forName(name).newInstance(), which requires a public no-arg constructor.
# Without this rule R8 removes the constructor, causing InstantiationException.
-keep class de.mybudgets.app.data.banking.NonValidatingDocumentBuilderFactory {
    public <init>();
}
