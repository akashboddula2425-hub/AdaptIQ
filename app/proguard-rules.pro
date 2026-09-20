# AdaptIQ ProGuard Rules
# MNN native libraries - keep JNI methods
-keep class com.adaptiq.tutor.engine.MnnBridge { *; }
-keep class com.adaptiq.tutor.engine.MnnBridge$NativeTokenCallback { *; }
-keep class com.adaptiq.tutor.engine.MnnBridge$Companion { *; }

# Room entities
-keep class com.adaptiq.tutor.data.KnowledgeGapEntity { *; }
-keep class com.adaptiq.tutor.data.KnowledgeGapFts { *; }

# Kotlin serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers class com.adaptiq.tutor.data.** {
    *** Companion;
}
-keepclasseswithmembers class com.adaptiq.tutor.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}
