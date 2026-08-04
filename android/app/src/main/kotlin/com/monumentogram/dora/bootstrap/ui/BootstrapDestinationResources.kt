package com.monumentogram.dora.bootstrap.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.monumentogram.dora.bootstrap.R
import com.monumentogram.dora.model.BootstrapDestination

@StringRes
internal fun BootstrapDestination.labelResource(): Int =
    when (this) {
        BootstrapDestination.HOME -> R.string.nav_home
        BootstrapDestination.HISTORY -> R.string.nav_history
        BootstrapDestination.TASKS -> R.string.nav_tasks
        BootstrapDestination.SETTINGS -> R.string.nav_settings
    }

@StringRes
internal fun BootstrapDestination.placeholderResource(): Int =
    when (this) {
        BootstrapDestination.HOME -> R.string.placeholder_home
        BootstrapDestination.HISTORY -> R.string.placeholder_history
        BootstrapDestination.TASKS -> R.string.placeholder_tasks
        BootstrapDestination.SETTINGS -> R.string.placeholder_settings
    }

@DrawableRes
internal fun BootstrapDestination.iconResource(): Int =
    when (this) {
        BootstrapDestination.HOME -> R.drawable.ic_bootstrap_home
        BootstrapDestination.HISTORY -> R.drawable.ic_bootstrap_history
        BootstrapDestination.TASKS -> R.drawable.ic_bootstrap_tasks
        BootstrapDestination.SETTINGS -> R.drawable.ic_bootstrap_settings
    }
