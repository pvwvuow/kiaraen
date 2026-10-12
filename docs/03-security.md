# امنیت — Auth، RLS و مدیریت کلیدها

## ۱. احراز هویت
- Supabase **GoTrue**؛ رمزها هرگز به دیتابیس ما نمی‌رسند (هش توسط سرویس Auth)
- اپ فقط با کلید **anon/publishable** بیلد می‌شود — این کلید «عمومی» است و امنیت واقعی
  توسط RLS تضمین می‌شود، نه مخفی‌بودن کلید
- `service_role` فقط در CI (لودر محتوا) و هرگز نه در اپ، نه در مخزن، نه در لاگ

## ۲. Row Level Security — قانون واحد
> هر کاربر فقط ردیف‌هایی می‌بیند/می‌نویسد که `user_id = auth.uid()` است.

| جدول | SELECT | INSERT | UPDATE | DELETE |
|---|---|---|---|---|
| profiles | خودش | ❌ (trigger) | خودش | ❌ |
| user_word_progress | خودش | خودش | خودش | خودش |
| review_log | خودش | خودش | **❌** | **❌** |
| quiz_attempts | خودش | خودش | **❌** | **❌** |
| user_stats | خودش | ❌ | ❌ (فقط trigger) | ❌ |
| content_* | همه (anon+auth) | ❌ | ❌ | ❌ |

- append-only بودن رویدادها = هیچ‌کس تاریخچه یادگیری‌اش را دستکاری نمی‌کند
- `user_stats` فقط با SECURITY DEFINER trigger ها نوشته می‌شود؛ تابع بازسازی
  `recalc_user_stats(uid)` داخل تابع چک می‌کند که فقط مالک خودش را بازسازی کند

## ۳. محافظت‌های سرور-ساید (فراتر از RLS)
- **LWW guard** (00005): نوشتن قدیمی از دستگاه دیگر بی‌صدا رد می‌شود؛ اسکیو ساعت با
  کلمپ ۵ دقیقه‌ای محدود شده
- **Idempotency**: `UNIQUE(user_id, client_event_id)` → retry شبکه رکورد تکراری نمی‌سازد
- **اعتبارسنجی دامنه**: CHECK constraint ها (ease 1.3..3.0، xp، status enum و …)

## ۴. اسرار در CI/CD
همه در **GitHub Secrets** (هیچ secret در فایل‌ها):

| Secret | مصرف | سطح دسترسی |
|---|---|---|
| `SUPABASE_ACCESS_TOKEN` | مایگریشن + بکاپ | کنترل کامل پروژه — فقط Actions |
| `SUPABASE_DB_PASSWORD` | db push / dump | فقط Actions |
| `SUPABASE_SERVICE_KEY` | لود محتوا (لودر پایتون) | فقط محیط اجرای محلی شما |
| `ANDROID_KEYSTORE_BASE64` (+3) | بیلد release امضاشده | فقط Actions |

## ۵. موارد امنیتی برای انسان‌ها
- ⚠️ هر توکنی که در چت/اسکرین‌شات/تیمز past شده **لو رفته** است — از GitHub
  Settings → Developer settings → بلاش کنید و توکن جدید با اسکوپ محدود بسازید
- توکن‌ها را با حداقل اسکوپ بسازید (repo:kiaraen فقط) و تاریخ انقضا بگذارید
- بکاپ‌ها شامل داده کاربرند → فقط artifact خصوصی، هرگز public release

## ۶. لاگ‌ها
- `SyncEngine` فقط پیام خطا و مرحله را لاگ می‌کند — **هرگز** توکن/رمز/ایمیل
- لاگ RLS: `pgaudit` سمت سرور در صورت نیاز (پلن Pro)
