package adreno.myauclickgui.feature.types.chat

class OutputReply(
    val prompt: String,
    formatted: List<String>,
    unformatted: List<String>
) : MyauReply() {
    val formatted = ArrayList(formatted)
    val unformatted = ArrayList(unformatted)
}
