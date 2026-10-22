-keep class com.pedro.** { *; }
-dontwarn com.pedro.**
-keep class id.distream.studio.model.** { *; }
-keep class id.distream.studio.saas.** { *; }
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
-dontwarn org.bouncycastle.**
