package pl.footballaipro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import kotlin.math.exp
import kotlin.math.pow

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FootballApp()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FootballApp() {
    var home by remember { mutableStateOf("1.50") }
    var away by remember { mutableStateOf("1.10") }
    var selected by remember { mutableStateOf("Panel") }

    MaterialTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            "FOOTBALL AI PRO 2.0",
                            fontWeight = FontWeight.Bold
                        )
                    }
                )
            },
            bottomBar = {
                NavigationBar {
                    listOf("Panel", "Analiza", "Value").forEach {
                        NavigationBarItem(
                            selected = selected == it,
                            onClick = { selected = it },
                            label = { Text(it) },
                            icon = {}
                        )
                    }
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    "Witaj w FOOTBALL AI PRO 2.0",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                when (selected) {
                    "Panel" -> {
                        InfoCard("SYSTEM", "Analiza piłkarska i prognozy")
                        InfoCard("MODEL", "Matematyczny model Poissona")
                        InfoCard("STATUS", "Aplikacja gotowa do analizy")
                    }

                    "Analiza" -> {
                        Text("ANALIZA MECZU", fontWeight = FontWeight.Bold)

                        OutlinedTextField(
                            value = home,
                            onValueChange = { home = it },
                            label = { Text("Oczekiwane gole gospodarzy") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = away,
                            onValueChange = { away = it },
                            label = { Text("Oczekiwane gole gości") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        val h = home.toDoubleOrNull()
                        val a = away.toDoubleOrNull()

                        if (h != null && a != null && h in 0.0..10.0 && a in 0.0..10.0) {
                            val result = calculate(h, a)

                            InfoCard("1 – Gospodarze", "${(result[0] * 100).toInt()}%")
                            InfoCard("X – Remis", "${(result[1] * 100).toInt()}%")
                            InfoCard("2 – Goście", "${(result[2] * 100).toInt()}%")
                            InfoCard("BTTS – Obie strzelą", "${(result[3] * 100).toInt()}%")
                            InfoCard("Over 2.5", "${(result[4] * 100).toInt()}%")
                        } else {
                            Text("Wpisz wartości od 0 do 10.")
                        }
                    }

                    "Value" -> {
                        Text("KALKULATOR VALUE BETTING", fontWeight = FontWeight.Bold)
                        Text("Porównuj własną ocenę prawdopodobieństwa z kursem bukmachera.")
                        InfoCard("WZÓR", "Value = prawdopodobieństwo × kurs − 1")
                        InfoCard("UWAGA", "Dodatnia wartość nie gwarantuje wygranej.")
                    }
                }
            }
        }
    }
}

@Composable
fun InfoCard(title: String, value: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))
            Text(value)
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
            val probability = poisson(home, h) * poisson(away, a)

            if (h > a) win += probability
            if (h == a) draw += probability
            if (h < a) lose += probability
            if (h > 0 && a > 0) btts += probability
            if (h + a > 2) over += probability
        }
    }

    return listOf(win, draw, lose, btts, over)
}
