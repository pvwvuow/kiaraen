# تست‌ها

## چه چیزی کجا تست می‌شود

| ریسک | تست | نوع | کجا |
|---|---|---|---|
| ریاضی استریک | ۷ حالت مرزی (دیروز، گپ، رکورد…) | unit خالص | `StreakCalculatorTest` |
| زمان‌بندی SRS | اولین مرور، رشد فاصله، ریست با جواب غلط، سقف‌ها | unit خالص | `SrsLiteTest` |
| قواعد LWW / replay | برنده‌ی تعارض، تشخیص رویداد تکراری | unit خالص | `SyncRulesTest` |
| RLS + idempotency سمت DB | ۱۰ assertion امنیتی | pgTAP | `supabase/tests/rls_tests.sql` |
| اپ یکپارچه | بعداً: Compose UI test + درون‌گردهای Room | — | فاز ۴ نقشه راه |

## اجرا

```bash
# unit (روی سیستم شما یا CI):
gradle :app:testDebugUnitTest

# RLS (نیازمند docker):
supabase start
supabase db reset          # migrations + tests environment
supabase test db           # supabase/tests/*.test.sql را اجرا می‌کند
```

## پوشش RLS چه چیزهایی را ثابت می‌کند
1. anon محتوا را می‌خواند اما profiles را نه
2. کاربر A ردیف‌های کاربر B را در progress نمی‌بیند
3. INSERT با user_id دیگری → 42501
4. UPDATE روی review_log → 42501 (append-only)
5. client_event_id تکراری → 23505 (idempotency fence)
6. کاربر A رویدادهای کاربر B را نمی‌بیند

## پارتی parity (کلاینت ↔ سرور)
`StreakCalculator` (کاتلین) و trigger `bump_stats_on_review` (SQL) باید همان عدد
استریک را بدهند — قواعد یکسان: شروع از ۱، +۱ برای روز پیوسته، ریست با گپ،
مقاوم به رویداد out-of-order. اگر قاعده‌ای تغییر کرد، **هر دو** باید با هم عوض شوند.

## سازگاری Migration ها
- Room: `exportSchema=true` → JSON اسکیماها در `app/schemas/` commit می‌شوند؛
  MigrationTestHelper از نسخه N به N+1 را می‌سنجد (از نسخه ۲ به بعد)
- Postgres: `supabase db reset` در CI محلی = همه مایگریشن‌ها از صفر باید تمیز بگذرند
