import adreno.myauclickgui.feature.types.fonts.Font
import java.awt.Color
import java.awt.Font as AwtFont
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.FileInputStream
import javax.imageio.ImageIO
fun main(args: Array<String>) {
    val cl = Thread.currentThread().contextClassLoader
    println("search.png via classloader: ${cl.getResourceAsStream("assets/myauclickgui/textures/images/search.png") != null}")
    println("ttf via classloader: ${cl.getResourceAsStream("myauclickgui/fonts/HarmonyOS_Sans_SC_Regular.ttf") != null}")
    val font = Font("HarmonyOS_Sans_SC_Regular")
    for (t in listOf("TEST", "Search...", "Agfy", "abc123")) {
        val h = font.getStringHeight(t, 14f)
        val top = font.getStringTopOffset(t, 14f)
        println("text='$t' h=$h topOffset=$top topMargin=${(h - top)}")
    }
}
