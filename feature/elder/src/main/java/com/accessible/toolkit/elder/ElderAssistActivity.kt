package com.accessible.toolkit.elder

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.TimePicker
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.Calendar

class ElderAssistActivity : AppCompatActivity() {

    private lateinit var medicationReminder: MedicationReminder
    private lateinit var emergencyCallManager: EmergencyCallManager

    private lateinit var tvEmergencyNumber: TextView
    private lateinit var tvReminderStatus: TextView
    private lateinit var rvReminders: RecyclerView
    private lateinit var btnAddReminder: Button
    private lateinit var btnSetEmergency: Button
    private lateinit var btnTestEmergency: Button

    private val reminders = mutableListOf<ReminderInfo>()
    private lateinit var reminderAdapter: ReminderAdapter

    data class ReminderInfo(
        val hour: Int,
        val minute: Int,
        val medicationName: String
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_elder_assist)

        medicationReminder = MedicationReminder(this)
        emergencyCallManager = EmergencyCallManager(this)

        initViews()
        setupRecyclerView()
        setupListeners()
        loadEmergencyNumber()
        checkCallPermission()
    }

    private fun initViews() {
        tvEmergencyNumber = findViewById(R.id.tv_emergency_number)
        tvReminderStatus = findViewById(R.id.tv_reminder_status)
        rvReminders = findViewById(R.id.rv_reminders)
        btnAddReminder = findViewById(R.id.btn_add_reminder)
        btnSetEmergency = findViewById(R.id.btn_set_emergency)
        btnTestEmergency = findViewById(R.id.btn_test_emergency)
    }

    private fun setupRecyclerView() {
        reminderAdapter = ReminderAdapter(reminders) { position ->
            showDeleteReminderDialog(position)
        }
        rvReminders.layoutManager = LinearLayoutManager(this)
        rvReminders.adapter = reminderAdapter
    }

    private fun setupListeners() {
        btnAddReminder.setOnClickListener {
            showAddReminderDialog()
        }

        btnSetEmergency.setOnClickListener {
            showSetEmergencyDialog()
        }

        btnTestEmergency.setOnClickListener {
            testEmergencyCall()
        }
    }

    private fun showAddReminderDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_reminder, null)
        val etMedicationName = dialogView.findViewById<EditText>(R.id.et_medication_name)
        val timePicker = dialogView.findViewById<TimePicker>(R.id.time_picker)

        AlertDialog.Builder(this)
            .setTitle("添加服药提醒")
            .setView(dialogView)
            .setPositiveButton("确定") { _, _ ->
                val medicationName = etMedicationName.text.toString().trim()
                if (medicationName.isEmpty()) {
                    Toast.makeText(this, "请输入药物名称", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val hour = timePicker.hour
                val minute = timePicker.minute

                medicationReminder.scheduleReminder(hour, minute, medicationName)
                reminders.add(ReminderInfo(hour, minute, medicationName))
                reminderAdapter.notifyItemInserted(reminders.size - 1)
                updateReminderStatus()

                Toast.makeText(this, "已设置 $hour:${String.format("%02d", minute)} 服用 $medicationName", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showDeleteReminderDialog(position: Int) {
        val reminder = reminders[position]
        AlertDialog.Builder(this)
            .setTitle("删除提醒")
            .setMessage("确定删除 ${reminder.medicationName} 的提醒吗？")
            .setPositiveButton("删除") { _, _ ->
                medicationReminder.cancelReminder()
                reminders.removeAt(position)
                reminderAdapter.notifyItemRemoved(position)
                updateReminderStatus()
                Toast.makeText(this, "提醒已删除", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showSetEmergencyDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_set_emergency, null)
        val etPhoneNumber = dialogView.findViewById<EditText>(R.id.et_phone_number)

        val currentNumber = emergencyCallManager.get
        etPhoneNumber.setText(currentNumber)

        AlertDialog.Builder(this)
            .setTitle("设置紧急联系人")
            .setView(dialogView)
            .setPositiveButton("保存") { _, _ ->
                val phoneNumber = etPhoneNumber.text.toString().trim()
                if (phoneNumber.isEmpty()) {
                    Toast.makeText(this, "请输入电话号码", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                saveEmergencyNumber(phoneNumber)
                Toast.makeText(this, "紧急联系人已设置", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun saveEmergencyNumber(number: String) {
        val prefs = getSharedPreferences("emergency", MODE_PRIVATE)
        prefs.edit().putString("phone_number", number).apply()
        tvEmergencyNumber.text = "紧急联系人: $number"
        emergencyCallManager.setPhoneNumber(number)
    }

    private fun loadEmergencyNumber() {
        val prefs = getSharedPreferences("emergency", MODE_PRIVATE)
        val number = prefs.getString("phone_number", null)
        if (number != null) {
            tvEmergencyNumber.text = "紧急联系人: $number"
            emergencyCallManager.setPhoneNumber(number)
        } else {
            tvEmergencyNumber.text = "紧急联系人: 未设置"
        }
    }

    private fun testEmergencyCall() {
        val number = emergencyCallManager.get
        if (number == null) {
            Toast.makeText(this, "请先设置紧急联系人", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("测试紧急呼叫")
            .setMessage("确定要拨打 $number 吗？")
            .setPositiveButton("拨打") { _, _ ->
                emergencyCallManager.makeEmergencyCall()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun checkCallPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CALL_PHONE), 100)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "电话权限已授予", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateReminderStatus() {
        tvReminderStatus.text = if (reminders.isEmpty()) {
            "暂无服药提醒"
        } else {
            "已设置 ${reminders.size} 个提醒"
        }
    }
}