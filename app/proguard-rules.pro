# Shizuku API is bundled by the app; keep it so the reflective call to
# Shizuku.newProcess (private static in 13.1.5) survives any future minification.
-keep class rikka.shizuku.Shizuku { *; }
-keep class rikka.shizuku.ShizukuRemoteProcess { *; }

# Shizuku's own consumer proguard rules are applied automatically.
