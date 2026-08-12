package adreno.myauclickgui.feature.types.module

import adreno.myauclickgui.feature.types.module.settings.Setting

class Module(
    var name: String,
    var state: Boolean,
    var keyBinding: String?,
    val settings: ArrayList<Setting<*>> = ArrayList()
) {
    var settingsLoaded: Boolean = false
}