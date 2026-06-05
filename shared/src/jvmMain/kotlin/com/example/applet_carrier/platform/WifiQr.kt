package com.example.applet_carrier.platform

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.awt.image.BufferedImage

/**
 * Wi-Fi QR payload + rendering. The payload is the de-facto `WIFI:` URI scanned by phone
 * cameras. Escaping of the special characters (`\ ; , : "`) is the pure, testable part;
 * rendering uses ZXing.
 */

enum class WifiAuth(val token: String, val label: String) {
    WPA("WPA", "WPA/WPA2/WPA3"),
    NONE("nopass", "None (open)"),
}

/** Escape the MeCard-style special characters used by the WIFI: scheme. */
internal fun escapeWifi(value: String): String = buildString {
    for (c in value) {
        if (c == '\\' || c == ';' || c == ',' || c == ':' || c == '"') append('\\')
        append(c)
    }
}

/** Build the `WIFI:T:..;S:..;P:..;H:..;;` payload. Password is omitted for open networks. */
internal fun buildWifiPayload(ssid: String, password: String, auth: WifiAuth, hidden: Boolean): String {
    val sb = StringBuilder("WIFI:")
    sb.append("T:").append(auth.token).append(';')
    sb.append("S:").append(escapeWifi(ssid)).append(';')
    if (auth != WifiAuth.NONE) sb.append("P:").append(escapeWifi(password)).append(';')
    if (hidden) sb.append("H:true;")
    sb.append(';')
    return sb.toString()
}

object WifiQr {
    /** Render [text] to a square QR [BufferedImage] (black on white, fixed M error correction). */
    fun render(text: String, size: Int = 512, margin: Int = 2): BufferedImage {
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to margin,
            EncodeHintType.CHARACTER_SET to "UTF-8",
        )
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints)
        val black = 0xFF000000.toInt()
        val white = 0xFFFFFFFF.toInt()
        val image = BufferedImage(matrix.width, matrix.height, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) {
                image.setRGB(x, y, if (matrix.get(x, y)) black else white)
            }
        }
        return image
    }
}
