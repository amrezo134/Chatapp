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
      const authHeader = request.headers.get("Authorization");
      if (!authHeader) {
        return new Response("Unauthorized", { status: 401, headers: cors });
      }

      const MAX_FILE_SIZE = 20 * 1024 * 1024;
      const contentLength = Number(request.headers.get("Content-Length") || 0);
      if (contentLength > MAX_FILE_SIZE) {
        return new Response("حجم الملف أكبر من 20 ميجا", { status: 413, headers: cors });
      }

      const fileName = request.headers.get("X-File-Name") || crypto.randomUUID();
      const fileType = request.headers.get("X-File-Type") || "application/octet-stream";
      const key = `${Date.now()}_${crypto.randomUUID()}_${fileName}`;

      const body = await request.arrayBuffer();

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

    // شات الـ AI (Cloudflare Workers AI): POST /gemini/chat
    // مفيش أي مزود خارجي ولا API key خالص - كله جوه Cloudflare عن طريق الـ AI binding
    if (url.pathname === "/gemini/chat" && request.method === "POST") {
      const authHeader = request.headers.get("Authorization");
      if (!authHeader) {
        return new Response("Unauthorized", { status: 401, headers: cors });
      }

      if (!env.AI) {
        return new Response("AI binding غير مضبوط على السيرفر", { status: 500, headers: cors });
      }

      let payload;
      try {
        payload = await request.json();
      } catch {
        return new Response("JSON غير صالح", { status: 400, headers: cors });
      }

      const contents = Array.isArray(payload.contents) ? payload.contents : [];
      if (contents.length === 0) {
        return new Response("contents مطلوب", { status: 400, headers: cors });
      }

      const messages = [];

      const systemText = payload.systemInstruction?.parts?.map((p) => p.text || "").join("") || "";
      if (systemText) {
        messages.push({ role: "system", content: systemText });
      }

      for (const c of contents) {
        const role = c.role === "model" ? "assistant" : "user";
        const text = Array.isArray(c.parts) ? c.parts.map((p) => p.text || "").join("") : "";
        messages.push({ role, content: text });
      }

      let aiResponse;
      try {
        aiResponse = await env.AI.run("@cf/meta/llama-3.1-8b-instruct", {
          messages,
          temperature: 0.8,
          max_tokens: 1024,
        });
      } catch (err) {
        return new Response(JSON.stringify({ error: String(err) }), {
          status: 500,
          headers: { ...cors, "Content-Type": "application/json" },
        });
      }

      const reply = aiResponse?.response || "";

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
