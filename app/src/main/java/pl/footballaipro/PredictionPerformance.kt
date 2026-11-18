
package pl.footballaipro

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class PredictionRecord(
    val id: String,
    val league: String,
    val home: String,
    val away: String,
    val market: String,
    val selection: String,
    val odds: Double,
    val probability: Double,
    val stake: Double,
    val createdAt: Long,
    val settled: Boolean = false,
    val won: Boolean? = null,
    val profit: Double = 0.0,
    val actualScore: String = "",
    val matchDate: String = ""
)

data class PerformanceStats(
    val total: Int,
    val settled: Int,
    val wins: Int,
    val losses: Int,
    val open: Int,
    val winRate: Double,
    val totalStake: Double,
    val totalProfit: Double,
    val roi: Double
)

object PredictionPerformance {

    private const val PREFS = "football_ai_predictions"
    private const val KEY = "records"

    const val HOME_WIN = "1"
    const val DRAW = "X"
    const val AWAY_WIN = "2"
    const val OVER_15 = "OVER_1_5"
    const val OVER_25 = "OVER_2_5"
    const val OVER_35 = "OVER_3_5"
    const val UNDER_25 = "UNDER_2_5"
    const val BTTS_YES = "BTTS_YES"
    const val BTTS_NO = "BTTS_NO"

    private fun preferences(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getAll(context: Context): List<PredictionRecord> {
        val json = preferences(context).getString(KEY, "[]") ?: "[]"
        val array = runCatching { JSONArray(json) }.getOrElse {
            // A damaged preference must not crash the statistics screen.
            return emptyList()
        }
        val records = mutableListOf<PredictionRecord>()

        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue

            records.add(
                PredictionRecord(
                    id = item.optString("id"),
                    league = item.optString("league"),
                    home = item.optString("home"),
                    away = item.optString("away"),
                    market = item.optString("market"),
                    selection = item.optString("selection"),
                    odds = item.optDouble("odds"),
                    probability = item.optDouble("probability"),
                    stake = item.optDouble("stake"),
                    createdAt = item.optLong("createdAt"),
                    settled = item.optBoolean("settled"),
                    won = if (item.isNull("won")) null
                          else item.optBoolean("won"),
                    profit = item.optDouble("profit"),
                    actualScore = item.optString("actualScore"),
                    matchDate = item.optString("matchDate")
                )
            )
        }
        return records
    }

    fun save(context: Context, record: PredictionRecord) {
        val records = getAll(context).toMutableList()
        records.removeAll { it.id == record.id }
        records.add(record)
        write(context, records)
    }

    fun createRecord(
        match: FootballMatch,
        selection: String,
        odds: Double,
        probability: Double,
        stake: Double = 1.0
    ): PredictionRecord {
        require(odds > 1.0 && odds.isFinite())
        require(probability in 0.0..100.0)
        require(stake > 0.0 && stake.isFinite())

        return PredictionRecord(
            id = "${System.currentTimeMillis()}_${match.home}_${match.away}",
            league = match.league,
            home = match.home,
            away = match.away,
            market = marketName(selection),
            selection = selection,
            odds = odds,
            probability = probability,
            stake = stake,
            createdAt = System.currentTimeMillis(),
            matchDate = match.date
        )
    }

    fun settleMatch(
        context: Context,
        match: FootballMatch
    ): Int {
        val score = FootballScoreParser.parse(match.score) ?: return 0
        val homeGoals = score.home
        val awayGoals = score.away

        val records = getAll(context).toMutableList()
        var settledCount = 0

        for (i in records.indices) {
            val record = records[i]

            if (record.settled ||
                !record.home.equals(match.home, true) ||
                !record.away.equals(match.away, true) ||
                !record.league.equals(match.league, true) ||
                (record.matchDate.isNotBlank() && record.matchDate != match.date)
            ) continue

            val won = evaluate(
                record.selection,
                homeGoals,
                awayGoals
            ) ?: continue

            val profit = if (won) {
                record.stake * (record.odds - 1.0)
            } else {
                -record.stake
            }

            records[i] = record.copy(
                settled = true,
                won = won,
                profit = profit,
                actualScore = "$homeGoals:$awayGoals"
            )
            settledCount++
        }

        if (settledCount > 0) {
            write(context, records)
        }

        return settledCount
    }

    private fun evaluate(
        selection: String,
        home: Int,
        away: Int
    ): Boolean? {
        val total = home + away

        return when (selection) {
            HOME_WIN -> home > away
            DRAW -> home == away
            AWAY_WIN -> away > home
            OVER_15 -> total >= 2
            OVER_25 -> total >= 3
            OVER_35 -> total >= 4
            UNDER_25 -> total <= 2
            BTTS_YES -> home > 0 && away > 0
            BTTS_NO -> home == 0 || away == 0
            else -> null
        }
    }

    fun getStats(context: Context): PerformanceStats {
        val records = getAll(context)
        val settled = records.filter { it.settled }
        val wins = settled.count { it.won == true }
        val losses = settled.count { it.won == false }
        val totalStake = settled.sumOf { it.stake }
        val totalProfit = settled.sumOf { it.profit }

        return PerformanceStats(
            total = records.size,
            settled = settled.size,
            wins = wins,
            losses = losses,
            open = records.count { !it.settled },
            winRate = if (settled.isEmpty()) 0.0
                      else wins * 100.0 / settled.size,
            totalStake = totalStake,
            totalProfit = totalProfit,
            roi = if (totalStake == 0.0) 0.0
                  else totalProfit / totalStake * 100.0
        )
    }

    fun clear(context: Context) {
        preferences(context).edit().remove(KEY).apply()
    }

    private fun write(
        context: Context,
        records: List<PredictionRecord>
    ) {
        val array = JSONArray()

        records.forEach { record ->
            val item = JSONObject().apply {
                put("id", record.id)
                put("league", record.league)
                put("home", record.home)
                put("away", record.away)
                put("market", record.market)
                put("selection", record.selection)
                put("odds", record.odds)
                put("probability", record.probability)
                put("stake", record.stake)
                put("createdAt", record.createdAt)
                put("settled", record.settled)
                put("won", record.won ?: JSONObject.NULL)
                put("profit", record.profit)
                put("actualScore", record.actualScore)
                put("matchDate", record.matchDate)
            }
            array.put(item)
        }

        preferences(context).edit()
            .putString(KEY, array.toString())
            .apply()
    }

    private fun marketName(selection: String): String =
        when (selection) {
            HOME_WIN, DRAW, AWAY_WIN -> "1X2"
            OVER_15, OVER_25, OVER_35, UNDER_25 -> "Gole"
            BTTS_YES, BTTS_NO -> "Obie drużyny strzelą"
            else -> "Inny"
        }
}
