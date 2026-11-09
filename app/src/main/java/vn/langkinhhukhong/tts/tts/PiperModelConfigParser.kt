package vn.langkinhhukhong.tts.tts

import android.content.Context
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile

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
 * Bộ đọc file `.onnx.json` chuẩn Piper, tự động:
 * 1. Sinh `tokens.txt` chuẩn cho `sherpa-onnx` từ `phoneme_id_map`.
 * 2. Bổ sung `metadata_props` Protobuf (`comment=piper`, `sample_rate`, `n_speakers`, `voice=vi`, `has_espeak=1`)
 *    trực tiếp vào file `.onnx` gốc của Piper nếu chưa có (để C++ `OfflineTtsVitsModel` đọc được 100%).
 * 3. Giải nén bộ từ điển ngữ âm `espeak-ng-data` dùng chung từ `assets/espeak-ng-data` ra bộ nhớ trong.
 */
object PiperModelConfigParser {

    private val espeakLock = Any()

    /**
     * Đảm bảo thư mục `espeak-ng-data` đã được giải nén đầy đủ ra `filesDir/espeak-ng-data`.
     */
    fun ensureEspeakNgDataStaged(context: Context): File? = synchronized(espeakLock) {
        val targetRoot = File(context.filesDir, "espeak-ng-data")
        val phontabFile = File(targetRoot, "phontab")
        val viDictFile = File(targetRoot, "vi_dict")
        if (phontabFile.exists() && phontabFile.length() > 1024L && viDictFile.exists()) {
            return targetRoot
        }

        return runCatching {
            copyAssetTreeRecursively(context, "espeak-ng-data", targetRoot)
            if (phontabFile.exists() && phontabFile.length() > 1024L) targetRoot else null
        }.getOrNull()
    }

    private fun copyAssetTreeRecursively(context: Context, assetPath: String, destDir: File) {
        val children = context.assets.list(assetPath) ?: return
        if (children.isEmpty()) {
            destDir.parentFile?.mkdirs()
            context.assets.open(assetPath).use { input ->
                FileOutputStream(destDir).use { output ->
                    input.copyTo(output)
                }
            }
        } else {
            destDir.mkdirs()
            for (child in children) {
                copyAssetTreeRecursively(
                    context = context,
                    assetPath = "$assetPath/$child",
                    destDir = File(destDir, child)
                )
            }
        }
    }

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
     * Đảm bảo file `.onnx` của Piper có đầy đủ `metadata_props` Protobuf (Field 14) mà `sherpa-onnx` yêu cầu:
     * (`model_type=vits`, `comment=piper`, `has_espeak=1`, `language=Vietnamese`, `voice=vi`, `sample_rate`, `n_speakers`).
     */
    fun ensureSherpaOnnxMetadata(onnxFile: File, metadata: PiperModelMetadata) {
        if (!onnxFile.exists() || onnxFile.length() < 1024L) return
        runCatching {
            val checkTailBytes = minOf(1024L, onnxFile.length()).toInt()
            val tailBuf = ByteArray(checkTailBytes)
            RandomAccessFile(onnxFile, "r").use { raf ->
                raf.seek(onnxFile.length() - checkTailBytes)
                raf.readFully(tailBuf)
            }
            val tailAscii = String(tailBuf, Charsets.ISO_8859_1)
            if (tailAscii.contains("comment") && tailAscii.contains("piper") && tailAscii.contains("sample_rate")) {
                return
            }

            val props = linkedMapOf(
                "model_type" to "vits",
                "comment" to "piper",
                "has_espeak" to "1",
                "language" to "Vietnamese",
                "voice" to metadata.espeakVoice.ifBlank { "vi" },
                "sample_rate" to metadata.sampleRate.toString(),
                "n_speakers" to metadata.numSpeakers.coerceAtLeast(1).toString()
            )

            val encodedBlob = ByteArrayOutputStream(160)
            for ((k, v) in props) {
                encodedBlob.write(encodeProtobufMetadataProp(k, v))
            }

            FileOutputStream(onnxFile, true).use { fos ->
                fos.write(encodedBlob.toByteArray())
                fos.flush()
            }
        }
    }

    private fun encodeProtobufMetadataProp(key: String, value: String): ByteArray {
        val entryOut = ByteArrayOutputStream()
        writeProtobufStringField(entryOut, fieldNumber = 1, text = key)
        writeProtobufStringField(entryOut, fieldNumber = 2, text = value)
        val entryBytes = entryOut.toByteArray()

        val outerOut = ByteArrayOutputStream(entryBytes.size + 8)
        writeVarint(outerOut, (14 shl 3) or 2) // Field 14 (metadata_props), wire type 2
        writeVarint(outerOut, entryBytes.size)
        outerOut.write(entryBytes)
        return outerOut.toByteArray()
    }

    private fun writeProtobufStringField(out: ByteArrayOutputStream, fieldNumber: Int, text: String) {
        val utf8 = text.toByteArray(Charsets.UTF_8)
        writeVarint(out, (fieldNumber shl 3) or 2)
        writeVarint(out, utf8.size)
        out.write(utf8)
    }

    private fun writeVarint(out: ByteArrayOutputStream, value: Int) {
        var v = value
        while (true) {
            if ((v and 0x7F.inv()) == 0) {
                out.write(v)
                return
            } else {
                out.write((v and 0x7F) or 0x80)
                v = v ushr 7
            }
        }
    }

    /**
     * Đồng bộ model từ `assets/models/<folder>` sang bộ nhớ trong `filesDir/voice_models/<folder>`
     * (dùng tên file ASCII sạch `voice.onnx` giống `NativeVoicePaths.cs` trên PC để C++ ONNX Runtime đọc an toàn tuyệt đối).
     */
    fun stageAssetModelToInternalStorage(
        context: Context,
        assetDir: String,
        targetDir: File,
        modelFileName: String,
        onnxJsonFileName: String,
        tokensFileName: String = "tokens.txt",
        onProgress: ((Float) -> Unit)? = null
    ): File? {
        return runCatching {
            val assetList = context.assets.list(assetDir) ?: return null
            targetDir.mkdirs()

            // 1. Copy .onnx.json trước để sinh tokens.txt & đọc metadata
            val dstJson = File(targetDir, onnxJsonFileName)
            if (assetList.contains(onnxJsonFileName) && (!dstJson.exists() || dstJson.length() == 0L)) {
                context.assets.open("$assetDir/$onnxJsonFileName").use { input ->
                    dstJson.outputStream().use { output -> input.copyTo(output) }
                }
            }

            // 2. Copy tokens.txt nếu có sẵn trong assets
            val dstTokens = File(targetDir, tokensFileName)
            if (assetList.contains(tokensFileName) && (!dstTokens.exists() || dstTokens.length() < 128L)) {
                context.assets.open("$assetDir/$tokensFileName").use { input ->
                    dstTokens.outputStream().use { output -> input.copyTo(output) }
                }
            }

            // 3. Đọc metadata và sinh tokens.txt chuẩn từ .onnx.json
            val meta = ensureTokensAndReadMetadata(targetDir, onnxJsonFileName, tokensFileName)

            // 4. Copy file .onnx chính ra tên chuẩn ASCII `voice.onnx`
            val safeAsciiOnnx = File(targetDir, "voice.onnx")
            val legacyOnnx = File(targetDir, modelFileName)
            if (!safeAsciiOnnx.exists() || safeAsciiOnnx.length() < 1024 * 1024L) {
                if (legacyOnnx.exists() && legacyOnnx.length() > 1024 * 1024L) {
                    legacyOnnx.renameTo(safeAsciiOnnx)
                } else if (assetList.contains(modelFileName)) {
                    val tmpFile = File(targetDir, "voice.onnx.tmp")
                    context.assets.open("$assetDir/$modelFileName").use { input ->
                        tmpFile.outputStream().use { output ->
                            val buffer = ByteArray(128 * 1024)
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
                    if (tmpFile.exists() && tmpFile.length() > 1024 * 1024L) {
                        if (safeAsciiOnnx.exists()) safeAsciiOnnx.delete()
                        tmpFile.renameTo(safeAsciiOnnx)
                    }
                }
            }

            if (safeAsciiOnnx.exists() && safeAsciiOnnx.length() > 1024 * 1024L) {
                ensureSherpaOnnxMetadata(safeAsciiOnnx, meta)
                onProgress?.invoke(1.0f)
                safeAsciiOnnx
            } else {
                null
            }
        }.getOrNull()
    }
}
