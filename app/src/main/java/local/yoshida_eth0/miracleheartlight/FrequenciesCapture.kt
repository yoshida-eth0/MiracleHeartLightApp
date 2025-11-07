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
import org.jtransforms.fft.FloatFFT_1D
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.hypot

/**
 * マイクから取得した音声データのFFT解析結果（周波数と強度のマップ）を通知するためのリスナーの型エイリアス。
 *
 * @param magnitudes キーが周波数（Hz）、値がその強度（magnitude）のマップ。
 */
typealias OnFrequenciesCapturedListener = (magnitudes: Map<Int, Float>) -> Unit

/**
 * マイクから継続的に音声を取得し、FFT（高速フーリエ変換）を実行して特定の周波数の強度を解析するクラス。
 *
 * @param context アプリケーションコンテキスト。パーミッションチェックなどに使用される。
 * @param config 音声キャプチャとFFTに関する設定（サンプリングレート、FFTサイズなど）。
 */
class FrequenciesCapture(private val context: Context, private val config: Config = Config.sharedInstance) {

    /**
     * 周波数強度がキャプチャされるたびに呼び出されるリスナー。
     */
    var onFrequenciesCaptured: OnFrequenciesCapturedListener? = null

    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var recordingThread: Thread? = null

    companion object {
        // 音声録音に関する定数
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        // 解析対象とする周波数のリスト（Hz）
        private val targetFrequencies = intArrayOf(18500, 18750, 19000, 19250, 19500)
    }

    // JTransformsライブラリを使用したFFTインスタンス
    private val fft = FloatFFT_1D(config.fftSize.toLong())

    // 解析対象の周波数と、FFT結果配列におけるその周波数のインデックスをマッピングするマップ。
    private val targetFrequencyIndices: Map<Int, Int> = targetFrequencies.associateWith { freq ->
        getIndexOfFrequency(freq.toFloat())
    }


    /**
     * 音声キャプチャとFFT解析を開始する。
     * `RECORD_AUDIO` パーミッションが許可されていない場合は処理を中断する。
     * すでに実行中の場合は何もしない。
     */
    @SuppressLint("MissingPermission")
    fun start() {
        if (isRecording) {
            Log.w("FrequenciesCapture", "Capture is already running.")
            return
        }

        // 録音権限の確認
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.e("FrequenciesCapture", "RECORD_AUDIO permission not granted.")
            return
        }

        // AudioRecordに必要な最小バッファサイズを取得
        val minBufferSize = AudioRecord.getMinBufferSize(config.sampleRate, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
            Log.e("FrequenciesCapture", "Invalid AudioRecord parameter.")
            return
        }

        // AudioRecordインスタンスの初期化
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            config.sampleRate,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            minBufferSize * 2 // バッファサイズには余裕を持たせる
        )

        // 初期化状態の確認
        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            Log.e("FrequenciesCapture", "AudioRecord could not be initialized.")
            return
        }

        // 録音開始
        audioRecord?.startRecording()
        isRecording = true
        Log.d("FrequenciesCapture", "Audio recording started.")

        // 別スレッドで音声処理を開始
        recordingThread = thread(start = true) {
            processAudioStream()
        }
    }

    /**
     * 音声キャプチャを停止し、関連するリソースを解放する。
     */
    fun stop() {
        if (!isRecording) return

        isRecording = false
        // 録音スレッドを中断し、終了を待つ
        recordingThread?.interrupt()
        recordingThread = null

        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e("FrequenciesCapture", "Error stopping AudioRecord", e)
        } finally {
            audioRecord = null
            Log.d("FrequenciesCapture", "Audio recording stopped.")
        }
    }

    /**
     * 録音スレッドで実行されるメインループ。
     * `AudioRecord` から音声データを読み込み、FFT処理を行い、結果をリスナーに通知する。
     */
    private fun processAudioStream() {
        val audioBuffer = ShortArray(config.fftSize)
        val fftBuffer = FloatArray(config.fftSize * 2) // FFTの入力と出力用のバッファ

        while (isRecording) {
            val readSize = audioRecord?.read(audioBuffer, 0, config.fftSize) ?: -1
            if (readSize > 0) {
                // --- FFT処理 ---
                // 1. 読み込んだShort型の音声データをFloat型に変換
                for (i in 0 until config.fftSize) {
                    fftBuffer[i] = audioBuffer[i].toFloat()
                }
                applyHanningWindow(fftBuffer)

                // 2. FFT（実数フォワード変換）を実行
                fft.realForward(fftBuffer)

                // 3. 対象周波数ごとに強度（magnitude）を計算
                val frequencyMagnitudes: Map<Int, Float> = targetFrequencyIndices.mapValues { (_, index) ->
                    calculateAverageMagnitudeAroundIndex(fftBuffer, index)
                }

                // 4. 計算結果をリスナーに通知
                onFrequenciesCaptured?.invoke(frequencyMagnitudes)
            }
        }
    }

    /**
     * 音声データにハニング窓（Hanning Window）を適用する。
     *
     * @param data 窓関数を適用する音声データの配列（Float型）。この配列の要素は直接変更される。
     */
    private fun applyHanningWindow(data: FloatArray) {
        for (i in data.indices) {
            // ハニング窓の計算式
            val multiplier = 0.5f * (1f - kotlin.math.cos(2f * PI.toFloat() * i / (data.size - 1)))
            data[i] = data[i] * multiplier
        }
    }

    /**
     * FFT結果のバッファと指定されたインデックスから振幅(magnitude)を計算する。
     * 振幅は `hypot(real, imag)` で計算される。
     *
     * @param fftBuffer FFT計算後の実数部・虚数部が交互に格納されたバッファ。
     * @param index 振幅を計算したい周波数のインデックス。
     * @return 計算された振幅。
     * @throws IndexOutOfBoundsException インデックスがFFT結果の有効範囲外の場合。
     */
    private fun calculateMagnitudeForIndex(fftBuffer: FloatArray, index: Int): Float {
        // インデックスがFFT結果の有効範囲内（ナイキスト周波数まで）か確認
        if (index >= 0 && index < config.fftSize / 2) {
            val real = fftBuffer[2 * index]
            val imag = fftBuffer[2 * index + 1]
            val magnitude = hypot(real, imag)
            // FFTの振幅は N/2 で割ることで、元の信号の振幅スケールに近づく
            // さらに窓関数によるエネルギー損失を補正するため、通常は2を掛ける
            return (magnitude / (config.fftSize / 2f)) * 2f
        }
        throw IndexOutOfBoundsException("Index $index is out of bounds for FFT results (size: ${config.fftSize / 2}).")
    }

    /**
     * 特定の周波数インデックスの周辺を含む複数のインデックスで振幅を計算し、その平均値を返す。
     * これにより、単一のインデックスの僅かなズレによる影響を緩和する。
     *
     * @param fftBuffer FFT計算後のバッファ。
     * @param index 中心となる周波数のインデックス。
     * @return 計算された周辺振幅の平均値。
     */
    private fun calculateAverageMagnitudeAroundIndex(fftBuffer: FloatArray, index: Int): Float {
        val magnitudes = mutableListOf<Float>()
        val neighborCount = config.fftNeighborCount
        // 中心インデックスの前後 `neighborCount` の範囲でループ
        for (i in (index - neighborCount)..(index + neighborCount)) {
            magnitudes.add(calculateMagnitudeForIndex(fftBuffer, i))
        }
        // 収集した振幅の平均値を返す
        return magnitudes.average().toFloat()
    }

    /**
     * 周波数（Hz）をFFTの結果配列におけるインデックスに変換する。
     *
     * @param frequency 変換したい周波数（Hz）。
     * @return FFT配列に対応するインデックス。
     */
    private fun getIndexOfFrequency(frequency: Float): Int {
        return (frequency * config.fftSize / config.sampleRate).toInt()
    }
}
