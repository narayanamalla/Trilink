# Proguard rules for TriLink Android App
-keepattributes *Annotation*
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class com.trilink.app.data.database.** { *; }
-keep class com.trilink.app.data.model.** { *; }
