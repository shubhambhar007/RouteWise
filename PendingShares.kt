package app.routewise.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class PendingShare(val id: String, val text: String)

@Singleton
class PendingShares @Inject constructor(@ApplicationContext context: Context) {
    private val preferences = context.getSharedPreferences("pending-shares", Context.MODE_PRIVATE)
    private val mutableItems = MutableStateFlow(runCatching {
        Json.decodeFromString<List<PendingShare>>(preferences.getString("items", "[]")!!)
    }.getOrDefault(emptyList()))
    val items = mutableItems.asStateFlow()

    @Synchronized fun add(text: String): PendingShare {
        require(text.isNotBlank() && text.length <= 16_384) { "Shared text is empty or too large." }
        val item = PendingShare(MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }, text)
        if (items.value.none { it.id == item.id }) {
            require(items.value.size < 100) { "Pending inbox is full. Resolve or discard an item first." }
            write(items.value + item)
        }
        return item
    }

    @Synchronized fun remove(id: String) = write(items.value.filterNot { it.id == id })

    private fun write(next: List<PendingShare>) {
        check(preferences.edit().putString("items", Json.encodeToString(next)).commit()) { "Unable to save pending share." }
        mutableItems.value = next
    }
}
