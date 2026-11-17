
package pl.footballaipro

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.footballDataStore by preferencesDataStore(
    name = "football_ai_pro_data"
)

private val HISTORY_KEY = stringPreferencesKey("match_history")

object FootballStorage {

    suspend fun saveHistory(
        context: Context,
        matches: List<FootballMatch>
    ) {
        val array = JSONArray()

        matches.forEach { match ->
            val item = JSONObject()
            item.put("league", match.league)
            item.put("home", match.home)
            item.put("away", match.away)
            item.put("score", match.score)
            item.put("date", match.date)
            item.put("source", match.source)
            array.put(item)
        }

        context.footballDataStore.edit { preferences ->
            preferences[HISTORY_KEY] = array.toString()
        }
    }

    fun getHistory(context: Context): Flow<List<FootballMatch>> {
        return context.footballDataStore.data.map { preferences ->
            val saved = preferences[HISTORY_KEY] ?: "[]"

            try {
                val array = JSONArray(saved)
                val matches = mutableListOf<FootballMatch>()

                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)

                    matches.add(
                        FootballMatch(
                            league = item.optString("league"),
                            home = item.optString("home"),
                            away = item.optString("away"),
                            score = item.optString("score"),
                            date = item.optString("date"),
                            source = item.optString("source")
                        )
                    )
                }

                matches
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
}
