package com.beemdevelopment.aegis.desktop.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.beemdevelopment.aegis.otp.HotpInfo
import com.beemdevelopment.aegis.otp.MotpInfo
import com.beemdevelopment.aegis.otp.OtpInfo
import com.beemdevelopment.aegis.otp.SteamInfo
import com.beemdevelopment.aegis.otp.TotpInfo
import com.beemdevelopment.aegis.otp.YandexInfo
import com.beemdevelopment.aegis.vault.VaultEntry
import kotlinx.coroutines.delay

/** One clock for the whole entry list, so every row agrees on the time and ticks together. */
@Composable
fun rememberOtpClock(tickMillis: Long = 200): State<Long> {
    val time = remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(tickMillis) {
        while (true) {
            time.longValue = System.currentTimeMillis()
            delay(tickMillis)
        }
    }

    return time
}

data class OtpState(
    val code: String,
    val nextCode: String?,
    /** 1.0 just after a rotation, falling to 0.0 as it expires. Null if the code does not expire. */
    val progress: Float?,
    val secondsRemaining: Int?,
    val error: String?,
)

/**
 * Generates the code for an entry. Errors come back in [OtpState.error] rather than being thrown,
 * so one malformed entry cannot take down the whole list.
 */
fun otpStateFor(entry: VaultEntry, nowMillis: Long, includeNext: Boolean): OtpState {
    val info = entry.info
    return try {
        when (info) {
            is HotpInfo -> OtpState(
                code = info.otp,
                nextCode = null,
                progress = null,
                secondsRemaining = null,
                error = null,
            )

            is TotpInfo -> {
                val period = info.period
                val seconds = nowMillis / 1000
                val elapsed = seconds % period
                val remaining = period - elapsed
                val millisRemaining = remaining * 1000 - (nowMillis % 1000)

                OtpState(
                    code = otpAt(info, seconds),
                    nextCode = if (includeNext) otpAt(info, seconds + period) else null,
                    progress = (millisRemaining.toFloat() / (period * 1000f)).coerceIn(0f, 1f),
                    secondsRemaining = remaining.toInt(),
                    error = null,
                )
            }

            else -> OtpState(info.otp, null, null, null, null)
        }
    } catch (e: Exception) {
        OtpState(code = "", nextCode = null, progress = null, secondsRemaining = null, error = e.message ?: "error")
    }
}

// Only the TotpInfo subclasses expose a time-taking overload, and each computes differently.
private fun otpAt(info: TotpInfo, seconds: Long): String = when (info) {
    is SteamInfo -> info.getOtp(seconds)
    is YandexInfo -> info.getOtp(seconds)
    is MotpInfo -> info.getOtp(seconds)
    else -> info.getOtp(seconds)
}

/** Splits a code into groups for readability, as on Android. A ragged split is left alone. */
fun groupCode(code: String, groupSize: Int): String {
    if (groupSize <= 0 || code.length <= groupSize || code.length % groupSize != 0) {
        return code
    }
    return code.chunked(groupSize).joinToString(" ")
}

@Composable
fun rememberRevealState(revealSeconds: Int): RevealState {
    val state = remember(revealSeconds) { RevealState(revealSeconds) }
    LaunchedEffect(state) {
        while (true) {
            delay(1000)
            state.expireStale()
        }
    }
    return state
}

class RevealState(private val revealSeconds: Int) {
    private var revealedAt by mutableStateOf<Map<java.util.UUID, Long>>(emptyMap())

    fun isRevealed(uuid: java.util.UUID): Boolean = revealedAt.containsKey(uuid)

    fun reveal(uuid: java.util.UUID) {
        revealedAt = revealedAt + (uuid to System.currentTimeMillis())
    }

    fun hide(uuid: java.util.UUID) {
        revealedAt = revealedAt - uuid
    }

    fun hideAll() {
        revealedAt = emptyMap()
    }

    internal fun expireStale() {
        if (revealedAt.isEmpty() || revealSeconds <= 0) {
            return
        }
        val cutoff = System.currentTimeMillis() - revealSeconds * 1000L
        val remaining = revealedAt.filterValues { it > cutoff }
        if (remaining.size != revealedAt.size) {
            revealedAt = remaining
        }
    }
}
