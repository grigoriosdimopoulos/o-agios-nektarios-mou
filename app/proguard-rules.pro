# Firestore maps documents onto these model classes reflectively, so their
# fields and no-arg constructors must survive shrinking.
-keep class gr.agiosnektarios.village.core.model.** { *; }
-keepclassmembers class gr.agiosnektarios.village.core.model.** {
    <init>();
}

# Firestore / Firebase
-keepattributes Signature, *Annotation*, EnclosingMethod, InnerClasses
-dontwarn com.google.firebase.**

# Kotlin metadata used by reflection in Firestore's POJO mapper
-keep class kotlin.Metadata { *; }
