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
        var losses: Int = 0,
        var homePlayed: Int = 0,
        var homeScored: Double = 0.0,
        var homeConceded: Double = 0.0,
        var awayPlayed: Int = 0,
        var awayScored: Double = 0.0,
        var awayConceded: Double = 0.0
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
        connection.setRequestProperty("User-Agent", "FootballAIPro/2.0")

        val json = try {
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }

        val array = JSONArray(json)
        val records = mutableMapOf<String, TeamRecord>()

        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val matchDate = item.optString("matchDateTime").take(10)

            // Do not leak the result of the match being predicted (or future matches)
            // into the model's historical sample.
            if (matchDate.isBlank() || matchDate >= match.date) continue

            val home = item.optJSONObject("team1")
                ?.optString("teamName").orEmpty()
            val away = item.optJSONObject("team2")
                ?.optString("teamName").orEmpty()
            if (home.isBlank() || away.isBlank()) continue

            val results = item.optJSONArray("matchResults") ?: continue
            val finalResult = findFinalResult(results) ?: continue
            if (!finalResult.has("pointsTeam1") ||
                !finalResult.has("pointsTeam2")) continue

            val goalsHome = finalResult.optInt("pointsTeam1")
            val goalsAway = finalResult.optInt("pointsTeam2")

            val homeRecord = records.getOrPut(home) { TeamRecord() }
            val awayRecord = records.getOrPut(away) { TeamRecord() }

            homeRecord.played++
            homeRecord.scored += goalsHome
            homeRecord.conceded += goalsAway
            homeRecord.homePlayed++
            homeRecord.homeScored += goalsHome
            homeRecord.homeConceded += goalsAway

            awayRecord.played++
            awayRecord.scored += goalsAway
            awayRecord.conceded += goalsHome
            awayRecord.awayPlayed++
            awayRecord.awayScored += goalsAway
            awayRecord.awayConceded += goalsHome

            when {
                goalsHome > goalsAway -> {
                    homeRecord.wins++
                    awayRecord.losses++
                }
                goalsHome < goalsAway -> {
                    awayRecord.wins++
                    homeRecord.losses++
                }
                else -> {
                    homeRecord.draws++
                    awayRecord.draws++
                }
            }
        }

        return TeamAnalysisData(
            home = (records[match.home] ?: TeamRecord()).toTeamStats(match.home),
            away = (records[match.away] ?: TeamRecord()).toTeamStats(match.away)
        )
    }

    private fun findFinalResult(results: JSONArray): JSONObject? {
        for (i in 0 until results.length()) {
            val result = results.optJSONObject(i) ?: continue
            if (result.optString("resultName")
                    .contains("Endergebnis", ignoreCase = true)) {
                return result
            }
        }
        return null
    }

    private fun TeamRecord.toTeamStats(name: String): TeamStats {
        return TeamStats(
            teamName = name,
            matchesPlayed = played,
            goalsScored = scored,
            goalsConceded = conceded,
            wins = wins,
            draws = draws,
            losses = losses,
            homeMatchesPlayed = homePlayed,
            homeGoalsScored = if (homePlayed > 0) homeScored else null,
            homeGoalsConceded = if (homePlayed > 0) homeConceded else null,
            awayMatchesPlayed = awayPlayed,
            awayGoalsScored = if (awayPlayed > 0) awayScored else null,
            awayGoalsConceded = if (awayPlayed > 0) awayConceded else null
        )
    }
}
