package com.technoral.teklif

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Message
import android.provider.MediaStore
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.technoral.teklif.databinding.ActivityMainBinding
import java.io.File

/**
 * Teklif sitesini uygulama icinde acan ana ekran. Oturum acma, dosya yukleme,
 * PDF indirme ve geri tusu gibi islemler burada tarayici yerine uygulamaya baglanir.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var blobBridge: BlobDownloadBridge

    private var siteUrl: String = ""
    private var siteHost: String? = null
    private var hasLoadError = false

    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var cameraOutputUri: Uri? = null
    private var pendingCameraRequest: PermissionRequest? = null
    private var pendingDownload: (() -> Unit)? = null

    private val fileChooserLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val callback = filePathCallback ?: return@registerForActivityResult
            filePathCallback = null

            val data = result.data
            val uris = when {
                result.resultCode != RESULT_OK -> null
                data?.data != null || data?.clipData != null ->
                    WebChromeClient.FileChooserParams.parseResult(result.resultCode, data)
                // Kamera ile cekilen fotograf Intent yerine onceden verdigimiz adrese yazilir.
                cameraOutputUri != null -> arrayOf(cameraOutputUri!!)
                else -> null
            }
            cameraOutputUri = null
            callback.onReceiveValue(uris)
        }

    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val request = pendingCameraRequest ?: return@registerForActivityResult
            pendingCameraRequest = null
            if (granted) request.grant(request.resources) else request.deny()
        }

    private val storagePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val action = pendingDownload
            pendingDownload = null
            if (granted) action?.invoke()
            else Toast.makeText(this, R.string.download_permission_needed, Toast.LENGTH_LONG).show()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        if (!Prefs.hasSiteUrl(this)) {
            openSetup()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        blobBridge = BlobDownloadBridge(this)
        siteUrl = Prefs.siteUrl(this)
        siteHost = Uri.parse(siteUrl).host

        configureWebView()
        registerBackHandler()

        if (savedInstanceState != null) {
            binding.webView.restoreState(savedInstanceState)
        } else {
            binding.webView.loadUrl(siteUrl)
        }
    }

    override fun onResume() {
        super.onResume()
        // Ayarlardan adres degistirilmisse yeni siteye gec.
        if (::binding.isInitialized && Prefs.siteUrl(this) != siteUrl) {
            siteUrl = Prefs.siteUrl(this)
            siteHost = Uri.parse(siteUrl).host
            binding.webView.clearHistory()
            binding.webView.loadUrl(siteUrl)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (::binding.isInitialized) binding.webView.saveState(outState)
    }

    override fun onDestroy() {
        if (::binding.isInitialized) binding.webView.destroy()
        super.onDestroy()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() = with(binding.webView) {
        settings.apply {
            javaScriptEnabled = true
            // JWT token'i localStorage'da tutuldugu icin sart.
            domStorageEnabled = true
            databaseEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = false
            displayZoomControls = false
            mediaPlaybackRequiresUserGesture = false
            setSupportMultipleWindows(true)
            javaScriptCanOpenWindowsAutomatically = true
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            cacheMode = WebSettings.LOAD_DEFAULT
            userAgentString = "$userAgentString TeklifApp/${BuildConfig.VERSION_NAME}"
        }

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(this@with, true)
        }

        addJavascriptInterface(blobBridge, BlobDownloadBridge.NAME)
        webViewClient = TeklifWebViewClient()
        webChromeClient = TeklifChromeClient()

        setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            startDownload(url, userAgent, contentDisposition, mimeType)
        }

        setOnScrollChangeListener { _, _, scrollY, _, _ ->
            // Sayfa asagi kaydirilmisken asagi cekince yenileme tetiklenmesin.
            binding.swipeRefresh.isEnabled = scrollY == 0
        }

        if (BuildConfig.DEBUG) WebView.setWebContentsDebuggingEnabled(true)

        binding.swipeRefresh.setOnRefreshListener { reload() }
        binding.retryButton.setOnClickListener {
            hideError()
            if (url.isNullOrBlank()) loadUrl(siteUrl) else reload()
        }
        binding.changeUrlButton.setOnClickListener { openSetup(finishCurrent = false) }
    }

    private fun registerBackHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.webView.canGoBack()) {
                    hideError()
                    binding.webView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    private fun openSetup(finishCurrent: Boolean = true) {
        startActivity(Intent(this, SetupActivity::class.java))
        if (finishCurrent) finish()
    }

    // ---------------------------------------------------------------- indirme

    private fun startDownload(
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?,
    ) {
        when {
            url.startsWith("blob:") -> downloadBlob(url, mimeType)
            url.startsWith("data:") ->
                blobBridge.onBlobData(url, mimeType.orEmpty(), "")
            else -> withStoragePermission {
                Downloads.viaDownloadManager(this, url, userAgent, contentDisposition, mimeType)
            }
        }
    }

    private fun downloadBlob(url: String, mimeType: String?) {
        Toast.makeText(this, R.string.download_in_progress, Toast.LENGTH_SHORT).show()
        binding.webView.evaluateJavascript(
            BlobDownloadBridge.fetchScript(url, mimeType, null),
            null,
        )
    }

    /** Android 9 ve oncesinde Indirilenler klasorune yazmak icin izin gerekir. */
    private fun withStoragePermission(action: () -> Unit) {
        val granted = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            action()
        } else {
            pendingDownload = action
            storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    // ------------------------------------------------------------ yonlendirme

    private fun isInternal(uri: Uri): Boolean {
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return false
        val host = uri.host ?: return false
        val base = siteHost ?: return false
        return host.equals(base, ignoreCase = true) || host.endsWith(".$base", ignoreCase = true)
    }

    /** Site disindaki baglantilari (tel:, mailto:, whatsapp, diger siteler) sisteme devreder. */
    private fun openExternally(uri: Uri): Boolean {
        val intent = if (uri.scheme == "intent") {
            runCatching { Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME) }.getOrNull()
        } else {
            Intent(Intent.ACTION_VIEW, uri)
        } ?: return false

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.error_no_app_for_link, Toast.LENGTH_LONG).show()
            true
        }
    }

    private fun showError(message: String?) {
        hasLoadError = true
        binding.errorMessage.text = message ?: getString(R.string.error_message)
        binding.errorView.visibility = android.view.View.VISIBLE
        binding.swipeRefresh.isRefreshing = false
    }

    private fun hideError() {
        hasLoadError = false
        binding.errorView.visibility = android.view.View.GONE
    }

    // ------------------------------------------------------------- webview'ler

    private inner class TeklifWebViewClient : WebViewClient() {

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val uri = request.url
            if (uri.scheme == "blob") {
                downloadBlob(uri.toString(), null)
                return true
            }
            if (isInternal(uri)) return false
            return openExternally(uri)
        }

        override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
            hasLoadError = false
            binding.progressBar.visibility = android.view.View.VISIBLE
        }

        override fun onPageFinished(view: WebView, url: String?) {
            binding.progressBar.visibility = android.view.View.GONE
            binding.swipeRefresh.isRefreshing = false
            if (!hasLoadError) binding.errorView.visibility = android.view.View.GONE
        }

        override fun onReceivedError(
            view: WebView,
            request: WebResourceRequest,
            error: WebResourceError,
        ) {
            // Sayfa icindeki gorsel/istek hatalari tam ekran hataya donusmesin.
            if (!request.isForMainFrame) return
            showError(getString(R.string.error_message))
        }
    }

    private inner class TeklifChromeClient : WebChromeClient() {

        override fun onProgressChanged(view: WebView, newProgress: Int) {
            binding.progressBar.progress = newProgress
            if (newProgress >= 100) binding.progressBar.visibility = android.view.View.GONE
        }

        override fun onShowFileChooser(
            webView: WebView,
            callback: ValueCallback<Array<Uri>>,
            params: FileChooserParams,
        ): Boolean {
            filePathCallback?.onReceiveValue(null)
            filePathCallback = callback
            return launchFileChooser(params)
        }

        override fun onPermissionRequest(request: PermissionRequest) {
            // Sayfa kamera isterse once Android iznini alalim.
            if (!request.resources.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE)) {
                request.deny()
                return
            }
            val granted = ContextCompat.checkSelfPermission(
                this@MainActivity,
                Manifest.permission.CAMERA,
            ) == PackageManager.PERMISSION_GRANTED

            if (granted) {
                request.grant(request.resources)
            } else {
                pendingCameraRequest = request
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }

        override fun onCreateWindow(
            view: WebView,
            isDialog: Boolean,
            isUserGesture: Boolean,
            resultMsg: Message,
        ): Boolean {
            // window.open ile acilan adresi yakalayip ayni pencerede ya da disarida acalim.
            val probe = WebView(view.context)
            probe.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    probeView: WebView,
                    request: WebResourceRequest,
                ): Boolean {
                    val uri = request.url
                    when {
                        uri.scheme == "blob" -> downloadBlob(uri.toString(), null)
                        isInternal(uri) -> binding.webView.loadUrl(uri.toString())
                        else -> openExternally(uri)
                    }
                    probeView.destroy()
                    return true
                }
            }
            (resultMsg.obj as WebView.WebViewTransport).webView = probe
            resultMsg.sendToTarget()
            return true
        }
    }

    // --------------------------------------------------------- dosya secimi

    private fun launchFileChooser(params: WebChromeClient.FileChooserParams): Boolean {
        val contentIntent = params.createIntent().apply {
            if (params.mode == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE) {
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            }
        }

        val chooser = Intent(Intent.ACTION_CHOOSER).apply {
            putExtra(Intent.EXTRA_INTENT, contentIntent)
            putExtra(Intent.EXTRA_TITLE, getString(R.string.file_chooser_title))
            createCameraIntent()?.let {
                putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(it))
            }
        }

        return try {
            fileChooserLauncher.launch(chooser)
            true
        } catch (e: ActivityNotFoundException) {
            filePathCallback?.onReceiveValue(null)
            filePathCallback = null
            cameraOutputUri = null
            false
        }
    }

    /** Dosya secim ekranina "fotograf cek" secenegi ekler. */
    private fun createCameraIntent(): Intent? {
        val hasCamera = packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
        if (!hasCamera) return null

        val photosDir = File(cacheDir, "camera").apply { mkdirs() }
        val photo = File(photosDir, "foto-${System.currentTimeMillis()}.jpg")
        val uri = runCatching {
            FileProvider.getUriForFile(this, "$packageName.fileprovider", photo)
        }.getOrNull() ?: return null

        cameraOutputUri = uri
        return Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, uri)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
    }
}
