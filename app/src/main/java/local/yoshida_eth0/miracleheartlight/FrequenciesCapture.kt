package local.yoshida_eth0.miracleheartlight

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import org.jtransforms.fft.DoubleFFT_1D
import kotlin.concurrent.thread
import kotlin.math.hypot

typealias OnFrequenciesCapturedListener = (magnitudes: Map<Int, Double>) -> Unit

/**
 * マイクから音声を取得し、特定の周波数の強度を解析するクラス。
 *
 * @param context アプリケーションコンテキスト。
 * @param config 音声キャプチャとFFTに関する設定。
 */
class FrequenciesCapture(private val context: Context, private val config: Config = Config.sharedInstance) {

    var onFrequenciesCaptured: OnFrequenciesCapturedListener? = null

    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var recordingThread: Thread? = null

    // 音声処理の定数
    companion object {
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    private val fft = DoubleFFT_1D(config.fftSize.toLong())

    // 解析対象の周波数リスト
    private val targetFrequencies = intArrayOf(18500, 18750, 19000, 19250, 19500)

    // 解析対象の周波数のインデックス
    private val targetFrequencyIndices: Map<Int, Int> = targetFrequencies.associateWith { freq ->
        getIndexOfFrequency(freq.toDouble())
    }


    /**
     * 音声キャプチャとFFT解析を開始します。
     * RECORD_AUDIO権限が許可されている必要があります。
     */
    @SuppressLint("MissingPermission")
    fun start() {
        if (isRecording) {
            Log.w("MiracleHeartLightCapture", "Capture is already running.")
            return
        }

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.e("MiracleHeartLightCapture", "RECORD_AUDIO permission not granted.")
            return
        }

        val minBufferSize = AudioRecord.getMinBufferSize(config.sampleRate, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
            Log.e("MiracleHeartLightCapture", "Invalid AudioRecord parameter.")
            return
        }

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            config.sampleRate,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            minBufferSize * 2
        )

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            Log.e("MiracleHeartLightCapture", "AudioRecord could not be initialized.")
            return
        }

        audioRecord?.startRecording()
        isRecording = true
        Log.d("MiracleHeartLightCapture", "Audio recording started.")

        recordingThread = thread(start = true) {
            processAudioStream()
        }
    }

    /**
     * 音声キャプチャを停止し、リソースを解放します。
     */
    fun stop() {
        if (!isRecording) return

        isRecording = false
        recordingThread?.interrupt()
        recordingThread = null

        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e("MiracleHeartLightCapture", "Error stopping AudioRecord", e)
        } finally {
            audioRecord = null
            Log.d("MiracleHeartLightCapture", "Audio recording stopped.")
        }
    }

    private fun processAudioStream() {
        val audioBuffer = ShortArray(config.fftSize)
        val fftBuffer = DoubleArray(config.fftSize * 2)

        while (isRecording) {
            val readSize = audioRecord?.read(audioBuffer, 0, config.fftSize) ?: -1
            if (readSize > 0) {
                // --- FFT処理 ---
                // 1. Short配列をDouble配列に変換
                for (i in 0 until config.fftSize) {
                    fftBuffer[i] = audioBuffer[i].toDouble()
                }

                // 2. FFTを実行
                fft.realForward(fftBuffer)

                // 3. 特定の周波数のmagnitudeを計算し、Mapに格納
                val frequencyMagnitudes: Map<Int, Double> = targetFrequencyIndices.mapValues { (_, index) ->
                    calculateAverageMagnitudeAroundIndex(fftBuffer, index)
                }

                // --- 4. 計算結果をリスナーに渡す ---
                onFrequenciesCaptured?.invoke(frequencyMagnitudes)

                // デバッグ用のログ出力は必要に応じて残す
                // val logBuilder = StringBuilder("Magnitudes for target frequencies:\n")
                // frequencyMagnitudes.forEach { (freq, magnitude) ->
                //     logBuilder.append("  - %d Hz: %.2f\n".format(freq, magnitude))
                // }
                // Log.d("FrequencyAnalysis", logBuilder.toString())
            }
        }
    }

    /**
     * FFT結果のバッファと指定されたインデックスから振幅(magnitude)を計算します。
     * @param fftBuffer FFT計算後の実数部・虚数部が格納されたバッファ。
     * @param index 振幅を計算したい周波数のインデックス。
     * @return 計算された振幅。インデックスが範囲外の場合は0.0を返します。
     */
    private fun calculateMagnitudeForIndex(fftBuffer: DoubleArray, index: Int): Double {
        // インデックスがFFT結果の範囲内にあることを確認
        if (index >= 0 && index < config.fftSize / 2) {
            val real = fftBuffer[2 * index]
            val imag = fftBuffer[2 * index + 1]
            return hypot(real, imag)
        }
        // 範囲外の場合は例外をスローする
        throw IndexOutOfBoundsException("Index $index is out of bounds for FFT results (size: ${config.fftSize / 2}).")
    }

    /**
     * 指定されたインデックスの周囲の振幅の平均値を計算します。
     * @param fftBuffer FFT計算後のバッファ。
     * @param index 中心となるインデックス。
     * @return 計算された振幅の平均値。
     */
    private fun calculateAverageMagnitudeAroundIndex(fftBuffer: DoubleArray, index: Int): Double {
        val magnitudes = mutableListOf<Double>()
        val neighborCount = config.fftNeighborCount
        // 中心インデックスから前後neighborCountの範囲でループ
        for (i in (index - neighborCount)..(index + neighborCount)) {
            // 各インデックスの振幅を計算してリストに追加
            magnitudes.add(calculateMagnitudeForIndex(fftBuffer, i))
        }
        // magnitudesリストの平均値を返す
        return magnitudes.average()
    }

    // 特定の周波数のインデックスを計算するヘルパー関数
    private fun getIndexOfFrequency(frequency: Double): Int {
        return (frequency * config.fftSize / config.sampleRate).toInt()
    }
}
