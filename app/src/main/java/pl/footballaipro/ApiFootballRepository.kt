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
    val season: Int,
    val seasons: List<Int> = listOf(season)
)

data class ApiFootballStanding(
    val rank: Int,
    val team: String,
    val points: Int,
    val played: Int,
    val wins: Int,
    val draws: Int,
    val losses: Int,
    val goalsFor: Int,
    val goalsAgainst: Int,
    val goalDifference: Int,
    val form: String
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

internal fun apiHttpErrorMessage(statusCode: Int, payload: JSONObject?): String {
    val details = payload?.optJSONObject("errors")?.let { errors ->
        errors.keys().asSequence()
            .map { key -> "$key: ${errors.opt(key)}" }
            .joinToString("; ")
            .takeIf { it.isNotBlank() }
    } ?: payload?.optString("message")?.takeIf { it.isNotBlank() }

    val explanation = when (statusCode) {
        400 -> "Nieprawidłowe parametry zapytania."
        401 -> "Klucz API-Football jest nieprawidłowy lub nieaktywny."
        403 -> "Plan API nie pozwala na pobranie tych danych."
        404 -> "Nie znaleziono żądanego zasobu."
        429 -> "Przekroczono limit zapytań API. Spróbuj później."
        in 500..599 -> "Serwer API-Football ma chwilowy problem."
        else -> "API-Football zwróciło błąd HTTP $statusCode."
    }
    return if (details != null) "$explanation Szczegóły: $details" else explanation
}

/**
 * API-Football v3 integration.
 * All requests are explicit and user-triggered; the caller should cache results
 * to respect the free plan's daily request quota.
 */
object ApiFootballRepository {
    private const val BASE_URL = "https://v3.football.api-sports.io"

    fun clearCache(context: Context) {
        context.getSharedPreferences(
            "api_football_response_cache",
            Context.MODE_PRIVATE
        ).edit().clear().apply()
    }

    private fun request(context: Context, apiKey: String, path: String, query: Map<String, String> = emptyMap()): JSONObject {
        require(apiKey.isNotBlank()) { "Dodaj klucz API-Football w ustawieniach." }
        val queryString = query.entries.joinToString("&") { (key, value) ->
            "${encode(key)}=${encode(value)}"
        }
        val fullUrl = BASE_URL + path + if (queryString.isNotEmpty()) "?$queryString" else ""
        val cache = context.getSharedPreferences("api_football_response_cache", Context.MODE_PRIVATE)
        // Keep cached responses separate when the user changes API credentials.
        // Only a short SHA-256 fingerprint is stored; the API key itself is not.
        val sha256 = MessageDigest.getInstance("SHA-256")
        fun fingerprint(value: String): String =
            sha256.digest(value.toByteArray(Charsets.UTF_8))
                .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

        // Hash the complete request identity instead of String.hashCode(), which is
        // only 32-bit and can collide for different leagues, seasons or dates.
        val credentialFingerprint = fingerprint(apiKey).take(32)
        val requestFingerprint = fingerprint(path + "?" + queryString)
        val cacheKey = "response_" + credentialFingerprint + "_" + requestFingerprint
        val cachedAt = cache.getLong(cacheKey + "_time", 0L)
        val cacheTtl = when {
            path == "/fixtures" && queryString.contains("live=all") -> 30L * 1000L
            path == "/leagues" -> 24L * 60L * 60L * 1000L
            path == "/fixtures" && queryString.contains("date=") -> 15L * 60L * 1000L
            path == "/fixtures" -> 6L * 60L * 60L * 1000L
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

        val responseCode: Int
        val body = try {
            responseCode = connection.responseCode
            val stream = if (responseCode in 200..299) {
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
            if (responseCode !in 200..299) {
                throw IllegalStateException(apiHttpErrorMessage(responseCode, null))
            }
            throw IllegalStateException("API-Football zwróciło nieprawidłową odpowiedź.")
        }

        if (responseCode !in 200..299) {
            throw IllegalStateException(apiHttpErrorMessage(responseCode, json))
        }

        val errors = json.optJSONObject("errors")
        if (errors != null && errors.length() > 0) {
            val details = errors.keys().asSequence()
                .map { key -> "$key: ${errors.opt(key)}" }
                .joinToString("; ")
            throw IllegalStateException("Błąd API-Football: $details")
        }
        val editor = cache.edit()
            .putString(cacheKey, body)
            .putLong(cacheKey + "_time", System.currentTimeMillis())
        val timestampKeys = cache.all.keys.filter {
            it.startsWith("response_") && it.endsWith("_time")
        }
        if (timestampKeys.size > 200) {
            val removeCount = timestampKeys.size - 150
            timestampKeys
                .sortedBy { cache.getLong(it, 0L) }
                .take(removeCount)
                .forEach { timestampKey ->
                    val responseKey = timestampKey.removeSuffix("_time")
                    editor.remove(responseKey).remove(timestampKey)
                }
        }
        editor.apply()
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
                val seasonsArray = item.optJSONArray("seasons") ?: JSONArray()
                val seasonYears = (0 until seasonsArray.length())
                    .mapNotNull { index ->
                        seasonsArray.optJSONObject(index)?.optInt("year")
                            ?.takeIf { it > 0 }
                    }
                    .distinct()
                    .sortedDescending()
                if (season !in seasonYears) continue
                val id = league.optInt("id")
                val name = league.optString("name")
                if (id > 0 && name.isNotBlank()) {
                    leagues.add(ApiFootballLeague(id, name, country, season, seasonYears))
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
        return fixtures.distinctBy { it.id }.sortedWith(compareBy({ it.date }, { it.time }, { it.leagueName }))
    }

    fun fetchSeasonFixtures(
        context: Context,
        apiKey: String,
        leagueId: Int,
        season: Int
    ): List<ApiFootballFixture> {
        val baseQuery = linkedMapOf(
            "league" to leagueId.toString(),
            "season" to season.toString(),
            "timezone" to "Europe/Warsaw"
        )
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

    fun fetchStandings(
        context: Context,
        apiKey: String,
        leagueId: Int,
        season: Int
    ): List<ApiFootballStanding> {
        val response = request(
            context,
            apiKey,
            "/standings",
            mapOf("league" to leagueId.toString(), "season" to season.toString())
        ).optJSONArray("response") ?: return emptyList()

        val result = mutableListOf<ApiFootballStanding>()
        for (i in 0 until response.length()) {
            val league = response.optJSONObject(i)?.optJSONObject("league") ?: continue
            val groups = league.optJSONArray("standings") ?: continue
            for (groupIndex in 0 until groups.length()) {
                val group = groups.optJSONArray(groupIndex) ?: continue
                for (rowIndex in 0 until group.length()) {
                    val row = group.optJSONObject(rowIndex) ?: continue
                    val team = row.optJSONObject("team")?.optString("name").orEmpty()
                    if (team.isBlank()) continue
                    val all = row.optJSONObject("all") ?: JSONObject()
                    val goals = all.optJSONObject("goals") ?: JSONObject()
                    result.add(
                        ApiFootballStanding(
                            rank = row.optInt("rank"),
                            team = team,
                            points = row.optInt("points"),
                            played = all.optInt("played"),
                            wins = all.optInt("win"),
                            draws = all.optInt("draw"),
                            losses = all.optInt("lose"),
                            goalsFor = goals.optInt("for"),
                            goalsAgainst = goals.optInt("against"),
                            goalDifference = row.optInt("goalsDiff"),
                            form = row.optString("form")
                        )
                    )
                }
            }
        }
        return result.sortedWith(compareBy({ it.rank }, { it.team }))
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
