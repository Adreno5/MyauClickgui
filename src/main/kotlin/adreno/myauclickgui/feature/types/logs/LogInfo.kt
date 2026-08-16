package adreno.myauclickgui.feature.types.logs

data class LogInfo(
    val content: String,
    val initTime: Long = System.nanoTime()
)
