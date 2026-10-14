# Release builds use R8. These rules keep the build from failing on optional classes that
# Google Tink (used by androidx.security:security-crypto) references but does not ship.
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn org.checkerframework.**
-dontwarn com.google.api.client.**
-dontwarn org.joda.time.**

# WebView JavaScript interfaces are not used; nothing extra to keep.
