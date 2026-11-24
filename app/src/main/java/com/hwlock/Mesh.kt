package com.hwlock

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

/**
 * Интеграция с МЭШ. ВНИМАНИЕ: публичного API нет — используется веб-API семейного кабинета
 * (school.mos.ru/api/family/web/v1/homeworks). Формат может измениться; при любой ошибке
 * приложение продолжает работать с ручными заданиями.
 */
object Mesh {
    const val LOGIN_URL = "https://school.mos.ru/"

    fun tokenFromCookies(): String? {
        val cm = CookieManager.getInstance()
        for (u in listOf("https://school.mos.ru", "https://dnevnik.mos.ru", "https://mos.ru")) {
            cm.getCookie(u)?.split(";")?.map { it.trim() }
                ?.firstOrNull { it.startsWith("aupd_token=") }?.let { return it.removePrefix("aupd_token=") }
        }
        return null
    }

    suspend fun sync(token: String): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val today = LocalDate.now()
            val url = "https://school.mos.ru/api/family/web/v1/homeworks?from=$today&to=${today.plusDays(1)}"
            val c = URL(url).openConnection() as HttpURLConnection
            c.setRequestProperty("Authorization", "Bearer $token")
            c.setRequestProperty("Cookie", "aupd_token=$token")
            c.setRequestProperty("X-Mes-Subsystem", "familyweb")
            c.setRequestProperty("Accept", "application/json")
            c.connectTimeout = 15000; c.readTimeout = 15000
            if (c.responseCode != 200) error("HTTP ${c.responseCode}")
            val body = c.inputStream.bufferedReader().readText()
            val root = JSONObject(body)
            val arr: JSONArray = root.optJSONArray("payload") ?: root.optJSONArray("items") ?: JSONArray()
            val dao = Graph.db.tasks()
            var added = 0
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val ext = o.optString("homework_id", o.optString("id", "")) + "|" + o.optString("date", "")
                if (dao.countExt(ext) > 0) continue
                val subject = o.optString("subject_name", o.optString("subject", "Предмет"))
                val entries = o.optJSONArray("homework_entries")
                val text = o.optString("description", o.optString("homework", "")).ifBlank {
                    (0 until (entries?.length() ?: 0)).joinToString("\n") { entries!!.getJSONObject(it).optString("description") }
                }
                val links = Regex("""https?:[^"\s\\]+""")
                    .findAll(o.toString().replace("\\/", "/")).map { it.value }.distinct().joinToString("\n")
                dao.upsert(TaskEntity(subject = subject, description = text, links = links,
                    date = today.toString(), externalId = ext))
                added++
            }
            added
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MeshScreen(onDone: (token: String?) -> Unit) {
    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        AndroidView(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            factory = { c ->
                WebView(c).apply {
                    layoutParams = ViewGroup.LayoutParams(-1, -1)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    CookieManager.getInstance().setAcceptCookie(true)
                    webViewClient = WebViewClient()
                    loadUrl(Mesh.LOGIN_URL)
                }
            })
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { onDone(null) }, Modifier.weight(1f)) { Text("Отмена") }
            Button(onClick = { onDone(Mesh.tokenFromCookies()) }, Modifier.weight(1f)) { Text("Я вошёл") }
        }
    }
}
