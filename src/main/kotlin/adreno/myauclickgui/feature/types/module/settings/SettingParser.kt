package adreno.myauclickgui.feature.types.module.settings

private val RGB_HEX_PATTERN = Regex("[0-9A-Fa-f]{6}")

fun parseModeOrColorSetting(name: String, lineOutput: String): Setting<*> {
    val settingValue = lineOutput.substringAfter("is set to ").substringBefore(" (")
    val settingModes = parseSettingModes(lineOutput)
    val colorValue = if (settingModes == listOf("RGB") && RGB_HEX_PATTERN.matches(settingValue)) {
        (0xFF000000L or settingValue.toLong(16)).toInt()
    } else {
        null
    }

    return if (colorValue != null) {
        ColorSetting(name, colorValue)
    } else {
        ModeSetting(name, settingValue, settingModes)
    }
}

fun parseNumberOrColorSetting(name: String, lineOutput: String): Setting<*>? {
    if (parseSettingModes(lineOutput) == listOf("RGB")) {
        return parseModeOrColorSetting(name, lineOutput)
    }

    val valueAndRange = lineOutput.substringAfter("is set to ", "")
    val rangeStart = valueAndRange.indexOf('(')
    val rangeEnd = valueAndRange.indexOf(')', rangeStart + 1)
    if (rangeStart !in 1 until rangeEnd) {
        return null
    }

    val value = valueAndRange.substring(0, rangeStart).trim().toFloatOrNull() ?: return null
    val range = valueAndRange.substring(rangeStart + 1, rangeEnd).trim()
    for (separator in 1 until range.length) {
        if (range[separator] != '-') {
            continue
        }
        val minimum = range.substring(0, separator).trim().toFloatOrNull() ?: continue
        val maximum = range.substring(separator + 1).trim().toFloatOrNull() ?: continue
        return NumberSetting(name, value, Pair(minimum, maximum))
    }
    return null
}

private fun parseSettingModes(lineOutput: String): List<String> =
    lineOutput.substringAfter("(").substringBefore(")").split(", ")
