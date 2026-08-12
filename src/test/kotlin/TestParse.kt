import adreno.myauclickgui.feature.managers.ChatManager

object TestParse {
    @JvmStatic
    fun main(args: Array<String>) {
        var passed = 0
        var total = 0

        fun check(name: String, condition: Boolean) {
            total++
            if (condition) passed++ else println("FAIL: $name")
        }

        val (m1, e1) = ChatManager.parseModules(listOf("» Fullbright (ON)"))
        check("basic on", m1.size == 1 && m1[0].name == "Fullbright" && m1[0].state && m1[0].keyBinding == null && e1.isEmpty())

        val (m2, e2) = ChatManager.parseModules(listOf("» [R] KillAura (OFF)"))
        check("bound off", m2.size == 1 && m2[0].name == "KillAura" && !m2[0].state && m2[0].keyBinding == "[R]" && e2.isEmpty())

        val (m3, e3) = ChatManager.parseModules(listOf("» [M] Xray (ON)"))
        check("bound on", m3.size == 1 && m3[0].name == "Xray" && m3[0].state && m3[0].keyBinding == "[M]" && e3.isEmpty())

        val (m4, e4) = ChatManager.parseModules(listOf("random", "[Myau] Modules:", ""))
        check("skip non-arrow", m4.isEmpty() && e4.isEmpty())

        val (m5, e5) = ChatManager.parseModules(listOf("» malformed"))
        check("malformed error", m5.isEmpty() && e5.size == 1)

        val (m6, e6) = ChatManager.parseModules(emptyList())
        check("empty input", m6.isEmpty() && e6.isEmpty())

        val (m7, e7) = ChatManager.parseModules(listOf("» Xray (OFF)", "» Fullbright (ON)", "garbage"))
        check("mixed batch", m7.size == 2 && m7[0].name == "Xray" && !m7[0].state && m7[1].name == "Fullbright" && m7[1].state && e7.isEmpty())

        val (m8, e8) = ChatManager.parseModules(listOf("» [K] Fullbright (ON)", "» Crash (OFF)"))
        check("state index regression", m8.size == 2 && m8[0].name == "Fullbright" && m8[0].state && m8[1].name == "Crash" && !m8[1].state && e8.isEmpty())

        if (passed == total) {
            println("ALL TESTS PASSED ($passed/$total)")
        } else {
            println("$passed/$total passed")
            System.exit(1)
        }
    }
}
