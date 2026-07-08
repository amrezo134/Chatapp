# Creatix Chat — تطبيق دردشة شخص لشخص (Android + Firebase)

## المميزات
- تسجيل دخول/حساب بإيميل وباسورد (Firebase Authentication)
- رسائل لحظية (real-time) من Firestore
- "بيكتب دلوقتي..." (Typing indicator)
- علامة "تمت القراءة" (Read receipts)
- إشعارات Push حتى لو التطبيق مقفول، بترسل **مباشرة من جهاز لجهاز** (بدون سيرفر، بدون Cloud Functions، بدون خطة Blaze أو بطاقة بنكية)
- minSdk 26 — target/compileSdk 35

## هيكل المشروع
```
ChatApp/
  app/            ← كود التطبيق (Kotlin + Compose)
  firestore.rules ← قواعد أمان قاعدة البيانات
  firebase.json   ← إعدادات الربط بمشروع Firebase
```

## خطوات الإعداد (من المتصفح بالكامل، بدون كمبيوتر وبدون بطاقة بنكية)

### 1) إنشاء مشروع Firebase
1. روح على console.firebase.google.com → Add project
2. أضف تطبيق Android بالـ package: `com.creatix.chatapp`
3. نزّل `google-services.json` وحطه جوه `app/` (يفضل يترفع على GitHub عادي، مش سري)

### 2) تفعيل Authentication و Firestore
- من القائمة الجانبية: Authentication → Get started → فعّل "Email/Password"
- Firestore Database → Create database → Start in production mode
- من تبويب "Rules" جوه Firestore، امسح اللي موجود، والصق محتوى ملف `firestore.rules` كامل، ودوس Publish (مفيش داعي لأي CLI)

### 3) تجهيز الإشعارات (Service Account بصلاحية محدودة)
1. من ⚙️ Project Settings → Service accounts
2. اعمل حساب خدمة جديد بصلاحية **Firebase Cloud Messaging API** بس (Role: "Firebase Cloud Messaging API Admin") — منستخدمش الحساب الافتراضي اللي له صلاحيات أدمن كاملة، تقليلًا للخطر لو الملف اتسرب
3. Generate new private key → هينزل ملف JSON
4. حوّل الملف ده لنص Base64 (فيه مواقع مجانية للتحويل، أو من موبايلك بأي تطبيق "base64 encode")
5. في مستودع GitHub بتاعك: Settings → Secrets and variables → Actions → New repository secret
   - الاسم: `FCM_SERVICE_ACCOUNT_BASE64`
   - القيمة: النص المُحوّل بالكامل

⚠️ **الملف ده لازم يفضل سري تمامًا.** أي حد يوصله يقدر يبعت إشعارات باسم تطبيقك. متحطوش أبدًا في الكود العلني — الـ workflow بيحقنه تلقائيًا وقت البناء بس.

### 4) البناء عبر GitHub Actions
- روح لتبويب Actions في المستودع → اختار "Build APK" → Run workflow
- بعد ما يخلص، هتلاقي الـ APK جاهز للتنزيل من داخل الـ run نفسه (Artifacts)

## هيكلة قاعدة البيانات في Firestore
```
users/{uid}
  - displayName, email, photoUrl, online, lastSeen, fcmToken

chats/{chatId}          // chatId = دمج الـ uid بتوع الاتنين مرتبين أبجديًا
  - participants: [uidA, uidB]
  - lastMessage, lastTimestamp
  - typing: { uidA: true/false, uidB: true/false }
  chats/{chatId}/messages/{messageId}
    - senderId, receiverId, text, timestamp, seen
```

## إزاي الإشعارات شغالة تقنيًا
لما اليوزر يبعت رسالة، `ChatRepository.sendMessage()` بيحفظها في Firestore، وبعدين بيجيب `fcmToken` بتاع المستقبل من مستنده في `users/`، وبيبعتله إشعار مباشر عن طريق `FcmPushSender` (JWT موقّع بمفتاح الـ Service Account → access token من جوجل → طلب POST لـ FCM HTTP v1 API). كل ده من غير أي سيرفر وسيط.
