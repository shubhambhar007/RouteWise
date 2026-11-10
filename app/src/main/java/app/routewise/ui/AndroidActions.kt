package app.routewise.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import app.routewise.BuildConfig
import app.routewise.core.MapsLinks
import app.routewise.core.TripTransfer
import app.routewise.data.Destination
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

object AndroidActions {
    fun navigateInApp(context: Context, destinations: List<Destination>): Boolean {
        if (BuildConfig.GOOGLE_NAVIGATION_API_KEY.isBlank()) {
            Toast.makeText(context, "Add a Google Navigation API key using NEXT_STEPS.md, then rebuild.", Toast.LENGTH_LONG).show()
            return false
        }
        if (destinations.isEmpty() || destinations.size > 25 || destinations.any { it.latitude == null || it.longitude == null }) {
            Toast.makeText(context, "In-app navigation needs 1-25 remaining stops with confirmed coordinates.", Toast.LENGTH_LONG).show()
            return false
        }
        context.startActivity(GoogleNavigationActivity.intent(context, destinations))
        return true
    }

    fun navigate(context: Context, destination: Destination): Boolean {
        val uri = Uri.parse(MapsLinks.navigation(destination))
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).setPackage("com.google.android.apps.maps"))
            return true
        } catch (_: ActivityNotFoundException) {
            return try { context.startActivity(Intent(Intent.ACTION_VIEW, uri)); true }
            catch (_: ActivityNotFoundException) { false }
        }
    }

    suspend fun share(context: Context, title: String, json: String) {
        val uri = withContext(Dispatchers.IO) {
            val directory = File(context.cacheDir, "shared").apply { mkdirs() }
            directory.listFiles()?.filter { it.lastModified() < System.currentTimeMillis() - 86_400_000 }?.forEach { it.delete() }
            val file = File(directory, "routewise-${UUID.randomUUID()}.json").apply { writeText(json) }
            FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        }
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, "Share trip"))
    }

    suspend fun readTrip(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        requireNotNull(context.contentResolver.openInputStream(uri)) { "Unable to open trip file." }.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var read = input.read(buffer)
            while (read != -1) {
                require(output.size() + read <= TripTransfer.MAX_BYTES) { "Trip file exceeds 8 MB." }
                output.write(buffer, 0, read)
                read = input.read(buffer)
            }
            output.toString("UTF-8")
        }
    }
}
