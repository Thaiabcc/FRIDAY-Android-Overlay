package com.stark.friday

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var urlInput: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("friday_prefs", Context.MODE_PRIVATE)
        val savedUrl = prefs.getString(
            "server_url",
            "https://laundry-melbourne-personnel-sufficiently.trycloudflare.com"
        ) ?: "https://laundry-melbourne-personnel-sufficiently.trycloudflare.com"

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#020A18"))
            setPadding(48, 72, 48, 48)
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val title = TextView(this).apply {
            text = "F.R.I.D.A.Y. // OVERLAY CONTROLLER"
            textSize = 20f
            setTextColor(Color.parseColor("#00F0FF"))
            setPadding(0, 0, 0, 16)
        }

        val subTitle = TextView(this).apply {
            text = "Cấu hình kết nối Cloudflare 4G / LAN tới Server FastAPI trên Laptop và bật Bong bóng Trợ lý ảo đè lên mọi ứng dụng."
            textSize = 14f
            setTextColor(Color.parseColor("#90B8D4"))
            setPadding(0, 0, 0, 36)
        }

        urlInput = EditText(this).apply {
            setText(savedUrl)
            hint = "https://....trycloudflare.com hoặc http://192.168.100.237:8000"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#527A96"))
            setPadding(32, 28, 32, 28)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#071C36"))
                setStroke(2, Color.parseColor("#00F0FF"))
                cornerRadius = 18f
            }
        }

        val btnLaunchBubble = Button(this).apply {
            text = "⚡ KÍCH HOẠT BONG BÓNG F.R.I.D.A.Y. OVERLAY"
            setTextColor(Color.parseColor("#02101F"))
            textSize = 15f
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#00F0FF"))
                cornerRadius = 20f
            }
            setPadding(24, 28, 24, 28)
            setOnClickListener {
                saveUrlAndStartOverlay(openHudNow = false)
            }
        }

        val btnOpenHudNow = Button(this).apply {
            text = "🎙️ BẬT CỬA SỔ KÍNH MỜ & NÓI NGAY"
            setTextColor(Color.parseColor("#FFAA00"))
            textSize = 15f
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#162944"))
                setStroke(3, Color.parseColor("#FFAA00"))
                cornerRadius = 20f
            }
            setPadding(24, 28, 24, 28)
            setOnClickListener {
                saveUrlAndStartOverlay(openHudNow = true)
            }
        }

        val btnStop = Button(this).apply {
            text = "TẮT BONG BÓNG OVERLAY"
            setTextColor(Color.parseColor("#FF6B88"))
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener {
                stopService(Intent(this@MainActivity, FridayOverlayService::class.java))
                Toast.makeText(this@MainActivity, "Đã tắt bong bóng F.R.I.D.A.Y.", Toast.LENGTH_SHORT).show()
            }
        }

        val space1 = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 36 }

        val space2 = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 24 }

        root.addView(title)
        root.addView(subTitle)
        root.addView(urlInput)
        root.addView(btnLaunchBubble, space1)
        root.addView(btnOpenHudNow, space2)
        root.addView(btnStop, space2)

        setContentView(root)
        checkAndRequestAudioPermission()

        // Nếu được gọi từ phím tắt Assistant (Voice Wake-up) -> Mở thẳng cửa sổ lắng nghe
        if (intent?.action == Intent.ACTION_ASSIST) {
            saveUrlAndStartOverlay(openHudNow = true)
        }
    }

    private fun checkAndRequestAudioPermission() {
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), 200)
        }
    }

    private fun saveUrlAndStartOverlay(openHudNow: Boolean) {
        val rawUrl = urlInput.text.toString().trim().trimEnd('/')
        getSharedPreferences("friday_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("server_url", rawUrl)
            .apply()

        // Kiểm tra quyền Hiển thị trên ứng dụng khác (Draw over other apps)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Toast.makeText(
                this,
                "Vui lòng bật quyền 'Hiển thị trên ứng dụng khác' cho F.R.I.D.A.Y.",
                Toast.LENGTH_LONG
            ).show()
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
            return
        }

        val serviceIntent = Intent(this, FridayOverlayService::class.java).apply {
            if (openHudNow) {
                putExtra("ACTION", "OPEN_HUD_AND_LISTEN")
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        Toast.makeText(this, "F.R.I.D.A.Y. Floating Overlay đã kích hoạt!", Toast.LENGTH_SHORT).show()
        moveTaskToBack(true)
    }
}
