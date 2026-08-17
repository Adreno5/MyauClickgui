package adreno.myauclickgui.feature.managers
import adreno.myauclickgui.MyauClickGui
import adreno.myauclickgui.feature.types.chat.ErrorReply
import adreno.myauclickgui.feature.types.chat.OutputReply
import adreno.myauclickgui.feature.types.config.Config
import adreno.myauclickgui.feature.types.module.Module
import adreno.myauclickgui.feature.types.module.settings.BooleanSetting
import adreno.myauclickgui.feature.types.module.settings.ColorSetting
import adreno.myauclickgui.feature.types.module.settings.FloatSetting
import adreno.myauclickgui.feature.types.module.settings.HideSetting
import adreno.myauclickgui.feature.types.module.settings.IntSetting
import adreno.myauclickgui.feature.types.module.settings.ModeSetting
import adreno.myauclickgui.feature.types.module.settings.NumberSetting
import adreno.myauclickgui.feature.types.module.settings.PercentSetting
import adreno.myauclickgui.feature.types.module.settings.Setting
import adreno.myauclickgui.feature.types.module.settings.SettingKind
import adreno.myauclickgui.feature.types.module.settings.classifySettingValue
import adreno.myauclickgui.feature.types.module.settings.parseColorSetting
import adreno.myauclickgui.feature.types.module.settings.parseFloatSetting
import adreno.myauclickgui.feature.types.module.settings.parseIntSetting
import adreno.myauclickgui.feature.types.module.settings.parseModeSetting
import adreno.myauclickgui.feature.types.module.settings.parseModeOrColorSetting
import adreno.myauclickgui.feature.types.module.settings.parseNumberOrColorSetting
import adreno.myauclickgui.feature.types.module.settings.parsePercentSetting
import adreno.myauclickgui.feature.utils.ChatUtil
import net.minecraft.client.Minecraft
import java.util.function.Consumer
import kotlin.math.pow
object ChatManager {
    private val mc = Minecraft.getMinecraft()
    private val mod = MyauClickGui.getInstance()
    private val chat = ChatUtil
    fun applyConfig(config: Config) {
        Thread({
            val reply = chat.getMyauReply(".config load ${config.name}")
            if (reply is ErrorReply) {
                chat.err("Failed to apply config: " + reply.content)
                return@Thread
            } else if (!(reply as OutputReply).unformatted.contains("Config has been loaded")) {
                chat.err("Failed when Myau loading config: ${reply.unformatted}")
            }
        }, "MyauClickGui-ConfigApplier")
            .apply { isDaemon = true }.start()
    }
    fun toggleModule(target: Module) {
        Thread({
            val reply = chat.getMyauReply(".toggle ${target.name}")
            if (reply is ErrorReply) {
                chat.err("Failed to toggle module: " + reply.content)
                return@Thread
            }
        }, "MyauClickGui-Toggler")
            .apply { isDaemon= true }.start()
    }
    fun setValue(module: Module, setting: BooleanSetting, value: Boolean) {
        sendSettingCommand(module, setting, value.toString()) { setting.value = value }
    }
    fun setValue(setting: HideSetting, value: Boolean) {
        val command = if (value) ".hide ${setting.module.name}" else ".show ${setting.module.name}"
        Thread({
            val reply = chat.getMyauReply(command)
            if (reply is ErrorReply) {
                chat.err("Failed to ${if (value) "hide" else "show"} ${setting.module.name}: " + reply.content)
                return@Thread
            }
            mc.addScheduledTask { setting.value = value }
        }, "MyauClickGui-VisibilityApplier")
            .apply { isDaemon = true }.start()
    }
    fun setValue(module: Module, setting: ModeSetting, value: String) {
        sendSettingCommand(module, setting, value) { setting.value = value }
    }
    fun setValue(module: Module, setting: NumberSetting, value: Float) {
        val decimals = numberDecimals(setting)
        val rounded = roundNumber(value, decimals)
        sendSettingCommand(module, setting, formatNumber(rounded, decimals)) { setting.value = rounded }
    }
    fun setValue(module: Module, setting: IntSetting, value: Int) {
        sendSettingCommand(module, setting, value.toString()) { setting.value = value }
    }
    fun setValue(module: Module, setting: FloatSetting, value: Float) {
        val decimals = maxOf(decimalPlaces(setting.range.first), decimalPlaces(setting.range.second))
        val rounded = roundNumber(value, decimals)
        sendSettingCommand(module, setting, formatNumber(rounded, decimals)) { setting.value = rounded }
    }
    fun setValue(module: Module, setting: ColorSetting, value: Int) {
        sendSettingCommand(module, setting, "%06X".format(value and 0xFFFFFF)) { setting.value = value }
    }
    fun setValue(module: Module, setting: PercentSetting, value: Float) {
        val rounded = kotlin.math.round(value)
        sendSettingCommand(module, setting, "${rounded.toLong()}%") { setting.value = rounded }
    }
    private fun sendSettingCommand(module: Module, setting: Setting<*>, commandValue: String, apply: () -> Unit) {
        Thread({
            val reply = chat.getMyauReply(".${module.name} ${setting.name} $commandValue")
            if (reply is ErrorReply) {
                chat.err("Failed to set ${module.name} ${setting.name}: " + reply.content)
                return@Thread
            }
            mc.addScheduledTask { apply() }
        }, "MyauClickGui-SettingApplier")
            .apply { isDaemon = true }.start()
    }
    fun formatNumber(value: Float): String =
        if (value.isFinite() && value == value.toLong().toFloat()) value.toLong().toString() else value.toString()
    fun numberDecimals(setting: NumberSetting): Int {
        return maxOf(decimalPlaces(setting.range.first), decimalPlaces(setting.range.second))
    }
    fun numberDecimals(setting: FloatSetting): Int {
        return maxOf(decimalPlaces(setting.range.first), decimalPlaces(setting.range.second))
    }
    fun formatNumber(value: Float, decimals: Int): String {
        if (decimals <= 0 || (value.isFinite() && value == value.toLong().toFloat())) return formatNumber(value)
        return String.format(java.util.Locale.ROOT, "%.${decimals}f", value)
            .trimEnd('0').trimEnd('.')
    }
    fun roundNumber(value: Float, decimals: Int): Float {
        if (!value.isFinite() || decimals <= 0) return if (value.isFinite()) value.toLong().toFloat() else value
        val scale = 10.0.pow(decimals.toDouble()).toFloat()
        return kotlin.math.round(value * scale) / scale
    }
    private fun decimalPlaces(value: Float): Int {
        val text = value.toString().lowercase(java.util.Locale.ROOT)
        val exponent = text.indexOf('e')
        if (exponent >= 0) {
            val exponentValue = text.substring(exponent + 1).toIntOrNull() ?: 0
            return maxOf(0, -exponentValue)
        }
        return text.substringAfter('.', "").length
    }
    fun loadSettingsForModule(target: Module, objectStringCallback: ((String) -> Unit)? = null): ArrayList<Setting<*>> {
        val settings: ArrayList<Setting<*>> = ArrayList()
        val errors: MutableList<String> = mutableListOf()
        mc.addScheduledTask { objectStringCallback?.let { it(target.name) } }
        val reply = chat.getMyauReply(".${target.name}")
        if (reply is ErrorReply) {
            chat.err("Failed to load configs: " + reply.content)
            return settings
        }
        val replyOut = reply as OutputReply
        val fmtLines = replyOut.formatted
        val rawLines = replyOut.unformatted
        for (i in rawLines.indices) {
            val rawLine = rawLines[i]
            val fmtLine = fmtLines.getOrElse(i) { rawLine }
            try {
                if (!rawLine.startsWith("»")) continue
                val settingName = rawLine.substringAfter("» ").substringBefore(": ")
                val fmtValue = fmtLine.substringAfter(": ").trimEnd()
                val kind = classifySettingValue(fmtValue)
                if (kind == SettingKind.BOOLEAN) {
                    val plainValue = rawLine.substringAfter(": ")
                    settings.add(BooleanSetting(settingName, plainValue == "true"))
                    continue
                }
                mc.addScheduledTask { objectStringCallback?.let { it(settingName) } }
                val sReply = chat.getMyauReply(".${target.name} $settingName")
                if (sReply is ErrorReply) {
                    errors.add("Failed to load setting $settingName: ${sReply.content}")
                    continue
                }
                val detailLine = (sReply as OutputReply).unformatted.firstOrNull() ?: run {
                    errors.add("Empty reply for setting $settingName")
                    continue
                }
                val parsed: Setting<*>? = when (kind) {
                    SettingKind.INT     -> parseIntSetting(settingName, detailLine)
                    SettingKind.FLOAT   -> parseFloatSetting(settingName, detailLine)
                    SettingKind.PERCENT -> parsePercentSetting(settingName, detailLine)
                    SettingKind.COLOR   -> parseColorSetting(settingName, detailLine)
                    SettingKind.MODE    -> parseModeSetting(settingName, detailLine)
                    SettingKind.UNKNOWN -> {
                        parseNumberOrColorSetting(settingName, detailLine)
                            ?: parseModeOrColorSetting(settingName, detailLine)
                    }
                    else -> null
                }
                if (parsed == null) {
                    errors.add("Unable to parse $settingName (kind=$kind): $detailLine")
                    continue
                }
                settings.add(parsed)
            } catch (e: Exception) {
                val detail = e.message ?: "No message"
                errors.add("Unable to parse setting output: $rawLine (${e.javaClass.simpleName}: $detail)")
            }
        }
        mc.addScheduledTask { objectStringCallback?.let { it("") } }
        mc.addScheduledTask {
            errors.forEach { chat.err(it) }
            chat.clog("Loaded ${settings.size} settings")
        }
        return settings
    }
    fun loadConfigs() {
        Thread({
            val reply = chat.getMyauReply(".config list")
            if (reply is ErrorReply) {
                chat.err("Failed to load configs: " + reply.content)
                return@Thread
            }
            val configs = ArrayList<Config>()
            val errors = ArrayList<String>()
            for (line in (reply as OutputReply).unformatted) {
                try {
                    if (!line.startsWith("»"))
                        continue
                    if (line.split(" ").size != 2) {
                        errors.add("Unable to parse the output: $line")
                        continue
                    }
                    val configName = line.removePrefix("» ").removeSuffix(".json")
                    configs.add(Config(
                        configName, "$configName.json"
                    ))
                } catch (e: Exception) {
                    val detail = e.message ?: "No message"
                    errors.add("Unable to parse the output: $line (${e.javaClass.simpleName}: $detail)")
                }
            }
            mc.addScheduledTask {
                mod.configs.clear()
                mod.configs.addAll(configs)
                errors.forEach { chat.err(it) }
                chat.clog("Loaded ${mod.configs.size} configs")
            }
        }, "MyauClickGui-ConfigLoader")
            .apply { isDaemon = true }.start()
    }
    private class ModuleEntry(val name: String, val state: Boolean, val keyBinding: String?)
    private fun parseModuleLine(line: String): ModuleEntry? {
        val split = line.split(" ")
        if (split.size !in 3..4) return null
        val hasBinding = split[1].contains("[") && split[1].contains("]")
        val name = if (hasBinding) split[2] else split[1]
        val state = split[if (hasBinding) 3 else 2].contains("ON")
        val keyBinding = if (hasBinding) split[1] else null
        return ModuleEntry(name, state, keyBinding)
    }
    fun loadModules() {
        Thread({
            val reply = chat.getMyauReply(".modules")
            if (reply is ErrorReply) {
                chat.log("Failed to load modules: " + reply.content)
                return@Thread
            }
            val modules = ArrayList<Module>()
            val errors = ArrayList<String>()
            for (line in (reply as OutputReply).unformatted) {
                try {
                    if (!line.startsWith("»"))
                        continue
                    val entry = parseModuleLine(line)
                    if (entry == null) {
                        errors.add("Unable to parse the output: $line")
                        continue
                    }
                    modules.add(Module(entry.name, entry.state, entry.keyBinding))
                } catch (e: Exception) {
                    val detail = e.message ?: "No message"
                    errors.add("Unable to parse the output: $line (${e.javaClass.simpleName}: $detail)")
                }
            }
            mc.addScheduledTask {
                mod.modules.clear()
                mod.modules.addAll(modules)
                errors.forEach { chat.err(it) }
                chat.clog("Loaded ${mod.modules.size} modules")
            }
        }, "MyauClickGui-ModuleLoader")
            .apply { isDaemon = true }.start()
    }
    fun loadModuleStates() {
        Thread({
            val reply = chat.getMyauReply(".modules")
            if (reply is ErrorReply) {
                chat.err("Failed to load module states: " + reply.content)
                return@Thread
            }
            val entries = ArrayList<ModuleEntry>()
            val errors = ArrayList<String>()
            for (line in (reply as OutputReply).unformatted) {
                try {
                    if (!line.startsWith("»"))
                        continue
                    val entry = parseModuleLine(line)
                    if (entry == null) {
                        errors.add("Unable to parse the output: $line")
                        continue
                    }
                    entries.add(entry)
                } catch (e: Exception) {
                    val detail = e.message ?: "No message"
                    errors.add("Unable to parse the output: $line (${e.javaClass.simpleName}: $detail)")
                }
            }
            mc.addScheduledTask {
                var updated = 0
                for (entry in entries) {
                    val existing = mod.modules.firstOrNull { it.name == entry.name }
                    if (existing != null) {
                        existing.state = entry.state
                        existing.keyBinding = entry.keyBinding
                        updated++
                    } else {
                        mod.modules.add(Module(entry.name, entry.state, entry.keyBinding))
                    }
                }
                errors.forEach { chat.err(it) }
                chat.clog("Updated $updated module states")
            }
        }, "MyauClickGui-ModuleStateLoader")
            .apply { isDaemon = true }.start()
    }
}
