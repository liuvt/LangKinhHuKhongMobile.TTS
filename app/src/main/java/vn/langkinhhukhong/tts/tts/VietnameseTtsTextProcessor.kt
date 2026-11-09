package vn.langkinhhukhong.tts.tts

import java.text.Normalizer
import java.util.Locale

/**
 * Bộ chuẩn hóa kịch bản & phân đoạn nhịp đọc Tiếng Việt chuẩn 1:1 với bản PC (`LangKinhHuKhong.TTS`):
 * - Đối chiếu trực tiếp từ `TextProcessor.cs` và `SpeechPacing.cs`.
 * - Giữ nguyên dấu phẩy (`,`) và dấu hai chấm (`:`) bên trong câu để model VITS/Piper không mất ngữ cảnh thanh điệu Tiếng Việt.
 * - Xử lý dấu ba chấm (`...` / `…`) giữa câu đứng trước chữ thường thành `,` để tránh nuốt từ nối không trọng âm (`thì`, `là`, `mà`, `nhưng`).
 * - Hỗ trợ lệnh nghỉ kịch bản `[pause:500ms]`, `[nghi:1.5s]`, `[break:1000ms]` và lọc bỏ ghi chú `[[...]]`, `[note:...]`.
 * - Chèn khoảng lặng PCM chuẩn xác sau dấu câu kết thúc và ngắt dòng.
 */
object VietnameseTtsTextProcessor {

    const val INITIAL_PAUSE_MS = 160
    const val PERIOD_PAUSE_MS = 310
    const val EXCLAMATION_PAUSE_MS = 350
    const val QUESTION_PAUSE_MS = 330
    const val ELLIPSIS_PAUSE_MS = 420
    const val LINE_PAUSE_MS = 350
    const val PARAGRAPH_PAUSE_MS = 550
    const val SOFT_PAUSE_MS = 100
    const val MAX_PHRASE_LENGTH = 240

    private val closingQuotesRegex = Regex("""[”’"')\]]+$""")
    private val inlineNotesRegex = Regex(
        """\[\[[^\]]*\]\]|\[(?:note|ghichu|comment)\s*:[^\]]*\]""",
        RegexOption.IGNORE_CASE
    )
    private val trailingPauseTagRegex = Regex(
        """\[(?:pause|nghi|break|silence|im)\s*:\s*\d+(?:[.,]\d+)?\s*(?:ms|s)?\s*\]\s*$""",
        RegexOption.IGNORE_CASE
    )
    private val inlinePauseCommandRegex = Regex(
        """^\[(?:pause|nghi|break|silence|im)\s*:\s*(\d+(?:[.,]\d+)?)\s*(ms|s)?\s*\]""",
        RegexOption.IGNORE_CASE
    )
    private val inlineNoteCommandRegex = Regex(
        """^(?:\[\[[^\]]*\]\]|\[(?:note|ghichu|comment)\s*:[^\]]*\])""",
        RegexOption.IGNORE_CASE
    )
    private val trailingEllipsisRegex = Regex("""(?:\.{2,}|…+)\s*([""'”’)\]]*)$""")
    private val anyEllipsisRegex = Regex("""(?:\.{2,}|…+)""")

    data class SpeechSegment(
        val text: String,
        val pauseAfterMs: Int
    )

    /**
     * Chuẩn hóa văn bản đầu vào giống `TextProcessor.NormalizeForTts` trên PC:
     * - Chuẩn hóa Unicode NFC cho dấu thanh Tiếng Việt.
     * - Bỏ ghi chú kịch bản và dòng comment `//`.
     * - Tự động thêm dấu chấm `.` cuối dòng nếu thiếu dấu câu kết thúc để VITS hạ giọng tự nhiên.
     */
    fun normalizeForTts(input: String?): String {
        if (input.isNullOrBlank()) return ""
        val nfcInput = Normalizer.normalize(input, Normalizer.Form.NFC)
        val lines = nfcInput.replace("\r\n", "\n").replace('\r', '\n').split('\n').toMutableList()

        for (i in lines.indices) {
            var line = inlineNotesRegex.replace(lines[i], "").trimEnd()
            if (line.trimStart().startsWith("//")) {
                lines[i] = ""
                continue
            }
            if (line.isBlank()) {
                lines[i] = ""
                continue
            }
            if (trailingPauseTagRegex.containsMatchIn(line)) {
                lines[i] = line
                continue
            }
            val closersMatch = closingQuotesRegex.find(line)
            val checkIdx = if (closersMatch != null) closersMatch.range.first - 1 else line.length - 1
            if (checkIdx >= 0 && line[checkIdx] !in ".!?…;:,。！？；：，、؟۔،") {
                line = StringBuilder(line).insert(checkIdx + 1, '.').toString()
            }
            lines[i] = line
        }

        return lines.joinToString("\n")
    }

    /**
     * Tách văn bản thành các câu đọc tự nhiên kèm khoảng lặng PCM sau mỗi câu (`SpeechPacing.SegmentText`).
     */
    fun segmentText(source: String?): List<SpeechSegment> {
        val result = mutableListOf<SpeechSegment>()
        if (source.isNullOrBlank()) return result

        val input = source.replace("\r\n", "\n").replace('\r', '\n')
        val segment = StringBuilder()
        var consecutiveBreaks = 0

        fun flush(pauseMs: Int) {
            val text = segment.toString().trim()
            segment.clear()
            if (text.isNotEmpty()) {
                result.add(SpeechSegment(text, pauseMs))
            } else if (pauseMs > 0 && result.isNotEmpty() && result.last().pauseAfterMs < pauseMs) {
                val lastIdx = result.lastIndex
                result[lastIdx] = result[lastIdx].copy(pauseAfterMs = pauseMs)
            }
        }

        var i = 0
        while (i < input.length) {
            val c = input[i]
            if (c == '\n') {
                consecutiveBreaks++
                flush(if (consecutiveBreaks > 1) PARAGRAPH_PAUSE_MS else LINE_PAUSE_MS)
                i++
                continue
            }

            if (c == '[') {
                val remaining = input.substring(i)
                val noteMatch = inlineNoteCommandRegex.find(remaining)
                if (noteMatch != null) {
                    i += noteMatch.value.length
                    continue
                }
                val pauseMatch = inlinePauseCommandRegex.find(remaining)
                if (pauseMatch != null) {
                    val rawVal = pauseMatch.groupValues[1].replace(',', '.')
                    val unit = pauseMatch.groupValues[2].lowercase(Locale.ROOT)
                    val number = rawVal.toDoubleOrNull()
                    if (number != null) {
                        val rawMs = if (unit == "s") (number * 1000.0).toInt() else number.toInt()
                        flush(rawMs.coerceIn(50, 10_000))
                        i += pauseMatch.value.length
                        continue
                    }
                }
            }

            if (!c.isWhitespace()) {
                consecutiveBreaks = 0
            }
            segment.append(c)

            // Không cắt câu ở dấu chấm/phẩy/hai chấm nằm giữa hai chữ số (3.14, 1,5, 10:30)
            val betweenDigits = i > 0 && i + 1 < input.length &&
                input[i - 1].isDigit() && input[i + 1].isDigit()
            if (betweenDigits && (c == '.' || c == ',' || c == ':')) {
                i++
                continue
            }

            // Xử lý dấu ba chấm (... hoặc …)
            if (c == '…' || (c == '.' && i + 1 < input.length && input[i + 1] == '.')) {
                var extraDots = 0
                while (i + 1 < input.length && (input[i + 1] == '.' || input[i + 1] == '…')) {
                    i++
                    extraDots++
                }

                var look = i + 1
                while (look < input.length && (input[look] == ' ' || input[look] == '\t')) {
                    look++
                }

                if (look < input.length && input[look].isLowerCase() && segment.length < MAX_PHRASE_LENGTH - 30) {
                    segment.setCharAt(segment.length - 1, ',')
                    i++
                    continue
                }

                repeat(extraDots) { segment.append('.') }
                while (i + 1 < input.length && isTrailingPunctuationOrQuote(input[i + 1])) {
                    i++
                    segment.append(input[i])
                }
                flush(ELLIPSIS_PAUSE_MS)
                i++
                continue
            }

            val pause = when (c) {
                '.', '。', '۔' -> PERIOD_PAUSE_MS
                '!', '！' -> EXCLAMATION_PAUSE_MS
                '?', '？', '؟' -> QUESTION_PAUSE_MS
                else -> 0
            }

            if (pause > 0) {
                while (i + 1 < input.length && isTrailingPunctuationOrQuote(input[i + 1])) {
                    i++
                    segment.append(input[i])
                }
                flush(pause)
                i++
                continue
            }

            // Chỉ ngắt mềm đối với câu quá dài (>= 240 ký tự)
            if (segment.length >= MAX_PHRASE_LENGTH) {
                val canSplit = c.isWhitespace() || c == ',' || c == '，' || c == '、'
                if (canSplit) {
                    flush(SOFT_PAUSE_MS)
                    i++
                    continue
                }
            }

            i++
        }

        flush(0)
        if (result.isNotEmpty()) {
            val lastIdx = result.lastIndex
            result[lastIdx] = result[lastIdx].copy(pauseAfterMs = 80)
        }
        return result
    }

    /**
     * Tiền xử lý từng câu trước khi đưa vào Piper/Sherpa-ONNX (`SpeechPacing.PrepareTextForPiper`).
     */
    fun prepareTextForPiper(text: String): String {
        if (text.isBlank()) return text
        val normalized = Normalizer.normalize(text.trim(), Normalizer.Form.NFC)
        val step1 = trailingEllipsisRegex.replace(normalized, ".$1")
        return anyEllipsisRegex.replace(step1, ",")
    }

    private fun isTrailingPunctuationOrQuote(c: Char): Boolean {
        return c == '.' || c == '!' || c == '?' || c == '…' || c == '。' ||
            c == '！' || c == '？' || c == '”' || c == '’' || c == '"' ||
            c == '\'' || c == ')' || c == ']' || c == '）' || c == '」' || c == '』'
    }
}
