package com.tuna.finlife.ui

enum class DeltaDirection(internal val prefix: String) {
    Up("▲ +"),
    Down("▼ −"),
    Unchanged(""),
    ;

    companion object {
        fun fromAmount(amount: Double): DeltaDirection = when {
            amount > 0.0 -> Up
            amount < 0.0 -> Down
            else -> Unchanged
        }
    }
}

internal fun formatDelta(amount: String, direction: DeltaDirection): String = direction.prefix + amount
