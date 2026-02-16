# 🔥 HotFix License System - Cloudflare Worker

Server-side license verification system using Cloudflare Workers + D1 Database.

## 📋 Features

- ✅ **Detailed Logging** - Every request is logged with full details
- ✅ **Double Slash Fix** - Automatically handles `//generate` → `/generate`
- ✅ **License Generation** - Admin panel for creating licenses
- ✅ **Device Binding** - Limit licenses to specific number of devices
- ✅ **Expiration Dates** - Set license expiration
- ✅ **Real-time Verification** - Verify licenses every 10 seconds

---

## 🚀 Deployment Guide

### Step 1: Create D1 Database

```bash
npx wrangler d1 create hotfix_licenses
```

**Output:**
```
✅ Successfully created DB 'hotfix_licenses'!

[[d1_databases]]
binding = "DB"
database_name = "hotfix_licenses"
database_id = "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
```

**IMPORTANT:** Copy the `database_id` from the output!

---

### Step 2: Update wrangler.toml

Open `wrangler.toml` and replace `YOUR_DATABASE_ID_HERE` with the actual database ID you got from Step 1.

```toml
[[d1_databases]]
binding = "DB"
database_name = "hotfix_licenses"
database_id = "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"  # ← Paste here
```

---

### Step 3: Create Database Tables

Run each command separately:

```bash
# Create licenses table
npx wrangler d1 execute hotfix_licenses --command "CREATE TABLE IF NOT EXISTS licenses (id INTEGER PRIMARY KEY AUTOINCREMENT, license_key TEXT UNIQUE NOT NULL, max_devices INTEGER DEFAULT 2, is_active INTEGER DEFAULT 1, expires_at INTEGER, created_at INTEGER NOT NULL);"

# Create devices table
npx wrangler d1 execute hotfix_licenses --command "CREATE TABLE IF NOT EXISTS devices (id INTEGER PRIMARY KEY AUTOINCREMENT, license_id INTEGER NOT NULL, device_id TEXT NOT NULL, device_info TEXT, activated_at INTEGER NOT NULL, last_check INTEGER, FOREIGN KEY (license_id) REFERENCES licenses(id), UNIQUE(license_id, device_id));"

# Create access_logs table
npx wrangler d1 execute hotfix_licenses --command "CREATE TABLE IF NOT EXISTS access_logs (id INTEGER PRIMARY KEY AUTOINCREMENT, license_id INTEGER, device_id TEXT, action TEXT, ip_address TEXT, timestamp INTEGER NOT NULL, FOREIGN KEY (license_id) REFERENCES licenses(id));"

# Create indexes
npx wrangler d1 execute hotfix_licenses --command "CREATE INDEX IF NOT EXISTS idx_license_key ON licenses(license_key);"
npx wrangler d1 execute hotfix_licenses --command "CREATE INDEX IF NOT EXISTS idx_device_license ON devices(license_id);"
npx wrangler d1 execute hotfix_licenses --command "CREATE INDEX IF NOT EXISTS idx_device_id ON devices(device_id);"
```

---

### Step 4: Set Admin Key

```bash
npx wrangler secret put ADMIN_KEY
```

When prompted, enter a strong password (example: `MySecretAdmin2024!`)

**IMPORTANT:** Remember this password! You'll need it to generate licenses.

---

### Step 5: Deploy Worker

```bash
npx wrangler deploy
```

**Output:**
```
✨ Your worker has been published to:
   https://hotfix-license-api.YOUR-SUBDOMAIN.workers.dev
```

---

## 🎯 Testing

### 1. Open Admin Panel

Go to: `https://hotapp.lastofanarchy.workers.dev/`

### 2. Generate License

- **Admin Key**: Enter the password you set in Step 4
- **Max Devices**: 2 (or any number)
- **Expires Days**: 30 (or 0 for no expiration)

Click **Generate License** → You should get a license key!

---

## 📊 View Logs

To see detailed logs and debug issues:

1. Go to Cloudflare Dashboard
2. **Workers & Pages** → Click your worker
3. **Logs** tab → Real-time logs

You'll see:
```
📥 Incoming Request: { method: 'POST', path: '/generate', ... }
🔑 Generate License Request
💾 Inserting into database...
✅ License created successfully
```

---

## 🔍 Troubleshooting

### Error: "Invalid endpoint"
**Cause:** Double slash in path (`//generate`)
**Fix:** ✅ Already fixed in this version! Worker now normalizes paths.

### Error: "Invalid admin key"
**Cause:** ADMIN_KEY not set or wrong password
**Fix:** Run `npx wrangler secret put ADMIN_KEY` again

### Error: "Database error"
**Cause:** D1 database not bound or tables not created
**Fix:**
1. Check `wrangler.toml` has correct `database_id`
2. Run database creation commands (Step 3)

### Error: "Cannot read property 'DB'"
**Cause:** Database binding not configured
**Fix:** Make sure `wrangler.toml` has the `[[d1_databases]]` section

---

## 📱 API Endpoints

### POST `/generate`
Generate new license (admin only)
```json
{
  "admin_key": "your-admin-key",
  "max_devices": 2,
  "expires_days": 30
}
```

### POST `/activate`
Activate license on device
```json
{
  "license_key": "xxx",
  "device_id": "xxx",
  "device_info": "Samsung Galaxy S21"
}
```

### POST `/verify`
Verify active license
```json
{
  "session_token": "xxx",
  "device_id": "xxx"
}
```

### POST `/revoke`
Revoke license (admin only)
```json
{
  "admin_key": "your-admin-key",
  "license_key": "xxx"
}
```

---

## 🔒 Security

- ✅ ADMIN_KEY stored as encrypted secret
- ✅ Session tokens for device authentication
- ✅ CORS enabled for API access
- ✅ Device binding prevents sharing
- ✅ Hardware-based device fingerprinting

---

## 💡 Tips

- Check **Cloudflare Logs** for debugging
- Use `console.log` statements (already added)
- Test with Postman or curl before Android app
- Keep ADMIN_KEY secure (don't share!)

---

**🎉 Ready to use!**
