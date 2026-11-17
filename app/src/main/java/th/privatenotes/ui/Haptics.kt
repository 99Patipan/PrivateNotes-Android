package th.privatenotes.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * Vibrates through the Vibrator service directly, so it works even when the system's
 * "touch vibration" setting is off. Falls back to View haptics if there is no vibrator.
 */
object Haptics {
    @Suppress("DEPRECATION")
    private fun vibrator(context: Context): Vibrator? {
        val v = if (Build.VERSION.SDK_INT >= 31) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        return if (v != null && v.hasVibrator()) v else null
    }

    /** Short tick for a key press. */
    fun tap(view: View) {
        val v = vibrator(view.context)
        if (v == null) {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            return
        }
        val amplitude = if (v.hasAmplitudeControl()) 170 else VibrationEffect.DEFAULT_AMPLITUDE
        v.vibrate(VibrationEffect.createOneShot(14, amplitude))
    }

    /** Double buzz for a wrong PIN. */
    fun error(view: View) {
        val v = vibrator(view.context)
        if (v == null) {
            view.performHapticFeedback(
                if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT
                else HapticFeedbackConstants.LONG_PRESS
            )
            return
        }
        v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 70, 60, 70, 60, 140), -1))
    }
}
