package adreno.myauclickgui.feature.utils

import adreno.myauclickgui.feature.types.fonts.Font
import adreno.myauclickgui.feature.types.other.ARGBColor
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.ScaledResolution
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.texture.TextureUtil
import net.minecraft.client.shader.Framebuffer
import net.minecraft.util.ResourceLocation
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.ARBShaderObjects
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL13
import org.lwjgl.opengl.GL20
import org.lwjgl.opengl.GL30
import java.awt.image.BufferedImage
import java.nio.IntBuffer
import java.util.*
import javax.imageio.ImageIO

object RenderUtil {
    private val mc = Minecraft.getMinecraft()
    private val scissorStack: Deque<IntArray> = ArrayDeque()
    private var stencilDepth = 0
    private const val MAX_STENCIL_DEPTH = 8
    private val textureCache = HashMap<ResourceLocation, Int>()
    private var blurFbo: Framebuffer? = null
    private var blurFbo2: Framebuffer? = null
    private var blurProgram = -1
    private var blurTexelSizeLoc = -1
    private var blurDirectionLoc = -1
    private var blurSamplerLoc = -1

    private val BLUR_SHADER = """
precision highp float;

uniform sampler2D texture;
uniform vec2 texelSize;
uniform vec2 direction;

void main() {
    vec2 uv = gl_TexCoord[0].xy;
    vec4 sum = texture2D(texture, uv) * 0.2270270270;
    vec2 offset = texelSize * direction;
    sum += texture2D(texture, uv + offset) * 0.1945945946;
    sum += texture2D(texture, uv - offset) * 0.1945945946;
    sum += texture2D(texture, uv + offset * 2.0) * 0.1216216216;
    sum += texture2D(texture, uv - offset * 2.0) * 0.1216216216;
    sum += texture2D(texture, uv + offset * 3.0) * 0.0540540541;
    sum += texture2D(texture, uv - offset * 3.0) * 0.0540540541;
    sum += texture2D(texture, uv + offset * 4.0) * 0.0162162162;
    sum += texture2D(texture, uv - offset * 4.0) * 0.0162162162;
    gl_FragColor = sum;
}
""".trimIndent()

    @JvmStatic
    fun drawRect(x: Float, y: Float, w: Float, h: Float, color: Int) {
        setGLState(color)
        GL11.glBegin(GL11.GL_QUADS)
        GL11.glVertex2f(x, y + h)
        GL11.glVertex2f(x + w, y + h)
        GL11.glVertex2f(x + w, y)
        GL11.glVertex2f(x, y)
        GL11.glEnd()
        restoreGLState()
    }

    @JvmStatic
    fun drawOutlinedRect(x: Float, y: Float, w: Float, h: Float, thickness: Float, color: Int) {
        drawRect(x, y, w, thickness, color)
        drawRect(x, y + h - thickness, w, thickness, color)
        drawRect(x, y + thickness, thickness, h - thickness * 2f, color)
        drawRect(x + w - thickness, y + thickness, thickness, h - thickness * 2f, color)
    }

    @JvmStatic
    fun drawRoundedRect(x: Float, y: Float, w: Float, h: Float, radius: Float, color: Int) {
        drawRoundedRect(x, y, w, h, radius, radius, radius, radius, color)
    }

    @JvmStatic
    fun drawRoundedRect(x: Float, y: Float, w: Float, h: Float,
                        topLeft: Float, topRight: Float, bottomRight: Float, bottomLeft: Float, color: Int) {
        val maxR = Math.min(w, h) / 2f
        val tl = Math.min(topLeft, maxR)
        val tr = Math.min(topRight, maxR)
        val br = Math.min(bottomRight, maxR)
        val bl = Math.min(bottomLeft, maxR)

        setGLState(color)
        GL11.glBegin(GL11.GL_TRIANGLE_FAN)
        GL11.glVertex2f(x + w / 2f, y + h / 2f)
        val segments = Math.max(4, (Math.max(tl, Math.max(tr, Math.max(br, bl))) * 1.5f).toInt())
        val startX = x
        val startY = if (tl > 0f) y + tl else y
        if (tl > 0f) {
            drawArc(x + tl, y + tl, tl, 180f, 270f, segments)
        } else {
            GL11.glVertex2f(x, y)
        }
        if (tr > 0f) {
            drawArc(x + w - tr, y + tr, tr, 270f, 360f, segments)
        } else {
            GL11.glVertex2f(x + w, y)
        }
        if (br > 0f) {
            drawArc(x + w - br, y + h - br, br, 0f, 90f, segments)
        } else {
            GL11.glVertex2f(x + w, y + h)
        }
        if (bl > 0f) {
            drawArc(x + bl, y + h - bl, bl, 90f, 180f, segments)
        } else {
            GL11.glVertex2f(x, y + h)
        }
        GL11.glVertex2f(startX, startY)
        GL11.glEnd()
        restoreGLState()
    }

    @JvmStatic
    fun drawOutlinedRoundedRect(x: Float, y: Float, w: Float, h: Float, radius: Float, thickness: Float, color: Int) {
        val r = Math.min(radius, Math.min(w, h) / 2f)

        setGLState(color)
        GL11.glLineWidth(thickness)
        GL11.glBegin(GL11.GL_LINE_LOOP)
        val segments = Math.max(4, (r * 1.5f).toInt())
        drawArc(x + r, y + r, r, 180f, 270f, segments)
        drawArc(x + w - r, y + r, r, 270f, 360f, segments)
        drawArc(x + w - r, y + h - r, r, 0f, 90f, segments)
        drawArc(x + r, y + h - r, r, 90f, 180f, segments)
        GL11.glEnd()
        GL11.glLineWidth(1f)
        restoreGLState()
    }

    @JvmStatic
    fun drawLine(x1: Float, y1: Float, x2: Float, y2: Float, thickness: Float, color: Int) {
        setGLState(color)
        GL11.glLineWidth(thickness)
        GL11.glBegin(GL11.GL_LINES)
        GL11.glVertex2f(x1, y1)
        GL11.glVertex2f(x2, y2)
        GL11.glEnd()
        GL11.glLineWidth(1f)
        restoreGLState()
    }

    @JvmStatic
    fun drawHorizontalLine(x: Float, y: Float, w: Float, thickness: Float, color: Int) {
        drawRect(x, y, w, thickness, color)
    }

    @JvmStatic
    fun drawVerticalLine(x: Float, y: Float, h: Float, thickness: Float, color: Int) {
        drawRect(x, y, thickness, h, color)
    }

    @JvmStatic
    fun drawCircle(cx: Float, cy: Float, radius: Float, color: Int) {
        drawEllipse(cx, cy, radius, radius, color)
    }

    @JvmStatic
    fun drawEllipse(cx: Float, cy: Float, radiusX: Float, radiusY: Float, color: Int) {
        setGLState(color)
        GL11.glBegin(GL11.GL_TRIANGLE_FAN)
        GL11.glVertex2f(cx, cy)
        val segments = Math.max(12, (Math.max(radiusX, radiusY) * 1.5f).toInt())
        for (i in 0..segments) {
            val angle = Math.PI * 2 * i / segments
            GL11.glVertex2f((cx + Math.cos(angle) * radiusX).toFloat(), (cy + Math.sin(angle) * radiusY).toFloat())
        }
        GL11.glEnd()
        restoreGLState()
    }

    @JvmStatic
    fun drawTriangle(x: Float, y: Float, w: Float, h: Float, color: Int) {
        setGLState(color)
        GL11.glBegin(GL11.GL_TRIANGLES)
        GL11.glVertex2f(x, y + h)
        GL11.glVertex2f(x + w, y + h)
        GL11.glVertex2f(x + w / 2f, y)
        GL11.glEnd()
        restoreGLState()
    }

    @JvmStatic
    fun drawDot(x: Float, y: Float, size: Float, color: Int) {
        drawRect(x, y, size, size, color)
    }

    @JvmStatic
    fun drawVerticalGradientRect(x: Float, y: Float, w: Float, h: Float, topColor: Int, bottomColor: Int) {
        gradientState()
        GL11.glBegin(GL11.GL_QUADS)
        glColor(topColor)
        GL11.glVertex2f(x, y)
        glColor(topColor)
        GL11.glVertex2f(x + w, y)
        glColor(bottomColor)
        GL11.glVertex2f(x + w, y + h)
        glColor(bottomColor)
        GL11.glVertex2f(x, y + h)
        GL11.glEnd()
        restoreGLState()
    }

    @JvmStatic
    fun drawHorizontalGradientRect(x: Float, y: Float, w: Float, h: Float, leftColor: Int, rightColor: Int) {
        gradientState()
        GL11.glBegin(GL11.GL_QUADS)
        glColor(leftColor)
        GL11.glVertex2f(x, y)
        glColor(rightColor)
        GL11.glVertex2f(x + w, y)
        glColor(rightColor)
        GL11.glVertex2f(x + w, y + h)
        glColor(leftColor)
        GL11.glVertex2f(x, y + h)
        GL11.glEnd()
        restoreGLState()
    }

    @JvmStatic
    fun drawText(text: String, x: Float, y: Float, color: Int): Int {
        return mc.fontRendererObj.drawString(text, x, y, color, false)
    }

    @JvmStatic
    fun drawTextWithShadow(text: String, x: Float, y: Float, color: Int): Int {
        return mc.fontRendererObj.drawString(text, x, y, color, true)
    }

    @JvmStatic
    fun drawCenteredText(text: String, x: Float, y: Float, color: Int): Int {
        return drawText(text, x - getStringWidth(text) / 2f, y, color)
    }

    @JvmStatic
    fun drawCenteredTextWithShadow(text: String, x: Float, y: Float, color: Int): Int {
        return drawTextWithShadow(text, x - getStringWidth(text) / 2f, y, color)
    }

    @JvmStatic
    fun drawText(text: String, x: Float, y: Float, font: Font, size: Float, color: Int): Int {
        setGlyphState(color)
        val scaledSize = scaledSize(size)
        var dx = x
        for (c in text) dx += drawGlyph(font, c, scaledSize, dx, y)
        restoreGLState()
        return Math.round(dx)
    }

    @JvmStatic
    fun drawTextWithShadow(text: String, x: Float, y: Float, font: Font, size: Float, color: Int): Int {
        drawText(text, x + 1f, y + 1f, font, size, 0xAA000000.toInt())
        return drawText(text, x, y, font, size, color)
    }

    @JvmStatic
    fun drawTextVCenter(text: String, x: Float, y: Float, font: Font, size: Float, color: Int): Int {
        return drawText(text, x, centeredTextBaseline(y, font, size), font, size, color)
    }

    @JvmStatic
    fun drawTextCenter(text: String, x: Float, y: Float, font: Font, size: Float, color: Int): Int {
        val w = getTextWidth(text, font, size)
        return drawText(text, x - w / 2f, centeredTextBaseline(y, font, size), font, size, color)
    }

    @JvmStatic
    fun drawCenteredText(text: String, x: Float, y: Float, font: Font, size: Float, color: Int): Int {
        return drawText(text, x - getStringWidth(text, font, size) / 2f, y, font, size, color)
    }

    @JvmStatic
    fun drawCenteredTextWithShadow(text: String, x: Float, y: Float, font: Font, size: Float, color: Int): Int {
        return drawTextWithShadow(text, x - getStringWidth(text, font, size) / 2f, y, font, size, color)
    }

    @JvmStatic
    fun drawTextWithFormatting(text: String, x: Float, y: Float, font: Font, size: Float, color: Int): Int {
        var dx = x
        var current = color
        val plain = StringBuilder()
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == '\u00a7' && i + 1 < text.length) {
                val code = text[i + 1]
                if (plain.isNotEmpty()) {
                    dx = drawText(plain.toString(), dx, y, font, size, current).toFloat()
                    plain.setLength(0)
                }
                current = formatCode(code, color)
                i += 2
            } else {
                plain.append(c)
                i++
            }
        }
        if (plain.isNotEmpty()) {
            dx = drawText(plain.toString(), dx, y, font, size, current).toFloat()
        }
        return Math.round(dx)
    }

    @JvmStatic
    fun getStringWidth(text: String, font: Font, size: Float): Float = font.getStringWidth(text, size * getScale())

    @JvmStatic
    fun getFontHeight(font: Font, size: Float): Int = font.getHeight(size * getScale())

    @JvmStatic
    fun getTextWidth(text: String, font: Font, size: Float): Float = font.getStringWidth(text, size * getScale())

    @JvmStatic
    fun getTextHeight(text: String, font: Font, size: Float): Int = font.getStringHeight(text, size * getScale())

    @JvmStatic
    fun getTextHeight(font: Font, size: Float): Int = font.getHeight(size * getScale())

    @JvmStatic
    fun getStringWidth(text: String): Int {
        return mc.fontRendererObj.getStringWidth(text)
    }

    @JvmStatic
    fun getFontHeight(): Int {
        return mc.fontRendererObj.FONT_HEIGHT
    }

    @JvmStatic
    fun drawTexture(texture: ResourceLocation, x: Float, y: Float, w: Float, h: Float) {
        drawTexture(texture, x, y, w, h, 0f, 0f, 1f, 1f, 0xFFFFFFFF.toInt())
    }

    @JvmStatic
    fun drawTexture(texture: ResourceLocation, x: Float, y: Float, w: Float, h: Float, color: Int) {
        drawTexture(texture, x, y, w, h, 0f, 0f, 1f, 1f, color)
    }

    @JvmStatic
    fun drawTexture(texture: ResourceLocation, x: Float, y: Float, w: Float, h: Float,
                    u: Float, v: Float, u2: Float, v2: Float, color: Int) {
        val texId = getTexture(texture)
        if (texId == -1) return
        GlStateManager.enableBlend()
        GlStateManager.enableTexture2D()
        GlStateManager.disableDepth()
        GlStateManager.disableCull()
        GlStateManager.bindTexture(texId)
        glColor(color)
        GL11.glBegin(GL11.GL_QUADS)
        GL11.glTexCoord2f(u, v)
        GL11.glVertex2f(x, y)
        GL11.glTexCoord2f(u2, v)
        GL11.glVertex2f(x + w, y)
        GL11.glTexCoord2f(u2, v2)
        GL11.glVertex2f(x + w, y + h)
        GL11.glTexCoord2f(u, v2)
        GL11.glVertex2f(x, y + h)
        GL11.glEnd()
        restoreGLState()
    }

    private fun getTexture(texture: ResourceLocation): Int {
        val cached = textureCache[texture]
        if (cached != null) return cached
        val stream = javaClass.classLoader
            .getResourceAsStream("assets/${texture.resourceDomain}/textures/${texture.resourcePath}")
        if (stream == null) return -1
        val texId = TextureUtil.glGenTextures()
        try {
            val img = ImageIO.read(stream)
            val pixels = img.getRGB(0, 0, img.width, img.height, null, 0, img.width)
            val buf = BufferUtils.createByteBuffer(img.width * img.height * 4)
            for (p in pixels) {
                buf.put(((p ushr 16) and 0xFF).toByte())
                buf.put(((p ushr 8) and 0xFF).toByte())
                buf.put((p and 0xFF).toByte())
                buf.put(((p ushr 24) and 0xFF).toByte())
            }
            buf.flip()
            GlStateManager.bindTexture(texId)
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR)
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR)
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, img.width, img.height, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buf)
            textureCache[texture] = texId
            return texId
        } catch (e: Exception) {
            return -1
        }
    }

    @JvmStatic
    fun withClipping(clip: () -> Unit, render: () -> Unit) {
        ensureStencil()

        val scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST)
        if (scissor) GL11.glDisable(GL11.GL_SCISSOR_TEST)

        if (stencilDepth >= MAX_STENCIL_DEPTH) {
            if (scissor) GL11.glEnable(GL11.GL_SCISSOR_TEST)
            throw IllegalStateException("withClipping supports up to $MAX_STENCIL_DEPTH nested levels")
        }

        val depth = stencilDepth++
        val stencilBit = 1 shl depth
        val parentMask = stencilBit - 1
        val clippingMask = parentMask or stencilBit

        GL11.glEnable(GL11.GL_STENCIL_TEST)
        if (depth == 0) {
            GL11.glStencilMask(0xFF)
            GL11.glClearStencil(0)
            GL11.glClear(GL11.GL_STENCIL_BUFFER_BIT)
        } else {
            // The bit is reused by sibling clips, so clear only this level.
            GL11.glStencilMask(stencilBit)
            GL11.glClearStencil(0)
            GL11.glClear(GL11.GL_STENCIL_BUFFER_BIT)
        }

        GL11.glStencilMask(stencilBit)
        GL11.glStencilFunc(GL11.GL_EQUAL, clippingMask, parentMask)
        GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_REPLACE, GL11.GL_REPLACE)
        GlStateManager.colorMask(false, false, false, false)

        try {
            clip()

            GlStateManager.colorMask(true, true, true, true)
            GL11.glStencilMask(0x00)
            GL11.glStencilFunc(GL11.GL_EQUAL, clippingMask, clippingMask)
            GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP)

            render()
        } finally {
            GlStateManager.colorMask(true, true, true, true)
            stencilDepth--
            if (stencilDepth == 0) {
                GL11.glStencilMask(0xFF)
                GL11.glDisable(GL11.GL_STENCIL_TEST)
            } else {
                val restoredMask = (1 shl stencilDepth) - 1
                GL11.glStencilMask(0x00)
                GL11.glStencilFunc(GL11.GL_EQUAL, restoredMask, restoredMask)
                GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP)
            }
            if (scissor) GL11.glEnable(GL11.GL_SCISSOR_TEST) else GL11.glDisable(GL11.GL_SCISSOR_TEST)
        }
    }

    @JvmStatic
    fun withClipping(x: Float, y: Float, w: Float, h: Float, render: Runnable) {
        beginScissor(x, y, w, h)
        try {
            render.run()
        } finally {
            endScissor()
        }
    }

    @JvmStatic
    fun beginScissor(x: Float, y: Float, w: Float, h: Float) {
        val box: IntBuffer = BufferUtils.createIntBuffer(16)
        GL11.glGetInteger(GL11.GL_SCISSOR_BOX, box)
        scissorStack.push(intArrayOf(box.get(0), box.get(1), box.get(2), box.get(3)))

        val scale = getScale()
        GL11.glEnable(GL11.GL_SCISSOR_TEST)
        GL11.glScissor((x * scale).toInt(), (mc.displayHeight - (y + h) * scale).toInt(),
                (w * scale).toInt(), (h * scale).toInt())
    }

    @JvmStatic
    fun endScissor() {
        if (scissorStack.isEmpty()) {
            GL11.glDisable(GL11.GL_SCISSOR_TEST)
            return
        }
        val previous = scissorStack.pop()
        GL11.glScissor(previous[0], previous[1], previous[2], previous[3])
        if (scissorStack.isEmpty()) {
            GL11.glDisable(GL11.GL_SCISSOR_TEST)
        }
    }
    @JvmStatic
    fun renderBlur(clip: () -> Unit, radius: Int) {
        if (radius <= 0) return
        val main = mc.framebuffer
        val width = main.framebufferWidth
        val height = main.framebufferHeight
        if (width <= 0 || height <= 0) return
        if (!ensureBlurFbos(width, height) || !ensureBlurShader()) return

        val sr = ScaledResolution(mc)
        val screenW = sr.scaledWidth.toFloat()
        val screenH = sr.scaledHeight.toFloat()
        val fbo1 = blurFbo!!
        val fbo2 = blurFbo2!!

        val blend = GL11.glIsEnabled(GL11.GL_BLEND)
        val depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST)
        val texture = GL11.glIsEnabled(GL11.GL_TEXTURE_2D)
        val lighting = GL11.glIsEnabled(GL11.GL_LIGHTING)
        val scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST)
        val stencil = GL11.glIsEnabled(GL11.GL_STENCIL_TEST)
        val cull = GL11.glIsEnabled(GL11.GL_CULL_FACE)

        GL11.glDisable(GL11.GL_SCISSOR_TEST)
        GL11.glDisable(GL11.GL_STENCIL_TEST)
        GlStateManager.disableCull()

        GlStateManager.matrixMode(GL11.GL_PROJECTION)
        GlStateManager.pushMatrix()
        GlStateManager.matrixMode(GL11.GL_MODELVIEW)
        GlStateManager.pushMatrix()

        GlStateManager.matrixMode(GL11.GL_PROJECTION)
        GlStateManager.loadIdentity()
        GlStateManager.ortho(0.0, width.toDouble(), height.toDouble(), 0.0, 1000.0, 3000.0)
        GlStateManager.matrixMode(GL11.GL_MODELVIEW)
        GlStateManager.loadIdentity()
        GlStateManager.translate(0f, 0f, -2000f)

        GlStateManager.enableTexture2D()
        GlStateManager.disableBlend()
        GlStateManager.disableDepth()
        GlStateManager.disableLighting()
        GlStateManager.colorMask(true, true, true, true)
        GlStateManager.color(1f, 1f, 1f, 1f)
        GL13.glActiveTexture(GL13.GL_TEXTURE0)
        GlStateManager.viewport(0, 0, width, height)

        ARBShaderObjects.glUseProgramObjectARB(blurProgram)
        ARBShaderObjects.glUniform1iARB(blurSamplerLoc, 0)
        ARBShaderObjects.glUniform2fARB(blurTexelSizeLoc, 1f / width, 1f / height)

        val iterations = Math.max(1, radius / 3)
        var src = main.framebufferTexture
        for (i in 0 until iterations) {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo1.framebufferObject)
            ARBShaderObjects.glUniform2fARB(blurDirectionLoc, 1f, 0f)
            GlStateManager.bindTexture(src)
            drawFullscreenQuad(width.toFloat(), height.toFloat())

            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo2.framebufferObject)
            ARBShaderObjects.glUniform2fARB(blurDirectionLoc, 0f, 1f)
            GlStateManager.bindTexture(fbo1.framebufferTexture)
            drawFullscreenQuad(width.toFloat(), height.toFloat())

            src = fbo2.framebufferTexture
        }

        GlStateManager.matrixMode(GL11.GL_PROJECTION)
        GlStateManager.popMatrix()
        GlStateManager.matrixMode(GL11.GL_MODELVIEW)
        GlStateManager.popMatrix()

        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, main.framebufferObject)
        GlStateManager.viewport(0, 0, main.framebufferWidth, main.framebufferHeight)
        ARBShaderObjects.glUseProgramObjectARB(0)
        GlStateManager.enableBlend()
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0)
        GlStateManager.color(1f, 1f, 1f, 1f)

        GL11.glDisable(GL11.GL_SCISSOR_TEST)
        withClipping(clip, {
            GlStateManager.pushMatrix()
            GlStateManager.loadIdentity()
            GlStateManager.translate(0f, 0f, -2000f)
            GL13.glActiveTexture(GL13.GL_TEXTURE0)
            GlStateManager.bindTexture(fbo2.framebufferTexture)
            drawFullscreenQuad(screenW, screenH)
            GlStateManager.popMatrix()
        })

        if (blend) GlStateManager.enableBlend() else GlStateManager.disableBlend()
        if (depth) GlStateManager.enableDepth() else GlStateManager.disableDepth()
        if (texture) GlStateManager.enableTexture2D() else GlStateManager.disableTexture2D()
        if (lighting) GlStateManager.enableLighting() else GlStateManager.disableLighting()
        if (scissor) GL11.glEnable(GL11.GL_SCISSOR_TEST)
        if (stencil) GL11.glEnable(GL11.GL_STENCIL_TEST)
        if (cull) GlStateManager.enableCull()
        GlStateManager.color(1f, 1f, 1f, 1f)
    }

    private fun ensureBlurFbos(width: Int, height: Int): Boolean {
        if (blurFbo != null && blurFbo!!.framebufferWidth == width && blurFbo!!.framebufferHeight == height) return true
        blurFbo?.deleteFramebuffer()
        blurFbo2?.deleteFramebuffer()
        try {
            blurFbo = Framebuffer(width, height, false)
            blurFbo2 = Framebuffer(width, height, false)
            setLinearFilter(blurFbo!!)
            setLinearFilter(blurFbo2!!)
            return true
        } catch (e: Exception) {
            return false
        }
    }

    private fun setLinearFilter(fbo: Framebuffer) {
        GlStateManager.bindTexture(fbo.framebufferTexture)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR)
    }

    private fun ensureBlurShader(): Boolean {
        if (blurProgram != -1) return true
        val fragment = ARBShaderObjects.glCreateShaderObjectARB(GL20.GL_FRAGMENT_SHADER)
        ARBShaderObjects.glShaderSourceARB(fragment, BLUR_SHADER)
        ARBShaderObjects.glCompileShaderARB(fragment)
        if (ARBShaderObjects.glGetObjectParameteriARB(fragment, ARBShaderObjects.GL_OBJECT_COMPILE_STATUS_ARB) == 0) {
            println("[Blur] shader compile error: " + ARBShaderObjects.glGetInfoLogARB(fragment, 1024))
            ARBShaderObjects.glDeleteObjectARB(fragment)
            return false
        }
        blurProgram = ARBShaderObjects.glCreateProgramObjectARB()
        ARBShaderObjects.glAttachObjectARB(blurProgram, fragment)
        ARBShaderObjects.glLinkProgramARB(blurProgram)
        if (ARBShaderObjects.glGetObjectParameteriARB(blurProgram, ARBShaderObjects.GL_OBJECT_LINK_STATUS_ARB) == 0) {
            println("[Blur] shader link error: " + ARBShaderObjects.glGetInfoLogARB(blurProgram, 1024))
            ARBShaderObjects.glDeleteObjectARB(blurProgram)
            blurProgram = -1
            ARBShaderObjects.glDeleteObjectARB(fragment)
            return false
        }
        ARBShaderObjects.glDeleteObjectARB(fragment)
        blurTexelSizeLoc = ARBShaderObjects.glGetUniformLocationARB(blurProgram, "texelSize")
        blurDirectionLoc = ARBShaderObjects.glGetUniformLocationARB(blurProgram, "direction")
        blurSamplerLoc = ARBShaderObjects.glGetUniformLocationARB(blurProgram, "texture")
        return true
    }

    private fun drawFullscreenQuad(width: Float, height: Float) {
        GL11.glBegin(GL11.GL_QUADS)
        GL11.glTexCoord2f(0f, 1f)
        GL11.glVertex2f(0f, 0f)
        GL11.glTexCoord2f(1f, 1f)
        GL11.glVertex2f(width, 0f)
        GL11.glTexCoord2f(1f, 0f)
        GL11.glVertex2f(width, height)
        GL11.glTexCoord2f(0f, 0f)
        GL11.glVertex2f(0f, height)
        GL11.glEnd()
    }

    @JvmStatic
    fun isInside(x: Float, y: Float, w: Float, h: Float, mx: Float, my: Float): Boolean {
        return mx >= x && mx <= x + w && my >= y && my <= y + h
    }

    @JvmStatic
    fun isInside(x: Float, y: Float, w: Float, h: Float, radius: Float, mx: Float, my: Float): Boolean {
        return isInside(x, y, w, h, radius, radius, radius, radius, mx, my)
    }

    @JvmStatic
    fun isInside(x: Float, y: Float, w: Float, h: Float,
                 topLeft: Float, topRight: Float, bottomRight: Float, bottomLeft: Float,
                 mx: Float, my: Float): Boolean {
        if (mx < x || mx > x + w || my < y || my > y + h) return false
        if (mx < x + topLeft && my < y + topLeft) return inCircle(x + topLeft, y + topLeft, topLeft, mx, my)
        if (mx > x + w - topRight && my < y + topRight) return inCircle(x + w - topRight, y + topRight, topRight, mx, my)
        if (mx > x + w - bottomRight && my > y + h - bottomRight) {
            return inCircle(x + w - bottomRight, y + h - bottomRight, bottomRight, mx, my)
        }
        if (mx < x + bottomLeft && my > y + h - bottomLeft) {
            return inCircle(x + bottomLeft, y + h - bottomLeft, bottomLeft, mx, my)
        }
        return true
    }

    private fun inCircle(cx: Float, cy: Float, r: Float, mx: Float, my: Float): Boolean {
        val dx = mx - cx
        val dy = my - cy
        return dx * dx + dy * dy <= r * r
    }

    @JvmStatic
    fun getScale(): Float {
        return ScaledResolution(mc).scaleFactor.toFloat()
    }

    @JvmStatic
    fun setColor(color: Int) {
        glColor(color)
    }

    @JvmStatic
    fun alpha(color: Int, alpha: Float): Int {
        return (color and 0x00FFFFFF) or ((alpha * 255f + 0.5f).toInt() and 0xFF shl 24)
    }

    @JvmStatic
    fun getRGB(r: Int, g: Int, b: Int, a: Int): Int {
        return (a and 0xFF shl 24) or (r and 0xFF shl 16) or (g and 0xFF shl 8) or (b and 0xFF)
    }

    @JvmStatic
    fun getRGB(r: Float, g: Float, b: Float, a: Float): Int {
        return getRGB(
                (r * 255f + 0.5f).toInt(),
                (g * 255f + 0.5f).toInt(),
                (b * 255f + 0.5f).toInt(),
                (a * 255f + 0.5f).toInt())
    }

    @JvmStatic
    fun parseARGB(color: Int): ARGBColor {
        return ARGBColor(
                r = color shr 16 and 0xFF,
                g = color shr 8 and 0xFF,
                b = color and 0xFF,
                a = color shr 24 and 0xFF)
    }

    @JvmStatic
    fun pushMatrix() {
        GlStateManager.pushMatrix()
    }

    @JvmStatic
    fun popMatrix() {
        GlStateManager.popMatrix()
    }

    @JvmStatic
    fun translate(x: Float, y: Float) {
        GlStateManager.translate(x, y, 0f)
    }

    @JvmStatic
    fun scale(x: Float, y: Float) {
        GlStateManager.scale(x, y, 1f)
    }

    @JvmStatic
    fun ensureStencil() {
        val fbo = mc.framebuffer
        if (!fbo.isStencilEnabled) {
            fbo.enableStencil()
            fbo.bindFramebuffer(true)
        }
    }

    private fun drawArc(cx: Float, cy: Float, radius: Float, startAngle: Float, endAngle: Float, segments: Int) {
        for (i in 0..segments) {
            val angle = Math.toRadians((startAngle + (endAngle - startAngle) * i / segments).toDouble())
            GL11.glVertex2f((cx + Math.cos(angle) * radius).toFloat(), (cy + Math.sin(angle) * radius).toFloat())
        }
    }

    private fun scaledSize(size: Float): Float = size * getScale()

    private fun centeredTextBaseline(y: Float, font: Font, size: Float): Float {
        return y + font.getCenterBaselineOffset(scaledSize(size))
    }

    private fun setGLState(color: Int) {
        GlStateManager.enableBlend()
        GlStateManager.disableTexture2D()
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0)
        GlStateManager.disableCull()
        GlStateManager.disableDepth()
        glColor(color)
    }

    private fun setGlyphState(color: Int) {
        GlStateManager.enableBlend()
        GlStateManager.enableTexture2D()
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0)
        GlStateManager.disableDepth()
        GlStateManager.disableCull()
        glColor(color)
    }

    private fun drawGlyph(font: Font, c: Char, size: Float, x: Float, y: Float): Float {
        val glyph = font.getGlyph(c, size)
        GlStateManager.bindTexture(glyph.textureId)
        val top = y + glyph.offsetY
        GL11.glBegin(GL11.GL_QUADS)
        GL11.glTexCoord2f(0f, 0f)
        GL11.glVertex2f(x, top)
        GL11.glTexCoord2f(1f, 0f)
        GL11.glVertex2f(x + glyph.width, top)
        GL11.glTexCoord2f(1f, 1f)
        GL11.glVertex2f(x + glyph.width, top + glyph.height)
        GL11.glTexCoord2f(0f, 1f)
        GL11.glVertex2f(x, top + glyph.height)
        GL11.glEnd()
        return glyph.advance
    }

    private fun formatCode(code: Char, fallback: Int): Int = when (code) {
        '0' -> 0xFF000000.toInt()
        '1' -> 0xFF0000AA.toInt()
        '2' -> 0xFF00AA00.toInt()
        '3' -> 0xFF00AAAA.toInt()
        '4' -> 0xFFAA0000.toInt()
        '5' -> 0xFFAA00AA.toInt()
        '6' -> 0xFFFFAA00.toInt()
        '7' -> 0xFFAAAAAA.toInt()
        '8' -> 0xFF555555.toInt()
        '9' -> 0xFF5555FF.toInt()
        'a', 'A' -> 0xFF55FF55.toInt()
        'b', 'B' -> 0xFF55FFFF.toInt()
        'c', 'C' -> 0xFFFF5555.toInt()
        'd', 'D' -> 0xFFFF55FF.toInt()
        'e', 'E' -> 0xFFFFFF55.toInt()
        'f', 'F' -> 0xFFFFFFFF.toInt()
        'r', 'R' -> fallback
        else -> fallback
    }

    private fun gradientState() {
        GlStateManager.enableBlend()
        GlStateManager.disableTexture2D()
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0)
        GL11.glShadeModel(GL11.GL_SMOOTH)
    }

    private fun restoreGLState() {
        GlStateManager.color(1f, 1f, 1f, 1f)
        GlStateManager.enableTexture2D()
        GlStateManager.disableBlend()
    }

    private fun glColor(color: Int) {
        GlStateManager.color(
                (color shr 16 and 0xFF) / 255f,
                (color shr 8 and 0xFF) / 255f,
                (color and 0xFF) / 255f,
                (color shr 24 and 0xFF) / 255f)
    }
}
