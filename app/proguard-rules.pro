# kotlinx.serialization: conservar serializadores de los DTO
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.sistemasdeinformacion.proyecto.**$$serializer { *; }
-keepclassmembers class com.sistemasdeinformacion.proyecto.** {
    *** Companion;
}
-keepclasseswithmembers class com.sistemasdeinformacion.proyecto.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Ktor / Supabase
-dontwarn org.slf4j.**
-dontwarn io.ktor.**
-keep class io.ktor.** { *; }
