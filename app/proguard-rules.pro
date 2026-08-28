# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

-dontwarn com.gemalto.jp2.**
-dontwarn com.tom_roush.pdfbox.**

# PDFBox: Reflection requires keeping the constructors of all PDFont subclasses
-keepclassmembers class * extends com.tom_roush.pdfbox.pdmodel.font.PDFont {
    <init>(com.tom_roush.pdfbox.cos.COSDictionary);
}

# PDFBox: Reflection requires keeping PDAnnotation subclasses
-keepclassmembers class * extends com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotation {
    <init>(com.tom_roush.pdfbox.cos.COSDictionary);
}

# Ensure specific PDFont types are kept to allow them to be instantiated
-keep class com.tom_roush.pdfbox.pdmodel.font.PDType1Font
-keep class com.tom_roush.pdfbox.pdmodel.font.PDTrueTypeFont
-keep class com.tom_roush.pdfbox.pdmodel.font.PDType0Font
-keep class com.tom_roush.pdfbox.pdmodel.font.PDType3Font

# Compose reflection
-keepclassmembers class * {
    @androidx.compose.runtime.Composable *;
}

# Room reflections
-keep class * extends androidx.room.RoomDatabase {
    <init>();
}
-keep class * implements androidx.room.RoomDatabase$Callback {
    <init>();
}
