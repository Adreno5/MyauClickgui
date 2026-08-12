package adreno.myauclickgui.feature.utils

class EaseOut(animationTime: Float, powerNumber: Int) {
    private var targetValue = 0f
    private var currentValue = 0f
    private var difference = 0f

    private var lastUpdate = System.nanoTime()

    var animCycle: Float = 0.001f.coerceAtLeast(animationTime)
        set(value) {
            field = 0.001f.coerceAtLeast(value)
        }

    var powerNumber: Int = 1.coerceAtLeast(powerNumber)
        set(value) {
            field = 1.coerceAtLeast(value)
        }

    fun getTargetValue(): Float = targetValue

    fun setTargetValue(targetValue: Float) {
        if (targetValue != this.targetValue) {
            val actualCurrentValue = calculateCurrentValue()
            difference = targetValue - actualCurrentValue
            currentValue = actualCurrentValue
            this.targetValue = targetValue
            lastUpdate = System.nanoTime()
        }
    }

    private fun calculateCurrentValue(): Float {
        val currentTime = System.nanoTime()
        val elapsed = (currentTime - lastUpdate) / 1_000_000_000f

        if (elapsed >= animCycle || animCycle <= 0) {
            return targetValue
        }

        val progress = elapsed / animCycle
        val easedProgress = 1.0f - Math.pow((1.0f - progress).toDouble(), powerNumber.toDouble()).toFloat()
        return currentValue + (difference * easedProgress)
    }

    fun getCurrentValue(): Float {
        val calculatedValue = calculateCurrentValue()

        val currentTime = System.nanoTime()
        val elapsed = (currentTime - lastUpdate) / 1_000_000_000f

        if (elapsed >= animCycle || animCycle <= 0) {
            currentValue = targetValue
            difference = 0f
        }

        return calculatedValue
    }

    fun reset() {
        currentValue = 0f
        targetValue = 0f
        difference = 0f
        lastUpdate = System.nanoTime()
    }

    fun setCurrentValue(value: Float) {
        if (value != currentValue) {
            difference = targetValue - value
            currentValue = value
            lastUpdate = System.nanoTime()
        }
    }

    fun isAnimating(): Boolean {
        val currentTime = System.nanoTime()
        val elapsed = (currentTime - lastUpdate) / 1_000_000_000f
        return elapsed < animCycle && animCycle > 0 && difference != 0f
    }

    fun getAnimationProgress(): Float {
        if (animCycle <= 0 || difference == 0f) return 1.0f

        val currentTime = System.nanoTime()
        val elapsed = (currentTime - lastUpdate) / 1_000_000_000f
        return Math.min(elapsed / animCycle, 1.0f)
    }
}
