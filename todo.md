# خطة تطوير تطبيق تنزيل الوسائط

## 1. البنية الأساسية
- [x] تحديث Gradle (Kotlin + Material 3 + التبعيات)
- [x] تحديث AndroidManifest (خدمة أمامية + أذونات + FileProvider)
- [x] إنشاء الثيمات والألوان والنصوص والأيقونات

## 2. النواة (Core)
- [x] Prefs / DownloadItem / DownloadStore
- [x] FormatMapper (الصيغ والأحجام)
- [x] YtDlpEngine (فحص، تنزيل، تحديث، 429)
- [x] MediaStoreHelper (نشر الملفات)

## 3. خدمة التنزيل
- [x] DownloadService مع إشعار تقدم + إلغاء

## 4. الواجهة
- [x] MainActivity + BottomNavigation
- [x] شاشة التنزيل
- [x] شاشة التحميلات
- [x] شاشة حالات واتساب (SAF)
- [x] شاشة الإعدادات

## 5. التحقق والتسليم
- [x] التحقق الساكن من XML وتوافق واجهات المكتبة
- [ ] بناء المشروع عبر CI / فحص الأخطاء
- [ ] رفع فرع + Pull Request
