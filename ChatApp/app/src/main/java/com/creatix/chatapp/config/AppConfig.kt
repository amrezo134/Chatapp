package com.creatix.chatapp.config

object AppConfig {
    /**
     * رابط الـ Cloudflare Worker بتاعك (نفس اللي بترفعه بأمر wrangler deploy).
     * غيّره لرابط الـ Worker الحقيقي بتاعك بعد النشر، مثال:
     * "https://chatapp-upload-api.YOUR_SUBDOMAIN.workers.dev"
     */
    const val WORKER_BASE_URL = "https://chatapp-upload-api.amrezo134.workers.dev"

    /**
     * حذف الرسالة من السيرفر بعد ما توصل للمستلم وتتخزن في الكاش المحلي (Room) على تليفونه.
     * معطّلة افتراضيًا — ولأمان المستخدم، الوضع ده مش بيأثر على أي رسالة إلا لو الطرفين
     * مفعّلين الباك اب (backupEnabled في مستند اليوزر): يعني محدش هيخسر رسايله لو تليفونه
     * ضاع أو اتفرمت، لأن نسخته الاحتياطية هي اللي بترجعها. اللي مش مفعّل الباك اب،
     * رسايله بتفضل على السيرفر زي ما هي.
     * لما تتفعّل: الشاتات الخاصة بس اللي بتتمسح (الجروبات العامة والمخصصة مش بتتأثر).
     */
    const val DELETE_AFTER_DELIVERY = false
}
