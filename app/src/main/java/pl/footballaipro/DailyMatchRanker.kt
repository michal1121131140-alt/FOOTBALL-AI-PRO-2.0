
package pl.footballaipro

data class RankedMatch(
    val match: FootballMatch,
    val homeForm: Double,
    val awayForm: Double,
    val prediction: MatchPrediction?,
    val confidence: Double,
    val rating: Double,
    val explanation: String,
    val recommendedMarket: String = "BRAK REKOMENDACJI",
    val recommendedProbability: Double = 0.0,
    val riskLabel: String = "BRAK DANYCH"
)

object DailyMatchRanker {

    private data class Candidate(
        val market: String,
        val probability: Double,
        val minimumProbability: Double
    )

    fun rank(
        matches: List<FootballMatch>,
        formByTeam: Map<String, TeamForm>,
        predictions: Map<String, MatchPrediction>,
        limit: Int = 10
    ): List<RankedMatch> {

        return matches.map { match ->

            val homeForm =
                formByTeam[match.home]?.formRating ?: 0.0

            val awayForm =
                formByTeam[match.away]?.formRating ?: 0.0

            val prediction =
                predictions[match.league + "|" + match.home + "|" + match.away]
                    ?: predictions[match.home + "|" + match.away]

            val confidence = prediction?.let {
                maxOf(it.homeWin, it.draw, it.awayWin)
            } ?: 0.0

            if (prediction == null) {
                return@map RankedMatch(
                    match = match,
                    homeForm = homeForm,
                    awayForm = awayForm,
                    prediction = null,
                    confidence = 0.0,
                    rating = 0.0,
                    explanation = "Brak pełnych danych do analizy",
                    recommendedMarket = "BRAK REKOMENDACJI",
                    recommendedProbability = 0.0,
                    riskLabel = "BRAK DANYCH"
                )
            }

            // Kandydaci do najlepszego typu
            val candidates = listOf(
                Candidate("1", prediction.homeWin, 58.0),
                Candidate("X", prediction.draw, 30.0),
                Candidate("2", prediction.awayWin, 58.0),
                Candidate("Over 1.5", prediction.over15, 68.0),
                Candidate("Over 2.5", prediction.over25, 58.0),
                Candidate("Over 3.5", prediction.over35, 48.0),
                Candidate("Under 2.5", prediction.under25, 58.0),
                Candidate("BTTS", prediction.bttsYes, 58.0)
            )

            // Wybieramy najlepszy rynek według modelu.
            // Nie oznacza to automatycznie zakładu z dodatnim EV.
            val bestCandidate = candidates
                .filter {
                    it.probability >= it.minimumProbability
                }
                .maxByOrNull {
                    candidateScore(
                        it.probability,
                        prediction.dataReliability
                    )
                }

            val formDifference =
                kotlin.math.abs(homeForm - awayForm)

            val formScore =
                (100.0 - formDifference).coerceIn(0.0, 100.0)

            val reliability =
                prediction.dataReliability.coerceIn(0.0, 100.0)

            val recommendation = bestCandidate?.market
                ?: "BRAK REKOMENDACJI"

            val probability =
                bestCandidate?.probability ?: 0.0

            val recommendationScore =
                bestCandidate?.let {
                    candidateScore(it.probability, reliability)
                } ?: 0.0

            // Ranking uwzględnia jakość typu, wiarygodność
            // danych oraz formę obu zespołów.
            val rating = (
                recommendationScore * 0.65 +
                reliability * 0.20 +
                formScore * 0.15
            ).coerceIn(0.0, 100.0)

            val riskLabel = when {
                bestCandidate == null -> "BRAK REKOMENDACJI"
                probability >= 75.0 && reliability >= 65.0 -> "NIŻSZE RYZYKO"
                probability >= 65.0 -> "UMIARKOWANE RYZYKO"
                else -> "WYŻSZE RYZYKO"
            }

            val explanation = when {
                bestCandidate == null ->
                    "Model nie znalazł typu spełniającego minimalne progi."

                reliability < 35.0 ->
                    "Ograniczona ilość danych. Traktuj prognozę ostrożnie."

                probability >= 75.0 ->
                    "Mocny sygnał modelu dla rynku $recommendation."

                probability >= 65.0 ->
                    "Obiecujący typ modelu: $recommendation."

                else ->
                    "Najlepszy dostępny rynek modelu: $recommendation."
            }

            RankedMatch(
                match = match,
                homeForm = homeForm,
                awayForm = awayForm,
                prediction = prediction,
                confidence = confidence,
                rating = rating,
                explanation = explanation,
                recommendedMarket = recommendation,
                recommendedProbability = probability,
                riskLabel = riskLabel
            )

        }.sortedByDescending { it.rating }
            .take(limit.coerceAtLeast(1))
    }

    private fun candidateScore(
        probability: Double,
        reliability: Double
    ): Double {
        return (
            probability * 0.75 +
            reliability.coerceIn(0.0, 100.0) * 0.25
        ).coerceIn(0.0, 100.0)
    }
}
