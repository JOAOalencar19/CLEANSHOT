# metadata-extractor / xmpcore use reflection-free code; keep XML parser service names.
-dontwarn javax.xml.**
-dontwarn org.w3c.dom.**
-keep class com.adobe.internal.xmp.** { *; }
-keep class com.drew.** { *; }
