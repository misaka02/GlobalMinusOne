# Kotlinx Serialization consumer rules (common + r8) are bundled by the library.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# Keep AndroidManifest components and Activities.
-keep public class com.example.globalminusone.service.OverlayService
-keep public class com.example.globalminusone.service.GlobalAccessibilityService
-keep public class com.example.globalminusone.service.MinusOneTileService
-keep public class com.example.globalminusone.service.BootReceiver
-keep public class com.example.globalminusone.MainActivity
-keep public class com.example.globalminusone.WidgetPickerActivity

# These entry points are constructed by privileged-service loaders using class names.
# Keep only the loader contracts, not entire Shizuku/libsu or browser packages.
-keep class com.example.globalminusone.browser.ShizukuChromeService {
    public <init>(android.content.Context);
}
-keep class com.example.globalminusone.browser.RootChromeService {
    public <init>();
}
-keep class com.example.globalminusone.screenassistant.ShizukuBreenoService {
    public <init>(android.content.Context);
}
-keep class com.example.globalminusone.screenassistant.ShizukuPrivilegedService {
    public <init>(...);
    public static void main(java.lang.String[]);
}
-keep class com.example.globalminusone.xposed.SystemViewCaptureHook { *; }
-dontwarn de.robv.android.xposed.**
-keep class com.example.globalminusone.system.ShizukuRadioService {
    public <init>(android.content.Context);
}
-keep class com.example.globalminusone.system.ShizukuShellService {
    public <init>(android.content.Context);
}
# ChromeBridge's descriptor/transaction protocol uses fixed literals. Its remaining code is
# directly referenced; reflection targets in PrivilegedChromeEngine are Android framework
# classes on the boot classpath, so no blanket keep rule is necessary for them.

# Optimization flags
-allowaccessmodification
-repackageclasses 'com.example.globalminusone.internal'
