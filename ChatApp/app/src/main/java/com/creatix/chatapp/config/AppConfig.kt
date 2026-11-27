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
     * مفعّلة بطلب صاحب التطبيق (27 نوفمبر 2026) — المسح إجباري عشان السيرفر ميتمليش.
     * الأمان: المستلم بيأكد الاستلام بعد ما الكتابة في الكاش المحلي تنجح بس، وأي فشل
     * بيخلي الرسالة على السيرفر. لو قواعد Firestore المحدّثة لسه مش متنشرة
     * (firebase deploy --only firestore:rules) المسح هيفشل بهدوء والرسايل هتفضل
     * على السيرفر لحد ما القواعد تتنشر. حماية الرسايل من ضياع التليفون جاية من
     * باك اب Google Drive (اللي التطبيق بيقترحه على المستخدم من أول دخول).
     * الشاتات الخاصة بس اللي بتتمسح (الجروبات العامة والمخصصة مش بتتأثر).
     */
    const val DELETE_AFTER_DELIVERY = true
}
