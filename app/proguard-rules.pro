# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in C:\Users\<username>\AppData\Local\Android\sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.

-keepattributes *Annotation*
-keepclassmembers class * {
    @org.checkerframework.checker.nullness.qual.* <fields>;
}

