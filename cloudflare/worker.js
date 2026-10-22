const MAX_IMAGE_BYTES = 8 * 1024 * 1024;

export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    if (request.method === "POST" && url.pathname === "/upload") {
      return handleUpload(request, env, url);
    }
    if (request.method === "GET" && url.pathname.startsWith("/i/")) {
      return handleImage(env, url);
    }
    return json({ error: "Not found." }, 404);
  },
};

async function handleUpload(request, env, url) {
  try {
    const token = bearerToken(request);
    if (!token) {
      return json({ error: "Authentication required." }, 401);
    }

    const uid = await verifyFirebaseUser(env, token);
    if (!uid) {
      return json({ error: "Invalid or expired login." }, 401);
    }

    const form = await request.formData();
    const kind = String(form.get("kind") || "");
    const file = form.get("file");
    const chatId = String(form.get("chatId") || "");

    if (kind !== "avatar" && kind !== "chat") {
      return json({ error: "Unknown upload type." }, 400);
    }
    if (!(file instanceof File)) {
      return json({ error: "Missing image file." }, 400);
    }
    if (file.type !== "image/jpeg") {
      return json({ error: "Only JPEG images are accepted." }, 415);
    }
    if (file.size <= 0 || file.size > MAX_IMAGE_BYTES) {
      return json({ error: "Image is too large." }, 413);
    }

    const bytes = new Uint8Array(await file.arrayBuffer());
    if (!isJpeg(bytes)) {
      return json({ error: "Invalid JPEG data." }, 415);
    }

    if (kind === "chat") {
      if (!chatId) {
        return json({ error: "Missing chat id." }, 400);
      }
      if (!(await isChatMember(env, token, chatId, uid))) {
        return json({ error: "You are not a member of this chat." }, 403);
      }
    }

    const prefix = kind === "avatar" ? "avatars" : "chats";
    const key = `${prefix}/${crypto.randomUUID()}.jpg`;
    await env.MEDIA_BUCKET.put(key, bytes, {
      httpMetadata: {
        contentType: "image/jpeg",
        cacheControl: "public, max-age=31536000, immutable",
      },
    });

    return json({ url: `${url.origin}/i/${key}` }, 201);
  } catch (error) {
    console.error("Upload failed", error);
    return json({ error: "Upload failed." }, 500);
  }
}

async function handleImage(env, url) {
  const key = decodeURIComponent(url.pathname.slice("/i/".length));
  if (!key || key.includes("..")) {
    return new Response("Not found", { status: 404 });
  }

  const object = await env.MEDIA_BUCKET.get(key);
  if (!object) {
    return new Response("Not found", { status: 404 });
  }

  const headers = new Headers();
  object.writeHttpMetadata(headers);
  headers.set("etag", object.httpEtag);
  headers.set("cache-control", "public, max-age=31536000, immutable");
  headers.set("x-content-type-options", "nosniff");
  headers.set("access-control-allow-origin", "*");
  return new Response(object.body, { headers });
}

function bearerToken(request) {
  const header = request.headers.get("authorization") || "";
  return header.startsWith("Bearer ") ? header.slice(7).trim() : "";
}

async function verifyFirebaseUser(env, token) {
  const endpoint =
    "https://identitytoolkit.googleapis.com/v1/accounts:lookup?key=" +
    encodeURIComponent(env.FIREBASE_API_KEY);
  const response = await fetch(endpoint, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ idToken: token }),
  });
  if (!response.ok) {
    return null;
  }
  const data = await response.json();
  return data.users?.[0]?.localId || null;
}

async function isChatMember(env, token, chatId, uid) {
  const endpoint =
    `https://firestore.googleapis.com/v1/projects/${env.FIREBASE_PROJECT_ID}` +
    `/databases/(default)/documents/chats/${encodeURIComponent(chatId)}`;
  const response = await fetch(endpoint, {
    headers: { authorization: `Bearer ${token}` },
  });
  if (!response.ok) {
    return false;
  }
  const document = await response.json();
  const members = document.fields?.members?.arrayValue?.values || [];
  return members.some((member) => member.stringValue === uid);
}

function isJpeg(bytes) {
  return bytes.length >= 3 &&
    bytes[0] === 0xff &&
    bytes[1] === 0xd8 &&
    bytes[2] === 0xff;
}

function json(body, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json; charset=utf-8" },
  });
}
