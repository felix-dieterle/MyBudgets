# Add project specific ProGuard rules here.
-keepattributes *Annotation*
-keep class de.mybudgets.app.data.model.** { *; }
-keep class de.mybudgets.app.data.api.dto.** { *; }

# Keep all hbci4java classes - the library loads passport implementations via
# Class.forName() (e.g. HBCIPassportPinTan), so they must not be stripped by R8.
-keep class org.kapott.hbci.** { *; }
-dontwarn org.kapott.hbci.**
