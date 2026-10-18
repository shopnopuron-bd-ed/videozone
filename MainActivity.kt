package com.example.streamly

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import android.widget.FrameLayout
import android.widget.ProgressBar
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * আপনার সাইটের HTTPS ঠিকানা এখানে বসান। শুধু এই হোস্টের লিংক অ্যাপের ভেতরে খুলবে,
 * বাকি সব লিংক বাইরের ব্রাউজারে খুলবে।
 */
private const val SITE_URL = "https://YOUR-SITE.pages.dev/"
private val SITE_HOST: String = Uri.parse(SITE_URL).host ?: ""

class MainActivity : AppCompatActivity() {

    private lateinit var web: WebView
    private lateinit var progress: ProgressBar
    private lateinit var root: FrameLayout
    private var customView: View? = null
    private var customCallback: WebChromeClient.CustomViewCallback? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = FrameLayout(this)
        web = WebView(this)
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 8)
            max = 100
        }
        root.addView(web, FrameLayout.LayoutParams(-1, -1))
        root.addView(progress)
        setContentView(root)

        with(web.settings) {
            javaScriptEnabled = true               // সাইটের জন্য প্রয়োজন
            domStorageEnabled = true               // localStorage: লগইন সেশন, হিস্ট্রি, থিম
            mediaPlaybackRequiresUserGesture = true // সাইট নিজেই ইউজার ক্লিকে প্লে করে
            allowFileAccess = false
            allowContentAccess = false
            javaScriptCanOpenWindowsAutomatically = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            cacheMode = WebSettings.LOAD_DEFAULT
        }
        CookieManager.getInstance().setAcceptCookie(true)
        // কোনো addJavascriptInterface (JS Bridge) ব্যবহার করা হয়নি — ইচ্ছাকৃত।

        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(v: WebView, req: WebResourceRequest): Boolean {
                val uri = req.url
                if (uri.scheme == "https" && uri.host == SITE_HOST) return false
                try { startActivity(Intent(Intent.ACTION_VIEW, uri)) } catch (_: Exception) {}
                return true
            }
            override fun onPageStarted(v: WebView, url: String, icon: Bitmap?) { progress.visibility = View.VISIBLE }
            override fun onPageFinished(v: WebView, url: String) { progress.visibility = View.GONE }
            override fun onReceivedError(v: WebView, req: WebResourceRequest, err: WebResourceError) {
                if (req.isForMainFrame) showOffline()
            }
        }

        web.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(v: WebView, p: Int) { progress.progress = p }

            // ⬇ Fullscreen ভিডিওর জন্য এটি বাধ্যতামূলক
            override fun onShowCustomView(view: View, cb: CustomViewCallback) {
                if (customView != null) { cb.onCustomViewHidden(); return }
                customView = view; customCallback = cb
                web.visibility = View.GONE
                root.addView(view, FrameLayout.LayoutParams(-1, -1))
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                WindowCompat.setDecorFitsSystemWindows(window, false)
                WindowInsetsControllerCompat(window, root).apply {
                    systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    hide(WindowInsetsCompat.Type.systemBars())
                }
            }
            override fun onHideCustomView() { exitFullscreen() }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    customView != null -> exitFullscreen()
                    web.canGoBack() -> web.goBack()
                    else -> { isEnabled = false; onBackPressedDispatcher.onBackPressed() }
                }
            }
        })

        if (savedInstanceState != null) web.restoreState(savedInstanceState) else web.loadUrl(SITE_URL)
    }

    private fun exitFullscreen() {
        customView?.let { root.removeView(it) }
        customCallback?.onCustomViewHidden()
        customView = null; customCallback = null
        web.visibility = View.VISIBLE
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        WindowInsetsControllerCompat(window, root).show(WindowInsetsCompat.Type.systemBars())
    }

    private fun showOffline() {
        web.loadDataWithBaseURL(null, """
            <html><head><meta name='viewport' content='width=device-width,initial-scale=1'></head>
            <body style='font-family:sans-serif;background:#0a0c11;color:#eef;text-align:center;padding:30vh 20px'>
            <h2>ইন্টারনেট সংযোগ নেই</h2><p>সংযোগ পরীক্ষা করে আবার চেষ্টা করুন।</p>
            <button style='padding:12px 24px;border:0;border-radius:10px;background:#ff6b4a;font-size:16px'
              onclick="location.href='$SITE_URL'">আবার চেষ্টা করুন</button></body></html>
        """.trimIndent(), "text/html", "utf-8", null)
    }

    override fun onSaveInstanceState(out: Bundle) { super.onSaveInstanceState(out); web.saveState(out) }
    override fun onPause() { super.onPause(); web.onPause(); CookieManager.getInstance().flush() }
    override fun onResume() { super.onResume(); web.onResume() }
    override fun onDestroy() { web.destroy(); super.onDestroy() }
}
