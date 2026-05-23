package com.simplr.mykitta2

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform