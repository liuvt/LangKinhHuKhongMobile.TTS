package vn.langkinhhukhong.tts.tts

import android.content.Context
import org.json.JSONObject
import java.io.File

/**
 * Thông số cấu hình đọc từ file `.onnx.json` của model Piper / VITS.
 */
data class PiperModelMetadata(
    val sampleRate: Int = 22050,
    val numSpeakers: Int = 1,
    val espeakVoice: String = "vi",
    val noiseScale: Float = 0.667f,
    val lengthScale: Float = 1.0f,
    val noiseW: Float = 0.8f,
    val speakerIdMap: Map<String, Int> = emptyMap(),
    val tokenCount: Int = 0
)

/**
 * Bộ đọc file `.onnx.json` chuẩn Piper và tự động sinh file `tokens.txt` chuẩn cho Sherpa-ONNX.
 *
 * Cấu trúc `phoneme_id_map` trong `.onnx.json`:
 * ```json
 * "phoneme_id_map": {
 *   "_": [0],
 *   "^": [1],
 *   "$": [2],
 *   " ": [3],
 *   ...
 * }
 * ```
 * Sẽ được xuất ra `tokens.txt` theo định dạng `<ký_hiệu> <id>` sắp xếp tăng dần theo ID.
 */
object PiperModelConfigParser {

    /**
     * Đọc metadata và tự động sinh `tokens.txt` từ file `.onnx.json` trong thư mục [modelDir].
     */
    fun ensureTokensAndReadMetadata(
        modelDir: File,
        onnxJsonFileName: String,
        tokensFileName: String = "tokens.txt"
    ): PiperModelMetadata {
        val jsonFile = File(modelDir, onnxJsonFileName)
        val tokensFile = File(modelDir, tokensFileName)

        if (!jsonFile.exists() || jsonFile.length() == 0L) {
            return PiperModelMetadata()
        }

        return runCatching {
            val jsonText = jsonFile.readText(Charsets.UTF_8)
            val root = JSONObject(jsonText)

            val sampleRate = root.optJSONObject("audio")?.optInt("sample_rate", 22050) ?: 22050
            val espeakVoice = root.optJSONObject("espeak")?.optString("voice", "vi") ?: "vi"
            val numSpeakers = root.optInt("num_speakers", 1).coerceAtLeast(1)

            val inferenceObj = root.optJSONObject("inference")
            val noiseScale = inferenceObj?.optDouble("noise_scale", 0.667)?.toFloat() ?: 0.667f
            val lengthScale = inferenceObj?.optDouble("length_scale", 1.0)?.toFloat() ?: 1.0f
            val noiseW = inferenceObj?.optDouble("noise_w", 0.8)?.toFloat() ?: 0.8f

            val speakerMap = mutableMapOf<String, Int>()
            root.optJSONObject("speaker_id_map")?.let { mapObj ->
                val keys = mapObj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    speakerMap[key] = mapObj.optInt(key, 0)
                }
            }

            val phonemeIdMap = root.optJSONObject("phoneme_id_map")
            var generatedTokens = 0
            if (phonemeIdMap != null) {
                val pairs = mutableListOf<Pair<String, Int>>()
                val keys = phonemeIdMap.keys()
                while (keys.hasNext()) {
                    val symbol = keys.next()
                    val idArray = phonemeIdMap.optJSONArray(symbol)
                    if (idArray != null && idArray.length() > 0) {
                        pairs.add(symbol to idArray.getInt(0))
                    }
                }
                pairs.sortBy { it.second }
                generatedTokens = pairs.size

                if (pairs.isNotEmpty() && (!tokensFile.exists() || tokensFile.length() < 128L)) {
                    val sb = StringBuilder(pairs.size * 8)
                    for ((symbol, id) in pairs) {
                        sb.append(symbol).append(' ').append(id).append('\n')
                    }
                    tokensFile.writeText(sb.toString(), Charsets.UTF_8)
                }
            }

            PiperModelMetadata(
                sampleRate = sampleRate,
                numSpeakers = numSpeakers,
                espeakVoice = espeakVoice,
                noiseScale = noiseScale,
                lengthScale = lengthScale,
                noiseW = noiseW,
                speakerIdMap = speakerMap,
                tokenCount = generatedTokens
            )
        }.getOrDefault(PiperModelMetadata())
    }

    /**
     * Đồng bộ model từ `assets/models/<folder>` sang bộ nhớ trong `filesDir/voice_models/<folder>`
     * và tự động sinh `tokens.txt` từ `.onnx.json` nếu chưa có.
     */
    fun stageAssetModelToInternalStorage(
        context: Context,
        assetDir: String,
        targetDir: File,
        modelFileName: String,
        onnxJsonFileName: String,
        tokensFileName: String = "tokens.txt",
        onProgress: ((Float) -> Unit)? = null
    ): Boolean {
        return runCatching {
            val assetList = context.assets.list(assetDir) ?: return false
            if (!assetList.contains(modelFileName)) return false

            targetDir.mkdirs()

            // 1. Copy .onnx.json trước để sinh tokens.txt
            if (assetList.contains(onnxJsonFileName)) {
                val dstJson = File(targetDir, onnxJsonFileName)
                if (!dstJson.exists() || dstJson.length() == 0L) {
                    context.assets.open("$assetDir/$onnxJsonFileName").use { input ->
                        dstJson.outputStream().use { output -> input.copyTo(output) }
                    }
                }
            }

            // 2. Copy tokens.txt nếu có sẵn trong assets
            if (assetList.contains(tokensFileName)) {
                val dstTokens = File(targetDir, tokensFileName)
                if (!dstTokens.exists() || dstTokens.length() < 128L) {
                    context.assets.open("$assetDir/$tokensFileName").use { input ->
                        dstTokens.outputStream().use { output -> input.copyTo(output) }
                    }
                }
            }

            // 3. Tự động sinh tokens.txt chuẩn từ .onnx.json nếu chưa có
            ensureTokensAndReadMetadata(targetDir, onnxJsonFileName, tokensFileName)

            // 4. Copy file .onnx chính (nếu chưa có hoặc kích thước chưa khớp)
            val dstOnnx = File(targetDir, modelFileName)
            if (!dstOnnx.exists() || dstOnnx.length() < 1024 * 1024L) {
                context.assets.open("$assetDir/$modelFileName").use { input ->
                    dstOnnx.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var bytesRead: Int
                        var totalRead = 0L
                        val estimatedTotal = 65L * 1024L * 1024L
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalRead += bytesRead
                            onProgress?.invoke((totalRead.toFloat() / estimatedTotal).coerceIn(0.05f, 0.95f))
                        }
                    }
                }
            } else {
                onProgress?.invoke(0.95f)
            }

            dstOnnx.exists() && dstOnnx.length() > 1024 * 1024L
        }.getOrDefault(false)
    }
}
