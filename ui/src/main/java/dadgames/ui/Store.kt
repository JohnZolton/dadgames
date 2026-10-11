package dadgames.ui

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

val storeJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

/**
 * JSON values in SharedPreferences. Saves run on one background thread so they land in order and
 * never block the UI. [BEST_PRACTICES #18, #20]
 */
class Store(context: Context, name: String) {
    private val prefs: SharedPreferences = context.getSharedPreferences(name, Context.MODE_PRIVATE)
    private val writer = Dispatchers.Default.limitedParallelism(1)

    fun <T> load(key: String, serializer: KSerializer<T>): T? =
        runCatching { prefs.getString(key, null)?.let { storeJson.decodeFromString(serializer, it) } }.getOrNull()

    /** Save in the background. Pass an immutable snapshot. */
    fun <T> save(scope: CoroutineScope, key: String, serializer: KSerializer<T>, value: T) {
        scope.launch(writer) { prefs.edit().putString(key, storeJson.encodeToString(serializer, value)).apply() }
    }

    /** Save right now on the calling thread (small values, or app shutdown). */
    fun <T> saveNow(key: String, serializer: KSerializer<T>, value: T) {
        prefs.edit().putString(key, storeJson.encodeToString(serializer, value)).apply()
    }

    fun remove(key: String) = prefs.edit().remove(key).apply()

    fun getBoolean(key: String, default: Boolean) = prefs.getBoolean(key, default)
    fun putBoolean(key: String, value: Boolean) = prefs.edit().putBoolean(key, value).apply()
}
