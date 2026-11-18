package pl.footballaipro

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest

data class ApiFootballLeague(
    val id: Int,
    val name: String,
    val country: String,
    val season: Int
)

data class ApiFootballFixture(
    val id: Int,
    val leagueId: Int,
    val leagueName: String,
    val home: String,
    val away: String,
    val date: String,
    val time: String,
    val status: String,
    val score: String
)

data class ApiFootballPredictionData(
    val advice: String,
    val winner: String,
    val homeWin: String,
    val draw: String,
    val awayWin: String,
    val homeForm: String,
    val awayForm: String,
    val comparison: Map<String, Pair<String, String>>
)

data class ApiFootballInjury(
    val player: String,
    val team: String,
    val reason: String
)

data class ApiFootballOdds(
    val bookmaker: String,
    val market: String,
    val selection: String,
    val odd: String
)

/**
 * API-Football v3 integration.
 * All requests are explicit and user-triggered; the caller should cache results
 * to respect the free plan's daily request quota.
 */
object ApiFootballRepository {
    private const val BASE_URL = "https://v3.football.api-sports.io"

    private fun request(context: Context, apiKey: String, path: String, query: Map<String, String> = emptyMap()): JSONObject {
        require(apiKey.isNotBlank()) { "Dodaj klucz API-Football w ustawieniach." }
        val queryString = query.entries.joinToString("&") { (key, value) ->
            "${encode(key)}=${encode(value)}"
        }
        val fullUrl = BASE_URL + path + if (queryString.isNotEmpty()) "?$queryString" else ""
        val cache = context.getSharedPreferences("api_football_response_cache", Context.MODE_PRIVATE)
        // Keep cached responses separate when the user changes API credentials.
        // Only a short SHA-256 fingerprint is stored; the API key itself is not.
        val credentialFingerprint = MessageDigest.getInstance("SHA-256")
            .digest(apiKey.toByteArray(Charsets.UTF_8))
            .take(8)
            .joinToString("") { byte -> "%02x".format(byte) }
        val cacheKey = "response_" + credentialFingerprint + "_" +
            (path + "?" + queryString).hashCode()
        val cachedAt = cache.getLong(cacheKey + "_time", 0L)
        val cacheTtl = when {
            path == "/fixtures" && queryString.contains("live=all") -> 30L * 1000L
            path == "/leagues" -> 24L * 60L * 60L * 1000L
            path == "/fixtures" -> 15L * 60L * 1000L
            path == "/odds" -> 3L * 60L * 60L * 1000L
            else -> 6L * 60L * 60L * 1000L
        }
        if (System.currentTimeMillis() - cachedAt in 0..cacheTtl) {
            cache.getString(cacheKey, null)?.let { cached ->
                runCatching { return JSONObject(cached) }
            }
        }
        val connection = (URL(fullUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15000
            readTimeout = 20000
            setRequestProperty("x-apisports-key", apiKey)
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "FootballAIPro/2.0")
        }

        val body = try {
            val stream = if (connection.responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }
            stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        } finally {
            connection.disconnect()
        }

        val json = try {
            JSONObject(body)
        } catch (_: Exception) {
            throw IllegalStateException("API-Football zwróciło nieprawidłową odpowiedź.")
        }

        val errors = json.optJSONObject("errors")
        if (errors != null && errors.length() > 0) {
            val details = errors.keys().asSequence()
                .map { key -> "$key: ${errors.opt(key)}" }
                .joinToString("; ")
            throw IllegalStateException("Błąd API-Football: $details")
        }
        cache.edit()
            .putString(cacheKey, body)
            .putLong(cacheKey + "_time", System.currentTimeMillis())
            .apply()
        return json
    }

    fun fetchCurrentLeagues(context: Context, apiKey: String, season: Int): List<ApiFootballLeague> {
        val leagues = mutableListOf<ApiFootballLeague>()
        var page = 1
        var totalPages = 1
        do {
            val payload = request(
                context,
                apiKey,
                "/leagues",
                mapOf("season" to season.toString(), "page" to page.toString())
            )
            totalPages = payload.optJSONObject("paging")?.optInt("total", 1)?.coerceAtLeast(1) ?: 1
            val response = payload.optJSONArray("response") ?: JSONArray()
            for (i in 0 until response.length()) {
                val item = response.optJSONObject(i) ?: continue
                val league = item.optJSONObject("league") ?: continue
                val country = item.optJSONObject("country")?.optString("name").orEmpty()
                val seasons = item.optJSONArray("seasons") ?: JSONArray()
                val seasonAvailable = (0 until seasons.length()).any { index ->
                    seasons.optJSONObject(index)?.optInt("year") == season
                }
                if (!seasonAvailable) continue
                val id = league.optInt("id")
                val name = league.optString("name")
                if (id > 0 && name.isNotBlank()) {
                    leagues.add(ApiFootballLeague(id, name, country, season))
                }
            }
            page++
        } while (page <= totalPages && page <= 100)
        return leagues.distinctBy { it.id }.sortedWith(compareBy({ it.country }, { it.name }))
    }

    fun fetchFixtures(
        context: Context,
        apiKey: String,
        date: String,
        leagueId: Int? = null,
        season: Int? = null
    ): List<ApiFootballFixture> {
        val baseQuery = linkedMapOf("date" to date, "timezone" to "Europe/Warsaw")
        if (leagueId != null && season != null) {
            baseQuery["league"] = leagueId.toString()
            baseQuery["season"] = season.toString()
        }
        val fixtures = mutableListOf<ApiFootballFixture>()
        var page = 1
        var totalPages = 1
        do {
            val query = baseQuery.toMutableMap()
            query["page"] = page.toString()
            val payload = request(context, apiKey, "/fixtures", query)
            totalPages = payload.optJSONObject("paging")?.optInt("total", 1)?.coerceAtLeast(1) ?: 1
            val response = payload.optJSONArray("response") ?: JSONArray()
            fixtures.addAll(parseFixtureResponse(response))
            page++
        } while (page <= totalPages && page <= 100)
        return fixtures.distinctBy { it.id }.sortedBy { it.date }
    }

    fun fetchLiveFixtures(
        context: Context,
        apiKey: String,
        leagueId: Int? = null
    ): List<ApiFootballFixture> {
        val baseQuery = linkedMapOf(
            "live" to "all",
            "timezone" to "Europe/Warsaw"
        )
        if (leagueId != null) baseQuery["league"] = leagueId.toString()

        val fixtures = mutableListOf<ApiFootballFixture>()
        var page = 1
        var totalPages = 1
        do {
            val query = baseQuery.toMutableMap()
            query["page"] = page.toString()
            val payload = request(context, apiKey, "/fixtures", query)
            totalPages = payload.optJSONObject("paging")
                ?.optInt("total", 1)?.coerceAtLeast(1) ?: 1
            val response = payload.optJSONArray("response") ?: JSONArray()
            fixtures.addAll(parseFixtureResponse(response))
            page++
        } while (page <= totalPages && page <= 100)

        return fixtures.distinctBy { it.id }.sortedWith(
            compareBy({ it.date }, { it.time }, { it.leagueName })
        )
    }

    private fun parseFixtureResponse(response: JSONArray): List<ApiFootballFixture> {
        val fixtures = mutableListOf<ApiFootballFixture>()
        for (i in 0 until response.length()) {
            val item = response.optJSONObject(i) ?: continue
            val fixture = item.optJSONObject("fixture") ?: continue
            val league = item.optJSONObject("league") ?: continue
            val teams = item.optJSONObject("teams") ?: continue
            val home = teams.optJSONObject("home")?.optString("name").orEmpty()
            val away = teams.optJSONObject("away")?.optString("name").orEmpty()
            if (home.isBlank() || away.isBlank()) continue

            val goals = item.optJSONObject("goals")
            val homeGoals = goals?.opt("home")
            val awayGoals = goals?.opt("away")
            val score = if (
                homeGoals != null && homeGoals != JSONObject.NULL &&
                awayGoals != null && awayGoals != JSONObject.NULL
            ) "$homeGoals : $awayGoals" else "Nie rozegrano"

            fixtures.add(
                ApiFootballFixture(
                    id = fixture.optInt("id"),
                    leagueId = league.optInt("id"),
                    leagueName = league.optString("name"),
                    home = home,
                    away = away,
                    date = fixture.optString("date").take(10),
                    time = fixture.optString("date").substringAfter('T', "").take(5),
                    status = fixture.optJSONObject("status")?.optString("short").orEmpty(),
                    score = score
                )
            )
        }
        return fixtures
    }

    fun fetchPredictions(context: Context, apiKey: String, fixtureId: Int): ApiFootballPredictionData? {
        val response = request(
            context, apiKey, "/predictions", mapOf("fixture" to fixtureId.toString())
        ).optJSONArray("response") ?: return null
        val item = response.optJSONObject(0) ?: return null
        val predictions = item.optJSONObject("predictions") ?: JSONObject()
        val winner = predictions.optJSONObject("winner")?.optString("name").orEmpty()
        val comparisonObject = item.optJSONObject("comparison") ?: JSONObject()
        val comparison = linkedMapOf<String, Pair<String, String>>()
        val keys = listOf("form", "att", "def", "poisson_distribution", "h2h", "goals", "total", "percent")
        for (key in keys) {
            val value = comparisonObject.optJSONObject(key) ?: continue
            comparison[key] = value.optString("home") to value.optString("away")
        }
        return ApiFootballPredictionData(
            advice = predictions.optString("advice"),
            winner = winner,
            homeWin = predictions.optJSONObject("percent")?.optString("home").orEmpty(),
            draw = predictions.optJSONObject("percent")?.optString("draw").orEmpty(),
            awayWin = predictions.optJSONObject("percent")?.optString("away").orEmpty(),
            homeForm = comparison["form"]?.first.orEmpty(),
            awayForm = comparison["form"]?.second.orEmpty(),
            comparison = comparison
        )
    }

    fun fetchInjuries(context: Context, apiKey: String, fixtureId: Int): List<ApiFootballInjury> {
        val response = request(
            context, apiKey, "/injuries", mapOf("fixture" to fixtureId.toString())
        ).optJSONArray("response") ?: JSONArray()
        val result = mutableListOf<ApiFootballInjury>()
        for (i in 0 until response.length()) {
            val item = response.optJSONObject(i) ?: continue
            val player = item.optJSONObject("player") ?: JSONObject()
            val team = item.optJSONObject("team") ?: JSONObject()
            result.add(
                ApiFootballInjury(
                    player = player.optString("name"),
                    team = team.optString("name"),
                    reason = listOf(player.optString("type"), player.optString("reason"))
                        .filter { it.isNotBlank() }.joinToString(" – ")
                )
            )
        }
        return result
    }

    fun fetchOdds(context: Context, apiKey: String, fixtureId: Int): List<ApiFootballOdds> {
        val response = request(
            context, apiKey, "/odds", mapOf("fixture" to fixtureId.toString())
        ).optJSONArray("response") ?: JSONArray()
        val result = mutableListOf<ApiFootballOdds>()
        for (i in 0 until response.length()) {
            val item = response.optJSONObject(i) ?: continue
            val bookmakers = item.optJSONArray("bookmakers") ?: continue
            for (b in 0 until bookmakers.length()) {
                val bookmaker = bookmakers.optJSONObject(b) ?: continue
                val bets = bookmaker.optJSONArray("bets") ?: continue
                for (j in 0 until bets.length()) {
                    val bet = bets.optJSONObject(j) ?: continue
                    val values = bet.optJSONArray("values") ?: continue
                    for (k in 0 until values.length()) {
                        val value = values.optJSONObject(k) ?: continue
                        result.add(
                            ApiFootballOdds(
                                bookmaker = bookmaker.optString("name"),
                                market = bet.optString("name"),
                                selection = value.optString("value"),
                                odd = value.optString("odd")
                            )
                        )
                    }
                }
            }
        }
        return result
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name())
}
