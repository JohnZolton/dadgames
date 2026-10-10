# kotlinx.serialization: keep generated serializers for saved games and settings.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class **$$serializer { *; }
-keep class euchre.core.** { *; }
-keep class euchre.app.AppSettings { *; }
-keep class euchre.app.Stats { *; }
