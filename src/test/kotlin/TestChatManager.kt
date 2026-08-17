import adreno.myauclickgui.feature.types.module.settings.ColorSetting
import adreno.myauclickgui.feature.types.module.settings.ModeSetting
import adreno.myauclickgui.feature.types.module.settings.NumberSetting
import adreno.myauclickgui.feature.types.module.settings.parseModeOrColorSetting
import adreno.myauclickgui.feature.types.module.settings.parseNumberOrColorSetting
fun main(args: Array<String>) {
    parsesNumericRgbDetailAsColor()
    preservesNumberSettingParsing()
    parsesRgbDetailAsOpaqueColor()
    fallsBackToModeForInvalidRgb()
}
private fun parsesNumericRgbDetailAsColor() {
    val setting = parseNumberOrColorSetting(
        "custom-color-1",
        "[Myau] HUD: custom-color-1 is set to 000000 (RGB)"
    )
    if (setting !is ColorSetting) {
        throw AssertionError("Expected numeric RGB value to remain a ColorSetting")
    }
    if (setting.value != 0xFF000000L.toInt()) {
        throw AssertionError("Expected opaque black, got ${setting.value.toLong() and 0xFFFFFFFFL}")
    }
}
private fun preservesNumberSettingParsing() {
    val setting = parseNumberOrColorSetting(
        "angle-step",
        "[Myau] KillAura: angle-step is set to 90 (30-180)"
    )
    if (setting !is NumberSetting) {
        throw AssertionError("Expected NumberSetting, got ${setting?.javaClass?.simpleName}")
    }
    if (setting.value != 90f || setting.range != Pair(30f, 180f)) {
        throw AssertionError("NumberSetting value or range changed")
    }
}
private fun parsesRgbDetailAsOpaqueColor() {
    val setting = parseModeOrColorSetting(
        "custom-color-1",
        "[Myau] HUD: custom-color-1 is set to FFFFFF (RGB)"
    )
    if (setting !is ColorSetting) {
        throw AssertionError("Expected ColorSetting, got ${setting.javaClass.simpleName}")
    }
    if (setting.value != 0xFFFFFFFFL.toInt()) {
        throw AssertionError("Expected opaque white, got ${setting.value.toLong() and 0xFFFFFFFFL}")
    }
}
private fun fallsBackToModeForInvalidRgb() {
    val invalidValues = listOf("12345", "FFFFF", "GGGGGG")
    for (value in invalidValues) {
        val lineOutput = "[Myau] HUD: custom-color-1 is set to $value (RGB)"
        val setting = if (value.toDoubleOrNull() != null) {
            parseNumberOrColorSetting("custom-color-1", lineOutput)
        } else {
            parseModeOrColorSetting("custom-color-1", lineOutput)
        }
        if (setting !is ModeSetting) {
            throw AssertionError("Expected ModeSetting fallback for $value, got ${setting?.javaClass?.simpleName}")
        }
        if (setting.value != value || setting.modes != listOf("RGB")) {
            throw AssertionError("ModeSetting fallback did not preserve $value (RGB)")
        }
    }
}
