package adreno.myauclickgui.feature.types.module.settings
import adreno.myauclickgui.feature.types.module.Module
class HideSetting(val module: Module, hidden: Boolean = false) : BooleanSetting("Hide", hidden)
