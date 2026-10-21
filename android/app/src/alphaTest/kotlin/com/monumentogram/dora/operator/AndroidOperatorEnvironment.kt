package com.monumentogram.dora.operator

import android.content.Context
import android.os.Build
import android.os.Process
import android.os.UserManager

/**
 * Public Android APIs cannot attest full-secondary topology on API 28–32. Host evidence is
 * required.
 */
internal fun androidOperatorEnvironment(context: Context): OperatorEnvironment {
    val users = checkNotNull(context.getSystemService(UserManager::class.java))
    val process = Process.myUserHandle()
    return OperatorEnvironment(
        context.packageName,
        // Android multiuser UID layout uses AID_USER_OFFSET=100000. No hidden UserHandle API.
        Process.myUid() / ANDROID_USER_UID_RANGE,
        users.getSerialNumberForUser(process),
        system = users.isSystemUser,
        // Managed-profile status is public only from API30; host topology is mandatory below it.
        managed = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && users.isManagedProfile,
        profile = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && users.isProfile,
        demo = users.isDemoUser,
        unlocked = users.isUserUnlocked,
    )
}

private const val ANDROID_USER_UID_RANGE = 100_000
