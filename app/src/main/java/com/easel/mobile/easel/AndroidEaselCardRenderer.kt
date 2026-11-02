package com.easel.mobile.easel

import android.app.Presentation
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import com.easel.mobile.core.easel.EaselCardAudit
import com.easel.mobile.core.easel.EaselCardRender
import com.easel.mobile.core.easel.EaselCardRenderer
import com.easel.mobile.core.easel.EaselLayoutAudit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.io.ByteArrayInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 手机原生 HTML -> PNG 渲染器。优先把 WebView 挂到 App 私有虚拟显示上取帧，失败时退回软件 Canvas 绘制。
 * 两种路径都禁用 JavaScript、网络、本地文件和 content provider，只允许内联样式、系统字体、SVG 与 data: 图片。
 */
class AndroidEaselCardRenderer(private val context: Context) : EaselCardRenderer {
    override suspend fun render(html: String, width: Int, height: Int, scale: Int, waitMs: Long): EaselCardRender {
        require(width >= 200 && height >= 200) { "width and height must be at least 200" }
        require(scale in 1..3) { "scale must be 1-3" }
        val outputWidth = width * scale
        val outputHeight = height * scale
        require(outputWidth <= 4096 && outputHeight <= 4096) { "output must be at most 4096 px per side" }
        require(outputWidth.toLong() * outputHeight <= 12_000_000L) { "output must be at most 12 MP" }

        val bitmap = withContext(Dispatchers.Main.immediate) { capture(html, width, height, scale, waitMs) }
        return try {
            withContext(Dispatchers.Default) {
                EaselCardRender(png(bitmap), audit(bitmap))
            }
        } finally {
            bitmap.recycle()
        }
    }

    private suspend fun capture(html: String, width: Int, height: Int, scale: Int, waitMs: Long): Bitmap {
        val outputWidth = width * scale
        val outputHeight = height * scale
        return try {
            captureOnDisplay(html, width, height, scale, outputWidth, outputHeight, waitMs)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Log.w(TAG, "private display capture failed; falling back to software draw", e)
            captureWithCanvas(html, width, height, scale, outputWidth, outputHeight, waitMs)
        }
    }

    private suspend fun captureOnDisplay(
        html: String,
        cssWidth: Int,
        cssHeight: Int,
        scale: Int,
        outputWidth: Int,
        outputHeight: Int,
        waitMs: Long,
    ): Bitmap {
        val renderContext = renderContext(cssWidth, cssHeight, scale)
        val reader = ImageReader.newInstance(outputWidth, outputHeight, PixelFormat.RGBA_8888, 2)
        val frameThread = HandlerThread("easel-card-frames").apply { start() }
        val frameHandler = Handler(frameThread.looper)
        val latest = AtomicReference<Bitmap?>()
        val generation = AtomicInteger(0)
        var display: VirtualDisplay? = null
        var presentation: Presentation? = null
        var window: FrameLayout? = null
        var windowManager: WindowManager? = null
        var web: WebView? = null
        var captured: Bitmap? = null

        try {
            reader.setOnImageAvailableListener({ source ->
                try {
                    source.acquireLatestImage()?.use { image ->
                        copyImage(image, outputWidth, outputHeight)?.let { next ->
                            latest.getAndSet(next)?.recycle()
                            generation.incrementAndGet()
                        }
                    }
                } catch (e: Throwable) {
                    Log.w(TAG, "card frame copy failed", e)
                }
            }, frameHandler)

            val manager = context.getSystemService(DisplayManager::class.java)
            val virtualDisplay = manager.createVirtualDisplay("easel-card", outputWidth, outputHeight, 160 * scale, reader.surface, 0)
                ?: error("createVirtualDisplay returned null")
            display = virtualDisplay
            val root = attachRoot(renderContext, virtualDisplay.display).also {
                presentation = it.presentation
                window = it.window
                windowManager = it.windowManager
            }.view
            val page = WebView(renderContext).also { web = it }
            configure(page)
            root.addView(page, FrameLayout.LayoutParams(outputWidth, outputHeight))

            val loaded = CompletableDeferred<Unit>()
            page.webViewClient = CardClient(loaded)
            // 丢掉建屏时的空白帧,只认开始加载之后的画面。
            latest.getAndSet(null)?.recycle()
            page.loadDataWithBaseURL(null, secureHtml(html), "text/html", "utf-8", null)
            if (withTimeoutOrNull(LOAD_TIMEOUT_MS) { loaded.await(); true } != true) {
                throw IOException("card page did not finish loading")
            }
            delay(waitMs.coerceAtLeast(0))

            val before = generation.get()
            root.postInvalidateOnAnimation()
            page.postInvalidateOnAnimation()
            withTimeoutOrNull(FRAME_WAIT_MS) {
                while (generation.get() <= before) delay(16)
            }
            // 静态页面出新帧后可能不再变化;此时 latest 已经是加载后的最后一帧,不必为了等“新”帧而失败。
            captured = latest.get() ?: throw IOException("the card display produced no frame")
            latest.set(null)
            return captured
        } finally {
            reader.setOnImageAvailableListener(null, null)
            frameThread.quitSafely()
            runCatching { frameThread.join(1_000) }
            web?.let { page ->
                (page.parent as? FrameLayout)?.removeView(page)
                runCatching { page.stopLoading() }
                runCatching { page.destroy() }
            }
            presentation?.let { runCatching { it.dismiss() } }
            windowManager?.let { wm -> window?.let { runCatching { wm.removeViewImmediate(it) } } }
            display?.release()
            reader.close()
            if (captured == null) latest.getAndSet(null)?.recycle()
        }
    }

    private suspend fun captureWithCanvas(
        html: String,
        cssWidth: Int,
        cssHeight: Int,
        scale: Int,
        outputWidth: Int,
        outputHeight: Int,
        waitMs: Long,
    ): Bitmap {
        val renderContext = renderContext(cssWidth, cssHeight, scale)
        val root = FrameLayout(renderContext).apply { setBackgroundColor(Color.WHITE) }
        val page = WebView(renderContext)
        try {
            page.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
            configure(page)
            root.addView(page, FrameLayout.LayoutParams(outputWidth, outputHeight))
            val loaded = CompletableDeferred<Unit>()
            page.webViewClient = CardClient(loaded)
            page.loadDataWithBaseURL(null, secureHtml(html), "text/html", "utf-8", null)
            if (withTimeoutOrNull(LOAD_TIMEOUT_MS) { loaded.await(); true } != true) {
                throw IOException("card page did not finish loading")
            }
            delay(waitMs.coerceAtLeast(0))
            val widthSpec = View.MeasureSpec.makeMeasureSpec(outputWidth, View.MeasureSpec.EXACTLY)
            val heightSpec = View.MeasureSpec.makeMeasureSpec(outputHeight, View.MeasureSpec.EXACTLY)
            root.measure(widthSpec, heightSpec)
            root.layout(0, 0, outputWidth, outputHeight)
            return Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888).also { bitmap ->
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.WHITE)
                root.draw(canvas)
            }
        } finally {
            (page.parent as? FrameLayout)?.removeView(page)
            runCatching { page.stopLoading() }
            runCatching { page.destroy() }
        }
    }

    private fun renderContext(cssWidth: Int, cssHeight: Int, scale: Int): Context {
        val config = Configuration(context.resources.configuration).apply {
            densityDpi = 160 * scale
            screenWidthDp = cssWidth
            screenHeightDp = cssHeight
            fontScale = 1f
            orientation = if (cssWidth > cssHeight) Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT
        }
        return context.createConfigurationContext(config)
    }

    private class AttachedRoot(
        val view: FrameLayout,
        val presentation: Presentation? = null,
        val window: FrameLayout? = null,
        val windowManager: WindowManager? = null,
    )

    /** 和 WebHost 一样:先试 Presentation,失败就加到私有显示的窗口上;两条都不通才退回软件绘制。 */
    private fun attachRoot(base: Context, display: android.view.Display): AttachedRoot {
        var presentation: Presentation? = null
        try {
            presentation = Presentation(base, display)
            val root = FrameLayout(presentation.context).apply { setBackgroundColor(Color.WHITE) }
            presentation.setContentView(root)
            presentation.show()
            return AttachedRoot(root, presentation = presentation)
        } catch (e: Exception) {
            Log.w(TAG, "Presentation on private display failed, trying WindowManager", e)
            runCatching { presentation?.dismiss() }
        }

        val windowContext = base.createDisplayContext(display)
            .createWindowContext(WindowManager.LayoutParams.TYPE_PRIVATE_PRESENTATION, null)
        val manager = windowContext.getSystemService(WindowManager::class.java)
        val root = FrameLayout(windowContext).apply { setBackgroundColor(Color.WHITE) }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_PRIVATE_PRESENTATION,
            0,
            PixelFormat.OPAQUE,
        )
        manager.addView(root, params)
        return AttachedRoot(root, window = root, windowManager = manager)
    }

    private fun configure(page: WebView) {
        page.setBackgroundColor(Color.WHITE)
        page.isVerticalScrollBarEnabled = false
        page.isHorizontalScrollBarEnabled = false
        page.overScrollMode = View.OVER_SCROLL_NEVER
        page.settings.apply {
            javaScriptEnabled = false
            domStorageEnabled = false
            allowFileAccess = false
            allowContentAccess = false
            @Suppress("DEPRECATION")
            allowFileAccessFromFileURLs = false
            @Suppress("DEPRECATION")
            allowUniversalAccessFromFileURLs = false
            blockNetworkLoads = true
            cacheMode = WebSettings.LOAD_NO_CACHE
            loadsImagesAutomatically = true
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            safeBrowsingEnabled = true
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
            useWideViewPort = false
            loadWithOverviewMode = false
            setGeolocationEnabled(false)
            @Suppress("DEPRECATION")
            saveFormData = false
        }
        page.webChromeClient = object : WebChromeClient() {
            override fun onJsAlert(view: WebView, url: String, message: String, result: JsResult): Boolean {
                result.cancel()
                return true
            }
        }
    }

    private fun secureHtml(html: String): String {
        val meta = "<meta http-equiv=\"Content-Security-Policy\" content=\"default-src 'none'; script-src 'none'; connect-src 'none'; object-src 'none'; frame-src 'none'; base-uri 'none'; form-action 'none'; style-src 'unsafe-inline'; img-src data:; font-src data:; media-src data:\">"
        val head = Regex("(?i)<head(?:\\s[^>]*)?>").find(html)
        if (head != null) {
            val at = head.range.last + 1
            return html.substring(0, at) + meta + html.substring(at)
        }
        val htmlTag = Regex("(?i)<html(?:\\s[^>]*)?>").find(html)
        if (htmlTag != null) {
            val at = htmlTag.range.last + 1
            return html.substring(0, at) + "<head>$meta</head>" + html.substring(at)
        }
        val body = Regex("(?i)<body(?:\\s[^>]*)?>").find(html)
        if (body != null) return html.substring(0, body.range.first) + "<head>$meta</head>" + html.substring(body.range.first)
        return "<!doctype html><html><head>$meta</head><body>$html</body></html>"
    }

    private fun copyImage(image: Image, width: Int, height: Int): Bitmap? {
        val plane = image.planes.firstOrNull() ?: return null
        if (plane.pixelStride != 4) return null
        val stridePx = plane.rowStride / plane.pixelStride
        if (stridePx < width) return null
        val raw = Bitmap.createBitmap(stridePx, height, Bitmap.Config.ARGB_8888)
        return try {
            val buffer = plane.buffer.apply { rewind() }
            val needed = raw.byteCount
            if (buffer.remaining() >= needed) {
                raw.copyPixelsFromBuffer(buffer)
            } else {
                // 最后一行可能不带对齐填充:垫进等长缓冲后再拷,不用整帧作废。
                val padded = ByteBuffer.allocateDirect(needed)
                padded.put(buffer)
                padded.rewind()
                raw.copyPixelsFromBuffer(padded)
            }
            if (stridePx == width) raw else Bitmap.createBitmap(raw, 0, 0, width, height).also { raw.recycle() }
        } catch (e: Throwable) {
            raw.recycle()
            throw e
        }
    }

    private fun audit(bitmap: Bitmap): EaselCardAudit {
        val longest = max(bitmap.width, bitmap.height)
        val factor = min(1.0, AUDIT_MAX_SIDE.toDouble() / longest)
        val width = (bitmap.width * factor).roundToInt().coerceAtLeast(1)
        val height = (bitmap.height * factor).roundToInt().coerceAtLeast(1)
        val sampled = if (width == bitmap.width && height == bitmap.height) bitmap else Bitmap.createScaledBitmap(bitmap, width, height, true)
        return try {
            val argb = IntArray(width * height)
            sampled.getPixels(argb, 0, width, 0, 0, width, height)
            val rows = Array(height) { y ->
                IntArray(width) { x ->
                    val color = argb[y * width + x]
                    (((color shr 16) and 0xFF) * 299 + ((color shr 8) and 0xFF) * 587 + (color and 0xFF) * 114) / 1000
                }
            }
            EaselLayoutAudit.analyze(rows)
        } finally {
            if (sampled !== bitmap) sampled.recycle()
        }
    }

    private fun png(bitmap: Bitmap): ByteArray = ByteArrayOutputStream().use { out ->
        check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) { "PNG encoding failed" }
        out.toByteArray()
    }

    private class CardClient(private val loaded: CompletableDeferred<Unit>) : WebViewClient() {
        override fun onPageFinished(view: WebView, url: String) {
            if (!loaded.isCompleted) loaded.complete(Unit)
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (request.isForMainFrame && !loaded.isCompleted) {
                loaded.completeExceptionally(IOException("${error.errorCode}: ${error.description}"))
            }
        }

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val scheme = request.url.scheme?.lowercase()
            return scheme != "data" && scheme != "about"
        }

        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
            val scheme = request.url.scheme?.lowercase()
            return if (scheme == "data" || scheme == "about") null else BLOCKED
        }

        override fun onRenderProcessGone(view: WebView, detail: android.webkit.RenderProcessGoneDetail): Boolean {
            if (!loaded.isCompleted) loaded.completeExceptionally(IOException("the WebView renderer crashed"))
            return true
        }
    }

    private companion object {
        const val TAG = "EaselCardRenderer"
        const val LOAD_TIMEOUT_MS = 15_000L
        const val FRAME_WAIT_MS = 3_000L
        const val AUDIT_MAX_SIDE = 900
        val BLOCKED = WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
    }
}
