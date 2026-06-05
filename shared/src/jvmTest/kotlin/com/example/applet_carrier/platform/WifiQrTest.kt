package com.example.applet_carrier.platform

import kotlin.test.Test
import kotlin.test.assertEquals

class WifiQrTest {

    // ---- escaping ----

    @Test
    fun escape_semicolonAndBackslash() {
        assertEquals("a\\;b", escapeWifi("a;b"))
        assertEquals("a\\\\b", escapeWifi("a\\b"))
    }

    @Test
    fun escape_colonCommaQuote() {
        assertEquals("a\\:b\\,c\\\"d", escapeWifi("a:b,c\"d"))
    }

    @Test
    fun escape_plainUnchanged() = assertEquals("PlainPass1", escapeWifi("PlainPass1"))

    // ---- payload ----

    @Test
    fun payload_wpa() {
        assertEquals("WIFI:T:WPA;S:MyNet;P:secret;;", buildWifiPayload("MyNet", "secret", WifiAuth.WPA, hidden = false))
    }

    @Test
    fun payload_open_omitsPassword() {
        assertEquals("WIFI:T:nopass;S:Cafe;;", buildWifiPayload("Cafe", "ignored", WifiAuth.NONE, hidden = false))
    }

    @Test
    fun payload_hidden_addsFlag() {
        assertEquals("WIFI:T:WPA;S:Net;P:pw;H:true;;", buildWifiPayload("Net", "pw", WifiAuth.WPA, hidden = true))
    }

    @Test
    fun payload_escapesSpecialChars() {
        assertEquals(
            "WIFI:T:WPA;S:Guest\\;Net;P:p\\:w\\,d;;",
            buildWifiPayload("Guest;Net", "p:w,d", WifiAuth.WPA, hidden = false),
        )
    }

    // ---- render ----

    @Test
    fun render_producesSquareImageOfRequestedSize() {
        val img = WifiQr.render("WIFI:T:WPA;S:x;P:y;;", size = 256)
        assertEquals(256, img.width)
        assertEquals(256, img.height)
    }
}
