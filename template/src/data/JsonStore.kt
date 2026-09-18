package __PKG__.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

/** Small typed key/value store on SharedPreferences, exposing each key as a StateFlow. */
class JsonStore(context: Context, name: String = "store") {
    private val sp = context.getSharedPreferences(name, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val flows = HashMap<String, MutableStateFlow<Any?>>()

    @Suppress("UNCHECKED_CAST")
    fun <T> flow(key: String, serializer: KSerializer<T>, default: T): StateFlow<T> =
        flows.getOrPut(key) {
            val v = sp.getString(key, null)?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() } ?: default
            MutableStateFlow(v as Any?)
        } as StateFlow<T>

    fun <T> set(key: String, serializer: KSerializer<T>, value: T) {
        sp.edit().putString(key, json.encodeToString(serializer, value)).apply()
        flows[key]?.value = value
    }
}
