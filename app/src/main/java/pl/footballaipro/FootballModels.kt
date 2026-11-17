
package pl.footballaipro

data class FootballLeague(
    val name: String,
    val shortcut: String,
    val season: Int
)

data class FootballMatch(
    val league: String,
    val home: String,
    val away: String,
    val score: String,
    val date: String,
    val source: String
)
