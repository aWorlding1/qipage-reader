package local.readapp

import android.content.Context
import android.content.res.Configuration
import local.readapp.core.I18n
import org.json.JSONObject
import java.util.Locale

/**
 * 按系统语言装载 assets/i18n/<语言>.json 词表。
 *
 * 简体中文是默认语言、无需词表；其余语言查不到条目时回退中文原文，不会出现空白界面。
 */
internal fun attachI18n(context: Context) {
    val language = resolveLanguage(context)
    if (language == "zh-CN") {
        I18n.install(emptyMap())
        return
    }
    val entries = HashMap<String, String>(1024)
    runCatching {
        context.assets.open("i18n/$language.json").bufferedReader().use { reader ->
            val json = JSONObject(reader.readText())
            json.keys().forEach { key -> entries[key] = json.getString(key) }
        }
    }
    I18n.install(entries)
}

private fun resolveLanguage(context: Context): String {
    val configuration: Configuration = context.resources.configuration
    val locale = if (configuration.locales.isEmpty) Locale.getDefault() else configuration.locales[0]
    return when (locale.language) {
        "zh" -> {
            val traditional = locale.script.equals("Hant", true) ||
                locale.country.equals("TW", true) ||
                locale.country.equals("HK", true) ||
                locale.country.equals("MO", true)
            if (traditional) "zh-Hant" else "zh-CN"
        }
        "en" -> "en"
        "ja" -> "ja"
        "ko" -> "ko"
        // 其余语言统一回退英文，避免出现中文与目标语言混杂的空白界面
        else -> "en"
    }
}
