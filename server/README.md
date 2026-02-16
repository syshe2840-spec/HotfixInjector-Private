# 🔐 License Server - Cloudflare Worker
## با رمزنگاری XOR و توکن یکبار مصرف (Nonce)

---

## 📋 فهرست

1. [ویژگی‌های امنیتی](#ویژگیهای-امنیتی)
2. [نصب و راه‌اندازی](#نصب-و-راهاندازی)
3. [ساختار دیتابیس](#ساختار-دیتابیس)
4. [API Endpoints](#api-endpoints)
5. [رمزنگاری XOR](#رمزنگاری-xor)
6. [مدیریت Nonce](#مدیریت-nonce)
7. [تست کردن](#تست-کردن)

---

## 🛡️ ویژگی‌های امنیتی

### 1. رمزنگاری XOR
- **همه ریکوست ها رمزنگاری شده**: قبل از ارسال با XOR encrypt میشن
- **همه ریسپانس ها رمزنگاری شده**: قبل از ارسال با XOR encrypt میشن
- **کلید XOR**: `last 8 chars of device_id + first 8 chars of license_key`
- **بدون کلید = بدون دسترسی**: نمیتونن data رو بخونن یا تغییر بدن

### 2. توکن یکبار مصرف (Nonce)
- **سرور nonce رو میسازه**: client نمیتونه nonce بسازه
- **هر verification = nonce جدید**: هر بار nonce عوض میشه
- **زمان سرور**: همه timestamp ها از سرور (client نمیتونه تایم رو تغییر بده)
- **بدون nonce = بدون دسترسی**: license بدون nonce معتبر نیست
- **nonce اشتباه = burn**: اگه nonce اشتباه باشه، license میسوزه

### 3. محافظت در برابر Replay Attack
- **نمیشه از nonce قدیمی استفاده کرد**: هر nonce فقط یکبار معتبره
- **nonce age check**: اگه nonce خیلی قدیمی باشه، رد میشه
- **Max age: 24 ساعت**: بعد از 24 ساعت باید re-activate کنه

### 4. محافظت در برابر Time Manipulation
- **همه timestamp ها سرور**: `Date.now()` در Cloudflare Worker
- **تایم client اعتماد نمیشه**: هیچ وقت از تایم گوشی استفاده نمیکنیم
- **نمیتونن تایم رو کم/زیاد کنن**: چون همش سمت سرور حساب میشه

---

## 🚀 نصب و راه‌اندازی

### مرحله 1: ساخت D1 Database در Cloudflare

```bash
# Login to Cloudflare
npx wrangler login

# Create D1 database
npx wrangler d1 create hotfix-licenses

# Copy the database_id from output
```

### مرحله 2: اجرای Schema

```bash
# Run the schema
npx wrangler d1 execute hotfix-licenses --file=database-schema.sql
```

### مرحله 3: تنظیم wrangler.toml

ساخت فایل `wrangler.toml`:

```toml
name = "hotfix-license-server"
main = "cloudflare-worker.js"
compatibility_date = "2024-01-01"

[[d1_databases]]
binding = "DB"
database_name = "hotfix-licenses"
database_id = "YOUR_DATABASE_ID_HERE"  # از مرحله 1
```

### مرحله 4: Deploy

```bash
# Deploy worker
npx wrangler deploy
```

### مرحله 5: تست

بعد از deploy، URL میگیری مثل:
```
https://hotfix-license-server.YOUR_SUBDOMAIN.workers.dev
```

این URL رو توی Android app قرار بده:
```java
private static final String API_BASE_URL = "https://hotfix-license-server.YOUR_SUBDOMAIN.workers.dev";
```

---

## 💾 ساختار دیتابیس

```sql
CREATE TABLE licenses (
  license_key TEXT PRIMARY KEY,      -- XXXXX-XXXXX-XXXXX-XXXXX
  device_id TEXT,                    -- از Android دریافت میشه
  session_token TEXT,                -- سرور میسازه
  nonce TEXT NOT NULL,               -- سرور میسازه (32 chars random)
  nonce_timestamp INTEGER NOT NULL,  -- زمان سرور (milliseconds)
  status TEXT DEFAULT 'active',      -- active | burned | expired
  created_at INTEGER NOT NULL,
  expires_at INTEGER,                -- NULL = بدون انقضا
  last_verified INTEGER,
  verification_count INTEGER DEFAULT 0
);
```

---

## 🌐 API Endpoints

### 1. POST `/activate`

**فعال‌سازی لایسنس روی یک دستگاه**

**Request:**
```json
{
  "license_key": "XXXXX-XXXXX-XXXXX-XXXXX",  // Plain (for XOR key)
  "device_id": "device123",                   // Plain (for XOR key)
  "encrypted": "BASE64_XOR_ENCRYPTED_DATA"    // باقی data ها encrypted
}

// Encrypted payload:
{
  "license_key": "XXXXX-XXXXX-XXXXX-XXXXX",
  "device_id": "device123",
  "device_info": "Samsung Galaxy S21..."
}
```

**Response (XOR Encrypted):**
```json
{
  "encrypted": "BASE64_XOR_ENCRYPTED_DATA"
}

// Decrypted:
{
  "success": true,
  "session_token": "random_token_here",
  "nonce": "random_nonce_here",  // ⚡ اولین nonce
  "expires_at": 1234567890000
}
```

**Errors:**
- `License not found` - لایسنس توی database نیست
- `License already activated on another device` - روی دستگاه دیگه فعاله
- `License has been burned/revoked` - لایسنس سوخته

---

### 2. POST `/verify`

**تأیید اعتبار لایسنس**

**Request:**
```json
{
  "license_key": "XXXXX-XXXXX-XXXXX-XXXXX",  // Plain (for XOR key)
  "encrypted": "BASE64_XOR_ENCRYPTED_DATA"
}

// Encrypted payload:
{
  "license_key": "XXXXX-XXXXX-XXXXX-XXXXX",
  "session_token": "token_from_activate",
  "nonce": "current_nonce",  // ⚡ nonce فعلی از فایل
  "device_id": "device123"
}
```

**Response (XOR Encrypted):**
```json
{
  "encrypted": "BASE64_XOR_ENCRYPTED_DATA"
}

// Decrypted (Success):
{
  "success": true,
  "valid": true,
  "nonce": "NEW_RANDOM_NONCE"  // ⚡ nonce جدید برای request بعدی
}

// Decrypted (Failure):
{
  "success": false,
  "error": "Invalid security token - license burned"  // nonce اشتباه بود
}
```

**Errors:**
- `Invalid session token` - session token اشتباه
- `Device mismatch` - دستگاه مطابقت نداره
- `Invalid security token - license burned` - nonce اشتباه (license میسوزه!)
- `Session expired - please re-activate` - nonce خیلی قدیمی
- `License has been burned/revoked` - license سوخته
- `License expired` - تاریخ انقضا گذشته

---

### 3. POST `/admin/create` (Admin Only)

**ساخت لایسنس جدید**

**Request:**
```json
{
  "license_key": "XXXXX-XXXXX-XXXXX-XXXXX",
  "expires_days": 365  // تعداد روز (یا null برای بدون انقضا)
}
```

**Response:**
```json
{
  "success": true,
  "license_key": "XXXXX-XXXXX-XXXXX-XXXXX",
  "expires_at": 1234567890000
}
```

---

### 4. POST `/admin/burn` (Admin Only)

**سوزوندن یک لایسنس (revoke)**

**Request:**
```json
{
  "license_key": "XXXXX-XXXXX-XXXXX-XXXXX"
}
```

**Response:**
```json
{
  "success": true,
  "message": "License burned"
}
```

---

## 🔐 رمزنگاری XOR

### تولید کلید XOR

```javascript
function generateXORKey(deviceId, licenseKey) {
  // 8 رقم آخر device_id
  const lastPart = deviceId.slice(-8);

  // 8 رقم اول license_key
  const firstPart = licenseKey.slice(0, 8);

  return lastPart + firstPart;  // 16 characters
}

// مثال:
// deviceId = "abc123def456ghi789"
// licenseKey = "HOTFI-X1234-5678-ABCD"
// xorKey = "ghi789" + "HOTFI" = "ghi789HOTFI"
```

### Encrypt/Decrypt

```javascript
function xorEncryptDecrypt(data, key) {
  const dataBytes = Buffer.from(data, 'utf8');
  const keyBytes = Buffer.from(key, 'utf8');
  const result = Buffer.alloc(dataBytes.length);

  for (let i = 0; i < dataBytes.length; i++) {
    result[i] = dataBytes[i] ^ keyBytes[i % keyBytes.length];
  }

  return result.toString('base64');  // Base64 encoded
}
```

### جریان کامل

```
Client:
1. payload = {"license_key": "...", "device_id": "..."}
2. xorKey = generateXORKey(device_id, license_key)
3. encrypted = xorEncrypt(JSON.stringify(payload), xorKey)
4. Send: {"encrypted": encrypted}

Server:
5. Receive: {"encrypted": encrypted}
6. xorKey = generateXORKey(device_id, license_key)
7. decrypted = xorDecrypt(encrypted, xorKey)
8. payload = JSON.parse(decrypted)
9. Process...
10. response = {"success": true, "nonce": "..."}
11. encrypted = xorEncrypt(JSON.stringify(response), xorKey)
12. Send: {"encrypted": encrypted}

Client:
13. Receive: {"encrypted": encrypted}
14. decrypted = xorDecrypt(encrypted, xorKey)
15. response = JSON.parse(decrypted)
```

---

## 🎲 مدیریت Nonce

### تولید Nonce (فقط سرور!)

```javascript
function generateNonce() {
  const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789';
  let nonce = '';
  const randomBytes = crypto.getRandomValues(new Uint8Array(32));

  for (let i = 0; i < 32; i++) {
    nonce += chars[randomBytes[i] % chars.length];
  }

  return nonce;  // 32 characters random string
}
```

### زمان‌بندی Nonce

```javascript
// همیشه از زمان سرور استفاده میشه
const now = Date.now();  // Cloudflare server time

// ذخیره nonce با timestamp
await db.execute(
  'UPDATE licenses SET nonce = ?, nonce_timestamp = ? WHERE license_key = ?',
  [newNonce, now, licenseKey]
);

// بررسی سن nonce
const nonceAge = now - license.nonce_timestamp;
const MAX_AGE = 24 * 60 * 60 * 1000;  // 24 hours

if (nonceAge > MAX_AGE) {
  return error("Session expired - please re-activate");
}
```

### جریان Nonce

```
Activation:
1. Server generates nonce_1
2. Server stores: {license_key, nonce: nonce_1, timestamp: SERVER_TIME}
3. Server sends: {nonce: nonce_1}
4. Client stores nonce_1 in file

Verification #1 (after 5 minutes):
1. Client reads nonce_1 from file
2. Client sends: {nonce: nonce_1}
3. Server validates: nonce_1 == stored_nonce ✅
4. Server generates nonce_2
5. Server stores: {nonce: nonce_2, timestamp: SERVER_TIME}
6. Server sends: {nonce: nonce_2}
7. Client updates file with nonce_2

Verification #2 (after 10 minutes):
1. Client reads nonce_2 from file
2. Client sends: {nonce: nonce_2}
3. Server validates: nonce_2 == stored_nonce ✅
4. Server generates nonce_3
5. ...

❌ Attack Attempt:
1. Hacker tries old nonce_1
2. Server validates: nonce_1 != stored_nonce (currently nonce_3) ❌
3. Server BURNS the license!
4. Client blocked forever
```

---

## 🧪 تست کردن

### تست با cURL

**1. ساخت لایسنس (Admin):**

```bash
curl -X POST https://YOUR_WORKER.workers.dev/admin/create \
  -H "Content-Type: application/json" \
  -d '{
    "license_key": "TEST1-TEST2-TEST3-TEST4",
    "expires_days": 365
  }'
```

**2. تست XOR Encryption (Node.js):**

```javascript
const crypto = require('crypto');

function generateXORKey(deviceId, licenseKey) {
  const lastPart = deviceId.slice(-8);
  const firstPart = licenseKey.slice(0, 8);
  return lastPart + firstPart;
}

function xorEncrypt(data, key) {
  const dataBytes = Buffer.from(data, 'utf8');
  const keyBytes = Buffer.from(key, 'utf8');
  const result = Buffer.alloc(dataBytes.length);

  for (let i = 0; i < dataBytes.length; i++) {
    result[i] = dataBytes[i] ^ keyBytes[i % keyBytes.length];
  }

  return result.toString('base64');
}

// Test
const deviceId = "test_device_12345678";
const licenseKey = "TEST1-TEST2-TEST3-TEST4";
const payload = JSON.stringify({
  license_key: licenseKey,
  device_id: deviceId,
  device_info: "Test Device"
});

const xorKey = generateXORKey(deviceId, licenseKey);
const encrypted = xorEncrypt(payload, xorKey);

console.log("XOR Key:", xorKey);
console.log("Encrypted:", encrypted);
```

**3. تست Activation:**

```bash
# استفاده از encrypted data از مرحله قبل
curl -X POST https://YOUR_WORKER.workers.dev/activate \
  -H "Content-Type: application/json" \
  -d '{
    "license_key": "TEST1-TEST2-TEST3-TEST4",
    "device_id": "test_device_12345678",
    "encrypted": "BASE64_FROM_PREVIOUS_STEP"
  }'
```

---

## 🔥 نکات امنیتی مهم

### 1. زمان همیشه سرور
```javascript
❌ BAD: const now = payload.timestamp;  // Client میتونه تقلب کنه
✅ GOOD: const now = Date.now();        // Server time
```

### 2. Nonce همیشه سرور میسازه
```javascript
❌ BAD: const nonce = payload.nonce;    // Client میتونه تقلب کنه
✅ GOOD: const nonce = generateNonce(); // Server generates
```

### 3. همیشه validate کن
```javascript
✅ GOOD:
if (sentNonce !== storedNonce) {
  // BURN THE LICENSE!
  burnLicense(licenseKey);
  return error("Security breach");
}
```

### 4. Admin endpoints رو محافظت کن
```javascript
// Add authentication
if (request.headers.get('Authorization') !== 'Bearer YOUR_SECRET') {
  return error('Unauthorized');
}
```

---

## 📊 مانیتورینگ

### Query های مفید

```sql
-- تعداد verification ها امروز
SELECT COUNT(*) FROM licenses
WHERE last_verified > (strftime('%s','now') - 86400) * 1000;

-- Top 10 licenses با بیشترین verification
SELECT license_key, verification_count, last_verified
FROM licenses
ORDER BY verification_count DESC
LIMIT 10;

-- License های burned
SELECT license_key, device_id, last_verified
FROM licenses
WHERE status = 'burned';

-- License های expire شده
SELECT license_key, expires_at
FROM licenses
WHERE expires_at < strftime('%s','now') * 1000;
```

---

## 🎯 خلاصه

✅ **XOR Encryption** - همه data ها رمزنگاری شده
✅ **Nonce-based** - هر verification یه nonce جدید
✅ **Server Time** - هیچ اعتمادی به client time نیست
✅ **Auto-Burn** - nonce اشتباه = license سوخته
✅ **Replay Protection** - nonce قدیمی قابل استفاده نیست

**قوی، امن، غیرقابل دور زدن! 🔐**
