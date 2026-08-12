package adreno.myauclickgui.feature.gui

import adreno.myauclickgui.MyauClickGui
import adreno.myauclickgui.feature.utils.RenderUtil
import adreno.myauclickgui.feature.utils.SoundUtil
import net.minecraft.client.gui.GuiScreen
import net.minecraft.util.ResourceLocation

class GuiClickGui : GuiScreen() {
    companion object {
        var INSTANCE: GuiClickGui? = null
    }

    private val mod = MyauClickGui.getInstance()
    private val sound = ResourceLocation("minecraft", "random.click")

    init {
        INSTANCE = this
    }

    override fun initGui() {
        SoundUtil.playDirect(sound)
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

    override fun drawBackground(tint: Int) { }
}
