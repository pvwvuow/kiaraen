# محدودیت‌های زیرساخت، هزینه‌ها و مسیر ارتقا

> اعداد «حدود مارس ۲۰۲۵» است؛ قبل از تصمیم‌های مهم با داشبورد فعلی Supabase چک کنید.

## Supabase Free — سقف‌ها و ترجمه‌شان برای Kiaraen

| محدودیت | سقف Free | یعنی چه |
|---|---|---|
| Database | 500MB | ~۵۰۰۰ لغت ≈ ۲MB؛ داده هر کاربر فعال/سال ≈ ۱-۲MB → **چند ده هزار کاربر** |
| Egress (پهنای باند) | 5GB/mo | با طراحی «کش محتوا + دلتا سینک» ≈ هر کاربر فعال/ماه ~۱۰MB → ~۵۰k MAU |
| Auth MAU | 50,000 | سقف واقعی پلن رایگان |
| Storage | 1GB | پک محتوا ~250KB × ۲۰ نسخه = ناچیز |
| Edge Functions | 500k invoke | فعلاً استفاده نمی‌کنیم |
| **توقف پروژه** | ⚠️ ۷ روز غیرفعالی → pause | بکاپ هفتگی + یک ping کار خودش را می‌کند |
| بکاپ | **ندارد** | workflow هفتگی `supabase-backup.yml` جبران می‌کند |
| PITR | فقط Pro | بکاپ دستی/CI جایگزین است |

**هشدار صادقانه:** پلن رایگان SLA ندارد و ممکن است پروژه غیرفعال pause شود؛
«ظرفیت» بالا به معنای گارانتی دسترس‌پذیری نیست.

## GitHub Actions

| حالت مخزن | سهمیه | مصرف ما |
|---|---|---|
| Public | رایگان نامحدود | CI هر push ~۴-۶ دقیقه |
| Private | 2000 دقیقه/ماه | ~۳۰۰ push در ماه |

بکاپ هفتگی + مایگریشن دستی ≈ ۱۰ دقیقه/ماه. مشکلی نیست.

## مانیتورینگ مصرف قبل از برخورد

1. داشبورد Supabase → Reports (DB size، egress، API requests)
2. ساده و مؤثر: یک workflow ماهانه که کوئری‌های زیر را اجرا و در Summary چاپ کند:

```sql
select pg_size_pretty(pg_database_size(current_database()));
select count(*) from auth.users;                       -- ثبت‌نامی ≠ فعال
select count(distinct user_id) from review_log
  where created_at > now() - interval '30 days';       -- MAU واقعی
select pg_size_pretty(pg_total_relation_size('review_log'));
```

3. Supabase اجازه تنظیم **budget alert** برای اکانت Billing را می‌دهد؛ فعالش کنید.

## مسیر ارتقا — مرحله به مرحله، فقط وقتی واقعاً لازم شد

| فاز | سیگنال نیاز | اقدام | هزینه |
|---|---|---|---|
| ۰-۱۰k کاربر | — | هیچ. طراحی فعلی کافی است | $0 |
| ۱۰-۵۰k MAU | egress > 4GB یا DB > 400MB | Supabase **Pro** | $25/mo |
| ۱۰۰k+ | review_log بزرگ | پارتیشن ماهانه + `pruneSyncedReviewLogs` سمت سرور | Pro |
| عملکرد | کوئری کند | read replica / caching RPC | Pro+ |
| میلیون‌ها | — | فقط `data/remote` عوض می‌شود (interface محور)؛ UI/Room دست‌نخورده | — |

## کاری که **عمداً** نکردیم (و چرا)
- Hilt/Koin، مالتی‌ماژول، Clean Architecture کامل، KMP: پیچیدگی بدون نیاز فعلی
- Edge Function ها: هیچ منطقی فعلاً به سرور لازم نیست؛ DB constraint + trigger کفایت می‌کند
- Realtime: برای یادگیری فردی لازم نیست؛ رتبه‌بندی (فاز ۶) به آن فکر می‌کند
