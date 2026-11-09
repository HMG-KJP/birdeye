package app.visiblesound.source

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import app.visiblesound.core.AppEvent
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Fills [buffer] with 16 kHz mono samples and returns how many; negative means the stream ended. */
fun interface PcmReader {
    fun read(buffer: ShortArray): Int
}

interface SpeechFeed {
    fun send(pcm: ByteArray)
    fun complete()
}

/**
 * The one microphone stream, shared: every chunk is classified for sirens and horns, and also
 * fed to speech recognition when [speechOpen] says so. A long conversation therefore can't make the
 * app miss a siren, which a time-shared microphone could.
 */
class MicPump(
    private val reader: PcmReader,
    private val classify: (FloatArray) -> List<Pair<String, Float>>,
    private val speech: SpeechFeed,
    private val speechOpen: () -> Boolean,
    private val clock: () -> Long,
    private val sink: (AppEvent) -> Unit,
    private val onError: (Throwable) -> Unit = {},
    private val chunkSize: Int = CHUNK_SAMPLES,
    private val windowSize: Int = WINDOW_SAMPLES,
    private val hopSize: Int = HOP_SAMPLES,
) {
    suspend fun run() {
        val buffer = ShortArray(chunkSize)
        val window = AudioWindow(windowSize, hopSize)
        var recognizing = false
        while (true) {
            currentCoroutineContext().ensureActive()
            val count = reader.read(buffer)
            if (count < 0) break
            if (count == 0) continue

            window.push(buffer, count).forEach(::classifyFrame)
            if (speechOpen()) {
                speech.send(pcmBytes(buffer, count))
                recognizing = true
            } else if (recognizing) {
                speech.complete()
                recognizing = false
            }
        }
        if (recognizing) speech.complete()
    }

    private fun classifyFrame(frame: FloatArray) {
        try {
            SoundFrameMapper.map(classify(frame), clock()).forEach(sink)
        } catch (e: Exception) {
            onError(e)
        }
    }

    companion object {
        const val SAMPLE_RATE = 16_000
        const val CHUNK_SAMPLES = 1_600

        /** The classifier's fixed input, about 0.975 s. */
        const val WINDOW_SAMPLES = 15_600

        /** A quarter second: three frames in a row must fit inside the one-second alert target. */
        const val HOP_SAMPLES = 4_000
    }
}

/** The app's microphone. RECORD_AUDIO must already be granted. */
class AudioRecordReader : PcmReader, AutoCloseable {
    @SuppressLint("MissingPermission")
    private val record = AudioRecord(
        MediaRecorder.AudioSource.VOICE_RECOGNITION,
        MicPump.SAMPLE_RATE,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT,
        maxOf(
            AudioRecord.getMinBufferSize(MicPump.SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT),
            MicPump.CHUNK_SAMPLES * 4,
        ),
    ).also { if (it.state == AudioRecord.STATE_INITIALIZED) it.startRecording() }

    val isReady: Boolean get() = record.state == AudioRecord.STATE_INITIALIZED

    override fun read(buffer: ShortArray): Int = if (isReady) record.read(buffer, 0, buffer.size) else -1

    override fun close() {
        if (isReady) record.stop()
        record.release()
    }
}
