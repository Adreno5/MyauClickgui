package adreno.myauclickgui.feature.utils

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.ScaledResolution
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.util.ResourceLocation
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL30
import java.nio.IntBuffer
import java.util.*

object RenderUtil {
    private val mc = Minecraft.getMinecraft()
    private val scissorStack: Deque<IntArray> = ArrayDeque()
    private var stencilBuffer = -1
    private var stencilFbo = -1
    private var stencilWidth = -1
    private var stencilHeight = -1

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
        if (radius <= 0f) {
            drawRect(x, y, w, h, color)
            return
        }
        var r = Math.min(radius, Math.min(w, h) / 2f)

        setGLState(color)
        GL11.glBegin(GL11.GL_POLYGON)
        val segments = Math.max(4, (r * 1.5f).toInt())
        drawArc(x + r, y + r, r, 180f, 270f, segments)
        drawArc(x + w - r, y + r, r, 270f, 360f, segments)
        drawArc(x + w - r, y + h - r, r, 0f, 90f, segments)
        drawArc(x + r, y + h - r, r, 90f, 180f, segments)
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
        mc.textureManager.bindTexture(texture)
        GlStateManager.enableBlend()
        GlStateManager.enableTexture2D()
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

    @JvmStatic
    fun withClipping(clip: Runnable, render: Runnable) {
        ensureStencil()

        GL11.glEnable(GL11.GL_STENCIL_TEST)
        GL11.glClearStencil(0)
        GL11.glStencilFunc(GL11.GL_ALWAYS, 1, 0xFF)
        GL11.glStencilOp(GL11.GL_REPLACE, GL11.GL_REPLACE, GL11.GL_REPLACE)
        GlStateManager.colorMask(false, false, false, false)
        GL11.glClear(GL11.GL_STENCIL_BUFFER_BIT)

        clip.run()

        GlStateManager.colorMask(true, true, true, true)
        GL11.glStencilFunc(GL11.GL_EQUAL, 1, 0xFF)
        GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP)

        render.run()

        GL11.glStencilFunc(GL11.GL_ALWAYS, 0, 0xFF)
        GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP)
        GL11.glDisable(GL11.GL_STENCIL_TEST)
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
        val box: IntBuffer = BufferUtils.createIntBuffer(4)
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
    fun isInside(x: Float, y: Float, w: Float, h: Float, mx: Float, my: Float): Boolean {
        return mx >= x && mx <= x + w && my >= y && my <= y + h
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

    private fun ensureStencil() {
        val fbo = mc.framebuffer
        if (stencilBuffer != -1 && stencilFbo == fbo.framebufferObject
                && stencilWidth == fbo.framebufferWidth && stencilHeight == fbo.framebufferHeight) {
            return
        }
        if (stencilBuffer != -1) {
            GL30.glDeleteRenderbuffers(stencilBuffer)
        }
        stencilBuffer = GL30.glGenRenderbuffers()
        stencilFbo = fbo.framebufferObject
        stencilWidth = fbo.framebufferWidth
        stencilHeight = fbo.framebufferHeight

        GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, stencilBuffer)
        GL30.glRenderbufferStorage(GL30.GL_RENDERBUFFER, GL30.GL_STENCIL_INDEX8, stencilWidth, stencilHeight)
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, stencilFbo)
        GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_STENCIL_ATTACHMENT,
                GL30.GL_RENDERBUFFER, stencilBuffer)
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0)
        GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, 0)
    }

    private fun drawArc(cx: Float, cy: Float, radius: Float, startAngle: Float, endAngle: Float, segments: Int) {
        for (i in 0..segments) {
            val angle = Math.toRadians((startAngle + (endAngle - startAngle) * i / segments).toDouble())
            GL11.glVertex2f((cx + Math.cos(angle) * radius).toFloat(), (cy + Math.sin(angle) * radius).toFloat())
        }
    }

    private fun setGLState(color: Int) {
        GlStateManager.enableBlend()
        GlStateManager.disableTexture2D()
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0)
        glColor(color)
    }

    private fun gradientState() {
        GlStateManager.enableBlend()
        GlStateManager.disableTexture2D()
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0)
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
