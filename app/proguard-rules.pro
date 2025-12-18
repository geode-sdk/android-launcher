# Add wildcard keep rules to all of the library code in use
-keep class org.fmod.** { *; }
-keep class org.cocos2dx.lib.** { *; }
-keep class com.customRobTop.** { *; }

# note: if you're going to add more rules, consider the @Keep annotation
# this should really only be kept to non-custom code
-keep class java.lang.management.** { *; }
