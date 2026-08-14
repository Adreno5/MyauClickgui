package adreno.myauclickgui.feature.gui

import adreno.myauclickgui.MyauClickGui
import adreno.myauclickgui.feature.managers.ChatManager
import adreno.myauclickgui.feature.types.fonts.Fonts
import adreno.myauclickgui.feature.types.module.Module
import adreno.myauclickgui.feature.types.module.settings.BooleanSetting
import adreno.myauclickgui.feature.types.module.settings.ColorSetting
import adreno.myauclickgui.feature.types.module.settings.ModeSetting
import adreno.myauclickgui.feature.types.module.settings.NumberSetting
import adreno.myauclickgui.feature.types.module.settings.Setting
import adreno.myauclickgui.feature.utils.EaseInOut
import adreno.myauclickgui.feature.utils.EaseOut
import adreno.myauclickgui.feature.utils.RenderUtil
import adreno.myauclickgui.feature.utils.SmoothColor
import adreno.myauclickgui.feature.utils.SoundUtil
import net.minecraft.client.gui.GuiScreen
import net.minecraft.client.gui.ScaledResolution
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.util.ResourceLocation
import org.lwjgl.input.Keyboard
import org.lwjgl.input.Mouse
import scala.annotation.switch
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.util.Random
import kotlin.math.abs

object GuiClickGui : GuiScreen() {
    private val mod = MyauClickGui.getInstance()
    private val chat = mod.chatManager
    private val sound = ResourceLocation("minecraft", "random.click")

    override fun initGui() {
        SoundUtil.playDirect(sound)
        super.initGui()
        if (mod.modules.isEmpty()) {
            loadingModules = true
            chat?.loadModules()
        }
        chat?.loadConfigs()
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
    private var pendingRelease: Boolean = false
    private var cursorLastUpdate: Long = 0L
    private val cursorAlpha = EaseInOut(0.2f, 2)
    private val cursorX = EaseInOut(0.1f, 2);
    private val placeAlpha = EaseOut(0.3f, 1)
    private val shownModules: MutableList<Module> = mutableListOf()
    private val searchIcon = ResourceLocation("myauclickgui", "images/search.png")
    private val moduleOpenMap = LinkedHashMap<Module, EaseInOut>()
    private val switchXMap = LinkedHashMap<BooleanSetting, EaseInOut>()
    private val switchBgMap = LinkedHashMap<BooleanSetting, SmoothColor>()
    private val sliderXMap = LinkedHashMap<NumberSetting, EaseInOut>()
    private val modeSettingExpend = LinkedHashMap<ModeSetting, EaseInOut>()
    private val modeSettingVLine = LinkedHashMap<ModeSetting, EaseOut>()
    private var expandedMode: ModeSetting? = null
    private var draggingModule: Module? = null
    private var draggingNumber: NumberSetting? = null
    private var draggingColor: ColorSetting? = null
    private var colorDragMode: ColorDragMode? = null
    private var colorDragH = 0f
    private var colorDragS = 0f
    private var colorDragV = 0f
    private var colorDragValue = 0xFFFFFFFF.toInt()
    private var dragTrackX = 0f
    private var dragTrackW = 0f
    private var lastLogCount = 0
    private val logOffset = EaseInOut(0.2f, 3)
    private val r = Random()

    private enum class ColorDragMode { SV, HUE }

    private val themeColor = 0xFF93A8E4.toInt()
    private val switchOffColor = 0xE0040210.toInt()
    private val switchOnColor = 0xE0383D5B.toInt()
    private val trackBaseColor = 0x3DFFFFFF.toInt()
    private val sliderColor = 0xFFFFFFFF.toInt()

    private var loadingModules = false
    private var loadingSettingsSuffix = ""

    init {
        leftColor.targetColor = 0x2A1F085C.toInt()
        placeAlpha.targetValue = 1f

        if (java.lang.Boolean.getBoolean("myauclickgui.testModules")) {
            testModules()
        }
    }

    private fun testModules() {
        for (i in 0 until 100) {
            val rSettings = ArrayList<Setting<*>>()
            val settingCount = r.nextInt() % 20 + 10
            for (j in 1 until settingCount) {
                val setting = when (r.nextInt(4)) {
                    0 -> BooleanSetting("TestBooleanSetting $j", r.nextBoolean())
                    1 -> NumberSetting("TestNumberSetting $j", r.nextFloat() % 1f, Pair(0f, 1f))
                    2 -> ModeSetting("TestModeSetting $j", "Option 2", listOf("Option 1", "Option 2", "Option 3"))
                    else -> ColorSetting(
                        "TestColorSetting $j",
                        (0xFF000000L or (r.nextInt().toLong() and 0xFFFFFFL)).toInt()
                    )
                }
                rSettings.add(setting)
            }
            mod.modules.add(Module("TestModule $i", r.nextBoolean(), null, rSettings))
        }
    }

    override fun mouseClicked(mouseX: Int, mouseY: Int, mouseButton: Int) {
        pendingClick = mouseButton
    }

    override fun mouseReleased(mouseX: Int, mouseY: Int, state: Int) {
        pendingRelease = true
    }

    override fun handleMouseInput() {
        pendingWheel = (pendingWheel ?: 0) + Mouse.getEventDWheel()
        super.handleMouseInput()
    }

    override fun drawScreen(mouseX: Int, mouseY: Int, partialTicks: Float) {
        preProcess()
        render(mouseX, mouseY, pendingClick, pendingWheel, pendingRelease)
        pendingClick = null
        pendingWheel = null
        pendingRelease = false
    }

    fun render(mouseX: Int, mouseY: Int, mouseButton: Int?, wheel: Int?, release: Boolean) {
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
        val typingWidth = sw * (0.15f - 0.025f)
        val typingX = x + sw * 0.025f
        val textWidths = FloatArray(searchText.length + 1)
        var textWidthAcc = 0f
        for (i in searchText.indices) {
            textWidthAcc += RenderUtil.getTextWidth(searchText[i].toString(), Fonts.HarmonyOS, 6f)
            textWidths[i + 1] = textWidthAcc
        }
        panelExpand.targetValue = if (configuringModule == null) 0f else 1f;

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
            val offset = sh * (if (wheel > 0) 0.1f else -0.1f)
            if (RenderUtil.isInside(x, y, lWidth, searchHeight, 8f, radius, radius, 8f, mouseX.toFloat(), mouseY.toFloat()))
                searchScroll.targetValue += offset
            else if (RenderUtil.isInside(x, y + searchHeight, lWidth, sHeight - searchHeight, 8f, mouseX.toFloat(), mouseY.toFloat()))
                moduleScroll.targetValue += offset
            else if (RenderUtil.isInside(x + lWidth, y, rWidth, sHeight, 0f, 8f, 8f, 0f, mouseX.toFloat(), mouseY.toFloat()))
                configurationScroll.targetValue += offset
        }

        RenderUtil.renderBlur({
            RenderUtil.drawRoundedRect(x, y, lWidth + rWidth, sHeight, 8f, -1)
        }, 48)
        RenderUtil.drawRoundedRect(x, y, lWidth, searchHeight, 8f, 8f, 0f, 0f, 0xD51E1732.toInt())

        RenderUtil.drawRoundedRect(x, y, lWidth + rWidth, sHeight, 8f, 0xD5070312.toInt())
        RenderUtil.drawRoundedRect(x, y, lWidth, sHeight, 8f, radius, radius, 8f, leftColor.currentValue)
        RenderUtil.drawRect(x, y + searchHeight - 1, lWidth * searchLine.currentValue, 2f, 0xFF93A8E4.toInt())
        RenderUtil.drawTexture(searchIcon, x + sw * 0.003f, y + sh * 0.007f, sh * 0.036f, sh * 0.036f)
        val searchTWidth = textWidths[searchCursor.coerceIn(0, searchText.length)]
        val searchTHeight = RenderUtil.getTextHeight(searchText, Fonts.HarmonyOS, 6f).toFloat()
        if (searchText.isEmpty() && !searchTyping)
            RenderUtil.drawTextVCenter("Search...", x + sw * 0.025f, y + searchHeight / 2f, Fonts.HarmonyOS, 6f, RenderUtil.getRGB(225, 225, 225, (225 * placeAlpha.currentValue).toInt()))
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
                RenderUtil.drawTextVCenter(searchText, typingX + searchScroll.currentValue, y + searchHeight / 2f, Fonts.HarmonyOS, 6f, -1)
            })
        if (loadingModules)
            RenderUtil.drawTextCenter("Loading modules...", x + lWidth / 2f,
                y + searchHeight + (sHeight - searchHeight) / 2f, Fonts.HarmonyOS, 6f, -1)
        else {
            val hGap = sw * 0.03f
            val vGap = sh * 0.009f
            val cHeight = sh * 0.045f
            var dy = y + searchHeight + 5 + moduleScroll.currentValue - cHeight - vGap
            val listTop = y + searchHeight
            val listBottom = y + sHeight
            RenderUtil.withClipping({
                RenderUtil.drawRoundedRect(x, y + searchHeight, lWidth, sHeight - searchHeight, 0f, 0f, 8f, 8f, -1)
            }, {
                for (module in mod.modules) {
                    val open = moduleOpenMap.getOrPut(module, { EaseInOut(0.3f, 3) })
                    open.targetValue = if (module.state) 1f else 0f
                    dy += cHeight + vGap
                    if (dy + 25f < listTop - 10f || dy > listBottom + 10f) {
                        continue
                    }
                    val leftR = open.currentValue * cHeight / 2f
                    val rightR = cHeight / 2f - leftR
                    if (mouseButton != null) {
                        val isIn = RenderUtil.isInside(
                            x,
                            y + searchHeight,
                            lWidth,
                            sHeight - searchHeight,
                            0f,
                            0f,
                            8f,
                            8f,
                            mouseX.toFloat(),
                            mouseY.toFloat()
                        ) &&
                                RenderUtil.isInside(
                                    x + open.currentValue * hGap,
                                    dy,
                                    lWidth - hGap,
                                    25f,
                                    leftR,
                                    rightR,
                                    rightR,
                                    leftR,
                                    mouseX.toFloat(),
                                    mouseY.toFloat()
                                )
                        if (mouseButton == 0 && isIn) {
                            chat!!.toggleModule(module)
                            module.state = !module.state
                        }
                        if (mouseButton == 1 && isIn) {
                            configuringModule = module
                            expandedMode = null
                            draggingModule = null
                            draggingNumber = null
                            draggingColor = null
                            colorDragMode = null
                            switchXMap.clear()
                            switchBgMap.clear()
                            Thread({
                                val settings = chat!!.loadSettingsForModule(module) { s -> loadingSettingsSuffix = s }
                                configuringModule?.takeIf { it.settings.isEmpty() }?.also { it.settings = settings }
                            }, "MyauClickGui-${module.name}SettingsGetter")
                                .apply { isDaemon = true }.start()
                        }
                    }
                    RenderUtil.drawRoundedRect(
                        x + open.currentValue * hGap, dy, lWidth - hGap, 25f,
                        leftR, rightR, rightR, leftR, 0xA11D1072.toInt()
                    )
                    RenderUtil.drawTextCenter(module.name, x + lWidth / 2f, dy + 12.5f, Fonts.HarmonyOS, 6f, -1)
                }
            })
            dy = y + searchHeight + 5 + moduleScroll.currentValue - cHeight - vGap
            for (module in mod.modules) {
                val open = moduleOpenMap.getOrPut(module, { EaseInOut(0.3f, 3) })
                open.targetValue = if (module.state) 1f else 0f
                dy += cHeight + vGap
                if (dy + 25f < listTop - 10f || dy > listBottom + 10f) {
                    continue
                }
                val leftR = open.currentValue * cHeight / 2f
                val rightR = cHeight / 2f - leftR
                RenderUtil.withClipping({
                    RenderUtil.drawRoundedRect(x, y + searchHeight, lWidth, sHeight - searchHeight, 0f, 0f, 8f, 8f, -1)
                }, {
                    RenderUtil.withClipping({
                        RenderUtil.drawRoundedRect(
                            x + open.currentValue * hGap, dy, lWidth - hGap, 25f,
                            leftR, rightR, rightR, leftR, -1
                        )
                    }, {
                        RenderUtil.drawHorizontalGradientRect(x, dy, hGap, 25f, 0xC1B22949.toInt(), 0x00000000.toInt())
                        RenderUtil.drawHorizontalGradientRect(x + lWidth - hGap, dy, hGap, 25f, 0x00000000.toInt(), 0xC1109B3E.toInt())
                    })
                })
            }
        }
        if (mouseButton == 0 && !RenderUtil.isInside(x, y, lWidth + rWidth, sHeight, 8f, mouseX.toFloat(), mouseY.toFloat())) {
            configuringModule = null
        }
        RenderUtil.withClipping({
            RenderUtil.drawRoundedRect(x + lWidth, y, rWidth, sHeight, 8f, 0f, 0f, 8f, -1)
        }, {
            if (loadingSettingsSuffix.isNotEmpty()) {
                RenderUtil.drawTextCenter(
                    "Loading settings...  ($loadingSettingsSuffix)",
                    x + lWidth + rWidth / 2f,
                    y + sHeight / 2f,
                    Fonts.HarmonyOS,
                    8.4f,
                    -1
                )
                configurationScroll.targetValue = 0f
            }
            else {
                val module = configuringModule ?: return@withClipping
                val sx = x + lWidth + 10f
                val rowWidth = rWidth - 20f
                val mx = mouseX.toFloat()
                val my = mouseY.toFloat()
                val inPanel = RenderUtil.isInside(x + lWidth, y, rWidth, sHeight, 8f, mx, my)

                fun renderBooleanSetting(setting: BooleanSetting, sy: Float) {
                    val nameWidth = RenderUtil.getTextWidth(setting.name, Fonts.HarmonyOS, 6f)
                    val baseX = sx + nameWidth + sw * 0.05f
                    val baseY = sy + (25f - 22f) / 2f
                    val switchX = switchXMap.getOrPut(setting, {
                        EaseInOut(0.3f, 3).apply { currentValue = if (setting.value) 20f else 2f }
                    })
                    val switchBg = switchBgMap.getOrPut(setting, {
                        SmoothColor(0.3f, 3).apply {
                            currentValue = if (setting.value) switchOnColor else switchOffColor
                            targetColor = if (setting.value) switchOnColor else switchOffColor
                        }
                    })
                    if (mouseButton == 0 && inPanel && RenderUtil.isInside(baseX, baseY, 40f, 22f, 11f, mx, my)) {
                        setting.value = !setting.value
                        ChatManager.setValue(module, setting, setting.value)
                    }
                    switchX.targetValue = if (setting.value) 20f else 2f
                    switchBg.targetColor = if (setting.value) switchOnColor else switchOffColor
                    RenderUtil.drawTextVCenter(setting.name, sx, sy + 12.5f, Fonts.HarmonyOS, 6f, -1)
                    RenderUtil.drawRoundedRect(baseX, baseY, 40f, 22f, 11f, switchBg.currentValue)
                    RenderUtil.drawRoundedRect(baseX + switchX.currentValue, baseY + 2f, 18f, 18f, 9f, sliderColor)
                }

                fun renderModeSetting(setting: ModeSetting, sy: Float) {
                    val modeExpend = modeSettingExpend.getOrPut(setting, { EaseInOut(0.3f, 3) })
                    val vLine = modeSettingVLine.getOrPut(setting, {
                        EaseOut(0.3f, 3).apply {
                            currentValue = 25f + setting.modes.indexOf(setting.value).coerceAtLeast(0) * 18f
                        }
                    })
                    val progress = modeExpend.currentValue
                    val totalExpandH = setting.modes.size * 18f
                    val modeX = sx + 12f
                    val rowRight = sx + rowWidth

                    if (mouseButton == 1 && inPanel && RenderUtil.isInside(sx, sy, rowWidth, 25f, mx, my)) {
                        expandedMode = if (expandedMode == setting) null else setting
                    }
                    if (mouseButton == 0 && progress > 0.5f && inPanel) {
                        for ((index, mode) in setting.modes.withIndex()) {
                            val rowY = sy + 25f + index * 18f
                            if (RenderUtil.isInside(modeX, rowY, rowRight - modeX, 18f, mx, my)) {
                                if (mode != setting.value) {
                                    setting.value = mode
                                    ChatManager.setValue(module, setting, mode)
                                }
                                break
                            }
                        }
                    }
                    val selectedIndex = setting.modes.indexOf(setting.value).coerceAtLeast(0)
                    modeExpend.targetValue = if (expandedMode == setting) 1f else 0f
                    vLine.targetValue = 25f + selectedIndex * 18f

                    RenderUtil.drawTextVCenter(setting.name, sx, sy + 12.5f, Fonts.HarmonyOS, 6f, -1)
                    RenderUtil.drawTextVCenter(
                        setting.value,
                        sx + RenderUtil.getTextWidth(setting.name, Fonts.HarmonyOS, 6f) + sw * 0.05f,
                        sy + 12.5f, Fonts.HarmonyOS, 5f, 0xBFFFFFFF.toInt()
                    )
                    if (progress > 0f) {
                        RenderUtil.withClipping({
                            RenderUtil.drawRect(sx, sy + 25f, rowWidth, progress * totalExpandH, -1)
                        }, {
                            for ((index, mode) in setting.modes.withIndex()) {
                                val rowY = sy + 25f + index * 18f
                                RenderUtil.drawRect(modeX, rowY, rowRight - modeX, 18f, switchOffColor)
                                RenderUtil.drawTextVCenter(
                                    mode, modeX + 8f, rowY + 9f, Fonts.HarmonyOS, 5f,
                                    if (mode == setting.value) -1 else 0x9FFFFFFF.toInt()
                                )
                            }
                            RenderUtil.drawRect(modeX, sy + vLine.currentValue, 1f, 18f, themeColor)
                            RenderUtil.drawRect(
                                modeX, sy + 25f + totalExpandH - 1f, rowRight - modeX, 1f,
                                RenderUtil.alpha(themeColor, progress)
                            )
                        })
                    }
                }

                fun renderNumberSetting(setting: NumberSetting, sy: Float) {
                    val decimals = ChatManager.numberDecimals(setting)
                    val minText = ChatManager.formatNumber(setting.range.first, decimals)
                    val maxText = ChatManager.formatNumber(setting.range.second, decimals)
                    val nameWidth = RenderUtil.getTextWidth(setting.name, Fonts.HarmonyOS, 6f)
                    val minWidth = RenderUtil.getTextWidth(minText, Fonts.HarmonyOS, 4.5f)
                    val maxWidth = RenderUtil.getTextWidth(maxText, Fonts.HarmonyOS, 4.5f)
                    val trackX = sx + nameWidth + 10f + minWidth + 5f
                    val trackW = (sx + rowWidth - trackX - 5f - maxWidth).coerceAtLeast(24f)
                    val trackY = sy + 10.5f
                    val range = setting.range.second - setting.range.first
                    val slider = sliderXMap.getOrPut(setting, { EaseInOut(0.3f, 3) })

                    if (draggingNumber == setting) {
                        if (Mouse.isButtonDown(0)) {
                            val pos = (mx - trackX).coerceIn(0f, trackW)
                            slider.currentValue = pos
                            slider.targetValue = pos
                        }
                    } else {
                        val target = if (range <= 0f) 0f
                            else ((setting.value - setting.range.first) / range * trackW).coerceIn(0f, trackW)
                        slider.targetValue = target
                    }
                    if (mouseButton == 0 && inPanel && RenderUtil.isInside(trackX, sy, trackW, 25f, mx, my)) {
                        draggingModule = module
                        draggingNumber = setting
                        dragTrackX = trackX
                        dragTrackW = trackW
                        val pos = (mx - trackX).coerceIn(0f, trackW)
                        slider.currentValue = pos
                        slider.targetValue = pos
                    }

                    RenderUtil.drawTextVCenter(setting.name, sx, sy + 12.5f, Fonts.HarmonyOS, 6f, -1)
                    RenderUtil.drawTextVCenter(minText, sx + nameWidth + 10f, sy + 12.5f, Fonts.HarmonyOS, 4.5f, 0xAAFFFFFF.toInt())
                    RenderUtil.drawTextVCenter(maxText, trackX + trackW + 5f, sy + 12.5f, Fonts.HarmonyOS, 4.5f, 0xAAFFFFFF.toInt())
                    RenderUtil.drawRoundedRect(trackX, trackY, trackW, 4f, 2f, trackBaseColor)
                    RenderUtil.withClipping({
                        RenderUtil.drawRect(trackX, trackY, slider.currentValue, 4f, -1)
                    }, {
                        RenderUtil.drawRoundedRect(trackX, trackY, trackW, 4f, 2f, themeColor)
                    })
                    RenderUtil.drawCircle(trackX + slider.currentValue, trackY + 2f, 5f, sliderColor)
                }

                fun renderColorSetting(setting: ColorSetting, sy: Float) {
                    val blockX = sx + rowWidth - 10f
                    val blockY = sy + (25f - 10f) / 2f
                    val displayColor = if (draggingColor == setting) colorDragValue else setting.value
                    val (h, s, v) = if (draggingColor == setting)
                        Triple(colorDragH, colorDragS, colorDragV) else rgbToHsv(setting.value)

                    val sqSize = 60f
                    val barW = 8f
                    val pickerX = sx + (rowWidth - sqSize - barW - 8f) / 2f
                    val pickerY = sy + 25f + (100f - sqSize) / 2f
                    val sqX = pickerX
                    val sqY = pickerY
                    val barX = pickerX + sqSize + 8f
                    val barY = pickerY

                    if (mouseButton == 0 && inPanel) {
                        if (RenderUtil.isInside(sqX, sqY, sqSize, sqSize, mx, my)) {
                            draggingModule = module
                            draggingColor = setting
                            colorDragMode = ColorDragMode.SV
                            colorDragH = rgbToHsv(setting.value).first
                            colorDragS = ((mx - sqX) / sqSize).coerceIn(0f, 1f)
                            colorDragV = (1f - (my - sqY) / sqSize).coerceIn(0f, 1f)
                            colorDragValue = hsvToRgb(colorDragH, colorDragS, colorDragV)
                        } else if (RenderUtil.isInside(barX, barY, barW, sqSize, mx, my)) {
                            draggingModule = module
                            draggingColor = setting
                            colorDragMode = ColorDragMode.HUE
                            val current = rgbToHsv(setting.value)
                            colorDragS = current.second
                            colorDragV = current.third
                            colorDragH = ((my - barY) / sqSize * 360f).coerceIn(0f, 360f)
                            colorDragValue = hsvToRgb(colorDragH, colorDragS, colorDragV)
                        }
                    }
                    if (draggingColor == setting && Mouse.isButtonDown(0)) {
                        when (colorDragMode) {
                            ColorDragMode.SV -> {
                                colorDragS = ((mx - sqX) / sqSize).coerceIn(0f, 1f)
                                colorDragV = (1f - (my - sqY) / sqSize).coerceIn(0f, 1f)
                            }
                            ColorDragMode.HUE -> {
                                colorDragH = ((my - barY) / sqSize * 360f).coerceIn(0f, 360f)
                            }
                            else -> {}
                        }
                        colorDragValue = hsvToRgb(colorDragH, colorDragS, colorDragV)
                    }

                    val hex = "%06X".format(displayColor and 0xFFFFFF)
                    RenderUtil.drawTextVCenter(setting.name, sx, sy + 12.5f, Fonts.HarmonyOS, 6f, -1)
                    RenderUtil.drawTextVCenter(
                        hex, blockX - 5f - RenderUtil.getTextWidth(hex, Fonts.HarmonyOS, 5f),
                        sy + 12.5f, Fonts.HarmonyOS, 5f, 0xD8FFFFFF.toInt()
                    )
                    RenderUtil.drawRoundedRect(blockX, blockY, 10f, 10f, 2f, displayColor)

                    RenderUtil.drawHorizontalGradientRect(sqX, sqY, sqSize, sqSize, sliderColor, hsvToRgb(h, 1f, 1f))
                    RenderUtil.drawVerticalGradientRect(sqX, sqY, sqSize, sqSize, 0x00000000, 0xFF000000.toInt())
                    val markerX = sqX + s * sqSize
                    val markerY = sqY + (1f - v) * sqSize
                    RenderUtil.drawOutlinedRect(
                        markerX - 3.5f, markerY - 3.5f, 7f, 7f, 1f,
                        if (v > 0.6f) 0xFF000000.toInt() else sliderColor
                    )
                    for (i in 0 until 6) {
                        RenderUtil.drawVerticalGradientRect(
                            barX, barY + i * 10f, barW, 10f,
                            hsvToRgb(i * 60f, 1f, 1f), hsvToRgb((i + 1) * 60f, 1f, 1f)
                        )
                    }
                    val hueY = (barY + h / 360f * sqSize).coerceIn(barY + 1f, barY + sqSize - 1f)
                    RenderUtil.drawRect(barX - 2f, hueY - 1f, barW + 4f, 2f, sliderColor)
                }

                var sy = y + 10f + configurationScroll.currentValue
                for (setting in module.settings) {
                    val settingHeight = when (setting) {
                        is BooleanSetting -> 25f
                        is ModeSetting -> {
                            val modeExpend = modeSettingExpend.getOrPut(setting, { EaseInOut(0.3f, 3) })
                            25f + modeExpend.currentValue * 18f * setting.modes.size
                        }
                        is NumberSetting -> 25f
                        is ColorSetting -> 125f
                        else -> 25f
                    }
                    if (sy + settingHeight < y + searchHeight - 10f || sy > y + sHeight + 10f) {
                        sy += settingHeight
                        continue
                    }
                    when (setting) {
                        is BooleanSetting -> renderBooleanSetting(setting, sy)
                        is ModeSetting -> renderModeSetting(setting, sy)
                        is NumberSetting -> renderNumberSetting(setting, sy)
                        is ColorSetting -> renderColorSetting(setting, sy)
                    }
                    sy += settingHeight
                }
            }
        })
        if (release || ((draggingNumber != null || draggingColor != null) && !Mouse.isButtonDown(0))) {
            val dragModule = draggingModule
            val dragNum = draggingNumber
            if (dragNum != null && dragModule != null) {
                val slider = sliderXMap[dragNum]
                val range = dragNum.range.second - dragNum.range.first
                val pos = (slider?.currentValue ?: 0f).coerceIn(0f, dragTrackW)
                val finalValue = if (range > 0f) dragNum.range.first + pos / dragTrackW * range else dragNum.range.first
                dragNum.value = finalValue
                ChatManager.setValue(dragModule, dragNum, finalValue)
            }
            val dragCol = draggingColor
            if (dragCol != null && dragModule != null) {
                dragCol.value = colorDragValue
                ChatManager.setValue(dragModule, dragCol, colorDragValue)
            }
            draggingNumber = null
            draggingColor = null
            colorDragMode = null
            draggingModule = null
        }
        GlStateManager.color(1f, 1f, 1f, 1f)
        GlStateManager.enableTexture2D()
        GlStateManager.disableBlend()
        GlStateManager.enableDepth()
        GlStateManager.enableCull()
        org.lwjgl.opengl.GL13.glActiveTexture(org.lwjgl.opengl.GL13.GL_TEXTURE0)
    }

    fun preProcess() {
        if (mod.modules.isNotEmpty() && loadingModules)
            loadingModules = false
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

    private fun hsvToRgb(h: Float, s: Float, v: Float): Int {
        val hh = (((h % 360f) + 360f) % 360f) / 60f
        val i = hh.toInt().coerceIn(0, 5)
        val f = hh - i
        val p = v * (1f - s)
        val q = v * (1f - s * f)
        val t = v * (1f - s * (1f - f))
        val (r, g, b) = when (i) {
            0 -> Triple(v, t, p)
            1 -> Triple(q, v, p)
            2 -> Triple(p, v, t)
            3 -> Triple(p, q, v)
            4 -> Triple(t, p, v)
            else -> Triple(v, p, q)
        }
        return RenderUtil.getRGB(
            (r * 255f + 0.5f).toInt(),
            (g * 255f + 0.5f).toInt(),
            (b * 255f + 0.5f).toInt(),
            255
        )
    }

    private fun rgbToHsv(color: Int): Triple<Float, Float, Float> {
        val r = ((color shr 16) and 0xFF) / 255f
        val g = ((color shr 8) and 0xFF) / 255f
        val b = (color and 0xFF) / 255f
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val d = max - min
        var h = when {
            d == 0f -> 0f
            max == r -> 60f * (((g - b) / d) % 6f)
            max == g -> 60f * ((b - r) / d + 2f)
            else -> 60f * ((r - g) / d + 4f)
        }
        if (h < 0f) h += 360f
        val s = if (max == 0f) 0f else d / max
        return Triple(h, s, max)
    }

    private fun charIndexAt(relX: Float, widths: FloatArray): Int {
        var best = 0
        var bestDist = Float.MAX_VALUE
        for (i in widths.indices) {
            val d = abs(widths[i] - relX)
            if (d < bestDist) {
                bestDist = d
                best = i
            }
        }
        return best
    }

    override fun onGuiClosed() {
        super.onGuiClosed()
        searchTyping = false
        expandedMode = null
        draggingNumber = null
        draggingColor = null
        colorDragMode = null
        draggingModule = null
    }

    override fun doesGuiPauseGame(): Boolean {
        return false
    }

    override fun drawBackground(tint: Int) { }
}
