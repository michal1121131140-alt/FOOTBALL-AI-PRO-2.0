
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
    val losses: Int,

    // Dodatkowe statystyki domowe
    val homeMatchesPlayed: Int = 0,
    val homeGoalsScored: Double? = null,
    val homeGoalsConceded: Double? = null,

    // Dodatkowe statystyki wyjazdowe
    val awayMatchesPlayed: Int = 0,
    val awayGoalsScored: Double? = null,
    val awayGoalsConceded: Double? = null
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
    val expectedAwayGoals: Double,
    val dataReliability: Double = 0.0
)

object FootballAnalyzer {

    fun calculate(
        home: TeamStats,
        away: TeamStats
    ): MatchPrediction {

        // Statystyki ofensywne i defensywne
        val homeAttack = average(
            home.homeGoalsScored,
            home.homeMatchesPlayed,
            home.goalsScored,
            home.matchesPlayed
        )

        val awayDefense = average(
            away.awayGoalsConceded,
            away.awayMatchesPlayed,
            away.goalsConceded,
            away.matchesPlayed
        )

        val awayAttack = average(
            away.awayGoalsScored,
            away.awayMatchesPlayed,
            away.goalsScored,
            away.matchesPlayed
        )

        val homeDefense = average(
            home.homeGoalsConceded,
            home.homeMatchesPlayed,
            home.goalsConceded,
            home.matchesPlayed
        )

        // Uwzględnienie przewagi własnego boiska
        val homeExpected = (
            (homeAttack * 0.55 + awayDefense * 0.45) * 1.10
        ).coerceIn(0.15, 5.0)

        val awayExpected = (
            (awayAttack * 0.55 + homeDefense * 0.45) * 0.92
        ).coerceIn(0.15, 5.0)

        var homeWin = 0.0
        var draw = 0.0
        var awayWin = 0.0
        var over15 = 0.0
        var over25 = 0.0
        var over35 = 0.0
        var btts = 0.0

        // Rozkład Poissona dla możliwych wyników
        val maxGoals = 12

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

                val totalGoals = h + a

                if (totalGoals >= 2) over15 += probability
                if (totalGoals >= 3) over25 += probability
                if (totalGoals >= 4) over35 += probability

                if (h > 0 && a > 0) {
                    btts += probability
                }
            }
        }

        // Normalizacja wyników, aby prawdopodobieństwa
        // uwzględniały również pominięty ogon rozkładu
        val total = homeWin + draw + awayWin

        if (total > 0.0) {
            homeWin /= total
            draw /= total
            awayWin /= total
            over15 /= total
            over25 /= total
            over35 /= total
            btts /= total
        }

        // Ocena ilości dostępnych danych
        val homeSample = min(home.matchesPlayed, 20).toDouble() / 20.0
        val awaySample = min(away.matchesPlayed, 20).toDouble() / 20.0

        var reliability =
            ((homeSample + awaySample) / 2.0) * 100.0

        val hasHomeSplit =
            home.homeMatchesPlayed >= 3 &&
            home.homeGoalsScored != null &&
            home.homeGoalsConceded != null

        val hasAwaySplit =
            away.awayMatchesPlayed >= 3 &&
            away.awayGoalsScored != null &&
            away.awayGoalsConceded != null

        if (hasHomeSplit && hasAwaySplit) {
            reliability = (reliability + 5.0).coerceAtMost(100.0)
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
            expectedAwayGoals = awayExpected,
            dataReliability = reliability
        )
    }

    private fun average(
        splitGoals: Double?,
        splitMatches: Int,
        totalGoals: Double,
        totalMatches: Int
    ): Double {

        // Korzystamy z podziału dom/wyjazd,
        // jeśli dostępne są co najmniej 3 mecze.
        if (splitGoals != null && splitMatches >= 3) {
            return splitGoals / splitMatches
        }

        return if (totalMatches <= 0) {
            1.2
        } else {
            totalGoals / totalMatches
        }
    }

    private fun poisson(
        goals: Int,
        expected: Double
    ): Double {

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
