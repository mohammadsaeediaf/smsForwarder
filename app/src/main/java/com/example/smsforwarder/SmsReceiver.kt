package com.example.smsforwarder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
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

        // فقط خودِ متن پیام فوروارد می‌شود (بدون اطلاعات فرستنده).
        val text = body.toString()
        if (text.isBlank()) return
        sendSms(context, destination, text)
    }

    /**
     * آیا فرستنده‌ی پیام با یکی از توکن‌های تنظیم‌شده می‌خواند؟
     * توکن می‌تواند یکی از اینها باشد و هر کدام را تشخیص می‌دهد:
     *  - نام/شناسه‌ی متنی فرستنده (مثل BANKMELI) — تطبیق متنیِ بدون حساسیت به حروف
     *  - یک شماره (مثل 09014527474) — تطبیق هوشمندِ شماره (مهم نیست +98 یا 0 داشته باشد)
     *  - نام یک مخاطب ذخیره‌شده (مثل mom) — با خواندن مخاطبین به شماره‌اش تبدیل می‌شود
     */
    private fun matchesSender(ctx: Context, sender: String): Boolean {
        val senderLower = sender.lowercase()
        val senderDigits = last10(sender)
        // اسم مخاطبی که این شماره با آن ذخیره شده (اگر دسترسی مخاطبین داده شده باشد)
        val senderContactName = lookupNameByNumber(ctx, sender)?.lowercase() ?: ""

        for (raw in Prefs.senderList(ctx)) {
            val token = raw.trim()
            if (token.isEmpty()) continue
            val tokenLower = token.lowercase()

            // ۱) تطبیق متنی مستقیم با شناسه‌ی فرستنده (مثل BANKMELI)
            if (senderLower.contains(tokenLower)) return true

            // ۲) تطبیق با نام مخاطبِ فرستنده (مثلاً کاربر "mom" نوشته و شماره با همین اسم ذخیره است)
            if (senderContactName.isNotEmpty() && senderContactName.contains(tokenLower)) return true

            // ۳) اگر توکن خودش یک شماره است، شماره‌به‌شماره بسنج
            val tokenDigits = last10(token)
            if (tokenDigits.length >= 4 && senderDigits.length >= 4 && tokenDigits == senderDigits) return true

            // ۴) توکن را به‌عنوان نام مخاطب در دفترچه جستجو کن و شماره‌هایش را با فرستنده بسنج
            if (senderDigits.length >= 4) {
                for (num in lookupNumbersByName(ctx, token)) {
                    if (num.length >= 4 && num == senderDigits) return true
                }
            }
        }
        return false
    }

    /** فقط ارقام یک رشته را نگه می‌دارد. */
    private fun digitsOnly(s: String): String = s.filter { it.isDigit() }

    /** ۱۰ رقم آخرِ شماره؛ برای یکی‌سازی فرمت‌های 0xxx / +98xxx / 0098xxx. */
    private fun last10(s: String): String {
        val d = digitsOnly(s)
        return if (d.length > 10) d.takeLast(10) else d
    }

    /** نام مخاطبی که این شماره با آن ذخیره شده را برمی‌گرداند (یا null). */
    private fun lookupNameByNumber(ctx: Context, number: String): String? {
        if (number.isBlank()) return null
        return try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(number)
            )
            ctx.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null, null, null
            )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        } catch (e: Exception) {
            null // دسترسی مخاطبین داده نشده یا خطای دیگر
        }
    }

    /** شماره‌های همه‌ی مخاطبینی که نامشان شامل name است (به‌صورت ۱۰ رقم آخر). */
    private fun lookupNumbersByName(ctx: Context, name: String): List<String> {
        if (name.isBlank()) return emptyList()
        val result = ArrayList<String>()
        try {
            ctx.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " LIKE ?",
                arrayOf("%$name%"),
                null
            )?.use { c ->
                while (c.moveToNext()) {
                    result.add(last10(c.getString(0) ?: ""))
                }
            }
        } catch (e: Exception) {
            // دسترسی مخاطبین داده نشده یا خطای دیگر
        }
        return result
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
