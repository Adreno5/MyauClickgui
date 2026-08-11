package adreno.myauclickgui

import adreno.myauclickgui.feature.gui.GuiClickGui
import adreno.myauclickgui.feature.managers.ChatManager
import adreno.myauclickgui.feature.types.module.Module
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.common.event.FMLInitializationEvent
import java.util.ArrayDeque
import java.util.ArrayList

@Mod(modid = MyauClickGui.MOD_ID, version = MyauClickGui.VERSION)
class MyauClickGui {
    companion object {
        const val MOD_ID = "MyauClickGui"
        const val VERSION = "1.0.0"

        private var INSTANCE: MyauClickGui? = null

        @JvmStatic
        fun getInstance(): MyauClickGui {
            return INSTANCE!!
        }
    }

    var chatManager: ChatManager? = null
    var guiClickGui: GuiClickGui? = null

    val modules: ArrayList<Module> = ArrayList()
    val logs: ArrayDeque<String> = ArrayDeque()

    init {
        INSTANCE = this
    }

    @Mod.EventHandler
    fun `init`(ignored: FMLInitializationEvent) {
        chatManager = ChatManager()
        guiClickGui = GuiClickGui()
    }
}
