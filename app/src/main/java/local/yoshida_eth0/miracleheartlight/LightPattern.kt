package local.yoshida_eth0.miracleheartlight

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.core.graphics.toColorInt
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

/**
 * ライトの点灯パターン（振る舞い）を定義するデータクラス。
 *
 * @property signal このライトアクションをトリガーする信号。
 * @property name パターンの名前（デバッグや識別に利用）。
 * @property behavior ライトの具体的な振る舞いを定義するコルーチン。色を引数として受け取る関数を介してUIに色を通知する。
 */
data class LightAction(
    val signal: Int,
    val name: String,
    val behavior: suspend ((Color) -> Unit) -> Unit
)

/**
 * 特定の信号に基づいて、定義されたライトの光り方を実行するクラス。
 *
 * @property offColor ライトが消灯しているときの色。デフォルトは黒。
 */
class LightPattern(private val offColor: Color = Color.Black) {

    // アニメーションの各ステップ間の遅延時間（ミリ秒）。これによりアニメーションの滑らかさが決まる。
    private val delayPerStep: Long = 50L

    // 事前定義された色の定数
    private val colorPink: Color = color("#EA9198")
    private val colorPinkWhite: Color = color("#FFC0CB")
    private val colorPurple: Color = color("#A757A8")
    private val colorLightBlue: Color = color("#9DCCE0")
    private val colorOrange: Color = color("#FFA500")

    /**
     * 利用可能なすべてのライトパターンのリスト。
     * 各パターンは `LightAction` データクラスで定義される。
     */
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

    /**
     * `patternList` を `signal` をキーとするマップに変換し、高速な検索を可能にする。
     */
    val patternMap: Map<Int, LightAction> = patternList.associateBy { it.signal }

    /**
     * 指定されたシグナルに対応する光のパターンを実行する。
     *
     * @param signal `SignalAnalyzer` から受け取った、実行すべきパターンを決定するための信号。
     * @param action 計算された色をUI（または他の描画先）に反映するためのコールバック関数。
     */
    suspend fun execute(signal: Int, action: (Color) -> Unit) {
        // マップから対応するLightActionを検索し、存在すればその振る舞いを実行する。
        val lightAction = patternMap[signal]
        lightAction?.behavior?.invoke(action)
    }

    // --- 以下、光の振る舞いを定義するためのプライベートヘルパーメソッド群 ---

    /**
     * 指定された単一の色を一度だけ通知し、点灯状態を維持する。
     *
     * @param color 表示する色。
     * @param action 色を通知するためのコールバック。
     */
    private suspend fun lighting(color: Color, action: (Color) -> Unit) {
        action(color)
    }

    /**
     * ライトを消灯する（`offColor` を通知する）。
     *
     * @param action 色を通知するためのコールバック。
     */
    suspend fun turnOff(action: (Color) -> Unit) {
        action(offColor)
    }

    /**
     * 指定された色で点滅（フェードイン・フェードアウト）を繰り返す。
     *
     * @param color 点滅させる色。
     * @param durationMillis 1回の点滅（フェードイン〜フェードアウト）にかかる時間。
     * @param action 色を通知するためのコールバック。
     */
    private suspend fun blinking(color: Color, durationMillis: Long, action: (Color) -> Unit) {
        while (true) {
            blinkOnce(color, durationMillis, action)
        }
    }

    /**
     * 2つの異なる色の点滅を交互に繰り返す。
     *
     * @param color1 1つ目の点滅色。
     * @param color2 2つ目の点滅色。
     * @param durationMillis1 1つ目の色の点滅時間。
     * @param durationMillis2 2つ目の色の点滅時間。
     * @param action 色を通知するためのコールバック。
     */
    private suspend fun blinking2(color1: Color, color2: Color, durationMillis1: Long, durationMillis2: Long, action: (Color) -> Unit) {
        while (true) {
            blinkOnce(color1, durationMillis1, action)
            blinkOnce(color2, durationMillis2, action)
        }
    }


    /**
     * 指定された色で1回だけ点滅（フェードイン・フェードアウト）を行う。
     * `sin` カーブを利用して滑らかな輝度変化を実現する。
     *
     * @param color 点滅する色。
     * @param durationMillis 1回の点滅にかかる時間。
     * @param action 色を通知するためのコールバック。
     */
    private suspend fun blinkOnce(color: Color, durationMillis: Long, action: (Color) -> Unit) {
        val steps = (durationMillis / delayPerStep).toInt()
        if (steps <= 0) {
            action(offColor) // durationが短すぎる場合は即座に消灯色を表示
            return
        }
        for (step in 0..steps) {
            // 進行度を角度に変換 (0 -> PI)
            val angle = (step.toDouble() / steps) * PI
            // sinカーブで輝度の割合を計算 (0 -> 1 -> 0)
            val fraction = sin(angle).toFloat()
            // 開始色(offColor)と目的色(color)を線形補間
            val blendedColor = lerp(start = offColor, stop = color, fraction = fraction)
            action(blendedColor)
            delay(delayPerStep)
        }
    }

    /**
     * 色のリストを順番に滑らかに遷移させるグラデーションを繰り返す。
     *
     * @param colors グラデーションさせる色のリスト。
     * @param durationMillis 各色間の遷移にかかる時間。
     * @param action 色を通知するためのコールバック。
     */
    private suspend fun gradation(colors: List<Color>, durationMillis: Long, action: (Color) -> Unit) {
        var currentColor = offColor
        while (true) {
            for (color in colors) {
                transition(
                    from = currentColor,
                    to = color,
                    // 最初の色への遷移は半分の時間で行う
                    durationMillis = if (currentColor != offColor) durationMillis else durationMillis / 2,
                    action
                )
                currentColor = color
            }
        }
    }

    /**
     * `gradation` の特殊版。ループの初回と2回目以降で遷移時間が異なる。
     *
     * @param colors グラデーションさせる色のリスト。
     * @param durationMillis ループ初回での各色間の遷移時間。
     * @param durationMillis2 ループ2回目以降での各色間の遷移時間。
     * @param action 色を通知するためのコールバック。
     */
    private suspend fun gradation2(colors: List<Color>, durationMillis: Long, durationMillis2: Long, action: (Color) -> Unit) {
        var currentColor = offColor
        var loopCount = 0

        while (true) {
            colors.forEachIndexed { index, color ->
                val duration = when {
                    // 初回の最初の遷移は半分の時間
                    loopCount == 0 && index == 0 -> durationMillis / 2
                    // 初回ループのそれ以降の遷移
                    loopCount == 0 -> durationMillis
                    // 2回目以降のループの遷移
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
            loopCount++
        }
    }


    /**
     * 2つの色（`from` と `to`）の間を滑らかに遷移させる。
     * 線形補間（lerp）を用いて中間色を計算する。
     *
     * @param from 開始色。
     * @param to 終了色。
     * @param durationMillis 遷移にかかる時間。
     * @param action 色を通知するためのコールバック。
     */
    private suspend fun transition(from: Color, to: Color, durationMillis: Long, action: (Color) -> Unit) {
        val steps = (durationMillis / delayPerStep).toInt()
        if (steps <= 0) {
            action(to) // durationが短すぎる場合は即座に終了色を表示
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
     * 16進数のカラーコード文字列（例: "#FFFFFF"）をComposeの `Color` オブジェクトに変換する。
     *
     * @param hex 16進数カラーコード。
     * @return 対応する `Color` オブジェクト。
     */
    private fun color(hex: String): Color {
        return Color(hex.toColorInt())
    }
}
