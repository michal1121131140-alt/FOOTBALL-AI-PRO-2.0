package pl.footballaipro

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
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

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            FootballApp(
                context = this,
                loadLeagues = { callback ->
                    fetchLeagues(callback)
                },
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
                            val result = FootballDataRepository.fetchTeamStats(
                                league,
                                match
                            )
                            runOnUiThread {
                                callback(result, null)
                            }
                        } catch (e: Exception) {
                            runOnUiThread {
                                callback(null, e.message)
                            }
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
                        leagues.add(
                            FootballLeague(name, shortcut, season)
                        )
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
                        var chosen: JSONObject? = null

                        for (j in 0 until results.length()) {
                            val result = results.getJSONObject(j)
                            if (result.optString("resultName")
                                    .contains("Endergebnis", true)
                            ) {
                                chosen = result
                                break
                            }
                        }

                        if (chosen == null) {
                            chosen = results.getJSONObject(results.length() - 1)
                        }

                        score = "${chosen.optInt("pointsTeam1")} : " +
                            "${chosen.optInt("pointsTeam2")}"
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

                val dayMatches = allMatches.filter {
                    it.date == selectedDate
                }

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
    var table by remember { mutableStateOf(emptyList<LeagueStanding>()) }

    var selectedLeague by remember { mutableStateOf<FootballLeague?>(null) }
    var selectedMatch by remember { mutableStateOf<FootballMatch?>(null) }

    var analysis by remember { mutableStateOf<TeamAnalysisData?>(null) }
    var prediction by remember { mutableStateOf<MatchPrediction?>(null) }

    var date by remember {
        mutableStateOf(
            SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        )
    }

    var screen by remember { mutableStateOf("MECZE") }
    var status by remember { mutableStateOf("Ładowanie lig...") }
    var loading by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    var tableLoading by remember { mutableStateOf(false) }
    var analysisLoading by remember { mutableStateOf(false) }

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

    val formByTeam = remember(seasonMatches, selectedMatch) {
        val cutoff = selectedMatch?.date ?: "9999-99-99"
        val previousMatches = seasonMatches.filter { it.date < cutoff }

        previousMatches
            .flatMap { listOf(it.home, it.away) }
            .distinct()
            .associateWith { team ->
                TeamFormAnalyzer.calculate(team, previousMatches)
            }
    }

    val rankedMatches = remember(matches, formByTeam) {
        DailyMatchRanker.rank(
            matches = matches,
            formByTeam = formByTeam,
            predictions = emptyMap(),
            limit = 10
        )
    }

    MaterialTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Navy)
                .padding(14.dp)
        ) {
            Text(
                "FOOTBALL AI PRO",
                color = White,
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold
            )

            Text(
                "2.0  •  INTELIGENTNA ANALIZA PIŁKARSKA",
                color = Blue,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(15.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                listOf("MECZE", "TOP", "TABELA", "ANALIZA", "HISTORIA")
                    .forEach { item ->
                        Button(
                            onClick = { screen = item },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(
                                horizontal = 1.dp,
                                vertical = 8.dp
                            ),
                            colors = ButtonDefaults.buttonColors(
                                containerColor =
                                    if (screen == item) Blue else Panel
                            )
                        ) {
                            Text(item, fontSize = 8.sp)
                        }
                    }
            }

            Spacer(Modifier.height(14.dp))

            when (screen) {

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
                            Text(
                                selectedLeague?.name ?: "Wybierz ligę",
                                color = White
                            )
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
                                date.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))
                            ) {
                                loading = true
                                status = "Pobieranie meczów..."

                                loadMatches(league, date) { result, all, message ->
                                    matches = result
                                    seasonMatches = all
                                    status = message
                                    loading = false
                                }
                            } else {
                                status = "Wybierz ligę i poprawną datę."
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Blue
                        )
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
                        "Ranking orientacyjny na podstawie dostępnej formy.",
                        color = Muted,
                        fontSize = 11.sp
                    )

                    Spacer(Modifier.height(10.dp))

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
                                        prediction = null
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
                                    "Ocena formy: ${"%.1f".format(Locale.US, ranked.rating)} / 100",
                                    color = Blue
                                )
                                Text(
                                    ranked.explanation,
                                    color = Muted,
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
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Blue
                        )
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
                                        "Mecze: ${team.played}  •  Bilans: ${team.goalsScored}:${team.goalsConceded}",
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

                            Text(
                                "${match.league} • ${match.date}",
                                color = Muted
                            )

                            Text(
                                "Wynik: ${match.score}",
                                color = Blue
                            )

                            Button(
                                onClick = {
                                    val league = selectedLeague
                                    if (league != null) {
                                        analysisLoading = true
                                        analysis = null
                                        prediction = null

                                        loadAnalysis(league, match) { result, error ->
                                            analysis = result
                                            prediction = result?.let {
                                                FootballAnalyzer.calculate(
                                                    it.home,
                                                    it.away
                                                )
                                            }
                                            status = error ?: "Analiza zakończona."
                                            analysisLoading = false
                                        }
                                    } else {
                                        status = "Wybierz ligę w zakładce MECZE."
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Blue
                                )
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

                                val previousMatches = seasonMatches.filter {
                                    it.date < match.date
                                }

                                val homeForm = TeamFormAnalyzer.calculate(
                                    match.home,
                                    previousMatches
                                )
                                val awayForm = TeamFormAnalyzer.calculate(
                                    match.away,
                                    previousMatches
                                )

                                FormCard(homeForm)
                                FormCard(awayForm)

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
                                        "Oczekiwane gole gości: ${"%.2f".format(Locale.US, result.expectedAwayGoals)}"
                                )

                                Text(
                                    "Prognoza jest wynikiem modelu statystycznego, a nie gwarancją rezultatu.",
                                    color = Muted,
                                    fontSize = 11.sp
                                )

                                Button(
                                    onClick = {
                                        scope.launch {
                                            val updated = (history + match).distinct()
                                            FootballStorage.saveHistory(context, updated)
                                            status = "Mecz zapisany w historii."
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Green
                                    )
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

                    Text(
                        "Zapisane przez Ciebie spotkania.",
                        color = Muted,
                        fontSize = 11.sp
                    )

                    Spacer(Modifier.height(10.dp))

                    Button(
                        onClick = {
                            scope.launch {
                                FootballStorage.saveHistory(context, emptyList())
                                status = "Historia wyczyszczona."
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Panel
                        )
                    ) {
                        Text("WYCZYŚĆ HISTORIĘ")
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
                                screen = "ANALIZA"
                            }
                        }
                    }

                    if (history.isEmpty()) {
                        Text(
                            "Historia jest pusta.",
                            color = Muted
                        )
                    }
                }
            }
        }
    }
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
        Text(
            stats.teamName,
            color = White,
            fontWeight = FontWeight.Bold
        )

        Text(
            "Rozegrane mecze: ${stats.matchesPlayed}",
            color = Muted
        )
        Text(
            "Średnia strzelonych goli: ${"%.2f".format(Locale.US, stats.goalsScored)}",
            color = Muted
        )
        Text(
            "Średnia straconych goli: ${"%.2f".format(Locale.US, stats.goalsConceded)}",
            color = Muted
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
        Text(
            form.teamName,
            color = White,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Ostatnie wyniki: ${form.lastResults}",
            color = Blue
        )
        Text(
            "Punkty: ${form.points} • Forma: ${"%.1f".format(Locale.US, form.formRating)}",
            color = Muted
        )
        Text(
            "Zwycięstwa: ${form.wins} • Remisy: ${form.draws} • Porażki: ${form.losses}",
            color = Muted
        )
        Text(
            "Aktualna seria: ${form.currentStreak}",
            color = Muted
        )
    }
}
