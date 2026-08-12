package adreno.myauclickgui.feature.managers

import adreno.myauclickgui.MyauClickGui
import adreno.myauclickgui.feature.types.chat.ErrorReply
import adreno.myauclickgui.feature.types.chat.OutputReply
import adreno.myauclickgui.feature.types.module.Module
import adreno.myauclickgui.feature.utils.ChatUtil
import net.minecraft.client.Minecraft

class ChatManager {
    private val mc = Minecraft.getMinecraft()
    private val mod = MyauClickGui.getInstance()
    private val chat = ChatUtil()

    fun loadModules() {
        Thread({ loadModulesWorker() }, "MyauClickGui-ModuleLoader")
                .apply { isDaemon = true }.start()
    }

    private fun loadModulesWorker() {
        val reply = chat.getMyauReply(".modules")

        if (reply is ErrorReply) {
            chat.log("Failed to load modules: " + reply.content)
            return
        }

        // module reply example
        /* [Myau] Modules:
           » Fullbright (ON)
           » [R] KillAura (OFF) */

        val (modules, errors) = parseModules((reply as OutputReply).unformatted)
        mc.addScheduledTask {
            mod.modules.clear()
            mod.modules.addAll(modules)
            errors.forEach { chat.err(it) }
            chat.clog("Loaded " + mod.modules.size + " modules")
        }
    }

    companion object {
        fun parseModules(lines: List<String>): Pair<List<Module>, List<String>> {
            val modules = ArrayList<Module>()
            val errors = ArrayList<String>()
            for (line in lines) {
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
            return modules to errors
        }
    }
}
