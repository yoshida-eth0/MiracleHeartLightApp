package local.yoshida_eth0.miracleheartlight

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.jtransforms.fft.FloatFFT_1D
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * 周波数スペクトルデータから音声を合成し、再生するクラス。
 *
 * @param config 音声合成に関する設定（サンプリングレート、FFTサイズなど）。
 */
class AudioSynthesizer(private val config: Config = Config.sharedInstance) {

    private val audioTrack: AudioTrack
    private val fft = FloatFFT_1D(config.fftSize.toLong())
    private val scope = CoroutineScope(Dispatchers.Default)
    private val pcmBuffer = ArrayBlockingQueue<ShortArray>(4)

    @Volatile
    private var isPlaying = false
    private var playbackJob: Job? = null

    // 解析するノイズレベルのシーケンスのサイズ。過去2秒間を解析対象とする。
    private val noiseAnalyseSize = config.sampleRate / config.fftSize * 2
    // 直近のノイズレベルのシーケンスを保持するスレッドセーフなキュー。
    private val recentNoiseLevels: ConcurrentLinkedQueue<Float> = ConcurrentLinkedQueue(List(noiseAnalyseSize) { 0.0f })

    @Volatile
    var gain = 2.0f

    companion object {
        // 制御周波数と可聴域周波数のマッピング
        val audibleFreqMap = mapOf<Int, Float>(
            18500 to 1046.502f,
            18750 to 1174.659f,
            19000 to 1318.510f,
            19250 to 1396.913f,
            19500 to 1567.982f,
        )
    }

    init {
        val bufferSize = AudioTrack.getMinBufferSize(
            config.sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ) * 2

        audioTrack = AudioTrack.Builder()
            // オーディオ属性の設定：音楽再生用の設定
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            // オーディオフォーマットの設定：サンプルレート、チャンネル、エンコーディング
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(config.sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            // バッファサイズの指定
            .setBufferSizeInBytes(bufferSize)
            // 転送モードの指定：ストリーミング再生
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
    }

    /**
     * 音声再生を開始する。
     */
    fun start() {
        // --- 修正点 2: 安全な開始処理 ---
        if (isPlaying || audioTrack.state != AudioTrack.STATE_INITIALIZED) return
        isPlaying = true

        audioTrack.play()
        playbackJob = scope.launch(Dispatchers.IO) {
            try {
                // isPlayingフラグでループを制御
                while (isPlaying) {
                    val data = pcmBuffer.take()
                    if (isPlaying) { // writeの直前にもう一度チェック
                        audioTrack.write(data, 0, data.size)
                    }
                }
            } catch (e: InterruptedException) {
                // take()で待機中にスレッドが中断された場合の処理
                Thread.currentThread().interrupt()
            }
        }
    }

    /**
     * 音声再生を停止する。
     */
    fun stop() {
        // --- 修正点 3: 安全な停止処理 ---
        if (!isPlaying) return
        isPlaying = false // ループを停止させる

        playbackJob?.cancel() // コルーチンをキャンセル
        pcmBuffer.clear() // バッファに残っているデータをクリア

        if (audioTrack.playState == AudioTrack.PLAYSTATE_PLAYING) {
            audioTrack.stop()
        }
    }

    /**
     * リソースを解放する。
     */
    fun release() {
        stop() // 念のためstopを呼んでから解放
        audioTrack.release()
    }

    /**
     * 周波数と強度のマップを受け取り、それを音声波形に変換して再生する。
     *
     * @param magnitudes 周波数(Int)と強度(Float)のマップ。
     */
    fun synthesizeAndPlay(magnitudes: Map<Int, Float>) {
        // isPlayingがfalse、またはバッファに空きがなければ何もしない
        if (!isPlaying || pcmBuffer.remainingCapacity() == 0) {
            return
        }

        // バックグラウンドスレッドで重い計算を実行
        scope.launch {
            // isPlayingがfalseなら計算を中断（最適化）
            if (!isPlaying) return@launch

            // 最も強度が大きいエントリを検索
            val maxEntry = magnitudes.maxBy { it.value }

            // 最大強度以外の周波数の平均強度を計算
            val noiseLevel = magnitudes.filter { it.key != maxEntry.key }.values.average().toFloat()

            // ノイズレベルのシーケンスを更新
            recentNoiseLevels.add(noiseLevel)
            if (recentNoiseLevels.size > noiseAnalyseSize) {
                recentNoiseLevels.poll()
            }

            // 直近のノイズレベルの平均値（平滑化されたノイズ閾値）を計算
            val noiseThreshold = recentNoiseLevels.toList().average().toFloat()

            val complexArray = FloatArray(config.fftSize * 2)

            magnitudes.forEach { (freq, magnitude) ->
                val audibleFreq = audibleFreqMap[freq]!!
                val index = (audibleFreq * config.fftSize / config.sampleRate).toInt()
                if (index < config.fftSize) {
                    complexArray[index * 2] = (magnitude-noiseThreshold).coerceAtLeast(0.0f) * gain
                }
            }

            fft.complexInverse(complexArray, true)

            val pcmData = ShortArray(config.fftSize) { i ->
                // 値をShortの範囲 (-32768 ~ 32767) に収める
                val value = complexArray[i].coerceIn(-1.0f, 1.0f)
                (value * Short.MAX_VALUE).toInt().toShort()
            }

            // キューに追加する前にもisPlayingをチェック
            if (isPlaying) {
                pcmBuffer.offer(pcmData)
            }
        }
    }
}
