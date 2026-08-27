# AmniShield ProGuard & R8 Optimization Rules - Production v0.16.0

# -----------------------------------------------------------------------------
# Optimization & Code Shrinking
# -----------------------------------------------------------------------------
-optimizationpasses 5
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-verbose

# Preserve line numbers and source file names for production stack trace symbolication
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep essential annotations and type signatures
-keepattributes *Annotation*,Signature,Exceptions,InnerClasses,EnclosingMethod

# -----------------------------------------------------------------------------
# AmniShield Core Data Models & State (Serialized / Reflectively accessed)
# -----------------------------------------------------------------------------
-keep class com.alhaq.amnishield.data.** { *; }
-keepclassmembers class com.alhaq.amnishield.data.** { *; }
-keep class com.alhaq.amnishield.data.sync.** { *; }
-keepclassmembers class com.alhaq.amnishield.data.sync.** { *; }

-keep class com.alhaq.amnishield.ui.state.** { *; }
-keepclassmembers class com.alhaq.amnishield.ui.state.** { *; }

-keep class com.alhaq.amnishield.premium.** { *; }
-keepclassmembers class com.alhaq.amnishield.premium.** { *; }

# -----------------------------------------------------------------------------
# Android System Components & Services
# -----------------------------------------------------------------------------
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.view.View

# Accessibility Service & VPN Service
-keep class com.alhaq.amnishield.services.** { *; }
-keepclassmembers class com.alhaq.amnishield.services.** { *; }
-keep class * extends android.net.VpnService { *; }

# Device Admin Receiver
-keep class * extends android.app.admin.DeviceAdminReceiver { *; }

# ViewBinding
-keep class com.alhaq.amnishield.databinding.** { *; }

# -----------------------------------------------------------------------------
# Third-Party Libraries & Frameworks
# -----------------------------------------------------------------------------

# Google Play Services & Google Sign-In
-keep class com.google.android.gms.auth.api.signin.** { *; }
-dontwarn com.google.android.gms.**

# Google Play Billing
-keep class com.android.billingclient.api.** { *; }
-keepclassmembers class com.android.billingclient.api.** { *; }

# Gson Serialization
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# MPAndroidChart
-keep class com.github.mikephil.charting.** { *; }
-dontwarn com.github.mikephil.charting.**

# Native (JNI) methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Standard Android Interfaces
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}

-keepnames class * implements java.io.Serializable
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    !private <fields>;
    !private <methods>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# WorkManager, Room & App Startup
-keep class androidx.work.** { *; }
-keep class * extends androidx.work.ListenableWorker { *; }
-dontwarn androidx.work.**
-dontwarn androidx.room.**
-dontwarn androidx.startup.**

# -----------------------------------------------------------------------------
# Log Stripping (Removes debug and verbose logs from release builds)
# -----------------------------------------------------------------------------
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
}

# Suppress harmless warnings from Conscrypt / BouncyCastle
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
