package com.teklif.tercuman.ui

import com.teklif.tercuman.translate.Lang

/** Uygulamanın kendi arayüz dili (çeviri dillerinden bağımsız). */
enum class UiLang(val code: String, val flag: String, val label: String) {
    TR("tr", "🇹🇷", "Türkçe"),
    EN("en", "🇬🇧", "English"),
    ZH("zh", "🇨🇳", "中文");

    fun strings(): S = when (this) {
        TR -> Tr
        EN -> En
        ZH -> Zh
    }

    companion object {
        fun fromCode(code: String?): UiLang? = entries.firstOrNull { it.code == code }
    }
}

/** Seçili arayüz dilinin metinleri; arayüz dışı sınıflar (ses, çeviri) buradan okur. */
object L {
    @Volatile
    var current: S = Tr
}

/** Arayüz metinleri. */
interface S {
    val appTitle: String
    val faceToFace: String
    val clearChat: String
    val settings: String
    val back: String
    val auto: String
    val edit: String
    val translate: String
    val cancel: String
    val retry: String
    val translating: String
    val enBridge: String
    val note: String
    val speak: String
    val enterApiKeyFirst: String
    fun translationFailed(detail: String?): String

    // Ayarlar
    val uiLangTitle: String
    val apiKeyLabel: String
    val apiKeyHelp: String
    val speechSection: String
    val openAiLabel: String
    val openAiHelpEmpty: String
    val openAiHelpSet: String
    val micCloseTitle: String
    val micCloseHelp: String
    val onTap: String
    fun seconds(n: Int): String
    val qualityTitle: String
    val qualityFast: String
    val qualityBalanced: String
    val qualityBest: String
    val qualityHelp: String
    val topicLabel: String
    val topicPlaceholder: String
    val topicHelp: String
    val glossaryLabel: String
    val glossaryPlaceholder: String
    val glossaryHelp: String
    val autoSpeakTitle: String
    val autoSpeakHelp: String
    val pinyinTitle: String
    val pinyinHelp: String
    val economyTitle: String
    val economyHelp: String
    val modelTitle: String
    val modelHelp: String

    // Çeviri hataları
    val errApiKey: String
    val errRateLimit: String
    fun errService(code: Int, detail: String?): String
    val errNetwork: String
    val errRefused: String
    val errEmpty: String
    val errParse: String

    // Ses
    val errTtsInit: String
    fun errVoiceMissing(lang: Lang): String
    val errMic: String
    val errMicPermission: String
    val errNoSpeech: String
    val errNotUnderstood: String
    val errSpeechInternet: String
    val errOpenAiKey: String
    val errOpenAiQuota: String
    fun errSpeechCode(code: Int): String
    val errNoRecognizer: String
    val errRecognizerBusy: String
    val errLangUnsupported: String
}

object Tr : S {
    override val appTitle = "Tercüman"
    override val faceToFace = "Yüz yüze mod"
    override val clearChat = "Konuşmayı temizle"
    override val settings = "Ayarlar"
    override val back = "Geri"
    override val auto = "Otomatik"
    override val edit = "Düzelt"
    override val translate = "Çevir"
    override val cancel = "Vazgeç"
    override val retry = "Tekrar dene"
    override val translating = "Çevriliyor…"
    override val enBridge = "EN köprü"
    override val note = "Not"
    override val speak = "Seslendir"
    override val enterApiKeyFirst = "Önce Ayarlar'dan Claude API anahtarını girin."
    override fun translationFailed(detail: String?) = "Çeviri başarısız: $detail"

    override val uiLangTitle = "Uygulama dili"
    override val apiKeyLabel = "Claude API anahtarı"
    override val apiKeyHelp = "console.anthropic.com → API Keys. Sadece bu telefonda saklanır."
    override val speechSection = "Ses tanıma ve otomatik dil algılama"
    override val openAiLabel = "OpenAI API anahtarı (isteğe bağlı, önerilir)"
    override val openAiHelpEmpty = "Boşsa telefonun kendi ses tanıması kullanılır. Doldurursanız Whisper konuşulan dili " +
        "sesten algılar ve bip sesi olmaz. platform.openai.com → API keys."
    override val openAiHelpSet = "Whisper etkin: konuşulan dil sesten otomatik algılanır."
    override val micCloseTitle = "Mikrofon ne zaman kapansın?"
    override val micCloseHelp = "Dokununca: bitirince tuşa tekrar dokunun ya da basılı tutup bırakın. " +
        "Saniye seçeneklerinde konuşma bittikten o kadar sessizlik sonra mikrofon kendiliğinden kapanır."
    override val onTap = "Dokununca"
    override fun seconds(n: Int) = "$n sn"
    override val qualityTitle = "Çeviri kalitesi / hız"
    override val qualityFast = "Hızlı"
    override val qualityBalanced = "Dengeli"
    override val qualityBest = "En iyi"
    override val qualityHelp = "Hızlı: yüz yüze sohbet için önerilir. En iyi: daha yavaş ama daha özenli."
    override val topicLabel = "Görüşmenin konusu (anlam yükleme)"
    override val topicPlaceholder = "Örn: CNC membran kapak üretimi, fiyat teklifi ve teslim süresi görüşmesi"
    override val topicHelp = "Yapay zekâ terimleri bu bağlama göre seçer."
    override val glossaryLabel = "Terim sözlüğü"
    override val glossaryPlaceholder = "membran kapak = membrane cover = 薄膜盖板\nteklif = quotation = 报价"
    override val glossaryHelp = "Her satıra bir terim, dillerin karşılıkları '=' ile ayrılır"
    override val autoSpeakTitle = "Çeviriyi sesli oku"
    override val autoSpeakHelp = "Her çeviri karşı tarafın dilinde otomatik okunur."
    override val pinyinTitle = "Çince altında pinyin okunuşu"
    override val pinyinHelp = "Kapalıyken çeviri daha hızlı biter."
    override val economyTitle = "Ekonomik mod"
    override val economyHelp = "Yalnızca çeviri üretilir; İngilizce köprü, düzeltilmiş metin ve not yazılmaz, " +
        "daha az geçmiş gönderilir. Token kullanımı yaklaşık yarıya iner, çeviri daha hızlı biter."
    override val modelTitle = "Yapay zekâ modeli"
    override val modelHelp = "Haiku: en hızlı ve en ucuz (önerilir). Sonnet: 20 kat pahalı, daha nüanslı. Opus: 40 kat pahalı, en doğru."

    override val errApiKey = "API anahtarı geçersiz. Ayarlardan kontrol edin."
    override val errRateLimit = "Çok fazla istek. Birkaç saniye sonra tekrar deneyin."
    override fun errService(code: Int, detail: String?) = "Çeviri servisi hatası ($code): $detail"
    override val errNetwork = "İnternet bağlantısı yok veya zayıf."
    override val errRefused = "Bu cümle çevrilemedi. Lütfen farklı ifade edin."
    override val errEmpty = "Boş yanıt alındı, tekrar deneyin."
    override val errParse = "Yanıt okunamadı, tekrar deneyin."

    override val errTtsInit = "Sesli okuma motoru başlatılamadı."
    override fun errVoiceMissing(lang: Lang) =
        "Telefonda ${lang.displayName} ses paketi yok. Ayarlar > Metin okuma çıkışı bölümünden indirin."
    override val errMic = "Mikrofon kullanılamıyor."
    override val errMicPermission = "Mikrofon izni verilmedi."
    override val errNoSpeech = "Uzun süre ses gelmedi. Tekrar dokunun."
    override val errNotUnderstood = "Anlaşılamadı, tekrar söyleyin."
    override val errSpeechInternet = "Ses tanıma için internet bağlantısı gerekli."
    override val errOpenAiKey = "OpenAI anahtarı geçersiz. Ayarlardan kontrol edin."
    override val errOpenAiQuota = "OpenAI kotası/bakiyesi yetersiz veya çok fazla istek."
    override fun errSpeechCode(code: Int) = "Ses tanıma hatası ($code)."
    override val errNoRecognizer = "Bu telefonda konuşma tanıma servisi bulunamadı. Google uygulamasını yükleyip " +
        "etkinleştirin ya da Ayarlar'a OpenAI anahtarı girin (Whisper her telefonda çalışır)."
    override val errRecognizerBusy = "Tanıyıcı meşgul, tekrar deneyin."
    override val errLangUnsupported = "Bu dil telefonda desteklenmiyor. Google uygulamasında ilgili dil paketini indirin."
}

object En : S {
    override val appTitle = "Interpreter"
    override val faceToFace = "Face-to-face mode"
    override val clearChat = "Clear conversation"
    override val settings = "Settings"
    override val back = "Back"
    override val auto = "Auto"
    override val edit = "Edit"
    override val translate = "Translate"
    override val cancel = "Cancel"
    override val retry = "Retry"
    override val translating = "Translating…"
    override val enBridge = "EN bridge"
    override val note = "Note"
    override val speak = "Speak"
    override val enterApiKeyFirst = "Enter your Claude API key in Settings first."
    override fun translationFailed(detail: String?) = "Translation failed: $detail"

    override val uiLangTitle = "App language"
    override val apiKeyLabel = "Claude API key"
    override val apiKeyHelp = "console.anthropic.com → API Keys. Stored only on this phone."
    override val speechSection = "Speech recognition and language detection"
    override val openAiLabel = "OpenAI API key (optional, recommended)"
    override val openAiHelpEmpty = "Empty: the phone's own speech recognition is used. Filled: Whisper detects the " +
        "spoken language from the audio and there are no beeps. platform.openai.com → API keys."
    override val openAiHelpSet = "Whisper enabled: the spoken language is detected automatically."
    override val micCloseTitle = "When should the microphone close?"
    override val micCloseHelp = "On tap: tap the button again when done, or hold and release. " +
        "With a seconds option the microphone closes by itself after that much silence."
    override val onTap = "On tap"
    override fun seconds(n: Int) = "$n s"
    override val qualityTitle = "Translation quality / speed"
    override val qualityFast = "Fast"
    override val qualityBalanced = "Balanced"
    override val qualityBest = "Best"
    override val qualityHelp = "Fast is recommended for live conversation. Best is slower but more careful."
    override val topicLabel = "Conversation topic (context)"
    override val topicPlaceholder = "e.g. CNC membrane cover production, quotation and delivery time"
    override val topicHelp = "The AI chooses terms according to this context."
    override val glossaryLabel = "Glossary"
    override val glossaryPlaceholder = "membrane cover = membran kapak = 薄膜盖板\nquotation = teklif = 报价"
    override val glossaryHelp = "One term per line, equivalents separated by '='"
    override val autoSpeakTitle = "Read translation aloud"
    override val autoSpeakHelp = "Every translation is spoken in the other side's language."
    override val pinyinTitle = "Pinyin under Chinese"
    override val pinyinHelp = "Translation finishes faster when off."
    override val economyTitle = "Economy mode"
    override val economyHelp = "Only the translation is generated; no English bridge, corrected text or note, " +
        "and less history is sent. Token use roughly halves and translation finishes faster."
    override val modelTitle = "AI model"
    override val modelHelp = "Haiku: fastest and cheapest (recommended). Sonnet: 20× the cost, more nuanced. Opus: 40×, most accurate."

    override val errApiKey = "Invalid API key. Check it in Settings."
    override val errRateLimit = "Too many requests. Try again in a few seconds."
    override fun errService(code: Int, detail: String?) = "Translation service error ($code): $detail"
    override val errNetwork = "No or weak internet connection."
    override val errRefused = "This sentence could not be translated. Please rephrase."
    override val errEmpty = "Empty response, try again."
    override val errParse = "Response could not be read, try again."

    override val errTtsInit = "Text-to-speech engine could not start."
    override fun errVoiceMissing(lang: Lang) =
        "No ${lang.displayName} voice installed. Download it under Settings > Text-to-speech output."
    override val errMic = "Microphone unavailable."
    override val errMicPermission = "Microphone permission not granted."
    override val errNoSpeech = "No speech for a long time. Tap again."
    override val errNotUnderstood = "Not understood, please repeat."
    override val errSpeechInternet = "Speech recognition needs an internet connection."
    override val errOpenAiKey = "Invalid OpenAI key. Check it in Settings."
    override val errOpenAiQuota = "OpenAI quota/balance exhausted or too many requests."
    override fun errSpeechCode(code: Int) = "Speech recognition error ($code)."
    override val errNoRecognizer = "No speech recognition service found on this phone. Install and enable the Google app, " +
        "or enter an OpenAI key in Settings (Whisper works on every phone)."
    override val errRecognizerBusy = "Recognizer busy, try again."
    override val errLangUnsupported = "This language is not supported on the phone. Download its pack in the Google app."
}

object Zh : S {
    override val appTitle = "翻译官"
    override val faceToFace = "面对面模式"
    override val clearChat = "清空对话"
    override val settings = "设置"
    override val back = "返回"
    override val auto = "自动"
    override val edit = "修改"
    override val translate = "翻译"
    override val cancel = "取消"
    override val retry = "重试"
    override val translating = "翻译中…"
    override val enBridge = "英文桥接"
    override val note = "备注"
    override val speak = "朗读"
    override val enterApiKeyFirst = "请先在设置中输入 Claude API 密钥。"
    override fun translationFailed(detail: String?) = "翻译失败：$detail"

    override val uiLangTitle = "应用语言"
    override val apiKeyLabel = "Claude API 密钥"
    override val apiKeyHelp = "console.anthropic.com → API Keys。仅保存在本手机。"
    override val speechSection = "语音识别与自动语言检测"
    override val openAiLabel = "OpenAI API 密钥（可选，推荐）"
    override val openAiHelpEmpty = "留空则使用手机自带的语音识别。填写后由 Whisper 从语音中识别所说语言，且没有提示音。platform.openai.com → API keys。"
    override val openAiHelpSet = "Whisper 已启用：自动识别所说语言。"
    override val micCloseTitle = "麦克风何时关闭？"
    override val micCloseHelp = "点击：说完后再点一下按钮，或按住说话、松开结束。选择秒数时，说完后静音达到该时长麦克风自动关闭。"
    override val onTap = "点击"
    override fun seconds(n: Int) = "$n 秒"
    override val qualityTitle = "翻译质量 / 速度"
    override val qualityFast = "快速"
    override val qualityBalanced = "均衡"
    override val qualityBest = "最佳"
    override val qualityHelp = "面对面交谈推荐“快速”。“最佳”更慢但更细致。"
    override val topicLabel = "谈话主题（上下文）"
    override val topicPlaceholder = "例如：CNC 薄膜盖板生产、报价与交期洽谈"
    override val topicHelp = "AI 会根据此背景选择术语。"
    override val glossaryLabel = "术语表"
    override val glossaryPlaceholder = "薄膜盖板 = membrane cover = membran kapak\n报价 = quotation = teklif"
    override val glossaryHelp = "每行一个术语，各语言对应词用 '=' 分隔"
    override val autoSpeakTitle = "朗读译文"
    override val autoSpeakHelp = "每条译文自动用对方语言朗读。"
    override val pinyinTitle = "中文下方显示拼音"
    override val pinyinHelp = "关闭后翻译完成更快。"
    override val economyTitle = "经济模式"
    override val economyHelp = "只生成译文；不写英文桥接、修正文本和备注，发送更少的历史记录。Token 用量约减半，翻译更快。"
    override val modelTitle = "AI 模型"
    override val modelHelp = "Haiku：最快最便宜（推荐）。Sonnet：费用 20 倍，更细腻。Opus：费用 40 倍，最准确。"

    override val errApiKey = "API 密钥无效，请在设置中检查。"
    override val errRateLimit = "请求过多，请几秒后重试。"
    override fun errService(code: Int, detail: String?) = "翻译服务错误（$code）：$detail"
    override val errNetwork = "没有网络或网络信号弱。"
    override val errRefused = "这句话无法翻译，请换一种说法。"
    override val errEmpty = "收到空回复，请重试。"
    override val errParse = "无法读取回复，请重试。"

    override val errTtsInit = "语音朗读引擎无法启动。"
    override fun errVoiceMissing(lang: Lang) = "手机未安装 ${lang.displayName} 语音包。请在 设置 > 文字转语音输出 中下载。"
    override val errMic = "麦克风不可用。"
    override val errMicPermission = "未授予麦克风权限。"
    override val errNoSpeech = "长时间没有声音，请再点一下。"
    override val errNotUnderstood = "没听清，请再说一遍。"
    override val errSpeechInternet = "语音识别需要网络连接。"
    override val errOpenAiKey = "OpenAI 密钥无效，请在设置中检查。"
    override val errOpenAiQuota = "OpenAI 额度/余额不足或请求过多。"
    override fun errSpeechCode(code: Int) = "语音识别错误（$code）。"
    override val errNoRecognizer = "本手机未找到语音识别服务。请安装并启用 Google 应用，或在设置中输入 OpenAI 密钥（Whisper 在任何手机上都可用）。"
    override val errRecognizerBusy = "识别器忙，请重试。"
    override val errLangUnsupported = "手机不支持该语言。请在 Google 应用中下载相应语言包。"
}
