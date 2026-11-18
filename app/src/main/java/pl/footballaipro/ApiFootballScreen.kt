package pl.footballaipro

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

private val ApiPanel = Color(0xFF12243B)
private val ApiBlue = Color(0xFF168BFF)
private val ApiWhite = Color(0xFFF2F6FC)
private val ApiMuted = Color(0xFF9DB0C8)
private val ApiGreen = Color(0xFF27C987)

private data class FixtureResearch(
    val prediction: ApiFootballPredictionData?,
    val injuries: List<ApiFootballInjury>,
    val odds: List<ApiFootballOdds>
)

@Composable
fun ApiFootballScreen(context: Context) {
    var apiKey by remember { mutableStateOf(ApiFootballSettings.getApiKey(context)) }
    var date by remember { mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())) }
    var fixtures by remember { mutableStateOf(emptyList<ApiFootballFixture>()) }
    var selected by remember { mutableStateOf<ApiFootballFixture?>(null) }
    var research by remember { mutableStateOf<FixtureResearch?>(null) }
    var status by remember { mutableStateOf("Wpisz klucz API-Football i pobierz mecze.") }
    var loading by remember { mutableStateOf(false) }
    var showKey by remember { mutableStateOf(false) }
    var leagueCount by remember { mutableStateOf<Int?>(null) }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("GLOBALNE MECZE I ANALIZA", color = ApiWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text("Mecze ze wszystkich rozgrywek dostępnych w bazie API-Football.", color = ApiMuted, fontSize = 11.sp)

        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Klucz API-Football") },
            visualTransformation = if (showKey) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = { TextButton(onClick = { showKey = !showKey }) { Text(if (showKey) "UKRYJ" else "POKAŻ") } }
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    ApiFootballSettings.saveApiKey(context, apiKey)
                    status = "Klucz zapisany na tym urządzeniu."
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = ApiPanel)
            ) { Text("ZAPISZ KLUCZ") }

            Button(
                onClick = {
                    if (apiKey.isBlank()) {
                        status = "Wpisz klucz API."
                    } else {
                        loading = true
                        status = "Sprawdzam klucz i dostępne ligi..."
                        thread {
                            try {
                                val year = SimpleDateFormat("yyyy", Locale.US).format(Date()).toInt()
                                val count = ApiFootballRepository.fetchCurrentLeagues(apiKey, year).size
                                ApiFootballSettings.saveApiKey(context, apiKey)
                                Handler(Looper.getMainLooper()).post {
                                    leagueCount = count
                                    status = "Klucz działa. Dostępne ligi w bieżącym sezonie: " + count
                                    loading = false
                                }
                            } catch (e: Exception) {
                                Handler(Looper.getMainLooper()).post {
                                    status = "Błąd klucza/API: " + (e.message ?: "nieznany błąd")
                                    loading = false
                                }
                            }
                        }
                    }
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = ApiBlue)
            ) { Text("SPRAWDŹ API") }
        }

        OutlinedTextField(
            value = date,
            onValueChange = { date = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Data (RRRR-MM-DD)") }
        )

        Button(
            onClick = {
                if (apiKey.isBlank()) {
                    status = "Wpisz klucz API-Football."
                } else if (!date.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
                    status = "Nieprawidłowa data. Użyj formatu RRRR-MM-DD."
                } else {
                    loading = true
                    selected = null
                    research = null
                    status = "Pobieram spotkania ze wszystkich dostępnych lig..."
                    thread {
                        try {
                            val result = ApiFootballRepository.fetchFixtures(apiKey, date)
                            Handler(Looper.getMainLooper()).post {
                                fixtures = result
                                status = if (result.isEmpty()) "Brak spotkań w bazie dla tej daty." else "Znaleziono spotkań: " + result.size
                                loading = false
                            }
                        } catch (e: Exception) {
                            Handler(Looper.getMainLooper()).post {
                                status = "Błąd pobierania: " + (e.message ?: "nieznany błąd")
                                loading = false
                            }
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = ApiBlue)
        ) { Text("POBIERZ WSZYSTKIE MECZE") }

        Text(status, color = ApiMuted, fontSize = 11.sp)
        if (loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = ApiBlue)
        leagueCount?.let { Text("Ligi wykryte podczas testu: " + it, color = ApiGreen, fontSize = 11.sp) }

        selected?.let { fixture ->
            Column(
                modifier = Modifier.fillMaxWidth().background(ApiPanel, RoundedCornerShape(12.dp)).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("ANALIZA MECZU", color = ApiBlue, fontWeight = FontWeight.Bold)
                Text(fixture.home + " — " + fixture.away, color = ApiWhite, fontWeight = FontWeight.Bold)
                Text(fixture.leagueName + " • " + fixture.date, color = ApiMuted, fontSize = 11.sp)

                if (research == null) {
                    Button(
                        onClick = {
                            loading = true
                            status = "Pobieram prognozę, absencje i kursy..."
                            thread {
                                try {
                                    val result = FixtureResearch(
                                        ApiFootballRepository.fetchPredictions(apiKey, fixture.id),
                                        ApiFootballRepository.fetchInjuries(apiKey, fixture.id),
                                        ApiFootballRepository.fetchOdds(apiKey, fixture.id)
                                    )
                                    Handler(Looper.getMainLooper()).post {
                                        research = result
                                        status = "Analiza pobrana."
                                        loading = false
                                    }
                                } catch (e: Exception) {
                                    Handler(Looper.getMainLooper()).post {
                                        status = "Błąd analizy: " + (e.message ?: "nieznany błąd")
                                        loading = false
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = ApiBlue)
                    ) { Text("URUCHOM ANALIZĘ 1X2") }
                } else {
                    val data = research!!
                    Text("PROGNOZA 1X2", color = ApiBlue, fontWeight = FontWeight.Bold)
                    val prediction = data.prediction
                    if (prediction == null) {
                        Text("Brak prognozy dla tego spotkania.", color = ApiMuted)
                    } else {
                        Text("1: " + prediction.homeWin.ifBlank { "—" } + "   X: " + prediction.draw.ifBlank { "—" } + "   2: " + prediction.awayWin.ifBlank { "—" }, color = ApiGreen, fontWeight = FontWeight.Bold)
                        if (prediction.winner.isNotBlank()) Text("Wskazany zwycięzca: " + prediction.winner, color = ApiWhite)
                        if (prediction.advice.isNotBlank()) Text(prediction.advice, color = ApiMuted, fontSize = 12.sp)
                        if (prediction.homeForm.isNotBlank() || prediction.awayForm.isNotBlank()) {
                            Text("Porównanie formy: " + prediction.homeForm + " / " + prediction.awayForm, color = ApiMuted, fontSize = 11.sp)
                        }
                    }

                    Text("KURSY BUKMACHERSKIE", color = ApiBlue, fontWeight = FontWeight.Bold)
                    val marketOdds = data.odds.filter {
                        it.market.contains("match winner", true) ||
                        it.market.contains("1x2", true) ||
                        it.market.equals("fulltime result", true)
                    }
                    if (marketOdds.isEmpty()) {
                        Text("Brak kursów 1X2 w bazie dla tego meczu. Pokrycie zależy od ligi i bukmachera.", color = ApiMuted, fontSize = 11.sp)
                    } else {
                        marketOdds.take(18).forEach { odd ->
                            Text(odd.bookmaker + " • " + odd.selection + ": " + odd.odd, color = ApiWhite, fontSize = 11.sp)
                        }
                    }

                    Text("KONTUZJE I ZAWIESZENIA", color = ApiBlue, fontWeight = FontWeight.Bold)
                    if (data.injuries.isEmpty()) {
                        Text("Brak zgłoszonych absencji w bazie; nie oznacza to potwierdzenia pełnego składu.", color = ApiMuted, fontSize = 11.sp)
                    } else {
                        data.injuries.take(12).forEach { injury ->
                            Text(injury.team + ": " + injury.player + if (injury.reason.isNotBlank()) " • " + injury.reason else "", color = ApiWhite, fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        Text("MECZE (" + fixtures.size + ")", color = ApiWhite, fontWeight = FontWeight.Bold)
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            items(fixtures) { fixture ->
                Column(
                    modifier = Modifier.fillMaxWidth().background(ApiPanel, RoundedCornerShape(10.dp))
                        .clickable { selected = fixture; research = null }
                        .padding(11.dp)
                ) {
                    Text(fixture.home + " — " + fixture.away, color = ApiWhite, fontWeight = FontWeight.Bold)
                    Text(fixture.leagueName + " • " + fixture.date, color = ApiMuted, fontSize = 11.sp)
                    Text(if (fixture.score == "Nie rozegrano") fixture.status else fixture.score, color = ApiBlue, fontSize = 11.sp)
                }
            }
        }
    }
}
