package adreno.myauclickgui.feature.utils

import adreno.myauclickgui.MyauClickGui
import adreno.myauclickgui.feature.types.chat.ErrorReply
import adreno.myauclickgui.feature.types.chat.MyauReply
import adreno.myauclickgui.feature.types.chat.OutputReply
import adreno.myauclickgui.feature.types.logs.LogInfo
import net.minecraft.client.Minecraft
import net.minecraft.util.ChatComponentText
import net.minecraft.util.IChatComponent
import net.minecraft.util.ResourceLocation
import java.util.ArrayDeque
import java.util.ArrayList
import java.util.Arrays
import java.util.Queue
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object ChatUtil {
    private val mc = Minecraft.getMinecraft()

    private val mod = MyauClickGui.getInstance()
    private val formatted = ArrayList<String>()
    private val unformatted = ArrayList<String>()
    private val scheduler = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "MyauClickGui - Collector").apply { isDaemon = true }
    }
    private var listening = false
    private val queue: Queue<PendingRequest> = ArrayDeque()
    private val sound = ResourceLocation("minecraft", "random.orb")

    private class PendingRequest(
        val prompt: String,
        val future: CompletableFuture<MyauReply>
    )

    fun getMyauReply(prompt: String): MyauReply {
        clog(prompt)
        check(!mc.isCallingFromMinecraftThread) {
            "getMyauReply must not be called from the Minecraft main thread"
        }
        val future = CompletableFuture<MyauReply>()
        synchronized(this) {
            if (mc.thePlayer == null) {
                future.complete(ErrorReply("the player is null"))
            } else if (listening) {
                queue.add(PendingRequest(prompt, future))
            } else {
                startCollect(prompt, future)
            }
        }
        return try {
            future.get()
        } catch (e: InterruptedException) {
            ErrorReply(e.stackTrace.contentToString())
        } catch (e: java.util.concurrent.ExecutionException) {
            ErrorReply(e.stackTrace.contentToString())
        }
    }

    private fun startCollect(prompt: String, future: CompletableFuture<MyauReply>) {
        formatted.clear()
        unformatted.clear()
        listening = true
        mc.addScheduledTask { mc.thePlayer!!.sendChatMessage(prompt) }

        scheduler.schedule({
            mc.addScheduledTask { finishCollect(prompt, future) }
        }, 90L, TimeUnit.MILLISECONDS)
    }

    private fun finishCollect(prompt: String, future: CompletableFuture<MyauReply>) {
        var next: PendingRequest? = null
        synchronized(this) {
            if (listening) {
                listening = false
                future.complete(OutputReply(prompt, formatted, unformatted))
            }
            next = queue.poll()
        }
        val pending = next
        if (pending != null)
            startCollect(pending.prompt, pending.future)
    }

    @JvmStatic
    fun onChatMessage(component: IChatComponent, chatLineId: Int): Boolean { // hooked by mixin
        val fmt = component.formattedText
        val plain = fmt.replace(Regex("§."), "")
        val match = plain.startsWith("[Myau]") || plain.startsWith("»")
        if (!match) return false
        unformatted.add(plain)
        formatted.add(fmt)
        if (listening) {
            clog(fmt)
            return true
        }
        return false
    }

    fun clog(message: String) {
        synchronized(mod.logs) {
            mod.logs.addLast(LogInfo(message))
            if (mod.logs.size > 250)
                mod.logs.removeFirst()
        }
    }

    fun log(message: String) {
        val text = "§7[§fMyauClickGui]§7 $message"
        mc.ingameGUI.chatGUI.printChatMessage(ChatComponentText(text))
        clog(text)
    }

    fun err(message: String) {
        val text = "§7[§cMyauClickGui · Error]§7 $message"
        mc.ingameGUI.chatGUI.printChatMessage(ChatComponentText(text))
        SoundUtil.playDirect(sound)
        clog(text)
    }
}
