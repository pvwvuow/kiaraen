# Kiaraen — اپ اندروید یادگیری لغات انگلیسی

معماری آینده‌نگر با هزینه صفر شروع می‌شود: **Android (Kotlin + Compose) + Supabase (Postgres/RLS/Auth/Storage) + GitHub Actions**.

> 📚 مستندات کامل در [`docs/`](docs/) — شروع کنید با [معماری](docs/01-architecture.md)
> 🚀 راه‌اندازی Supabase در ۵ دقیقه: [`docs/09-supabase-setup.md`](docs/09-supabase-setup.md)
> 🎨 طرح‌های مرجع رابط کاربری: [`design/`](design/) (پیش‌نمایش‌های پروتوتایپ HTML مصوب)

## ویژگی‌های کلیدی طراحی

- **آفلاین‌فرست واقعی**: بانک ۵۰۰۰ لغتی (A1…C2، ۱۰۱ دسته، ۱۶ مجموعه) داخل APK؛
  یادگیری بدون اینترنت، سینک دلتا بعداً
- **حالت مهمان**: بدون هیچ کلیدی هم اپ کامل کار می‌کند؛ با ساخت حساب، همان پیشرفت
  خودکار به حساب منتقل می‌شود (local → account linking)
- **احراز هویت کامل**: ورود / ثبت‌نام / بازیابی رمز با Supabase GoTrue —
  password هرگز به دیتابیس ما نمی‌رسد
- **امنیت پیش‌فرض**: RLS روی همه جداول کاربر؛ رویدادها append-only؛
  سرویس‌کی هرگز در اپ/مخزن نیست
- **سینک ضدخطا**: idempotency با `client_event_id`، تعارض چنددستگاهی با LWW سمت DB
- **آماده رشد**: ستون‌های SRS کامل، سطح‌های جدولی، لایه remote قابل تعویض
- **هزینه $0**: درخواست صفر برای بازکردن اپ؛ محتوا فقط با تغییر نسخه دانلود می‌شود

## شروع سریع (Android Studio)

1. ریپو را کلون/دانلود کن و در Android Studio باز کن (اندروید استودیو خودش SDK را تنظیم می‌کند).
2. **Run** بزن — همین حالا اپ در «حالت مهمان» کامل کار می‌کند (بدون هیچ کلیدی).
3. برای سینک ابری: [`docs/09-supabase-setup.md`](docs/09-supabase-setup.md) — بعد از
   ۵ دقیقه، `local.properties` را مثل `local.properties.example` پر کن و دوباره Run بزن.

## بیلد APK

```bash
# لوکال
gradle :app:assembleDebug

# یا با کلیدهای ساپابیس
gradle :app:assembleDebug -Pkiara.supabase.url=https://YOUR-PROJECT.supabase.co -Pkiara.supabase.anonKey=YOUR-ANON-KEY

# روی GitHub Actions: هر push روی main → APK دیباگ در Artifacts ورک‌فلو Android CI
# نسخه امضاشده: تگ v* بزن → ورک‌فلو Android Release (Secretهای امضا لازم)
```

## راه‌اندازی بک‌اند (یک بار)

```bash
# ساده‌ترین راه: bootstrap.sql را در SQL Editor اجرا کن → docs/09-supabase-setup.md
# یا با CLI:
supabase link --project-ref <ref> && supabase db push
SUPABASE_URL=... SUPABASE_SERVICE_KEY=... python3 scripts/load_content_supabase.py
```

## ساختار مخزن

```
app/                    اپ اندروید (تک‌ماژول، لایه‌بندی پکیجی)
supabase/migrations/    مایگریشن‌های نسخه‌دار SQL (00001..00005)
supabase/bootstrap.sql  همان ۵ مایگریشن یکجا — برای SQL Editor داشبورد
supabase/tests/         تست‌های pgTAP امنیتی (RLS)
content/wordbank/       منبع حقیقت محتوا (TSV لغات ۶ سطح)
content-pack.json       پک نسخه‌دار تولیدشده (APK + Storage)
scripts/                پایپ‌لاین محتوا + لودر Supabase
.github/workflows/      CI، Release، مایگریشن، بکاپ هفتگی
design/                 پیش‌نمایش‌های مصوب UI (پروتوتایپ HTML)
docs/                   ۹ سند معماری/امنیت/هزینه/راه‌اندازی (فارسی)
local.properties.example  الگوی کلیدهای لوکال (خود فایل gitignored است)
```

## قرارداد مهم

پیش از هر تغییر دیتابیس: مایگریشن جدید، نه ویرایش قبلی‌ها.
پیش از هر معماری‌سازی: [`docs/08-roadmap.md`](docs/08-roadmap.md) را ببین —
«لازم است؟» فقط با داده واقعی.
