package pl.footballaipro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FootballCoreTest {

    @Test
    fun scoreParserAcceptsSupportedSeparatorsAndSpaces() {
        assertEquals(FootballScoreParser.Score(2, 1), FootballScoreParser.parse("2 : 1"))
        assertEquals(FootballScoreParser.Score(3, 0), FootballScoreParser.parse("3-0"))
        assertEquals(FootballScoreParser.Score(1, 4), FootballScoreParser.parse("1–4"))
        assertEquals(null, FootballScoreParser.parse("Nie rozegrano"))
    }

    @Test
    fun valueBettingCalculatesPositiveAndNegativeExpectedValue() {
        val positive = ValueBetting.calculate(60.0, 2.0)
        assertEquals(20.0, positive.expectedValuePercent, 0.0001)
        assertTrue(positive.isValueBet)

        val negative = ValueBetting.calculate(40.0, 2.0)
        assertEquals(-20.0, negative.expectedValuePercent, 0.0001)
        assertFalse(negative.isValueBet)
    }

    @Test
    fun recentFormUsesNewestCompletedMatchesFirst() {
        val matches = listOf(
            match("A", "B", "1:0", "2026-01-01"),
            match("C", "A", "0:0", "2026-01-02"),
            match("A", "D", "0:2", "2026-01-03")
        )

        val form = TeamFormAnalyzer.calculate("A", matches)

        assertEquals(listOf("P", "R", "W"), form.lastResults)
        assertEquals(1, form.wins)
        assertEquals(1, form.draws)
        assertEquals(1, form.losses)
        assertEquals(4, form.points)
    }

    @Test
    fun headToHeadUsesSharedScoreParserAndIgnoresTeamNameCase() {
        val stats = HeadToHeadAnalyzer.calculate(
            "TEAM A",
            "team b",
            listOf(match("Team A", "Team B", "2 : 1", "2026-01-01"))
        )

        assertEquals(1, stats.totalMatches)
        assertEquals(1, stats.homeWins)
        assertEquals(2, stats.homeGoals)
        assertEquals(1, stats.awayGoals)
    }

    @Test
    fun poissonPredictionProbabilitiesAndScorelinesAreNormalized() {
        val home = TeamStats(
            teamName = "Home",
            matchesPlayed = 10,
            goalsScored = 20.0,
            goalsConceded = 10.0,
            wins = 6,
            draws = 2,
            losses = 2,
            homeMatchesPlayed = 5,
            homeGoalsScored = 12.0,
            homeGoalsConceded = 4.0
        )
        val away = TeamStats(
            teamName = "Away",
            matchesPlayed = 10,
            goalsScored = 14.0,
            goalsConceded = 16.0,
            wins = 3,
            draws = 3,
            losses = 4,
            awayMatchesPlayed = 5,
            awayGoalsScored = 7.0,
            awayGoalsConceded = 9.0
        )

        val prediction = FootballAnalyzer.calculate(home, away)
        val total = prediction.homeWin + prediction.draw + prediction.awayWin

        assertEquals(100.0, total, 0.0001)
        assertEquals(5, prediction.mostLikelyScores.size)
        assertTrue(prediction.mostLikelyScores.zipWithNext().all {
            it.first.probabilityPercent >= it.second.probabilityPercent
        })
        assertTrue(prediction.mostLikelyScores.all {
            it.probabilityPercent > 0.0
        })
    }

    @Test
    fun historicalBacktestDoesNotUseFutureMatches() {
        val matches = listOf(
            match("Home", "Away", "2:1", "2026-01-10"),
            match("Home", "X1", "1:0", "2026-01-11"),
            match("Away", "Y1", "1:0", "2026-01-11"),
            match("Home", "X2", "1:0", "2026-01-12"),
            match("Away", "Y2", "1:0", "2026-01-12"),
            match("Home", "X3", "1:0", "2026-01-13"),
            match("Away", "Y3", "1:0", "2026-01-13")
        )

        val summary = PredictionBacktest.run(matches)

        assertEquals(0, summary.testedMatches)
        assertEquals(matches.size, summary.skippedMatches)
    }

    private fun match(
        home: String,
        away: String,
        score: String,
        date: String
    ) = FootballMatch(
        league = "Test League",
        home = home,
        away = away,
        score = score,
        date = date,
        source = "Unit Test"
    )
}
