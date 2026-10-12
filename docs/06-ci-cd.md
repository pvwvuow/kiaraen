# Build، Deploy و مایگریشن‌ها

## GitHub Actions (۴ workflow)

| فایل | تریگر | کار |
|---|---|---|
| `android-ci.yml` | push/PR | تست واحد + build debug APK + artifact |
| `android-release.yml` | tag `v*` / دستی | بیلد signed AAB+APK با keystore از Secrets → GitHub Release |
| `supabase-migrations.yml` | دستی (سپس push قابل فعال‌سازی) | `supabase db push` مایگریشن‌های نسخه‌دار |
| `supabase-backup.yml` | هفتگی cron | dump کامل schema+data → artifact ۹۰ روزه |

## راه‌اندازی یک‌باره (چک‌لیست)

```bash
# 1) Supabase: پروژه بساز، سپس:
supabase link --project-ref <ref>
supabase db push                    # مایگریشن‌ها 00001..00005
# 2) محتوا:
export SUPABASE_URL=... SUPABASE_SERVICE_KEY=...
python3 scripts/load_content_supabase.py
# 3) gradle.properties پروژه: url + anon key واقعی
# 4) GitHub Secrets (بخوان docs/03-security.md §۴)
```

## قواعد مایگریشن
- هر تغییر دیتابیس = فایل جدید `supabase/migrations/000NN_*.sql` — فایل‌های قبلی **هرگز** ویرایش نمی‌شوند
- forward-only؛ rollback یعنی مایگریشن جدید معکوس
- سازگاری با اپ‌های قدیمی: تغییرات **additive** (ستون nullable / جدول جدید) امن‌اند؛
  breaking changes فقط وقتی minVersion اپ بالاتر رفت

## امضای release
- keystore محلی بساز: `keytool -genkeypair -v -keystore kiaraen-release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias kiaraen`
- base64 کن و در Secrets بگذار؛ فایل jks هرگز commit نمی‌شود (.gitignore)
- **keystore را جدا هم بکاپ کن** — گمشدنش یعنی امکان آپدیت در استور را از دست می‌دهی

## نسخه‌گذاری
`versionCode` عدد صحیح افزایشی، `versionName` semver. release فقط با tag:
```bash
git tag v0.1.0 && git push origin v0.1.0
```
