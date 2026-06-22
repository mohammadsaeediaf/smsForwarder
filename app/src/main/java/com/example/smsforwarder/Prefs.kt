package com.example.smsforwarder

import android.content.Context

/**
 * نگه‌داری تنظیمات ساده در SharedPreferences:
 * - شماره مقصد
 * - لیست فرستنده‌های بانکی
 * - فعال/غیرفعال بودن فوروارد
 */
object Prefs {
    private const val NAME = "sms_forwarder_prefs"

    // مقادیر پیش‌فرض فرستنده. اینها فقط نمونه‌اند —
    // حتماً داخل اپ، همان چیزی که روی گوشی شما به‌عنوان فرستنده‌ی بانک ملی نشان داده می‌شود را وارد کنید.
    const val DEFAULT_SENDERS = "BANKMELLI,Melli,Bank Melli"

    private fun sp(ctx: Context) = ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun getDestination(ctx: Context): String =
        sp(ctx).getString("destination", "") ?: ""

    fun setDestination(ctx: Context, value: String) =
        sp(ctx).edit().putString("destination", value).apply()

    fun getSenders(ctx: Context): String =
        sp(ctx).getString("senders", DEFAULT_SENDERS) ?: DEFAULT_SENDERS

    fun setSenders(ctx: Context, value: String) =
        sp(ctx).edit().putString("senders", value).apply()

    fun isEnabled(ctx: Context): Boolean =
        sp(ctx).getBoolean("enabled", false)

    fun setEnabled(ctx: Context, value: Boolean) =
        sp(ctx).edit().putBoolean("enabled", value).apply()

    /** لیست فرستنده‌ها را به‌صورت جداشده با کاما یا خط جدید می‌شکند. */
    fun senderList(ctx: Context): List<String> =
        getSenders(ctx)
            .split(",", "\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
}
