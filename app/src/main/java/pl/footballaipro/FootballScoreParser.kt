package pl.footballaipro

/**
 * Jedno miejsce parsowania wyników z OpenLigaDB i historii aplikacji.
 * Obsługuje m.in. "2 : 1", "2-1" oraz "2–1".
 */
object FootballScoreParser {
    data class Score(val home: Int, val away: Int)

    fun parse(value: String): Score? {
        val match = Regex("^\\s*(\\d+)\\s*[:\\-–]\\s*(\\d+)\\s*$")
            .matchEntire(value) ?: return null
        val home = match.groupValues[1].toIntOrNull() ?: return null
        val away = match.groupValues[2].toIntOrNull() ?: return null
        return Score(home, away)
    }
}
