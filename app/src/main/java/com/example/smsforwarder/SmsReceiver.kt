package com.example.smsforwarder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsManager
import android.util.Log

/**
 * هر پیامک ورودی را می‌گیرد، اگر فرستنده‌اش با لیست تنظیم‌شده بخواند،
 * متن آن را به شماره‌ی مقصد فوروارد می‌کند.
 *
 * این Receiver در AndroidManifest ثبت شده، پس حتی وقتی اپ بسته است هم
 * با رسیدن پیامک توسط سیستم بیدار می‌شود (به شرطی که اپ حداقل یک‌بار باز شده باشد
 * و کاربر آن را Force Stop نکرده باشد).
 */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        // اگر فوروارد خاموش است یا مقصد خالی است، کاری نکن.
        if (!Prefs.isEnabled(context)) return
        val destination = Prefs.getDestination(context)
        if (destination.isBlank()) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (messages.isEmpty()) return

        val sender = messages[0].originatingAddress ?: ""
        // پیام‌های بلند چند تکه می‌شوند؛ همه‌ی تکه‌ها را به هم بچسبان.
        val body = StringBuilder()
        for (m in messages) body.append(m.messageBody ?: "")

        if (!matchesSender(context, sender)) return

        val text = body.toString()
        if (text.isBlank()) return
        sendSms(context, destination, text)
    }

    /** آیا فرستنده‌ی پیام با یکی از توکن‌های تنظیم‌شده می‌خواند؟ (بدون حساسیت به بزرگ/کوچک بودن حروف) */
    private fun matchesSender(ctx: Context, sender: String): Boolean {
        val s = sender.lowercase()
        return Prefs.senderList(ctx).any { token ->
            val t = token.lowercase()
            t.isNotEmpty() && s.contains(t)
        }
    }

    private fun sendSms(ctx: Context, destination: String, text: String) {
        try {
            val sm: SmsManager =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                    ctx.getSystemService(SmsManager::class.java)
                else
                    @Suppress("DEPRECATION") SmsManager.getDefault()

            // متن ممکن است طولانی‌تر از یک پیامک باشد؛ تقسیمش کن.
            val parts = sm.divideMessage(text)
            sm.sendMultipartTextMessage(destination, null, parts, null, null)
            Log.i(TAG, "پیام به $destination فوروارد شد")
        } catch (e: Exception) {
            Log.e(TAG, "ارسال پیامک ناموفق بود", e)
        }
    }

    companion object {
        private const val TAG = "SmsForwarder"
    }
}
