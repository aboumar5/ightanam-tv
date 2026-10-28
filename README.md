# اغتنم — ساعة الأذان لشاشات Android TV / Google TV

يحوّل شاشتك الذكية إلى ساعة أذان: شاشة توقف بمواقيت الصلاة، وبطاقة أذان صغيرة تطلع فوق أي شي تشاهده، وتذكيرات يوم الجمعة، وشاشة رئيسية عربية بسيطة.

مبني على تطبيق الويب [اغتنم — ساعة الأذان](https://aboumarai.com/ightanam) (الصفحة تشتغل داخل التطبيق).

| الشاشة الرئيسية | شاشة التوقف |
|---|---|
| ![](docs/home.png) | ![](docs/screensaver.png) |

| بطاقة الأذان | تذكير الجمعة |
|---|---|
| ![](docs/adhan-card.png) | ![](docs/friday-card.png) |

## المزايا

- **شاشة توقف:** الساعة، والتاريخ الهجري، والصلاة القادمة، وكم باقي عليها، ومواقيت اليوم، و«أذّن … منذ X د» لمدة نص ساعة بعد الأذان.
- **بطاقة الأذان:** بطاقة في زاوية الشاشة وقت الصلاة:
  - توقف الفيديو اللي شغّال، وتشغّل الأذان، وبعدها ترجّع الفيديو.
  - زر **رجوع** يوقف الأذان وتكمل مشاهدتك.
  - صوت المؤذن نفس اللي اخترته في الصفحة.
  - أذان الفجر له صوت خاص.
- **وضع الجمعة:**
  - تذكير سورة الكهف، وزر الوسط يشغّلها.
  - تذكير التبكير قبل الصلاة بساعة وربع.
  - تذكير ساعة الإجابة قبل المغرب بساعة.
- **شاشة رئيسية (اختيارية):** الوقت والتاريخ الهجري والصلاة القادمة، وصف واحد فيه تطبيقاتك.
- **المواقيت:** أم القرى، وحساب العصر على المذهب الشافعي. المدينة اللي تختارها في الصفحة تنتقل للتطبيق تلقائياً، ولو ما فيه اتصال يحسب المواقيت بنفسه.

## البناء (Windows)

يُبنى بدون Gradle، باستخدام أدوات Android SDK مباشرة. تحتاج:

- **Android SDK** فيه `build-tools\35.0.0` و`platforms\android-35`، في `%LOCALAPPDATA%\Android\Sdk`.
- **JDK 17:** عرّف `JAVA_HOME` أو حطه في `%USERPROFILE%\dev\jdk17`.
- **خط ثمانية (اختياري):** ترخيصه يمنع إعادة توزيع ملفاته، عشان كذا مب موجود في المستودع. نزّله من [font.thmanyah.com](https://font.thmanyah.com) وحط ملفات `.ttf` أو `.otf` في مجلد `fonts`. لو ما حطيته، التطبيق يستخدم خط النظام.

```powershell
.\build.ps1
```

الناتج: `ightanam-tv.apk`، موقّع بمفتاح debug.

## التثبيت على الشاشة

١. فعّل **خيارات المطوّر** ثم **تصحيح USB** (أو تصحيح الشبكة) من إعدادات الشاشة. بعدها اعرف عنوان IP حق الشاشة من إعدادات الشبكة.

٢. اتصل بالشاشة، ووافق على الرسالة اللي تطلع عليها:

```bash
adb connect <TV_IP>:5555
```

٣. ثبّت التطبيق:

```bash
adb install -r ightanam-tv.apk
```

٤. اسمح لبطاقة الأذان تطلع فوق التطبيقات الثانية:

```bash
adb shell appops set com.aboumarai.ightanam SYSTEM_ALERT_WINDOW allow
```

٥. خلّه شاشة التوقف:

```bash
adb shell settings put secure screensaver_components com.aboumarai.ightanam/.IghtanamDream
```

```bash
adb shell settings put secure screensaver_enabled 1
```

٦. افتح «اغتنم» مرة وحدة، واضغط «ابدأ»، واختر مدينتك والمؤذن.

### الشاشة الرئيسية (اختياري)

Google TV ما يقبل شاشة رئيسية ثانية إلا إذا عطّلت شاشته. هذا التعطيل يرجع بسهولة، لكن تتوقف معه اقتراحات Google TV. البحث الصوتي والكاست يبقون شغالين.

```bash
adb shell pm disable-user --user 0 com.google.android.apps.tv.launcherx
```

```bash
adb shell pm disable-user --user 0 com.google.android.tungsten.setupwraith
```

```bash
adb shell cmd package set-home-activity com.aboumarai.ightanam/.HomeActivity
```

للتراجع:

```bash
adb shell pm enable com.google.android.apps.tv.launcherx
```

```bash
adb shell pm enable com.google.android.tungsten.setupwraith
```

> **مهم:** لو بتسوي إعادة ضبط المصنع، فعّل `setupwraith` قبلها، لأن الإعداد الأول للشاشة يحتاجه.

### تجربة الأذان والتذكيرات

```bash
adb shell am broadcast -n com.aboumarai.ightanam/.AdhanReceiver --ez test true --es key asr
```

```bash
adb shell am broadcast -n com.aboumarai.ightanam/.AdhanReceiver --ez test true --es friday kahf
```

### للأمان

بعد ما تخلص، اقفل **تصحيح USB** من خيارات المطوّر، وألغِ تصاريح التصحيح.

## الملفات

| الملف | وظيفته |
|---|---|
| `Web.java` | يفتح صفحة الويب، ويرتّب واجهتها للريموت، ويجيب منها المدينة والمؤذن والمواقيت |
| `IghtanamDream.java` | شاشة التوقف |
| `AdhanScheduler.java` / `PrayerTimes.java` | منبّه الصلاة القادمة، وحساب المواقيت لو ما فيه اتصال |
| `AdhanReceiver.java` / `AdhanService.java` | بطاقة الأذان وتذكيرات الجمعة اللي تطلع فوق المشاهدة |
| `FridayScheduler.java` | مواعيد تذكيرات الجمعة |
| `HomeActivity.java` | الشاشة الرئيسية |
| `art.ps1` | يرسم أيقونة التطبيق وشعاره |

## الترخيص

الكود تحت رخصة MIT، وتفاصيلها في ملف [LICENSE](LICENSE). خط ثمانية له ترخيصه الخاص من شركة ثمانية، ومب مشمول هنا.
