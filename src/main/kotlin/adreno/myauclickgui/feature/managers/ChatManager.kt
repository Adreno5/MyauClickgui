package adreno.myauclickgui.feature.managers

import adreno.myauclickgui.MyauClickGui
import adreno.myauclickgui.feature.types.chat.ErrorReply
import adreno.myauclickgui.feature.types.chat.MyauReply
import adreno.myauclickgui.feature.types.chat.OutputReply
import adreno.myauclickgui.feature.types.module.Module
import adreno.myauclickgui.feature.utils.ChatUtil

class ChatManager {
    private val mod = MyauClickGui.getInstance()
    private val chat = ChatUtil()

    fun loadModules() {
        val reply: MyauReply = chat.getMyauReply(".modules")

        if (reply is ErrorReply) {
            chat.log("Failed to load modules: " + reply.content)
            return
        }

        // module reply example
        /* [Myau] Modules:
           » Fullbright (ON)
           » [R] KillAura (OFF) */

        mod.modules.clear()
        val lines = (reply as OutputReply).unformatted
        for (line in lines) {
            if (!arrowPrefix(line))
                continue

            val split = line.split(" ")
            if (split.size !in 3..4) {
                chat.err("Unable to parse the output: $line")
                continue
            }
            val hasBinding = split[1].contains("[") && split[1].contains("]")
            val moduleName = if (hasBinding) split[2] else split[1]
            val moduleState = split[if (hasBinding) 2 else 3].contains("ON")
            val moduleBinding = if (hasBinding) split[1] else null

            mod.modules.add(Module(moduleName, moduleState, moduleBinding))
        }
        chat.clog("Loaded " + mod.modules.size + " modules")
    }

    private fun arrowPrefix(string: String): Boolean {
        return string.startsWith("»")
    }
}
