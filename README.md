# Meta Media Downloader APK

نسخة Android أصلية من برنامج Local Web السابق. لا تحتاج Termux أو Pydroid أو Telegram: محرك yt-dlp وFFmpeg مضمّنان داخل التطبيق عبر مكتبة Android، وتعمل الواجهة العربية RTL داخل WebView محلي.

## تنزيل APK

حمّل الإصدار المجاني القابل للتثبيت من [GitHub Releases](https://github.com/abdulmajeedaltuhami/meta-media-downloader-android/releases/latest). الإصدار الحالي هو APK Universal موقّع Debug للتجربة، وحجمه كبير لأن yt-dlp وFFmpeg وبيئة Python مضمّنة داخله.

> هذا المشروع متاح بصيغة source-available للاستخدام غير التجاري تحت PolyForm Noncommercial 1.0.0. القيود غير التجارية تعني أنه ليس ترخيصاً معتمداً من OSI باسم Open Source بالمعنى القانوني الصارم، رغم أن الشفرة متاحة للقراءة والتعديل والاستخدام الشخصي.

## الوظائف

- YouTube وFacebook وInstagram وThreads وTikTok للروابط العامة التي يدعمها yt-dlp.
- اختيار الجودة أو الصوت فقط.
- الترجمة الأصلية العربية/الإنجليزية عند توفرها.
- تنزيل القوائم عند تفعيل الخيار.
- منع تكرار نفس الرابط مع نفس الإعدادات بعد اكتماله.
- حفظ مباشر في `Download/MetaVideosBot/files`.
- قراءة حالات WhatsApp المحلية ونسخها إلى `Download/MetaVideosBot/WhatsAppStatus`.

## بناء APK

يتطلب Android Studio أو بيئة Linux x86-64 فيها Java 17 وAndroid SDK 35:

```bash
export ANDROID_SDK_ROOT="$HOME/Android/Sdk"
./gradlew assembleDebug
```

الناتج:

```text
app/build/outputs/apk/debug/app-debug.apk
```

يمكن البناء بدون إعداد محلي عبر GitHub Actions: ارفع المجلد إلى مستودع GitHub، ثم افتح تبويب **Actions** وشغّل **Build Android APK**. حمّل artifact باسم `meta-media-downloader-apk`.

## التثبيت

فعّل السماح بتثبيت التطبيقات من هذا المصدر، ثم افتح ملف APK. سيطلب التطبيق صلاحية قراءة الوسائط، وعلى Android القديم صلاحية التخزين. افتح WhatsApp مرة واحدة قبل قراءة الحالات حتى تكون الحالات محفوظة محلياً.

## ملاحظات مهمة

- الإصدار الأول يستهدف Android 7 أو أحدث ونسخ ARM64/ARMv7/x86_64 حسب ABI المتاح.
- حجم APK كبير لأن Python/yt-dlp وFFmpeg مضمّنة داخله. يمكن لاحقاً نشر AAB لتصغير التنزيل حسب نوع المعالج.
- Threads وInstagram قد يرفضان بعض الروابط العامة أو يطلبان ملفات cookies؛ التطبيق لا يتجاوز الحسابات الخاصة أو الحماية التقنية.
- لا يوجد خادم خارجي ولا مشاركة تلقائية للملفات.
- التنزيل يعمل كمهمة خلفية داخل التطبيق. لا تغلق التطبيق قسرياً أثناء التنزيل في هذا الإصدار.
- استخدم المحتوى الذي تملك حق تنزيله فقط.

## مصدر النسخة السابقة

تم الإبقاء على نسخة Flask الأصلية منفصلة في الحزمة السابقة لاستخدامها مع Termux/Pydroid عند الحاجة.
