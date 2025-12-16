# DJI SDK ProGuard Rules
-keepclassmembers class * extends android.app.Service
-keepclassmembers public class * extends android.content.BroadcastReceiver
-keepclassmembers public class * extends android.app.Activity
-keepclassmembers public class * extends android.app.Application

# DJI
-keep class dji.** { *; }
-keep class com.dji.** { *; }
-keep class com.secneo.** { *; }
-dontwarn dji.**
-dontwarn com.dji.**
