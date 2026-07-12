package com.accessible.toolkit.elder

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class EmergencyCallManager(private val context: Context) {

    companion object {
        private const val CONTACT_PICKER_REQUEST = 1001
        private const val PERMISSION_REQUEST_CONTACTS = 1002
    }

    private val reminder = MedicationReminder(context)

    fun getEmergencyContacts(): List<MedicationReminder.EmergencyContact> {
        return reminder.getEmergencyContacts()
    }

    fun startContactPicker(activity: Activity) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.READ_CONTACTS),
                PERMISSION_REQUEST_CONTACTS
            )
            return
        }

        val intent = Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI)
        activity.startActivityForResult(intent, CONTACT_PICKER_REQUEST)
    }

    fun handleContactPickerResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode != CONTACT_PICKER_REQUEST || resultCode != Activity.RESULT_OK || data == null) {
            return
        }

        val contactUri = data.data ?: return
        val cursor = context.contentResolver.query(contactUri, null, null, null, null) ?: return

        cursor.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                val idIndex = it.getColumnIndex(ContactsContract.Contacts._ID)
                val name = if (nameIndex >= 0) it.getString(nameIndex) else "未知"
                val contactId = if (idIndex >= 0) it.getString(idIndex) else ""

                // Get phone number
                val phoneCursor = context.contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                    "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                    arrayOf(contactId),
                    null
                )

                phoneCursor?.use { pc ->
                    if (pc.moveToFirst()) {
                        val numberIndex = pc.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                        val number = if (numberIndex >= 0) pc.getString(numberIndex) else ""

                        if (number.isNotBlank()) {
                            val contact = MedicationReminder.EmergencyContact(
                                name = name,
                                phoneNumber = number.replace("\\s".toRegex(), "")
                            )
                            reminder.addEmergencyContact(contact)
                        }
                    }
                }
            }
        }
    }

    fun removeEmergencyContact(id: String) {
        reminder.removeEmergencyContact(id)
    }

    fun makeEmergencyCall(phoneNumber: String) {
        val intent = Intent(Intent.ACTION_CALL).apply {
            data = Uri.parse("tel:$phoneNumber")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (e: SecurityException) {
            Toast.makeText(context, "需要电话权限才能拨打电话", Toast.LENGTH_SHORT).show()
        }
    }
}
