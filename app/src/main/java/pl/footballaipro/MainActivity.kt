package pl.footballaipro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

private val Navy = Color(0xFF071426)
private val Panel = Color(0xFF11243B)
private val Blue = Color(0xFF2196F3)
private val White = Color(0xFFF2F6FC)
private val Muted = Color(0xFF9FB1C8)

data class FootballMatch(
    val league: String,
    val home: String,
    val away: String,
    val score: String,
    val time: String,
    val source: String
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FootballApp(
                onLoad = { date, update ->
                    loadFootballMatches(date, update)
                }
            )
        }
    }

    private fun loadFootballMatches(
        date: String,
        update: (List<FootballMatch>, String) -> Unit
    ) {
        thread {
            val found = mutableListOf<FootballMatch>()
            val errors = mutableListOf<String>()

            try {
                val address =
                    "https://www.thesportsdb.com/api/v1/json/123/eventsday.php?d=$date&s=Soccer"
                val json = JSONObject(download(address))
                val events = json.optJSONArray("events") ?: JSONArray()

                for (i in 0 until events.length()) {
                    val event = events.optJSONObject(i) ?: continue
                    val home = event.optString("strHomeTeam", "")
                    val away = event.optString("strAwayTeam", "")
                    if (home.isBlank() || away.isBlank()) continue

                    val hs = event.optString("intHomeScore", "")
                    val ascore = event.optString("intAwayScore", "")
                    val score = if (
                        hs.isNotBlank() && ascore.isNotBlank() &&
                        hs != "null" && ascore != "null"
                    ) "$hs : $ascore" else "vs"

                    found.add(
                        FootballMatch(
                            event.optString("strLeague", "Piłka nożna"),
                            home, away, score,
                            event.optString("strTime", ""),
                            "TheSportsDB"
                        )
                    )
                }
            } catch (e: Exception) {
                errors.add("TheSportsDB")
            }

            try {
                val year = date.substring(0, 4)
                val address = "https://api.openligadb.de/getmatchdata/bl1/$year"
                val json = JSONArray(download(address))

                for (i in 0 until json.length()) {
                    val match = json.optJSONObject(i) ?: continue
                    val matchDate = match.optString("matchDateTime", "")
                    if (!matchDate.startsWith(date)) continue

                    val team1 = match.optJSONObject("team1")
                        ?.optString("teamName", "") ?: ""
                    val team2 = match.optJSONObject("team2")
                        ?.optString("teamName", "") ?: ""
                    if (team1.isBlank() || team2.isBlank()) continue

                    var score = "vs"
                    val results = match.optJSONArray("matchResults")
                    if (results != null) {
                        for (j in 0 until results.length()) {
                            val result = results.optJSONObject(j) ?: continue
                            if (result.optString("resultName") == "Endergebnis") {
                                score =
                                    "${result.optInt("pointsTeam1")} : ${result.optInt("pointsTeam2")}"
                            }
                        }
                    }

                    found.add(
                        FootballMatch(
                            match.optString("leagueName", "Bundesliga"),
                            team1, team2, score,
                            matchDate.substringAfter("T").take(5),
                            "OpenLigaDB"
                        )
                    )
                }
            } catch (e: Exception) {
                errors.add("OpenLigaDB")
            }

            val unique = found.distinctBy {
                "${it.home.lowercase()}-${it.away.lowercase()}-$date"
            }.sortedBy { it.time }

            runOnUiThread {
                val status = when {
                    unique.isNotEmpty() ->
                        "Znaleziono ${unique.size} meczów. Źródła: ${unique.map { it.source }.distinct().joinToString(", ")}"
                    errors.size == 2 ->
                        "Oba źródła są chwilowo niedostępne."
                    else -> "Brak znalezionych meczów dla tej daty."
                }
                update(unique, status)
            }
        }
    }
}

@Composable
fun FootballApp(
    onLoad: (String, (List<FootballMatch>, String) -> Unit) -> Unit
) {
    var date by remember {
        mutableStateOf(
            SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        )
    }
    var matches by remember { mutableStateOf(listOf<FootballMatch>()) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("Wybierz datę i pobierz mecze.") }

    MaterialTheme(colorScheme = darkColorScheme(
        background = Navy,
        surface = Panel,
        primary = Blue,
        onBackground = White,
        onSurface = White
    )) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Navy)
                .verticalScroll(rememberScrollState())
                .padding(18.dp)
        ) {
            Text(
                "FOOTBALL AI PRO",
                fontSize = 27.sp,
                fontWeight = FontWeight.ExtraBold,
                color = White
            )
            Text(
                "WERSJA 2.0  •  MULTI DATA",
                color = Blue,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(24.dp))
            Text(
                "MECZE I WYNIKI",
                color = White,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = date,
                onValueChange = { date = it },
                label = { Text("Data (RRRR-MM-DD)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = White,
                    unfocusedTextColor = White,
                    focusedBorderColor = Blue,
                    unfocusedBorderColor = Muted,
                    focusedLabelColor = Blue,
                    unfocusedLabelColor = Muted
                )
            )

            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    if (!date.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
                        message = "Wpisz datę w formacie RRRR-MM-DD."
                    } else {
                        loading = true
                        message = "Pobieranie danych z kilku źródeł..."
                        matches = emptyList()
                        onLoad(date) { result, status ->
                            matches = result
                            message = status
                            loading = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !loading,
                colors = ButtonDefaults.buttonColors(containerColor = Blue)
            ) {
                Text(
                    if (loading) "POBIERANIE..." else "POBIERZ MECZE",
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(16.dp))
            Text(message, color = Muted, fontSize = 13.sp)
            Spacer(Modifier.height(18.dp))

            matches.forEach { match ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    colors = CardDefaults.cardColors(containerColor = Panel),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(Modifier.padding(15.dp)) {
                        Text(
                            match.league,
                            color = Blue,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            match.home,
                            color = White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            match.score,
                            color = Blue,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            match.away,
                            color = White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "${match.time}  •  ${match.source}",
                            color = Muted,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Text(
                "DANE ZEWNĘTRZNE • WYNIKI MOGĄ BYĆ NIEPEŁNE",
                color = Muted,
                fontSize = 10.sp
            )
        }
    }
}

private fun download(address: String): String {
    val connection = URL(address).openConnection() as HttpURLConnection
    try {
        connection.requestMethod = "GET"
        connection.connectTimeout = 12000
        connection.readTimeout = 12000
        connection.setRequestProperty("Accept", "application/json")

        if (connection.responseCode !in 200..299) {
            throw Exception("HTTP ${connection.responseCode}")
        }

        return connection.inputStream.bufferedReader().use {
            it.readText()
        }
    } finally {
        connection.disconnect()
    }
}
