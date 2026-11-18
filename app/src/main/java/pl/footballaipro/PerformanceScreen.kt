package pl.footballaipro

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun PerformanceScreen(seasonMatches: List<FootballMatch> = emptyList()) {
    val context = LocalContext.current
    val records = remember {
        mutableStateOf(PredictionPerformance.getAll(context))
    }
    val stats = remember(records.value) {
        PredictionPerformance.getStats(context)
    }
    var backtest by remember {
        mutableStateOf(BacktestSummary(0, 0, 0, 0.0, 0.0))
    }
    var backtestLoading by remember { mutableStateOf(true) }
    LaunchedEffect(seasonMatches) {
        backtestLoading = true
        try {
            backtest = withContext(Dispatchers.Default) {
                PredictionBacktest.run(seasonMatches)
            }
        } finally {
            backtestLoading = false
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp)
    ) {
        Text(
            text = "SKUTECZNOŚĆ PRO",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = { records.value = PredictionPerformance.getAll(context) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("ODŚWIEŻ STATYSTYKI")
        }
        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text("TEST HISTORYCZNY MODELU", fontWeight = FontWeight.Bold)
                when {
                    seasonMatches.isEmpty() -> {
                        Text("Załaduj mecze ligi i sezonu w zakładce MECZE, aby uruchomić test.")
                    }
                    backtestLoading -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            CircularProgressIndicator()
                            Text("Obliczanie testu historycznego w tle...")
                        }
                    }
                    backtest.testedMatches == 0 -> {
                        Text("Za mało wcześniejszych wyników, aby rzetelnie przetestować prognozy.")
                        Text("Pominięte mecze: ${backtest.skippedMatches}")
                    }
                    else -> {
                        Text("Sprawdzone mecze: ${backtest.testedMatches}")
                        Text("Pominięte mecze: ${backtest.skippedMatches}")
                        Text(
                            "Trafione wskazania 1X2: ${backtest.correctPredictions} / ${backtest.testedMatches}"
                        )
                        Text(
                            "Skuteczność 1X2: ${format(backtest.accuracy)}%",
                            fontWeight = FontWeight.Bold
                        )
                        Text("Średni Brier Score: ${format(backtest.averageBrierScore)}")
                    }
                }
                Text(
                    "Test wykorzystuje wyłącznie wcześniejsze wyniki z aktualnie załadowanej ligi i sezonu.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatCard("Wszystkie typy", stats.total.toString(), Modifier.weight(1f))
            StatCard("Oczekujące", stats.open.toString(), Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatCard("Wygrane", stats.wins.toString(), Modifier.weight(1f))
            StatCard("Przegrane", stats.losses.toString(), Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatCard("Skuteczność", format(stats.winRate) + "%", Modifier.weight(1f))
            StatCard("ROI", format(stats.roi) + "%", Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Bilans finansowy", fontWeight = FontWeight.Bold)
                Text("Stawki rozliczone: ${format(stats.totalStake)}")
                Text(
                    text = "Zysk / strata: ${format(stats.totalProfit)}",
                    fontWeight = FontWeight.Bold
                )
                Text("Rozliczone typy: ${stats.settled}")
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text("HISTORIA TYPÓW", style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))

        if (records.value.isEmpty()) {
            Text("Nie zapisano jeszcze żadnych typów.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(
                    items = records.value.sortedByDescending { it.createdAt },
                    key = { it.id }
                ) { record ->
                    PredictionRecordCard(record)
                }
            }
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.bodySmall)
            Text(value, style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PredictionRecordCard(record: PredictionRecord) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text("${record.home} - ${record.away}", fontWeight = FontWeight.Bold)
            Text("Liga: ${record.league}")
            Text("Rynek: ${record.market}")
            Text("Typ: ${record.selection}")
            Text("Kurs: ${format(record.odds)}")
            Text("Stawka: ${format(record.stake)}")
            Text("Prawdopodobieństwo modelu: ${format(record.probability)}%")
            Spacer(modifier = Modifier.height(4.dp))
            val status = when {
                !record.settled -> "OCZEKUJE NA WYNIK"
                record.won == true -> "WYGRANY"
                else -> "PRZEGRANY"
            }
            Text(
                text = status,
                fontWeight = FontWeight.Bold,
                color = when {
                    !record.settled -> MaterialTheme.colorScheme.primary
                    record.won == true -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.error
                }
            )
            if (record.settled) {
                Text("Wynik: ${record.actualScore}")
                Text("Bilans: ${format(record.profit)}")
            }
        }
    }
}

private fun format(value: Double): String =
    String.format(Locale("pl", "PL"), "%.2f", value)
