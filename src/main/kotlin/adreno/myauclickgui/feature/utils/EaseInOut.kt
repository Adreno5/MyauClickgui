package adreno.myauclickgui.feature.utils

class EaseInOut(animationTime: Float, powerNumber: Int) {
    private var _targetValue = 0f
    private var _currentValue = 0f
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

    var targetValue: Float
        get() = _targetValue
        set(value) {
            if (value != _targetValue) {
                val actualCurrentValue = calculateCurrentValue()
                difference = value - actualCurrentValue
                _currentValue = actualCurrentValue
                _targetValue = value
                lastUpdate = System.nanoTime()
            }
        }

    private fun calculateCurrentValue(): Float {
        val currentTime = System.nanoTime()
        val elapsed = (currentTime - lastUpdate) / 1_000_000_000f

        if (elapsed >= animCycle || animCycle <= 0) {
            return _targetValue
        }

        val progress = elapsed / animCycle
        val easedProgress = if (progress < 0.5f) {
            0.5f * Math.pow((2.0f * progress).toDouble(), powerNumber.toDouble()).toFloat()
        } else {
            1.0f - 0.5f * Math.pow((2.0f - 2.0f * progress).toDouble(), powerNumber.toDouble()).toFloat()
        }
        return _currentValue + (difference * easedProgress)
    }

    var currentValue: Float
        get() {
            val calculatedValue = calculateCurrentValue()

            val currentTime = System.nanoTime()
            val elapsed = (currentTime - lastUpdate) / 1_000_000_000f

            if (elapsed >= animCycle || animCycle <= 0) {
                _currentValue = _targetValue
                difference = 0f
            }

            return calculatedValue
        }
        set(value) {
            if (value != _currentValue) {
                difference = _targetValue - value
                _currentValue = value
                lastUpdate = System.nanoTime()
            }
        }

    val isAnimating: Boolean
        get() {
            val currentTime = System.nanoTime()
            val elapsed = (currentTime - lastUpdate) / 1_000_000_000f
            return elapsed < animCycle && animCycle > 0 && difference != 0f
        }

    val animationProgress: Float
        get() {
            if (animCycle <= 0 || difference == 0f) return 1.0f

            val currentTime = System.nanoTime()
            val elapsed = (currentTime - lastUpdate) / 1_000_000_000f
            return Math.min(elapsed / animCycle, 1.0f)
        }

    fun reset() {
        _currentValue = 0f
        _targetValue = 0f
        difference = 0f
        lastUpdate = System.nanoTime()
    }
}
