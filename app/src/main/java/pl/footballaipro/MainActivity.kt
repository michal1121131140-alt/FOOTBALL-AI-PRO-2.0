package pl.footballaipro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import kotlin.math.exp
import kotlin.math.pow

private val Navy = Color(0xFF081426)
private val CardBlue = Color(0xFF122641)
private val AccentBlue = Color(0xFF3198FF)
private val LightText = Color(0xFFEAF3FF)
private val MutedText = Color(0xFF91A8C4)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { FootballApp() }
    }
}

@Composable
fun FootballApp() {
    var selected by remember { mutableStateOf("Panel") }
    var home by remember { mutableStateOf("1.50") }
    var away by remember { mutableStateOf("1.10") }

    MaterialTheme {
        Scaffold(
            containerColor = Navy,
            topBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Navy)
                        .padding(20.dp)
                ) {
                    Text("FOOTBALL", color = AccentBlue, fontSize = 13.sp,
                        fontWeight = FontWeight.Bold)
                    Text("AI PRO 2.0", color = LightText, fontSize = 27.sp,
                        fontWeight = FontWeight.ExtraBold)
                    Text("FOOTBALL INTELLIGENCE SYSTEM",
                        color = MutedText, fontSize = 10.sp)
                }
            },
            bottomBar = {
                NavigationBar(containerColor = CardBlue) {
                    listOf("Panel", "Analiza", "Value").forEach { item ->
                        NavigationBarItem(
                            selected = selected == item,
                            onClick = { selected = item },
                            label = { Text(item) },
                            icon = {},
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = AccentBlue,
                                selectedTextColor = AccentBlue,
                                indicatorColor = Color(0xFF203D60),
                                unselectedTextColor = MutedText
                            )
                        )
                    }
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .background(Navy)
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                when (selected) {
                    "Panel" -> {
                        Text("CENTRUM ANALIZ", color = LightText,
                            fontSize = 21.sp, fontWeight = FontWeight.Bold)
                        Text("Witaj w swoim centrum piłkarskim.",
                            color = MutedText)

                        InfoCard("SYSTEM STATUS", "SYSTEM GOTOWY", "●")
                        InfoCard("MODEL ANALITYCZNY",
                            "Matematyczny model Poissona", "AI")
                        InfoCard("TRYB PRACY",
                            "Analiza prawdopodobieństwa meczów", "PRO")

                        Text("MODUŁY SYSTEMU", color = AccentBlue,
                            fontWeight = FontWeight.Bold)
                        InfoCard("MATCH ANALYSIS",
                            "Analiza wyników i prawdopodobieństw", "01")
                        InfoCard("VALUE BETTING",
                            "Ocena wartości kursów", "02")
                    }

                    "Analiza" -> {
                        Text("ANALIZA MECZU", color = LightText,
                            fontSize = 21.sp, fontWeight = FontWeight.Bold)
                        Text("Wprowadź oczekiwaną liczbę goli.",
                            color = MutedText)

                        OutlinedTextField(
                            value = home,
                            onValueChange = { home = it },
                            label = { Text("Gole gospodarzy") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = fieldColors()
                        )
                        OutlinedTextField(
                            value = away,
                            onValueChange = { away = it },
                            label = { Text("Gole gości") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = fieldColors()
                        )

                        val h = home.replace(",", ".").toDoubleOrNull()
                        val a = away.replace(",", ".").toDoubleOrNull()

                        if (h != null && a != null && h in 0.0..10.0 && a in 0.0..10.0) {
                            val result = calculate(h, a)
                            InfoCard("1  GOSPODARZE",
                                "${(result[0] * 100).toInt()}%", "1")
                            InfoCard("X  REMIS",
                                "${(result[1] * 100).toInt()}%", "X")
                            InfoCard("2  GOŚCIE",
                                "${(result[2] * 100).toInt()}%", "2")
                            InfoCard("BTTS  OBIE STRZELĄ",
                                "${(result[3] * 100).toInt()}%", "⚽")
                            InfoCard("OVER 2.5",
                                "${(result[4] * 100).toInt()}%", "GOLE")
                        } else {
                            Text("Wpisz wartości od 0 do 10.",
                                color = MutedText)
                        }
                    }

                    "Value" -> {
                        Text("VALUE BETTING", color = LightText,
                            fontSize = 21.sp, fontWeight = FontWeight.Bold)
                        Text("Kalkulator wartości kursu.",
                            color = MutedText)
                        InfoCard("WZÓR",
                            "Value = prawdopodobieństwo × kurs − 1", "PRO")
                        InfoCard("INTERPRETACJA",
                            "Dodatnia wartość może wskazywać korzystny kurs.", "INFO")
                        Text("Pamiętaj: model nie gwarantuje trafnych prognoz.",
                            color = MutedText, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = LightText,
    unfocusedTextColor = LightText,
    focusedBorderColor = AccentBlue,
    unfocusedBorderColor = MutedText,
    focusedLabelColor = AccentBlue,
    unfocusedLabelColor = MutedText,
    cursorColor = AccentBlue
)

@Composable
fun InfoCard(title: String, value: String, tag: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardBlue)
    ) {
        Row(
            modifier = Modifier.padding(18.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = MutedText, fontSize = 11.sp,
                    fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(7.dp))
                Text(value, color = LightText, fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold)
            }
            Text(tag, color = AccentBlue, fontWeight = FontWeight.Bold)
        }
    }
}

fun poisson(lambda: Double, k: Int): Double {
    return exp(-lambda) * lambda.pow(k) / factorial(k)
}

fun factorial(n: Int): Double {
    var result = 1.0
    for (i in 1..n) result *= i
    return result
}

fun calculate(home: Double, away: Double): List<Double> {
    var win = 0.0
    var draw = 0.0
    var lose = 0.0
    var btts = 0.0
    var over = 0.0

    for (h in 0..10) {
        for (a in 0..10) {
            val p = poisson(home, h) * poisson(away, a)
            if (h > a) win += p
            if (h == a) draw += p
            if (h < a) lose += p
            if (h > 0 && a > 0) btts += p
            if (h + a > 2) over += p
        }
    }
    return listOf(win, draw, lose, btts, over)
}
