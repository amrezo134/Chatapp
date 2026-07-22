export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    const cors = {
      "Access-Control-Allow-Origin": "*",
      "Access-Control-Allow-Methods": "GET,POST,OPTIONS",
      "Access-Control-Allow-Headers": "Content-Type,Authorization,X-File-Name,X-File-Type",
    };

    if (request.method === "OPTIONS") {
      return new Response(null, { headers: cors });
    }

    // رفع ملف: POST /upload
    if (url.pathname === "/upload" && request.method === "POST") {
      // تحقق بسيط: لازم يبقى فيه Authorization header (Firebase ID Token)
      const authHeader = request.headers.get("Authorization");
      if (!authHeader) {
        return new Response("Unauthorized", { status: 401, headers: cors });
      }

      // ليمت حجم الملف: 20 ميجا كحد أقصى
      const MAX_FILE_SIZE = 20 * 1024 * 1024; // 20 MB بالبايت
      const contentLength = Number(request.headers.get("Content-Length") || 0);
      if (contentLength > MAX_FILE_SIZE) {
        return new Response("حجم الملف أكبر من 20 ميجا", { status: 413, headers: cors });
      }

      const fileName = request.headers.get("X-File-Name") || crypto.randomUUID();
      const fileType = request.headers.get("X-File-Type") || "application/octet-stream";
      const key = `${Date.now()}_${crypto.randomUUID()}_${fileName}`;

      const body = await request.arrayBuffer();

      // تحقق تاني بعد قراءة الملف فعليًا (احتياطي لو الـ Content-Length كان غلط أو مش موجود)
      if (body.byteLength > MAX_FILE_SIZE) {
        return new Response("حجم الملف أكبر من 20 ميجا", { status: 413, headers: cors });
      }

      await env.MEDIA_BUCKET.put(key, body, {
        httpMetadata: { contentType: fileType },
      });

      const publicUrl = `${url.origin}/file/${key}`;
      return new Response(JSON.stringify({ url: publicUrl, key }), {
        headers: { ...cors, "Content-Type": "application/json" },
      });
    }

    // شات الـ AI (Gemini): POST /gemini/chat
    // التطبيق بيبعت هنا بس، والـ Worker هو اللي بيكلم Gemini بالـ API Key السري
    // (مخزن كـ Secret في Cloudflare، مش موجود جوه كود التطبيق خالص)
    if (url.pathname === "/gemini/chat" && request.method === "POST") {
      const authHeader = request.headers.get("Authorization");
      if (!authHeader) {
        return new Response("Unauthorized", { status: 401, headers: cors });
      }

      if (!env.GEMINI_API_KEY) {
        return new Response("GEMINI_API_KEY غير مضبوط على السيرفر", { status: 500, headers: cors });
      }

      let payload;
      try {
        payload = await request.json();
      } catch {
        return new Response("JSON غير صالح", { status: 400, headers: cors });
      }

      // payload.contents لازم يكون بصيغة Gemini: [{ role: "user"|"model", parts: [{ text: "..." }] }]
      const contents = Array.isArray(payload.contents) ? payload.contents : [];
      if (contents.length === 0) {
        return new Response("contents مطلوب", { status: 400, headers: cors });
      }

      const model = payload.model || "gemini-2.0-flash";
      const geminiUrl =
        `https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent?key=${env.GEMINI_API_KEY}`;

      const geminiRes = await fetch(geminiUrl, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          contents,
          systemInstruction: payload.systemInstruction || undefined,
          generationConfig: {
            temperature: 0.8,
            maxOutputTokens: 1024,
          },
        }),
      });

      const data = await geminiRes.json();

      if (!geminiRes.ok) {
        return new Response(JSON.stringify({ error: data }), {
          status: geminiRes.status,
          headers: { ...cors, "Content-Type": "application/json" },
        });
      }

      const reply =
        data?.candidates?.[0]?.content?.parts?.map((p) => p.text || "").join("") || "";

      return new Response(JSON.stringify({ reply }), {
        headers: { ...cors, "Content-Type": "application/json" },
      });
    }

    // جلب ملف: GET /file/xxxx
    if (url.pathname.startsWith("/file/") && request.method === "GET") {
      const key = decodeURIComponent(url.pathname.replace("/file/", ""));
      const object = await env.MEDIA_BUCKET.get(key);
      if (!object) return new Response("Not found", { status: 404, headers: cors });

      const headers = new Headers(cors);
      object.writeHttpMetadata(headers);
      headers.set("etag", object.httpEtag);
      headers.set("Cache-Control", "public, max-age=31536000");
      return new Response(object.body, { headers });
    }

    return new Response("Not found", { status: 404, headers: cors });
  },
};
