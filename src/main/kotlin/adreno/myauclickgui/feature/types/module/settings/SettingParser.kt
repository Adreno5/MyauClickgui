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
        val isPercent = settingValue.endsWith("%") && settingModes.all { it.endsWith("%") }
        if (isPercent) {
            val value = settingValue.trimEnd('%').toFloatOrNull()
            val ranges = settingModes.mapNotNull { it.trimEnd('%').toFloatOrNull() }
            if (value != null && ranges.size >= 2) {
                return PercentSetting(name, value, Pair(ranges.first(), ranges.last()))
            }
        }
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
    val rawValue = valueAndRange.substring(0, rangeStart).trim()
    val range = valueAndRange.substring(rangeStart + 1, rangeEnd).trim()
    if (rawValue.endsWith("%") && range.contains("%")) {
        val value = rawValue.trimEnd('%').toFloatOrNull() ?: return null
        val rangeParts = range.split("-")
        if (rangeParts.size >= 2) {
            val minimum = rangeParts.first().trim().trimEnd('%').toFloatOrNull() ?: return null
            val maximum = rangeParts.last().trim().trimEnd('%').toFloatOrNull() ?: return null
            return PercentSetting(name, value, Pair(minimum, maximum))
        }
        return null
    }
    val value = rawValue.toFloatOrNull() ?: return null
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
enum class SettingKind { BOOLEAN, MODE, INT, FLOAT, PERCENT, COLOR, UNKNOWN }
private val VALUE_RUN_REGEX = Regex("§(.)([^§]*)")
fun classifySettingValue(formattedValue: String): SettingKind {
    val runs = VALUE_RUN_REGEX.findAll(formattedValue)
        .map { it.groupValues[1].lowercase() to it.groupValues[2] }
        .filter { it.first != "r" && it.second.isNotEmpty() }
        .toList()
    if (runs.isEmpty()) return SettingKind.UNKNOWN
    if (runs.size >= 2) return SettingKind.COLOR
    val (code, text) = runs[0]
    return when (code) {
        "a", "c" -> if (text == "true" || text == "false") SettingKind.BOOLEAN else SettingKind.UNKNOWN
        "9" -> SettingKind.MODE
        "e" -> SettingKind.INT
        "6" -> SettingKind.FLOAT
        "b" -> SettingKind.PERCENT
        else -> SettingKind.UNKNOWN
    }
}
private fun parseValueAndRangeRaw(lineOutput: String): Pair<String, String>? {
    val valueAndRange = lineOutput.substringAfter("is set to ", "")
    val rangeStart = valueAndRange.indexOf('(')
    val rangeEnd = valueAndRange.indexOf(')', rangeStart + 1)
    if (rangeStart !in 1 until rangeEnd) return null
    val value = valueAndRange.substring(0, rangeStart).trim()
    val range = valueAndRange.substring(rangeStart + 1, rangeEnd).trim()
    return value to range
}
private inline fun <T> splitRange(range: String, parse: (String) -> T?): Pair<T, T>? {
    for (i in 1 until range.length) {
        if (range[i] != '-') continue
        val minimum = parse(range.substring(0, i).trim()) ?: continue
        val maximum = parse(range.substring(i + 1).trim()) ?: continue
        return minimum to maximum
    }
    return null
}
fun parseIntSetting(name: String, lineOutput: String): IntSetting? {
    val (rawValue, rawRange) = parseValueAndRangeRaw(lineOutput) ?: return null
    val value = rawValue.toIntOrNull() ?: rawValue.toFloatOrNull()?.toInt() ?: return null
    val (minimum, maximum) = splitRange(rawRange) { it.toIntOrNull() ?: it.toFloatOrNull()?.toInt() } ?: return null
    return IntSetting(name, value, Pair(minimum, maximum))
}
fun parseFloatSetting(name: String, lineOutput: String): FloatSetting? {
    val (rawValue, rawRange) = parseValueAndRangeRaw(lineOutput) ?: return null
    val value = rawValue.toFloatOrNull() ?: return null
    val (minimum, maximum) = splitRange(rawRange) { it.toFloatOrNull() } ?: return null
    return FloatSetting(name, value, Pair(minimum, maximum))
}
fun parsePercentSetting(name: String, lineOutput: String): PercentSetting? {
    val (rawValue, rawRange) = parseValueAndRangeRaw(lineOutput) ?: return null
    val value = rawValue.trimEnd('%').toFloatOrNull() ?: return null
    val (minimum, maximum) = splitRange(rawRange) { it.trimEnd('%').toFloatOrNull() } ?: return null
    return PercentSetting(name, value, Pair(minimum, maximum))
}
fun parseModeSetting(name: String, lineOutput: String): ModeSetting {
    val settingValue = lineOutput.substringAfter("is set to ").substringBefore(" (")
    return ModeSetting(name, settingValue, parseSettingModes(lineOutput))
}
fun parseColorSetting(name: String, lineOutput: String): ColorSetting? {
    val settingValue = lineOutput.substringAfter("is set to ").substringBefore(" (")
    if (!RGB_HEX_PATTERN.matches(settingValue)) return null
    return ColorSetting(name, (0xFF000000L or settingValue.toLong(16)).toInt())
}
