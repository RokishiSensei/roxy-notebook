# --- kotlinx.serialization ---------------------------------------------------
# Keep the generated serializers for the persisted model so JSON import/export and
# SharedPreferences round-tripping survive shrinking.
-keepattributes *Annotation*, InnerClasses, Signature, RuntimeVisibleAnnotations
-dontnote kotlinx.serialization.**

-keepclassmembers class com.lexi.wordbook.data.** {
    *** Companion;
}
-keepclasseswithmembers class com.lexi.wordbook.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.lexi.wordbook.data.**$$serializer { *; }
-keepclassmembers class com.lexi.wordbook.data.Word {
    <fields>;
}

# --- Android entry points reached through the framework ----------------------
-keep class com.lexi.wordbook.WordBookApp { *; }
-keep class com.lexi.wordbook.MainActivity { *; }
-keep class * extends androidx.lifecycle.ViewModel { *; }

# --- Compose / TTS noise -----------------------------------------------------
-dontwarn org.jetbrains.annotations.**
-dontwarn kotlinx.serialization.**
