// Cloudflare Worker for HotFix Injector License System
// With detailed logging for debugging

const ENCRYPTION_KEY = 'Kh7Gm2Qp5Rt8Wx4Zv1Nc9Bs6Yf3Dj0L'; // Must match Android app

export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    // Normalize path - remove double slashes
    let path = url.pathname.replace(/\/+/g, '/');

    // Log incoming request
    console.log('📥 Incoming Request:', {
      method: request.method,
      originalPath: url.pathname,
      normalizedPath: path,
      url: request.url,
      headers: Object.fromEntries(request.headers)
    });

    // CORS headers
    const corsHeaders = {
      'Access-Control-Allow-Origin': '*',
      'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
      'Access-Control-Allow-Headers': 'Content-Type, Authorization',
    };

    if (request.method === 'OPTIONS') {
      console.log('✅ CORS preflight request');
      return new Response(null, { headers: corsHeaders });
    }

    try {
      // Admin Panel HTML (Root path or /admin)
      if (request.method === 'GET' && (path === '/' || path === '/admin')) {
        console.log('🎨 Serving Admin Panel HTML');
        return new Response(getAdminHTML(), {
          headers: {
            'Content-Type': 'text/html',
            ...corsHeaders
          }
        });
      }

      // Generate License
      if (request.method === 'POST' && path === '/generate') {
        console.log('🔑 Generate License Request');
        const body = await request.json();
        console.log('📦 Request Body:', body);

        const result = await generateLicense(env, body);
        console.log('✅ Generate Result:', result);

        return jsonResponse(result, corsHeaders);
      }

      // Activate License
      if (request.method === 'POST' && path === '/activate') {
        console.log('🚀 Activate License Request');
        const body = await request.json();
        console.log('📦 Request Body:', {
          license_key: body.license_key,
          device_id: body.device_id?.substring(0, 10) + '...',
          device_info: body.device_info
        });

        const result = await activateLicense(env, body);
        console.log('✅ Activate Result:', result);

        return jsonResponse(result, corsHeaders);
      }

      // Verify License
      if (request.method === 'POST' && path === '/verify') {
        console.log('✔️ Verify License Request');
        const body = await request.json();
        console.log('📦 Request Body:', {
          session_token: body.session_token?.substring(0, 20) + '...',
          device_id: body.device_id?.substring(0, 10) + '...'
        });

        const result = await verifyLicense(env, body);
        console.log('✅ Verify Result:', result);

        return jsonResponse(result, corsHeaders);
      }

      // Revoke License
      if (request.method === 'POST' && path === '/revoke') {
        console.log('🗑️ Revoke License Request');
        const body = await request.json();
        console.log('📦 Request Body:', body);

        const result = await revokeLicense(env, body);
        console.log('✅ Revoke Result:', result);

        return jsonResponse(result, corsHeaders);
      }

      // Unknown endpoint
      console.error('❌ Unknown endpoint:', {
        method: request.method,
        path: path,
        originalPath: url.pathname
      });

      return jsonResponse({
        success: false,
        error: `Invalid endpoint: ${request.method} ${path}`
      }, corsHeaders, 404);

    } catch (error) {
      console.error('❌ Error:', error.message, error.stack);
      return jsonResponse({
        success: false,
        error: error.message
      }, corsHeaders, 500);
    }
  }
};

// Helper: JSON Response
function jsonResponse(data, headers = {}, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      'Content-Type': 'application/json',
      ...headers
    }
  });
}

// Generate License
async function generateLicense(env, body) {
  const { admin_key, max_devices = 2, expires_days = 30 } = body;

  console.log('🔐 Checking admin key...');
  if (!admin_key || admin_key !== env.ADMIN_KEY) {
    console.error('❌ Invalid admin key');
    return { success: false, error: 'Invalid admin key' };
  }

  // Generate random license key
  const licenseKey = generateRandomKey(32);
  const expiresAt = expires_days > 0 ? Date.now() + (expires_days * 24 * 60 * 60 * 1000) : null;

  console.log('💾 Inserting into database...', {
    licenseKey: licenseKey.substring(0, 10) + '...',
    maxDevices: max_devices,
    expiresAt: expiresAt ? new Date(expiresAt).toISOString() : 'Never'
  });

  try {
    await env.DB.prepare(
      'INSERT INTO licenses (license_key, max_devices, expires_at, created_at) VALUES (?, ?, ?, ?)'
    ).bind(licenseKey, max_devices, expiresAt, Date.now()).run();

    console.log('✅ License created successfully');
    return {
      success: true,
      license_key: licenseKey,
      max_devices,
      expires_at: expiresAt,
      expires_date: expiresAt ? new Date(expiresAt).toISOString() : null
    };
  } catch (error) {
    console.error('❌ Database error:', error.message);
    return { success: false, error: 'Database error: ' + error.message };
  }
}

// Activate License
async function activateLicense(env, body) {
  const { license_key, device_id, device_info } = body;

  console.log('🔍 Looking up license...');
  const license = await env.DB.prepare(
    'SELECT * FROM licenses WHERE UPPER(license_key) = UPPER(?)'
  ).bind(license_key).first();

  if (!license) {
    console.error('❌ License not found');
    return { success: false, error: 'Invalid license key' };
  }

  console.log('📋 License found:', {
    id: license.id,
    isActive: license.is_active,
    maxDevices: license.max_devices,
    expiresAt: license.expires_at
  });

  if (!license.is_active) {
    console.error('❌ License is revoked');
    return { success: false, error: 'License has been revoked' };
  }

  if (license.expires_at && Date.now() > license.expires_at) {
    console.error('❌ License expired');
    return { success: false, error: 'License has expired' };
  }

  // Check existing devices
  console.log('🔍 Checking existing devices...');
  const devices = await env.DB.prepare(
    'SELECT * FROM devices WHERE license_id = ?'
  ).bind(license.id).all();

  console.log('📱 Found devices:', devices.results.length);

  // Check if device already exists
  const existingDevice = devices.results.find(d => d.device_id === device_id);
  if (existingDevice) {
    console.log('✅ Device already activated');
    const sessionToken = generateSessionToken(license_key, device_id);
    return {
      success: true,
      session_token: sessionToken,
      expires_at: license.expires_at,
      message: 'Device already activated'
    };
  }

  // Check device limit
  if (devices.results.length >= license.max_devices) {
    console.error('❌ Device limit reached');
    return {
      success: false,
      error: `Maximum ${license.max_devices} devices allowed. Current: ${devices.results.length}`
    };
  }

  // Add new device
  console.log('➕ Adding new device...');
  await env.DB.prepare(
    'INSERT INTO devices (license_id, device_id, device_info, activated_at) VALUES (?, ?, ?, ?)'
  ).bind(license.id, device_id, device_info, Date.now()).run();

  // Generate session token
  const sessionToken = generateSessionToken(license_key, device_id);

  console.log('✅ Device activated successfully');
  return {
    success: true,
    session_token: sessionToken,
    expires_at: license.expires_at,
    message: 'License activated successfully'
  };
}

// Verify License
async function verifyLicense(env, body) {
  const { session_token, device_id } = body;

  // Decode session token
  const tokenData = decodeSessionToken(session_token);
  if (!tokenData) {
    console.error('❌ Invalid session token');
    return { success: false, error: 'Invalid session token', valid: false };
  }

  console.log('🔍 Looking up license for verification...');
  const license = await env.DB.prepare(
    'SELECT * FROM licenses WHERE UPPER(license_key) = UPPER(?)'
  ).bind(tokenData.license_key).first();

  if (!license || !license.is_active) {
    console.error('❌ License not active');
    return { success: false, error: 'License not active', valid: false };
  }

  if (license.expires_at && Date.now() > license.expires_at) {
    console.error('❌ License expired');
    return { success: false, error: 'License expired', valid: false };
  }

  // Check device
  const device = await env.DB.prepare(
    'SELECT * FROM devices WHERE license_id = ? AND device_id = ?'
  ).bind(license.id, device_id).first();

  if (!device) {
    console.error('❌ Device not authorized');
    return { success: false, error: 'Device not authorized', valid: false };
  }

  // Update last check
  await env.DB.prepare(
    'UPDATE devices SET last_check = ? WHERE id = ?'
  ).bind(Date.now(), device.id).run();

  console.log('✅ License verified successfully');
  return { success: true, valid: true };
}

// Revoke License
async function revokeLicense(env, body) {
  const { admin_key, license_key } = body;

  if (!admin_key || admin_key !== env.ADMIN_KEY) {
    console.error('❌ Invalid admin key');
    return { success: false, error: 'Invalid admin key' };
  }

  console.log('🗑️ Revoking license...');
  await env.DB.prepare(
    'UPDATE licenses SET is_active = 0 WHERE UPPER(license_key) = UPPER(?)'
  ).bind(license_key).run();

  console.log('✅ License revoked');
  return { success: true, message: 'License revoked' };
}

// Generate random key
function generateRandomKey(length) {
  const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789';
  let result = '';
  const randomValues = crypto.getRandomValues(new Uint8Array(length));
  for (let i = 0; i < length; i++) {
    result += chars[randomValues[i] % chars.length];
  }
  return result;
}

// Generate session token (HMAC-based)
function generateSessionToken(licenseKey, deviceId) {
  const data = `${licenseKey}:${deviceId}:${Date.now()}`;
  return btoa(data); // Simple encoding (can be enhanced with HMAC)
}

// Decode session token
function decodeSessionToken(token) {
  try {
    const decoded = atob(token);
    const [license_key, device_id, timestamp] = decoded.split(':');
    return { license_key, device_id, timestamp: parseInt(timestamp) };
  } catch {
    return null;
  }
}

// Admin Panel HTML
function getAdminHTML() {
  return `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>🔥 HotFix License Manager</title>
  <style>
    * { margin: 0; padding: 0; box-sizing: border-box; }
    body {
      font-family: 'Segoe UI', system-ui, sans-serif;
      background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
      min-height: 100vh;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 20px;
    }
    .container {
      background: white;
      border-radius: 20px;
      padding: 40px;
      max-width: 500px;
      width: 100%;
      box-shadow: 0 20px 60px rgba(0,0,0,0.3);
    }
    h1 {
      text-align: center;
      color: #667eea;
      margin-bottom: 30px;
      font-size: 2em;
    }
    .form-group {
      margin-bottom: 20px;
    }
    label {
      display: block;
      margin-bottom: 8px;
      color: #333;
      font-weight: 600;
      font-size: 0.95em;
    }
    input, button {
      width: 100%;
      padding: 12px 16px;
      border: 2px solid #e0e0e0;
      border-radius: 10px;
      font-size: 1em;
      transition: all 0.3s;
    }
    input:focus {
      outline: none;
      border-color: #667eea;
      box-shadow: 0 0 0 3px rgba(102, 126, 234, 0.1);
    }
    button {
      background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
      color: white;
      border: none;
      cursor: pointer;
      font-weight: 600;
      margin-top: 10px;
    }
    button:hover {
      transform: translateY(-2px);
      box-shadow: 0 5px 20px rgba(102, 126, 234, 0.4);
    }
    .result {
      margin-top: 20px;
      padding: 15px;
      border-radius: 10px;
      display: none;
    }
    .success {
      background: #d4edda;
      border: 2px solid #c3e6cb;
      color: #155724;
    }
    .error {
      background: #f8d7da;
      border: 2px solid #f5c6cb;
      color: #721c24;
    }
    .license-key {
      background: #f8f9fa;
      padding: 12px;
      border-radius: 8px;
      margin-top: 10px;
      font-family: 'Courier New', monospace;
      word-break: break-all;
      font-size: 0.9em;
      border: 2px dashed #667eea;
    }
    .note {
      font-size: 0.85em;
      color: #666;
      margin-top: 5px;
    }
  </style>
</head>
<body>
  <div class="container">
    <h1>🔥 HotFix License Manager</h1>

    <div class="form-group">
      <label>🔑 Admin Key</label>
      <input type="password" id="adminKey" placeholder="Enter admin key">
    </div>

    <div class="form-group">
      <label>📱 Max Devices</label>
      <input type="number" id="maxDevices" value="2" min="1" max="10">
    </div>

    <div class="form-group">
      <label>📅 Expires In (Days)</label>
      <input type="number" id="expiresDays" value="30" min="0" max="365">
      <div class="note">0 = No expiration</div>
    </div>

    <button onclick="generateLicense()">🚀 Generate License</button>

    <div id="result" class="result"></div>
  </div>

  <script>
    async function generateLicense() {
      const adminKey = document.getElementById('adminKey').value;
      const maxDevices = parseInt(document.getElementById('maxDevices').value);
      const expiresDays = parseInt(document.getElementById('expiresDays').value);

      const resultDiv = document.getElementById('result');

      if (!adminKey) {
        resultDiv.className = 'result error';
        resultDiv.style.display = 'block';
        resultDiv.innerHTML = '❌ Error<br>Please enter admin key';
        return;
      }

      try {
        const response = await fetch('/generate', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ admin_key: adminKey, max_devices: maxDevices, expires_days: expiresDays })
        });

        const data = await response.json();

        if (data.success) {
          resultDiv.className = 'result success';
          resultDiv.innerHTML = \`
            ✅ Success<br>
            <div class="license-key">\${data.license_key}</div>
            <div class="note">Max Devices: \${data.max_devices}</div>
            <div class="note">Expires: \${data.expires_date || 'Never'}</div>
          \`;
        } else {
          resultDiv.className = 'result error';
          resultDiv.innerHTML = '❌ Error<br>' + data.error;
        }
        resultDiv.style.display = 'block';

      } catch (error) {
        resultDiv.className = 'result error';
        resultDiv.style.display = 'block';
        resultDiv.innerHTML = '❌ Error<br>' + error.message;
      }
    }
  </script>
</body>
</html>`;
}
