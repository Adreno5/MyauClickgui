package adreno.myauclickgui.feature.types.module.settings

open class BooleanSetting(override val name: String, override var value: Boolean) : Setting<Boolean>(name, value)