package com.creatix.chatapp.config

object AppConfig {
    /**
     * رابط الـ Cloudflare Worker بتاعك (نفس اللي بترفعه بأمر wrangler deploy).
     * غيّره لرابط الـ Worker الحقيقي بتاعك بعد النشر، مثال:
     * "https://chatapp-upload-api.YOUR_SUBDOMAIN.workers.dev"
     */
    const val WORKER_BASE_URL = "https://chatapp-upload-api.amrezo134.workers.dev/"
}
