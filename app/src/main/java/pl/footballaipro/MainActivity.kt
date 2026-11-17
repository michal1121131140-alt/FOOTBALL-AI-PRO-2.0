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
                }
            )
        }
    }

    private fun request(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 12000
        connection.readTimeout = 12000
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
                        if (leagues.isEmpty()) "Nie znaleziono lig."
                        else "Załadowano ${leagues.size} lig."
                    )
                }
            } catch (e: Exception) {
                runOnUiThread {
                    callback(emptyList(), "Błąd pobierania lig: ${e.message}")
                }
            }
        }
    }

    private fun fetchMatches(
        league: FootballLeague,
        selectedDate: String,
        callback: (List<FootballMatch>, String) -> Unit
    ) {
        thread {
            try {
                val json = request(
                    "https://api.openligadb.de/getmatchdata/" +
                        "${league.shortcut}/${league.season}"
                )

                val array = JSONArray(json)
                val matches = mutableListOf<FootballMatch>()

                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    val date = item.optString("matchDateTime").take(10)

                    if (date != selectedDate) continue

                    val team1 = item.optJSONObject("team1")
                    val team2 = item.optJSONObject("team2")

                    val home = team1?.optString("teamName").orEmpty()
                    val away = team2?.optString("teamName").orEmpty()

                    if (home.isBlank() || away.isBlank()) continue

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

                    matches.add(
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

                runOnUiThread {
                    callback(
                        matches,
                        if (matches.isEmpty())
                            "Brak spotkań tej ligi w wybranym dniu."
                        else
                            "Znaleziono ${matches.size} spotkań."
                    )
                }
            } catch (e: Exception) {
                runOnUiThread {
                    callback(emptyList(), "Błąd pobierania meczów: ${e.message}")
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
        (List<FootballMatch>, String) -> Unit
    ) -> Unit
) {
    var leagues by remember { mutableStateOf(emptyList<FootballLeague>()) }
    var matches by remember { mutableStateOf(emptyList<FootballMatch>()) }
    var selectedLeague by remember { mutableStateOf<FootballLeague?>(null) }
    var selectedMatch by remember { mutableStateOf<FootballMatch?>(null) }

    val history by FootballStorage.getHistory(context)
        .collectAsState(initial = emptyList())

    val scope = rememberCoroutineScope()

    var date by remember {
        mutableStateOf(
            SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        )
    }

    var status by remember { mutableStateOf("Ładowanie dostępnych lig...") }
    var expanded by remember { mutableStateOf(false) }
    var screen by remember { mutableStateOf("MECZE") }
    var loading by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        loadLeagues { result, message ->
            leagues = result
            selectedLeague = result.firstOrNull()
            status = message
        }
    }

    MaterialTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Navy)
                .padding(16.dp)
        ) {
            Text(
                "FOOTBALL AI",
                color = White,
                fontSize = 27.sp,
                fontWeight = FontWeight.ExtraBold
            )

            Text(
                "PRO 2.0  •  ANALIZA PIŁKARSKA",
                color = Blue,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("MECZE", "TOP", "ANALIZA", "HISTORIA").forEach { item ->
                    Button(
                        onClick = { screen = item },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (screen == item) Blue else Panel
                        ),
                        contentPadding = PaddingValues(
                            horizontal = 9.dp,
                            vertical = 8.dp
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(item, fontSize = 9.sp)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            when (screen) {
                "MECZE" -> {
                    Text("WYBIERZ ROZGRYWKI", color = Muted, fontSize = 12.sp)

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
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Text("DATA MECZÓW (RRRR-MM-DD)", color = Muted, fontSize = 12.sp)

                    OutlinedTextField(
                        value = date,
                        onValueChange = { date = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = White,
                            unfocusedTextColor = White,
                            focusedBorderColor = Blue,
                            unfocusedBorderColor = Muted
                        )
                    )

                    Spacer(Modifier.height(10.dp))

                    Button(
                        onClick = {
                            val league = selectedLeague
                            if (league != null &&
                                date.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
                                loading = true
                                status = "Pobieranie spotkań..."

                                loadMatches(league, date) { result, message ->
                                    matches = result
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
                        Text("POBIERZ MECZE", fontWeight = FontWeight.Bold)
                    }

                    Spacer(Modifier.height(12.dp))
                    Text(status, color = Muted, fontSize = 12.sp)

                    if (loading) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = Blue
                        )
                    }

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(matches) { match ->
                            MatchCard(match) {
                                selectedMatch = match
                                screen = "ANALIZA"
                            }
                        }
                    }
                }

                "TOP" -> {
                    Text(
                        "NAJCIEKAWSZE SPOTKANIA",
                        color = White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        "Spotkania z aktualnie pobranej daty. Ranking będzie " +
                            "oparty na rzeczywistych statystykach.",
                        color = Muted,
                        fontSize = 12.sp
                    )

                    Spacer(Modifier.height(12.dp))

                    LazyColumn {
                        items(matches.take(10)) { match ->
                            MatchCard(match) {
                                selectedMatch = match
                                screen = "ANALIZA"
                            }
                        }
                    }
                }

                "ANALIZA" -> {
                    Text(
                        "ANALIZA MECZU",
                        color = White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(Modifier.height(14.dp))

                    if (selectedMatch == null) {
                        Text(
                            "Wybierz mecz z listy, aby otworzyć analizę.",
                            color = Muted
                        )
                    } else {
                        val match = selectedMatch!!

                        Text(
                            "${match.home} — ${match.away}",
                            color = White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Text(match.league, color = Blue)
                        Text("Wynik: ${match.score}", color = White)

                        Spacer(Modifier.height(16.dp))

                        Text(
                            "DANE I PROGNOZY",
                            color = Blue,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(Modifier.height(8.dp))

                        Text(
                            "Źródło danych: ${match.source}\n" +
                                "Data spotkania: ${match.date}\n\n" +
                                "Prognozy 1X2, BTTS i Over/Under wymagają " +
                                "danych statystycznych obu drużyn. Nie " +
                                "podajemy fikcyjnych procentów.",
                            color = White
                        )

                        Spacer(Modifier.height(16.dp))

                        Button(
                            onClick = {
                                scope.launch {
                                    val updated = (history + match).distinct()
                                    FootballStorage.saveHistory(context, updated)
                                    status = "Analiza zapisana w historii."
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Blue
                            )
                        ) {
                            Text("ZAPISZ W HISTORII")
                        }

                        Text(status, color = Muted)
                    }
                }

                "HISTORIA" -> {
                    Text(
                        "ZAPISANE ANALIZY",
                        color = White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(Modifier.height(12.dp))

                    if (history.isEmpty()) {
                        Text(
                            "Nie zapisano jeszcze żadnych analiz.",
                            color = Muted
                        )
                    } else {
                        LazyColumn {
                            items(history) { match ->
                                MatchCard(match) {
                                    selectedMatch = match
                                    screen = "ANALIZA"
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Text(
                "FOOTBALL AI PRO 2.0  •  DANE STATYSTYCZNE",
                color = Muted,
                fontSize = 10.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
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
            .background(Panel, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(14.dp)
    ) {
        Text(match.league, color = Blue, fontSize = 11.sp)
        Spacer(Modifier.height(6.dp))

        Text(
            match.home,
            color = White,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )

        Text(
            match.away,
            color = White,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )

        Spacer(Modifier.height(7.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Wynik: ${match.score}", color = White, fontSize = 13.sp)
            Text("ANALIZUJ  ›", color = Blue, fontSize = 12.sp)
        }

        Text("Źródło: ${match.source}", color = Muted, fontSize = 10.sp)
    }
}
