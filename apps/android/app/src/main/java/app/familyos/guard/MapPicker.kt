package app.familyos.guard

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/** Простая карта на OpenStreetMap: тап по карте выбирает точку. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MapPickerDialog(
    initialLat: Double,
    initialLng: Double,
    onDismiss: () -> Unit,
    onPick: (Double, Double) -> Unit,
) {
    var picked by remember { mutableStateOf<Pair<Double, Double>?>(null) }

    val html = """
        <!DOCTYPE html>
        <html>
        <head>
          <meta name="viewport" content="width=device-width, initial-scale=1.0">
          <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"/>
          <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
          <style>html,body,#map{height:100%;margin:0;background:#0B0F14}</style>
        </head>
        <body>
          <div id="map"></div>
          <script>
            var map = L.map('map').setView([$initialLat, $initialLng], 16);
            L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
              maxZoom: 19, attribution: 'OSM'
            }).addTo(map);
            var marker = L.marker([$initialLat, $initialLng], {draggable:true}).addTo(map);
            function report(lat, lng) {
              if (window.AndroidMap && window.AndroidMap.onPick) {
                window.AndroidMap.onPick(lat, lng);
              }
            }
            report($initialLat, $initialLng);
            map.on('click', function(e) {
              marker.setLatLng(e.latlng);
              report(e.latlng.lat, e.latlng.lng);
            });
            marker.on('dragend', function() {
              var p = marker.getLatLng();
              report(p.lat, p.lng);
            });
          </script>
        </body>
        </html>
    """.trimIndent()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Место на карте") },
        text = {
            Column {
                AndroidView(
                    modifier = Modifier.fillMaxWidth().height(320.dp),
                    factory = { context ->
                        WebView(context).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            webViewClient = WebViewClient()
                            addJavascriptInterface(
                                object {
                                    @JavascriptInterface
                                    fun onPick(lat: Double, lng: Double) {
                                        picked = lat to lng
                                    }
                                },
                                "AndroidMap",
                            )
                            loadDataWithBaseURL("https://localhost", html, "text/html", "utf-8", null)
                        }
                    },
                )
                picked?.let {
                    Text(
                        "Выбрано: %.5f, %.5f".format(it.first, it.second),
                        modifier = Modifier.padding(top = 8.dp),
                    )
                } ?: Text("Нажми на карту, чтобы отметить место", Modifier.padding(top = 8.dp))
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    picked?.let { onPick(it.first, it.second) }
                    onDismiss()
                },
                enabled = picked != null,
            ) { Text("Выбрать") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
