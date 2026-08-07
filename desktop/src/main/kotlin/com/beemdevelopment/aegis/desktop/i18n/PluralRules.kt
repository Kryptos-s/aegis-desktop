package com.beemdevelopment.aegis.desktop.i18n

/**
 * The CLDR plural category an Android `<plurals>` resource is keyed on. The JVM has no
 * `getQuantityString`, and `ChoiceFormat` works on numeric ranges rather than these categories,
 * so the rules for the languages Aegis ships are spelled out here. Integers only.
 */
internal object PluralRules {
    const val ZERO = "zero"
    const val ONE = "one"
    const val TWO = "two"
    const val FEW = "few"
    const val MANY = "many"
    const val OTHER = "other"

    fun select(language: String, n: Int): String {
        val count = if (n < 0) -n else n
        return when (language) {
            // No grammatical plural at all.
            "ja", "ko", "zh", "vi", "th", "id", "in", "ms", "my", "km", "lo", "kn", "ta", "te" ->
                OTHER

            // 0 is singular here.
            "fr", "hy", "ff", "kab" -> if (count == 0 || count == 1) ONE else OTHER

            // Two forms, singular at exactly 1.
            "en", "de", "nl", "sv", "da", "nb", "no", "nn", "es", "it", "pt", "ca", "gl", "eu",
            "ast", "el", "fi", "et", "hu", "tr", "bg", "sq", "ka", "af", "sw", "ml", "fa", "hi",
            "iw", "he", "ur", "eo" -> if (count == 1) ONE else OTHER

            // Slavic: one / few / many.
            "ru", "uk", "be", "sr", "hr", "bs" -> {
                val mod10 = count % 10
                val mod100 = count % 100
                when {
                    mod10 == 1 && mod100 != 11 -> ONE
                    mod10 in 2..4 && mod100 !in 12..14 -> FEW
                    else -> MANY
                }
            }

            "pl" -> {
                val mod10 = count % 10
                val mod100 = count % 100
                when {
                    count == 1 -> ONE
                    mod10 in 2..4 && mod100 !in 12..14 -> FEW
                    else -> MANY
                }
            }

            "cs", "sk" -> when {
                count == 1 -> ONE
                count in 2..4 -> FEW
                else -> OTHER
            }

            "lt" -> {
                val mod10 = count % 10
                val mod100 = count % 100
                when {
                    mod10 == 1 && mod100 !in 11..19 -> ONE
                    mod10 in 2..9 && mod100 !in 11..19 -> FEW
                    else -> OTHER
                }
            }

            "lv" -> {
                val mod10 = count % 10
                val mod100 = count % 100
                when {
                    mod10 == 0 || mod100 in 11..19 -> ZERO
                    mod10 == 1 && mod100 != 11 -> ONE
                    else -> OTHER
                }
            }

            "ro" -> {
                val mod100 = count % 100
                when {
                    count == 1 -> ONE
                    count == 0 || mod100 in 1..19 -> FEW
                    else -> OTHER
                }
            }

            "ar" -> {
                val mod100 = count % 100
                when {
                    count == 0 -> ZERO
                    count == 1 -> ONE
                    count == 2 -> TWO
                    mod100 in 3..10 -> FEW
                    mod100 in 11..99 -> MANY
                    else -> OTHER
                }
            }

            // Unknown language: the two-form rule is the most common shape.
            else -> if (count == 1) ONE else OTHER
        }
    }
}
