package local.yoshida_eth0.miracleheartlight

import android.util.Log

typealias OnSignalChangedListener = (signal: Int) -> Unit

/**
 * @param config 音声キャプチャとFFTに関する設定。
 */
class SignalAnalyzer (private val config: Config = Config.sharedInstance) {

    var onSignalChanged: OnSignalChangedListener? = null

    var currentSignal: Int = 0
        private set

    private val analyzeSize: Int = config.sampleRate / config.fftSize
    private val history: MutableList<Int> = MutableList(analyzeSize) { 0 }

    companion object {
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

    @Synchronized
    fun update(frequencyMagnitudes: Map<Int, Double>) {
        history.add(getStrongestFrequency(frequencyMagnitudes))
        if (history.size > analyzeSize) {
            history.removeAt(0)
        }

        // 有効なシグナルが更新された場合
        val newSignal = getSignal()
        if (newSignal != -1 && newSignal != currentSignal) {
            currentSignal = newSignal
            onSignalChanged?.invoke(currentSignal)
        }
    }

    /**
     * Calculates the strongest frequency from the given frequency magnitudes.
     *
     * @param frequencyMagnitudes A map where keys are frequencies and values are their magnitudes.
     * @return The frequency with the highest magnitude, or 0 if the map is empty.
     */
    fun getStrongestFrequency(frequencyMagnitudes: Map<Int, Double>): Int {
        if (frequencyMagnitudes.isEmpty()) {
            return 0
        }

        // 最も強い周波数
        val maxEntry = frequencyMagnitudes.maxBy { it.value }

        // 最も強い周波数を除いたmagnitudeの平均値
        val baseMagnitude = frequencyMagnitudes.filter { it.key != maxEntry.key }.values.average()

        // 最も強い周波数のmagnitudeが500以上且つ偏差の3倍
        if (maxEntry.value >= 500 && maxEntry.value > baseMagnitude * 3) {
            return maxEntry.key
        }
        return 0
    }

    private fun getSignal(): Int {
        // 0を除外
        val nonZeroHistory = history.filter { it != 0 }

        // 連続する値を除外
        val normalizedHistory = nonZeroHistory.filterIndexed { index, value ->
            index == 0 || value != nonZeroHistory[index - 1]
        }

        // validPatternに一致する部分シーケンスを後ろから探し、見つかったらそれを基にシグナルを生成する
        return normalizedHistory
            .windowed(validPattern.size,1) // リストをvalidPatternのサイズでスライディングウィンドウ化
            .findLast { sublist -> // ウィンドウを後ろから検索
                // 各ウィンドウ（sublist）がvalidPatternに一致するかチェック
                sublist.withIndex().all { (index, value) ->
                    value in validPattern[index]
                }
            }
            ?.mapIndexed { index, value ->
                // 一致したsublistの各要素が、validPatternのサブリスト内で何番目のインデックスかを計算する
                validPattern[index].indexOf(value)
            }
            ?.fold(0) { acc, bit ->
                // 左に1ビットシフトし、新しいビットを加算(OR)する
                (acc shl 1) or bit
            }
            ?: -1 // 一致するものがなければ-1を返す
    }
}
