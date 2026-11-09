package app.visiblesound.source

import android.content.Context
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * On-device sound classification: the Pleos SDK has no equivalent. Runs YAMNet directly on
 * TensorFlow Lite, because MediaPipe's audio library has no x86_64 build and so would not run on
 * Windows or Linux emulators. The windows arrive already overlapped from [MicPump].
 */
class YamnetSoundSource(context: Context) : AutoCloseable {
    private val interpreter = Interpreter(loadModel(context), Interpreter.Options().setNumThreads(THREADS))
    private val labels = context.assets.open(LABELS).bufferedReader().use { it.readLines() }.map { it.trim() }

    /** Only the sounds the app listens for are read out of the model's scores. */
    private val listened = labels.withIndex().filter { it.value in SoundFrameMapper.LISTENED_LABELS }
    private val inputIsBatched = interpreter.getInputTensor(0).shape().size > 1
    private val scores = Array(1) { FloatArray(labels.size) }

    fun classify(window: FloatArray): List<Pair<String, Float>> {
        val input: Any = if (inputIsBatched) arrayOf(window) else window
        interpreter.run(input, scores)
        return listened.map { (index, label) -> label to scores[0][index] }
    }

    override fun close() = interpreter.close()

    private fun loadModel(context: Context): MappedByteBuffer =
        context.assets.openFd(MODEL).use { asset ->
            FileInputStream(asset.fileDescriptor).use { stream ->
                stream.channel.map(FileChannel.MapMode.READ_ONLY, asset.startOffset, asset.declaredLength)
            }
        }

    private companion object {
        const val MODEL = "yamnet.tflite"
        const val LABELS = "yamnet_labels.txt"
        const val THREADS = 2
    }
}
