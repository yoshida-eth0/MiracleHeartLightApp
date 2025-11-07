package local.yoshida_eth0.miracleheartlight

import java.util.concurrent.ConcurrentLinkedQueue

/**
 * `SignalAnalyzer` から新しい信号が検出されたときに呼び出されるリスナーの型エイリアス。
 *
 * @param signal 検出された新しい信号（整数値）。
 */
typealias OnSignalChangedListener = (signal: Int) -> Unit

/**
 * 直近の周波数シーケンスを分析し、特定のパターンに基づいて信号を検出するクラス。
 *
 *
 * @property config 音声処理とFFTに関する設定を保持する `Config` インスタンス。
 */
class SignalAnalyzer (private val config: Config = Config.sharedInstance) {

    /**
     * 新しい信号が検出されたときに通知を受け取るためのリスナー。
     */
    var onSignalChanged: OnSignalChangedListener? = null

    /**
     * 現在検出されている信号。
     * この値は `update` メソッドによってのみ更新される。
     */
    var currentSignal: Int = 0
        private set

    // 解析する周波数シーケンスのサイズ。過去1秒間を解析対象とする。
    private val analyzeSize: Int = config.sampleRate / config.fftSize
    // 直近の最も強い周波数のシーケンスを保持するスレッドセーフなキュー。
    private val recentStrongestFrequencies: ConcurrentLinkedQueue<Int> = ConcurrentLinkedQueue(List(analyzeSize) { 0 })

    companion object {
        /**
         * 信号として認識される有効な周波数パターンの定義。
         * このパターンのシーケンスが検出されると、信号が生成される。
         */
        private val validPattern = listOf(
            listOf(18500),
            listOf(18750, 19250),
            listOf(19000, 19500),
            listOf(18750, 19250),
            listOf(19000, 19500),
            listOf(18750, 19250),
            listOf(19000, 19500),
            listOf(18750, 19250),
        )
    }

    /**
     * 新しい周波数強度データを受け取り、周波数シーケンスを更新して信号を再評価する。
     *
     * @param frequencyMagnitudes 周波数とその強度のマップ。
     */
    fun update(frequencyMagnitudes: Map<Int, Double>) {
        // 最強周波数のシーケンスを更新
        recentStrongestFrequencies.add(getStrongestFrequency(frequencyMagnitudes))
        if (recentStrongestFrequencies.size > analyzeSize) {
            recentStrongestFrequencies.poll()
        }

        // 新しい信号をシーケンスから抽出し、現在の信号と異なる場合はリスナーに通知
        val newSignal = getSignal()
        if (newSignal != -1 && newSignal != currentSignal) {
            currentSignal = newSignal
            onSignalChanged?.invoke(currentSignal)
        }
    }

    /**
     * 与えられた周波数強度のマップから、最も顕著な周波数を特定する。
     * 最強周波数が他の周波数と比較して十分に強い場合にのみ、その周波数を返す。
     *
     * @param frequencyMagnitudes 周波数とその強度のマップ。
     * @return 最も強い周波数。条件を満たさない、またはマップが空の場合は0を返す。
     */
    fun getStrongestFrequency(frequencyMagnitudes: Map<Int, Double>): Int {
        if (frequencyMagnitudes.isEmpty()) {
            return 0
        }

        // 最も強度が大きいエントリを検索
        val maxEntry = frequencyMagnitudes.maxBy { it.value }

        // 最大強度以外の周波数の平均強度を計算
        val noiseLevel = frequencyMagnitudes.filter { it.key != maxEntry.key }.values.average()

        // ノイズの3倍より大きいか評価
        if (maxEntry.value > noiseLevel * 3) {
            return maxEntry.key
        }
        return 0
    }

    /**
     * 直近の周波数シーケンスを分析し、`validPattern` に一致するパターンを探して信号を抽出する。
     *
     * @return 検出された信号（ビット演算によって生成された整数）。パターンに一致しない場合は-1を返す。
     */
    private fun getSignal(): Int {
        return recentStrongestFrequencies.toList()
            // 0（無音または無効な周波数）を除外
            .filter { it != 0 }
            // 連続する同じ周波数をフィルタリングし、変化点のみを抽出
            .let { frequencies ->
                frequencies.filterIndexed { index, value ->
                    index == 0 || value != frequencies[index - 1]
                }
            }
            // 変化点から `validPattern` に一致する部分シーケンスを後方から検索
            .windowed(validPattern.size, 1)
            .findLast { sublist ->
                // 各ウィンドウ（sublist）が `validPattern` の各ステップの条件を満たすかチェック
                sublist.withIndex().all { (index, value) ->
                    value in validPattern[index]
                }
            }
            // 見つかった場合、ビット列に変換
            ?.let { foundPattern ->
                foundPattern.mapIndexed { index, value ->
                    // 一致した周波数が `validPattern` の中で何番目の選択肢か（0 or 1）を特定
                    validPattern[index].indexOf(value)
                }
                .fold(0) { acc, bit ->
                    // ビットを組み立てて最終的な信号（整数）を生成
                    (acc shl 1) or bit
                }
            }
            // 見つからなければ-1を返す
            ?: -1
    }
}
