package com.rssolanki.hisabkitab

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.print.PrintAttributes
import android.print.PrintManager
import android.provider.ContactsContract
import android.provider.MediaStore
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.webkit.WebViewAssetLoader
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * HisabKitab Android app — Developer: RS Solanki
 *
 * Same UI as the web app (assets/index.html is the exact same Index.html),
 * same Google Sheet backend: every action is sent to the Apps Script Web App (doPost),
 * so web users and app users share one SaaS database.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var web: WebView
    private val io = Executors.newFixedThreadPool(4)
    private val ui = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences("hisabkitab", Context.MODE_PRIVATE) }

    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var contactsRequestId = ""
    private var printWebView: WebView? = null

    private val fileChooserLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val cb = fileCallback
            fileCallback = null
            cb?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data))
        }

    private val pickContactLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            onContactPicked(result)
        }

    private val contactsPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) loadAllContacts(contactsRequestId) else sendContacts(contactsRequestId, "[]", "denied")
        }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        web = WebView(this)
        web.setBackgroundColor(Color.parseColor("#062B27"))
        setContentView(web)

        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        with(web.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            setSupportMultipleWindows(false)
            javaScriptCanOpenWindowsAutomatically = false
            mediaPlaybackRequiresUserGesture = true
            textZoom = 100
        }
        web.addJavascriptInterface(Bridge(), "HKNative")

        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                assetLoader.shouldInterceptRequest(request.url)

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url
                if (url.host == APP_HOST) return false
                openExternal(url)          // WhatsApp, phone dialer, other websites
                return true
            }
        }

        web.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView,
                filePathCallback: ValueCallback<Array<Uri>>,
                fileChooserParams: FileChooserParams
            ): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = filePathCallback
                return try {
                    fileChooserLauncher.launch(fileChooserParams.createIntent())
                    true
                } catch (e: ActivityNotFoundException) {
                    fileCallback = null
                    false
                }
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                web.evaluateJavascript("(window.__hkBack && window.__hkBack()) ? 'yes' : 'no'") { r ->
                    if (r == null || !r.contains("yes")) finish()
                }
            }
        })

        web.loadUrl("https://$APP_HOST/assets/index.html")
    }

    override fun onDestroy() {
        io.shutdownNow()
        printWebView?.destroy()
        web.destroy()
        super.onDestroy()
    }

    /* ------------------------------------------------------------------ */
    /*  JavaScript bridge — index.html calls these as window.HKNative.*   */
    /* ------------------------------------------------------------------ */
    inner class Bridge {

        @JavascriptInterface
        fun getServer(): String = serverUrl()

        @JavascriptInterface
        fun setServer(url: String) {
            prefs.edit().putString(KEY_SERVER, url.trim()).apply()
        }

        @JavascriptInterface
        fun appVersion(): String = appVersionName()

        @JavascriptInterface
        fun api(id: String, action: String, payload: String, token: String) {
            val server = serverUrl()
            io.execute {
                val result: String? = try {
                    callServer(server, action, payload, token)
                } catch (e: Exception) {
                    null
                }
                val arg = if (result == null) "null" else JSONObject.quote(result)
                runJs("window.__hkNativeCb && window.__hkNativeCb(" + JSONObject.quote(id) + "," + arg + ")")
            }
        }

        @JavascriptInterface
        fun pickContact(id: String) {
            ui.post {
                contactsRequestId = id
                try {
                    pickContactLauncher.launch(
                        Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
                    )
                } catch (e: Exception) {
                    sendContacts(id, "[]", "cancel")
                }
            }
        }

        @JavascriptInterface
        fun allContacts(id: String) {
            ui.post {
                contactsRequestId = id
                val granted = ContextCompat.checkSelfPermission(
                    this@MainActivity, Manifest.permission.READ_CONTACTS
                ) == PackageManager.PERMISSION_GRANTED
                if (granted) loadAllContacts(id)
                else contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
            }
        }

        @JavascriptInterface
        fun saveFile(name: String, content: String, mime: String) {
            io.execute { saveFileImpl(name, content, mime) }
        }

        @JavascriptInterface
        fun printHtml(html: String, title: String) {
            ui.post { printImpl(html, title) }
        }

        @JavascriptInterface
        fun share(text: String) {
            ui.post {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                }
                try {
                    startActivity(Intent.createChooser(send, getString(R.string.share_title)))
                } catch (e: Exception) {
                    // nothing to share with
                }
            }
        }

        @JavascriptInterface
        fun toast(msg: String) {
            ui.post { Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show() }
        }
    }

    /* ---------------------------- Server API ---------------------------- */

    private fun serverUrl(): String {
        val saved = prefs.getString(KEY_SERVER, null)
        if (!saved.isNullOrBlank()) return saved
        return getString(R.string.server_url).trim()
    }

    /** POSTs {action, payload, token} to the Apps Script Web App and returns its JSON reply. */
    private fun callServer(server: String, action: String, payload: String, token: String): String {
        if (!server.startsWith("https://")) return ERR_SERVER
        val body = JSONObject()
            .put("action", action)
            .put("payload", if (payload.isBlank()) JSONObject() else JSONObject(payload))
            .put("token", token)
            .toString()
            .toByteArray(Charsets.UTF_8)

        var conn = URL(server).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.instanceFollowRedirects = false
        conn.connectTimeout = 20_000
        conn.readTimeout = 60_000
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "text/plain; charset=utf-8")
        conn.setFixedLengthStreamingMode(body.size)
        conn.outputStream.use { it.write(body) }

        // Apps Script answers a POST with a redirect to googleusercontent.com — follow it with GET.
        var code = conn.responseCode
        var hops = 0
        while (code in 300..399 && hops < 5) {
            val location = conn.getHeaderField("Location") ?: break
            val next = URL(conn.url, location)
            conn.disconnect()
            conn = next.openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 20_000
            conn.readTimeout = 60_000
            code = conn.responseCode
            hops++
        }

        if (code in 400..499) {
            conn.disconnect()
            return ERR_SERVER
        }
        if (code !in 200..299) {
            conn.disconnect()
            throw IOException("HTTP $code")
        }
        val text = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        conn.disconnect()
        // A Google sign-in / error page (HTML) means wrong URL or access is not "Anyone".
        return if (text.trimStart().startsWith("{")) text else ERR_SERVER
    }

    /* ----------------------------- Contacts ----------------------------- */

    private fun onContactPicked(result: ActivityResult) {
        val id = contactsRequestId
        val uri = result.data?.data
        if (result.resultCode != RESULT_OK || uri == null) {
            sendContacts(id, "[]", "cancel")
            return
        }
        io.execute {
            val arr = JSONArray()
            try {
                contentResolver.query(
                    uri,
                    arrayOf(
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                        ContactsContract.CommonDataKinds.Phone.NUMBER
                    ),
                    null, null, null
                )?.use { c ->
                    if (c.moveToFirst()) {
                        arr.put(JSONObject().put("name", c.getString(0) ?: "").put("phone", c.getString(1) ?: ""))
                    }
                }
            } catch (e: Exception) {
                // ignore — empty list
            }
            sendContacts(id, arr.toString(), "")
        }
    }

    private fun loadAllContacts(id: String) {
        io.execute {
            val arr = JSONArray()
            val seen = HashSet<String>()
            try {
                contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                        ContactsContract.CommonDataKinds.Phone.NUMBER
                    ),
                    null, null,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
                )?.use { c ->
                    while (c.moveToNext()) {
                        val name = c.getString(0) ?: ""
                        val number = c.getString(1) ?: ""
                        val key = name.lowercase() + "|" + number.filter { it.isDigit() }.takeLast(10)
                        if (seen.add(key)) arr.put(JSONObject().put("name", name).put("phone", number))
                    }
                }
            } catch (e: Exception) {
                // ignore — whatever was read is returned
            }
            sendContacts(id, arr.toString(), "")
        }
    }

    private fun sendContacts(id: String, json: String, err: String) {
        runJs(
            "window.__hkContacts && window.__hkContacts(" +
                JSONObject.quote(id) + "," + JSONObject.quote(json) + "," + JSONObject.quote(err) + ")"
        )
    }

    /* ------------------------- Files & printing ------------------------- */

    private fun saveFileImpl(name: String, content: String, mime: String) {
        val safeName = name.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val bytes = content.toByteArray(Charsets.UTF_8)
        try {
            val uri: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, safeName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mime)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/HisabKitab")
                }
                val u = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: throw IOException("insert failed")
                val out = contentResolver.openOutputStream(u) ?: throw IOException("open failed")
                out.use { it.write(bytes) }
                ui.post {
                    Toast.makeText(this, getString(R.string.saved_to, safeName), Toast.LENGTH_LONG).show()
                }
                u
            } else {
                val dir = File(cacheDir, "exports").apply { mkdirs() }
                val f = File(dir, safeName)
                f.writeBytes(bytes)
                FileProvider.getUriForFile(this, "$packageName.files", f)
            }
            ui.post { shareFile(uri, mime) }
        } catch (e: Exception) {
            ui.post { Toast.makeText(this, R.string.save_failed, Toast.LENGTH_SHORT).show() }
        }
    }

    private fun shareFile(uri: Uri, mime: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(Intent.createChooser(send, getString(R.string.share_title)))
        } catch (e: Exception) {
            // no app to share with — file is still saved in Downloads
        }
    }

    /** Statement / report → Android print dialog (Save as PDF or a printer). */
    private fun printImpl(html: String, title: String) {
        printWebView?.destroy()
        val pw = WebView(this)
        var started = false
        pw.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                if (started) return
                started = true
                val pm = getSystemService(Context.PRINT_SERVICE) as PrintManager
                val jobName = "HisabKitab - $title"
                pm.print(jobName, view.createPrintDocumentAdapter(jobName), PrintAttributes.Builder().build())
            }
        }
        printWebView = pw   // keep a reference until printing is done
        pw.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
    }

    /* ------------------------------ Helpers ----------------------------- */

    private fun runJs(js: String) {
        ui.post { if (!isFinishing && !isDestroyed) web.evaluateJavascript(js, null) }
    }

    private fun openExternal(uri: Uri) {
        try {
            val intent = if (uri.scheme == "intent") {
                Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME)
            } else {
                Intent(Intent.ACTION_VIEW, uri)
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, R.string.no_app, Toast.LENGTH_SHORT).show()
        }
    }

    @Suppress("DEPRECATION")
    private fun appVersionName(): String = try {
        packageManager.getPackageInfo(packageName, 0).versionName ?: ""
    } catch (e: Exception) {
        ""
    }

    companion object {
        private const val APP_HOST = WebViewAssetLoader.DEFAULT_DOMAIN   // appassets.androidplatform.net
        private const val KEY_SERVER = "server_url"
        private const val ERR_SERVER = "{\"ok\":false,\"error\":\"E_SERVER\"}"
    }
}
