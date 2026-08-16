package adreno.myauclickgui.feature.types.module.settings

// value and range stored as percent (e.g. 0f–150f); send as integer percent string (e.g. "75")
class PercentSetting(override val name: String, override var value: Float, var range: Pair<Float, Float>) : Setting<Float>(name, value)
