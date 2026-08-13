package adreno.myauclickgui.feature.types.module.settings

class ModeSetting(override val name: String, override var value: String, val modes: List<String>) : Setting<String>(name, value)