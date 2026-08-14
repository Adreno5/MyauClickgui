package adreno.myauclickgui.feature.managers

import adreno.myauclickgui.MyauClickGui
import adreno.myauclickgui.feature.types.chat.ErrorReply
import adreno.myauclickgui.feature.types.chat.OutputReply
import adreno.myauclickgui.feature.types.config.Config
import adreno.myauclickgui.feature.types.module.Module
import adreno.myauclickgui.feature.types.module.settings.BooleanSetting
import adreno.myauclickgui.feature.types.module.settings.ColorSetting
import adreno.myauclickgui.feature.types.module.settings.ModeSetting
import adreno.myauclickgui.feature.types.module.settings.NumberSetting
import adreno.myauclickgui.feature.types.module.settings.Setting
import adreno.myauclickgui.feature.types.module.settings.parseModeOrColorSetting
import adreno.myauclickgui.feature.types.module.settings.parseNumberOrColorSetting
import adreno.myauclickgui.feature.utils.ChatUtil
import net.minecraft.client.Minecraft
import java.util.function.Consumer

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

    fun setValue(module: Module, setting: ModeSetting, value: String) {
        sendSettingCommand(module, setting, value) { setting.value = value }
    }

    fun setValue(module: Module, setting: NumberSetting, value: Float) {
        sendSettingCommand(module, setting, formatNumber(value)) { setting.value = value }
    }

    fun setValue(module: Module, setting: ColorSetting, value: Int) {
        sendSettingCommand(module, setting, "%06X".format(value and 0xFFFFFF)) { setting.value = value }
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

    fun formatNumber(value: Float, decimals: Int): String {
        if (decimals <= 0 || (value.isFinite() && value == value.toLong().toFloat())) return formatNumber(value)
        return String.format(java.util.Locale.ROOT, "%.${decimals}f", value)
            .trimEnd('0').trimEnd('.')
    }

    fun roundNumber(value: Float, decimals: Int): Float {
        if (!value.isFinite() || decimals <= 0) return if (value.isFinite()) value.toLong().toFloat() else value
        val scale = Math.pow(10.0, decimals.toDouble()).toFloat()
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
        Thread({
            mc.addScheduledTask { objectStringCallback?.let { it(target.name) } }
            val reply = chat.getMyauReply(".${target.name}")

            if (reply is ErrorReply) {
                chat.err("Failed to load configs: " + reply.content)
                return@Thread
            }

            // settings reply example (. + Module name)
            /*  [Myau] KillAura (OFF):
                » mode: SINGLE // Mode Setting
                » sort: DISTANCE
                » auto-block: SPOOF
                » auto-block-require-press: false // Boolean Setting
                » auto-block-no-slow: false
                » auto-block-hold: 1.5 // Number Setting */
            for (line in (reply as OutputReply).unformatted) {
                try {
                    if (!line.startsWith("»"))
                        continue
                    val settingName = line.substringAfter("» ").substringBefore(": ")
                    val valueString = line.substringAfter(": ")
                    val setting = if (valueString == "false" || valueString == "true") BooleanSetting(settingName, valueString.toBoolean())
                    else if (valueString.toDoubleOrNull() != null) {
                        // Number Setting reply example (. + Module name + Setting name (+ value))
                        // [Myau] KillAura: angle-step is set to 90 (30-180)
                        mc.addScheduledTask { objectStringCallback?.let { it(settingName) } }
                        val sReply = chat.getMyauReply(".${target.name} $settingName")
                        if (sReply is ErrorReply) {
                            errors.add("Failed to load setting $settingName: ${sReply.content}")
                            continue
                        }
                        val lineOutput = (sReply as OutputReply).unformatted.firstOrNull()
                        val parsedSetting = lineOutput?.let { parseNumberOrColorSetting(settingName, it) }
                        if (parsedSetting == null) {
                            errors.add("Unable to parse number setting $settingName: ${lineOutput ?: "empty reply"}")
                            continue
                        }
                        parsedSetting
                    } else {
                        // Mode Setting reply example (. + Module name + Setting name)
                        // [Myau] KillAura: mode is set to SINGLE (SINGLE, SWITCH)
                        mc.addScheduledTask { objectStringCallback?.let { it(settingName) } }
                        val sReply = chat.getMyauReply(".${target.name} $settingName")
                        if (sReply is ErrorReply) {
                            errors.add("Failed to load setting $settingName: ${sReply.content}")
                            continue
                        }
                        val lineOutput = (sReply as OutputReply).unformatted[0]
                        parseModeOrColorSetting(settingName, lineOutput)
                    }
                    settings.add(setting)
                } catch (e: Exception) {
                    val detail = e.message ?: "No message"
                    errors.add("Unable to parse setting output: $line (${e.javaClass.simpleName}: $detail)")
                }
            }

            mc.addScheduledTask { objectStringCallback?.let { it("") } }
            mc.addScheduledTask {
                errors.forEach { chat.err(it) }
                chat.clog("Loaded ${settings.size} settings")
            }
        }, "MyauClickGui-SettingsCollector")
            .apply { isDaemon = true }.start()
        return settings
    }

    fun loadConfigs() {
        Thread({
            val reply = chat.getMyauReply(".config list")

            if (reply is ErrorReply) {
                chat.err("Failed to load configs: " + reply.content)
                return@Thread
            }

            // configs reply example
            /* [Myau] Configs:
               » vulcan.json
               » hyp.json
               » bmc.json
               » MYAU.json */

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

    fun loadModules() {
        Thread({
            val reply = chat.getMyauReply(".modules")

            if (reply is ErrorReply) {
                chat.log("Failed to load modules: " + reply.content)
                return@Thread
            }

            // modules reply example
            /* [Myau] Modules:
               » Fullbright (ON)
               » [R] KillAura (OFF) */

            val modules = ArrayList<Module>()
            val errors = ArrayList<String>()
            for (line in (reply as OutputReply).unformatted) {
                try {
                    if (!line.startsWith("»"))
                        continue

                    val split = line.split(" ")
                    if (split.size !in 3..4) {
                        errors.add("Unable to parse the output: $line")
                        continue
                    }
                    val hasBinding = split[1].contains("[") && split[1].contains("]")
                    val moduleName = if (hasBinding) split[2] else split[1]
                    val moduleState = split[if (hasBinding) 3 else 2].contains("ON")
                    val moduleBinding = if (hasBinding) split[1] else null

                    modules.add(Module(moduleName, moduleState, moduleBinding))
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
}