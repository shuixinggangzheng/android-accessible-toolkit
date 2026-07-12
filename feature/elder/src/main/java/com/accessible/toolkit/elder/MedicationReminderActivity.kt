package com.accessible.toolkit.elder

import android.Manifest
import android.app.Activity
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import java.util.*

class MedicationReminderActivity : AppCompatActivity() {

    private lateinit var reminder: MedicationReminder
    private lateinit var emergencyManager: EmergencyCallManager
    private lateinit var adapter: ReminderAdapter
    private lateinit var contactAdapter: EmergencyContactAdapter

    private val tempTimes = mutableListOf<MedicationReminder.MedTime>()
    private val tempRepeatDays = mutableListOf<Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_medication_reminder)

        reminder = MedicationReminder(this)
        emergencyManager = EmergencyCallManager(this)

        setupToolbar()
        setupReminderList()
        setupContactList()
        setupButtons()
    }

    private fun setupToolbar() {
        findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar).apply {
            title = "服药提醒与紧急呼叫"
            setSupportActionBar(this)
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            setNavigationOnClickListener { finish() }
        }
    }

    private fun setupReminderList() {
        adapter = ReminderAdapter(
            reminders = reminder.getReminders().toMutableList(),
            onToggle = { id, enabled ->
                reminder.toggleReminder(id, enabled)
                adapter.updateReminders(reminder.getReminders())
            },
            onDelete = { id ->
                AlertDialog.Builder(this)
                    .setTitle("删除提醒")
                    .setMessage("确定要删除这个提醒吗？")
                    .setPositiveButton("删除") { _, _ ->
                        reminder.removeReminder(id)
                        adapter.updateReminders(reminder.getReminders())
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
        )

        findViewById<RecyclerView>(R.id.rv_reminders).apply {
            layoutManager = LinearLayoutManager(this@MedicationReminderActivity)
            this.adapter = this@MedicationReminderActivity.adapter
        }

        updateReminderStatus()
    }

    private fun setupContactList() {
        contactAdapter = EmergencyContactAdapter(
            contacts = emergencyManager.getEmergencyContacts().toMutableList(),
            onCall = { contact ->
                AlertDialog.Builder(this)
                    .setTitle("拨打紧急电话")
                    .setMessage("确定要拨打 ${contact.name} (${contact.phoneNumber}) 吗？")
                    .setPositiveButton("拨打") { _, _ ->
                        emergencyManager.makeEmergencyCall(contact.phoneNumber)
                    }
                    .setNegativeButton("取消", null)
                    .show()
            },
            onDelete = { id ->
                AlertDialog.Builder(this)
                    .setTitle("删除联系人")
                    .setMessage("确定要删除这个紧急联系人吗？")
                    .setPositiveButton("删除") { _, _ ->
                        emergencyManager.removeEmergencyContact(id)
                        contactAdapter.updateContacts(emergencyManager.getEmergencyContacts())
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
        )

        findViewById<RecyclerView>(R.id.rv_contacts).apply {
            layoutManager = LinearLayoutManager(this@MedicationReminderActivity)
            this.adapter = this@MedicationReminderActivity.contactAdapter
        }

        updateContactStatus()
    }

    private fun setupButtons() {
        findViewById<Button>(R.id.btn_add_reminder).setOnClickListener {
            showAddReminderDialog()
        }

        findViewById<Button>(R.id.btn_add_contact).setOnClickListener {
            emergencyManager.startContactPicker(this)
        }
    }

    private fun showAddReminderDialog() {
        tempTimes.clear()
        tempRepeatDays.clear()

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_reminder, null)
        val etName = dialogView.findViewById<EditText>(R.id.et_medication_name)
        val etDosage = dialogView.findViewById<EditText>(R.id.et_dosage)
        val spinnerRepeat = dialogView.findViewById<Spinner>(R.id.spinner_repeat_mode)
        val chipGroupDays = dialogView.findViewById<ChipGroup>(R.id.chip_group_days)
        val tvTimes = dialogView.findViewById<TextView>(R.id.tv_selected_times)
        val btnAddTime = dialogView.findViewById<Button>(R.id.btn_add_time)

        // Setup repeat mode spinner
        val repeatModes = arrayOf("每天", "每周", "仅一次")
        spinnerRepeat.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, repeatModes)

        // Show/hide day chips based on repeat mode
        spinnerRepeat.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                chipGroupDays.visibility = if (position == 1) View.VISIBLE else View.GONE
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Setup day chips
        val days = arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
        for ((index, day) in days.withIndex()) {
            val chip = Chip(this).apply {
                text = day
                isCheckable = true
                setOnCheckedChangeListener { _, isChecked ->
                    val dayNum = index + 1
                    if (isChecked) {
                        tempRepeatDays.add(dayNum)
                    } else {
                        tempRepeatDays.remove(dayNum)
                    }
                }
            }
            chipGroupDays.addView(chip)
        }

        // Add time button
        btnAddTime.setOnClickListener {
            showTimePicker { hour, minute ->
                tempTimes.add(MedicationReminder.MedTime(hour, minute))
                updateTimesDisplay(tvTimes)
            }
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("添加服药提醒")
            .setView(dialogView)
            .setPositiveButton("添加") { _, _ ->
                val name = etName.text.toString().trim()
                val dosage = etDosage.text.toString().trim()
                val repeatMode = when (spinnerRepeat.selectedItemPosition) {
                    0 -> MedicationReminder.RepeatMode.DAILY
                    1 -> MedicationReminder.RepeatMode.WEEKLY
                    2 -> MedicationReminder.RepeatMode.ONCE
                    else -> MedicationReminder.RepeatMode.DAILY
                }

                if (name.isBlank()) {
                    Toast.makeText(this, "请输入药物名称", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (tempTimes.isEmpty()) {
                    Toast.makeText(this, "请添加至少一个提醒时间", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val newReminder = MedicationReminder.MedicationReminderItem(
                    name = name,
                    times = tempTimes.toList(),
                    dosage = dosage.ifBlank { "按医嘱服用" },
                    repeatMode = repeatMode,
                    repeatDays = tempRepeatDays.toList()
                )

                reminder.addReminder(newReminder)
                adapter.updateReminders(reminder.getReminders())
                updateReminderStatus()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showTimePicker(onTimeSet: (Int, Int) -> Unit) {
        val calendar = Calendar.getInstance()
        TimePickerDialog(
            this,
            { _, hourOfDay, minute -> onTimeSet(hourOfDay, minute) },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            true
        ).show()
    }

    private fun updateTimesDisplay(textView: TextView) {
        if (tempTimes.isEmpty()) {
            textView.text = "未设置提醒时间"
        } else {
            textView.text = tempTimes.joinToString("、") { it.toDisplayString() }
        }
    }

    private fun updateReminderStatus() {
        val reminders = reminder.getReminders()
        val statusText = when {
            reminders.isEmpty() -> "暂无服药提醒"
            else -> "共 ${reminders.size} 个提醒"
        }
        findViewById<TextView>(R.id.tv_reminder_status)?.text = statusText
    }

    private fun updateContactStatus() {
        val contacts = emergencyManager.getEmergencyContacts()
        val statusText = when {
            contacts.isEmpty() -> "未设置紧急联系人"
            else -> "已设置 ${contacts.size} 个联系人"
        }
        findViewById<TextView>(R.id.tv_contact_status)?.text = statusText
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        emergencyManager.handleContactPickerResult(requestCode, resultCode, data)
        contactAdapter.updateContacts(emergencyManager.getEmergencyContacts())
        updateContactStatus()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1002 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            emergencyManager.startContactPicker(this)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        reminder.destroy()
    }
}
