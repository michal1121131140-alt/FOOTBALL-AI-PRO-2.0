package pl.footballaipro

import java.text.SimpleDateFormat
import java.util.Locale

object FootballDateValidator {
    fun isValid(value: String): Boolean {
        if (!value.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) return false
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            isLenient = false
        }
        return runCatching {
            val parsed = formatter.parse(value) ?: return false
            formatter.format(parsed) == value
        }.getOrDefault(false)
    }
}
