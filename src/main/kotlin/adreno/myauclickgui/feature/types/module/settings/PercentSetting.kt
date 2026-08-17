package adreno.myauclickgui.feature.types.module.settings
class PercentSetting(override val name: String, override var value: Float, var range: Pair<Float, Float>) : Setting<Float>(name, value)
