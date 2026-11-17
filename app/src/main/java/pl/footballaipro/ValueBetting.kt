package pl.footballaipro

data class OddsAnalysis(
    val decimalOdds: Double,
    val modelProbability: Double,
    val impliedProbability: Double,
    val fairOdds: Double,
    val edgePercentagePoints: Double,
    val expectedValuePercent: Double,
    val isValueBet: Boolean
)

object ValueBetting {

    fun calculate(
        probabilityPercent: Double,
        decimalOdds: Double
    ): OddsAnalysis {

        require(probabilityPercent in 0.01..100.0) {
            "Prawdopodobieństwo musi wynosić od 0,01 do 100%"
        }

        require(decimalOdds > 1.0 && decimalOdds.isFinite()) {
            "Kurs musi być większy niż 1,00"
        }

        val probability = probabilityPercent / 100.0
        val impliedProbability = 1.0 / decimalOdds
        val fairOdds = 1.0 / probability

        val edge = (probability - impliedProbability) * 100.0
        val expectedValue = (probability * decimalOdds - 1.0) * 100.0

        return OddsAnalysis(
            decimalOdds = decimalOdds,
            modelProbability = probabilityPercent,
            impliedProbability = impliedProbability * 100.0,
            fairOdds = fairOdds,
            edgePercentagePoints = edge,
            expectedValuePercent = expectedValue,
            isValueBet = expectedValue > 0.0
        )
    }
}
