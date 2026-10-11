# kotlinx.serialization: keep @Serializable classes and their generated serializers so saved games
# and settings still load after R8. Verify save/restore on a release build. [BEST_PRACTICES #17]
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keep @kotlinx.serialization.Serializable class * { *; }
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class **$$serializer { *; }
