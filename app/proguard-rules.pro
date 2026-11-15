# Kotlinx Serialization rules
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class com.example.globalminusone.data.**$$serializer { *; }
-keepclassmembers class com.example.globalminusone.data.** {
    *** Companion;
}
-keepclasseswithmembers class com.example.globalminusone.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep @kotlinx.serialization.Serializable class com.example.globalminusone.data.** { *; }

# Keep Android Services, Receivers, and Activities
-keep public class com.example.globalminusone.service.** { *; }
-keep public class com.example.globalminusone.MainActivity
-keep public class com.example.globalminusone.WidgetPickerActivity

# These two entry points are constructed by privileged-service loaders using class names.
# Keep only the loader contracts, not entire Shizuku/libsu or browser packages.
-keep class com.example.globalminusone.browser.ShizukuChromeService {
    public <init>(android.content.Context);
}
-keep class com.example.globalminusone.browser.RootChromeService {
    public <init>();
}
# Explicit entry invoked by the already-authorized Root shell's app_process.
-keep class com.example.globalminusone.split.VendorSplitEntry {
    public static void main(java.lang.String[]);
}
# ChromeBridge's descriptor/transaction protocol uses fixed literals. Its remaining code is
# directly referenced; reflection targets in PrivilegedChromeEngine are Android framework
# classes on the boot classpath, so no blanket keep rule is necessary for them.

# Optimization flags
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*
-allowaccessmodification
-repackageclasses 'com.example.globalminusone.internal'
