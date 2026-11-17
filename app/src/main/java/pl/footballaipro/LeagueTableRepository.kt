package pl.footballaipro

import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

data class LeagueStanding(
    val position: Int,
    val teamName: String,
    val played: Int,
    val wins: Int,
    val draws: Int,
    val losses: Int,
    val goalsScored: Int,
    val goalsConceded: Int,
    val goalDifference: Int,
    val points: Int,
    val teamIconUrl: String
)

object LeagueTableRepository {

    fun fetchTable(league: FootballLeague): List<LeagueStanding> {
        val url = URL(
            "https://api.openligadb.de/getbltable/${league.shortcut}/${league.season}"
        )

        val connection = url.openConnection() as HttpURLConnection

        connection.connectTimeout = 15000
        connection.readTimeout = 15000
        connection.requestMethod = "GET"

        val response = try {
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }

        val array = JSONArray(response)
        val table = mutableListOf<LeagueStanding>()

        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)

            val scored = item.optInt("goals")
            val conceded = item.optInt("opponentGoals")

            table.add(
                LeagueStanding(
                    position = i + 1,
                    teamName = item.optString("teamName", "Nieznana drużyna"),
                    played = item.optInt("matches"),
                    wins = item.optInt("won"),
                    draws = item.optInt("draw"),
                    losses = item.optInt("lost"),
                    goalsScored = scored,
                    goalsConceded = conceded,
                    goalDifference = item.optInt(
                        "goalDiff",
                        scored - conceded
                    ),
                    points = item.optInt("points"),
                    teamIconUrl = item.optString("teamIconUrl")
                )
            )
        }

        return table
    }
}
