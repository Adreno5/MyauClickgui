package adreno.myauclickgui.feature.gui

import adreno.myauclickgui.MyauClickGui
import adreno.myauclickgui.feature.types.fonts.Fonts
import adreno.myauclickgui.feature.types.module.Module
import adreno.myauclickgui.feature.utils.EaseInOut
import adreno.myauclickgui.feature.utils.EaseOut
import adreno.myauclickgui.feature.utils.RenderUtil
import adreno.myauclickgui.feature.utils.SmoothColor
import adreno.myauclickgui.feature.utils.SoundUtil
import net.minecraft.client.gui.GuiScreen
import net.minecraft.client.gui.ScaledResolution
import net.minecraft.util.ResourceLocation
import org.lwjgl.input.Keyboard
import org.lwjgl.input.Mouse
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection

object GuiClickGui : GuiScreen() {
    private val mod = MyauClickGui.getInstance()
    private val chat = mod.chatManager
    private val sound = ResourceLocation("minecraft", "random.click")

    override fun initGui() {
        SoundUtil.playDirect(sound)
        super.initGui()
        if (mod.modules.isEmpty())
            mod.chatManager?.loadModules()
        mod.chatManager?.loadConfigs()
    }

    private var searchTyping: Boolean = false
    private var searchText: String = ""
    private var searchCursor: Int = 0
    private val searchScroll = EaseInOut(0.2f, 3)
    private var searchSelectRange: IntRange? = null
    private var selectAnchor: Int? = null
    private val selectLeft = EaseOut(0.1f, 2)
    private val selectRight = EaseOut(0.1f, 2)
    private val searchLine = EaseOut(0.2f, 3)
    private var configuringModule: Module? = null
    private val moduleScroll: EaseOut = EaseOut(0.2f, 3)
    private val configurationScroll: EaseOut = EaseOut(0.2f, 3)
    private val panelExpand: EaseInOut = EaseInOut(0.5f, 3)
    private val leftColor: SmoothColor = SmoothColor(0.5f, 1)
    private var pendingClick: Int? = null
    private var pendingWheel: Int? = null
    private var cursorLastUpdate: Long = 0L
    private val cursorAlpha = EaseInOut(0.2f, 2)
    private val cursorX = EaseInOut(0.1f, 2);
    private val placeAlpha = EaseOut(0.3f, 1)
    private val shownModules: MutableList<Module> = mutableListOf()
    private val searchIcon = ResourceLocation("myauclickgui", "images/search.png")

    init {
        leftColor.targetColor = 0x2A1F085C.toInt()
        placeAlpha.targetValue = 1f
    }

    override fun mouseClicked(mouseX: Int, mouseY: Int, mouseButton: Int) {
        pendingClick = mouseButton
    }

    override fun handleMouseInput() {
        pendingWheel = (pendingWheel ?: 0) + Mouse.getEventDWheel()
        super.handleMouseInput()
    }

    override fun keyTyped(typedChar: Char, keyCode: Int) {
        val ctrl = Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL)
        if (ctrl) {
            when (keyCode) {
                Keyboard.KEY_A -> searchSelectRange = searchText.indices
                Keyboard.KEY_C -> searchSelectRange?.let {
                    Toolkit.getDefaultToolkit().systemClipboard
                        .setContents(StringSelection(searchText.substring(it)), null)
                }
                Keyboard.KEY_V -> {
                    val clip = getClipboard()
                    if (clip != null) {
                        searchText = if (searchSelectRange == null) {
                            val s = StringBuilder(searchText).insert(searchCursor, clip).toString()
                            searchCursor += clip.length
                            s
                        } else {
                            val range = searchSelectRange!!
                            val s = StringBuilder(searchText).replace(range.first, range.last + 1, clip).toString()
                            searchCursor = range.first + clip.length
                            s
                        }
                        searchSelectRange = null
                        selectAnchor = null
                    }
                }
            }
        } else if (keyCode == Keyboard.KEY_LEFT || keyCode == Keyboard.KEY_RIGHT) {
            val shift = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT)
            val dir = if (keyCode == Keyboard.KEY_LEFT) -1 else 1
            if (shift) {
                val anchor = selectAnchor ?: searchCursor
                selectAnchor = anchor
                searchCursor = (searchCursor + dir).coerceIn(0, searchText.length)
                searchSelectRange = minOf(searchCursor, anchor) until maxOf(searchCursor, anchor)
            } else {
                if (searchSelectRange != null) {
                    searchCursor = if (dir < 0) searchSelectRange!!.first else searchSelectRange!!.last + 1
                    searchSelectRange = null
                } else {
                    searchCursor = (searchCursor + dir).coerceIn(0, searchText.length)
                }
                selectAnchor = null
            }
        } else if (keyCode == Keyboard.KEY_BACK) {
            searchText = if (searchSelectRange != null) {
                val range = searchSelectRange!!
                val s = StringBuilder(searchText).delete(range.first, range.last + 1).toString()
                searchCursor = range.first
                s
            } else if (searchCursor > 0) {
                val s = StringBuilder(searchText).delete(searchCursor - 1, searchCursor).toString()
                searchCursor--
                s
            } else {
                searchText
            }
            searchSelectRange = null
            selectAnchor = null
        } else if (typedChar != '\u0000') {
            val inputString = typedChar.toString()
            searchText = if (searchSelectRange == null) {
                val s = StringBuilder(searchText).insert(searchCursor, inputString).toString()
                searchCursor++
                s
            } else {
                val range = searchSelectRange!!
                val s = StringBuilder(searchText).replace(range.first, range.last + 1, inputString).toString()
                searchCursor = range.first + inputString.length
                s
            }
            searchSelectRange = null
            selectAnchor = null
        }
        super.keyTyped(typedChar, keyCode)
    }

    private fun getClipboard(): String? = try {
        Toolkit.getDefaultToolkit().systemClipboard.getData(DataFlavor.stringFlavor) as? String
    } catch (e: Exception) {
        e.printStackTrace()
        ""
    }

    private fun charIndexAt(relX: Float, widths: FloatArray): Int {
        var best = 0
        var bestDist = Float.MAX_VALUE
        for (i in widths.indices) {
            val d = Math.abs(widths[i] - relX)
            if (d < bestDist) {
                bestDist = d
                best = i
            }
        }
        return best
    }

    override fun drawScreen(mouseX: Int, mouseY: Int, partialTicks: Float) {
        render(mouseX, mouseY, pendingClick, pendingWheel)
        pendingClick = null
        pendingWheel = null
    }

    fun render(mouseX: Int, mouseY: Int, mouseButton: Int?, wheel: Int?) {
        val sr = ScaledResolution(mc)
        val sw = sr.scaledWidth
        val sh = sr.scaledHeight
        val lWidth = sw * 0.15f
        val rWidth = sw * 0.43f * panelExpand.currentValue
        val sHeight = sh * 0.76f
        val searchHeight = sh * 0.05f
        if (panelExpand.targetValue == 1f)
            leftColor.targetColor = 0x2A100430.toInt()
        else if (panelExpand.targetValue == 0f)
            leftColor.targetColor = 0x2A1F085C.toInt()
        val x = (sw - lWidth - rWidth) / 2
        val y = (sh - sHeight) / 2
        val radius = (1 - panelExpand.currentValue) * 8f
        val typingWidth = sw * (0.15f - 0.017f)
        val typingX = x + sw * 0.025f
        val textWidths = FloatArray(searchText.length + 1)
        var textWidthAcc = 0f
        for (i in searchText.indices) {
            textWidthAcc += RenderUtil.getTextWidth(searchText[i].toString(), Fonts.HarmonyOS, 14f)
            textWidths[i + 1] = textWidthAcc
        }

        if (System.nanoTime() - cursorLastUpdate > 500000000L) {
            cursorLastUpdate = System.nanoTime()
            cursorAlpha.targetValue = 1f - cursorAlpha.currentValue
        }

        if (mouseButton == 0) {
            val inSearch = RenderUtil.isInside(x, y, lWidth, searchHeight, 8f, 8f, 0f, 0f, mouseX.toFloat(), mouseY.toFloat())
            searchTyping = inSearch
            if (inSearch) {
                searchCursor = charIndexAt(mouseX.toFloat() - typingX - searchScroll.currentValue, textWidths)
                selectAnchor = searchCursor
                searchSelectRange = null
            } else {
                selectAnchor = null
            }
            searchLine.targetValue = if (searchTyping) 1f else 0f
        }
        if (selectAnchor != null && Mouse.isButtonDown(0)) {
            searchCursor = charIndexAt(mouseX.toFloat() - typingX - searchScroll.currentValue, textWidths)
            val anchor = selectAnchor!!
            searchSelectRange = minOf(searchCursor, anchor) until maxOf(searchCursor, anchor)
        }
        if (wheel != null && wheel != 0) {
            val offset = sh * (if (wheel > 0) 0.07f else -0.07f)
            if (RenderUtil.isInside(x, y, lWidth, sHeight, 8f, radius, radius, 8f, mouseX.toFloat(), mouseY.toFloat()))
                searchScroll.targetValue += offset
            else if (RenderUtil.isInside(x, y + searchHeight, lWidth, sHeight - searchHeight, 8f, mouseX.toFloat(), mouseY.toFloat()))
                moduleScroll.targetValue += offset
            else if (RenderUtil.isInside(x + lWidth, y, rWidth, sHeight, 0f, 8f, 7f, 0f, mouseX.toFloat(), mouseY.toFloat()))
                configurationScroll.targetValue += offset
        }

        RenderUtil.renderBlur({
            RenderUtil.drawRoundedRect(x, y, lWidth, sHeight, 8f, radius, radius, 8f, -1)
        }, 48)
        RenderUtil.drawRoundedRect(x, y, lWidth, searchHeight, 8f, 8f, 0f, 0f, 0xD51E1732.toInt())
        RenderUtil.drawRoundedRect(x, y, lWidth + rWidth, sHeight, 8f, 0xD5070312.toInt())
        RenderUtil.drawRoundedRect(x, y, lWidth, sHeight, 8f, radius, radius, 8f, leftColor.currentValue)
        RenderUtil.drawRect(x, y + searchHeight - 1, lWidth * searchLine.currentValue, 2f, 0xFF93A8E4.toInt())
        RenderUtil.drawTexture(searchIcon, x + sw * 0.003f, y + sh * 0.007f, sh * 0.036f, sh * 0.036f)
        val searchTWidth = textWidths[searchCursor.coerceIn(0, searchText.length)]
        val searchTHeight = RenderUtil.getTextHeight(searchText, Fonts.HarmonyOS, 14f).toFloat()
        if (searchText.isEmpty() && !searchTyping)
            RenderUtil.drawTextVCenter("Search...", x + sw * 0.025f, y + (searchHeight - searchTHeight) / 2f, Fonts.HarmonyOS, 14f, RenderUtil.getRGB(225, 225, 225, (225 * placeAlpha.currentValue).toInt()))
        cursorX.targetValue = searchTWidth + 0.7f
        if (searchText.isNotEmpty() || searchTyping)
            RenderUtil.withClipping({
                RenderUtil.drawRect(typingX, y, typingWidth - 4f, searchHeight, -1)
            }, {
                val range = searchSelectRange
                if (range != null && range.first < range.last) {
                    selectLeft.targetValue = textWidths[range.first]
                    selectRight.targetValue = textWidths[range.last + 1]
                    val selX = typingX + selectLeft.currentValue + searchScroll.currentValue
                    val selW = typingX + selectRight.currentValue + searchScroll.currentValue - selX
                    RenderUtil.drawRect(selX, y + (searchHeight - searchTHeight) / 2f, selW, searchTHeight, 0x4D409CFF.toInt())
                }
                RenderUtil.drawRect(typingX + cursorX.currentValue + searchScroll.currentValue, y + (searchHeight - searchTHeight) / 2f, 1f, searchTHeight,
                    RenderUtil.getRGB(245, 248, 255, (255 * cursorAlpha.currentValue).toInt()))
                RenderUtil.drawTextVCenter(searchText, typingX + searchScroll.currentValue, y + (searchHeight - searchTHeight) / 2f, Fonts.HarmonyOS, 14f, -1)
            })
    }

    override fun onGuiClosed() {
        super.onGuiClosed()
        searchTyping = false
    }

    override fun doesGuiPauseGame(): Boolean {
        return false
    }

    override fun drawBackground(tint: Int) { }
}
