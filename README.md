# Streamly — সেটআপ গাইড (Core Version)

## ফাইল
| ফাইল | কাজ |
|---|---|
| `schema.sql` | ডাটাবেস টেবিল, RLS, স্টোরেজ পলিসি, **সার্ভার-সাইড Admin allowlist** |
| `index.html` | পুরো ওয়েবসাইট + প্লেয়ার + অ্যাডমিন প্যানেল (কোনো বিল্ড স্টেপ নেই) |
| `_redirects` | Cloudflare Pages / Netlify-এর জন্য SPA রুটিং (`/watch/...` লিংক কাজ করবে) |
| `android/MainActivity.kt` | Android WebView (Fullscreen, Back, Offline, নিরাপদ লিংক হ্যান্ডলিং) |

## ধাপ ১ — Supabase
1. supabase.com-এ প্রজেক্ট খুলুন।
2. **SQL Editor** → `schema.sql` পেস্ট করে Run করুন। (এতে অ্যাডমিন ইমেইল `alsamimhossain83@gmail.com` allowlist-এ বসে যায়।)
3. **Project Settings → API** থেকে `Project URL` ও `anon public key` কপি করুন → `index.html`-এর `CFG`-তে বসান।
   `service_role` key কখনো ফ্রন্টএন্ডে দেবেন না।

## ধাপ ২ — লগইন (Authentication)
- **Providers → Google**: Google Cloud Console-এ OAuth Client (Web) বানিয়ে Client ID/Secret বসান। Supabase-এ দেখানো Callback URL-টি Google-এর "Authorized redirect URIs"-এ দিন।
- **URL Configuration**: Site URL = আপনার সাইটের ঠিকানা; Redirect URLs-এ `https://আপনার-সাইট/login` যোগ করুন।
- **Email provider** চালু রাখুন (Magic Link)।

## ধাপ ৩ — হোস্টিং
Cloudflare Pages বা Netlify-তে `streamsite` ফোল্ডার আপলোড করুন (HTTPS স্বয়ংক্রিয়)। `SITE_URL` সেট করুন `CFG`-তে।

## ধাপ ৪ — অ্যাডমিন
- অ্যাডমিন ইমেইল দিয়ে লগইন করলে নেভিগেশনে "অ্যাডমিন" আসবে।
- **অ্যাডমিন ইমেইল বদলাতে/যোগ করতে:** SQL Editor-এ  
  `insert into admin_allowlist(email) values ('new@gmail.com');`  
  এটি env variable নয়, ডাটাবেসের টেবিলে থাকে, কারণ এটি স্ট্যাটিক সাইট। ক্লায়েন্ট এই টেবিল পড়তে/লিখতে পারে না।
- আসল নিরাপত্তা `is_admin()` + RLS থেকে আসে (ভিডিও/ক্যাটাগরি/সেটিংস/লগ/স্টোরেজ লেখা শুধু অ্যাডমিনের)। ফ্রন্টএন্ডে বাটন লুকানো শুধু সুবিধার জন্য।

## ভিডিও আপলোড ও স্ট্রিমিং
- Supabase Storage-এ সরাসরি আপলোড: MP4/WebM, ফ্রি প্ল্যানে ফাইল প্রতি ~৫০MB।
- বড় ভিডিও/অ্যাডাপ্টিভ স্ট্রিমিং: Cloudflare Stream বা অন্য সার্ভিসে আপলোড করে **HLS (.m3u8) লিংক** ভিডিও ফর্মে বসান। প্লেয়ার hls.js দিয়ে কোয়ালিটি সিলেক্টর দেখাবে।
- পাবলিশ করার আগে সোর্স না থাকলে ডাটাবেসই (constraint) আটকে দেয়।

## Android WebView
1. নতুন Android Studio প্রজেক্ট → `MainActivity.kt` বসান, `SITE_URL` বদলান।
2. `AndroidManifest.xml`-এ: `<uses-permission android:name="android.permission.INTERNET"/>` এবং activity-তে  
   `android:configChanges="orientation|screenSize|keyboardHidden"` ও `android:hardwareAccelerated="true"`।
3. **Fullscreen** কাজ করে `WebChromeClient.onShowCustomView` দিয়ে — এটি ছাড়া ভিডিও ফুলস্ক্রিন হবে না।
4. ⚠ **Google লগইন WebView-তে Google নিজেই ব্লক করে।** অ্যাডমিন লগইন Chrome/Android ব্রাউজারে করুন, অথবা WebView-তে "ইমেইল লগইন লিংক" ব্যবহার করুন। (WebView ও Chrome-এর সেশন আলাদা।)

## যা এই Core Version-এ আছে
হোম (ফিচার্ড/কন্টিনিউ ওয়াচিং/ট্রেন্ডিং/লেটেস্ট/পপুলার/ক্যাটাগরি সেকশন), ওয়াচ পেজ (MP4+HLS, স্পিড, কোয়ালিটি, PiP, রিট্রাই, প্রগ্রেস সেভ), সার্চ+ফিল্টার+সর্ট, ক্যাটাগরি, হিস্ট্রি, ওয়াচলিস্ট, স্ট্যাটিক পেজ, ৪০৪, ডার্ক/লাইট, অ্যাডমিন (ড্যাশবোর্ড, ভিডিও CRUD+আপলোড প্রগ্রেস, ক্যাটাগরি CRUD, হোমপেজ, সেটিংস, লগ), ডিডুপ্লিকেটেড ভিউ কাউন্ট।

## যা এখনো নেই (পরবর্তী ধাপ)
- Like, Comments, Playlists, Subtitle (.vtt) আপলোড
- Watch Time ও ট্রাফিক অ্যানালিটিক্স গ্রাফ (এখন শুধু ভিউ সংখ্যা)
- Sitemap.xml ও robots.txt অটো-জেনারেশন (Cloudflare Worker/Edge Function লাগবে)। বর্তমানে SEO মেটা ও JSON-LD ব্রাউজারে JS দিয়ে বসে — Google JS রেন্ডার করে, কিন্তু সার্ভার-সাইড রেন্ডারিং সবচেয়ে নির্ভরযোগ্য।
- ভিউ ডিডুপ্লিকেশন `viewer_key` (ব্রাউজারে সংরক্ষিত) দিয়ে হয়; ইচ্ছাকৃত বট ঠেকাতে Edge Function + rate limit যোগ করতে হবে।
- গেস্টের লোকাল হিস্ট্রি/ওয়াচলিস্ট লগইনের সময় অ্যাকাউন্টে মার্জ হয় না।
- Admin লগে "গুরুত্বপূর্ণ পরিবর্তনে কনফার্মেশন" ডিলিট/পাবলিশে আছে, বাকি সেটিংসে নেই।
- নমুনা টেক্সট: About/Privacy/Terms/Copyright পেজের লেখা আপনাকে বদলাতে হবে।

## টেস্ট চেকলিস্ট
১) হোম লোড ২) সার্চ/ফিল্টার ৩) ভিডিও প্লে ও ফুলস্ক্রিন ৪) অ্যাডমিন ইমেইলে লগইন → প্যানেল খোলে ৫) অন্য Gmail-এ `/admin` → "Access Denied" ৬) লগআউট করে `/admin` → লগইন পেজ ৭) অ্যাডমিন আপলোড/এডিট/পাবলিশ/ডিলিট ৮) লগআউট অবস্থায় ব্রাউজার কনসোল থেকে `sb.from('videos').insert(...)` → RLS এরর আসা উচিত ৯) মোবাইল ও WebView-তে ভিডিও + Back বাটন ১০) ইন্টারনেট বন্ধ করে অফলাইন স্ক্রিন।
