package pl.footballaipro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FootballCoreLogicTest {

    @Test
    fun scoreParserSupportsSupportedSeparatorsAndRejectsUnfinishedScores() {
        assertEquals(FootballScoreParser.Score(2, 1), FootballScoreParser.parse("2 : 1"))
        assertEquals(FootballScoreParser.Score(3, 0), FootballScoreParser.parse("3-0"))
        assertEquals(FootballScoreParser.Score(1, 4), FootballScoreParser.parse("1–4"))
        assertNull(FootballScoreParser.parse("Nie rozegrano"))
        assertNull(FootballScoreParser.parse("2:1 po karnych"))
    }

    @Test
    fun teamFormUsesNewestCompletedMatchesAndCorrectTeamPerspective() {
        val matches = listOf(
            match("Alpha", "Beta", "1:0", "2026-01-01"),
            match("Gamma", "Alpha", "0:2", "2026-01-02"),
            match("Alpha", "Delta", "1:1", "2026-01-03"),
            match("Beta", "Alpha", "3:0", "2026-01-04"),
            match("Alpha", "Epsilon", "Nie rozegrano", "2026-01-05")
        )

        val form = TeamFormAnalyzer.calculate("Alpha", matches, limit = 3)

        assertEquals(listOf("P", "R", "W"), form.lastResults)
        assertEquals(1, form.wins)
        assertEquals(1, form.draws)
        assertEquals(1, form.losses)
        assertEquals(4, form.points)
        assertEquals(100.0 * 4.0 / 9.0, form.formRating, 0.0001)
    }

    @Test
    fun headToHeadNormalizesHomeAndAwayPerspective() {
        val matches = listOf(
            match("Alpha", "Beta", "2:0", "2026-01-01"),
            match("Beta", "Alpha", "1:1", "2026-01-02"),
            match("Alpha", "Beta", "0:3", "2026-01-03")
        )

        val stats = HeadToHeadAnalyzer.calculate("Alpha", "Beta", matches)

        assertEquals(3, stats.totalMatches)
        assertEquals(1, stats.homeWins)
        assertEquals(1, stats.draws)
        assertEquals(1, stats.awayWins)
        assertEquals(3, stats.homeGoals)
        assertEquals(4, stats.awayGoals)
        assertEquals(7.0 / 3.0, stats.averageGoals, 0.0001)
    }

    @Test
    fun poissonPredictionProbabilitiesAreBoundedAndConsistent() {
        val home = TeamStats(
            teamName = "Alpha", matchesPlayed = 10, goalsScored = 20.0,
            goalsConceded = 10.0, wins = 6, draws = 2, losses = 2,
            homeMatchesPlayed = 5, homeGoalsScored = 12.0, homeGoalsConceded = 4.0
        )
        val away = TeamStats(
            teamName = "Beta", matchesPlayed = 10, goalsScored = 12.0,
            goalsConceded = 15.0, wins = 3, draws = 3, losses = 4,
            awayMatchesPlayed = 5, awayGoalsScored = 6.0, awayGoalsConceded = 9.0
        )

        val prediction = FootballAnalyzer.calculate(home, away)

        assertEquals(100.0, prediction.homeWin + prediction.draw + prediction.awayWin, 0.05)
        assertEquals(100.0, prediction.over25 + prediction.under25, 0.0001)
        listOf(
            prediction.homeWin, prediction.draw, prediction.awayWin,
            prediction.over15, prediction.over25, prediction.over35,
            prediction.under25, prediction.bttsYes, prediction.bttsNo
        ).forEach { probability ->
            assertTrue(probability in 0.0..100.0)
        }
        assertEquals(5, prediction.mostLikelyScores.size)
        assertTrue(prediction.mostLikelyScores.zipWithNext().all {
            it.first.probabilityPercent >= it.second.probabilityPercent
        })
    }


    @Test
    fun malformedTeamStatisticsNeverProduceNonFinitePredictions() {
        val home = TeamStats(
            teamName = "Alpha",
            matchesPlayed = -5,
            goalsScored = Double.NaN,
            goalsConceded = -10.0,
            wins = 0,
            draws = 0,
            losses = 0,
            homeMatchesPlayed = 10,
            homeGoalsScored = Double.POSITIVE_INFINITY,
            homeGoalsConceded = -2.0
        )
        val away = TeamStats(
            teamName = "Beta",
            matchesPlayed = 0,
            goalsScored = Double.NEGATIVE_INFINITY,
            goalsConceded = Double.NaN,
            wins = 0,
            draws = 0,
            losses = 0,
            awayMatchesPlayed = 10,
            awayGoalsScored = Double.NaN,
            awayGoalsConceded = Double.POSITIVE_INFINITY
        )
        val invalidForm = TeamForm(
            teamName = "Alpha",
            lastResults = listOf("W", "W", "W"),
            wins = 3,
            draws = 0,
            losses = 0,
            points = 9,
            formRating = Double.NaN,
            currentStreak = "3 × W"
        )

        val prediction = FootballAnalyzer.calculate(
            home,
            away,
            homeForm = invalidForm,
            awayForm = invalidForm,
            h2h = HeadToHeadStats(4, 99, -20, 0, 0, 0, 0.0)
        )

        val probabilities = listOf(
            prediction.homeWin, prediction.draw, prediction.awayWin,
            prediction.over15, prediction.over25, prediction.over35,
            prediction.under25, prediction.bttsYes, prediction.bttsNo,
            prediction.dataReliability
        )
        assertTrue(probabilities.all { it.isFinite() && it in 0.0..100.0 })
        assertEquals(100.0, prediction.homeWin + prediction.draw + prediction.awayWin, 0.05)
        assertTrue(prediction.expectedHomeGoals.isFinite())
        assertTrue(prediction.expectedAwayGoals.isFinite())
        assertTrue(prediction.mostLikelyScores.all { it.probabilityPercent.isFinite() })
    }

    @Test
    fun valueBettingCalculatesPositiveAndNegativeExpectedValue() {
        val positive = ValueBetting.calculate(60.0, 2.0)
        val negative = ValueBetting.calculate(40.0, 2.0)

        assertTrue(positive.isValueBet)
        assertEquals(20.0, positive.expectedValuePercent, 0.0001)
        assertFalse(negative.isValueBet)
        assertEquals(-20.0, negative.expectedValuePercent, 0.0001)
    }

    @Test(expected = IllegalArgumentException::class)
    fun valueBettingRejectsOddsAtOrBelowOne() {
        ValueBetting.calculate(50.0, 1.0)
    }

    @Test
    fun apiFootballOnlySettlesFixturesWithFinishedStatuses() {
        listOf("FT", "AET", "PEN", "AWD", "WO").forEach { status ->
            assertTrue("$status should be finished", isFinishedFixture(status))
            assertTrue("$status should be case-insensitive", isFinishedFixture(status.lowercase()))
        }

        listOf(
            "NS", "1H", "HT", "2H", "ET", "BT", "P",
            "LIVE", "SUSP", "INT", "PST", "CANC", "ABD", ""
        ).forEach { status ->
            assertFalse("$status must not settle a prediction", isFinishedFixture(status))
        }
    }

    @Test
    fun apiErrorsExplainAuthenticationQuotaAndServerFailures() {
        assertTrue(apiHttpErrorMessage(401, null).contains("klucz API-Football", ignoreCase = true))
        assertTrue(apiHttpErrorMessage(429, null).contains("limit zapytań", ignoreCase = true))
        assertTrue(apiHttpErrorMessage(503, null).contains("chwilowy problem", ignoreCase = true))
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
        source = "Unit test"
    )
}
