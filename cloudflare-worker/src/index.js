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
