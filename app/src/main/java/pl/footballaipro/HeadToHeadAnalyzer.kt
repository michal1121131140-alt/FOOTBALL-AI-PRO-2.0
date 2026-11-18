package pl.footballaipro

data class HeadToHeadStats(
    val totalMatches: Int,
    val homeWins: Int,
    val draws: Int,
    val awayWins: Int,
    val homeGoals: Int,
    val awayGoals: Int,
    val averageGoals: Double
)

object HeadToHeadAnalyzer {

    fun calculate(
        homeTeam: String,
        awayTeam: String,
        matches: List<FootballMatch>,
        limit: Int = 10
    ): HeadToHeadStats {

        val previousMatches = matches
            .filter {
                (it.home.equals(homeTeam, ignoreCase = true) &&
                    it.away.equals(awayTeam, ignoreCase = true)) ||
                (it.home.equals(awayTeam, ignoreCase = true) &&
                    it.away.equals(homeTeam, ignoreCase = true))
            }
            .sortedByDescending { it.date }
            .take(limit)

        var homeWins = 0
        var draws = 0
        var awayWins = 0
        var homeGoals = 0
        var awayGoals = 0
        var validMatches = 0

        for (match in previousMatches) {
            val score = FootballScoreParser.parse(match.score) ?: continue
            val first = score.home
            val second = score.away

            val homeScore: Int
            val awayScore: Int

            if (match.home.equals(homeTeam, ignoreCase = true)) {
                homeScore = first
                awayScore = second
            } else {
                homeScore = second
                awayScore = first
            }

            validMatches++
            homeGoals += homeScore
            awayGoals += awayScore

            when {
                homeScore > awayScore -> homeWins++
                homeScore == awayScore -> draws++
                else -> awayWins++
            }
        }

        val averageGoals = if (validMatches > 0) {
            (homeGoals + awayGoals).toDouble() / validMatches
        } else {
            0.0
        }

        return HeadToHeadStats(
            totalMatches = validMatches,
            homeWins = homeWins,
            draws = draws,
            awayWins = awayWins,
            homeGoals = homeGoals,
            awayGoals = awayGoals,
            averageGoals = averageGoals
        )
    }
}
