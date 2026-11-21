# JPush/JCore use JNI and reflective callbacks. Keep the vendor boundary intact;
# the application remains optimizable and resource shrinking stays enabled.
-keep class cn.jpush.** { *; }
-keep class cn.jiguang.** { *; }
-keep class cn.jmessage.** { *; }
-keep class com.qingcheng.ordering.QingchengJCommonService { *; }
-keep class com.qingcheng.ordering.QingchengJPushReceiver { *; }
-keep class com.qingcheng.ordering.JPushReceiver { *; }
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*
# Optional vendor integrations are absent from this application's dependency graph.
-dontwarn cn.jpush.**
-dontwarn cn.jiguang.**
