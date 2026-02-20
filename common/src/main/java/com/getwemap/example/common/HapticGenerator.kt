package com.getwemap.example.common

import android.Manifest
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.annotation.RequiresPermission

class HapticGenerator(context: Context) {

    private val successDuration: Long = 40
    private val errorPattern = longArrayOf(0, 60, 50, 80)
    private val disableRepeat: Int = -1

    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vm.defaultVibrator
    } else {
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    @RequiresPermission(Manifest.permission.VIBRATE)
    fun success() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val effect = VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
            vibrator.vibrate(effect)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = VibrationEffect.createOneShot(successDuration, VibrationEffect.DEFAULT_AMPLITUDE)
            vibrator.vibrate(effect)
        } else {
            vibrator.vibrate(successDuration)
        }
    }

    @RequiresPermission(Manifest.permission.VIBRATE)
    fun error() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val effect = VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK)
            vibrator.vibrate(effect)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = VibrationEffect.createWaveform(errorPattern, disableRepeat)
            vibrator.vibrate(effect)
        } else {
            vibrator.vibrate(errorPattern, disableRepeat)
        }
    }
}