package pl.footballaipro

data class TeamForm(
    val teamName: String,
    val lastResults: List<String>,
    val wins: Int,
    val draws: Int,
    val losses: Int,
    val points: Int,
    val formRating: Double,
    val currentStreak: String
)

object TeamFormAnalyzer {

    fun calculate(
        teamName: String,
        matches: List<FootballMatch>,
        limit: Int = 5
    ): TeamForm {
        val completed = matches
            .filter {
                (it.home.equals(teamName, ignoreCase = true) ||
                    it.away.equals(teamName, ignoreCase = true)) &&
                    FootballScoreParser.parse(it.score) != null
            }
            .sortedByDescending { it.date }
            .take(limit)

        var wins = 0
        var draws = 0
        var losses = 0

        val results = completed.mapNotNull { match ->
            val score = FootballScoreParser.parse(match.score)
                ?: return@mapNotNull null
            val isHome = match.home.equals(teamName, ignoreCase = true)

            when {
                score.home == score.away -> {
                    draws++
                    "R"
                }
                (isHome && score.home > score.away) ||
                    (!isHome && score.away > score.home) -> {
                    wins++
                    "W"
                }
                else -> {
                    losses++
                    "P"
                }
            }
        }

        val points = wins * 3 + draws
        val rating = if (results.isEmpty()) 0.0
            else points.toDouble() / (results.size * 3) * 100.0
        val streak = results.firstOrNull()?.let { first ->
            "${results.takeWhile { it == first }.size} × $first"
        } ?: "Brak danych"

        return TeamForm(
            teamName = teamName,
            lastResults = results,
            wins = wins,
            draws = draws,
            losses = losses,
            points = points,
            formRating = rating,
            currentStreak = streak
        )
    }
}
