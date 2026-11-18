package pl.footballaipro

data class BacktestSummary(
    val testedMatches: Int,
    val skippedMatches: Int,
    val correctPredictions: Int,
    val accuracy: Double,
    val averageBrierScore: Double
)

object PredictionBacktest {

    // For each historical match, only earlier completed matches are used.
    fun run(matches: List<FootballMatch>): BacktestSummary {
        val ordered = matches.sortedBy { it.date }
        var tested = 0
        var skipped = 0
        var correct = 0
        var brierTotal = 0.0

        ordered.forEach { target ->
            val actual = FootballScoreParser.parse(target.score)
            if (actual == null) {
                skipped++
                return@forEach
            }

            val previous = ordered.filter {
                it.date < target.date &&
                    FootballScoreParser.parse(it.score) != null
            }
            val homeStats = teamStats(target.home, previous)
            val awayStats = teamStats(target.away, previous)

            if (homeStats == null || awayStats == null ||
                homeStats.matchesPlayed < 3 || awayStats.matchesPlayed < 3
            ) {
                skipped++
                return@forEach
            }

            val homeForm = TeamFormAnalyzer.calculate(target.home, previous)
            val awayForm = TeamFormAnalyzer.calculate(target.away, previous)
            val h2h = HeadToHeadAnalyzer.calculate(
                target.home, target.away, previous, 10
            )
            val prediction = FootballAnalyzer.calculate(
                homeStats, awayStats, homeForm, awayForm, h2h
            )

            val actualIndex = when {
                actual.home > actual.away -> 0
                actual.home == actual.away -> 1
                else -> 2
            }
            val probabilities = listOf(
                prediction.homeWin / 100.0,
                prediction.draw / 100.0,
                prediction.awayWin / 100.0
            )
            val predictedIndex = probabilities.indices.maxByOrNull {
                probabilities[it]
            } ?: 1

            if (predictedIndex == actualIndex) correct++

            brierTotal += probabilities.indices.sumOf { index ->
                val observed = if (index == actualIndex) 1.0 else 0.0
                val difference = probabilities[index] - observed
                difference * difference
            }
            tested++
        }

        return BacktestSummary(
            testedMatches = tested,
            skippedMatches = skipped,
            correctPredictions = correct,
            accuracy = if (tested == 0) 0.0 else correct * 100.0 / tested,
            averageBrierScore = if (tested == 0) 0.0 else brierTotal / tested
        )
    }

    private fun teamStats(team: String, matches: List<FootballMatch>): TeamStats? {
        var played = 0
        var scored = 0
        var conceded = 0
        var wins = 0
        var draws = 0
        var losses = 0
        var homePlayed = 0
        var homeScored = 0
        var homeConceded = 0
        var awayPlayed = 0
        var awayScored = 0
        var awayConceded = 0

        matches.forEach { match ->
            val score = FootballScoreParser.parse(match.score) ?: return@forEach
            val isHome = match.home == team
            val isAway = match.away == team
            if (!isHome && !isAway) return@forEach

            val goalsFor = if (isHome) score.home else score.away
            val goalsAgainst = if (isHome) score.away else score.home
            played++
            scored += goalsFor
            conceded += goalsAgainst

            if (isHome) {
                homePlayed++
                homeScored += goalsFor
                homeConceded += goalsAgainst
            } else {
                awayPlayed++
                awayScored += goalsFor
                awayConceded += goalsAgainst
            }

            when {
                goalsFor > goalsAgainst -> wins++
                goalsFor == goalsAgainst -> draws++
                else -> losses++
            }
        }

        if (played == 0) return null
        return TeamStats(
            teamName = team,
            matchesPlayed = played,
            goalsScored = scored.toDouble(),
            goalsConceded = conceded.toDouble(),
            wins = wins,
            draws = draws,
            losses = losses,
            homeMatchesPlayed = homePlayed,
            homeGoalsScored = if (homePlayed > 0) homeScored.toDouble() else null,
            homeGoalsConceded = if (homePlayed > 0) homeConceded.toDouble() else null,
            awayMatchesPlayed = awayPlayed,
            awayGoalsScored = if (awayPlayed > 0) awayScored.toDouble() else null,
            awayGoalsConceded = if (awayPlayed > 0) awayConceded.toDouble() else null
        )
    }
}
