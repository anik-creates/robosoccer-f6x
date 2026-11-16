# Keep native JNI methods
-keepclasseswithmembernames class * {
    native <methods>;
}

-keep class com.anik.f6xcontroller.transport.** { *; }
