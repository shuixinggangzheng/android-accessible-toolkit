package com.accessible.toolkit.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.accessible.toolkit.bridge.BridgeService
import com.accessible.toolkit.elder.MedicationReminderActivity
import com.accessible.toolkit.service.AccessibleService
import com.accessible.toolkit.subtitle.SubtitleService
import com.accessible.toolkit.voice.VoiceAssistActivity

class MainActivity : AppCompatActivity() {

    private lateinit var viewModel: MainViewModel
    private lateinit var permissionManager: PermissionManager
    private lateinit var prefs: AppPreferences
    private var isSubtitleRunning = false
    private var isBridgeRunning = false

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

        viewModel = ViewModelProvider(this)[MainViewModel::class.java]
        permissionManager = PermissionManager(this)
        permissionManager.registerLaunchers(requestPermissionLauncher, overlayPermissionLauncher)

        setupButtons()
        updateSubtitleButtonState()
        updateBridgeButtonState()
        startQuickBallService()
    }

    private fun setupButtons() {
        findViewById<Button>(R.id.btn_subtitle).setOnClickListener {
            toggleSubtitle()
        }

        findViewById<Button>(R.id.btn_voice_assist).setOnClickListener {
            openVoiceAssist()
        }

        findViewById<Button>(R.id.btn_voice_output).setOnClickListener {
            openVoiceOutput()
        }

        findViewById<Button>(R.id.btn_communication).setOnClickListener {
            openCommunicationPanel()
        }

        findViewById<Button>(R.id.btn_medication_reminder).setOnClickListener {
            openElderAssist()
        }

        findViewById<Button>(R.id.btn_emergency_call).setOnClickListener {
            showEmergencyCallDialog()
        }

        findViewById<Button>(R.id.btn_web_bridge).setOnClickListener {
            toggleBridge()
        }

        findViewById<Button>(R.id.btn_accessibility_settings).setOnClickListener {
            permissionManager.openAccessibilitySettings()
        }

        findViewById<Button>(R.id.btn_app_settings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun toggleSubtitle() {
        if (isSubtitleRunning) {
            stopSubtitleService()
        } else {
            permissionManager.setCallback(object : PermissionManager.PermissionCallback {
                override fun onAllPermissionsGranted() {
                    startSubtitleService()
                }

                override fun onPermissionDenied(permission: String) {
                    Toast.makeText(this@MainActivity, "需要权限才能使用字幕功能", Toast.LENGTH_SHORT).show()
                }

                override fun onOverlayPermissionDenied() {
                    Toast.makeText(this@MainActivity, "需要悬浮窗权限", Toast.LENGTH_SHORT).show()
                }
            })
            permissionManager.checkAndRequestAllPermissions()
        }
    }

    private fun startSubtitleService() {
        SubtitleService.start(this)
        isSubtitleRunning = true
        updateSubtitleButtonState()
        Toast.makeText(this, "字幕服务已启动", Toast.LENGTH_SHORT).show()
    }

    private fun stopSubtitleService() {
        SubtitleService.stop(this)
        isSubtitleRunning = false
        updateSubtitleButtonState()
        Toast.makeText(this, "字幕服务已停止", Toast.LENGTH_SHORT).show()
    }

    private fun updateSubtitleButtonState() {
        val button = findViewById<Button>(R.id.btn_subtitle)
        button.text = if (SubtitleService.isRunning) "停止字幕" else "听障字幕"
        isSubtitleRunning = SubtitleService.isRunning
    }

    private fun toggleBridge() {
        if (isBridgeRunning) {
            stopBridgeService()
        } else {
            startBridgeService()
        }
    }

    private fun startBridgeService() {
        BridgeService.start(this)
        isBridgeRunning = true
        updateBridgeButtonState()
        showWebBridgeInfo()
    }

    private fun stopBridgeService() {
        BridgeService.stop(this)
        isBridgeRunning = false
        updateBridgeButtonState()
        Toast.makeText(this, "跨设备字幕服务已停止", Toast.LENGTH_SHORT).show()
    }

    private fun updateBridgeButtonState() {
        val button = findViewById<Button>(R.id.btn_web_bridge)
        button.text = if (BridgeService.isRunning) "停止跨设备字幕" else "跨设备字幕"
        isBridgeRunning = BridgeService.isRunning
    }

    private fun openVoiceAssist() {
        val intent = Intent(this, VoiceAssistActivity::class.java)
        startActivity(intent)
    }

    private fun openVoiceOutput() {
        val intent = Intent(this, VoiceOutputActivity::class.java)
        startActivity(intent)
    }

    private fun openCommunicationPanel() {
        val intent = Intent(this, CommunicationPanelActivity::class.java)
        startActivity(intent)
    }

    private fun openElderAssist() {
        val intent = Intent(this, MedicationReminderActivity::class.java)
        startActivity(intent)
    }

    private fun showEmergencyCallDialog() {
        val reminder = com.accessible.toolkit.elder.MedicationReminder(this)
        val contacts = reminder.getEmergencyContacts()

        if (contacts.isNotEmpty()) {
            if (contacts.size == 1) {
                val contact = contacts[0]
                AlertDialog.Builder(this)
                    .setTitle("紧急呼叫")
                    .setMessage("确定要拨打 ${contact.name} (${contact.phoneNumber}) 吗？")
                    .setPositiveButton("拨打") { _, _ ->
                        val intent = Intent(Intent.ACTION_CALL).apply {
                            data = Uri.parse("tel:${contact.phoneNumber}")
                        }
                        try {
                            startActivity(intent)
                        } catch (e: SecurityException) {
                            Toast.makeText(this, "需要电话权限", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .setNegativeButton("取消", null)
                    .show()
            } else {
                val names = contacts.map { "${it.name} (${it.phoneNumber})" }.toTypedArray()
                AlertDialog.Builder(this)
                    .setTitle("选择紧急联系人")
                    .setItems(names) { _, which ->
                        val contact = contacts[which]
                        val intent = Intent(Intent.ACTION_CALL).apply {
                            data = Uri.parse("tel:${contact.phoneNumber}")
                        }
                        try {
                            startActivity(intent)
                        } catch (e: SecurityException) {
                            Toast.makeText(this, "需要电话权限", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
        } else {
            Toast.makeText(this, "请先设置紧急联系人", Toast.LENGTH_SHORT).show()
            openElderAssist()
        }
    }

    private fun showWebBridgeInfo() {
        val serverIp = getDeviceIpAddress()
        val wsPort = 8765
        val httpPort = wsPort + 1
        AlertDialog.Builder(this)
            .setTitle("跨设备字幕")
            .setMessage("在电脑或手机浏览器中打开:\nhttp://$serverIp:$httpPort\n\nWebSocket 端口: $wsPort\n\n确保设备在同一局域网")
            .setPositiveButton("确定", null)
            .show()
    }

    private fun getDeviceIpAddress(): String {
        try {
            val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
            val wifiInfo = wifiManager.connectionInfo
            val ipAddress = wifiInfo.ipAddress
            return String.format(
                "%d.%d.%d.%d",
                ipAddress and 0xff,
                ipAddress shr 8 and 0xff,
                ipAddress shr 16 and 0xff,
                ipAddress shr 24 and 0xff
            )
        } catch (e: Exception) {
            return "192.168.1.100"
        }
    }

    private fun startQuickBallService() {
        QuickBallService.start(this)
    }

    override fun onResume() {
        super.onResume()
        updateSubtitleButtonState()
        updateBridgeButtonState()
    }
}
