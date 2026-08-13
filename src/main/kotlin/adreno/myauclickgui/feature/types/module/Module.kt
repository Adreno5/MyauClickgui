package adreno.myauclickgui.feature.types.module

import adreno.myauclickgui.feature.types.module.settings.Setting

class Module(
    var name: String,
    var state: Boolean,
    var keyBinding: String?,
    var settings: ArrayList<Setting<*>> = ArrayList()
)