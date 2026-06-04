package com.example.applet_carrier

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform