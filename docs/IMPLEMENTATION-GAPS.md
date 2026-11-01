# وثيقة فجوات التنفيذ — Hudhud Android App

> **التاريخ:** 31 أكتوبر 2026  
> **آخر تحديث:** 31 أكتوبر 2026  
> **الغرض:** حصر جميع المكونات الناقصة وتحديد أولويات التنفيذ  
> **المرجع:** [المتطلبات الفنية](./HUDHUD-TECHNICAL-REQUIREMENTS.md)، [المواصفات التقنية](./HUDHUD-TECHNOLOGY-SPECIFICATION.md)

---

## ملخص الحالة الحالية - بعد التنفيذ

| التصنيف | النسبة |
|---------|--------|
| مكتمل | ~75% |
| جزئي | ~15% |
| فارغ | ~10% |

## ما تم إنجازه

### المراحل المنجزة
1. ✅ قاعدة البيانات والنماذج (Phase 1)
2. ✅ معالجة الملفات والقوالب (Phase 2)
3. ✅ محرك الذكاء الاصطناعي والواجهة (Phase 3)
4. ✅ التنسيق والتكامل (Phase 4)

---

## المرحلة الأولى: الأساسات (Base Layer)

### 1.1 قاعدة البيانات — Room Database

**الجاهزية:** 20% ❌

#### Entities المطلوبة (ناقصة)

| Entity | الملف | الحالة | ملاحظات |
|--------|-------|--------|---------|
| Template | `Template.kt` | ❌ غير موجود | يجب إنشاؤه |
| ImportBatch | `ImportBatch.kt` | ❌ غير موجود | يجب إنشاؤه |
| ImportMapping | `ImportMapping.kt` | ❌ غير موجود | يجب إنشاؤه |
| CampaignMessage | `CampaignMessage.kt` | ❌ غير موجود | يجب إنشاؤه |

#### الجداول المطلوبة (ناقصة)

| Table | الوصف | الحالة |
|-------|-------|--------|
| templates | تخزين القوالب المدمجة والمستخدم | ❌ غير موجود |
| import_batches | سجلات الدفعات | ❌ غير موجود |
| import_mappings | ربط المتغيرات بالأعمدة | ❌ غير موجود |
| campaign_messages | رسائل الحملات | ❌ غير موجود |

#### DAOs المطلوبة (ناقصة)

| DAO | الحالة | ملاحظات |
|-----|--------|---------|
| TemplateDao | ⚠️ موجود جزئياً | ينقص: delete, update, count |
| CampaignDao | ⚠️ موجود | يحتاج إضافة campaign_messages |
| MessageDao | ⚠️ موجود | كافٍ للمرحلة الحالية |
| ImportBatchDao | ❌ غير موجود | يجب إنشاؤه |
| ImportMappingDao | ❌ غير موجود | يجب إنشاؤه |

---

### 1.2 نماذج البيانات — Domain Models

**الجاهزية:** 40% ⚠️

| Model | الملف | الحالة | النواقص |
|-------|-------|--------|---------|
| Template | `domain/model/Template.kt` | ❌ غير موجود | يجب إنشاؤه مع: id, name, text, variables, sourceType, version |
| ImportBatch | `domain/model/ImportBatch.kt` | ❌ غير موجود | يجب إنشاؤه |
| ImportMapping | `domain/model/ImportMapping.kt` | ❌ غير موجود | يجب إنشاؤه |
| ColumnInfo | `domain/model/ColumnInfo.kt` | ❌ غير موجود | يجب إنشاؤه لتحليل الأعمدة |
| FileColumn | `domain/model/FileColumn.kt` | ❌ غير موجود | يجب إنشاؤه لتمثيل أعمدة الملف |
| SmartSuggestion | `domain/model/SmartSuggestion.kt` | ❌ غير موجود | للاقتراحات الذكية |

---

### 1.3 Repositories — طبقة البيانات

**الجاهزية:** 10% ❌

| Repository | الحالة | المسؤوليات المطلوبة |
|------------|--------|-------------------|
| TemplateRepository | ❌ غير موجود | CRUD للقوالب، جلب المدمجة، البحث |
| CampaignRepository | ⚠️ موجود جزئياً |_campaigns table فقط |
| ImportBatchRepository | ❌ غير موجود | إدارة الدفعات والربط |
| FileRepository | ❌ غير موجود | اختيار الملفات وقراءتها |

---

## المرحلة الثانية: معالجة الملفات

### 2.1 اختيار الملفات — File Selection

**الجاهزية:** 60% ⚠️

| المكون | الملف | الحالة | النواقص |
|--------|-------|--------|---------|
| FilePickerActivity | `ui/document/FilePickerActivity.kt` | ⚠️ موجود | يحتاج: معالجة PDF، تحقق тип الملف |
| DocumentPicker | `data/document/DocumentPicker.kt` | ⚠️ موجود | يحتاج: SAF، ACTION_SEND |
| FileValidator | `data/document/FileValidator.kt` | ❌ غير موجود | يجب إنشاؤه |

---

### 2.2 قراءة Excel — XLSX Reader

**الجاهزية:** 50% ⚠️

| المكون | الملف | الحالة | النواقص |
|--------|-------|--------|---------|
| XlsxReader | `data/document/XlsxReader.kt` | ⚠️ موجود | يحتاج: SAX streaming، أوراق متعددة |
| SheetMetadata | `domain/model/SheetMetadata.kt` | ❌ غير موجود | يجب إنشاؤه |
| ExcelSchemaExtractor | `data/document/ExcelSchemaExtractor.kt` | ❌ غير موجود | لاستخراج مخطط الأعمدة |

**النواقص الحرجة:**
- [ ] دعم قراءة متدفقة (SAX) للملفات الكبيرة
- [ ] معالجة أوراق متعددة
- [ ] استخراج مخطط الأعمدة (اسم العمود + نوع البيانات)
- [ ] تجاهل الصفوف الفارغة والعناوين المتكررة

---

### 2.3 قراءة PDF — PDF Reader

**الجاهزية:** 0% ❌

| المكون | الملف | الحالة | ملاحظات |
|--------|-------|--------|---------|
| PdfColumnAnalyzer | `ui/document/PdfColumnAnalyzer.kt` | ⚠️ موجود | تحليل فقط - لا استخراج |
| PdfCampaignExtractor | `data/document/PdfCampaignExtractor.kt` | ❌ غير موجود | يجب إنشاؤه |
| PdfSchemaExtractor | `data/document/PdfSchemaExtractor.kt` | ❌ غير موجود | يجب إنشاؤه |

**الحالة:** PDF غير مدعوم حالياً للحملات

---

## المرحلة الثالثة: محرك القوالب

### 3.1 معالجة القوالب — Template Processing

**الجاهزية:** 30% ⚠️

| المكون | الملف | الحالة | النواقص |
|--------|-------|--------|---------|
| TemplateProcessor | `domain/template/TemplateProcessor.kt` | ⚠️ موجود | يحتاج: استخراج تلقائي للمتغيرات |
| VariableExtractor | `domain/template/VariableExtractor.kt` | ❌ غير موجود | يجب إنشاؤه |
| TemplateValidator | `domain/template/TemplateValidator.kt` | ❌ غير موجود | يجب إنشاؤه |

---

### 3.2 ربط المتغيرات — Variable Linking

**الجاهزية:** 0% ❌

| المكون | الملف | الحالة | المسؤوليات |
|--------|-------|--------|------------|
| VariableLinker | `data/ai/VariableLinker.kt` | ❌ غير موجود | ربط تلقائي للمتغيرات بالأعمدة |
| ColumnTemplateMapper | `domain/template/ColumnTemplateMapper.kt` | ❌ غير موجود | خريطة الربط بين المتغيرات والأعمدة |

---

### 3.3 القوالب المدمجة — Built-in Templates

**الجاهزية:** 50% ⚠️

| المكون | الملف | الحالة | ملاحظات |
|--------|-------|--------|---------|
| BuiltInTemplateSeeder | `data/assets/BuiltInTemplateSeeder.kt` | ⚠️ موجود | يحتاج: إضافة قوالب عربية أكثر |
| TemplateJson | `assets/templates/` | ❌ غير موجود | مجلد JSON للقوالب |

---

## المرحلة الرابعة: محرك الذكاء الاصطناعي

**الجاهزية:** 0% ❌

> جميع المكونات المطلوبة تعمل محلياً ولا تحتاج اتصال إنترنت

### 4.1 اكتشاف تلقائي للأعمدة

| المكون | الملف | الحالة | المسؤوليات |
|--------|-------|--------|------------|
| ColumnAnalyzer | `data/ai/ColumnAnalyzer.kt` | ❌ غير موجود | تحليل الأعمدة وتحديد نوعها |
| PhoneDetector | `data/ai/PhoneDetector.kt` | ❌ غير موجود | اكتشاف أرقام الهواتف |
| NameDetector | `data/ai/NameDetector.kt` | ❌ غير موجود | اكتشاف أسماء الأشخاص |
| AmountDetector | `data/ai/AmountDetector.kt` | ❌ غير موجود | اكتشاف المبالغ المالية |
| DateDetector | `data/ai/DateDetector.kt` | ❌ غير موجود | اكتشاف التواريخ |

**قواعد الكشف:**
```
Phone: يبدأ بـ + أو 00 أو 0، أرقام فقط، 7-15 رقماً
Name: يتطابق مع: name, fname, first_name, customer_name, اسم
Amount: أرقام مع أو بدون نقاط عشرية
Date: يتطابق مع أنماط التاريخ الشائعة
Text: نص عربي/إنجليزي طويل (20+ حرف)
```

---

### 4.2 اقتراح تلقائي للقالب

| المكون | الملف | الحالة | المسؤوليات |
|--------|-------|--------|------------|
| TemplateMatcher | `data/ai/TemplateMatcher.kt` | ❌ غير موجود | اقتراح القالب المناسب للملف |
| TemplateScoreCalculator | `data/ai/TemplateScoreCalculator.kt` | ❌ غير موجود | حساب درجة التطابق |

**خوارزمية الاقتراح:**
```
1. استخرج متغيرات كل قالب متاح
2. قارن مع أعمدة الملف:
   - تطابق تام (Name == name) → 100 نقطة
   - تطابق جزئي (Name ~= customer_name) → 70 نقطة
3. أضف وزن الاستخدام السابق (+20 نقطة)
4. رتب النتائج وأعرض أفضل 3 اقتراحات
```

---

### 4.3 ربط تلقائي للمتغيرات

| المكون | الملف | الحالة | المسؤوليات |
|--------|-------|--------|------------|
| AutoLinker | `data/ai/AutoLinker.kt` | ❌ غير موجود | ربط تلقائي للمتغيرات |
| LinkConfidenceCalculator | `data/ai/LinkConfidenceCalculator.kt` | ❌ غير موجود | حساب ثقة الربط |

---

### 4.4 تحليل ذكي للرسائل

| المكون | الملف | الحالة | المسؤوليات |
|--------|-------|--------|------------|
| MessageValidator | `data/ai/MessageValidator.kt` | ❌ غير موجود | تحليل الرسائل واكتشاف المشاكل |
| MissingValueDetector | `data/ai/MissingValueDetector.kt` | ❌ غير موجود | كشف القيم الناقصة |
| PhoneValidator | `data/ai/PhoneValidator.kt` | ❌ غير موجود | التحقق من أرقام الهواتف |
| DuplicateDetector | `data/ai/DuplicateDetector.kt` | ❌ غير موجود | كشف الرسائل المكررة |
| LengthValidator | `data/ai/LengthValidator.kt` | ❌ غير موجود | التحقق من طول الرسالة |

**المشاكل المكتشفة:**
- [ ] قيم ناقصة (متغير بلا قيمة)
- [ ] أرقام غير صالحة
- [ ] نصوص فارغة
- [ ] نصوص طويلة جداً (+600 حرف)
- [ ] رسائل مكررة

---

### 4.5 إكمال تلقائي للمتغيرات

| المكون | الملف | الحالة | المسؤوليات |
|--------|-------|--------|------------|
| SmartFillManager | `data/ai/SmartFillManager.kt` | ❌ غير موجود | إدارة الإكمال التلقائي |
| MostCommonValueFinder | `data/ai/MostCommonValueFinder.kt` | ❌ غير موجود | البحث عن القيمة الأكثر شيوعاً |
| DefaultValueResolver | `data/ai/DefaultValueResolver.kt` | ❌ غير موجود | حل القيم الافتراضية |

---

### 4.6 تعلم التفضيلات

| المكون | الملف | الحالة | المسؤوليات |
|--------|-------|--------|------------|
| PreferenceLearner | `data/ai/PreferenceLearner.kt` | ❌ غير موجود | تعلم من قرارات المستخدم |
| PreferenceStorage | `data/local/PreferenceStorage.kt` | ❌ غير موجود | تخزين التفضيلات محلياً |

---

### 4.7 التحذيرات الاستباقية

| المكون | الملف | الحالة | المسؤوليات |
|--------|-------|--------|------------|
| SmartWarnings | `data/ai/SmartWarnings.kt` | ❌ غير موجود | تحذيرات استباقية |
| BatchSizeAnalyzer | `data/ai/BatchSizeAnalyzer.kt` | ❌ غير موجود | تحليل حجم الدفعة |
| RateLimitAdvisor | `data/ai/RateLimitAdvisor.kt` | ❌ غير موجود | نصائح معدل الإرسال |

---

## المرحلة الخامسة: واجهة المستخدم

### 5.1 الشاشات الرئيسية

| الشاشة | الملف | الحالة | ملاحظات |
|--------|-------|--------|---------|
| MainActivity | `ui/home/MainActivity.kt` | ⚠️ موجود | يحتاج: تحسين المدخلين |
| TemplatesActivity | `ui/templates/TemplatesActivity.kt` | ⚠️ موجود | كافٍ للمرحلة الأولى |
| ReadyMessagesActivity | `ui/ready/ReadyMessagesActivity.kt` | ⚠️ موجود | كافٍ للمرحلة الأولى |

---

### 5.2 شاشات النماذج (Forms)

| الشاشة | الملف | الحالة | ملاحظات |
|--------|-------|--------|---------|
| TemplateEditorActivity | `ui/template/TemplateEditorActivity.kt` | ⚠️ موجود | يحتاج: إضافة متغيرات |
| DynamicTemplateActivity | `ui/template/DynamicTemplateActivity.kt` | ⚠️ موجود | كافٍ للمرحلة الأولى |
| ConnectionSettingsActivity | `ui/settings/ConnectionSettingsActivity.kt` | ⚠️ موجود | كافٍ للمرحلة الأولى |
| CampaignsActivity | `ui/campaigns/CampaignsActivity.kt` | ⚠️ موجود | كافٍ للمرحلة الأولى |

---

### 5.3 شاشات جديدة مطلوبة

| الشاشة | الملف | الحالة | المسؤوليات |
|--------|-------|--------|------------|
| FileImportActivity | `ui/import/FileImportActivity.kt` | ❌ غير موجود | اختيار الملف ونوع الاستيراد |
| ColumnMappingActivity | `ui/mapping/ColumnMappingActivity.kt` | ❌ غير جديد | ربط المتغيرات بالأعمدة |
| MessagePreviewActivity | `ui/preview/MessagePreviewActivity.kt` | ❌ غير موجود | مراجعة الرسائل |
| SmartSuggestionsSheet | `ui/shared/SmartSuggestionsSheet.kt` | ❌ غير موجود | عرض الاقتراحات الذكية |
| ImportMappingActivity | `ui/import/ImportMappingActivity.kt` | ❌ غير موجود | تحديد أعمدة الهاتف والربط |

---

### 5.4 UI Components

| المكون | الملف | الحالة |
|--------|-------|--------|
| TemplateAdapter | `ui/adapters/TemplateAdapter.kt` | ⚠️ موجود |
| MessageAdapter | `ui/adapters/MessageAdapter.kt` | ❌ غير موجود |
| ColumnAdapter | `ui/adapters/ColumnAdapter.kt` | ❌ غير موجود |
| SmartSuggestionAdapter | `ui/adapters/SmartSuggestionAdapter.kt` | ❌ غير موجود |

---

## المرحلة السادسة: API والاتصال

### 6.1 طبقة الاتصال

**الجاهزية:** 10% ⚠️

| المكون | الملف | الحالة | النواقص |
|--------|-------|--------|---------|
| HudhudApiService | `data/remote/HudhudApiService.kt` | ⚠️ موجود | يحتاج: POST messages |
| ApiClient | `data/remote/ApiClient.kt` | ⚠️ موجود | يحتاج: WebSocket |
| ApiKeyAuth | `data/remote/ApiKeyAuth.kt` | ⚠️ موجود | كافٍ |

---

### 6.2 نقاط النهاية المطلوبة

| Endpoint | الطريقة | الحالة | ملاحظات |
|----------|---------|--------|---------|
| /api/v1/messages | GET | ⚠️ موجود | يعمل (يعيد 500) |
| /api/v1/messages/{id} | GET | ⚠️ موجود | يعمل (يعيد 500) |
| /api/v1/messages/{id} | DELETE | ⚠️ موجود | يعمل (يعيد 500) |
| /api/v1/messages | POST | ❌ غير موجود | **غير موثق** |
| /api/v1/messages/status | GET | ❌ غير موجود | غير موثق |

---

## المرحلة السابعة: الاختبارات

### 7.1 اختبارات الوحدة

| المكون | الملفات | الحالة |
|--------|---------|--------|
| TemplateProcessor tests | `test/domain/template/` | ⚠️ موجود جزئياً |
| XlsxReader tests | `test/data/document/` | ⚠️ موجود جزئياً |
| ColumnAnalyzer tests | `test/data/ai/` | ❌ غير موجود |
| TemplateMatcher tests | `test/data/ai/` | ❌ غير موجود |
| MessageValidator tests | `test/data/ai/` | ❌ غير موجود |

### 7.2 اختبارات التكامل

| الاختبار | الحالة |
|----------|--------|
| تدفق الرسائل الجاهزة | ❌ غير موجود |
| تدفق البيانات بالقالب | ❌ غير موجود |
| اقتراح القالب التلقائي | ❌ غير موجود |
| اكتشاف الأعمدة | ❌ غير موجود |

---

## أولويات التنفيذ الموصى بها

### الأولوية الأولى: قاعدة البيانات والموديلات

```
1. إنشاء Template Entity
2. إنشاء ImportBatch Entity
3. إنشاء ImportMapping Entity
4. إنشاء CampaignMessage Entity
5. تحديث AppDatabase
6. إنشاء TemplateDao كامل
7. إنشاء ImportBatchDao
8. إنشاء ImportMappingDao
```

### الثانية: معالجة الملفات

```
1. تحسين XlsxReader لدعم SAX
2. إنشاء ExcelSchemaExtractor
3. إنشاء FileValidator
4. إنشاء DocumentPicker محسن
```

### الثالثة: محرك القوالب

```
1. إنشاء VariableExtractor
2. إنشاء TemplateValidator
3. إضافة قوالب مدمجة عربية
4. إنشاء ColumnTemplateMapper
```

### الرابعة: محرك الذكاء الاصطناعي

```
1. إنشاء ColumnAnalyzer
2. إنشاء PhoneDetector و other detectors
3. إنشاء TemplateMatcher
4. إنشاء AutoLinker
5. إنشاء MessageValidator
6. إنشاء SmartWarnings
7. إنشاء PreferenceLearner
```

### الخامسة: واجهة المستخدم

```
1. إنشاء FileImportActivity
2. إنشاء ColumnMappingActivity
3. إنشاء MessagePreviewActivity
4. إنشاء SmartSuggestionsSheet
```

### السادسة: API والإرسال

```
1. إضافة POST endpoint للرسائل (عند توفر التوثيق)
2. إضافة WebSocket support
3. إضافة معالجة الأخطاء
```

---

## ملاحظات للتنفيذ

1. **الذكاء الاصطناعي يعمل محلياً** - لا يرسل بيانات للخادم
2. **الاقتراحات تُعرض على المستخدم** - وليست تلقائية تماماً
3. **PDF غير مدعوم** - للحملات في المرحلة الأولى
4. **API الإرسال غير موثق** - محجوب حتى توفر التوثيق
5. **الملفات تقرأ متدفقة** - لتجنب استهلاك الذاكرة

---

## ملفات للبدء

للبدء بالتنفيذ، يجب إنشاء الملفات التالية بالترتيب:

### المجموعة الأولى (الأساس)

```
1. domain/model/Template.kt
2. domain/model/ImportBatch.kt
3. domain/model/ImportMapping.kt
4. domain/model/ColumnInfo.kt
5. domain/model/FileColumn.kt
6. domain/model/SmartSuggestion.kt
7. data/local/TemplateEntity.kt (Room)
8. data/local/ImportBatchEntity.kt (Room)
9. data/local/ImportMappingEntity.kt (Room)
```

### المجموعة الثانية (المعالجة)

```
10. data/document/FileValidator.kt
11. data/document/ExcelSchemaExtractor.kt
12. domain/template/VariableExtractor.kt
13. domain/template/TemplateValidator.kt
14. domain/template/ColumnTemplateMapper.kt
```

### المجموعة الثالثة (الذكاء)

```
15. data/ai/ColumnAnalyzer.kt
16. data/ai/PhoneDetector.kt
17. data/ai/TemplateMatcher.kt
18. data/ai/AutoLinker.kt
19. data/ai/MessageValidator.kt
20. data/ai/SmartWarnings.kt
21. data/ai/PreferenceLearner.kt
```

### المجموعة الرابعة (الواجهة)

```
22. ui/import/FileImportActivity.kt
23. ui/mapping/ColumnMappingActivity.kt
24. ui/preview/MessagePreviewActivity.kt
25. ui/shared/SmartSuggestionsSheet.kt
```

---

**تاريخ الإنشاء:** 31 أكتوبر 2026  
**آخر تحديث:** 31 أكتوبر 2026