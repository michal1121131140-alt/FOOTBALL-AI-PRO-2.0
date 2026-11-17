
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
                (it.home == teamName || it.away == teamName) &&
                it.score.contains("-")
            }
            .sortedByDescending { it.date }
            .take(limit)

        var wins = 0
        var draws = 0
        var losses = 0

        val results = completed.mapNotNull { match ->
            val goals = match.score.split("-", ":", "–")
            if (goals.size < 2) return@mapNotNull null

            val homeGoals = goals[0].trim().toIntOrNull()
                ?: return@mapNotNull null
            val awayGoals = goals[1].trim().toIntOrNull()
                ?: return@mapNotNull null

            val isHome = match.home == teamName

            val result = when {
                homeGoals == awayGoals -> {
                    draws++
                    "R"
                }
                (isHome && homeGoals > awayGoals) ||
                (!isHome && awayGoals > homeGoals) -> {
                    wins++
                    "W"
                }
                else -> {
                    losses++
                    "P"
                }
            }

            result
        }

        val points = wins * 3 + draws
        val rating = if (completed.isEmpty()) {
            0.0
        } else {
            points.toDouble() / (completed.size * 3) * 100.0
        }

        val streak = results.firstOrNull()?.let { first ->
            val count = results.takeWhile { it == first }.size
            "$count × $first"
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
