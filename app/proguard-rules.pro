# Life Pilot ProGuard / R8 Keep Rules

# Preserve domain model fields & no-arg constructors for Cloud Firestore toObject() reflection
-keepclassmembers class com.example.domain.model.** {
    <init>(...);
    <fields>;
}
-keep class com.example.domain.model.** { *; }

# Preserve Gson / JSON / Firebase Annotations
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
