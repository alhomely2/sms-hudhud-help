# دليل تنفيذ طبقة الإرسال المحمية

## 1. الهدف

هذا المستند يشرح كيف يتم تنفيذ طبقة الإرسال المحمية في التطبيق بشكل عملي، مع الحفاظ على جميع القيود التي وردت في خطة الإعدادات والحدود التشغيلية.

الهدف النهائي:

- السماح فقط بالإرسال عندما يكون هناك موقع نشط صالح
- التحقق من أن API key موجود ومخزن بشكل آمن
- عدم تشغيل الإرسال قبل اعتماد عقد المنصة
- تطبيق throttling وretry بشكل آمن
- استئناف الرسائل في قائمة الانتظار دون ازدواجية
- تسجيل كل محاولة عبر correlation ID وHTTP status

---

## 2. مكونات التنفيذ

### 2.1 AppSettingsStore

المسؤولية:
- قراءة ملف الموقع النشط
- حفظ كل ملف موقع بشكل مستقل
- إدارة `delayBetweenMessagesMs`, `batchSize`, `maxRetryAttempts`, وtimeouts
- الاحتفاظ بحالة الموافقة الرسمية على العقد في كل ملف موقع عبر `isContractApproved`
- التحقق من القيم قبل الحفظ

القيم الحالية موجودة في `SiteProfile` و`AppSettingsStore`، ولا ينبغي تعديلها مباشرة من أي شاشة أو repository إلا عبر هذه الطبقة. 
الترتيب الحالي في المشروع يلزم حفظ حالة الموافقة كتاريخ/حالة مستمر في ملف الموقع نفسه، وليس كعلامة مؤقتة فقط في الواجهة أو callback.

### 2.2 ApiKeyStore

المسؤولية:
- تخزين مفتاح API بشكل مشفر باستخدام Android Keystore
- قراءته عند الحاجة فقط
- عرضه بشكل masked وليس بشكل كامل
- عدم تسجيل المفتاح في السجلات أو الاستثناءات

يسمح هذا بإبقاء المفتاح بعيدًا عن أي كائن `SiteProfile` أو log message.

### 2.3 HudhudClient

المسؤولية:
- تنفيذ `probe()` إلى endpoint الموثق
- قراءة `X-Correlation-Id`
- قراءة `Retry-After` وratelimit headers
- إرجاع `ConnectionResult` مع حالة الوصول والمصادقة والخدمة

يجب استخدام هذه الطبقة فقط لاختبار الاتصال وتأكيد توفر endpoint، وليس كإرسال فعلي للحملات.

### 2.4 RequestThrottlePolicy

المسؤولية:
- حساب التأخير بين الطلبات
- حساب backoff عند الفشل
- تحليل `Retry-After` بصيغة الثواني أو RFC 1123
- تصنيف HTTP codes إلى retryable أو permanent

قواعد التشغيل المهمة:

- `429` => retryable
- `502/503/504` => retryable
- `401/403/422/400` => not retryable
- `500` => لا يُعاد محاولة إلا إذا كان موثقًا كخطأ مؤقت

---

## 3. طبقة الحماية (Send Gate)

### 3.1 الفكرة

كل فحص قبل الإرسال يجب أن يمر عبر طبقة واحدة: `PlatformSendGuard`.

### 3.2 منطق القرار

يُفحص ما يلي قبل كل إرسال فعلي:

1. هل يوجد profile نشط؟
2. هل ملف الموقع صالح؟
3. هل `baseUrl` HTTPS؟
4. هل يوجد API key لهذا الموقع؟
5. هل تم اعتماد العقد الرسمي للمنصة؟
6. هل تم التحقق من endpoint باستخدام `probe()`؟
7. هل الطلب الحالي في حالة `approved`؟

إذا كان أي شرط مفقودًا، تُعد المحاولة `blocked` بدلًا من `sending`.

### 3.3 حالة الإرجاع

مثال:

- `APPROVED`
- `BLOCKED_MISSING_PROFILE`
- `BLOCKED_MISSING_API_KEY`
- `BLOCKED_INVALID_URL`
- `BLOCKED_UNAPPROVED_CONTRACT`
- `BLOCKED_INVALID_STATE`

### 3.4 التنفيذ الحالي في المشروع

النسخة الحالية تنفذ هذه الطبقة فعليًا عبر `PlatformSendGuard` بحيث يتم التحقق قبل أي إرسال حقيقي:

- وجود ملف موقع نشط (`getActiveProfile()` / `getProfile()`)
- تفعيل الملف (`isEnabled`)
- أن يكون الرابط `https://` فقط
- عدم احتواء بيانات الاعتماد أو الاستعلام أو fragment في الرابط
- وجود `API Key` في `ApiKeyStore` أو مصدر lookup المعتمد
- أن يكون `isContractApproved == true`
- أن يكون `contractApprovedProvider()` مُفعلًا في وقت التشغيل

ملاحظة مهمة: التحقق من `probe()` يبقى كخطوة خارجية ضمن إعدادات التشغيل أو الاختبار، لكنه ليس شرطًا داخل `evaluate()` نفسه. شرط الحماية الأساسي في التنفيذ الحالي هو أن يكون الملف النشط صالحًا وقيمته `isContractApproved == true`، مع وجود مفتاح API وشروط URL السليمة. إذا فشل أي شرط، يتم إرجاع `SendGateResult(status, detail)` بدلًا من السماح بالإرسال. هذا يضمن أن الإرسال الحقيقي يبقى معطلًا افتراضيًا حتى الموافقة الرسمية على العقد.

### 3.5 تنفيذ عملي

```kotlin
class PlatformSendGuard(
    private val appSettings: AppSettingsStore,
    private val apiKeyLookup: (String) -> String?,
    private val contractApprovedProvider: () -> Boolean = { false },
) {
    fun evaluate(profileId: String? = null): SendGateResult {
        val targetProfileId = profileId ?: appSettings.getActiveProfile().id
        val profile = appSettings.getProfile(targetProfileId)
            ?: return SendGateResult.blocked(
                SendGateStatus.BLOCKED_MISSING_PROFILE,
                "No site profile exists for id=$targetProfileId",
            )

        if (!profile.isEnabled) {
            return SendGateResult.blocked(
                SendGateStatus.BLOCKED_INVALID_STATE,
                "The selected profile is disabled.",
            )
        }

        if (!isValidBaseUrl(profile.baseUrl)) {
            return SendGateResult.blocked(
                SendGateStatus.BLOCKED_INVALID_URL,
                "The site base URL must be HTTPS and must not contain credentials or query fragments.",
            )
        }

        val apiKey = apiKeyLookup(targetProfileId)
        if (apiKey.isNullOrBlank()) {
            return SendGateResult.blocked(
                SendGateStatus.BLOCKED_MISSING_API_KEY,
                "No API key is stored for the selected profile.",
            )
        }

        if (!profile.isContractApproved || !contractApprovedProvider()) {
            return SendGateResult.blocked(
                SendGateStatus.BLOCKED_UNAPPROVED_CONTRACT,
                "Platform contract and endpoint approval are required before sending.",
            )
        }

        return SendGateResult.approved()
    }
}
```

---

## 4. قائمة الانتظار المحلية

### 4.1 لماذا تحتاج queue؟

لأن الحملة محلية، لكن الإرسال إلى المنصة غير موثوق بعد. لذلك لا بد من فصل:

- الحملة المحلية
- الرسائل الجاهزة
- الرسائل المرسلة
- حالات الفشل وretry

### 4.2 الهيكل المقترح

```kotlin
@Entity(tableName = "outgoing_messages")
data class OutgoingMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val campaignId: Long,
    val messageId: Long,
    val siteProfileId: String,
    val status: String,
    val retryCount: Int = 0,
    val maxRetryAttempts: Int = 3,
    val payloadJson: String,
    val lastError: String? = null,
    val correlationId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val lastAttemptAt: Long? = null,
)
```

### 4.3 حالات الرسالة

حالات الرسالة في التنفيذ الحالي تتوافق مع ثوابت `OutgoingMessageEntity` الفعلية:

- `PENDING`
- `QUEUED`
- `SENDING`
- `SENT`
- `FAILED`
- `RETRY_WAITING`
- `BLOCKED`

ملاحظة: عند توثيق الحالة في المستندات أو UI، يجب استخدام أسماء الثوابت الحقيقية في التطبيق بدلًا من صيغة lowercase، لأن Room وrepository يَستخدمون القيم الكبيرة (`PENDING`, `SENT` ...).

### 4.4 آلية deduplication

عند إعادة تشغيل التطبيق أو إعادة المحاولة، يجب منع إرسال نفس الرسالة أكثر من مرة من خلال:

- `messageId` فريد داخل الحملة
- `correlationId` فريد لكل محاولة
- تتبع الحالة الحالية في queue
- عدم السماح بحالة `SENDING` مع إعادة إرسال إذا لم يتم تأكيد النتيجة

---

## 5. طبقة Remote Dispatch

### 5.1 مسؤولية الطبقة

هذه الطبقة هي المسؤولة عن:

- سحب الرسائل من queue
- تجهيز payload لكل دفعة
- اختيار الموقع النشط
- تطبيق delay
- تطبيق retry
- تحديث حالة الرسالة

### 5.2 مراحل التنفيذ

1. قراءة قائمة الرسائل في `pending` أو `retry_waiting`
2. التحقق من `SendGuard`
3. قراءة `SiteProfile` المناسب
4. حساب delay باستخدام `RequestThrottlePolicy`
5. إرسال الدفعة
6. إذا نجحت -> `sent`
7. إذا فشلت وكانت retryable -> `retry_waiting`
8. إذا فشلت وكانت permanent -> `failed`

### 5.3 مثال

```kotlin
class RemoteDispatchRepository(
    private val appSettings: AppSettingsStore,
    private val queueDao: OutgoingMessageDao,
    private val sendGuard: PlatformSendGuard,
    private val clientFactory: (SiteProfile) -> HudhudClient,
) {
    suspend fun dispatchNextBatch(siteProfileId: String, maxItems: Int = 50) {
        val profile = appSettings.getProfile(siteProfileId) ?: return
        val gate = sendGuard.evaluate(siteProfileId)
        if (!gate.canProceed) {
            queueDao.markBlocked(siteProfileId, gate.detail ?: "Delivery blocked by send gate")
            return
        }

        val batch = queueDao.loadPendingBatch(siteProfileId, maxItems.coerceAtLeast(1))
        if (batch.isEmpty()) return

        val delayMs = RequestThrottlePolicy.calculateDelayMs(profile)
        if (delayMs > 0L) kotlinx.coroutines.delay(delayMs)

        val client = clientFactory(profile)
        // send batch to server and update row status based on outcome
    }
}
```

هذا المثال يطابق التنفيذ الفعلي في المشروع: أولًا تتأكد من صلاحية `SendGateResult`، ثم تَقوم بتحميل batch من `PENDING` أو `RETRY_WAITING`، وبعدها تُجري العملية الفعلية وتحديث حالة كل رسالة بحسب النتيجة.

---

## 6. سياسة throttling وretry

### 6.1 القراءة من SiteProfile

يجب استخراج القيم التالية من الموقع النشط:

- `delayBetweenMessagesMs`
- `batchSize`
- `maxRetryAttempts`
- `connectTimeoutSeconds`
- `readTimeoutSeconds`
- `callTimeoutSeconds`

### 6.2 القواعد

- كل موقع له حدود خاصة به
- لا تُستخدم حدود موحدة بين المواقع
- لا يسمح للتطبيق بافتراض أن `X-Ratelimit-Limit: 300` يعني 300 طلب/دقيقة في كل حالة
- يطبق الخادم `Retry-After` بشكل أولوي
- ترتيب الأولوية:
  1. `Retry-After` من الخادم
  2. إعدادات الموقع
  3. backoff المتسلسل

### 6.3 مثال حساب delay

```kotlin
val configuredDelay = profile.delayBetweenMessagesMs.coerceAtLeast(0)
val serverDelay = requestThrottlePolicy.parseRetryAfterSeconds(response.header("Retry-After"))
val effectiveDelay = max(configuredDelay, serverDelay?.times(1000) ?: 0L)
```

---

## 7. التتبع

يجب أن يحتوي كل سجل محاولة على:

- `campaignId`
- `messageId`
- `siteProfileId`
- `httpCode`
- `correlationId`
- `retryCount`
- `lastError`
- `timestamp`

لا يكتفي بالتسجيل فقط، بل يجب أن يكون سهل القراءة في UI أو logs.

### 7.1 مثال log payload

```json
{
  "campaignId": 21,
  "messageId": 183,
  "siteProfileId": "default_staging",
  "status": "failed",
  "httpCode": 429,
  "retryCount": 2,
  "correlationId": "a1b2c3",
  "lastError": "Too many requests",
  "timestamp": "2026-11-01T01:00:00Z"
}
```

---

## 8. التشغيل الآمن

### 8.1 Feature flag

يجب أن يكون الإرسال الحقيقي معطّلًا افتراضيًا:

```kotlin
const val ENABLE_REMOTE_SEND = false
```

### 8.2 Kill switch

يجب أن يوجد زر أو إعداد يوقف الإرسال فورًا في أي وقت.

### 8.3 Rollout

التطبيق لا يمر إلى الحالة التشغيلية إلا بعد:

- اعتماد المنصة
- اختبار endpoint
- فحص authentication
- اختبار payload عبر probe
- تأكيد idempotency

---

## 9. خطة التنفيذ المرحلية

### Sprint 1
- إعداد `SendGuard`
- إنشاء queue database schema
- إنشاء DAO وrepository
- إضافة states في UI

### Sprint 2
- ربط `SiteProfile` و`AppSettingsStore`
- تطبيق `RequestThrottlePolicy`
- إضافة retry flow

### Sprint 3
- تنفيذ `RemoteDispatchRepository`
- اختبار batchsend
- logging + correlation

### Sprint 4
- rollout + feature flag + approval gate

---

## 10. معيار النجاح

تُعتبر المرحلة مكتملة عندما:

- لا يوجد إرسال فعلي بدون gate approval
- كل رسالة تذهب أولاً إلى queue
- لا توجد duplicate sent messages
- لا توجد retries غير آمنة
- كل محاولة تُسجّل مع correlation ID
- يمكن استئناف إرسال الحملة بعد إعادة التشغيل

---

## 11. الخلاصة

هذه الطبقة ستمنح التطبيق شبكة إرسال آمنة ومتحكمة، مع فصل واضح بين:

- حفظ الحملة محليًا
- إعدادات الموقع
- throttling وretry
- الإرسال الحقيقي إلى المنصة

وهي النتيجة المطلوبة في الخطة الأساسية، وتضمن أن لا يبدأ التطبيق في إرسال الرسائل قبل الحصول على اعتماد المنصة وبيانات العقد الصحيحة.
