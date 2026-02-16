# 🚀 راهنمای Upload به GitHub

## مرحله 1: ساخت Repository خصوصی

1. برو به: https://github.com/new
2. **Repository name:** `HotfixInjector-Private` (یا هر نامی که دوست داری)
3. **Description:** `Xposed Module with Server-Side License Protection`
4. **Visibility:** انتخاب کن **Private** ⚠️ (خیلی مهمه!)
5. **Initialize repository:** همه رو خالی بذار (چون قبلاً کد داریم)
6. کلیک روی **Create repository**

---

## مرحله 2: Push کردن کد

بعد از ساخت repository، این دستورات رو توی Git Bash یا CMD اجرا کن:

```bash
cd "F:\AiHotFix Lock\HotfixInjector"

# Initialize git
git init

# Add all files
git add .

# First commit
git commit -m "Initial commit - HotFix Injector with License Protection"

# Add remote (جایگزین USERNAME با نام کاربری GitHub)
git remote add origin https://github.com/USERNAME/HotfixInjector-Private.git

# Push to main branch
git branch -M main
git push -u origin main
```

---

## مرحله 3: بررسی GitHub Actions

1. برو به repository تو GitHub
2. تب **Actions** رو باز کن
3. باید workflow "Build APK" رو ببینی که داره اجرا میشه
4. منتظر بمون تا تموم شه (حدود 5-10 دقیقه)

---

## مرحله 4: دانلود APK

بعد از build موفق:

### روش 1: از Artifacts
1. برو به تب **Actions**
2. آخرین workflow run رو باز کن
3. پایین صفحه، قسمت **Artifacts** رو ببین
4. دانلود کن: `HotfixInjector-YYYYMMDD-HHMMSS.zip`
5. Extract کن و APK رو داخلش پیدا می‌کنی

### روش 2: از Releases (فقط برای push به main)
1. برو به تب **Releases**
2. آخرین release رو باز کن
3. مستقیماً APK رو دانلود کن

---

## 🔒 امنیت

✅ Repository رو **Private** نگه دار
✅ هیچ‌وقت ADMIN_KEY یا ENCRYPTION_KEY رو commit نکن
✅ فایل‌های `.env` یا `secrets.properties` رو به `.gitignore` اضافه کن

---

## 🔄 آپدیت کردن کد

بعداً که تغییری دادی:

```bash
cd "F:\AiHotFix Lock\HotfixInjector"

git add .
git commit -m "توضیح تغییرات"
git push
```

GitHub Actions خودکار APK جدید رو می‌سازه! 🚀

---

## 📱 استفاده

1. APK رو دانلود کن
2. روی دستگاه Android نصب کن
3. در LSPosed فعال کن
4. Reboot
5. لایسنس رو فعال کن

---

## 💡 نکات

- **Build Time:** 5-10 دقیقه
- **Artifact Retention:** 30 روز
- **Manual Trigger:** از تب Actions می‌تونی دستی trigger کنی
- **Auto Build:** هر push به main خودکار build میشه

---

**موفق باشی!** 🎉
