package java.awt;

/**
 * Minimal stub for java.awt.Image on Android.
 *
 * <p>JAXB runtime (com.sun.xml.bind.v2.model.impl.RuntimeBuiltinLeafInfoImpl) references
 * {@code Image.class} as a class literal in its static initializer to register a built-in
 * base64Binary handler for AWT images.  Since {@code java.awt} does not exist in Android's
 * boot classpath, loading {@code RuntimeBuiltinLeafInfoImpl} fails at runtime with
 * {@code NoClassDefFoundError: Failed resolution of: Ljava/awt/Image;}.
 *
 * <p>This stub makes the {@code java.awt.Image} class resolvable from the app's DEX so
 * that the JAXB static initializer can complete without error.  The actual image-handling
 * methods of {@code RuntimeBuiltinLeafInfoImpl} (which reference {@code BufferedImage},
 * {@code ImageIO}, etc.) are never invoked during CAMT XML parsing.
 */
public abstract class Image {
}
