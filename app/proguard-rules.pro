# Keep kotlinx.serialization metadata used by backup/restore.
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.mindtrace.app.**$$serializer { *; }
-keepclassmembers class com.mindtrace.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.mindtrace.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# SQLCipher native bindings.
-keep class net.zetetic.database.** { *; }
