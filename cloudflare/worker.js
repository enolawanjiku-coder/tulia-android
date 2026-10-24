const MAX_IMAGE_BYTES = 8 * 1024 * 1024;
const MAX_AUDIO_BYTES = 25 * 1024 * 1024;
const MAX_PREVIEW_CHARS = 200;
const MAX_RECIPIENTS = 512;

const UPLOAD_TYPES = {
  "image/jpeg": { maxBytes: MAX_IMAGE_BYTES, extension: "jpg", sniff: isJpeg },
  "audio/mp4": { maxBytes: MAX_AUDIO_BYTES, extension: "m4a", sniff: isMp4 },
  "audio/aac": { maxBytes: MAX_AUDIO_BYTES, extension: "aac", sniff: isAac },
};

export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    if (request.method === "POST" && url.pathname === "/upload") {
      return handleUpload(request, env, url);
    }
    if (request.method === "POST" && url.pathname === "/notify") {
      return handleNotify(request, env);
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
      return json({ error: "Missing file." }, 400);
    }
    const accepted = UPLOAD_TYPES[file.type];
    if (!accepted) {
      return json({ error: "This file type is not supported." }, 415);
    }
    if (file.size <= 0 || file.size > accepted.maxBytes) {
      return json({ error: "File is too large." }, 413);
    }

    const bytes = new Uint8Array(await file.arrayBuffer());
    if (!accepted.sniff(bytes)) {
      return json({ error: "Invalid file data." }, 415);
    }

    if (kind === "chat") {
      if (!chatId) {
        return json({ error: "Missing chat id." }, 400);
      }
      if (!(await getChatForMember(env, token, chatId, uid))) {
        return json({ error: "You are not a member of this chat." }, 403);
      }
    }

    const prefix = kind === "avatar" ? "avatars" : "chats";
    const extension = accepted.extension;
    const key = extension
      ? `${prefix}/${crypto.randomUUID()}.${extension}`
      : `${prefix}/${crypto.randomUUID()}`;
    await env.MEDIA_BUCKET.put(key, bytes, {
      httpMetadata: {
        contentType: file.type,
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

async function handleNotify(request, env) {
  try {
    const token = bearerToken(request);
    if (!token) {
      return json({ error: "Authentication required." }, 401);
    }
    const uid = await verifyFirebaseUser(env, token);
    if (!uid) {
      return json({ error: "Invalid or expired login." }, 401);
    }

    let body;
    try {
      body = await request.json();
    } catch (error) {
      return json({ error: "Invalid request body." }, 400);
    }
    const chatId = clampString(body?.chatId, 200);
    const type = body?.type === "image" ? "image" : "text";
    const preview = clampString(body?.preview, MAX_PREVIEW_CHARS);
    if (!chatId) {
      return json({ error: "Missing chat id." }, 400);
    }

    const chat = await getChatForMember(env, token, chatId, uid);
    if (!chat) {
      return json({ error: "You are not a member of this chat." }, 403);
    }

    const members = arrayField(chat, "members")
      .filter((member) => member !== uid)
      .slice(0, MAX_RECIPIENTS);
    if (members.length === 0) {
      return json({ sent: 0 });
    }

    let accessToken;
    try {
      accessToken = await getGoogleAccessToken(env);
    } catch (error) {
      console.error("Push is not configured", error);
      return json({ error: "Push notifications are not configured." }, 503);
    }

    const memberNames = mapField(chat, "memberNames");
    const senderDoc = await readDocument(env, accessToken, `users/${uid}`);
    const senderName = clampString(
      senderDoc?.fields?.name?.stringValue || memberNames[uid] || "Tulia",
      100);

    const results = await Promise.allSettled(members.map(async (member) => {
      const profile = await readDocument(env, accessToken, `users/${member}`);
      if (arrayField(profile, "blocked").includes(uid)) {
        return 0;
      }
      const muted = mapField(profile, "mutedChats");
      if (muted[chatId]) {
        return 0;
      }
      const tokenDoc = await readDocument(env, accessToken, `pushTokens/${member}`);
      const tokens = arrayField(tokenDoc, "tokens").slice(0, 20);
      const sends = await Promise.allSettled(tokens.map((deviceToken) =>
        sendFcm(env, accessToken, deviceToken, {
          chatId,
          type,
          preview,
          sender: senderName,
          senderUid: uid,
        })));
      return sends.filter((send) => send.status === "fulfilled" && send.value).length;
    }));

    const sent = results.reduce((total, result) =>
      total + (result.status === "fulfilled" && result.value ? result.value : 0), 0);
    return json({ sent });
  } catch (error) {
    console.error("Notify failed", error);
    return json({ error: "Could not send push notifications." }, 500);
  }
}

async function getChatForMember(env, token, chatId, uid) {
  const endpoint =
    `https://firestore.googleapis.com/v1/projects/${env.FIREBASE_PROJECT_ID}` +
    `/databases/(default)/documents/chats/${encodeURIComponent(chatId)}`;
  const response = await fetch(endpoint, {
    headers: { authorization: `Bearer ${token}` },
  });
  if (!response.ok) {
    return null;
  }
  const document = await response.json();
  const members = arrayField(document, "members");
  return members.includes(uid) ? document : null;
}

async function readDocument(env, accessToken, path) {
  const endpoint =
    `https://firestore.googleapis.com/v1/projects/${env.FIREBASE_PROJECT_ID}` +
    `/databases/(default)/documents/${path}`;
  const response = await fetch(endpoint, {
    headers: { authorization: `Bearer ${accessToken}` },
  });
  if (!response.ok) {
    return null;
  }
  return response.json();
}

async function sendFcm(env, accessToken, deviceToken, data) {
  const projectId = env.FIREBASE_PROJECT_ID;
  const response = await fetch(
    `https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`,
    {
      method: "POST",
      headers: {
        authorization: `Bearer ${accessToken}`,
        "content-type": "application/json",
      },
      body: JSON.stringify({
        message: {
          token: deviceToken,
          data,
          android: { priority: "HIGH" },
        },
      }),
    });
  if (!response.ok && response.status !== 404) {
    console.error("FCM send failed", response.status, await response.text());
  }
  return response.ok;
}

let serviceAccountCache = null;
let accessTokenCache = { token: null, expiresAt: 0 };

async function getGoogleAccessToken(env) {
  const sa = loadServiceAccount(env);
  const now = Math.floor(Date.now() / 1000);
  if (accessTokenCache.token && accessTokenCache.expiresAt - 60 > now) {
    return accessTokenCache.token;
  }

  const header = base64UrlEncodeString(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const claims = base64UrlEncodeString(JSON.stringify({
    iss: sa.client_email,
    scope: [
      "https://www.googleapis.com/auth/firebase.messaging",
      "https://www.googleapis.com/auth/datastore",
    ].join(" "),
    aud: sa.token_uri,
    iat: now,
    exp: now + 3600,
  }));
  const unsigned = `${header}.${claims}`;

  const keyBytes = pemToBytes(sa.private_key);
  const key = await crypto.subtle.importKey(
    "pkcs8",
    keyBytes,
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"]);
  const signature = await crypto.subtle.sign(
    "RSASSA-PKCS1-v1_5",
    key,
    new TextEncoder().encode(unsigned));

  const assertion = `${unsigned}.${base64UrlEncodeBytes(new Uint8Array(signature))}`;
  const tokenResponse = await fetch(sa.token_uri, {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion,
    }),
  });
  if (!tokenResponse.ok) {
    throw new Error(`Token exchange failed (${tokenResponse.status}).`);
  }
  const tokenData = await tokenResponse.json();

  accessTokenCache = {
    token: tokenData.access_token,
    expiresAt: now + (tokenData.expires_in || 3600),
  };
  return accessTokenCache.token;
}

function loadServiceAccount(env) {
  if (serviceAccountCache) {
    return serviceAccountCache;
  }
  if (!env.FCM_SA) {
    throw new Error("Missing FCM_SA secret.");
  }
  const sa = typeof env.FCM_SA === "string" ? JSON.parse(env.FCM_SA) : env.FCM_SA;
  if (!sa.client_email || !sa.private_key || !sa.token_uri) {
    throw new Error("FCM_SA secret is not a valid service account key.");
  }
  serviceAccountCache = sa;
  return sa;
}

function pemToBytes(pem) {
  const body = pem
    .replace(/-----BEGIN PRIVATE KEY-----/, "")
    .replace(/-----END PRIVATE KEY-----/, "")
    .replace(/\s+/g, "");
  const binary = atob(body);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i += 1) {
    bytes[i] = binary.charCodeAt(i);
  }
  return bytes;
}

function base64UrlEncodeString(value) {
  return base64UrlEncodeBytes(new TextEncoder().encode(value));
}

function base64UrlEncodeBytes(bytes) {
  let binary = "";
  for (let i = 0; i < bytes.length; i += 1) {
    binary += String.fromCharCode(bytes[i]);
  }
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function arrayField(document, name) {
  const values = document?.fields?.[name]?.arrayValue?.values || [];
  return values.map((value) => value.stringValue).filter(Boolean);
}

function mapField(document, name) {
  const fields = document?.fields?.[name]?.mapValue?.fields || {};
  const result = {};
  for (const [key, value] of Object.entries(fields)) {
    result[key] = value.stringValue ?? null;
  }
  return result;
}

function clampString(value, maxLength) {
  if (typeof value !== "string") {
    return "";
  }
  return value.length > maxLength ? value.slice(0, maxLength) : value;
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

function isJpeg(bytes) {
  return bytes.length >= 3 &&
    bytes[0] === 0xff &&
    bytes[1] === 0xd8 &&
    bytes[2] === 0xff;
}

function isMp4(bytes) {
  return bytes.length >= 12 &&
    bytes[4] === 0x66 && // f
    bytes[5] === 0x74 && // t
    bytes[6] === 0x79 && // y
    bytes[7] === 0x70;   // p
}

function isAac(bytes) {
  return bytes.length >= 2 &&
    ((bytes[0] === 0xff && (bytes[1] & 0xf6) === 0xf0) ||
      (bytes[0] === 0x41 && bytes[1] === 0x44 && bytes[2] === 0x49 && bytes[3] === 0x46));
}

function json(body, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json; charset=utf-8" },
  });
}
