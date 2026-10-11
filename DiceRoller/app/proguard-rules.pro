# kotlinx.serialization — keep generated serializers for our @Serializable models.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.example.diceroller.**$$serializer { *; }
-keepclassmembers class com.example.diceroller.** { *** Companion; }
-keepclasseswithmembers class com.example.diceroller.** {
    kotlinx.serialization.KSerializer serializer(...);
}
