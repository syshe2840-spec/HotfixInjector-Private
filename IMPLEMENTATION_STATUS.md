# 📊 وضعیت پیاده سازی - License System

## ✅ پیاده سازی شده (Completed):

### 1. XOR Encryption
**چطوری کار میکنه:**

```
┌─────────────────────────────────────────────────────────────┐
│ CLIENT (Android App)                                        │
├─────────────────────────────────────────────────────────────┤
│ 1. تولید کلید XOR:                                         │
│    deviceId = "abc123def456ghi789"                          │
│    licenseKey = "HOTFI-X1234-5678-ABCD"                     │
│    xorKey = last8(deviceId) + first8(licenseKey)            │
│    xorKey = "ghi789" + "HOTFI" = "ghi789HOTFI" (16 chars)   │
│                                                             │
│ 2. Encrypt Request:                                        │
│    payload = {"license_key": "...", "device_id": "..."}    │
│    for each byte:                                          │
│        encrypted[i] = payload[i] XOR key[i % keyLength]    │
│    base64 = Base64.encode(encrypted)                       │
│                                                             │
│ 3. Send:                                                   │
│    POST /activate                                          │
│    {                                                       │
│      "license_key": "HOTFI...",  // Plain (for XOR key)   │
│      "device_id": "abc...",      // Plain (for XOR key)   │
│      "encrypted": "BASE64..."    // XOR encrypted data    │
│    }                                                       │
└─────────────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────────────┐
│ SERVER (Cloudflare Worker)                                  │
├─────────────────────────────────────────────────────────────┤
│ 1. دریافت Request:                                          │
│    license_key = request.body.license_key                   │
│    device_id = request.body.device_id                       │
│    encrypted = request.body.encrypted                       │
│                                                             │
│ 2. تولید همون کلید XOR:                                    │
│    xorKey = device_id.slice(-8) + license_key.slice(0, 8)   │
│    xorKey = "ghi789HOTFI"  (same as client!)                │
│                                                             │
│ 3. Decrypt:                                                │
│    for each byte:                                          │
│        decrypted[i] = encrypted[i] XOR key[i % keyLength]   │
│    payload = JSON.parse(decrypted)                         │
│                                                             │
│ 4. پردازش و ایجاد پاسخ:                                    │
│    response = {success: true, nonce: "..."}                │
│                                                             │
│ 5. Encrypt Response:                                       │
│    encryptedResponse = xorEncrypt(response, xorKey)        │
│    send: {"encrypted": "BASE64_ENCRYPTED_RESPONSE"}        │
└─────────────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────────────┐
│ CLIENT (Android App)                                        │
├─────────────────────────────────────────────────────────────┤
│ Decrypt Response:                                          │
│    encryptedResponse = response.encrypted                  │
│    decrypted = xorDecrypt(encryptedResponse, xorKey)       │
│    data = JSON.parse(decrypted)                            │
│    // حالا میتونه از nonce و session_token استفاده کنه    │
└─────────────────────────────────────────────────────────────┘
```

**نکات مهم XOR:**
- ✅ کلید XOR از `device_id` + `license_key` ساخته میشه
- ✅ همیشه 16 کاراکتر طول داره
- ✅ Client و Server باید از همون الگوریتم استفاده کنن
- ✅ بدون کلید درست = garbage data
- ✅ سریع و امن برای این use case

---

### 2. Server-Time Based Expiration
**چطوری کار میکنه:**

```
Activation:
- Server sends: server_time = 2024-01-01 12:00:00
- Client saves: last_server_time = 12:00:00, last_check_client = NOW

After 5 minutes (verification):
- Client calculates:
    elapsed = NOW - last_check_client = 5 minutes
    estimated_server_time = last_server_time + elapsed
    estimated_server_time = 12:00:00 + 5min = 12:05:00

- Check expiration:
    if (estimated_server_time > expires_at) → EXPIRED!

- Server sends new: server_time = 12:05:00 (actual server time)
- Client updates: last_server_time = 12:05:00
```

**چرا Client نمیتونه تقلب کنه:**
- ✅ زمان واقعی از سرور میاد
- ✅ هر 5 دقیقه sync میشه
- ✅ تغییر زمان گوشی فقط `elapsed` رو تغییر میده
- ✅ اگه جلو ببره → زودتر expire میشه!
- ✅ اگه عقب ببره → بعد از sync بعدی، زمان واقعی میاد

---

### 3. Nonce (One-Time Token)
**وضعیت فعلی:**
- ✅ سرور nonce رو میسازه (32 chars random)
- ✅ client فقط ذخیره و ارسال میکنه
- ✅ هر verification = nonce جدید
- ✅ nonce اشتباه = license میسوزه
- ✅ نمیشه از nonce قدیمی استفاده کرد

**Nonce Flow:**
```
Activation:
└─ Server generates nonce_1
└─ Client stores nonce_1

Verification #1:
└─ Client sends nonce_1
└─ Server validates nonce_1 == stored ✅
└─ Server generates nonce_2
└─ Client stores nonce_2

Verification #2:
└─ Client sends nonce_2
└─ Server validates nonce_2 == stored ✅
└─ Server generates nonce_3
...
```

---

## 🚧 در حال پیاده سازی (In Progress):

### 4. App Signature Validation
**هدف:**
- سرور فقط به request های با امضای خاص پاسخ بده
- امضای اپ: `A40DA80A59D170CAA950CF15C18C454D47A39B26989D8B640ECD745BA71BF5DC`

**آنچه انجام شده:**
- ✅ `APP_SIGNATURE` constant اضافه شد
- ✅ در `activate()` و `verify()` به payload اضافه میشه
- ❌ سرور هنوز چک نمیکنه (باید پیاده سازی بشه)

**آنچه باید انجام بشه:**
```javascript
// در Cloudflare Worker
const ALLOWED_SIGNATURES = [
  "A40DA80A59D170CAA950CF15C18C454D47A39B26989D8B640ECD745BA71BF5DC"
];

function validateAppSignature(signature) {
  if (!ALLOWED_SIGNATURES.includes(signature)) {
    return false; // Reject!
  }
  return true;
}

// در handleActivate() و handleVerify():
const app_signature = payload.app_signature;
if (!validateAppSignature(app_signature)) {
  return jsonResponse({ success: false, error: 'Invalid app signature' }, 403);
}
```

---

### 5. Comprehensive Device Info
**هدف:**
- جمع آوری اطلاعات کامل دستگاه
- ذخیره در database سرور

**آنچه انجام شده:**
- ✅ `getDeviceInfo()` بازنویسی شد
- ✅ حالا JSONObject برمیگردونه با:
  - manufacturer, model, brand, device, product
  - Android version, SDK, Android ID
  - Build info (ID, time, type, tags, fingerprint)
  - Bootloader, security patch, CPU ABI
  - و...

**آنچه باید انجام بشه:**
1. آپدیت database schema:
```sql
ALTER TABLE licenses ADD COLUMN device_info TEXT;
```

2. در Cloudflare Worker:
```javascript
// Save device_info
await env.DB.prepare(`
  UPDATE licenses
  SET device_id = ?,
      device_info = ?,  -- NEW
      session_token = ?,
      ...
`).bind(
  deviceId,
  JSON.stringify(payload.device_info),  -- Save as JSON string
  sessionToken,
  ...
).run();
```

---

### 6. Nonce Expiration (7 Minutes)
**هدف:**
- هر nonce فقط 7 دقیقه معتبر باشه
- استفاده از estimated server time

**آنچه انجام شده:**
- ✅ `NONCE_VALIDITY_MS = 7 * 60 * 1000` اضافه شد
- ✅ `nonceTimestamp` field به `LicenseData` اضافه شد
- ✅ `isNonceExpired()` method اضافه شد
- ❌ هنوز در همه جاها به کار گرفته نشده

**آنچه باید انجام بشه:**

1. **آپدیت activate():**
```java
// دریافت nonce_timestamp از سرور
long nonceTimestamp = json.optLong("nonce_timestamp", serverTime);

// ذخیره در SharedPreferences
prefs.edit()
    .putString("nonce", nonce)
    .putLong("nonce_timestamp", nonceTimestamp)  // NEW
    .apply();
```

2. **آپدیت verify():**
```java
// دریافت nonce_timestamp جدید
long newNonceTimestamp = json.optLong("nonce_timestamp", serverTime);
```

3. **آپدیت verifyOffline():**
```java
// چک expiration نonce
if (license.isNonceExpired()) {
    Log.e(TAG, "[VERIFY-OFFLINE] ⏰ NONCE EXPIRED (older than 7 minutes)!");
    Log.e(TAG, "[VERIFY-OFFLINE] Must re-verify with server");
    clearLicense();
    return LicenseResult.failure("Nonce expired - please reconnect");
}
```

4. **آپدیت file operations:**
```java
// writeLicenseToFile():
data.put("nonce", nonce);
data.put("nonce_timestamp", nonceTimestamp);  // NEW

// readLicenseFromFile():
long nonceTimestamp = json.optLong("nonce_timestamp", 0);

// Construct LicenseData:
new LicenseData(licenseKey, token, nonce, nonceTimestamp,
                status, lastCheck, lastServerTime, createdAt, expires, device);
```

5. **آپدیت Server:**
```javascript
// handleActivate():
const response = {
  success: true,
  session_token: sessionToken,
  nonce: nonce,
  nonce_timestamp: now,  // NEW: When nonce was created
  ...
};

// handleVerify():
const response = {
  success: true,
  valid: true,
  nonce: newNonce,
  nonce_timestamp: now,  // NEW: When new nonce was created
  ...
};
```

6. **آپدیت Legacy Constructor:**
```java
public LicenseData(String sessionToken, long expiresAt, String deviceId) {
    this.licenseKey = null;
    this.sessionToken = sessionToken;
    this.nonce = null;
    this.nonceTimestamp = 0;  // NEW
    this.status = "valid";
    ...
}
```

---

## 📝 کامل شدن Implementation:

### Checklist برای Nonce Expiration:

**Client (Android):**
- [ ] آپدیت `activate()` برای دریافت `nonce_timestamp`
- [ ] آپدیت `verify()` برای دریافت `nonce_timestamp`
- [ ] آپدیت `updateLicenseStatus()` برای ذخیره `nonce_timestamp`
- [ ] آپدیت `writeLicenseToFile()` برای نوشتن `nonce_timestamp`
- [ ] آپدیت `readLicenseFromFile()` برای خواندن `nonce_timestamp`
- [ ] آپدیت `verifyOffline()` برای چک `isNonceExpired()`
- [ ] آپدیت legacy constructor
- [ ] تست کردن

**Server (Cloudflare):**
- [ ] اضافه کردن `app_signature` validation
- [ ] ذخیره `device_info` در database
- [ ] ارسال `nonce_timestamp` در activate
- [ ] ارسال `nonce_timestamp` در verify
- [ ] آپدیت database schema برای `device_info`
- [ ] تست کردن

---

## 🎯 خلاصه نهایی:

**کار شده:**
✅ XOR Encryption (client + server)
✅ Server-Time Based Expiration
✅ Nonce One-Time Token
✅ AlarmManager برای periodic verification
✅ Multiple Nonce Checks
✅ App Signature (client-side)
✅ Comprehensive Device Info (client-side)
✅ Nonce Expiration Logic (partial)

**باید انجام بشه:**
❌ App Signature Validation (server-side)
❌ Device Info Storage (server-side)
❌ Nonce Expiration (کامل کردن در client + server)
❌ تست کامل سیستم

**زمان تخمینی:** 1-2 ساعت برای کامل کردن همه

---

## 📖 مستندات فنی:

### نحوه محاسبه Estimated Server Time:
```
estimatedServerTime = last_server_time + (current_client_time - last_check_client_time)

مثال:
- Activation: server_time = 1000, client_time = 500
- Save: last_server_time = 1000, last_check_client = 500

- After 5 min: client_time = 800 (client added 300)
- elapsed = 800 - 500 = 300
- estimated = 1000 + 300 = 1300

- User changes time to 2000:
- elapsed = 2000 - 500 = 1500
- estimated = 1000 + 1500 = 2500 (too high, will expire!)

- Next sync: server sends actual time = 1300
- Save: last_server_time = 1300, last_check_client = 2000
- Now calculations are corrected!
```

### نحوه چک Nonce Expiration:
```
estimatedServerTime = getEstimatedServerTime()
nonceAge = estimatedServerTime - nonceTimestamp
if (nonceAge > 7 minutes) → EXPIRED

مثال:
- Activation: nonce_timestamp = 1000 (server time)
- After 5 min: estimated = 1300
- nonceAge = 1300 - 1000 = 300 = 5 min ✅ Valid
- After 10 min: estimated = 1600
- nonceAge = 1600 - 1000 = 600 = 10 min ❌ EXPIRED
```

---

**Last Updated:** 2026-02-16
**Commit:** 03a57f9
