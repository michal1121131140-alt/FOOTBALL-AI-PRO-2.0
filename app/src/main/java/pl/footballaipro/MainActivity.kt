
package pl.footballaipro

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

private val Navy = Color(0xFF081426)
private val Panel = Color(0xFF12243B)
private val Blue = Color(0xFF168BFF)
private val White = Color(0xFFF2F6FC)
private val Muted = Color(0xFF9DB0C8)
private val Green = Color(0xFF27C987)
private val Red = Color(0xFFFF647C)

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            FootballApp(
                context = this,
                loadLeagues = { callback -> fetchLeagues(callback) },
                loadMatches = { league, date, callback ->
                    fetchMatches(league, date, callback)
                },
                loadTable = { league, callback ->
                    thread {
                        try {
                            val result = LeagueTableRepository.fetchTable(league)
                            runOnUiThread {
                                callback(result, "Tabela załadowana.")
                            }
                        } catch (e: Exception) {
                            runOnUiThread {
                                callback(emptyList(), "Błąd tabeli: ${e.message}")
                            }
                        }
                    }
                },
                loadAnalysis = { league, match, callback ->
                    thread {
                        try {
                            val result =
                                FootballDataRepository.fetchTeamStats(league, match)
                            runOnUiThread { callback(result, null) }
                        } catch (e: Exception) {
                            runOnUiThread { callback(null, e.message) }
                        }
                    }
                }
            )
        }
    }

    private fun request(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 15000
        connection.readTimeout = 15000
        connection.requestMethod = "GET"
        connection.setRequestProperty("User-Agent", "FootballAIPro/2.0")

        return try {
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun fetchLeagues(
        callback: (List<FootballLeague>, String) -> Unit
    ) {
        thread {
            try {
                val year = SimpleDateFormat("yyyy", Locale.US)
                    .format(Date()).toInt()

                val json = request(
                    "https://api.openligadb.de/getavailableleagues/$year"
                )

                val array = JSONArray(json)
                val leagues = mutableListOf<FootballLeague>()

                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    val shortcut = item.optString("leagueShortcut")
                    val name = item.optString("leagueName")
                    val season = item.optInt("leagueSeason", year)

                    if (shortcut.isNotBlank() && name.isNotBlank()) {
                        leagues.add(FootballLeague(name, shortcut, season))
                    }
                }

                runOnUiThread {
                    callback(
                        leagues.distinctBy { it.shortcut },
                        "Załadowano ${leagues.size} lig."
                    )
                }
            } catch (e: Exception) {
                runOnUiThread {
                    callback(emptyList(), "Błąd lig: ${e.message}")
                }
            }
        }
    }

    private fun fetchMatches(
        league: FootballLeague,
        selectedDate: String,
        callback: (List<FootballMatch>, List<FootballMatch>, String) -> Unit
    ) {
        thread {
            try {
                val json = request(
                    "https://api.openligadb.de/getmatchdata/" +
                        "${league.shortcut}/${league.season}"
                )

                val array = JSONArray(json)
                val allMatches = mutableListOf<FootballMatch>()

                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)

                    val home = item.optJSONObject("team1")
                        ?.optString("teamName").orEmpty()
                    val away = item.optJSONObject("team2")
                        ?.optString("teamName").orEmpty()

                    if (home.isBlank() || away.isBlank()) continue

                    val date = item.optString("matchDateTime").take(10)
                    val results = item.optJSONArray("matchResults")
                    var score = "Nie rozegrano"

                    if (results != null && results.length() > 0) {
                        var finalResult: JSONObject? = null

                        for (j in 0 until results.length()) {
                            val result = results.getJSONObject(j)
                            if (result.optString("resultName")
                                    .contains("Endergebnis", true)
                            ) {
                                finalResult = result
                                break
                            }
                        }

                        if (finalResult != null) {
                            val homeGoals = finalResult.opt("pointsTeam1")
                                ?.toString()?.toIntOrNull()
                            val awayGoals = finalResult.opt("pointsTeam2")
                                ?.toString()?.toIntOrNull()
                            if (homeGoals != null && awayGoals != null &&
                                homeGoals >= 0 && awayGoals >= 0
                            ) {
                                score = "$homeGoals : $awayGoals"
                            }
                        }
                    }

                    allMatches.add(
                        FootballMatch(
                            league.name,
                            home,
                            away,
                            score,
                            date,
                            "OpenLigaDB"
                        )
                    )
                }

                val dayMatches = allMatches.filter { it.date == selectedDate }

                runOnUiThread {
                    callback(
                        dayMatches,
                        allMatches,
                        if (dayMatches.isEmpty()) {
                            "Brak spotkań w wybranym dniu."
                        } else {
                            "Znaleziono ${dayMatches.size} spotkań."
                        }
                    )
                }
            } catch (e: Exception) {
                runOnUiThread {
                    callback(
                        emptyList(),
                        emptyList(),
                        "Błąd pobierania: ${e.message}"
                    )
                }
            }
        }
    }
}

@Composable
fun FootballApp(
    context: Context,
    loadLeagues: ((List<FootballLeague>, String) -> Unit) -> Unit,
    loadMatches: (
        FootballLeague,
        String,
        (List<FootballMatch>, List<FootballMatch>, String) -> Unit
    ) -> Unit,
    loadTable: (
        FootballLeague,
        (List<LeagueStanding>, String) -> Unit
    ) -> Unit,
    loadAnalysis: (
        FootballLeague,
        FootballMatch,
        (TeamAnalysisData?, String?) -> Unit
    ) -> Unit
) {
    var leagues by remember { mutableStateOf(emptyList<FootballLeague>()) }
    var matches by remember { mutableStateOf(emptyList<FootballMatch>()) }
    var seasonMatches by remember { mutableStateOf(emptyList<FootballMatch>()) }
    var globalMatches by remember { mutableStateOf(emptyList<FootballMatch>()) }
    var globalSeasonMatches by remember { mutableStateOf(emptyList<FootballMatch>()) }
    var table by remember { mutableStateOf(emptyList<LeagueStanding>()) }

    var selectedLeague by remember { mutableStateOf<FootballLeague?>(null) }
    var selectedMatch by remember { mutableStateOf<FootballMatch?>(null) }

    var analysis by remember { mutableStateOf<TeamAnalysisData?>(null) }
    var prediction by remember { mutableStateOf<MatchPrediction?>(null) }

    var date by remember {
        mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()))
    }

    var screen by remember { mutableStateOf("MECZE") }
    var status by remember { mutableStateOf("Ładowanie lig...") }
    var loading by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    var tableLoading by remember { mutableStateOf(false) }
    var analysisLoading by remember { mutableStateOf(false) }
    var showClearHistoryDialog by remember { mutableStateOf(false) }

    var oddsInput by remember { mutableStateOf("") }
    var stakeInput by remember { mutableStateOf("1.00") }
    var selectedMarket by remember { mutableStateOf("1") }
    var oddsAnalysis by remember { mutableStateOf<OddsAnalysis?>(null) }

    val history by FootballStorage.getHistory(context)
        .collectAsState(initial = emptyList())

    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        loadLeagues { result, message ->
            leagues = result
            selectedLeague = result.firstOrNull()
            status = message
        }
    }

    val combinedSeasonMatches = remember(seasonMatches, globalSeasonMatches) {
        (seasonMatches + globalSeasonMatches).distinctBy {
            "${it.league}|${it.home}|${it.away}|${it.date}"
        }
    }
    val combinedDailyMatches = remember(matches, globalMatches, date) {
        (matches.filter { it.date == date } + globalMatches.filter { it.date == date }).distinctBy {
            "${it.league}|${it.home}|${it.away}|${it.date}"
        }
    }

    var rankedMatches by remember { mutableStateOf(emptyList<RankedMatch>()) }
    var rankingLoading by remember { mutableStateOf(false) }

    LaunchedEffect(combinedDailyMatches, combinedSeasonMatches, date) {
        rankingLoading = true
        rankedMatches = withContext(Dispatchers.Default) {
            val previousForDate = combinedSeasonMatches.filter { it.date < date }
            val formByTeam = previousForDate
                .flatMap { listOf(it.home, it.away) }
                .distinct()
                .associateWith { team ->
                    TeamFormAnalyzer.calculate(team, previousForDate)
                }

            val predictions = combinedDailyMatches.mapNotNull { match ->
                val previous = combinedSeasonMatches.filter { it.date < match.date }
                val homeStats = buildTeamStats(match.home, previous)
                val awayStats = buildTeamStats(match.away, previous)

                if (homeStats != null && awayStats != null) {
                    try {
                        val homeForm = formByTeam[match.home]
                            ?: TeamFormAnalyzer.calculate(match.home, previous)
                        val awayForm = formByTeam[match.away]
                            ?: TeamFormAnalyzer.calculate(match.away, previous)
                        val h2h = HeadToHeadAnalyzer.calculate(
                            match.home, match.away, previous, 10
                        )
                        (match.league + "|" + match.home + "|" + match.away) to
                            FootballAnalyzer.calculate(
                                homeStats, awayStats, homeForm, awayForm, h2h
                            )
                    } catch (_: Exception) {
                        null
                    }
                } else {
                    null
                }
            }.toMap()

            DailyMatchRanker.rank(
                matches = combinedDailyMatches.filter {
                    FootballScoreParser.parse(it.score) == null
                },
                formByTeam = formByTeam,
                predictions = predictions,
                limit = 10
            )
        }
        rankingLoading = false
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Blue,
            onPrimary = White,
            secondary = Green,
            onSecondary = Navy,
            background = Navy,
            onBackground = White,
            surface = Panel,
            onSurface = White,
            surfaceVariant = Panel,
            onSurfaceVariant = Muted,
            error = Red,
            onError = White
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Navy)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(14.dp)
        ) {
            Text(
                "FOOTBALL AI PRO",
                color = White,
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold
            )

            Text(
                "2.0.4 • INTELIGENTNA ANALIZA PIŁKARSKA",
                color = Blue,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(15.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                listOf(
                    "MECZE",
                    "GLOBAL",
                    "TOP",
                    "TABELA",
                    "ANALIZA",
                    "HISTORIA",
                    "STATYSTYKI"
                ).forEach { item ->
                    Button(
                        onClick = { screen = item },
                        contentPadding = PaddingValues(
                            horizontal = 9.dp,
                            vertical = 8.dp
                        ),
                        colors = ButtonDefaults.buttonColors(
                            containerColor =
                                if (screen == item) Blue else Panel
                        )
                    ) {
                        Text(item, fontSize = 9.sp)
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            when (screen) {
                "GLOBAL" -> {
                    ApiFootballScreen(
                        context = context,
                        selectedDate = date,
                        onDateChanged = { date = it },
                        onFixturesLoaded = { loaded ->
                            globalMatches = loaded
                            if (loaded.isNotEmpty() && globalSeasonMatches.isNotEmpty()) {
                                val refreshedKeys = loaded.map {
                                    "${it.league}|${it.home}|${it.away}|${it.date}"
                                }.toSet()
                                globalSeasonMatches = (
                                    globalSeasonMatches.filterNot {
                                        "${it.league}|${it.home}|${it.away}|${it.date}" in refreshedKeys
                                    } + loaded
                                ).distinctBy {
                                    "${it.league}|${it.home}|${it.away}|${it.date}"
                                }
                            }
                        },
                        onSeasonMatchesLoaded = { globalSeasonMatches = it }
                    )
                }

                "MECZE" -> {
                    Text("WYBIERZ LIGĘ", color = Muted, fontSize = 12.sp)

                    Box {
                        Button(
                            onClick = { expanded = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Panel
                            )
                        ) {
                            Text(selectedLeague?.name ?: "Wybierz ligę", color = White)
                        }

                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                            modifier = Modifier.heightIn(max = 400.dp)
                        ) {
                            leagues.forEach { league ->
                                DropdownMenuItem(
                                    text = { Text(league.name) },
                                    onClick = {
                                        selectedLeague = league
                                        matches = emptyList()
                                        seasonMatches = emptyList()
                                        table = emptyList()
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Text("DATA (RRRR-MM-DD)", color = Muted, fontSize = 11.sp)

                    OutlinedTextField(
                        value = date,
                        onValueChange = { date = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(Modifier.height(8.dp))

                    Button(
                        onClick = {
                            val league = selectedLeague
                            if (league != null &&
                                FootballDateValidator.isValid(date)
                            ) {
                                loading = true
                                status = "Pobieranie meczów..."

                                loadMatches(league, date) { result, all, message ->
                                    matches = result
                                    seasonMatches = all

                                    var settledCount = 0
                                    all.forEach { match ->
                                        settledCount +=
                                            PredictionPerformance.settleMatch(context, match)
                                    }

                                    status = if (settledCount > 0) {
                                        "$message Rozliczono typów: $settledCount."
                                    } else {
                                        message
                                    }

                                    loading = false
                                }
                            } else {
                                status = "Wybierz ligę i poprawną datę."
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Blue)
                    ) {
                        Text("POBIERZ MECZE")
                    }

                    Text(status, color = Muted, fontSize = 11.sp)

                    if (loading) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = Blue
                        )
                    }

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(matches) { match ->
                            MatchCard(match) {
                                selectedMatch = match
                                analysis = null
                                prediction = null
                                oddsAnalysis = null
                                screen = "ANALIZA"
                            }
                        }
                    }
                }

                "TOP" -> {
                    Text(
                        "TOP 10 SPOTKAŃ",
                        color = White,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Ranking na podstawie formy i prognoz statystycznych.",
                        color = Muted,
                        fontSize = 11.sp
                    )
                    Spacer(Modifier.height(10.dp))

                    if (rankingLoading) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = Blue
                        )
                    } else if (combinedDailyMatches.isEmpty()) {
                        InfoCard("Pobierz mecze w zakładce MECZE lub GLOBAL.")
                    } else if (rankedMatches.isEmpty()) {
                        InfoCard("Brak spotkań do rankingu dla wybranej daty.")
                    }

                    if (
                        combinedDailyMatches.any { it.source == "API-Football" } &&
                        combinedSeasonMatches.none { it.source == "API-Football" }
                    ) {
                        InfoCard(
                            "Aby TOP analizował mecze globalne, w zakładce GLOBAL wybierz konkretną ligę i pobierz jej sezon."
                        )
                    }

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(rankedMatches) { ranked ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Panel, RoundedCornerShape(12.dp))
                                    .clickable {
                                        selectedMatch = ranked.match
                                        analysis = null
                                        prediction = ranked.prediction
                                        oddsAnalysis = null
                                        screen = "ANALIZA"
                                    }
                                    .padding(12.dp)
                            ) {
                                Text(
                                    "${ranked.match.home} — ${ranked.match.away}",
                                    color = White,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "Ocena TOP: ${"%.1f".format(Locale.US, ranked.rating)} / 100",
                                    color = Blue
                                )
                                Text(
                                    "Forma: ${"%.1f".format(Locale.US, ranked.homeForm)} – " +
                                        "${"%.1f".format(Locale.US, ranked.awayForm)}",
                                    color = Muted,
                                    fontSize = 11.sp
                                )
                                ranked.prediction?.let {
                                    Text(
                                        "1: ${"%.1f".format(Locale.US, it.homeWin)}%  " +
                                            "X: ${"%.1f".format(Locale.US, it.draw)}%  " +
                                            "2: ${"%.1f".format(Locale.US, it.awayWin)}%",
                                        color = Green,
                                        fontSize = 12.sp
                                    )
                                } ?: Text(
                                    "Brak danych do prognozy Poissona",
                                    color = Muted,
                                    fontSize = 11.sp
                                )
                                if (ranked.recommendedMarket != "BRAK REKOMENDACJI") {
                                    Text(
                                        "SUGEROWANY RYNEK: ${ranked.recommendedMarket} " +
                                            "(${"%.1f".format(Locale.US, ranked.recommendedProbability)}%)",
                                        color = Green,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        "Ocena ryzyka: ${ranked.riskLabel}",
                                        color = Muted,
                                        fontSize = 10.sp
                                    )
                                } else {
                                    Text(
                                        "Brak rekomendacji — za mało wiarygodnych danych.",
                                        color = Red,
                                        fontSize = 11.sp
                                    )
                                }
                                Text(
                                    ranked.explanation,
                                    color = Muted,
                                    fontSize = 11.sp
                                )
                                Text(
                                    "Otwórz pełną analizę",
                                    color = Blue,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }

                "TABELA" -> {
                    Text(
                        "TABELA LIGOWA",
                        color = White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Button(
                        onClick = {
                            selectedLeague?.let { league ->
                                tableLoading = true
                                loadTable(league) { result, message ->
                                    table = result
                                    status = message
                                    tableLoading = false
                                }
                            } ?: run {
                                status = "Najpierw wybierz ligę w zakładce MECZE."
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Blue)
                    ) {
                        Text("POBIERZ TABELĘ")
                    }

                    Text(status, color = Muted, fontSize = 11.sp)

                    if (tableLoading) {
                        CircularProgressIndicator(color = Blue)
                    }

                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(table) { team ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "${team.position}.",
                                    color = Blue,
                                    modifier = Modifier.width(30.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(team.teamName, color = White)
                                    Text(
                                        "Mecze: ${team.played} • Bilans: " +
                                            "${team.goalsScored}:${team.goalsConceded}",
                                        color = Muted,
                                        fontSize = 10.sp
                                    )
                                }
                                Text(
                                    "${team.points} pkt",
                                    color = Green,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            HorizontalDivider(color = Panel)
                        }
                    }
                }

                "ANALIZA" -> {
                    val match = selectedMatch

                    if (match == null) {
                        Text(
                            "WYBIERZ MECZ",
                            color = White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Przejdź do zakładki MECZE i wybierz spotkanie.",
                            color = Muted
                        )
                    } else {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                "${match.home} — ${match.away}",
                                color = White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text("${match.league} • ${match.date}", color = Muted)
                            Text("Wynik: ${match.score}", color = Blue)

                            Button(
                                onClick = {
                                    analysisLoading = true
                                    analysis = null
                                    prediction = null
                                    oddsAnalysis = null

                                    if (match.source == "API-Football") {
                                        scope.launch {
                                            try {
                                                val result = withContext(Dispatchers.Default) {
                                                    val previous = combinedSeasonMatches.filter {
                                                        it.date < match.date
                                                    }
                                                    val homeStats = buildTeamStats(match.home, previous)
                                                    val awayStats = buildTeamStats(match.away, previous)
                                                    if (homeStats == null || awayStats == null) {
                                                        null
                                                    } else {
                                                        val homeForm = TeamFormAnalyzer.calculate(
                                                            match.home, previous
                                                        )
                                                        val awayForm = TeamFormAnalyzer.calculate(
                                                            match.away, previous
                                                        )
                                                        val h2h = HeadToHeadAnalyzer.calculate(
                                                            match.home, match.away, previous, 10
                                                        )
                                                        TeamAnalysisData(homeStats, awayStats) to
                                                            FootballAnalyzer.calculate(
                                                                homeStats, awayStats, homeForm, awayForm, h2h
                                                            )
                                                    }
                                                }
                                                if (result != null) {
                                                    analysis = result.first
                                                    prediction = result.second
                                                    status = "Analiza globalna zakończona na podstawie wcześniejszych meczów sezonu."
                                                } else {
                                                    status = "Brak wystarczającej historii. W GLOBAL pobierz cały sezon tej ligi."
                                                }
                                            } catch (e: Exception) {
                                                status = "Błąd analizy globalnej: " +
                                                    (e.message ?: "nieznany błąd")
                                            } finally {
                                                analysisLoading = false
                                            }
                                        }
                                    } else {
                                        val league = leagues.firstOrNull {
                                            it.name == match.league
                                        } ?: selectedLeague

                                        if (league != null) {
                                            loadAnalysis(league, match) { result, error ->
                                                analysis = result
                                                prediction = result?.let {
                                                    val previous = seasonMatches.filter {
                                                        it.date < match.date
                                                    }
                                                    val homeForm = TeamFormAnalyzer.calculate(
                                                        match.home, previous
                                                    )
                                                    val awayForm = TeamFormAnalyzer.calculate(
                                                        match.away, previous
                                                    )
                                                    val h2h = HeadToHeadAnalyzer.calculate(
                                                        match.home, match.away, previous, 10
                                                    )
                                                    FootballAnalyzer.calculate(
                                                        it.home, it.away, homeForm, awayForm, h2h
                                                    )
                                                }
                                                status = error ?: "Analiza zakończona."
                                                analysisLoading = false
                                            }
                                        } else {
                                            status = "Nie znaleziono ligi dla tego spotkania."
                                            analysisLoading = false
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Blue)
                            ) {
                                Text("URUCHOM ANALIZĘ")
                            }

                            if (analysisLoading) {
                                CircularProgressIndicator(color = Blue)
                            }

                            Text(status, color = Muted, fontSize = 11.sp)

                            analysis?.let { data ->
                                SectionTitle("STATYSTYKI DRUŻYN")
                                TeamStatsCard(data.home)
                                TeamStatsCard(data.away)

                                SectionTitle("FORMA OSTATNICH SPOTKAŃ")

                                val analysisSeasonMatches = if (match.source == "API-Football") {
                                    combinedSeasonMatches
                                } else {
                                    seasonMatches
                                }
                                val previousMatches =
                                    analysisSeasonMatches.filter { it.date < match.date }

                                FormCard(
                                    TeamFormAnalyzer.calculate(
                                        match.home,
                                        previousMatches
                                    )
                                )
                                FormCard(
                                    TeamFormAnalyzer.calculate(
                                        match.away,
                                        previousMatches
                                    )
                                )

                                SectionTitle("BEZPOŚREDNIE POJEDYNKI")

                                val h2h = HeadToHeadAnalyzer.calculate(
                                    match.home,
                                    match.away,
                                    previousMatches,
                                    10
                                )

                                InfoCard(
                                    "Liczba spotkań: ${h2h.totalMatches}\n" +
                                        "Zwycięstwa gospodarzy: ${h2h.homeWins}\n" +
                                        "Remisy: ${h2h.draws}\n" +
                                        "Zwycięstwa gości: ${h2h.awayWins}\n" +
                                        "Średnia goli: ${"%.2f".format(Locale.US, h2h.averageGoals)}"
                                )
                            }

                            prediction?.let { result ->
                                SectionTitle("PROGNOZA STATYSTYCZNA")

                                InfoCard(
                                    "Zwycięstwo gospodarzy: ${"%.1f".format(Locale.US, result.homeWin)}%\n" +
                                        "Remis: ${"%.1f".format(Locale.US, result.draw)}%\n" +
                                        "Zwycięstwo gości: ${"%.1f".format(Locale.US, result.awayWin)}%\n\n" +
                                        "Powyżej 1.5 gola: ${"%.1f".format(Locale.US, result.over15)}%\n" +
                                        "Powyżej 2.5 gola: ${"%.1f".format(Locale.US, result.over25)}%\n" +
                                        "Powyżej 3.5 gola: ${"%.1f".format(Locale.US, result.over35)}%\n" +
                                        "Poniżej 2.5 gola: ${"%.1f".format(Locale.US, result.under25)}%\n\n" +
                                        "Obie drużyny strzelą: ${"%.1f".format(Locale.US, result.bttsYes)}%\n" +
                                        "Nie strzelą obie: ${"%.1f".format(Locale.US, result.bttsNo)}%\n\n" +
                                        "Oczekiwane gole gospodarzy: ${"%.2f".format(Locale.US, result.expectedHomeGoals)}\n" +
                                        "Oczekiwane gole gości: ${"%.2f".format(Locale.US, result.expectedAwayGoals)}\n\n" +
                                        "Wiarygodność danych: ${"%.0f".format(Locale.US, result.dataReliability)}%"
                                )

                                if (result.mostLikelyScores.isNotEmpty()) {
                                    SectionTitle("NAJBARDZIEJ PRAWDOPODOBNE WYNIKI")
                                    InfoCard(
                                        result.mostLikelyScores.joinToString("\n") { score ->
                                            "${score.homeGoals} : ${score.awayGoals} — " +
                                                "${"%.1f".format(Locale.US, score.probabilityPercent)}%"
                                        }
                                    )
                                }

                                SectionTitle("VALUE BETTING")
                                Text(
                                    "Wybierz rynek i wpisz kurs dziesiętny bukmachera.",
                                    color = Muted,
                                    fontSize = 11.sp
                                )
                                Text(
                                    "To analiza statystyczna, nie gwarancja wyniku ani porada finansowa. " +
                                        "Zakłady wiążą się z ryzykiem utraty pieniędzy. Graj odpowiedzialnie, tylko 18+.",
                                    color = Muted,
                                    fontSize = 10.sp
                                )

                                val markets = listOf(
                                    "1", "X", "2",
                                    "Over 1.5", "Over 2.5", "Over 3.5",
                                    "Under 2.5", "BTTS", "BTTS No"
                                )

                                markets.chunked(3).forEach { rowMarkets ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        rowMarkets.forEach { market ->
                                            Button(
                                                onClick = {
                                                    selectedMarket = market
                                                    oddsAnalysis = null
                                                },
                                                modifier = Modifier.weight(1f),
                                                contentPadding = PaddingValues(4.dp),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor =
                                                        if (selectedMarket == market) Blue
                                                        else Panel
                                                )
                                            ) {
                                                Text(market, fontSize = 11.sp)
                                            }
                                        }
                                    }
                                }

                                OutlinedTextField(
                                    value = oddsInput,
                                    onValueChange = {
                                        oddsInput = it
                                        oddsAnalysis = null
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("Kurs dziesiętny") },
                                    placeholder = { Text("np. 2,10") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Decimal
                                    )
                                )

                                OutlinedTextField(
                                    value = stakeInput,
                                    onValueChange = { stakeInput = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("Stawka") },
                                    placeholder = { Text("np. 10,00") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Decimal
                                    )
                                )

                                val marketProbability = when (selectedMarket) {
                                    "1" -> result.homeWin
                                    "X" -> result.draw
                                    "2" -> result.awayWin
                                    "Over 1.5" -> result.over15
                                    "Over 2.5" -> result.over25
                                    "Over 3.5" -> result.over35
                                    "Under 2.5" -> result.under25
                                    "BTTS" -> result.bttsYes
                                    "BTTS No" -> result.bttsNo
                                    else -> 0.0
                                }

                                Text(
                                    "Prawdopodobieństwo modelu dla $selectedMarket: " +
                                        "${"%.1f".format(Locale.US, marketProbability)}%",
                                    color = Muted,
                                    fontSize = 12.sp
                                )

                                Button(
                                    onClick = {
                                        val odds = oddsInput.replace(",", ".").toDoubleOrNull()
                                        if (odds == null || odds <= 1.0) {
                                            oddsAnalysis = null
                                            status = "Wpisz prawidłowy kurs większy niż 1,00."
                                        } else {
                                            try {
                                                oddsAnalysis = ValueBetting.calculate(
                                                    marketProbability,
                                                    odds
                                                )
                                                status = "Obliczono analizę Value Betting."
                                            } catch (e: Exception) {
                                                oddsAnalysis = null
                                                status = e.message
                                                    ?: "Nie udało się obliczyć kursu."
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = Blue)
                                ) {
                                    Text("OBLICZ VALUE BET")
                                }

                                oddsAnalysis?.let { value ->
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Panel, RoundedCornerShape(12.dp))
                                            .padding(13.dp),
                                        verticalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Text(
                                            if (value.isValueBet) {
                                                "POTENCJALNA WARTOŚĆ VALUE"
                                            } else {
                                                "BRAK WARTOŚCI VALUE"
                                            },
                                            color = if (value.isValueBet) Green else Red,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            "Kurs wpisany: ${"%.2f".format(Locale.US, value.decimalOdds)}",
                                            color = White
                                        )
                                        Text(
                                            "Prawdopodobieństwo modelu: ${"%.2f".format(Locale.US, value.modelProbability)}%",
                                            color = White
                                        )
                                        Text(
                                            "Prawdopodobieństwo z kursu: ${"%.2f".format(Locale.US, value.impliedProbability)}%",
                                            color = White
                                        )
                                        Text(
                                            "Kurs fair: ${"%.2f".format(Locale.US, value.fairOdds)}",
                                            color = White
                                        )
                                        Text(
                                            "Przewaga modelu: ${"%.2f".format(Locale.US, value.edgePercentagePoints)} p.p.",
                                            color = White
                                        )
                                        Text(
                                            "Oczekiwana wartość: ${"%.2f".format(Locale.US, value.expectedValuePercent)}%",
                                            color = if (value.isValueBet) Green else Red,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    Button(
                                        onClick = {
                                            val odds = oddsInput
                                                .replace(",", ".")
                                                .toDoubleOrNull()
                                            val stake = stakeInput
                                                .replace(",", ".")
                                                .toDoubleOrNull()

                                            val selection = when (selectedMarket) {
                                                "1" -> PredictionPerformance.HOME_WIN
                                                "X" -> PredictionPerformance.DRAW
                                                "2" -> PredictionPerformance.AWAY_WIN
                                                "Over 1.5" -> PredictionPerformance.OVER_15
                                                "Over 2.5" -> PredictionPerformance.OVER_25
                                                "Over 3.5" -> PredictionPerformance.OVER_35
                                                "Under 2.5" -> PredictionPerformance.UNDER_25
                                                "BTTS" -> PredictionPerformance.BTTS_YES
                                                "BTTS No" -> PredictionPerformance.BTTS_NO
                                                else -> null
                                            }

                                            if (odds == null || odds <= 1.0 ||
                                                stake == null || stake <= 0.0 ||
                                                selection == null
                                            ) {
                                                status = "Sprawdź kurs i stawkę."
                                            } else {
                                                try {
                                                    val record =
                                                        PredictionPerformance.createRecord(
                                                            match = match,
                                                            selection = selection,
                                                            odds = odds,
                                                            probability = marketProbability,
                                                            stake = stake
                                                        )

                                                    PredictionPerformance.save(
                                                        context,
                                                        record
                                                    )

                                                    status = "Typ zapisany do rozliczenia."
                                                } catch (e: Exception) {
                                                    status = e.message
                                                        ?: "Nie udało się zapisać typu."
                                                }
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Green
                                        )
                                    ) {
                                        Text("ZAPISZ TYP DO ROZLICZENIA")
                                    }
                                }

                                Text(
                                    "Model statystyczny nie gwarantuje wyniku. " +
                                        "Value Betting jest wyliczeniem matematycznym, " +
                                        "a nie pewnym sposobem zarabiania.",
                                    color = Muted,
                                    fontSize = 11.sp
                                )

                                Button(
                                    onClick = {
                                        scope.launch {
                                            val updated = history.filterNot { saved ->
                                                saved.league.equals(match.league, true) &&
                                                    saved.home.equals(match.home, true) &&
                                                    saved.away.equals(match.away, true) &&
                                                    saved.date == match.date &&
                                                    saved.source == match.source
                                            } + match
                                            FootballStorage.saveHistory(context, updated)
                                            status = "Mecz zapisany w historii."
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = Green)
                                ) {
                                    Text("ZAPISZ W HISTORII")
                                }
                            }
                        }
                    }
                }

                "HISTORIA" -> {
                    Text(
                        "HISTORIA ANALIZ",
                        color = White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text("Zapisane przez Ciebie spotkania.", color = Muted, fontSize = 11.sp)
                    Spacer(Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { showClearHistoryDialog = true },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Panel)
                        ) {
                            Text("WYCZYŚĆ")
                        }
                        Button(
                            onClick = {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/csv"
                                    putExtra(Intent.EXTRA_SUBJECT, "FOOTBALL AI PRO — historia meczów")
                                    putExtra(Intent.EXTRA_TEXT, FootballStorage.exportHistoryCsv(history))
                                }
                                context.startActivity(
                                    Intent.createChooser(shareIntent, "Eksportuj historię meczów")
                                )
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Blue)
                        ) {
                            Text("EKSPORT CSV")
                        }
                    }

                    if (showClearHistoryDialog) {
                        AlertDialog(
                            onDismissRequest = { showClearHistoryDialog = false },
                            title = { Text("Wyczyścić historię?") },
                            text = { Text("Zapisane mecze zostaną usunięte z tego urządzenia. Tej operacji nie można cofnąć.") },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        showClearHistoryDialog = false
                                        scope.launch {
                                            FootballStorage.saveHistory(context, emptyList())
                                            status = "Historia wyczyszczona."
                                        }
                                    }
                                ) { Text("WYCZYŚĆ") }
                            },
                            dismissButton = {
                                TextButton(onClick = { showClearHistoryDialog = false }) {
                                    Text("ANULUJ")
                                }
                            }
                        )
                    }

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(history) { match ->
                            MatchCard(match) {
                                selectedMatch = match
                                analysis = null
                                prediction = null
                                oddsAnalysis = null
                                screen = "ANALIZA"
                            }
                        }
                    }

                    if (history.isEmpty()) {
                        Text("Historia jest pusta.", color = Muted)
                    }
                }

                "STATYSTYKI" -> {
                    PerformanceScreen(combinedSeasonMatches)
                }
            }
        }
    }
}

// Tworzy sumy i podział dom/wyjazd z wcześniejszych zakończonych meczów.
private fun buildTeamStats(
    team: String,
    matches: List<FootballMatch>
): TeamStats? {
    var played = 0
    var scoredTotal = 0
    var concededTotal = 0
    var homePlayed = 0
    var homeScored = 0
    var homeConceded = 0
    var awayPlayed = 0
    var awayScored = 0
    var awayConceded = 0
    var wins = 0
    var draws = 0
    var losses = 0

    for (match in matches) {
        val score = FootballScoreParser.parse(match.score) ?: continue
        val isHome = match.home == team
        val isAway = match.away == team
        if (!isHome && !isAway) continue

        val scored = if (isHome) score.home else score.away
        val conceded = if (isHome) score.away else score.home
        played++
        scoredTotal += scored
        concededTotal += conceded

        if (isHome) {
            homePlayed++
            homeScored += scored
            homeConceded += conceded
        } else {
            awayPlayed++
            awayScored += scored
            awayConceded += conceded
        }

        when {
            scored > conceded -> wins++
            scored == conceded -> draws++
            else -> losses++
        }
    }

    if (played == 0) return null

    return TeamStats(
        teamName = team,
        matchesPlayed = played,
        goalsScored = scoredTotal.toDouble(),
        goalsConceded = concededTotal.toDouble(),
        wins = wins,
        draws = draws,
        losses = losses,
        homeMatchesPlayed = homePlayed,
        homeGoalsScored = if (homePlayed > 0) homeScored.toDouble() else null,
        homeGoalsConceded = if (homePlayed > 0) homeConceded.toDouble() else null,
        awayMatchesPlayed = awayPlayed,
        awayGoalsScored = if (awayPlayed > 0) awayScored.toDouble() else null,
        awayGoalsConceded = if (awayPlayed > 0) awayConceded.toDouble() else null
    )
}

@Composable
fun MatchCard(
    match: FootballMatch,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Panel, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(13.dp)
    ) {
        Text(
            "${match.home} — ${match.away}",
            color = White,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp
        )
        Spacer(Modifier.height(5.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(match.date, color = Muted, fontSize = 11.sp)
            Text(match.score, color = Blue, fontWeight = FontWeight.Bold)
        }
        Text(
            "Dotknij, aby przejść do analizy",
            color = Muted,
            fontSize = 10.sp
        )
    }
}

@Composable
fun SectionTitle(title: String) {
    Text(
        title,
        color = Blue,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold
    )
}

@Composable
fun InfoCard(text: String) {
    Text(
        text,
        modifier = Modifier
            .fillMaxWidth()
            .background(Panel, RoundedCornerShape(12.dp))
            .padding(13.dp),
        color = White,
        fontSize = 13.sp
    )
}

@Composable
fun TeamStatsCard(stats: TeamStats) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Panel, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Text(stats.teamName, color = White, fontWeight = FontWeight.Bold)
        Text("Rozegrane mecze: ${stats.matchesPlayed}", color = Muted)
        val matchesPlayed = stats.matchesPlayed.coerceAtLeast(1)
        Text(
            "Średnia strzelonych goli: ${"%.2f".format(Locale.US, stats.goalsScored / matchesPlayed)}",
            color = Muted
        )
        Text(
            "Średnia straconych goli: ${"%.2f".format(Locale.US, stats.goalsConceded / matchesPlayed)}",
            color = Muted
        )
        Text(
            "Mecze u siebie / na wyjeździe: ${stats.homeMatchesPlayed} / ${stats.awayMatchesPlayed}",
            color = Muted,
            fontSize = 11.sp
        )
        Text(
            "Zwycięstwa: ${stats.wins} • Remisy: ${stats.draws} • Porażki: ${stats.losses}",
            color = White
        )
    }
}

@Composable
fun FormCard(form: TeamForm) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Panel, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Text(form.teamName, color = White, fontWeight = FontWeight.Bold)
        Text("Ostatnie wyniki: ${form.lastResults}", color = Blue)
        Text(
            "Punkty: ${form.points} • Forma: ${"%.1f".format(Locale.US, form.formRating)}",
            color = Muted
        )
        Text(
            "Zwycięstwa: ${form.wins} • Remisy: ${form.draws} • Porażki: ${form.losses}",
            color = Muted
        )
        Text("Aktualna seria: ${form.currentStreak}", color = Muted)
    }
}
