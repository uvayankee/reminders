# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# We are an open-source app, so we specifically disable obfuscation to keep stack traces readable
# and simplify debugging. We still benefit from R8 removing unused code (shrinking) and optimizing.
-dontobfuscate

# Ensure fundamental Kotlin and Coroutines structures remain intact if accessed via reflection
-keep class kotlin.coroutines.Continuation
-keep class kotlinx.coroutines.** { *; }

# Koin and Room generally ship with their own consumer proguard rules, 
# so we don't need extensive manual rules for them out of the box.
