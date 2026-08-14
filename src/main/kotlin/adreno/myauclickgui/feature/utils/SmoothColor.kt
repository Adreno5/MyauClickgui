package adreno.myauclickgui.feature.utils

import java.awt.Color

class SmoothColor(animationTime: Float, powerNumber: Int) {
    private val rt = EaseOut(animationTime, powerNumber)
    private val gr = EaseOut(animationTime, powerNumber)
    private val bt = EaseOut(animationTime, powerNumber)
    private val at = EaseOut(animationTime, powerNumber)

    var targetColor: Int
        get() = RenderUtil.getRGB(
            clampColor(rt.targetValue),
            clampColor(gr.targetValue),
            clampColor(bt.targetValue),
            clampColor(at.targetValue)
        )
        set(value) {
            val parsed = RenderUtil.parseARGB(value)
            rt.targetValue = parsed.r.toFloat()
            gr.targetValue = parsed.g.toFloat()
            bt.targetValue = parsed.b.toFloat()
            at.targetValue = parsed.a.toFloat()
        }

    private fun clampColor(value: Float): Int = 0.coerceAtLeast(255.coerceAtMost(value.toInt()))

    var currentValue: Int
        get() = RenderUtil.getRGB(
            clampColor(rt.currentValue),
            clampColor(gr.currentValue),
            clampColor(bt.currentValue),
            clampColor(at.currentValue)
        )
        set(value) {
            val parsed = RenderUtil.parseARGB(value)
            rt.currentValue = parsed.r.toFloat()
            gr.currentValue = parsed.g.toFloat()
            bt.currentValue = parsed.b.toFloat()
            at.currentValue = parsed.a.toFloat()
        }

    fun reset() {
        rt.reset()
        gr.reset()
        bt.reset()
        at.reset()
    }
}
