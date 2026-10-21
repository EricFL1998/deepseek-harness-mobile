package com.deepseek.dsh.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.ByteArrayOutputStream

/**
 * 语音输入的录音器：16 kHz 单声道 PCM16。
 *
 * 官方网页端用 `MediaRecorder` + `decodeAudioData` + `OfflineAudioContext`
 * 重采样成 16 kHz 单声道 PCM16 WAV；Android 侧直接用 `AudioRecord` 按目标
 * 采样率采集，省掉重采样，输出同样的规范 WAV（Host 的 `speech/transcribe`
 * 只接受这种格式）。
 */
internal class VoiceRecorder {
    /** 采样率：与 Host 要求的 WAV 一致。 */
    private val sampleRate = 16_000

    private var record: AudioRecord? = null
    private var worker: Thread? = null
    private var pcm: ByteArrayOutputStream? = null

    @Volatile
    private var running = false

    /** 录音电平回调（0..1 的 RMS，用于界面电平条）。 */
    var onLevel: ((Float) -> Unit)? = null

    /** 是否正在录音。 */
    val isRecording: Boolean
        get() = running

    /**
     * 开始录音。
     * @returns 是否成功启动（没有麦克风或被占用时为 false）。
     */
    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (running) return true
        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuffer <= 0) return false
        val bufferSize = minBuffer * 2
        val recorder = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize,
            )
        } catch (_: Exception) {
            return false
        }
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            return false
        }
        val output = ByteArrayOutputStream()
        pcm = output
        running = true
        record = recorder
        recorder.startRecording()
        worker = Thread {
            val buffer = ByteArray(minBuffer)
            while (running) {
                val read = runCatching { recorder.read(buffer, 0, buffer.size) }.getOrDefault(0)
                if (read > 0) {
                    output.write(buffer, 0, read)
                    // RMS 电平：把 PCM16 采样平方平均后开方，映射到 0..1。
                    var sum = 0.0
                    var index = 0
                    while (index + 1 < read) {
                        val sample = ((buffer[index + 1].toInt() shl 8) or (buffer[index].toInt() and 0xFF)).toShort()
                        val value = sample / 32768.0
                        sum += value * value
                        index += 2
                    }
                    val rms = if (read > 0) kotlin.math.sqrt(sum / (read / 2)) else 0.0
                    onLevel?.invoke((rms * 4).coerceIn(0.0, 1.0).toFloat())
                }
            }
        }.apply {
            name = "dsh-voice-recorder"
            start()
        }
        return true
    }

    /**
     * 结束录音并返回 16 kHz 单声道 PCM16 WAV 的字节。
     * @returns WAV 数据；没有录到内容时返回 null。
     */
    fun stop(): ByteArray? {
        if (!running) return null
        running = false
        runCatching { worker?.join(300) }
        worker = null
        val recorder = record
        record = null
        if (recorder != null) {
            runCatching { recorder.stop() }
            runCatching { recorder.release() }
        }
        val data = pcm?.toByteArray()
        pcm = null
        onLevel?.invoke(0f)
        if (data == null || data.size < 320) return null
        return encodeWav(data)
    }

    /** 丢弃当前录音。 */
    fun cancel() {
        if (!running) return
        running = false
        runCatching { worker?.join(200) }
        worker = null
        val recorder = record
        record = null
        if (recorder != null) {
            runCatching { recorder.stop() }
            runCatching { recorder.release() }
        }
        pcm = null
        onLevel?.invoke(0f)
    }

    /** 把裸 PCM16 数据包成 44 字节头的规范 WAV。 */
    private fun encodeWav(data: ByteArray): ByteArray {
        val total = data.size
        val header = ByteArray(44)
        fun u32(offset: Int, value: Int) {
            header[offset] = (value and 0xFF).toByte()
            header[offset + 1] = ((value shr 8) and 0xFF).toByte()
            header[offset + 2] = ((value shr 16) and 0xFF).toByte()
            header[offset + 3] = ((value shr 24) and 0xFF).toByte()
        }
        fun u16(offset: Int, value: Int) {
            header[offset] = (value and 0xFF).toByte()
            header[offset + 1] = ((value shr 8) and 0xFF).toByte()
        }
        "RIFF".forEachIndexed { index, char -> header[index] = char.code.toByte() }
        u32(4, 36 + total)
        "WAVE".forEachIndexed { index, char -> header[8 + index] = char.code.toByte() }
        "fmt ".forEachIndexed { index, char -> header[12 + index] = char.code.toByte() }
        u32(16, 16)
        u16(20, 1) // PCM
        u16(22, 1) // mono
        u32(24, sampleRate)
        u32(28, sampleRate * 2) // byte rate = sampleRate * blockAlign
        u16(32, 2) // block align
        u16(34, 16) // bits per sample
        "data".forEachIndexed { index, char -> header[36 + index] = char.code.toByte() }
        u32(40, total)
        return header + data
    }
}
