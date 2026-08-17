package adreno.myauclickgui.feature
import adreno.myauclickgui.MyauClickGui
import net.minecraft.client.Minecraft
import net.minecraft.client.settings.KeyBinding
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.InputEvent
import org.lwjgl.input.Keyboard
class ClickGuiKeyBinding {
    companion object {
        @JvmField
        val keyGui: KeyBinding = KeyBinding("Open Myau ClickGui", Keyboard.KEY_RSHIFT, "MyauClickGui")
    }
    @SubscribeEvent
    fun onKey(ignored: InputEvent.KeyInputEvent) {
        if (keyGui.isPressed) {
            Minecraft.getMinecraft().displayGuiScreen(MyauClickGui.getInstance().guiClickGui)
        }
    }
}
