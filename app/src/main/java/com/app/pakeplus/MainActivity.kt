package com.app.pakeplus

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.webkit.PermissionRequest // 必须保留这个
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GestureDetectorCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.net.toUri
import org.json.JSONObject
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var gestureDetector: GestureDetectorCompat

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. 先初始化布局配置 (防止闪退)
        enableEdgeToEdge()
        setContentView(R.layout.single_main)

        // 设置安全区域
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.ConstraintLayout)) { view, insets ->
            val systemBar = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBar.left, systemBar.top, systemBar.right, 0)
            insets
        }

        // 2. 读取配置
        val config = parseJsonWithNative(this, "app.json")
        val fullScreen = config?.get("fullScreen") as? Boolean ?: false
        val debug = config?.get("debug") as? Boolean ?: false
        val userAgent = config?.get("userAgent") as? String ?: ""
        
        // 强制指定网址
        val webUrl = "https://xmas.chaz.fun/?id=cQ3w6ttvVhsEIKZc&m=view"

        // 开启 WebView 调试
        WebView.setWebContentsDebuggingEnabled(debug)

        // 3. 全屏设置
        if (fullScreen) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
            )
            window.setFlags(
                WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION,
                WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION
            )
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val lp = window.attributes
                lp.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                window.attributes = lp
            } else {
                window.decorView.systemUiVisibility = (
                        View.SYSTEM_UI_FLAG_FULLSCREEN or
                                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        )
            }
        }

        // 4. 初始化 WebView (合并了之前的重复逻辑)
        webView = findViewById(R.id.webview)

        // 5. 配置 Settings
        webView.settings.apply {
            javaScriptEnabled = true        // 启用JS
            domStorageEnabled = true        // 启用DOM存储
            allowFileAccess = true          // 允许文件访问
            useWideViewPort = true
            loadWithOverviewMode = true
            mediaPlaybackRequiresUserGesture = false // 允许自动播放
            setSupportMultipleWindows(true)
            
            // 设置 UA
            if (userAgent.isNotEmpty()) {
                userAgentString = userAgent
            }
            setSupportZoom(false)
        }
        
        webView.clearCache(true)

        // 6. 配置 Client
        // 处理网页跳转、Intent 拦截
        webView.webViewClient = MyWebViewClient(debug)
        
        // 处理进度条、以及最重要的【摄像头权限】
        // 注意：这里使用的是底部的 MyChromeClient 类，我已经帮你修改了那个类
        webView.webChromeClient = MyChromeClient()

        // 7. 手势设置 (Swipe Back)
        gestureDetector =
            GestureDetectorCompat(this, object : GestureDetector.SimpleOnGestureListener() {
                override fun onFling(
                    e1: MotionEvent?,
                    e2: MotionEvent,
                    velocityX: Float,
                    velocityY: Float
                ): Boolean {
                    if (e1 == null) return false
                    val diffX = e2.x - e1.x
                    val diffY = e2.y - e1.y
                    if (abs(diffX) > abs(diffY)) {
                        if (abs(diffX) > 100 && abs(velocityX) > 100) {
                            if (diffX > 0) {
                                if (webView.canGoBack()) {
                                    webView.goBack()
                                    return true
                                }
                            } else {
                                if (webView.canGoForward()) {
                                    webView.goForward()
                                    return true
                                }
                            }
                        }
                    }
                    return false
                }
            })

        webView.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            false
        }

        // 8. 最后一步：加载网页 (确保所有监听器都就绪)
        webView.loadUrl(webUrl)
    }


    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    fun parseJsonWithNative(context: Context, jsonFilePath: String): Map<String, Any>? {
        return try {
            val jsonString = assets.open(jsonFilePath).bufferedReader().use { it.readText() }
            val jsonObject = JSONObject(jsonString)
            mapOf(
                "name" to jsonObject.getString("name"),
                "webUrl" to jsonObject.getString("webUrl"),
                "debug" to jsonObject.getBoolean("debug"),
                "userAgent" to jsonObject.getString("userAgent"),
                "fullScreen" to jsonObject.getBoolean("fullScreen")
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // =========================================================
    // 内部类：处理页面加载逻辑
    // =========================================================
    inner class MyWebViewClient(val debug: Boolean) : WebViewClient() {

        @Deprecated("Deprecated in Java", ReplaceWith("false"))
        override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
            val urlStr = url.toString()

            if (urlStr.startsWith("http://") || urlStr.startsWith("https://")) {
                return false
            }

            // 处理 Intent
            if (urlStr.startsWith("intent://")) {
                try {
                    val intent = Intent.parseUri(urlStr, Intent.URI_INTENT_SCHEME)
                    if (intent.resolveActivity(view?.context?.packageManager!!) != null) {
                        view.context?.startActivity(intent)
                        return true
                    }
                    val fallbackUrl = intent.getStringExtra("browser_fallback_url")
                    if (!fallbackUrl.isNullOrEmpty()) {
                        view.loadUrl(fallbackUrl)
                        return true
                    }
                } catch (e: Exception) {
                    Log.e("WebViewClient", "Bad Intent URI", e)
                }
            }

            // 处理其他 Scheme
            try {
                val intent = Intent(Intent.ACTION_VIEW, urlStr.toUri())
                if (intent.resolveActivity(view?.context?.packageManager!!) != null) {
                    view.context?.startActivity(intent)
                    return true
                }
            } catch (e: Exception) {
                Log.e("WebViewClient", "Error starting external app", e)
            }
            return false
        }

        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            super.onPageStarted(view, url, favicon)
            // 注入 JS (需确保 assets 里有 custom.js 和 vConsole.js，否则可能会抛错，这里加了 try catch 保护)
            try {
                if (debug) {
                    val vConsole = assets.open("vConsole.js").bufferedReader().use { it.readText() }
                    val openDebug = """var vConsole = new window.VConsole()"""
                    view?.evaluateJavascript(vConsole + openDebug, null)
                }
                val injectJs = assets.open("custom.js").bufferedReader().use { it.readText() }
                view?.evaluateJavascript(injectJs, null)
            } catch (e: Exception) {
                // 文件不存在时不崩溃
            }
        }
        
        override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
             super.onReceivedError(view, request, error)
        }
    }

    // =========================================================
    // 内部类：处理 Chrome Client (进度条、权限)
    // =========================================================
    inner class MyChromeClient : WebChromeClient() {
        
        // 👇👇👇 这里的修改最关键！加上了权限处理 👇👇👇
        override fun onPermissionRequest(request: PermissionRequest) {
            // 收到网页的摄像头/麦克风请求时，直接批准
            request.grant(request.resources)
        }
        // 👆👆👆 修改结束 👆👆👆

        override fun onProgressChanged(view: WebView?, newProgress: Int) {
            super.onProgressChanged(view, newProgress)
        }
    }
}
