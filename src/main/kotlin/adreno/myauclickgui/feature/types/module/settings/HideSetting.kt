package adreno.myauclickgui.feature.types.module.settings

import adreno.myauclickgui.feature.types.module.Module

/**
 * Synthetic toggle prepended to every module's settings list. It is not reported by
 * Myau as a property: toggling it runs ".hide <module>" / ".show <module>" instead of
 * the usual ".<module> <setting> <value>" command.
 *
 * value == true means the module is hidden from Myau's HUD.
 */
class HideSetting(val module: Module, hidden: Boolean = false) : BooleanSetting("Hide", hidden)
