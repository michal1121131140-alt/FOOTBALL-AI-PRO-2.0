package pl.footballaipro

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class TeamAnalysisData(
    val home: TeamStats,
    val away: TeamStats
)

object FootballDataRepository {

    private data class TeamRecord(
        var played: Int = 0,
        var scored: Double = 0.0,
        var conceded: Double = 0.0,
        var wins: Int = 0,
        var draws: Int = 0,
        var losses: Int = 0
    )

    fun fetchTeamStats(
        league: FootballLeague,
        match: FootballMatch
    ): TeamAnalysisData {

        val url = URL(
            "https://api.openligadb.de/getmatchdata/" +
                "${league.shortcut}/${league.season}"
        )

        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 15000
        connection.readTimeout = 15000
        connection.requestMethod = "GET"

        val json = try {
            connection.inputStream.bufferedReader().use {
                it.readText()
            }
        } finally {
            connection.disconnect()
        }

        val array = JSONArray(json)
        val records = mutableMapOf<String, TeamRecord>()

        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)

            val matchDate = item.optString("matchDateTime").take(10)

            // Korzystamy tylko z meczów rozegranych przed analizowanym spotkaniem.
            if (matchDate >= match.date) continue

            val team1 = item.optJSONObject("team1") ?: continue
            val team2 = item.optJSONObject("team2") ?: continue

            val home = team1.optString("teamName")
            val away = team2.optString("teamName")

            if (home.isBlank() || away.isBlank()) continue

            val results = item.optJSONArray("matchResults") ?: continue
            val finalResult = findFinalResult(results) ?: continue

            val goals1 = finalResult.optInt("pointsTeam1")
            val goals2 = finalResult.optInt("pointsTeam2")

            val homeRecord = records.getOrPut(home) { TeamRecord() }
            val awayRecord = records.getOrPut(away) { TeamRecord() }

            homeRecord.played++
            homeRecord.scored += goals1
            homeRecord.conceded += goals2

            awayRecord.played++
            awayRecord.scored += goals2
            awayRecord.conceded += goals1

            when {
                goals1 > goals2 -> {
                    homeRecord.wins++
                    awayRecord.losses++
                }

                goals1 < goals2 -> {
                    awayRecord.wins++
                    homeRecord.losses++
                }

                else -> {
                    homeRecord.draws++
                    awayRecord.draws++
                }
            }
        }

        val homeRecord = records[match.home] ?: TeamRecord()
        val awayRecord = records[match.away] ?: TeamRecord()

        return TeamAnalysisData(
            home = homeRecord.toTeamStats(match.home),
            away = awayRecord.toTeamStats(match.away)
        )
    }

    private fun findFinalResult(
        results: JSONArray
    ): JSONObject? {
        for (i in 0 until results.length()) {
            val result = results.optJSONObject(i) ?: continue

            if (result.optString("resultName")
                    .contains("Endergebnis", ignoreCase = true)) {
                return result
            }
        }

        return null
    }

    private fun TeamRecord.toTeamStats(
        name: String
    ): TeamStats {
        return TeamStats(
            teamName = name,
            matchesPlayed = played,
            goalsScored = scored,
            goalsConceded = conceded,
            wins = wins,
            draws = draws,
            losses = losses
        )
    }
}
