package com.accessible.toolkit.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
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
import com.accessible.toolkit.elder.ElderAssistActivity
import com.accessible.toolkit.service.AccessibleService
import com.accessible.toolkit.subtitle.SubtitleService
import com.accessible.toolkit.voice.VoiceAssistActivity

class MainActivity : AppCompatActivity() {

    private lateinit var viewModel: MainViewModel
    private lateinit var permissionManager: PermissionManager
    private var isSubtitleRunning = false

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
        setContentView(R.layout.activity_main)

        viewModel = ViewModelProvider(this)[MainViewModel::class.java]
        permissionManager = PermissionManager(this)
        permissionManager.registerLaunchers(requestPermissionLauncher, overlayPermissionLauncher)

        setupButtons()
        updateSubtitleButtonState()
        startQuickBallService()
    }

    private fun setupButtons() {
        findViewById<Button>(R.id.btn_subtitle).setOnClickListener {
            toggleSubtitle()
        }

        findViewById<Button>(R.id.btn_voice_assist).setOnClickListener {
            openVoiceAssist()
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
            showWebBridgeInfo()
        }

        findViewById<Button>(R.id.btn_accessibility_settings).setOnClickListener {
            permissionManager.openAccessibilitySettings()
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

    private fun openVoiceAssist() {
        val intent = Intent(this, VoiceAssistActivity::class.java)
        startActivity(intent)
    }

    private fun openCommunicationPanel() {
        val intent = Intent(this, CommunicationPanelActivity::class.java)
        startActivity(intent)
    }

    private fun openElderAssist() {
        val intent = Intent(this, ElderAssistActivity::class.java)
        startActivity(intent)
    }

    private fun showEmergencyCallDialog() {
        val prefs = getSharedPreferences("emergency", MODE_PRIVATE)
        val emergencyNumber = prefs.getString("phone_number", null)

        if (emergencyNumber != null) {
            AlertDialog.Builder(this)
                .setTitle("紧急呼叫")
                .setMessage("确定要拨打 $emergencyNumber 吗？")
                .setPositiveButton("拨打") { _, _ ->
                    val intent = Intent(Intent.ACTION_CALL).apply {
                        data = Uri.parse("tel:$emergencyNumber")
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
            Toast.makeText(this, "请先设置紧急联系人", Toast.LENGTH_SHORT).show()
            openElderAssist()
        }
    }

    private fun showWebBridgeInfo() {
        val serverIp = "192.168.1.100"
        val port = 8765
        AlertDialog.Builder(this)
            .setTitle("跨设备字幕")
            .setMessage("在电脑浏览器中打开:\nhttp://$serverIp:$port\n\n确保手机和电脑在同一局域网")
            .setPositiveButton("确定", null)
            .show()
    }

    private fun startQuickBallService() {
        QuickBallService.start(this)
    }

    override fun onResume() {
        super.onResume()
        updateSubtitleButtonState()
    }
}