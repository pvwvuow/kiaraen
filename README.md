# Kiaraen — اپ اندروید یادگیری لغات انگلیسی

معماری آینده‌نگر با هزینه صفر شروع می‌شود: **Android (Kotlin + Compose) + Supabase (Postgres/RLS/Auth/Storage) + GitHub Actions**.

> 📚 مستندات کامل در [`docs/`](docs/) — شروع کنید با [معماری](docs/01-architecture.md)

## ویژگی‌های کلیدی طراحی

- **آفلاین‌فرست واقعی**: بانک ۵۰۰۰ لغتی (A1…C2، ۱۰۱ دسته، ۱۶ مجموعه) داخل APK؛
  یادگیری بدون اینترنت، سینک دلتا بعداً
- **امنیت پیش‌فرض**: RLS روی همه جداول کاربر؛ رویدادها append-only؛
  سرویس‌کی هرگز در اپ/مخزن نیست
- **سینک ضدخطا**: idempotency با `client_event_id`، تعارض چنددستگاهی با LWW سمت DB
- **آماده رشد**: ستون‌های SRS کامل، سطح‌های جدولی، لایه remote قابل تعویض
- **هزینه $0**: درخواست صفر برای بازکردن اپ؛ محتوا فقط با تغییر نسخه دانلود می‌شود

## شروع سریع

```bash
# اپ
gradle :app:assembleDebug            # یا از Android Studio

# بک‌اند (یک بار)
supabase link --project-ref <ref> && supabase db push
SUPABASE_URL=... SUPABASE_SERVICE_KEY=... python3 scripts/load_content_supabase.py
```

## ساختار مخزن

```
app/                    اپ اندروید (تک‌ماژول، لایه‌بندی پکیجی)
supabase/migrations/    مایگریشن‌های نسخه‌دار SQL (00001..00005)
supabase/tests/         تست‌های pgTAP امنیتی (RLS)
content/wordbank/       منبع حقیقت محتوا (TSV لغات ۶ سطح)
content-pack.json       پک نسخه‌دار تولیدشده (APK + Storage)
scripts/                پایپ‌لاین محتوا + لودر Supabase
.github/workflows/      CI، Release، مایگریشن، بکاپ هفتگی
docs/                   ۸ سند معماری/امنیت/هزینه/نقشه‌راه (فارسی)
```

## قرارداد مهم

پیش از هر تغییر دیتابیس: مایگریشن جدید، نه ویرایش قبلی‌ها.
پیش از هر معماری‌سازی: [`docs/08-roadmap.md`](docs/08-roadmap.md) را ببین —
«لازم است؟» فقط با داده واقعی.
