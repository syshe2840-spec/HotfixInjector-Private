# 🔥 HotFix Injector - Protected Edition

**Xposed Module with Server-Side License Protection**

این ماژول به شما اجازه می‌دهد فایل‌های DEX را به صورت runtime به برنامه‌های اندروید inject کنید، با یک سیستم لایسنس قوی محافظت شده توسط **Cloudflare Worker + D1 Database**.

---

## 🔐 ویژگی‌های امنیتی

- ✅ **Server-Side License Verification** - کنترل کامل از سمت سرور
- ✅ **Device Binding** - محدودیت تعداد دستگاه (پیش‌فرض: 2 دستگاه)
- ✅ **Expiration Date** - تاریخ انقضای لایسنس
- ✅ **Real-Time Verification** - بررسی هر 10 ثانیه در حین اجرا
- ✅ **Auto-Crash Protection** - اگر لایسنس معتبر نباشد، برنامه crash می‌کند
- ✅ **Encrypted Communication** - ارتباط امن با سرور (HTTPS)
- ✅ **Unique Device ID** - شناسه منحصر به فرد دستگاه

---

## 📋 پیش‌نیازها

### برای سرور:
- حساب کاربری Cloudflare (رایگان)
- Node.js و npm (برای Wrangler CLI)

### برای اندروید:
- Android Studio یا Gradle Build Tools
- LSPosed Manager (برای اجرای ماژول)
- دسترسی Root

---

## 🚀 راهنمای نصب سرور

### مرحله 1: نصب Cloudflare Worker

```bash
cd cloudflare-worker

# نصب Wrangler
npm install -g wrangler

# لاگین به Cloudflare
wrangler login

# ساخت D1 Database
wrangler d1 create hotfix_licenses
```

### مرحله 2: کانفیگ Database

خروجی مرحله قبل شبیه این است:
```
database_id = "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
```

این ID را کپی کرده و در فایل `wrangler.toml` جایگزین کنید:
```toml
database_id = "YOUR_DATABASE_ID_HERE"
```

### مرحله 3: اجرای Schema

```bash
wrangler d1 execute hotfix_licenses --file=schema.sql
```

### مرحله 4: تغییر کلیدهای امنیتی

در فایل `worker.js`:
```javascript
const ENCRYPTION_KEY = 'YOUR_32_CHAR_ENCRYPTION_KEY_HERE!!'; // 32 کاراکتر
```

در فایل `wrangler.toml`:
```toml
ADMIN_KEY = "YOUR_ADMIN_KEY_CHANGE_THIS"
```

### مرحله 5: Deploy کردن

```bash
wrangler deploy
```

بعد از deploy، آدرس Worker شما نمایش داده می‌شود:
```
https://hotfix-license-api.YOUR_SUBDOMAIN.workers.dev
```

---

## 📱 راهنمای نصب اندروید

### مرحله 1: کانفیگ API URL

در فایل [LicenseClient.java](app/src/main/java/com/example/hotfixinjector/LicenseClient.java):

```java
private static final String API_BASE_URL = "https://YOUR_WORKER.workers.dev";
```

آدرس Worker خود را جایگزین کنید.

### مرحله 2: Build کردن APK

```bash
cd HotfixInjector
./gradlew assembleRelease
```

فایل APK در:
```
app/build/outputs/apk/release/app-release.apk
```

### مرحله 3: نصب و فعال‌سازی

1. APK را روی دستگاه Android نصب کنید
2. LSPosed Manager را باز کنید
3. ماژول HotFix Injector را فعال کنید
4. Reboot کنید

---

## 🔑 تولید لایسنس (Admin)

### روش 1: از طریق cURL

```bash
curl -X POST https://YOUR_WORKER.workers.dev/generate \
  -H "Content-Type: application/json" \
  -d '{
    "admin_key": "YOUR_ADMIN_KEY",
    "max_devices": 2,
    "expires_in_days": 30
  }'
```

### روش 2: از طریق Postman/Insomnia

**Endpoint:** `POST https://YOUR_WORKER.workers.dev/generate`

**Headers:**
```
Content-Type: application/json
```

**Body (JSON):**
```json
{
  "admin_key": "YOUR_ADMIN_KEY",
  "max_devices": 2,
  "expires_in_days": 30
}
```

**Response:**
```json
{
  "success": true,
  "license_key": "ABCDE-12345-FGHIJ-67890",
  "max_devices": 2,
  "expires_at": 1735689600000,
  "expires_in_days": 30
}
```

---

## 👤 فعال‌سازی لایسنس (کاربر)

### مرحله 1: دریافت Device ID

1. اپلیکیشن HotFix Injector را باز کنید
2. روی دکمه **"🔐 LICENSE ACTIVATION"** کلیک کنید
3. Device ID خود را یادداشت کنید (مثلاً: `a1b2c3d4e5f6...`)

### مرحله 2: درخواست لایسنس

- Device ID را به فروشنده/ادمین ارسال کنید
- لایسنس کلید دریافت کنید (فرمت: `XXXXX-XXXXX-XXXXX-XXXXX`)

### مرحله 3: فعال‌سازی

1. در صفحه License Activation، کلید را وارد کنید
2. روی **"ACTIVATE LICENSE"** کلیک کنید
3. منتظر تایید از سرور بمانید

اگر موفقیت‌آمیز بود:
```
✅ License Activated Successfully!
Status: ACTIVE
Expires: 2025-12-31 23:59
```

---

## 🛡️ نحوه عملکرد حفاظت

### 1. بررسی اولیه (قبل از Injection)
```
[LICENSE] Checking license...
[LICENSE] Verifying with server...
✅ [LICENSE] License verified successfully
```

اگر لایسنس معتبر نباشد:
```
❌ [LICENSE] No active license - INJECTION BLOCKED
```

### 2. بررسی مداوم (هر 10 ثانیه)
```
🛡️ License Guard started - verifying every 10 seconds
🔍 Verifying license...
✅ License valid
```

اگر تایید نشود (2 بار پشت سر هم):
```
❌ License verification failed (1/2)
❌ License verification failed (2/2)
💣 MAXIMUM FAILURES REACHED - TERMINATING APPLICATION
💥 CRASHING APPLICATION
```

### 3. Crash Mechanism

اگر لایسنس معتبر نباشد، اپلیکیشن scope شده:
- فوراً **crash** می‌کند
- `System.exit(1)` فراخوانی می‌شود
- Process kill می‌شود

---

## 🔧 مدیریت لایسنس‌ها

### مشاهده لایسنس‌ها

```bash
wrangler d1 execute hotfix_licenses --command "SELECT * FROM licenses"
```

### مشاهده دستگاه‌های فعال

```bash
wrangler d1 execute hotfix_licenses --command "SELECT * FROM devices"
```

### لغو لایسنس

```bash
curl -X POST https://YOUR_WORKER.workers.dev/revoke \
  -H "Content-Type: application/json" \
  -d '{
    "admin_key": "YOUR_ADMIN_KEY",
    "license_key": "ABCDE-12345-FGHIJ-67890"
  }'
```

### تغییر تاریخ انقضا

```bash
wrangler d1 execute hotfix_licenses --command \
  "UPDATE licenses SET expires_at = 1767225600000 WHERE license_key = 'XXXXX-XXXXX-XXXXX-XXXXX'"
```

---

## 📊 لاگ‌ها و Debugging

### لاگ‌های اندروید (Logcat)

```bash
adb logcat | grep HotfixInjector
adb logcat | grep LicenseClient
adb logcat | grep LicenseGuard
```

### لاگ‌های سرور (Cloudflare)

در Cloudflare Dashboard:
1. Workers & Pages
2. انتخاب Worker
3. Logs > Real-time Logs

---

## 🚨 عیب‌یابی مشکلات

### مشکل 1: "No active license"
**علت:** لایسنس فعال‌سازی نشده
**راه حل:** از صفحه License Activation، لایسنس را فعال کنید

### مشکل 2: "License verification failed"
**علت:** اتصال اینترنت یا سرور مشکل دارد
**راه حل:**
- اتصال اینترنت را چک کنید
- آدرس `API_BASE_URL` را چک کنید
- سرور Worker را چک کنید

### مشکل 3: "Maximum X devices allowed"
**علت:** تعداد دستگاه‌ها تمام شده
**راه حل:**
- دستگاه قدیمی را از database حذف کنید:
```bash
wrangler d1 execute hotfix_licenses --command \
  "DELETE FROM devices WHERE license_key = 'YOUR_KEY' AND device_id = 'OLD_DEVICE_ID'"
```

### مشکل 4: "License expired"
**علت:** تاریخ انقضا گذشته
**راه حل:**
- تاریخ انقضا را تمدید کنید (دستور بالا)
- یا لایسنس جدید صادر کنید

---

## 🔒 توصیه‌های امنیتی

1. ✅ **هیچ‌وقت ADMIN_KEY را public نکنید**
2. ✅ **ENCRYPTION_KEY را تغییر دهید**
3. ✅ **آدرس Worker را فقط به کاربران بدهید**
4. ✅ **لایسنس‌ها را برای هر کاربر جداگانه تولید کنید**
5. ✅ **Logs را مرتباً چک کنید**
6. ⚠️ **Certificate Pinning** (اختیاری - برای امنیت بیشتر)

---

## 📈 محدودیت‌های رایگان Cloudflare

| منبع | محدودیت رایگان |
|------|----------------|
| Workers Requests | 100,000/day |
| D1 Database | 5 GB |
| D1 Reads | 5M/day |
| D1 Writes | 100K/day |

برای ماژول شما **کاملاً کافی** است! 🚀

---

## 📝 لایسنس پروژه

این پروژه **محافظت شده با سیستم لایسنس** است.
- استفاده شخصی: آزاد
- توزیع: نیازمند مجوز
- فروش: نیازمند مجوز

---

## 👨‍💻 پشتیبانی

برای سوالات و مشکلات:
- 📧 Email: your-email@example.com
- 💬 Telegram: @your_telegram
- 🐛 Issues: GitHub Issues

---

## 🎉 تبریک!

شما با موفقیت یک ماژول Xposed محافظت شده با سیستم لایسنس server-side ساختید!

**نکته مهم:** این سیستم لایسنس نیاز به اتصال اینترنت دارد. بدون اینترنت، ماژول کار نمی‌کند.

