package com.beemdevelopment.aegis.desktop.i18n

import java.io.InputStream
import java.util.Locale
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants
import javax.xml.stream.XMLStreamReader

// Top-level rather than a member of Strings: it is read while Strings is still initializing, and a
// member would still be null at that point.
private val KNOWN_LOCALES = listOf(
    "ar-SA", "ast-ES", "bg-BG", "ca-ES", "cs-CZ", "da-DK", "de-DE", "el-GR", "es-ES", "et-EE",
    "eu-ES", "fa-IR", "fi-FI", "fr-FR", "fy-NL", "gl-ES", "hi-IN", "hu-HU", "in-ID", "it-IT",
    "iw-IL", "ja-JP", "kn-IN", "ko-KR", "lt-LT", "lv-LV", "ml-IN", "nb-NO", "nl-NL", "pl-PL",
    "pt-BR", "pt-PT", "ro-RO", "ru-RU", "sk-SK", "sr-SP", "sv-SE", "tr-TR", "uk-UA", "vi-VN",
    "zh-CN", "zh-TW",
)

/**
 * The app's translated strings, read straight from the Android `strings.xml` resources Aegis
 * ships so they stay mergeable with upstream. Android's `%1$s` specifiers are what
 * [String.format] already takes; only plurals need translating, in [PluralRules].
 */
object Strings {
    private const val BASE_PATH = "/i18n"

    @Volatile
    private var current: Bundle = Bundle.load(Locale.getDefault())

    @Volatile
    var locale: Locale = Locale.getDefault()
        private set

    val availableLocales: List<Locale> by lazy { discoverLocales() }

    fun setLocale(locale: Locale) {
        this.locale = locale
        current = Bundle.load(locale)
    }

    /** Returns the string with the given name, or the name itself if it is missing. */
    operator fun get(name: String): String = current.strings[name] ?: name

    fun format(name: String, vararg args: Any?): String {
        val template = get(name)
        return try {
            String.format(locale, template, *args)
        } catch (e: java.util.IllegalFormatException) {
            // A translation with a broken format specifier must not crash the app.
            template
        }
    }

    /** The plural form for [quantity], which is passed as the first format argument. */
    fun plural(name: String, quantity: Int, vararg args: Any?): String {
        val forms = current.plurals[name]
        val category = PluralRules.select(current.language, quantity)
        val template = forms?.get(category)
            ?: forms?.get("other")
            ?: forms?.values?.firstOrNull()
            ?: return name

        val allArgs = if (args.isEmpty()) arrayOf<Any?>(quantity) else args
        return try {
            String.format(locale, template, *allArgs)
        } catch (e: java.util.IllegalFormatException) {
            template
        }
    }

    fun has(name: String): Boolean = current.strings.containsKey(name)

    private fun discoverLocales(): List<Locale> {
        // Probed from the known set rather than scanned: enumerating a classpath directory is not
        // portable across a jar, an exploded build and a jlink image.
        return KNOWN_LOCALES.mapNotNull { tag ->
            val locale = Locale.forLanguageTag(tag)
            if (Bundle.resourceFor(locale) != null) locale else null
        }
    }

    /** One loaded language: base English overlaid with a translation, so partials fall back. */
    private class Bundle(
        val language: String,
        val strings: Map<String, String>,
        val plurals: Map<String, Map<String, String>>,
    ) {
        companion object {
            fun load(locale: Locale): Bundle {
                val base = parse(resource("$BASE_PATH/values/strings.xml"))
                val translated = resourceFor(locale)?.let { parse(resource(it)) }

                if (translated == null) {
                    return Bundle("en", base.first, base.second)
                }

                return Bundle(
                    language = locale.language,
                    strings = base.first + translated.first,
                    plurals = base.second + translated.second,
                )
            }

            // Android qualifiers look like values-pt-rBR. Exact region first, then any region for
            // the same language.
            fun resourceFor(locale: Locale): String? {
                val language = locale.language
                val country = locale.country

                if (country.isNotEmpty()) {
                    val exact = "$BASE_PATH/values-$language-r${country.uppercase(Locale.ROOT)}/strings.xml"
                    if (exists(exact)) {
                        return exact
                    }
                }

                return KNOWN_LOCALES
                    .firstOrNull { it.substringBefore('-') == language }
                    ?.let { tag ->
                        val parts = tag.split('-')
                        val path = "$BASE_PATH/values-${parts[0]}-r${parts[1].uppercase(Locale.ROOT)}/strings.xml"
                        if (exists(path)) path else null
                    }
            }

            private fun exists(path: String): Boolean =
                Strings::class.java.getResource(path) != null

            private fun resource(path: String): InputStream =
                Strings::class.java.getResourceAsStream(path)
                    ?: throw IllegalStateException("Missing string resource: $path")

            /** Reads an Android `strings.xml`. Later definitions win. */
            private fun parse(stream: InputStream): Pair<Map<String, String>, Map<String, Map<String, String>>> {
                val strings = LinkedHashMap<String, String>()
                val plurals = LinkedHashMap<String, MutableMap<String, String>>()

                val factory = XMLInputFactory.newInstance().apply {
                    setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false)
                    setProperty(XMLInputFactory.SUPPORT_DTD, false)
                    setProperty(XMLInputFactory.IS_COALESCING, true)
                }

                stream.use {
                    val reader = factory.createXMLStreamReader(it)
                    try {
                        var pluralName: String? = null
                        var quantity: String? = null

                        while (reader.hasNext()) {
                            when (reader.next()) {
                                XMLStreamConstants.START_ELEMENT -> when (reader.localName) {
                                    "string" -> {
                                        val name = reader.getAttributeValue(null, "name")
                                        if (name != null) {
                                            strings[name] = unescape(readText(reader))
                                        }
                                    }

                                    "plurals" -> pluralName = reader.getAttributeValue(null, "name")

                                    "item" -> if (pluralName != null) {
                                        quantity = reader.getAttributeValue(null, "quantity")
                                        val text = unescape(readText(reader))
                                        if (quantity != null) {
                                            plurals.getOrPut(pluralName) { LinkedHashMap() }[quantity] = text
                                        }
                                    }
                                }

                                XMLStreamConstants.END_ELEMENT -> if (reader.localName == "plurals") {
                                    pluralName = null
                                }
                            }
                        }
                    } finally {
                        reader.close()
                    }
                }

                return strings to plurals
            }

            private fun readText(reader: XMLStreamReader): String {
                val sb = StringBuilder()
                var depth = 0
                while (reader.hasNext()) {
                    when (reader.next()) {
                        XMLStreamConstants.CHARACTERS, XMLStreamConstants.CDATA ->
                            sb.append(reader.text)

                        XMLStreamConstants.START_ELEMENT -> depth++

                        XMLStreamConstants.END_ELEMENT -> {
                            if (depth == 0) {
                                return sb.toString()
                            }
                            depth--
                        }
                    }
                }
                return sb.toString()
            }

            /** Undoes Android's backslash escapes. The parser has already handled XML entities. */
            private fun unescape(value: String): String {
                if (!value.contains('\\')) {
                    return value
                }

                val sb = StringBuilder(value.length)
                var i = 0
                while (i < value.length) {
                    val c = value[i]
                    if (c == '\\' && i + 1 < value.length) {
                        when (val next = value[i + 1]) {
                            'n' -> sb.append('\n')
                            't' -> sb.append('\t')
                            '\'' -> sb.append('\'')
                            '"' -> sb.append('"')
                            '@' -> sb.append('@')
                            '?' -> sb.append('?')
                            '\\' -> sb.append('\\')
                            else -> {
                                sb.append(c)
                                sb.append(next)
                            }
                        }
                        i += 2
                    } else {
                        sb.append(c)
                        i++
                    }
                }
                return sb.toString()
            }
        }
    }

}
