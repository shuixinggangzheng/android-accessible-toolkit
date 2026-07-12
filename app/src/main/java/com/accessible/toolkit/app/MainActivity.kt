package com.accessible.toolkit.app

import android.Manifest
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Bundle
import android.view.View
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.accessible.toolkit.bridge.BridgeService
import com.accessible.toolkit.elder.MedicationReminder
import com.accessible.toolkit.elder.MedicationReminderActivity
import com.accessible.toolkit.service.AccessibleService
import com.accessible.toolkit.subtitle.SubtitleService
import com.accessible.toolkit.voice.VoiceAssistActivity

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: AppPreferences
    private lateinit var permissionManager: PermissionManager

    // Status cards
    private lateinit var cardSubtitleLabel: TextView
    private lateinit var cardSubtitleStatus: TextView
    private lateinit var dotSubtitle: View
    private lateinit var cardBridgeStatus: TextView
    private lateinit var cardMedicationStatus: TextView

    // Module switches
    private lateinit var switchSubtitleModule: Switch
    private lateinit var switchTtsModule: Switch
    private lateinit var switchBridgeModule: Switch
    private lateinit var switchElderModule: Switch

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        permissionManager.handlePermissionResult(permissions)
    }

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        permissionManager.handleOverlayResult()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = AppPreferences(this)
        if (!prefs.hasSeenPrivacyIntro) {
            startActivity(Intent(this, PrivacyFirstActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            finish()
            return
        }

        setContentView(R.layout.activity_main)

        permissionManager = PermissionManager(this)
        permissionManager.registerLaunchers(requestPermissionLauncher, overlayPermissionLauncher)

        initViews()
        setupQuickButtons()
        setupModuleSwitches()
        setupBottomNav()
        updateStatusCards()
        applyModuleVisibility()
        startQuickBallService()
    }

    private fun initViews() {
        cardSubtitleLabel = findViewById(R.id.tv_card_subtitle_label)
        cardSubtitleStatus = findViewById(R.id.tv_card_subtitle_status)
        dotSubtitle = findViewById(R.id.dot_subtitle)
        cardBridgeStatus = findViewById(R.id.tv_card_bridge_status)
        cardMedicationStatus = findViewById(R.id.tv_card_medication_status)

        switchSubtitleModule = findViewById(R.id.switch_subtitle_module)
        switchTtsModule = findViewById(R.id.switch_tts_module)
        switchBridgeModule = findViewById(R.id.switch_bridge_module)
        switchElderModule = findViewById(R.id.switch_elder_module)
    }

    private fun setupQuickButtons() {
        findViewById<MaterialButton>(R.id.btn_subtitle).setOnClickListener {
            ensurePermissions { toggleSubtitle() }
        }

        findViewById<MaterialButton>(R.id.btn_tts_panel).setOnClickListener {
            startActivity(Intent(this, VoiceOutputActivity::class.java))
        }

        findViewById<MaterialButton>(R.id.btn_bridge).setOnClickListener {
            toggleBridge()
        }

        findViewById<MaterialButton>(R.id.btn_medication).setOnClickListener {
            startActivity(Intent(this, MedicationReminderActivity::class.java))
        }
    }

    private fun setupModuleSwitches() {
        switchSubtitleModule.isChecked = prefs.moduleSubtitleEnabled
        switchTtsModule.isChecked = prefs.moduleTtsEnabled
        switchBridgeModule.isChecked = prefs.moduleBridgeEnabled
        switchElderModule.isChecked = prefs.moduleElderEnabled

        switchSubtitleModule.setOnCheckedChangeListener { _, v ->
            prefs.moduleSubtitleEnabled = v
            if (!v) SubtitleService.stop(this)
            applyModuleVisibility()
        }

        switchTtsModule.setOnCheckedChangeListener { _, v ->
            prefs.moduleTtsEnabled = v
            applyModuleVisibility()
        }

        switchBridgeModule.setOnCheckedChangeListener { _, v ->
            prefs.moduleBridgeEnabled = v
            if (!v) BridgeService.stop(this)
            applyModuleVisibility()
        }

        switchElderModule.setOnCheckedChangeListener { _, v ->
            prefs.moduleElderEnabled = v
            applyModuleVisibility()
        }
    }

    private fun applyModuleVisibility() {
        val showSubtitle = prefs.moduleSubtitleEnabled
        val showTts = prefs.moduleTtsEnabled
        val showBridge = prefs.moduleBridgeEnabled
        val showElder = prefs.moduleElderEnabled

        findViewById<MaterialButton>(R.id.btn_subtitle).visibility = if (showSubtitle) View.VISIBLE else View.GONE
        findViewById<MaterialButton>(R.id.btn_tts_panel).visibility = if (showTts) View.VISIBLE else View.GONE
        findViewById<MaterialButton>(R.id.btn_bridge).visibility = if (showBridge) View.VISIBLE else View.GONE
        findViewById<MaterialButton>(R.id.btn_medication).visibility = if (showElder) View.VISIBLE else View.GONE
    }

    private fun setupBottomNav() {
        findViewById<BottomNavigationView>(R.id.bottom_nav).apply {
            setOnItemSelectedListener { item ->
                when (item.itemId) {
                    R.id.nav_home -> true
                    R.id.nav_settings -> {
                        startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
                        false
                    }
                    else -> false
                }
            }
        }
    }

    private fun updateStatusCards() {
        val subtitleRunning = SubtitleService.isRunning
        val subtitlePaused = SubtitleService.isPaused

        if (subtitleRunning) {
            dotSubtitle.visibility = View.VISIBLE
            cardSubtitleStatus.text = if (subtitlePaused) "已暂停" else "监听中"
            cardSubtitleStatus.setTextColor(if (subtitlePaused) 0xFFF44336.toInt() else 0xFF4CAF50.toInt())
        } else {
            dotSubtitle.visibility = View.GONE
            cardSubtitleStatus.text = "未启动"
            cardSubtitleStatus.setTextColor(0xFF9E9E9E.toInt())
        }

        if (BridgeService.isRunning) {
            cardBridgeStatus.text = "已连接"
            cardBridgeStatus.setTextColor(0xFF4CAF50.toInt())
        } else {
            cardBridgeStatus.text = "未连接"
            cardBridgeStatus.setTextColor(0xFF9E9E9E.toInt())
        }

        val reminder = MedicationReminder(this)
        val reminders = reminder.getReminders()
        cardMedicationStatus.text = "${reminders.size} 个提醒"
        cardMedicationStatus.setTextColor(
            if (reminders.isNotEmpty()) 0xFF4CAF50.toInt() else 0xFF9E9E9E.toInt()
        )
    }

    private fun ensurePermissions(onGranted: () -> Unit) {
        permissionManager.setCallback(object : PermissionManager.PermissionCallback {
            override fun onAllPermissionsGranted() { onGranted() }
            override fun onPermissionDenied(permission: String) {
                Toast.makeText(this@MainActivity, "需要权限才能使用此功能", Toast.LENGTH_SHORT).show()
            }
            override fun onOverlayPermissionDenied() {
                Toast.makeText(this@MainActivity, "需要悬浮窗权限", Toast.LENGTH_SHORT).show()
            }
        })
        permissionManager.checkAndRequestAllPermissions()
    }

    private fun toggleSubtitle() {
        if (SubtitleService.isRunning) {
            SubtitleService.stop(this)
            Toast.makeText(this, "字幕已停止", Toast.LENGTH_SHORT).show()
        } else {
            SubtitleService.start(this)
            Toast.makeText(this, "字幕已启动", Toast.LENGTH_SHORT).show()
        }
        updateStatusCards()
    }

    private fun toggleBridge() {
        if (!prefs.moduleBridgeEnabled) {
            Toast.makeText(this, "请先开启 PC 字幕模块", Toast.LENGTH_SHORT).show()
            return
        }
        if (BridgeService.isRunning) {
            BridgeService.stop(this)
            Toast.makeText(this, "PC 字幕已停止", Toast.LENGTH_SHORT).show()
        } else {
            BridgeService.start(this)
            val ip = BridgeService.getDeviceLanIp(this)
            Toast.makeText(this, "PC 字幕已启动\nhttp://$ip:8766", Toast.LENGTH_LONG).show()
        }
        updateStatusCards()
    }

    private fun startQuickBallService() {
        QuickBallService.start(this)
    }

    override fun onResume() {
        super.onResume()
        updateStatusCards()
    }
}
