package adreno.myauclickgui.feature.utils

import adreno.myauclickgui.MyauClickGui
import adreno.myauclickgui.feature.types.chat.ErrorReply
import adreno.myauclickgui.feature.types.chat.MyauReply
import adreno.myauclickgui.feature.types.chat.OutputReply
import net.minecraft.client.Minecraft
import net.minecraft.util.ChatComponentText
import net.minecraft.util.ResourceLocation
import net.minecraftforge.client.event.ClientChatReceivedEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import java.util.ArrayDeque
import java.util.ArrayList
import java.util.Arrays
import java.util.Queue
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ChatUtil {
    companion object {
        private val mc = Minecraft.getMinecraft()
    }

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
        } finally {
            SoundUtil.playDirect(sound)
        }
    }

    private fun startCollect(prompt: String, future: CompletableFuture<MyauReply>) {
        formatted.clear()
        unformatted.clear()
        listening = true
        mc.addScheduledTask { mc.thePlayer!!.sendChatMessage(prompt) }

        scheduler.schedule({
            mc.addScheduledTask { finishCollect(prompt, future) }
        }, 100L, TimeUnit.MILLISECONDS)
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

    @SubscribeEvent
    fun onReceive(event: ClientChatReceivedEvent) {
        if (event.type != 1.toByte()) return
        formatted.add(event.message.formattedText)
        unformatted.add(event.message.unformattedText)
        if (listening) {
            event.isCanceled = true
            clog(event.message.unformattedText)
        }
    }

    fun clog(message: String) {
        mod.logs.addLast(message)
        if (mod.logs.size > 1500)
            mod.logs.removeFirst()
    }

    fun log(message: String) {
        mc.ingameGUI.chatGUI.printChatMessage(ChatComponentText("§7[§fMyauClickGui]§7 $message"))
    }

    fun err(message: String) {
        mc.ingameGUI.chatGUI.printChatMessage(ChatComponentText("§7[§cMyauClickGui · Error]§7 $message"))
        SoundUtil.playDirect(sound)
    }
}
