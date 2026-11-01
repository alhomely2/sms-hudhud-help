# ملخص مشروع Hudhud Android App

> **آخر تحديث:** 31 أكتوبر 2026

---

## الهيكل النهائي للتطبيق

```
com.hudhud.app/
├── data/
│   ├── ai/                              # محرك الذكاء الاصطناعي
│   │   ├── AutoLinker.kt               ✅ ربط تلقائي للمتغيرات
│   │   ├── BatchSizeAnalyzer.kt        ✅ تحليل حجم الدفعة
│   │   ├── ColumnAnalyzer.kt           ✅ تحليل الأعمدة
│   │   ├── MessageValidator.kt         ✅ التحقق من الرسائل
│   │   ├── MostCommonValueFinder.kt    ✅ البحث عن القيم الشائعة
│   │   ├── PreferenceLearner.kt        ✅ تعلم التفضيلات
│   │   ├── RateLimitAdvisor.kt         ✅ نصائح معدل الإرسال
│   │   ├── SmartWarnings.kt            ✅ التحذيرات الذكية
│   │   └── TemplateMatcher.kt          ✅ اقتراح القوالب
│   ├── document/                       # معالجة الملفات
│   │   ├── ExcelSchemaExtractor.kt     ✅ استخراج مخطط Excel
│   │   └── FileValidator.kt            ✅ التحقق من الملفات
│   ├── local/                          # قاعدة البيانات
│   │   ├── AppDatabase.kt              ✅ محدث
│   │   ├── ImportBatchDao.kt           ✅ جديد
│   │   ├── ImportBatchEntity.kt        ✅ جديد
│   │   ├── ImportMappingDao.kt         ✅ جديد
│   │   ├── ImportMappingEntity.kt      ✅ جديد
│   │   ├── PreferenceStorage.kt        ✅ جديد
│   │   └── TemplateEntity.kt           ✅ موجود
│   ├── remote/
│   │   └── HudhudResult.kt             ✅ جديد
│   └── repository/
│       ├── ImportBatchRepository.kt    ✅ جديد
│       └── TemplateRepository.kt       ✅ جديد
├── domain/
│   ├── model/                          # نماذج البيانات
│   │   ├── ColumnInfo.kt               ✅ جديد
│   │   ├── FileColumn.kt               ✅ جديد
│   │   ├── MessageDraft.kt             ✅ جديد
│   │   └── SmartSuggestion.kt          ✅ جديد
│   ├── template/                       # محرك القوالب
│   │   ├── ColumnTemplateMapper.kt     ✅ جديد
│   │   ├── MessageGenerator.kt         ✅ جديد
│   │   ├── TemplateValidator.kt        ✅ جديد
│   │   └── VariableExtractor.kt        ✅ جديد
│   └── usecase/
│       └── SmartImportUseCase.kt       ✅ جديد
├── ui/
│   ├── import/
│   │   └── FileImportActivity.kt       ✅ جديد
│   ├── mapping/
│   │   └── ColumnMappingActivity.kt    ✅ جديد
│   ├── preview/
│   │   ├── MessagePreviewActivity.kt   ✅ جديد
│   │   └── MessagePreviewAdapter.kt    ✅ جديد
│   └── shared/
│       └── SmartSuggestionsBottomSheet.kt ✅ جديد
└── util/
    └── Resource.kt                     ✅ جديد
```

---

## الميزات الذكية المُنفذة

### 1. اكتشاف تلقائي للأعمدة
- تحليل محتوى الأعمدة وتحديد نوعها (هاتف، اسم، مبلغ، تاريخ، نص)
- عرض نسبة الثقة لكل اكتشاف
- دعم اللغة العربية والإنجليزية

### 2. اقتراح تلقائي للقالب
- تحليل هيكل الملف ومقارنته بالقوالب المتاحة
- حساب درجة التطابق (0-100%)
- استخدام سجل التفضيلات لتحسين الاقتراحات

### 3. ربط تلقائي للمتغيرات
- ربط كل متغير ({{Name}}، {{Amount}}...) بالأعمدة المناسبة
- استخدام多种 استراتيجيات (تطابق تام، جزئي، نوعي)
- عرض ثقة كل ربط

### 4. تحليل ذكي للرسائل
- كشف القيم الناقصة
- التحقق من أرقام الهواتف
- كشف الرسائل المكررة
- التحقق من طول الرسالة

### 5. التحذيرات الاستباقية
- تحذير عند حجم دفعة كبير
- تحذير عند نسبة أخطاء عالية
- تحذير عند وجود رسائل فارغة

### 6. تعلم التفضيلات
- حفظ قرارات المستخدم السابقة
- تحسين الاقتراحات المستقبلية
- تخزين محلي على الجهاز

---

## ملفات الـ Layout الجديدة

- `activity_file_import.xml` - شاشة استيراد الملفات
- `activity_column_mapping.xml` - شاشة ربط الأعمدة
- `activity_message_preview.xml` - شاشة المعاينة
- `item_message_preview.xml` - عنصر القائمة
- `bottom_sheet_smart_suggestions.xml` - شريحة الاقتراحات
- `item_smart_suggestion.xml` - عنصر الاقتراح

---

## حالة المكونات

| المكون | الحالة |
|--------|--------|
| قاعدة البيانات (Room) | ✅ مكتمل |
| نماذج البيانات | ✅ مكتمل |
| Repositories | ✅ مكتمل |
| معالجة الملفات | ✅ مكتمل |
| محرك القوالب | ✅ مكتمل |
| محرك الذكاء الاصطناعي | ✅ مكتمل |
| Activities | ✅ مكتمل |
| Layouts | ✅ مكتمل |
| API والإرسال | ⚠️ بانتظار التوثيق |

---

## المتطلبات المطلوبة للإكمال

1. **توثيق API المنصة** - لإرسال الرسائل
2. **اختبار على أجهزة حقيقية** - للتحقق من الأداء
3. **إعداد CI/CD** - للنشر
4. **اختبارات الوحدة** - لتغطية الكود

---

## كيفية الاستخدام

### المسار 1: رسائل جاهزة
1. اضغط على "رسائل جاهزة" في الشاشة الرئيسية
2. اختر ملف Excel يحتوي أرقام الهواتف ونصوص الرسائل
3. حدد عمود الهاتف وعمود النص (أو اترك الذكاء يكتشف تلقائياً)
4. راجع الرسائل وأصلح أي أخطاء
5. أرسل

### المسار 2: بيانات تحتاج قالب
1. اضغط على "تجهيز حملة رسائل" في الشاشة الرئيسية
2. اختر ملف Excel الذي يحتوي بيانات
3. الذكاء الاصطناعي يقترح قالباً مناسباً
4. ربط المتغيرات بالأعمدة (أو اترك الذكاء يربط تلقائياً)
5. راجع الرسائل وأصلح أي أخطاء
6. أرسل