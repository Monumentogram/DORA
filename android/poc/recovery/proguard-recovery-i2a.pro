# REC-I2A local unpublished graph/R8 probe only.
# OD-14 permits exactly these three JSR-305 warning suppressions and no broader rule.
-dontwarn javax.annotation.Nullable
-dontwarn javax.annotation.concurrent.GuardedBy
-dontwarn javax.annotation.concurrent.ThreadSafe

# Keep the candidate dependency reachable so this probe exercises its full shrinker surface.
-keep class com.google.crypto.tink.** { *; }

# Keep the isolated REC-I2B boundary reachable even though the PoC has no harness entry point yet.
-keep class com.monumentogram.dora.poc.recovery.crypto.** { *; }
