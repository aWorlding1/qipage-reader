package local.readapp.core

/**
 * 极简本地化：以中文原文为键，按系统语言在词表中查表。
 *
 * 查不到的条目原样返回中文，所以漏译不会出现空白或乱码。
 * 简体中文是默认语言、不需要词表；同时提供繁体中文、英文、日文、韩文。
 *
 * 放在 core 这一层，是为了让 feature / data / engine-* 各模块都能共用同一个 T()。
 */
object I18n {
    @Volatile private var table: Map<String, String> = emptyMap()

    fun translate(source: String): String = table[source] ?: source

    /** 由应用层在进入界面之前装载当前系统语言对应的词表。 */
    fun install(entries: Map<String, String>) {
        table = entries
    }
}

/** 取当前系统语言的文案；缺条目时回退中文原文。 */
fun T(source: String): String = I18n.translate(source)
