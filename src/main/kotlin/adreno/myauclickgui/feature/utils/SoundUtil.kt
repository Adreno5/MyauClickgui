package adreno.myauclickgui.feature.utils

import net.minecraft.client.Minecraft
import net.minecraft.client.audio.PositionedSound
import net.minecraft.client.audio.ISound
import net.minecraft.client.audio.SoundEventAccessorComposite
import net.minecraft.client.audio.SoundHandler
import net.minecraft.util.ChatComponentText
import net.minecraft.util.ResourceLocation

object SoundUtil {
    private val mc = Minecraft.getMinecraft()

    @JvmStatic
    fun playDirect(sound: ResourceLocation) {
        val sh: SoundHandler = mc.soundHandler
        val accessor: SoundEventAccessorComposite = sh.getSound(sound) ?: return
        sh.playSound(DirectSound(accessor.soundEventLocation))
    }

    private class DirectSound(sound: ResourceLocation) : PositionedSound(sound) {
        init {
            attenuationType = ISound.AttenuationType.NONE
        }
    }
}
