
package pl.footballaipro

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

data class TeamStats(
    val teamName: String,
    val matchesPlayed: Int,
    val goalsScored: Double,
    val goalsConceded: Double,
    val wins: Int,
    val draws: Int,
    val losses: Int
)

data class MatchPrediction(
    val homeWin: Double,
    val draw: Double,
    val awayWin: Double,
    val over15: Double,
    val over25: Double,
    val over35: Double,
    val under25: Double,
    val bttsYes: Double,
    val bttsNo: Double,
    val expectedHomeGoals: Double,
    val expectedAwayGoals: Double
)

object FootballAnalyzer {

    fun calculate(
        home: TeamStats,
        away: TeamStats
    ): MatchPrediction {

        val homeAttack = average(
            home.goalsScored,
            home.matchesPlayed
        )

        val awayAttack = average(
            away.goalsScored,
            away.matchesPlayed
        )

        val homeDefense = average(
            home.goalsConceded,
            home.matchesPlayed
        )

        val awayDefense = average(
            away.goalsConceded,
            away.matchesPlayed
        )

        val homeExpected = min(
            5.0,
            max(0.15, (homeAttack + awayDefense) / 2.0 * 1.12)
        )

        val awayExpected = min(
            5.0,
            max(0.15, (awayAttack + homeDefense) / 2.0 * 0.92)
        )

        var homeWin = 0.0
        var draw = 0.0
        var awayWin = 0.0
        var over15 = 0.0
        var over25 = 0.0
        var over35 = 0.0
        var btts = 0.0

        val maxGoals = 10

        for (h in 0..maxGoals) {
            for (a in 0..maxGoals) {

                val probability =
                    poisson(h, homeExpected) *
                    poisson(a, awayExpected)

                when {
                    h > a -> homeWin += probability
                    h == a -> draw += probability
                    else -> awayWin += probability
                }

                if (h + a >= 2) over15 += probability
                if (h + a >= 3) over25 += probability
                if (h + a >= 4) over35 += probability
                if (h > 0 && a > 0) btts += probability
            }
        }

        val total = homeWin + draw + awayWin

        if (total > 0.0) {
            homeWin /= total
            draw /= total
            awayWin /= total
        }

        return MatchPrediction(
            homeWin = percent(homeWin),
            draw = percent(draw),
            awayWin = percent(awayWin),
            over15 = percent(over15),
            over25 = percent(over25),
            over35 = percent(over35),
            under25 = percent(1.0 - over25),
            bttsYes = percent(btts),
            bttsNo = percent(1.0 - btts),
            expectedHomeGoals = homeExpected,
            expectedAwayGoals = awayExpected
        )
    }

    private fun average(goals: Double, matches: Int): Double {
        return if (matches <= 0) 1.2 else goals / matches
    }

    private fun poisson(goals: Int, expected: Double): Double {
        var factorial = 1.0

        for (i in 1..goals) {
            factorial *= i
        }

        return exp(-expected) *
            expected.pow(goals) / factorial
    }

    private fun Double.pow(power: Int): Double {
        var result = 1.0
        repeat(power) {
            result *= this
        }
        return result
    }

    private fun percent(value: Double): Double {
        return (value * 100.0).coerceIn(0.0, 100.0)
    }
}
