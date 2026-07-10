# kotlinx-serialization: standard consumer keeps (no @Serializable types in this app today,
# but keep the generated serializer lookup machinery safe if that changes).
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class bd.sicip.qavisit.**$$serializer { *; }
-keepclassmembers class bd.sicip.qavisit.** {
    *** Companion;
}
-keepclasseswithmembers class bd.sicip.qavisit.** {
    kotlinx.serialization.KSerializer serializer(...);
}
