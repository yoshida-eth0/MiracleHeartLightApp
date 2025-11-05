package local.yoshida_eth0.miracleheartlight

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.core.graphics.toColorInt
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

// 1. 振る舞いを表すデータクラスを定義
data class LightAction(
    val signal: Int,
    val name: String,
    val behavior: suspend ((Color) -> Unit) -> Unit
)

/**
 * ライトの光り方を定義し、実行するクラス。
 */
class LightPattern(private val offColor: Color = Color.Black) {

    // delayPerStepはアニメーションの滑らかさ(fps)を定義するため、インスタンス変数として維持
    private val delayPerStep: Long = 50L

    private val colorPink: Color = color("#EA9198")
    private val colorPinkWhite: Color = color("#FFC0CB")
    private val colorPurple: Color = color("#A757A8")
    private val colorLightBlue: Color = color("#9DCCE0")
    private val colorOrange: Color = color("#FFA500")

    // 2. SignalとLightActionをマッピングするMapを定義
    val patternList = listOf(
        LightAction(
            signal = 1,
            name = "長黄短緑",
            behavior = { action -> gradation(listOf(Color.Yellow, Color.Yellow, Color.Green), 1050L, action) }
        ),
        LightAction(
            signal = 5,
            name = "黄",
            behavior = { action -> lighting(Color.Yellow, action) }
        ),
        LightAction(
            signal = 21,
            name = "薄ピンク点滅(白)",
            behavior = { action -> blinking(colorPinkWhite, 1800L, action) }
        ),
        LightAction(
            signal = 22,
            name = "青点滅",
            behavior = { action -> blinking(Color.Blue, 2100L, action) }
        ),
        LightAction(
            signal = 23,
            name = "オレンジ点滅",
            behavior = { action -> blinking(colorOrange, 1800L, action) }
        ),
        LightAction(
            signal = 25,
            name = "赤オレンジピンク黄緑水青紫",
            behavior = { action -> gradation(listOf(Color.Red, colorOrange, colorPink, Color.Yellow, Color.Green, Color.Blue, colorPurple), 1200L, action) }
        ),
        LightAction(
            signal = 26,
            name = "水色点滅",
            behavior = { action -> blinking(colorLightBlue, 2100L, action) }
        ),
        LightAction(
            signal = 27, // durationがいまいち
            name = "緑点滅",
            behavior = { action -> blinking(Color.Green, 1800L, action) }
        ),
        LightAction(
            signal = 32,
            name = "紫青",
            behavior = { action -> gradation2(listOf(colorPurple, Color.Blue), 1500L, 950L, action) }
        ),
        LightAction(
            signal = 35,
            name = "ピンク黄緑水色青紫赤オレンジ",
            behavior = { action -> gradation(listOf(colorPink, Color.Yellow, Color.Green, colorLightBlue, Color.Blue, colorPurple, Color.Red, colorOrange), 1100L, action) }
        ),
        LightAction(
            signal = 42,
            name = "ピンク黄水色",
            behavior = { action -> gradation(listOf(colorPink, Color.Yellow, colorLightBlue), 1100L, action) }
        ),
        LightAction(
            signal = 52,
            name = "水色",
            behavior = { action -> lighting(colorLightBlue, action) }
        ),
        LightAction(
            signal = 57,
            name = "紫青高速",
            behavior = { action -> gradation2(listOf(colorPurple, Color.Blue), 650L, 550L, action) }
        ),
        LightAction(
            signal = 61,
            name = "長薄ピンク緑",
            behavior = { action -> gradation(listOf(colorPinkWhite, colorPinkWhite, Color.Green), 1050L, action) }
        ),
        LightAction(
            signal = 62,
            name = "消灯",
            behavior = { action -> turnOff(action) }
        ),
        LightAction(
            signal = 66,
            name = "薄ピンク(白)",
            behavior = { action -> lighting(colorPinkWhite, action) }
        ),
        LightAction(
            signal = 67,
            name = "長水色短黄",
            behavior = { action -> gradation2(listOf(colorLightBlue, colorLightBlue, Color.Yellow), 1050L, 900L, action) }
        ),
        LightAction(
            signal = 70,
            name = "消灯",
            behavior = { action -> turnOff(action) }
        ),
        LightAction(
            signal = 76,
            name = "オレンジ",
            behavior = { action -> lighting(colorOrange, action) }
        ),
        LightAction(
            signal = 78,
            name = "緑点滅",
            behavior = { action -> blinking(Color.Green, 2100L, action) }
        ),
        LightAction(
            signal = 90,
            name = "ピンク黄水",
            behavior = { action -> gradation2(listOf(colorPink, Color.Yellow, colorLightBlue), 1000L, 850L, action) },
        ),
        LightAction(
            signal = 95,
            name = "薄ピンク点滅(白)",
            behavior = { action -> blinking(colorPinkWhite, 2100L, action) }
        ),
        LightAction(
            signal = 99,
            name = "長ピンク短赤",
            behavior = { action -> gradation2(listOf(colorPink, colorPink, Color.Red), 1100L,850L, action) }
        ),
        LightAction(
            signal = 101,
            name = "黄点滅",
            behavior = { action -> blinking(Color.Yellow, 1800L, action) }
        ),
        LightAction(
            signal = 103,
            name = "水色点滅",
            behavior = { action -> blinking(colorLightBlue, 1800L, action) }
        ),
        LightAction(
            signal = 105,
            name = "高速赤オレンジピンク黄緑水色青紫",
            behavior = { action -> gradation(listOf(Color.Red, colorOrange, colorPink, Color.Yellow, Color.Green, colorLightBlue, colorPurple), 600L, action) }
        ),
        LightAction(
            signal = 107,
            name = "赤点滅",
            behavior = { action -> blinking(Color.Red, 2100L, action) }
        ),
        LightAction(
            signal = 111,
            name = "ピンク点滅",
            behavior = { action -> blinking(colorPink, 1800L, action) }
        ),
        LightAction(
            signal = 113,
            name = "紫",
            behavior = { action -> lighting(colorPurple, action) }
        ),
        LightAction(
            signal = 120,
            name = "青",
            behavior = { action -> lighting(Color.Blue, action) }
        ),
        LightAction(
            signal = 123,
            name = "ピンク点滅",
            behavior = { action -> blinking(colorPink, 2100L, action) }
        ),
        LightAction(
            signal = 124,
            name = "ピンク",
            behavior = { action -> lighting(colorPink, action) }
        ),
    )
    // patternListからsignalをキーとするMapを作成
    val patternMap: Map<Int, LightAction> = patternList.associateBy { it.signal }

    /**
     * 指定されたシグナル名に対応する光のパターンを実行します。
     * @param signal SignalAnalyzerから受け取ったシグナル。
     * @param action 計算された色をUIに反映するための処理。
     */
    suspend fun execute(signal: Int, action: (Color) -> Unit) {
        // Mapから対応するLightActionを探し、なければ何もしない
        val lightAction = patternMap[signal]
        lightAction?.behavior?.invoke(action)
    }

    // --- 以下は、振る舞いを定義するためのヘルパーメソッド群 (privateに変更) ---

    /**
     * 指定された単一の色を一度だけ通知します。
     */
    private suspend fun lighting(color: Color, action: (Color) -> Unit) {
        action(color)
    }

    /**
     * ライトを消灯します（offColorを通知します）。
     */
    public suspend fun turnOff(action: (Color) -> Unit) {
        action(offColor)
    }

    /**
     * 指定された色のアルファ値を滑らかに変化させ（点滅させ）、その結果を無限に通知するサスペンド関数。
     */
    private suspend fun blinking(color: Color, durationMillis: Long, action: (Color) -> Unit) {
        while (true) {
            blinkOnce(color, durationMillis, action)
        }
    }

    /**
     * 2つの点灯をループさせるアニメーション。
     * @param color1 1つ目の点灯の色。
     * @param color2 2つ目の点灯の色。
     * @param action 色を通知するための処理。
     */
    private suspend fun blinking2(color1: Color, color2: Color, durationMillis1: Long, durationMillis2: Long, action: (Color) -> Unit) {
        while (true) {
            // 1つ目の点灯
            blinkOnce(color1, durationMillis1, action)
            // 2つ目の点灯
            blinkOnce(color2, durationMillis2, action)
        }
    }


    /**
     * 指定された色で1回だけ点滅（フェードイン・フェードアウト）する。
     * @param color 点滅する色。
     * @param durationMillis 1回の点滅にかかる時間。
     * @param action 色を通知するための処理。
     */
    private suspend fun blinkOnce(color: Color, durationMillis: Long, action: (Color) -> Unit) {
        val steps = (durationMillis / delayPerStep).toInt()
        if (steps <= 0) {
            action(offColor) // durationが短すぎる場合は消灯色を即座に表示
            return
        }
        for (step in 0..steps) {
            val angle = (step.toDouble() / steps) * PI
            val fraction = sin(angle).toFloat()
            val blendedColor = lerp(start = offColor, stop = color, fraction = fraction)
            action(blendedColor)
            delay(delayPerStep)
        }
    }

    /**
     * 複数の色を順番にグラデーションさせます
     * @param colors グラデーションさせる色のリスト。
     * @param action 色を通知するための処理。
     */
    private suspend fun gradation(colors: List<Color>, durationMillis: Long, action: (Color) -> Unit) {
        var currentColor = offColor
        while (true) {
            for (color in colors) {
                transition(
                    from = currentColor,
                    to = color,
                    durationMillis = if (currentColor!=offColor) durationMillis else durationMillis / 2,
                    action
                )
                currentColor = color
            }
        }
    }

    private suspend fun gradation2(colors: List<Color>, durationMillis: Long, durationMillis2: Long, action: (Color) -> Unit) {
        var currentColor = offColor
        var loopCount = 0 // whileループの回数を追跡するカウンター

        while (true) {
            colors.forEachIndexed { index, color ->
                // durationを決定するロジック
                val duration = when {
                    // whileループ1回目(loopCount=0) かつ colorsのループ0要素目(index=0)
                    loopCount == 0 && index == 0 -> durationMillis / 2
                    // whileループ1回目(loopCount=0) かつ 0要素目以外
                    loopCount == 0 -> durationMillis
                    // whileループ2回目以降 (loopCount > 0)
                    else -> durationMillis2
                }

                transition(
                    from = currentColor,
                    to = color,
                    durationMillis = duration,
                    action
                )
                currentColor = color
            }
            loopCount++ // whileループのカウンターをインクリメント
        }
    }


    /**
     * 2つの色の間で滑らかなグラデーション（線形補間）を行います。
     * @param from 開始色。
     * @param to 終了色。
     * @param durationMillis この遷移にかける時間。
     * @param action 色を通知するための処理。
     */
    private suspend fun transition(from: Color, to: Color, durationMillis: Long, action: (Color) -> Unit) {
        val steps = (durationMillis / delayPerStep).toInt()
        if (steps <= 0) {
            action(to)
            return
        }
        for (step in 0..steps) {
            val fraction = step.toFloat() / steps
            val blendedColor = lerp(start = from, stop = to, fraction = fraction)
            action(blendedColor)
            delay(delayPerStep)
        }
    }

    /**
     * 16進数のカラーコード文字列からComposeのColorオブジェクトを生成します。
     */
    private fun color(hex: String): Color {
        return Color(hex.toColorInt())
    }
}
