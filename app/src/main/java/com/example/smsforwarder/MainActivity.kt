package com.example.smsforwarder

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.Toast

/**
 * صفحه‌ی تنظیمات: شماره‌ی مقصد، لیست فرستنده‌های بانکی، و کلید روشن/خاموش.
 * مقادیر در SharedPreferences ذخیره می‌شوند و Receiver از همان‌ها می‌خواند.
 */
class MainActivity : Activity() {

    private lateinit var destEdit: EditText
    private lateinit var sendersEdit: EditText
    private lateinit var enabledSwitch: Switch

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        destEdit = findViewById(R.id.destEdit)
        sendersEdit = findViewById(R.id.sendersEdit)
        enabledSwitch = findViewById(R.id.enabledSwitch)
        val saveBtn: Button = findViewById(R.id.saveBtn)
        val permBtn: Button = findViewById(R.id.permBtn)

        // بارگذاری مقادیر ذخیره‌شده
        destEdit.setText(Prefs.getDestination(this))
        sendersEdit.setText(Prefs.getSenders(this))
        enabledSwitch.isChecked = Prefs.isEnabled(this)

        saveBtn.setOnClickListener {
            Prefs.setDestination(this, destEdit.text.toString().trim())
            Prefs.setSenders(this, sendersEdit.text.toString().trim())
            Prefs.setEnabled(this, enabledSwitch.isChecked)
            Toast.makeText(this, "تنظیمات ذخیره شد", Toast.LENGTH_SHORT).show()
        }

        permBtn.setOnClickListener { requestSmsPermissions() }

        // درخواست دسترسی‌ها هنگام باز شدن
        requestSmsPermissions()
    }

    private fun requestSmsPermissions() {
        val needed = arrayOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.SEND_SMS
        ).filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }

        if (needed.isNotEmpty()) {
            requestPermissions(needed.toTypedArray(), REQ_SMS)
        } else {
            Toast.makeText(this, "دسترسی‌ها از قبل داده شده‌اند", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_SMS) {
            val granted = grantResults.isNotEmpty() &&
                grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            val msg = if (granted) "دسترسی پیامک داده شد" else "بدون دسترسی پیامک، اپ کار نمی‌کند"
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        private const val REQ_SMS = 1
    }
}
