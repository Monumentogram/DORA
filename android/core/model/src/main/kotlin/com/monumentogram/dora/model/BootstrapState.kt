package com.monumentogram.dora.model

enum class BootstrapState {
    READY;

    companion object {
        fun current(): BootstrapState = READY
    }
}
