# Add project specific ProGuard rules here.
-keepattributes *Annotation*
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Gson needs generic signatures and inner-class metadata
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses

# Portal JSON (Gson reflects on field names; renaming them would break the web page and the stored notices).
-keep class io.github.dulle88.atrium.portal.PortalStatus { *; }
-keep class io.github.dulle88.atrium.portal.PhotoInfo { *; }
-keep class io.github.dulle88.atrium.portal.SlideshowInfo { *; }
-keep class io.github.dulle88.atrium.portal.NoticeInfo { *; }
-keep class io.github.dulle88.atrium.portal.TvInfo { *; }
-keep class io.github.dulle88.atrium.portal.Notice { *; }
-keep class io.github.dulle88.atrium.portal.PortalStore$LegacyMessage { *; }
-keep class io.github.dulle88.atrium.portal.PortalServer$NoticeBody { *; }
-keep class io.github.dulle88.atrium.portal.PortalServer$OrderBody { *; }
-keep class io.github.dulle88.atrium.portal.PortalServer$SecondsBody { *; }
-keep class io.github.dulle88.atrium.portal.PortalServer$ClockBody { *; }
-keep class io.github.dulle88.atrium.portal.PortalServer$PinBody { *; }
-keep class io.github.dulle88.atrium.portal.PortalServer$PhonesBody { *; }
-keep class io.github.dulle88.atrium.portal.PhoneContact { *; }
# NanoHTTPD logs through java.util.logging; nothing reflective, but silence optional-dependency warnings.
-dontwarn fi.iki.elonen.**
