package com.instrument.data.alarm

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.*
import com.instrument.domain.model.GasLevel
import com.instrument.domain.repository.SettingsRepository

// Android向けアラーム実装（振動 + ビープ音）
// settingsRepo を参照してアラーム音・振動の ON/OFF を動的に制御する
class AndroidAlarmController(
    private val context: Context,
    private val settingsRepo: SettingsRepository,
) : AlarmController {

    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vm = context.getSystemService(android.os.VibratorManager::class.java)
        vm.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    private var toneGenerator: ToneGenerator? = null

    override fun trigger(level: GasLevel) {
        dismiss()
        val settings = settingsRepo.settings.value
        when (level) {
            GasLevel.WARNING  -> {
                if (settings.vibrationEnabled) vibrate(longArrayOf(0, 100), -1)
                if (settings.soundEnabled) playTone(ToneGenerator.TONE_PROP_BEEP, 500)
            }
            GasLevel.DANGER   -> {
                if (settings.vibrationEnabled) vibrate(longArrayOf(0, 100, 100, 100, 100, 100), -1)
                if (settings.soundEnabled) playTone(ToneGenerator.TONE_PROP_BEEP2, 1000)
            }
            GasLevel.CRITICAL -> {
                if (settings.vibrationEnabled) vibrate(longArrayOf(0, 200, 100), 0)
                if (settings.soundEnabled) playTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 3000)
            }
            GasLevel.SAFE -> Unit
        }
    }

    override fun dismiss() {
        vibrator.cancel()
        toneGenerator?.stopTone()
        toneGenerator?.release()
        toneGenerator = null
    }

    override fun release() = dismiss()

    private fun vibrate(pattern: LongArray, repeat: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = VibrationEffect.createWaveform(pattern, repeat)
            vibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, repeat)
        }
    }

    private fun playTone(tone: Int, durationMs: Int) {
        runCatching {
            toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 80)
            toneGenerator?.startTone(tone, durationMs)
        }
    }
}
