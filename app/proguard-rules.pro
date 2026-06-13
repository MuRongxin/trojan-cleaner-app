# ── 极致混淆规则 ──
# 尽可能混淆所有类名、方法名、字段名

# Keep the application entry point
-keep class com.shortvideocleaner.app.MainActivity { *; }

# Keep JavaMail (reflection-based)
-keep class javax.mail.** { *; }
-keep class com.sun.mail.** { *; }
-dontwarn javax.mail.**
-dontwarn com.sun.mail.**

# Keep Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Keep Android framework entry points
-keep class * extends android.app.Activity
-keep class * extends android.app.Application
-keep class * extends android.app.Service
-keep class * extends android.content.BroadcastReceiver
-keep class * extends android.content.ContentProvider

# ── 激进混淆 ──
# 混淆所有未 keep 的类
-repackageclasses 'a'
-allowaccessmodification
-overloadaggressively
-useuniqueclassmembernames

# 移除所有日志
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}

# 保持 R 类和资源 ID
-keepclassmembers class **.R$* {
    public static <fields>;
}
