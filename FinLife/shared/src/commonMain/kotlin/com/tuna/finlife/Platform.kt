package com.tuna.finlife

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform