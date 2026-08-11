package adreno.myauclickgui.feature.gui

import adreno.myauclickgui.MyauClickGui
import net.minecraft.client.gui.GuiScreen

class GuiClickGui : GuiScreen() {
    companion object {
        var INSTANCE: GuiClickGui? = null
    }

    private val mod = MyauClickGui.getInstance()

    init {
        INSTANCE = this
    }

    override fun initGui() {
        super.initGui()
        if (mod.modules.isEmpty()) {
            mod.chatManager?.loadModules()
        }
    }

    override fun drawScreen(mouseX: Int, mouseY: Int, partialTicks: Float) {
    }

    override fun doesGuiPauseGame(): Boolean {
        return false
    }
}
