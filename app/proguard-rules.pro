# LIKHTAR release rules. Minification is disabled until beta hardening (Phase 5).

# Медіатека (SMB, smbj 0.15): Kerberos (org.ietf.jgss) і EL-фільтри mbassador на Android не
# потрібні — лише попередження. Обробники подій mbassador знаходить рефлексією за @Handler.
-dontwarn javax.el.**
-dontwarn org.ietf.jgss.**
-keepclassmembers class * { @net.engio.mbassy.listener.Handler *; }
-keep class net.engio.mbassy.** { *; }
-keep class com.hierynomus.** { *; }
