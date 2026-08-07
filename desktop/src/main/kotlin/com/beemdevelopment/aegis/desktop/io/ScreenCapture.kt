package com.beemdevelopment.aegis.desktop.io

import com.beemdevelopment.aegis.util.QrCodes
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import java.awt.Robot
import java.awt.image.BufferedImage

/** Finds a QR code shown somewhere on screen, the desktop stand-in for a phone camera. */
object ScreenCapture {

    /**
     * @return the decoded text, "" if no code was found, or null if the screen could not be
     *   captured. Under Wayland a `Robot` grab only sees XWayland clients, if it works at all.
     */
    fun findQrCodeOnScreen(): String? {
        if (GraphicsEnvironment.isHeadless()) {
            return null
        }

        val screens = try {
            GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices
        } catch (e: Throwable) {
            return null
        }

        var captured = false
        for (screen in screens) {
            val image = capture(screen.defaultConfiguration.bounds) ?: continue
            captured = true

            val result = try {
                QrCodes.decodeFromImage(image)
            } catch (e: QrCodes.DecodeError) {
                null
            }

            if (result != null) {
                return result.text
            }
        }

        return if (captured) "" else null
    }

    private fun capture(bounds: Rectangle): BufferedImage? = try {
        Robot().createScreenCapture(bounds)
    } catch (e: Throwable) {
        // SecurityException, AWTException, or a compositor that refuses the grab.
        null
    }
}
