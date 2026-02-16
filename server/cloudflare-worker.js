// ==================== Cloudflare Worker for License System ====================
// With XOR Encryption and Nonce-based Security
// Database: Cloudflare D1 (SQLite)

// ==================== XOR ENCRYPTION ====================

/**
 * Generate XOR key from device_id and license_key
 * Must match Android client logic:
 * Key = last 8 chars of device_id + first 8 chars of license_key
 */
function generateXORKey(deviceId, licenseKey) {
  // Take last 8 characters of device_id
  const lastPart = deviceId.length >= 8
    ? deviceId.slice(-8)
    : deviceId;

  // Take first 8 characters of license_key
  const firstPart = licenseKey.length >= 8
    ? licenseKey.slice(0, 8)
    : licenseKey;

  return lastPart + firstPart;
}

/**
 * XOR encrypt/decrypt (symmetric operation)
 * Input: string data, string key
 * Output: Base64 encoded result
 */
function xorEncryptDecrypt(data, key) {
  const dataBytes = Buffer.from(data, 'utf8');
  const keyBytes = Buffer.from(key, 'utf8');
  const result = Buffer.alloc(dataBytes.length);

  for (let i = 0; i < dataBytes.length; i++) {
    result[i] = dataBytes[i] ^ keyBytes[i % keyBytes.length];
  }

  return result.toString('base64');
}

/**
 * Decrypt XOR encrypted data (Base64 input)
 */
function xorDecrypt(base64Data, key) {
  const dataBytes = Buffer.from(base64Data, 'base64');
  const keyBytes = Buffer.from(key, 'utf8');
  const result = Buffer.alloc(dataBytes.length);

  for (let i = 0; i < dataBytes.length; i++) {
    result[i] = dataBytes[i] ^ keyBytes[i % keyBytes.length];
  }

  return result.toString('utf8');
}

// ==================== NONCE MANAGEMENT ====================

/**
 * Generate random nonce (server-side only!)
 * 32 characters, cryptographically secure
 */
function generateNonce() {
  const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789';
  let nonce = '';
  const randomBytes = crypto.getRandomValues(new Uint8Array(32));

  for (let i = 0; i < 32; i++) {
    nonce += chars[randomBytes[i] % chars.length];
  }

  return nonce;
}

/**
 * Get current server timestamp (milliseconds)
 * NEVER trust client time!
 */
function getServerTimestamp() {
  return Date.now();
}

// ==================== DATABASE SCHEMA ====================

/*
CREATE TABLE IF NOT EXISTS licenses (
  license_key TEXT PRIMARY KEY,
  device_id TEXT,
  session_token TEXT,
  nonce TEXT NOT NULL,               -- Current nonce (server-generated)
  nonce_timestamp INTEGER NOT NULL,  -- When nonce was generated (server time)
  status TEXT DEFAULT 'active',      -- 'active' | 'burned' | 'expired'
  created_at INTEGER NOT NULL,
  expires_at INTEGER,
  last_verified INTEGER,
  verification_count INTEGER DEFAULT 0
);

CREATE INDEX idx_device_id ON licenses(device_id);
CREATE INDEX idx_session_token ON licenses(session_token);
CREATE INDEX idx_status ON licenses(status);
*/

// ==================== HANDLERS ====================

async function handleActivate(request, env) {
  try {
    // 1. Decrypt request
    const body = await request.json();
    const encryptedData = body.encrypted;

    if (!encryptedData) {
      return jsonResponse({ success: false, error: 'Missing encrypted data' }, 400);
    }

    // For activation, we need license_key from encrypted payload to decrypt
    // So we need to try decrypting with a temporary approach
    // Actually, we need to get license_key first, so let's decrypt in steps

    // Temporary: decode base64 to get rough idea (this is just for demo)
    // In production, you might want to use a master key for initial decrypt
    // OR require license_key to be sent in plain for activation only

    // Let's assume license_key is sent in plain for activation
    const licenseKey = body.license_key; // Plain for activation
    const deviceId = body.device_id;     // Plain for activation

    if (!licenseKey || !deviceId) {
      return jsonResponse({ success: false, error: 'Missing license_key or device_id' }, 400);
    }

    // Now decrypt the actual payload
    const xorKey = generateXORKey(deviceId, licenseKey);
    const decryptedData = xorDecrypt(encryptedData, xorKey);
    const payload = JSON.parse(decryptedData);

    console.log('[ACTIVATE] License:', licenseKey);
    console.log('[ACTIVATE] Device:', deviceId);

    // 2. Validate license key format
    if (!/^[A-Z0-9]{5}-[A-Z0-9]{5}-[A-Z0-9]{5}-[A-Z0-9]{5}$/.test(licenseKey)) {
      return encryptedResponse({ success: false, error: 'Invalid license format' }, xorKey);
    }

    // 3. Check if license exists in database
    const existing = await env.DB.prepare(
      'SELECT * FROM licenses WHERE license_key = ?'
    ).bind(licenseKey).first();

    if (!existing) {
      return encryptedResponse({ success: false, error: 'License not found' }, xorKey);
    }

    // 4. Check if already activated on different device
    if (existing.device_id && existing.device_id !== deviceId) {
      return encryptedResponse({
        success: false,
        error: 'License already activated on another device'
      }, xorKey);
    }

    // 5. Check if burned
    if (existing.status === 'burned') {
      return encryptedResponse({
        success: false,
        error: 'License has been burned/revoked'
      }, xorKey);
    }

    // 6. Generate session token and nonce (server-side!)
    const sessionToken = generateNonce(); // Use as session token
    const nonce = generateNonce();        // Initial nonce
    const now = getServerTimestamp();     // Server time, not client time!

    // 7. Update database
    await env.DB.prepare(`
      UPDATE licenses
      SET device_id = ?,
          session_token = ?,
          nonce = ?,
          nonce_timestamp = ?,
          last_verified = ?,
          verification_count = verification_count + 1
      WHERE license_key = ?
    `).bind(
      deviceId,
      sessionToken,
      nonce,
      now,
      now,
      licenseKey
    ).run();

    // 8. Return encrypted response
    const response = {
      success: true,
      session_token: sessionToken,
      nonce: nonce,  // Initial nonce for client
      expires_at: existing.expires_at || 0
    };

    return encryptedResponse(response, xorKey);

  } catch (error) {
    console.error('[ACTIVATE] Error:', error);
    return jsonResponse({ success: false, error: 'Server error' }, 500);
  }
}

async function handleVerify(request, env) {
  try {
    // 1. Decrypt request
    const body = await request.json();
    const encryptedData = body.encrypted;

    if (!encryptedData) {
      return jsonResponse({ success: false, error: 'Missing encrypted data' }, 400);
    }

    // For verification, we need to get license_key from database using session_token
    // But we need to decrypt first, which needs license_key... chicken and egg!
    // Solution: Store license_key hash or require license_key in plain

    // Let's require license_key in plain for XOR key generation
    const licenseKey = body.license_key; // Plain

    if (!licenseKey) {
      return jsonResponse({ success: false, error: 'Missing license_key' }, 400);
    }

    // Get device_id from database to generate XOR key
    const licenseData = await env.DB.prepare(
      'SELECT * FROM licenses WHERE license_key = ?'
    ).bind(licenseKey).first();

    if (!licenseData) {
      return jsonResponse({ success: false, error: 'License not found' }, 404);
    }

    // Now decrypt payload
    const xorKey = generateXORKey(licenseData.device_id, licenseKey);
    const decryptedData = xorDecrypt(encryptedData, xorKey);
    const payload = JSON.parse(decryptedData);

    const { session_token, nonce, device_id } = payload;

    console.log('[VERIFY] License:', licenseKey);
    console.log('[VERIFY] Device:', device_id);
    console.log('[VERIFY] Nonce received:', nonce ? nonce.substring(0, 8) + '...' : 'NONE');

    // 2. Validate session token
    if (session_token !== licenseData.session_token) {
      return encryptedResponse({
        success: false,
        error: 'Invalid session token'
      }, xorKey);
    }

    // 3. Validate device
    if (device_id !== licenseData.device_id) {
      return encryptedResponse({
        success: false,
        error: 'Device mismatch'
      }, xorKey);
    }

    // 4. CRITICAL: Validate nonce (server-side validation!)
    if (!nonce || nonce !== licenseData.nonce) {
      console.error('[VERIFY] NONCE MISMATCH!');
      console.error('[VERIFY] Received:', nonce);
      console.error('[VERIFY] Expected:', licenseData.nonce);

      // Burn the license for security breach attempt
      await env.DB.prepare(
        'UPDATE licenses SET status = ? WHERE license_key = ?'
      ).bind('burned', licenseKey).run();

      return encryptedResponse({
        success: false,
        error: 'Invalid security token - license burned'
      }, xorKey);
    }

    // 5. Check nonce age (optional: prevent replay attacks with old nonces)
    const now = getServerTimestamp();
    const nonceAge = now - licenseData.nonce_timestamp;
    const MAX_NONCE_AGE = 24 * 60 * 60 * 1000; // 24 hours

    if (nonceAge > MAX_NONCE_AGE) {
      console.warn('[VERIFY] Nonce too old:', nonceAge / 1000 / 60, 'minutes');
      // Don't burn, just require re-activation
      return encryptedResponse({
        success: false,
        error: 'Session expired - please re-activate'
      }, xorKey);
    }

    // 6. Check if burned
    if (licenseData.status === 'burned') {
      return encryptedResponse({
        success: false,
        error: 'License has been burned/revoked'
      }, xorKey);
    }

    // 7. Check expiration
    if (licenseData.expires_at && now > licenseData.expires_at) {
      return encryptedResponse({
        success: false,
        valid: false,
        error: 'License expired'
      }, xorKey);
    }

    // 8. Generate NEW nonce (server-side, based on server time!)
    const newNonce = generateNonce();

    // 9. Update database with new nonce
    await env.DB.prepare(`
      UPDATE licenses
      SET nonce = ?,
          nonce_timestamp = ?,
          last_verified = ?,
          verification_count = verification_count + 1
      WHERE license_key = ?
    `).bind(
      newNonce,
      now,
      now,
      licenseKey
    ).run();

    console.log('[VERIFY] ✅ SUCCESS - New nonce generated');

    // 10. Return encrypted response with NEW nonce
    const response = {
      success: true,
      valid: true,
      nonce: newNonce  // NEW nonce for next request
    };

    return encryptedResponse(response, xorKey);

  } catch (error) {
    console.error('[VERIFY] Error:', error);
    return jsonResponse({ success: false, error: 'Server error' }, 500);
  }
}

// ==================== ADMIN ENDPOINTS ====================

async function handleCreateLicense(request, env) {
  try {
    const body = await request.json();
    const { license_key, expires_days } = body;

    if (!license_key) {
      return jsonResponse({ success: false, error: 'Missing license_key' }, 400);
    }

    // Generate initial nonce (server-side)
    const initialNonce = generateNonce();
    const now = getServerTimestamp();
    const expiresAt = expires_days ? now + (expires_days * 24 * 60 * 60 * 1000) : null;

    await env.DB.prepare(`
      INSERT INTO licenses (
        license_key,
        nonce,
        nonce_timestamp,
        status,
        created_at,
        expires_at
      )
      VALUES (?, ?, ?, 'active', ?, ?)
    `).bind(
      license_key,
      initialNonce,
      now,
      now,
      expiresAt
    ).run();

    return jsonResponse({
      success: true,
      license_key,
      expires_at: expiresAt
    });

  } catch (error) {
    console.error('[CREATE] Error:', error);
    return jsonResponse({ success: false, error: 'Server error' }, 500);
  }
}

async function handleBurnLicense(request, env) {
  try {
    const body = await request.json();
    const { license_key } = body;

    if (!license_key) {
      return jsonResponse({ success: false, error: 'Missing license_key' }, 400);
    }

    await env.DB.prepare(
      'UPDATE licenses SET status = ? WHERE license_key = ?'
    ).bind('burned', license_key).run();

    return jsonResponse({ success: true, message: 'License burned' });

  } catch (error) {
    console.error('[BURN] Error:', error);
    return jsonResponse({ success: false, error: 'Server error' }, 500);
  }
}

// ==================== HELPERS ====================

function jsonResponse(data, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: { 'Content-Type': 'application/json' }
  });
}

function encryptedResponse(data, xorKey) {
  const jsonStr = JSON.stringify(data);
  const encrypted = xorEncryptDecrypt(jsonStr, xorKey);

  return new Response(JSON.stringify({ encrypted }), {
    status: 200,
    headers: { 'Content-Type': 'application/json' }
  });
}

// ==================== MAIN HANDLER ====================

export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    // CORS headers
    const corsHeaders = {
      'Access-Control-Allow-Origin': '*',
      'Access-Control-Allow-Methods': 'POST, GET, OPTIONS',
      'Access-Control-Allow-Headers': 'Content-Type',
    };

    // Handle CORS preflight
    if (request.method === 'OPTIONS') {
      return new Response(null, { headers: corsHeaders });
    }

    try {
      // Route handlers
      if (url.pathname === '/activate' && request.method === 'POST') {
        const response = await handleActivate(request, env);
        Object.keys(corsHeaders).forEach(key => response.headers.set(key, corsHeaders[key]));
        return response;
      }

      if (url.pathname === '/verify' && request.method === 'POST') {
        const response = await handleVerify(request, env);
        Object.keys(corsHeaders).forEach(key => response.headers.set(key, corsHeaders[key]));
        return response;
      }

      // Admin endpoints (should add authentication!)
      if (url.pathname === '/admin/create' && request.method === 'POST') {
        const response = await handleCreateLicense(request, env);
        Object.keys(corsHeaders).forEach(key => response.headers.set(key, corsHeaders[key]));
        return response;
      }

      if (url.pathname === '/admin/burn' && request.method === 'POST') {
        const response = await handleBurnLicense(request, env);
        Object.keys(corsHeaders).forEach(key => response.headers.set(key, corsHeaders[key]));
        return response;
      }

      return jsonResponse({ error: 'Not found' }, 404);

    } catch (error) {
      console.error('Worker error:', error);
      return jsonResponse({ error: 'Internal server error' }, 500);
    }
  }
};
