# DSH Mobile release rules.

# kotlinx.serialization: generated serializers are looked up through companions.
-keepattributes *Annotation*, InnerClasses, Signature
-keepclassmembers class **$$serializer {
    *** descriptor;
}
-keepclasseswithmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.deepseek.dsh.core.model.**$$serializer { *; }
-keep,includedescriptorclasses class com.deepseek.dsh.core.protocol.**$$serializer { *; }
-keep,includedescriptorclasses class com.deepseek.dsh.core.bridge.**$$serializer { *; }

# Room generates implementations by name.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep @androidx.room.Entity class * { *; }
-dontwarn androidx.room.paging.**

# OkHttp platform hooks.
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Plugin SDK types are loaded by the companion APK, keep the public surface stable.
-keep public class com.deepseek.dsh.plugin.nativesdk.** { public *; }
