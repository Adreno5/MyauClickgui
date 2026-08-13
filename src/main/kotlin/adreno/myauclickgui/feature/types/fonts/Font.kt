package adreno.myauclickgui.feature.types.fonts

import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.texture.TextureUtil
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11
import java.awt.Color
import java.awt.Font as AwtFont
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.font.FontRenderContext
import java.awt.image.BufferedImage
import java.util.HashMap
import kotlin.math.ceil

class Font(val name: String) {

    private val base: AwtFont = load()
    private val fonts = HashMap<Float, AwtFont>()
    private val glyphs = HashMap<Float, HashMap<Char, Glyph>>()
    private val frc = FontRenderContext(null, true, true)

    private fun load(): AwtFont {
        val stream = javaClass.classLoader.getResourceAsStream("myauclickgui/fonts/$name.ttf")
        return try {
            if (stream != null) {
                AwtFont.createFont(AwtFont.TRUETYPE_FONT, stream)
            } else {
                AwtFont(name, AwtFont.PLAIN, 12)
            }
        } catch (e: Exception) {
            AwtFont(name, AwtFont.PLAIN, 12)
        }
    }

    private fun awt(size: Float): AwtFont = fonts.getOrPut(size) { base.deriveFont(size * SAMPLE) }

    fun getGlyph(c: Char, size: Float): Glyph {
        val sizeGlyphs = glyphs.getOrPut(size) { HashMap() }
        return sizeGlyphs.getOrPut(c) { createGlyph(c, size) }
    }

    private fun createGlyph(c: Char, size: Float): Glyph {
        val gv = awt(size).createGlyphVector(frc, charArrayOf(c))
        val bounds = gv.getGlyphPixelBounds(0, frc, 0f, 0f)
        val w = 1.coerceAtLeast(ceil(bounds.width.toDouble()).toInt() + 1)
        val h = 1.coerceAtLeast(ceil(bounds.height.toDouble()).toInt() + 1)

        val img = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics() as Graphics2D
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g.font = awt(size)
        g.color = Color.WHITE
        g.drawGlyphVector(gv, -bounds.x.toFloat(), -bounds.y.toFloat())
        g.dispose()

        val pixels = img.getRGB(0, 0, w, h, null, 0, w)
        val buf = BufferUtils.createByteBuffer(w * h * 4)
        for (p in pixels) {
            buf.put(((p ushr 16) and 0xFF).toByte())
            buf.put(((p ushr 8) and 0xFF).toByte())
            buf.put((p and 0xFF).toByte())
            buf.put(((p ushr 24) and 0xFF).toByte())
        }
        buf.flip()

        val texId = TextureUtil.glGenTextures()
        GlStateManager.bindTexture(texId)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR)
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, w, h, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buf)

        return Glyph(
            texId,
            w / SAMPLE.toFloat(),
            h / SAMPLE.toFloat(),
            bounds.y.toFloat() / SAMPLE,
            gv.getGlyphMetrics(0).advanceX / SAMPLE)
    }

    fun getStringWidth(text: String, size: Float): Float {
        var width = 0f
        for (c in text) width += getGlyph(c, size).advance
        return width
    }

    fun getHeight(size: Float): Int = ceil(awt(size).getMaxCharBounds(frc).height / SAMPLE).toInt()

    fun getCenterBaselineOffset(size: Float): Float {
        val metrics = awt(size).getLineMetrics("Ag", frc)
        return (metrics.ascent - metrics.descent) / (2f * SAMPLE)
    }

    fun getStringHeight(text: String, size: Float): Int {
        if (text.isEmpty()) return getHeight(size)
        val bounds = awt(size).createGlyphVector(frc, text).getPixelBounds(frc, 0f, 0f)
        return ceil((bounds.height / SAMPLE).toDouble()).toInt()
    }

    fun getStringTopOffset(text: String, size: Float): Float {
        if (text.isEmpty()) return 0f
        val bounds = awt(size).createGlyphVector(frc, text).getPixelBounds(frc, 0f, 0f)
        return -bounds.y.toFloat() / SAMPLE
    }

    class Glyph(val textureId: Int, val width: Float, val height: Float, val offsetY: Float, val advance: Float)

    companion object {
        private const val SAMPLE = 3
    }
}
