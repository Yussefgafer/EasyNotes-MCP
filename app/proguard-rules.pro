# Disable obfuscation globally for easier debugging of Reflection/Serialization issues
-dontobfuscate

# Keep essential parts
-keep class com.kin.easynotes.domain.model.Settings { *; }
-keep class io.modelcontextprotocol.** { *; }
-keep class kotlinx.serialization.** { *; }

# Google Play Billing
-keep class com.android.vending.billing.**

# Ktor/Netty/CIO
-dontwarn io.netty.**
-dontwarn org.apache.log4j.**
-dontwarn org.apache.logging.log4j.**
-dontwarn io.ktor.**
