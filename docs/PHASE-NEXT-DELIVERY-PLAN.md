# خطة المرحلة التالية: طبقة الإرسال المحمية

## 1. المقدمة

تهدف هذه المرحلة إلى بناء طبقة إرسال آمنة ومحمية قبل أي تنفيذ فعلي لرفع الحملات أو إرسال الرسائل إلى المنصة. الهدف ليس تشغيل الإرسال مباشرة، بل بناء بنية مؤسسية تضمن:

- اعتماد موقع المنصة النشط فقط
- وجود مفتاح API محمي ومخزن بشكل آمن
- التحقق من صحة URL وHTTPS
- احترام إعدادات throttling وretry
- منع أي إرسال قبل اعتماد عقد المنصة وموثوقية endpoint
- تسجيل كل محاولة بإشارات واضحة مثل `X-Correlation-Id` وHTTP status

يُستند هذا المخطط إلى ما تم تنفيذه فعليًا في:

- `AppSettingsStore`
- `SiteProfile` و`SiteProfileValidator`
- `RequestThrottlePolicy`
- `ApiKeyStore`
- `HudhudClient`
- `CampaignStorage`

وتبقى الحملات والقوالب في طبقة معالجة البيانات المحلية، بينما الإرسال الحقيقي إلى المنصة يبقى مؤجلًا حتى يتم اعتماد العقد الرسمي من جهة المنصة.

---

## 2. الهدف من المرحلة

بناء طبقة تحت اسم "Send Gate / Outgoing Queue" تضمن:

1. عدم إرسال أي رسالة إذا لم يكن هناك موقع نشط صالح.
2. عدم إرسال أي رسالة إذا كان مفتاح API مفقودًا أو غير صالح.
3. عدم إرسال أي رسالة إذا كان endpoint أو contract غير موثوق.
4. تطبيق `delay`, `batchSize`, `maxRetryAttempts`, وtimeouts من إعدادات الموقع.
5. تطبيق سياسات إعادة المحاولة فقط للأخطاء العابرة.
6. تسجيل كل محاولة بنجاح/فشل/تأخير/معدل/مؤشر ارتباط.
7. تمكين الإرسال الحقيقي فقط بعد اعتماد المنصة رسميًا.

---

## 3. مخرجات المرحلة

خلال هذه المرحلة سيتم تحقيق ما يلي:

- Gate حماية قبل الإرسال
- قائمة انتظار محلية للرسائل المستعدة للإرسال
- سياسة retry/throttle موحدة ومرفقة بإعدادات كل موقع
- طبقة remote sender مع batch processing
- تسجيل وتتبّع حالة الحملة والرسائل
- Feature flag/kill switch يمنع التشغيل افتراضيًا

---

## 4. بنية التنفيذ المقترحة

### 4.1 Gate الحماية (Send Guard)

يُنشأ كائن أو use case مستقل مثل:

- `PlatformSendGuard`
- `DeliveryEligibilityChecker`

المسؤوليات:

- التحقق من وجود profile نشط صالح من `AppSettingsStore`
- التحقق من وجود API key في `ApiKeyStore`
- التحقق من أن `baseUrl` صالح وHTTPS فقط
- التحقق من عدم وجود URL يحتوي على بيانات اعتماد أو query/fragment
- التحقق من أن العقد أو endpoint تم اعتماده من المنصة
- تصنيف الحالة إلى:
  - `approved`
  - `blocked_missing_profile`
  - `blocked_missing_api_key`
  - `blocked_unapproved_contract`
  - `blocked_invalid_url`
  - `blocked_invalid_state`

النتيجة:
- لا يمكن تشغيل أي إرسال فعلي إلا إذا كانت الحالة `approved`.

### 4.2 قائمة الانتظار المحلية (Outgoing Queue)

يتم إضافة نموذج جديد في Room/SQLite، مثل:

- `OutgoingMessageQueueEntity`
- `CampaignSendJobEntity`

الحقول المقترحة:

- `id`
- `campaignId`
- `messageId`
- `siteProfileId`
- `status`
- `retryCount`
- `maxRetryAttempts`
- `queuedAt`
- `updatedAt`
- `lastAttemptAt`
- `payloadJson`
- `lastError`
- `correlationId`

الحالات المقترحة:

- `pending`
- `queued`
- `sending`
- `sent`
- `failed`
- `throttled`
- `blocked`

المسؤولية:

- إدخال الرسائل الجاهزة إلى قائمة الانتظار
- جلب الرسائل في حالة `pending` فقط
- تحديث الحالة بعد كل محاولة
- منع تكرار التسليم عبر `idempotency` أو حالة الرسالة

### 4.3 طبقة throttling وretry

يتم الاعتماد على القيم الموجودة في:

- `SiteProfile`
- `RequestThrottlePolicy`

ويتم تنفيذ:

- حساب delay قبل كل دفعة
- حساب `backoff` بين المحاولات
- احترام `Retry-After` من الخادم
- تطبيق حدود `batchSize` لكل ملف موقع
- عدم محاولة retry في الحالات الآتية:
  - `400`
  - `401`
  - `403`
  - `404`
  - `422`
  - `500` غير موثق
- السماح فقط في الحالات العابرة الموثقة، مثل:
  - `429`
  - `502`
  - `503`
  - `504`

### 4.4 تنفيذ طبقة الإرسال إلى المنصة

يتم إنشاء repository/service جديد مثل:

- `RemoteDispatchRepository`
- `PlatformMessageSender`

المسؤوليات:

- جلب `pending` أو `queued` من قائمة الانتظار
- بناء payload لكل رسالة أو لكل دفعة
- اختيار الموقع النشط وملف الموقع المناسب
- إرسال دفعات وفق `batchSize`
- تطبيق delay بين الدفعات
- تحديث حالة الرسالة بناءً على حالة الاستجابة
- تسجيل `httpCode`, `X-Correlation-Id`, `Retry-After`, وheaders الأخرى

### 4.5 طبقة التتبع والتسجيل

يجب تسجيل كل محاولة من خلال نظام audit/logging مستقل، مع:

- `campaignId`
- `messageId`
- `siteProfileId`
- `status`
- `httpCode`
- `correlationId`
- `retryCount`
- `lastError`
- `rateLimitHeaders`
- `timestamp`

هذا يضمن تمكين المراقبة دون كشف المفتاح أو البيانات الحساسة.

---

## 5. تفكيك المرحلة إلى Tasks

### المرحلة 1: حماية الإرسال قبل التنفيذ

#### Task 1.1 — إنشاء Send Guard
- إنشاء كلاس `PlatformSendGuard`
- قراءة الموقع النشط من `AppSettingsStore`
- قراءة مفتاح API من `ApiKeyStore`
- التحقق من اعتماد العقد المشروع
- إرجاع حالة `approved` أو `blocked`

#### Task 1.2 — تعطيل زر الإرسال تلقائيًا
- إذا كانت الحالة `blocked`، تعطيل الزر أو إخفاؤه
- عرض رسالة واضحة للمشغل

#### Task 1.3 — اختبار الوحدة
- حالات الموقع غير موجود
- مفتاح API مفقود
- URL غير صالح
- contract غير موثق
- contract موثق بشكل صحيح

### المرحلة 2: قائمة انتظار الإرسال المحلية

#### Task 2.1 — تصميم قاعدة البيانات
- إضافة `OutgoingMessageQueueEntity`
- إضافة DAO وrepository
- دعم `pending`, `sending`, `sent`, `failed`, `blocked`

#### Task 2.2 — ربط الحملة بجداول القوائم
- تحويل الحملة المحلية إلى عناصر queue
- فصل حالة الحملة المحلية عن حالة الإرسال لها

#### Task 2.3 — مسار الاستئناف
- إعادة محاولة الرسائل الفاشلة فقط إذا كانت retryable
- حفظ retryCount و maxRetryAttempts

### المرحلة 3: سياسة throttling والتكرار

#### Task 3.1 — ربط إعدادات الموقع
- قراءة `delayBetweenMessagesMs`
- قراءة `batchSize`
- قراءة `maxRetryAttempts`
- قراءة `connect/read/call timeout`

#### Task 3.2 — تنفيذ `RequestThrottlePolicy` بالكامل
- تطبيق `calculateDelayMs`
- تطبيق `backoffDelayMs`
- احترام `Retry-After`

#### Task 3.3 — منع التكرار غير الآمن
- لا تكرر 401/403/422
- لا تكرر 500 دون توثيق
- لا تكرر في الحالات غير المعروفة

### المرحلة 4: التنفيذ الفعلي للإرسال

#### Task 4.1 — بناء payload
- بناء هيكل payload النهائي حسب عقد المنصة
- تضمين `X-Correlation-Id`
- تضمين metadata اللازمة

#### Task 4.2 — تشغيل batch sender
- جلب batch من queue
- إرسال وفق `batchSize`
- تطبيق delay بين الدفعات

#### Task 4.3 — تحويل الاستجابة إلى حالة
- `sent`
- `retryable_failed`
- `permanent_failed`
- `blocked`

### المرحلة 5: التتبع ومراقبة الحملة

#### Task 5.1 — UI لحالة الإرسال
- pending/queued/sending/sent/failed/blocked
- عدد الرسائل المنجزة
- عدد الرسائل الفاشلة
- آخر خطأ
- آخر retry

#### Task 5.2 — سجل الحملة
- correlation ID
- response code
- retry after
- headers المرتبطة

### المرحلة 6: اختبار واعتراف السلامة

#### Task 6.1 — اختبارات unit
- `SendGuard`
- `RequestThrottlePolicy`
- `OutgoingMessageQueue`

#### Task 6.2 — اختبارات integration
- 429 مع Retry-After
- 503 و 504
- 401 و403
- 500 غير موثق

#### Task 6.3 — اختبار dry-run
- فحص endpoint عبر `HudhudClient.probe`
- التأكد أن المنصة تستجيب كما هو متوقع قبل الإطلاق الحقيقي

### المرحلة 7: التشغيل الآمن

#### Task 7.1 — Feature flag
- `enable_remote_send = false` بشكل افتراضي

#### Task 7.2 — اعتماد العقد الرسمي
- يجب أن يأتي اعتماد المنصة من مالك المنصة أو جهة المسؤول
- لا يتم التشغيل قبل ذلك

#### Task 7.3 — rollout تدريجي
- مرحلة اختبار
- حملة محدودة
- التوسع بعد التأكد الكامل

---

## 6. معيار القبول النهائي

تُعتبر المرحلة ناجحة فقط إذا تحقق التالي:

- لا توجد أي رسالة تُرسل دون مرور `Send Guard`
- كل رسالةٍ تُخزن أولاً في queue محلي
- كل retry يحسب من إعدادات الموقع و`Retry-After`
- لا توجد retries غير آمنة
- كل طلب له `X-Correlation-Id`
- الرسائل تُحدّث حالة تلقائيًا بعد كل محاولة
- التشغيل الحقيقي غير مفعّل إلا بعد اعتماد العقد الرسمي

---

## 7. الترتيب العملي للتنفيذ

1. إنشاء `Send Guard`
2. إنشاء `Outgoing Queue`
3. دمج `RequestThrottlePolicy`
4. بناء `RemoteDispatchRepository`
5. عرض حالة الحملة في واجهة المستخدم
6. اختبارات الوحدة والتكامل
7. تشغيل حقيقي بعد اعتماد عقد المنصة

---

## 8. مراجعة التغطية مقابل خطة الإعدادات الأساسية

تمت مراجعة هذه الخطة مقابل بنود [SETTINGS-MANAGEMENT-PLAN.md](./SETTINGS-MANAGEMENT-PLAN.md) للتأكد من شمولها، وكانت نتائج المراجعة كما يلي:

- تغطية ملفات المواقع والاتصال: نعم
  - الموقع النشط، التحقق من HTTPS، رفض بيانات اعتماد URL، وقت التشغيل، timeouts، API key، وGate الحماية.
- تغطية throttling: نعم
  - `delay`, `backoff`, `Retry-After`, `429`, `X-Ratelimit-*`, إعادة المحاولة فقط للأخطاء العابرة.
- تغطية batch size: نعم
  - ربط batch size بكل موقع، مع فصلها عن Room batch insertion المحلي.
- تغطية منع الإرسال غير الموثق: نعم
  - `Send Guard` مع `approved/blocked` وfeature flag/kill switch.
- تغطية idempotency: نعم
  - queue + state + retry policy + correlation + deduplication عند إعادة التشغيل.
- تغطية التتبع والمراقبة: نعم
  - `X-Correlation-Id` وHTTP status وlast error وheaders logging.
- تغطية الاعتماد على العقد الرسمي: نعم
  - لا يوجد إرسال فعلي قبل اعتماد المنصة، مع جدولة rollout تدريجي.
- تغطية فصل البيانات المحلية عن الإرسال الفعلي: نعم
  - الحملات لا تُرسل مباشرة من Room، بل تمر عبر queue ثم remote sender.

أي نقطة لم تُغطي بشكل صريح؟
- لا توجد نقطة جوهرية مفقودة في الخطة الحالية، لكن ينبغي إضافة وصفٍ واضحٍ لآلية deduplication عند إعادة التشغيل، ومعالجة الحقول في queue لتجنب إرسال نفس الرسالة أكثر من مرة. هذا تم تضمينه داخل `OutgoingQueue` و`idempotency`.

---

## 9. الخلاصة

المرحلة الحالية تمثل انتقالًا جاهزًا من "تهيئة الإعدادات والبيانات المحلية" إلى "بناء طبقة إرسال محمية وقابلة للقيادة". لا يُسمح بالانتقال إلى الإرسال الحقيقي إلا بعد:

- اعتماد عقد المنصة
- اختبار endpoint
- قبول payload contract
- تأكيد idempotency/behavior
- إتاحة feature flag للمشغل

وبذلك تظل المجموعات الحالية — القوالم والحملات والإعدادات — محمية من الإرسال غير الموثق، مع استعداد كامل للمرحلة التشغيلية عند توفر العقد الرسمي.
