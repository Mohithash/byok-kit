package __PKG__.ui

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

/**
 * Read-aloud on the device's text-to-speech engine. Long text is split into sentence-sized chunks under the
 * engine's input limit and queued, so whole answers can be read. [speaking] and [available] are Compose state,
 * so a play/stop button can just read them. Create one per screen with [rememberSpeaker], which shuts it down
 * when the screen leaves composition. Needs the TTS_SERVICE `<queries>` entry in the manifest on Android 11+.
 */
class Speaker(context: Context) {
    /** True while an utterance from the latest [speak] call is queued or playing. */
    var speaking by mutableStateOf(false); private set

    /** False once the device's TTS engine failed to start (none installed or disabled) — hide the button then. */
    var available by mutableStateOf(true); private set

    private val main = Handler(Looper.getMainLooper())
    private var ready = false
    private var closed = false
    private var pending: Pair<String, Locale?>? = null
    private var session = 0
    private var lastId: String? = null

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        main.post {
            if (closed) return@post
            if (status == TextToSpeech.SUCCESS) {
                ready = true
                pending?.let { (text, locale) -> pending = null; speak(text, locale) }
            } else { available = false; pending = null; speaking = false }
        }
    }

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) = finish(utteranceId, last = true)
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = finish(utteranceId, last = false)
            override fun onError(utteranceId: String?, errorCode: Int) = finish(utteranceId, last = false)
            override fun onStop(utteranceId: String?, interrupted: Boolean) = finish(utteranceId, last = false)
        })
    }

    /** An utterance ended: the session is over when its last chunk finished, or when any chunk failed or stopped. */
    private fun finish(id: String?, last: Boolean) {
        main.post { if (id != null && id.startsWith("s$session-") && (!last || id == lastId)) speaking = false }
    }

    /**
     * Reads [text] aloud, replacing anything already playing. Markdown is flattened to plain speech first.
     * [locale] picks the voice language when the engine has it (defaults to the device language).
     */
    fun speak(text: String, locale: Locale? = null) {
        if (closed || !available) return
        val plain = plainText(text)
        if (plain.isBlank()) { stop(); return }
        if (!ready) { pending = plain to locale; speaking = true; return }
        val lang = locale ?: Locale.getDefault()
        if (runCatching { tts.isLanguageAvailable(lang) >= TextToSpeech.LANG_AVAILABLE }.getOrDefault(false)) runCatching { tts.language = lang }
        session++
        val max = runCatching { TextToSpeech.getMaxSpeechInputLength() }.getOrDefault(4000).coerceIn(200, 3900)
        val parts = chunks(plain, max)
        lastId = "s$session-${parts.lastIndex}"
        speaking = true
        parts.forEachIndexed { i, part ->
            val r = tts.speak(part, if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, "s$session-$i")
            if (r == TextToSpeech.ERROR) { tts.stop(); speaking = false; return }
        }
    }

    /** Starts reading [text], or stops if something is already being read — one button does both. */
    fun toggle(text: String, locale: Locale? = null) = if (speaking) stop() else speak(text, locale)

    /** Stops speech at once and clears the queue. */
    fun stop() {
        pending = null
        session++
        if (ready) runCatching { tts.stop() }
        speaking = false
    }

    /** Releases the engine. The speaker can't be used afterwards. */
    fun shutdown() {
        if (closed) return
        stop()
        closed = true
        runCatching { tts.shutdown() }
    }

    companion object {
        /** Flattens Markdown (headings, emphasis, code fences, links, bullets, tables) into text that reads well aloud. */
        fun plainText(markdown: String): String = markdown
            .replace(Regex("```[\\w-]*"), " ")
            .replace(Regex("!\\[([^\\]]*)]\\([^)]*\\)"), "$1")
            .replace(Regex("\\[([^\\]]+)]\\([^)]*\\)"), "$1")
            .replace(Regex("(?m)^\\s{0,3}#{1,6}\\s*"), "")
            .replace(Regex("(?m)^\\s*>\\s?"), "")
            .replace(Regex("(?m)^\\s*([-*+]|\\d+[.)])\\s+"), "")
            .replace(Regex("(?m)^\\s*\\|?\\s*:?-{3,}.*$"), "")
            .replace(Regex("(?m)^[ \\t]*\\||\\|[ \\t]*$"), "")
            .replace("|", ", ")
            .replace(Regex("[*_~`]+"), "")
            .replace(Regex("[ \\t]+"), " ")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()

        /** Splits [text] into pieces of at most [max] chars, preferring paragraph, then sentence, then word breaks. */
        fun chunks(text: String, max: Int): List<String> {
            val out = ArrayList<String>()
            val buf = StringBuilder()
            fun flush() { if (buf.isNotBlank()) out += buf.toString().trim(); buf.setLength(0) }
            val sentences = text.split(Regex("(?<=[.!?。！？])\\s+|\\n+")).filter { it.isNotBlank() }
            for (s in sentences) {
                if (s.length > max) {
                    flush()
                    var rest = s
                    while (rest.length > max) {
                        val cut = rest.lastIndexOf(' ', max).takeIf { it > max / 2 } ?: max
                        out += rest.substring(0, cut).trim(); rest = rest.substring(cut)
                    }
                    if (rest.isNotBlank()) buf.append(rest.trim())
                    continue
                }
                if (buf.length + s.length + 1 > max) flush()
                if (buf.isNotEmpty()) buf.append(' ')
                buf.append(s)
            }
            flush()
            return out
        }
    }
}

/** A [Speaker] tied to this composition: created once, stopped and released when the composable leaves. */
@Composable
fun rememberSpeaker(): Speaker {
    val ctx = LocalContext.current
    val speaker = remember { Speaker(ctx) }
    DisposableEffect(speaker) { onDispose { speaker.shutdown() } }
    return speaker
}
