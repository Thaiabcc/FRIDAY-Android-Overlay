package com.stark.friday

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.TextView
import java.util.Locale
import kotlin.math.abs

class FridayOverlayService : Service(), TextToSpeech.OnInitListener {

    private lateinit var windowManager: WindowManager
    private var bubbleView: View? = null
    private var hudContainerView: FrameLayout? = null
    private var hudWebView: WebView? = null

    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        tts = TextToSpeech(this, this)
        startForegroundNotification()
        createFloatingBubble()
        createGlassmorphismHudPopup()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.getStringExtra("ACTION")
        if (action == "OPEN_HUD_AND_LISTEN") {
            showHudPopup(autoListen = true)
        }
        return START_STICKY
    }

    private fun startForegroundNotification() {
        val channelId = "friday_overlay_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "F.R.I.D.A.Y. Tactical Overlay",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val openIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, channelId)
                .setContentTitle("F.R.I.D.A.Y. Đang Trực Tuyến")
                .setContentText("Chạm vào bong bóng Arc Reactor trên màn hình để giao việc")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentIntent(pendingIntent)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("F.R.I.D.A.Y. Đang Trực Tuyến")
                .setContentText("Chạm vào bong bóng Arc Reactor để giao việc")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentIntent(pendingIntent)
                .build()
        }

        startForeground(1001, notification)
    }

    /**
     * 1. Tạo Bong bóng Floating Bubble (Lò phản ứng Hồ Quang trong suốt, mờ ảo, kéo thả tự do)
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun createFloatingBubble() {
        val sizePx = (64 * resources.displayMetrics.density).toInt()
        val bubbleLayout = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#88041830")) // Nền tối bán trong suốt
                setStroke((2.5f * resources.displayMetrics.density).toInt(), Color.parseColor("#00F0FF")) // Viền Neon Cyan
            }
            elevation = 16f
        }

        val coreIcon = TextView(this).apply {
            text = "⚡"
            textSize = 24f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#00F0FF"))
        }
        bubbleLayout.addView(
            coreIcon,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            sizePx,
            sizePx,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24
            y = 320
        }

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        bubbleLayout.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(bubbleLayout, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val diffX = abs(event.rawX - initialTouchX)
                    val diffY = abs(event.rawY - initialTouchY)
                    if (diffX < 12 && diffY < 12) {
                        // Người dùng chạm vào bong bóng -> Bật cửa sổ kính mờ Glassmorphism và lắng nghe ngay!
                        showHudPopup(autoListen = true)
                    }
                    true
                }
                else -> false
            }
        }

        bubbleView = bubbleLayout
        windowManager.addView(bubbleView, params)
    }

    /**
     * 2. Tạo cửa sổ Pop-up Glassmorphism (Kính mờ + Blur Background Android 12+ + Sóng âm Neon)
     */
    @SuppressLint("SetJavaScriptEnabled")
    private fun createGlassmorphismHudPopup() {
        val container = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#66010814"))
            visibility = View.GONE
            setOnClickListener {
                hideHudPopup()
            }
        }

        val webView = WebView(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.cacheMode = WebSettings.LOAD_NO_CACHE
            webChromeClient = object : WebChromeClient() {
                override fun onPermissionRequest(request: PermissionRequest?) {
                    request?.grant(request.resources)
                }
            }
            addJavascriptInterface(FridayJsBridge(), "AndroidBridge")
            loadUrl("file:///android_asset/overlay_hud.html")
        }

        container.addView(
            webView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.CENTER
            )
        )

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        // Cho phép nhận bàn phím + hiệu ứng làm mờ nền phía sau (FLAG_BLUR_BEHIND trên Android 12+)
        var flags = WindowManager.LayoutParams.FLAG_DIM_BEHIND
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags = flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
        }

        val hudParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType,
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
            dimAmount = 0.55f
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                blurBehindRadius = 38
            }
        }

        hudContainerView = container
        hudWebView = webView
        windowManager.addView(hudContainerView, hudParams)
    }

    private fun getSavedServerUrl(): String {
        val prefs = getSharedPreferences("friday_prefs", Context.MODE_PRIVATE)
        return prefs.getString(
            "server_url",
            "https://laundry-melbourne-personnel-sufficiently.trycloudflare.com"
        ) ?: "https://laundry-melbourne-personnel-sufficiently.trycloudflare.com"
    }

    private fun showHudPopup(autoListen: Boolean) {
        mainHandler.post {
            bubbleView?.visibility = View.GONE
            hudContainerView?.visibility = View.VISIBLE
            val url = getSavedServerUrl().replace("'", "\\'")
            hudWebView?.evaluateJavascript("setServerUrlFromNative('$url');", null)
            if (autoListen) {
                hudWebView?.evaluateJavascript("setWaveState('listening');", null)
                startListeningVietnamese()
            }
        }
    }

    private fun hideHudPopup() {
        mainHandler.post {
            stopListening()
            hudContainerView?.visibility = View.GONE
            bubbleView?.visibility = View.VISIBLE
        }
    }

    /**
     * 3. Nhận diện giọng nói tiếng Việt (vi-VN) Native trên Android & kết nối sang WebView
     */
    private fun startListeningVietnamese() {
        mainHandler.post {
            try {
                if (speechRecognizer == null) {
                    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
                    speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) {
                            hudWebView?.evaluateJavascript("setWaveState('listening');", null)
                        }
                        override fun onBeginningOfSpeech() {}
                        override fun onRmsChanged(rmsdB: Float) {}
                        override fun onBufferReceived(buffer: ByteArray?) {}
                        override fun onEndOfSpeech() {}
                        override fun onError(error: Int) {
                            hudWebView?.evaluateJavascript("setWaveState('idle');", null)
                        }
                        override fun onResults(results: Bundle?) {
                            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            if (!matches.isNullOrEmpty()) {
                                val spokenText = matches[0].replace("'", "\\'")
                                hudWebView?.evaluateJavascript("onVoiceResultFromNative('$spokenText');", null)
                            } else {
                                hudWebView?.evaluateJavascript("setWaveState('idle');", null)
                            }
                        }
                        override fun onPartialResults(partialResults: Bundle?) {}
                        override fun onEvent(eventType: Int, params: Bundle?) {}
                    })
                }

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "vi-VN")
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                }
                speechRecognizer?.startListening(intent)
            } catch (e: Exception) {
                hudWebView?.evaluateJavascript("setWaveState('idle');", null)
            }
        }
    }

    private fun stopListening() {
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
            } catch (_: Exception) {}
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale("vi", "VN")
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    mainHandler.post {
                        hudWebView?.evaluateJavascript("setWaveState('speaking');", null)
                    }
                }
                override fun onDone(utteranceId: String?) {
                    mainHandler.post {
                        hudWebView?.evaluateJavascript("setWaveState('idle');", null)
                    }
                }
                override fun onError(utteranceId: String?) {
                    mainHandler.post {
                        hudWebView?.evaluateJavascript("setWaveState('idle');", null)
                    }
                }
            })
        }
    }

    inner class FridayJsBridge {
        @JavascriptInterface
        fun startNativeVoiceRecognition() {
            startListeningVietnamese()
        }

        @JavascriptInterface
        fun speakText(text: String) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "FRIDAY_UTTERANCE_ID")
        }

        @JavascriptInterface
        fun minimizeToBubble() {
            hideHudPopup()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        bubbleView?.let { windowManager.removeView(it) }
        hudContainerView?.let { windowManager.removeView(it) }
        speechRecognizer?.destroy()
        tts?.shutdown()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
