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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

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
                            runOnUiThread { callback(result, "Tabela załadowana.") }
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
                                    .contains("Endergebnis", true)) {
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
                        if (dayMatches.isEmpty())
                            "Brak spotkań w wybranym dniu."
                        else
                            "Znaleziono ${dayMatches.size} spotkań."
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

    var oddsText by remember { mutableStateOf("2.00") }
    var selectedProbability by remember { mutableStateOf("HOME") }

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

    val formByTeam = remember(seasonMatches) {
        seasonMatches.map { listOf(it.home, it.away) }
            .flatten()
            .distinct()
            .associateWith { team ->
                TeamFormAnalyzer.calculate(team, seasonMatches)
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
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                listOf("MECZE", "TOP", "TABELA", "ANALIZA", "HISTORIA")
                    .forEach { item ->
                        Button(
                            onClick = { screen = item },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(
                                horizontal = 2.dp,
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
                                date.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
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
                                    ranked.match.home + " — " + ranked.match.away,
                                    color = White,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "Ocena formy: ${"%.1f".format(ranked.rating)} / 100",
                                    color = Blue
                                )
                                Text(ranked.explanation, color = Muted, fontSize = 11.sp)
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
        
