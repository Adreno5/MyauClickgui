package adreno.myauclickgui.feature.managers

import adreno.myauclickgui.MyauClickGui
import adreno.myauclickgui.feature.types.chat.ErrorReply
import adreno.myauclickgui.feature.types.chat.OutputReply
import adreno.myauclickgui.feature.types.config.Config
import adreno.myauclickgui.feature.types.module.Module
import adreno.myauclickgui.feature.utils.ChatUtil
import net.minecraft.client.Minecraft

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