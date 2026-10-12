# همگام‌سازی و کارکرد آفلاین

## اصول
1. **آفلاین‌فرست**: هر نوشتن → Room فوراً (`pendingSync=1`)؛ UI هرگز منتظر شبکه نیست
2. **فقط دلتا**: هرگز «کل داده» دانلود/آپلود نمی‌شود؛ push = ردیف‌های pending،
   pull = وضعیت کاربر، محتوا = فقط وقتی نسخه عوض شده
3. **idempotent بودن مطلق**: retry، قطع‌و‌وصل، kill شدن اپ — هیچ رکورد تکراری

## قرارداد رویدادها vs وضعیت

| نوع | جدول | تکرار retry | تعارض چنددستگاه |
|---|---|---|---|
| رویداد | review_log, quiz_attempts | `UNIQUE(user_id, client_event_id)` + `ignoreDuplicates` | مهم نیست (append) |
| وضعیت | user_word_progress, lesson_progress | upsert روی PK مرکب | **LWW** روی `client_updated_at` |

## چرخه سینک (`SyncEngine.syncAll`)

```
push review_log (batch 200)      → 优选 success → markSynced
push quiz_attempts               → همان
push user_word_progress          → سرور با trigger LWW نوشتن کهنه را می‌ریزد
pull user_word_progress          → merge محلی با SyncRules.remoteWins
pull user_stats                  → کش سرور معتبر؛ بازنویسی کش محلی
content refresh                  → ۱ ردیف content_packs؛ فقط اگر نسخه جدید بود دانلود
```

- هر مرحله مستقل است؛ شکست مرحله n مانع مرحله n+1 نیست
- نتیجه false → WorkManager `Result.retry()` با backoff نمایی ۳۰s→۱h
- یکی‌شات بعد از هر نوشتن + دوره‌ای هر ۶ ساعت (شبکه‌دار) + pull بعد از لاگین

## ورود از حالت محلی به حساب
کاربر می‌تواند قبل از ثبت‌نام هم یاد بگیرد (userId='local'). بعد از اولین لاگین
`ProgressDao.migrateLocalData(uid)` همه ردیف‌های 'local' را به حساب منتقل می‌کند و
`SyncWorker(pull=true)` اجرا می‌شود → هیچ پیشرفتی از دست نمی‌رود.

## تعارض‌ها و محدودیت‌های شناخته‌شده
- **ساعت دستگاه**: LWW روی ساعت کلاینت است + کلمپ ۵ دقیقه آینده. فاز ۵ (نقشه راه):
  HLC (Hybrid Logical Clock) — schema آماده است چون client/server timestamps جدا هستند
- **استریک و تایم‌زون**: فعلاً UTC؛ `profiles.tz_offset_minutes` از الان ذخیره می‌شود
- **حذف روی دستگاه A** → دستگاه B آن را دوباره pull می‌کند (حل تعارض حذف/نوشتن
  نیاز به tombstone دارد) — فاز ۵؛ فعلاً حذف لغت عملیات عادی کاربر نیست

## بودجه ترافیک (چرا این طراحی ارزان است)
- بازکردن اپ با محتوای کش‌شده: **صفر** درخواست
- بررسی نسخه محتوا: ۱ درخواست ~۵۰۰ بایت
- سینک یک جلسه ۵۰ سؤالی: ۱ batch review_log (~۵۰ ردیف × ~۱۲۰بایت) + ۱ upsert وضعیت
  ≈ **< ۲۵KB** به‌ازای هر جلسه
