# MacroMandate Sovereign ProGuard Rules

# 1. Keep Data Models (Prevents Gson/Room from failing to map fields)
-keep class com.sharek.macromandate.model.** { *; }
-keep class com.sharek.macromandate.network.** { *; }
-keep class com.sharek.macromandate.data.local.** { *; }

# 2. Retrofit & OkHttp
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepattributes Signature, InnerClasses, EnclosingMethod

# 3. Gson
-keepattributes *Annotation*
-keep class com.google.gson.reflect.TypeToken
-keep class * extends com.google.gson.TypeAdapter

# 4. Room
-dontwarn androidx.room.**

# 5. Biometric API
-dontwarn androidx.biometric.**
