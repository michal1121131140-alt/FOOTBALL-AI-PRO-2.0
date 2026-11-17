package pl.footballaipro

data class RankedMatch(
    val match: FootballMatch,
    val homeForm: Double,
    val awayForm: Double,
    val prediction: MatchPrediction?,
    val confidence: Double,
    val rating: Double,
    val explanation: String
)

object DailyMatchRanker {

    fun rank(
        matches: List<FootballMatch>,
        formByTeam: Map<String, TeamForm>,
        predictions: Map<String, MatchPrediction>,
        limit: Int = 10
    ): List<RankedMatch> {

        return matches.map { match ->

            val homeForm = formByTeam[match.home]?.formRating ?: 0.0
            val awayForm = formByTeam[match.away]?.formRating ?: 0.0

            val prediction = predictions[match.home + "|" + match.away]

            val confidence = prediction?.let {
                maxOf(it.homeWin, it.draw, it.awayWin)
            } ?: 0.0

            val formDifference = kotlin.math.abs(homeForm - awayForm)

            val rating = (
                confidence * 0.6 +
                (100.0 - formDifference) * 0.4
            ).coerceIn(0.0, 100.0)

            val explanation = when {
                prediction == null -> "Brak pełnych danych do prognozy"
                confidence >= 65.0 -> "Wyraźniejszy faworyt według modelu"
                formDifference <= 10.0 -> "Wyrównana forma obu drużyn"
                else -> "Różnica formy między drużynami"
            }

            RankedMatch(
                match = match,
                homeForm = homeForm,
                awayForm = awayForm,
                prediction = prediction,
                confidence = confidence,
                rating = rating,
                explanation = explanation
            )

        }.sortedByDescending { it.rating }
            .take(limit.coerceAtLeast(1))
    }
}
