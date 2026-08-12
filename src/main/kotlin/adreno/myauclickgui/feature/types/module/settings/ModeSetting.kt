package adreno.myauclickgui.feature.types.module.settings

class ModeSetting<T>(override val name: String, override var value: T, val modes: List<T>) : Setting<T>(name, value)