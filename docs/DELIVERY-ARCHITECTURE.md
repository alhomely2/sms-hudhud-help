# البنية التقنية لطبقة الإرسال المحمية

## 1. نظرة عامة

تتكون البنية من أربعة طبقات رئيسية:

1. إعدادات الموقع
2. قاعدة البيانات المحلية
3. طبقة throttling/retry
4. طبقة إرسال الاتصال إلى المنصة

هذا الفصل يشرح كيف تتفاعل هذه الطبقات معًا دون تجاوز القيد الأساسي: "لا إرسال فعلي قبل اعتماد العقد وإتاحة endpoint الصحيح".

---

## 2. الطبقة 1: إعدادات الموقع

### الملفات الأساسية

- `AppSettingsStore`
- `SiteProfile`
- `SiteProfileValidator`

### الوظيفة

- إدارة ملفات الموقع
- حفظ الموقع النشط
- إسناد كل ملف موقع لإعداداته الخاصة
- التحقق من القيم المسموح بها

### مثال

```kotlin
val profile = appSettings.getActiveProfile()
val delay = profile.delayBetweenMessagesMs
val batchSize = profile.batchSize
val retries = profile.maxRetryAttempts
```

### القيم الأساسية

- `delayBetweenMessagesMs`: 0..60000
- `batchSize`: 10..500
- `maxRetryAttempts`: 1..10
- `connectTimeoutSeconds`: 5..60
- `readTimeoutSeconds`: 5..120
- `callTimeoutSeconds`: 5..180

---

## 3. الطبقة 2: قاعدة البيانات المحلية

### لماذا تحتاج قاعدة بيانات منفصلة؟

لأن الحملات تُنشأ محليًا وتخزن أولاً في `Room`, ثم تُحَوَّل إلى queue للإرسال، بدلًا من إنشاء اتصال مباشر فورًا إلى المنصة.

### المكونات المقترحة

- `CampaignEntity`
- `CampaignMessageEntity`
- `OutgoingMessageQueueEntity`
- `OutgoingQueueDao`
- `RemoteDispatchRepository`

### المبدأ

- لا يرسل التطبيق مباشرة من `CampaignStorage`
- يمر كل محتوى جاهز إلى queue
- queue هي نقطة التحول بين البيانات المحلية والإرسال

---

## 4. الطبقة 3: throttling و retry

### الملفات الأساسية

- `RequestThrottlePolicy`
- `SiteProfile`

### المهمة

تحديد:

- متى يبدأ الطلب التالي
- متى يتوقف الطلب لتقدير server limit
- متى يتم retry
- متى يتم إيقاف retry

### مثال منطق القرار

```kotlin
val retryAfter = RequestThrottlePolicy.parseRetryAfterSeconds(response.header("Retry-After"))
val effectiveDelay = RequestThrottlePolicy.calculateDelayMs(profile, retryAfter)
```

### القواعد

- يُستخدم `Retry-After` إذا جاء من الخادم
- يتم إبطاء إرسالات الدفعات قبل كل batch
- لا يتم retry في حالات auth/client errors
- retry محصور في حالات مؤقتة موثقة فقط

---

## 5. الطبقة 4: Remote Sender

### الوظيفة

هذه الطبقة تتحكم في:

- جلب batch من queue
- اختياره من الموقع النشط
- إرسال payload إلى المنصة
- تحديث الحالة
- إرجاع النتيجة للـ queue

### المكونات المقترحة

```kotlin
class RemoteDispatchRepository(
    private val appSettings: AppSettingsStore,
    private val apiKeyStore: ApiKeyStore,
    private val queueDao: OutgoingQueueDao,
    private val client: HudhudClient,
) {
    suspend fun processBatch() {
        // 1. load pending queue items
        // 2. validate send guard
        // 3. send request
        // 4. update queue state
    }
}
```

### معايير التفاعل

- لكل طلب يوجد `X-Correlation-Id`
- كل استجابة تُسجل بالدرجة المطلوبة
- لا يتم إرسال payload إذا كانت حالة `blocked`

---

## 6. معالجة الأمان

### أمان المفتاح

- لا يمر المفتاح داخل `SiteProfile`
- لا تُكتب قيمة المفتاح في السجلات
- يتم استخدام `ApiKeyStore` فقط عند الإرسال

### أمان الطلبات

- `HTTPS` فقط
- لا تتبع redirects إلى مضيف آخر
- لا تُرسل بيانات اعتماد داخل URL
- لا يتم إرسال الطلب إذا كان URL غير صالح

---

## 7. تسلسل التنفيذ

### الترتيب الفعلي

1. `SendGuard`
2. `OutgoingQueue`
3. `RetryPolicy`
4. `RemoteDispatchRepository`
5. `UI Status Monitoring`
6. `Feature Flag`
7. `Production Approval`

### السبب

لأن التطبيق يجب أن يحقق أولاً:

- إنكار الإرسال غير الموثق
- إدارة queue
- التحكم في retry
- التتبع

ثم فقط يسمح بإرسال الحزم الفعلية.

---

## 8. توحيد القيم

تعاون الطبقات التالية على نفس القيم:

- `SiteProfile`: مصدر البيانات الأساسية
- `AppSettingsStore`: مخزن الملف الحالي
- `RequestThrottlePolicy`: استخدام القيم في retry
- `RemoteDispatchRepository`: تطبيق القيم في كل دفعة

لا يُسمح لأي طبقة أخرى بتحديث القيم مباشرةً، لأن ذلك يخلق حالة غير موحدة.

---

## 9. حالة التشغيل

تتضمن البنية حالات تشغيل واضحة:

- `ready` : النموذج جاهز للإرسال
- `blocked` : لا يسمح بالإرسال
- `queued` : منتظر في queue
- `sending` : جاري الإرسال
- `retry_waiting` : ينتظر retry
- `sent` : تم التسليم
- `failed` : فشل نهائي

---

## 10. الخلاصة

تعتمد هذه البنية على فصل واضح بين:

- إعدادات الموقع
- الحقول المحلية
- queue والإدارة
- retry/throttling
- الإرسال الفعلي

وهذا الفصل هو ما يضمن أن التطبيق لا يسيء فهم الإعدادات كإرسال فعلي، ويظل متوافقًا مع خطة الإعدادات الأساسية حتى يتم اعتماد عقد المنصة رسميًا.
