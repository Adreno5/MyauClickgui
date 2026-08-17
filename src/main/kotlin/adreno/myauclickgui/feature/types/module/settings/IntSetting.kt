package adreno.myauclickgui.feature.types.module.settings
class IntSetting(override val name: String, override var value: Int, var range: Pair<Int, Int>) : Setting<Int>(name, value)
