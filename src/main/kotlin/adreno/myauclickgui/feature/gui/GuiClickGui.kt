package adreno.myauclickgui.feature.gui

import adreno.myauclickgui.MyauClickGui
import adreno.myauclickgui.feature.managers.ChatManager
import adreno.myauclickgui.feature.types.fonts.Fonts
import adreno.myauclickgui.feature.types.logs.LogInfo
import adreno.myauclickgui.feature.types.module.Module
import adreno.myauclickgui.feature.types.module.settings.BooleanSetting
import adreno.myauclickgui.feature.types.module.settings.ColorSetting
import adreno.myauclickgui.feature.types.module.settings.FloatSetting
import adreno.myauclickgui.feature.types.module.settings.HideSetting
import adreno.myauclickgui.feature.types.module.settings.IntSetting
import adreno.myauclickgui.feature.types.module.settings.ModeSetting
import adreno.myauclickgui.feature.types.module.settings.NumberSetting
import adreno.myauclickgui.feature.types.module.settings.PercentSetting
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
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.util.Random
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object GuiClickGui : GuiScreen() {
    private val mod = MyauClickGui.getInstance()
    private val chat = mod.chatManager
    private val sound = ResourceLocation("minecraft", "random.click")

    override fun initGui() {
        SoundUtil.playDirect(sound)
        super.initGui()
        swallowOpenKeys = true
        if (mod.modules.isEmpty()) {
            loadingModules = true
            chat?.loadModules()
        } else {
            chat?.loadModuleStates()
        }
        chat?.loadConfigs()
        // Some modules gain or lose settings at runtime, so refresh whatever panel is
        // still open from last time.
        configuringModule?.let { reloadSettings(it) }
    }

    /**
     * (Re)loads [module]'s settings. When it already has settings on screen the reload
     * runs silently in the background and swaps them in when done, so the panel stays
     * usable instead of flashing the loading placeholder.
     */
    private fun reloadSettings(module: Module) {
        val silent = module.settings.isNotEmpty()
        silentSettingsReload = silent
        Thread({
            val settings = chat!!.loadSettingsForModule(module) { s ->
                if (!silent) loadingSettingsSuffix = s
            }
            mc.addScheduledTask {
                if (settings.isNotEmpty()) {
                    module.settings = withHideSetting(module, settings)
                    switchXMap.clear()
                    switchBgMap.clear()
                }
                silentSettingsReload = false
            }
        }, "MyauClickGui-${module.name}SettingsGetter")
            .apply { isDaemon = true }.start()
    }

    /** Prepends the synthetic "Hide" toggle that drives Myau's .show/.hide commands. */
    private fun withHideSetting(module: Module, settings: ArrayList<Setting<*>>): ArrayList<Setting<*>> {
        if (settings.firstOrNull() is HideSetting) return settings
        val combined = ArrayList<Setting<*>>(settings.size + 1)
        combined.add(HideSetting(module))
        combined.addAll(settings)
        return combined
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
    private var rankedSearchQuery = ""
    private var rankedModuleFingerprint = 0
    private val searchIcon = ResourceLocation("myauclickgui", "images/search.png")
    private val moduleOpenMap = LinkedHashMap<Module, EaseInOut>()
    private val switchXMap = LinkedHashMap<BooleanSetting, EaseInOut>()
    private val switchBgMap = LinkedHashMap<BooleanSetting, SmoothColor>()
    private val sliderXMap = LinkedHashMap<NumberSetting, EaseInOut>()
    private val intSliderXMap = LinkedHashMap<IntSetting, EaseInOut>()
    private val floatSliderXMap = LinkedHashMap<FloatSetting, EaseInOut>()
    private val percentSliderXMap = LinkedHashMap<PercentSetting, EaseInOut>()
    private val modeSettingExpend = LinkedHashMap<ModeSetting, EaseInOut>()
    private val modeSettingVLine = LinkedHashMap<ModeSetting, EaseOut>()
    private val colorSettingExpend = LinkedHashMap<ColorSetting, EaseInOut>()
    private val expandedModes = HashSet<ModeSetting>()
    private val expandedColors = HashSet<ColorSetting>()
    private var draggingModule: Module? = null
    private var draggingNumber: NumberSetting? = null
    private var draggingInt: IntSetting? = null
    private var draggingFloat: FloatSetting? = null
    private var draggingPercent: PercentSetting? = null
    private var draggingColor: ColorSetting? = null
    private var colorDragMode: ColorDragMode? = null
    private var colorDragH = 0f
    private var colorDragS = 0f
    private var colorDragV = 0f
    private var colorDragValue = 0xFFFFFFFF.toInt()
    private var dragTrackX = 0f
    private var dragTrackW = 0f
    private val logOffset = EaseInOut(0.2f, 3)
    private val logBgWidth = EaseInOut(0.3f, 2)
    private val logHeights: ArrayDeque<Float> = ArrayDeque()
    private val r = Random()

    private enum class ColorDragMode { SV, HUE }

    private const val themeColor = 0xFF93A8E4.toInt()
    private const val switchOffColor = 0xE0040210.toInt()
    private const val switchOnColor = 0xE0383D5B.toInt()
    private const val trackBaseColor = 0x3DFFFFFF.toInt()
    private const val sliderColor = 0xFFFFFFFF.toInt()

    private var loadingModules = false
    private var loadingSettingsSuffix = ""
    // The keypress that opened this screen also reaches keyTyped; swallow input until
    // every key held at open time has been released.
    private var swallowOpenKeys = true
    // Set while settings are being refreshed in the background for a module that
    // already has values on screen, so the loading placeholder stays hidden.
    private var silentSettingsReload = false

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

        val logsSnapshot: List<LogInfo>
        synchronized(mod.logs) {
            // Entries only ever leave from the front: expiry below and the size cap in
            // ChatUtil.clog both removeFirst. Track the height they take with them so the
            // bottom-anchored stack can be shifted by the same amount.
            var removedHeight = 0f

            // remove entries older than 10 seconds
            val expireNs = 10_000_000_000L
            while (mod.logs.isNotEmpty() && System.nanoTime() - mod.logs.peekFirst().initTime > expireNs) {
                mod.logs.removeFirst()
                if (logHeights.isNotEmpty()) removedHeight += logHeights.removeFirst()
            }
            // Catch up on entries dropped by the size cap outside this block.
            while (logHeights.size > mod.logs.size) {
                removedHeight += logHeights.removeFirst()
            }

            logsSnapshot = ArrayList(mod.logs)

            // Measure entries appended since the last frame; they extend the tail.
            var addedHeight = 0f
            for (i in logHeights.size until logsSnapshot.size) {
                val h = RenderUtil.getTextHeight(logsSnapshot[i].content, Fonts.HarmonyOS, 6f).toFloat()
                logHeights.addLast(h)
                addedHeight += h
            }

            // Removing from the front shortens the stack at the top. Shrinking the offset
            // and the anchor together leaves the surviving entries where they already are,
            // instead of snapping them up by the removed height and easing back down.
            if (removedHeight > 0f) {
                val settled = logOffset.currentValue
                logOffset.targetValue = max(0f, logOffset.targetValue - removedHeight)
                logOffset.currentValue = max(0f, settled - removedHeight)
            }
            if (addedHeight > 0f) {
                logOffset.targetValue += addedHeight
            }

            var maxWidth = 0f
            var ly = sr.scaledHeight - logOffset.currentValue
            RenderUtil.renderBlur({
                    RenderUtil.drawRoundedRect(0f, max(ly - 15f, 0f), logBgWidth.currentValue + 10f, sr.scaledHeight.toFloat(), 0f, 5f, 0f, 0f, -1)
                }, 32)
            RenderUtil.drawRoundedRect(0f, max(ly - 15f, 0f), logBgWidth.currentValue + 10f, sr.scaledHeight.toFloat(), 0f, 5f, 0f, 0f, 0xD5070312.toInt())
            for ((log, h) in logsSnapshot.asSequence().zip(logHeights.asSequence())) {
                if (ly >= sr.scaledHeight) break
                if (ly + h > 0f) {
                    val elapsed = (System.nanoTime() - log.initTime) / 1_000_000_000f
                    val alpha = min(1f, elapsed / 0.5f) * (1f - max(0f, (elapsed - 9f) / 1f))
                    RenderUtil.drawTextWithFormatting(log.content, 5f, ly, Fonts.HarmonyOS, 6f,
                        RenderUtil.getRGB(1f, 1f, 1f, alpha))
                }
                maxWidth = max(maxWidth, RenderUtil.getTextWidth(log.content, Fonts.HarmonyOS, 6f))
                ly += h
            }
            logBgWidth.targetValue = maxWidth
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
            RenderUtil.withClipping(typingX, y, typingWidth - 4f, searchHeight) {
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
            }
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
            RenderUtil.withClipping(x, y + searchHeight, lWidth, sHeight - searchHeight) {
                updateSearchRanking()
                for (module in shownModules) {
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
                            draggingModule = null
                            draggingNumber = null
                            draggingInt = null
                            draggingFloat = null
                            draggingPercent = null
                            draggingColor = null
                            colorDragMode = null
                            switchXMap.clear()
                            switchBgMap.clear()
                            configurationScroll.targetValue = 0f
                            reloadSettings(module)
                        }
                    }
                    RenderUtil.drawRoundedRect(
                        x + open.currentValue * hGap, dy, lWidth - hGap, 25f,
                        leftR, rightR, rightR, leftR, 0xA11D1072.toInt()
                    )
                    RenderUtil.drawTextCenter(module.name, x + lWidth / 2f, dy + 12.5f, Fonts.HarmonyOS, 6f, -1)
                }
            }
            dy = y + searchHeight + 5 + moduleScroll.currentValue - cHeight - vGap
            for (module in shownModules) {
                val open = moduleOpenMap.getOrPut(module, { EaseInOut(0.3f, 3) })
                open.targetValue = if (module.state) 1f else 0f
                dy += cHeight + vGap
                if (dy + 25f < listTop - 10f || dy > listBottom + 10f) {
                    continue
                }
                val leftR = open.currentValue * cHeight / 2f
                val rightR = cHeight / 2f - leftR
                RenderUtil.withClipping(x, y + searchHeight, lWidth, sHeight - searchHeight) {
                    RenderUtil.withClipping(x + open.currentValue * hGap, dy, lWidth - hGap, 25f) {
                        RenderUtil.drawHorizontalGradientRect(x, dy, hGap, 25f, 0xC1B22949.toInt(), 0x00000000.toInt())
                        RenderUtil.drawHorizontalGradientRect(x + lWidth - hGap, dy, hGap, 25f, 0x00000000.toInt(), 0xC1109B3E.toInt())
                    }
                }
            }
        }
        if (mouseButton == 0 && !RenderUtil.isInside(x, y, lWidth + rWidth, sHeight, 8f, mouseX.toFloat(), mouseY.toFloat())) {
            configuringModule = null
        }
        RenderUtil.withClipping(x + lWidth, y, rWidth, sHeight) {
            // A silent reload keeps the current values on screen, so only show the
            // placeholder when there is nothing to display yet.
            if (loadingSettingsSuffix.isNotEmpty() && !silentSettingsReload) {
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
                        val newValue = !setting.value
                        if (setting is HideSetting) {
                            ChatManager.setValue(setting, newValue)
                        } else {
                            setting.value = newValue
                            ChatManager.setValue(module, setting, newValue)
                        }
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
                            currentValue = 25f + setting.modes.indexOf(setting.value).coerceAtLeast(0) * 20f
                        }
                    })
                    val progress = modeExpend.currentValue
                    val totalExpandH = setting.modes.size * 20f
                    val modeX = sx + 12f
                    val rowRight = sx + rowWidth

                    if (mouseButton != null && inPanel && RenderUtil.isInside(sx, sy, rowWidth, 25f, mx, my)) {
                        if (expandedModes.contains(setting)) {
                            expandedModes.remove(setting)
                        } else {
                            expandedModes.add(setting)
                        }
                    }
                    if (mouseButton == 0 && progress > 0.5f && inPanel) {
                        for ((index, mode) in setting.modes.withIndex()) {
                            val rowY = sy + 25f + index * 20f
                            if (RenderUtil.isInside(modeX, rowY, rowRight - modeX, 20f, mx, my)) {
                                if (mode != setting.value) {
                                    setting.value = mode
                                    ChatManager.setValue(module, setting, mode)
                                }
                                break
                            }
                        }
                    }
                    val selectedIndex = setting.modes.indexOf(setting.value).coerceAtLeast(0)
                    modeExpend.targetValue = if (expandedModes.contains(setting)) 1f else 0f
                    vLine.targetValue = 25f + selectedIndex * 20f

                    RenderUtil.drawTextVCenter(setting.name, sx, sy + 12.5f, Fonts.HarmonyOS, 6f, -1)
                    RenderUtil.drawTextVCenter(
                        setting.value,
                        sx + RenderUtil.getTextWidth(setting.name, Fonts.HarmonyOS, 6f) + sw * 0.05f,
                        sy + 12.5f, Fonts.HarmonyOS, 5f, 0xBFFFFFFF.toInt()
                    )
                    if (progress > 0f) {
                        RenderUtil.withClipping(sx, sy + 25f, rowWidth, progress * totalExpandH) {
                            for ((index, mode) in setting.modes.withIndex()) {
                                val rowY = sy + 25f + index * 20f
                                RenderUtil.drawRect(modeX, rowY, rowRight - modeX, 20f, switchOffColor)
                                RenderUtil.drawTextVCenter(
                                    mode, modeX + 8f, rowY + 9f, Fonts.HarmonyOS, 5f,
                                    if (mode == setting.value) -1 else 0x9FFFFFFF.toInt()
                                )
                            }
                            RenderUtil.drawRect(modeX, sy + vLine.currentValue, 1f, 20f, themeColor)
                            RenderUtil.drawRect(
                                modeX, sy + 25f + totalExpandH - 1f, rowRight - modeX, 1f,
                                RenderUtil.alpha(themeColor, progress)
                            )
                        }
                    }
                }

                // Reserves a fixed-width slot for the live value label so the track does
                // not shift horizontally while the value changes during a drag.
                fun sliderLayout(name: String, minText: String, maxText: String, rangeText: String): Triple<Float, Float, Float> {
                    val nameWidth = RenderUtil.getTextWidth(name, Fonts.HarmonyOS, 6f)
                    val slotWidth = max(
                        RenderUtil.getTextWidth(minText, Fonts.HarmonyOS, 4.5f),
                        RenderUtil.getTextWidth(maxText, Fonts.HarmonyOS, 4.5f)
                    )
                    val rangeWidth = RenderUtil.getTextWidth(rangeText, Fonts.HarmonyOS, 4.5f)
                    val valueSlotX = sx + nameWidth + 10f
                    val trackX = valueSlotX + slotWidth + 8f
                    val trackW = (sx + rowWidth - trackX - 5f - rangeWidth).coerceAtLeast(24f)
                    return Triple(trackX, trackW, valueSlotX)
                }

                fun drawSliderRow(
                    name: String, currentText: String, rangeText: String,
                    valueSlotX: Float, trackX: Float, trackW: Float, sy: Float, sliderPos: Float
                ) {
                    val trackY = sy + 10.5f
                    RenderUtil.drawTextVCenter(name, sx, sy + 12.5f, Fonts.HarmonyOS, 6f, -1)
                    RenderUtil.drawTextVCenter(currentText, valueSlotX, sy + 12.5f, Fonts.HarmonyOS, 4.5f, -1)
                    RenderUtil.drawTextVCenter(rangeText, trackX + trackW + 5f, sy + 12.5f, Fonts.HarmonyOS, 4.5f, 0x88FFFFFF.toInt())
                    RenderUtil.drawRoundedRect(trackX, trackY, trackW, 4f, 2f, trackBaseColor)
                    RenderUtil.withClipping(trackX, trackY, sliderPos, 4f) {
                        RenderUtil.drawRoundedRect(trackX, trackY, trackW, 4f, 2f, themeColor)
                    }
                    RenderUtil.drawCircle(trackX + sliderPos, trackY + 2f, 5f, sliderColor)
                }

                fun renderNumberSetting(setting: NumberSetting, sy: Float) {
                    val decimals = ChatManager.numberDecimals(setting)
                    val minText = ChatManager.formatNumber(setting.range.first, decimals)
                    val maxText = ChatManager.formatNumber(setting.range.second, decimals)
                    val rangeText = "$minText-$maxText"
                    val (trackX, trackW, valueSlotX) = sliderLayout(setting.name, minText, maxText, rangeText)
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

                    val sliderPos = slider.currentValue
                    // While dragging, derive the label from the live handle position.
                    val liveValue = if (draggingNumber == setting && trackW > 0f)
                        setting.range.first + sliderPos / trackW * range else setting.value
                    val currentText = ChatManager.formatNumber(ChatManager.roundNumber(liveValue, decimals), decimals)
                    drawSliderRow(setting.name, currentText, rangeText, valueSlotX, trackX, trackW, sy, sliderPos)
                }

                fun renderIntSetting(setting: IntSetting, sy: Float) {
                    val minText = setting.range.first.toString()
                    val maxText = setting.range.second.toString()
                    val rangeText = "$minText-$maxText"
                    val (trackX, trackW, valueSlotX) = sliderLayout(setting.name, minText, maxText, rangeText)
                    val range = setting.range.second - setting.range.first
                    val slider = intSliderXMap.getOrPut(setting) { EaseInOut(0.3f, 3) }

                    if (draggingInt == setting) {
                        if (Mouse.isButtonDown(0)) {
                            val pos = (mx - trackX).coerceIn(0f, trackW)
                            slider.currentValue = pos
                            slider.targetValue = pos
                        }
                    } else {
                        val target = if (range <= 0) 0f
                            else ((setting.value - setting.range.first).toFloat() / range * trackW).coerceIn(0f, trackW)
                        slider.targetValue = target
                    }
                    if (mouseButton == 0 && inPanel && RenderUtil.isInside(trackX, sy, trackW, 25f, mx, my)) {
                        draggingModule = module
                        draggingInt = setting
                        dragTrackX = trackX
                        dragTrackW = trackW
                        val pos = (mx - trackX).coerceIn(0f, trackW)
                        slider.currentValue = pos
                        slider.targetValue = pos
                    }

                    val sliderPos = slider.currentValue
                    val liveValue = if (draggingInt == setting && trackW > 0f)
                        kotlin.math.round(setting.range.first + sliderPos / trackW * range).toInt()
                            .coerceIn(setting.range.first, setting.range.second)
                        else setting.value
                    drawSliderRow(setting.name, liveValue.toString(), rangeText, valueSlotX, trackX, trackW, sy, sliderPos)
                }

                fun renderFloatSetting(setting: FloatSetting, sy: Float) {
                    val decimals = ChatManager.numberDecimals(setting)
                    val minText = ChatManager.formatNumber(setting.range.first, decimals)
                    val maxText = ChatManager.formatNumber(setting.range.second, decimals)
                    val rangeText = "$minText-$maxText"
                    val (trackX, trackW, valueSlotX) = sliderLayout(setting.name, minText, maxText, rangeText)
                    val range = setting.range.second - setting.range.first
                    val slider = floatSliderXMap.getOrPut(setting) { EaseInOut(0.3f, 3) }

                    if (draggingFloat == setting) {
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
                        draggingFloat = setting
                        dragTrackX = trackX
                        dragTrackW = trackW
                        val pos = (mx - trackX).coerceIn(0f, trackW)
                        slider.currentValue = pos
                        slider.targetValue = pos
                    }

                    val sliderPos = slider.currentValue
                    val liveValue = if (draggingFloat == setting && trackW > 0f)
                        setting.range.first + sliderPos / trackW * range else setting.value
                    val currentText = ChatManager.formatNumber(ChatManager.roundNumber(liveValue, decimals), decimals)
                    drawSliderRow(setting.name, currentText, rangeText, valueSlotX, trackX, trackW, sy, sliderPos)
                }

                fun renderPercentSetting(setting: PercentSetting, sy: Float) {
                    val minText = "${kotlin.math.round(setting.range.first).toLong()}%"
                    val maxText = "${kotlin.math.round(setting.range.second).toLong()}%"
                    val rangeText = "$minText-$maxText"
                    val (trackX, trackW, valueSlotX) = sliderLayout(setting.name, minText, maxText, rangeText)
                    val range = setting.range.second - setting.range.first
                    val slider = percentSliderXMap.getOrPut(setting) { EaseInOut(0.3f, 3) }

                    if (draggingPercent == setting) {
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
                        draggingPercent = setting
                        dragTrackX = trackX
                        dragTrackW = trackW
                        val pos = (mx - trackX).coerceIn(0f, trackW)
                        slider.currentValue = pos
                        slider.targetValue = pos
                    }

                    val sliderPos = slider.currentValue
                    val liveValue = if (draggingPercent == setting && trackW > 0f)
                        setting.range.first + sliderPos / trackW * range else setting.value
                    val currentText = "${kotlin.math.round(liveValue).toLong()}%"
                    drawSliderRow(setting.name, currentText, rangeText, valueSlotX, trackX, trackW, sy, sliderPos)
                }

                fun renderColorSetting(setting: ColorSetting, sy: Float) {
                    val colorExpend = colorSettingExpend.getOrPut(setting) { EaseInOut(0.3f, 3) }
                    colorExpend.targetValue = if (expandedColors.contains(setting)) 1f else 0f
                    val expandProgress = colorExpend.currentValue

                    val blockX = sx + rowWidth - 10f
                    val blockY = sy + (25f - 10f) / 2f
                    val displayColor = if (draggingColor == setting) colorDragValue else setting.value
                    val (h, s, v) = if (draggingColor == setting)
                        Triple(colorDragH, colorDragS, colorDragV) else rgbToHsv(setting.value)

                    val sqSize = 150f
                    val barW = 8f
                    val pickerX = sx + (rowWidth - sqSize - barW - 8f) / 2f
                    val pickerY = sy + 25f
                    val barX = pickerX + sqSize + 8f

                    if (mouseButton != null && inPanel && RenderUtil.isInside(sx, sy, rowWidth, 25f, mx, my)) {
                        if (expandedColors.contains(setting)) expandedColors.remove(setting)
                        else expandedColors.add(setting)
                    }

                    val clipH = expandProgress * 150f
                    if (expandProgress > 0f && mouseButton == 0 && inPanel) {
                        if (RenderUtil.isInside(pickerX, pickerY, sqSize, clipH.coerceAtMost(sqSize), mx, my)) {
                            draggingModule = module
                            draggingColor = setting
                            colorDragMode = ColorDragMode.SV
                            colorDragH = rgbToHsv(setting.value).first
                            colorDragS = ((mx - pickerX) / sqSize).coerceIn(0f, 1f)
                            colorDragV = (1f - (my - pickerY) / sqSize).coerceIn(0f, 1f)
                            colorDragValue = hsvToRgb(colorDragH, colorDragS, colorDragV)
                        } else if (RenderUtil.isInside(barX, pickerY, barW, clipH.coerceAtMost(sqSize), mx, my)) {
                            draggingModule = module
                            draggingColor = setting
                            colorDragMode = ColorDragMode.HUE
                            val current = rgbToHsv(setting.value)
                            colorDragS = current.second
                            colorDragV = current.third
                            colorDragH = ((my - pickerY) / sqSize * 360f).coerceIn(0f, 360f)
                            colorDragValue = hsvToRgb(colorDragH, colorDragS, colorDragV)
                        }
                    }
                    if (draggingColor == setting && Mouse.isButtonDown(0)) {
                        when (colorDragMode) {
                            ColorDragMode.SV -> {
                                colorDragS = ((mx - pickerX) / sqSize).coerceIn(0f, 1f)
                                colorDragV = (1f - (my - pickerY) / sqSize).coerceIn(0f, 1f)
                            }
                            ColorDragMode.HUE -> {
                                colorDragH = ((my - pickerY) / sqSize * 360f).coerceIn(0f, 360f)
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

                    if (clipH > 0f) {
                        RenderUtil.withClipping(pickerX, pickerY, sqSize + barW + 8f, clipH) {
                            RenderUtil.drawHorizontalGradientRect(pickerX,
                                pickerY, sqSize, sqSize, sliderColor, hsvToRgb(h, 1f, 1f))
                            RenderUtil.drawVerticalGradientRect(pickerX, pickerY, sqSize, sqSize, 0x00000000, 0xFF000000.toInt())
                            val markerX = pickerX + s * sqSize
                            val markerY = pickerY + (1f - v) * sqSize
                            RenderUtil.drawOutlinedRect(
                                markerX - 3.5f, markerY - 3.5f, 7f, 7f, 1f,
                                if (v > 0.6f) 0xFF000000.toInt() else sliderColor
                            )
                            val segH = sqSize / 6f
                            for (i in 0 until 6) {
                                RenderUtil.drawVerticalGradientRect(
                                    barX, pickerY + i * segH, barW, segH,
                                    hsvToRgb(i * 60f, 1f, 1f), hsvToRgb((i + 1) * 60f, 1f, 1f)
                                )
                            }
                            val hueY = (pickerY + h / 360f * sqSize).coerceIn(pickerY + 1f, pickerY + sqSize - 1f)
                            RenderUtil.drawRect(barX - 2f, hueY - 1f, barW + 4f, 2f, sliderColor)
                        }
                    }
                }

                var sy = y + 10f + configurationScroll.currentValue
                for (setting in module.settings) {
                    val settingHeight = when (setting) {
                        is BooleanSetting -> 25f
                        is ModeSetting -> {
                            val modeExpend = modeSettingExpend.getOrPut(setting, { EaseInOut(0.3f, 3) })
                            25f + modeExpend.currentValue * 20f * setting.modes.size
                        }
                        is NumberSetting -> 25f
                        is IntSetting -> 25f
                        is FloatSetting -> 25f
                        is PercentSetting -> 25f
                        is ColorSetting -> {
                            val colorExpend = colorSettingExpend.getOrPut(setting, { EaseInOut(0.3f, 3) })
                            25f + colorExpend.currentValue * 150f
                        }
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
                        is IntSetting -> renderIntSetting(setting, sy)
                        is FloatSetting -> renderFloatSetting(setting, sy)
                        is PercentSetting -> renderPercentSetting(setting, sy)
                        is ColorSetting -> renderColorSetting(setting, sy)
                    }
                    sy += settingHeight
                }
            }
        }
        if (release || ((draggingNumber != null || draggingInt != null || draggingFloat != null || draggingPercent != null || draggingColor != null) && !Mouse.isButtonDown(0))) {
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
            val dragInt = draggingInt
            if (dragInt != null && dragModule != null) {
                val slider = intSliderXMap[dragInt]
                val range = dragInt.range.second - dragInt.range.first
                val pos = (slider?.currentValue ?: 0f).coerceIn(0f, dragTrackW)
                val finalValue = if (range > 0) (dragInt.range.first + pos / dragTrackW * range).toInt().coerceIn(dragInt.range.first, dragInt.range.second) else dragInt.range.first
                dragInt.value = finalValue
                ChatManager.setValue(dragModule, dragInt, finalValue)
            }
            val dragFloat = draggingFloat
            if (dragFloat != null && dragModule != null) {
                val slider = floatSliderXMap[dragFloat]
                val range = dragFloat.range.second - dragFloat.range.first
                val pos = (slider?.currentValue ?: 0f).coerceIn(0f, dragTrackW)
                val finalValue = if (range > 0f) dragFloat.range.first + pos / dragTrackW * range else dragFloat.range.first
                dragFloat.value = finalValue
                ChatManager.setValue(dragModule, dragFloat, finalValue)
            }
            val dragPct = draggingPercent
            if (dragPct != null && dragModule != null) {
                val slider = percentSliderXMap[dragPct]
                val range = dragPct.range.second - dragPct.range.first
                val pos = (slider?.currentValue ?: 0f).coerceIn(0f, dragTrackW)
                val finalValue = if (range > 0f) dragPct.range.first + pos / dragTrackW * range else dragPct.range.first
                dragPct.value = finalValue
                ChatManager.setValue(dragModule, dragPct, finalValue)
            }
            val dragCol = draggingColor
            if (dragCol != null && dragModule != null) {
                dragCol.value = colorDragValue
                ChatManager.setValue(dragModule, dragCol, colorDragValue)
            }
            draggingNumber = null
            draggingInt = null
            draggingFloat = null
            draggingPercent = null
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
        // The key that opened this screen is still down and would otherwise be typed
        // into the search box. Ignore input until it is released.
        if (swallowOpenKeys) {
            if (Keyboard.isKeyDown(keyCode)) {
                super.keyTyped(typedChar, keyCode)
                return
            }
            swallowOpenKeys = false
        }
        val textBefore = searchText
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
        // A changed query reorders the list, so animate it back to the top.
        if (searchText != textBefore) {
            moduleScroll.targetValue = 0f
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

    private data class SearchCandidate(val module: Module, val score: Float, val order: Int)

    private fun updateSearchRanking() {
        val query = searchText.trim().lowercase(java.util.Locale.ROOT)
        var fingerprint = mod.modules.size
        for (module in mod.modules) fingerprint = fingerprint * 31 + module.name.hashCode()
        if (query == rankedSearchQuery && fingerprint == rankedModuleFingerprint && shownModules.size == mod.modules.size) return

        rankedSearchQuery = query
        rankedModuleFingerprint = fingerprint
        shownModules.clear()
        if (query.isEmpty()) {
            shownModules.addAll(mod.modules)
            return
        }

        val compactQuery = query.filter { it.isLetterOrDigit() }
        val candidates = mod.modules.mapIndexed { index, module ->
            SearchCandidate(module, searchConfidence(module.name, query, compactQuery), index)
        }
        candidates.sortedWith(compareByDescending<SearchCandidate> { it.score }.thenBy { it.order })
            .forEach { shownModules.add(it.module) }
    }

    private fun searchConfidence(rawName: String, query: String, compactQuery: String): Float {
        val name = rawName.lowercase(java.util.Locale.ROOT)
        val compactName = name.filter { it.isLetterOrDigit() }
        if (compactQuery.isEmpty()) return 0f

        var score = 0f
        if (name == query) score += 1_000_000f
        if (name.startsWith(query)) score += 220_000f + query.length * 500f

        val lengthDistance = abs(compactName.length - compactQuery.length).toFloat()
        val lengthSimilarity = 1f - (lengthDistance / maxOf(compactName.length, compactQuery.length, 1)).coerceIn(0f, 1f)
        score += lengthSimilarity * 18_000f

        val acronym = buildAcronym(rawName)
        if (acronym == compactQuery) score += 160_000f
        else if (acronym.startsWith(compactQuery)) score += 45_000f + compactQuery.length * 300f
        else if (isSubsequence(compactQuery, acronym)) score += 18_000f

        val contiguous = longestCommonSubstring(compactName, compactQuery)
        score += contiguous.toFloat() / compactQuery.length * 42_000f

        val ordered = orderedCoverage(compactName, compactQuery)
        score += ordered * 26_000f

        val distribution = characterDistribution(compactName, compactQuery)
        score += distribution * 24_000f

        score += boundaryCoverage(rawName, compactQuery) * 16_000f
        score += (1f - levenshteinDistance(compactName, compactQuery).toFloat() /
            maxOf(compactName.length, compactQuery.length, 1)) * 12_000f
        return score
    }

    private fun buildAcronym(value: String): String {
        val result = StringBuilder()
        var boundary = true
        for (index in value.indices) {
            val c = value[index]
            if (!c.isLetterOrDigit()) {
                boundary = true
                continue
            }
            val uppercaseBoundary = index > 0 && c.isUpperCase() && value[index - 1].isLowerCase()
            if (boundary || uppercaseBoundary) result.append(c.lowercaseChar())
            boundary = false
        }
        return result.toString()
    }

    private fun isSubsequence(needle: String, haystack: String): Boolean {
        var cursor = 0
        for (c in haystack) if (cursor < needle.length && c == needle[cursor]) cursor++
        return cursor == needle.length
    }

    private fun longestCommonSubstring(first: String, second: String): Int {
        if (first.isEmpty() || second.isEmpty()) return 0
        var previous = IntArray(second.length + 1)
        var best = 0
        for (a in first) {
            val current = IntArray(second.length + 1)
            for (j in second.indices) {
                if (a == second[j]) {
                    current[j + 1] = previous[j] + 1
                    best = maxOf(best, current[j + 1])
                }
            }
            previous = current
        }
        return best
    }

    private fun orderedCoverage(name: String, query: String): Float {
        var cursor = 0
        var matched = 0
        for (c in query) {
            while (cursor < name.length && name[cursor] != c) cursor++
            if (cursor == name.length) break
            matched++
            cursor++
        }
        return matched.toFloat() / query.length
    }

    private fun characterDistribution(name: String, query: String): Float {
        val available = name.groupingBy { it }.eachCount().toMutableMap()
        var matched = 0
        for (c in query) {
            val count = available[c] ?: 0
            if (count > 0) {
                available[c] = count - 1
                matched++
            }
        }
        return matched.toFloat() / query.length
    }

    private fun boundaryCoverage(rawName: String, query: String): Float {
        val boundaries = buildAcronym(rawName)
        if (boundaries.isEmpty()) return 0f
        var matched = 0
        for (c in query) if (boundaries.indexOf(c) >= 0) matched++
        return matched.toFloat() / query.length
    }

    private fun levenshteinDistance(first: String, second: String): Int {
        var previous = IntArray(second.length + 1) { it }
        for (i in first.indices) {
            val current = IntArray(second.length + 1)
            current[0] = i + 1
            for (j in second.indices) {
                current[j + 1] = minOf(
                    current[j] + 1,
                    previous[j + 1] + 1,
                    previous[j] + if (first[i] == second[j]) 0 else 1
                )
            }
            previous = current
        }
        return previous[second.length]
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
        draggingNumber = null
        draggingInt = null
        draggingFloat = null
        draggingPercent = null
        draggingColor = null
        colorDragMode = null
        draggingModule = null
        // Clean up any clip state left over by an aborted frame (e.g. exception
        // thrown inside withClipping).
        RenderUtil.resetClipState()
    }

    override fun doesGuiPauseGame(): Boolean {
        return false
    }

    override fun drawBackground(tint: Int) { }
}
