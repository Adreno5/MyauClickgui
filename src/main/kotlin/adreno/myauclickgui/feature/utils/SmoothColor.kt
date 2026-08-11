package adreno.myauclickgui.feature.utils

import java.awt.Color

class SmoothColor(animationTime: Float, powerNumber: Int) {
    private val rt = EaseOut(animationTime, powerNumber)
    private val gr = EaseOut(animationTime, powerNumber)
    private val bt = EaseOut(animationTime, powerNumber)
    private val at = EaseOut(animationTime, powerNumber)

    fun setTargetValue(color: Color) {
        rt.setTargetValue(color.red.toFloat())
        gr.setTargetValue(color.green.toFloat())
        bt.setTargetValue(color.blue.toFloat())
        at.setTargetValue(color.alpha.toFloat())
    }

    private fun clampColor(value: Float): Int = Math.max(0, Math.min(255, value.toInt()))

    fun getCurrentValue(): Int {
        return Color(
            clampColor(rt.getCurrentValue()),
            clampColor(gr.getCurrentValue()),
            clampColor(bt.getCurrentValue()),
            clampColor(at.getCurrentValue())
        ).rgb
    }
}
