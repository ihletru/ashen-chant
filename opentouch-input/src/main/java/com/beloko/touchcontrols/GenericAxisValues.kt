package com.beloko.touchcontrols

import android.view.MotionEvent

class GenericAxisValues {
    private val values = FloatArray(64)

    fun getAxisValue(a: Int): Float = values[a]

    fun setAxisValue(a: Int, v: Float) {
        values[a] = v
    }

    fun setAndroidValues(event: MotionEvent) {
        for (n in 0 until 64) {
            values[n] = event.getAxisValue(n)
        }
    }
}
