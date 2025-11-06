package local.yoshida_eth0.miracleheartlight

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.core.graphics.toColorInt
import kotlinx.coroutines.delay
import local.yoshida_eth0.miracleheartlight.BlinkEasing.SharpBlink
import local.yoshida_eth0.miracleheartlight.TransitionEasing.SharpTransition

/**
 * ライトの具体的な光り方（振る舞い）を定義するインターフェース。
 * suspend関数であり、色の更新を行うためのactionコールバックを受け取る。
 */
fun interface LightBehavior {
    suspend fun execute(action: (Color) -> Unit)
}

/**
 * ライトの点灯パターン（振る舞い）を定義するデータクラス。
 *
 * @property signal このライトアクションをトリガーする信号。
 * @property name パターンの名前（デバッグや識別に利用）。
 * @property behavior ライトの具体的な振る舞いを定義する `LightBehavior` インターフェース。
 */
data class LightAction(
    val signal: Int,
    val name: String,
    val behavior: LightBehavior?
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
     * 利用可能なすべてのライトパターンのマップ。
     * 各パターンは `LightAction` データクラスで定義される。
     */
    val patternMap: Map<Int, LightAction> = listOf(
        LightAction(
            signal = 1,
            name = "長黄短緑",
            behavior = { action -> gradation(listOf(Color.Yellow, Color.Yellow, Color.Green), 1250L, action) }
        ),
        LightAction(
            signal = 5,
            name = "黄",
            behavior = { action -> lighting(Color.Yellow, action) }
        ),
        LightAction(
            signal = 21,
            name = "薄ピンク点滅(白)",
            behavior = { action -> blinking(colorPinkWhite, 2000L, action) }
        ),
        LightAction(
            signal = 22,
            name = "青点滅",
            behavior = { action -> blinking(Color.Blue, 2400L, action) }
        ),
        LightAction(
            signal = 23,
            name = "オレンジ点滅",
            behavior = { action -> blinking(colorOrange, 2000L, action) }
        ),
        LightAction(
            signal = 25,
            name = "赤オレンジピンク黄緑水青紫",
            behavior = { action -> gradation(listOf(Color.Red, colorOrange, colorPink, Color.Yellow, Color.Green, colorLightBlue, Color.Blue, colorPurple), 1200L, action) }
        ),
        LightAction(
            signal = 26,
            name = "水色点滅",
            behavior = { action -> blinking(colorLightBlue, 2400L, action) }
        ),
        LightAction(
            signal = 27,
            name = "緑点滅",
            behavior = { action -> blinking(Color.Green, 2000L, action) }
        ),
        LightAction(
            signal = 32,
            name = "紫青",
            behavior = { action -> gradation(listOf(colorPurple, Color.Blue), 1200L, action) }
        ),
        LightAction(
            signal = 35,
            name = "ピンク黄緑水色青紫赤オレンジ",
            behavior = { action -> gradation(listOf(colorPink, Color.Yellow, Color.Green, colorLightBlue, Color.Blue, colorPurple, Color.Red, colorOrange), 1250L, action) }
        ),
        LightAction(
            signal = 42,
            name = "ピンク黄水色",
            behavior = { action -> gradation(listOf(colorPink, Color.Yellow, colorLightBlue), 1250L, action) }
        ),
        LightAction(
            signal = 52,
            name = "水色",
            behavior = { action -> lighting(colorLightBlue, action) }
        ),
        LightAction(
            signal = 57,
            name = "紫青高速",
            behavior = { action -> gradation(listOf(colorPurple, colorPurple, Color.Blue), 400L, action) }
        ),
        LightAction(
            signal = 61,
            name = "長薄ピンク緑",
            behavior = { action -> gradation(listOf(colorPinkWhite, colorPinkWhite, Color.Green), 1000L, action) }
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
            behavior = { action -> gradation(listOf(colorLightBlue, colorLightBlue, Color.Yellow), 1000L, action) }
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
            behavior = { action -> blinking(Color.Green, 2400L, action) }
        ),
        LightAction(
            signal = 90,
            name = "ピンク黄水",
            behavior = { action -> gradation(listOf(colorPink, Color.Yellow, colorLightBlue), 1000L, action) },
        ),
        LightAction(
            signal = 95,
            name = "薄ピンク点滅(白)",
            behavior = { action -> blinking(colorPinkWhite, 2500L, action) }
        ),
        LightAction(
            signal = 99,
            name = "長ピンク短赤",
            behavior = { action -> gradation(listOf(colorPink, colorPink, Color.Red),1000L, action) }
        ),
        LightAction(
            signal = 101,
            name = "黄点滅",
            behavior = { action -> blinking(Color.Yellow, 2000L, action) }
        ),
        LightAction(
            signal = 103,
            name = "水色点滅",
            behavior = { action -> blinking(colorLightBlue, 2000L, action) }
        ),
        LightAction(
            signal = 105,
            name = "高速赤オレンジピンク黄緑水色青紫",
            behavior = { action -> gradation(listOf(Color.Red, colorOrange, colorPink, Color.Yellow, Color.Green, colorLightBlue, Color.Blue, colorPurple), 600L, action) }
        ),
        LightAction(
            signal = 107,
            name = "赤点滅",
            behavior = { action -> blinking(Color.Red, 2400L, action) }
        ),
        LightAction(
            signal = 111,
            name = "ピンク点滅",
            behavior = { action -> blinking(colorPink, 2000L, action) }
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
            behavior = { action -> blinking(colorPink, 2400L, action) }
        ),
        LightAction(
            signal = 124,
            name = "ピンク",
            behavior = { action -> lighting(colorPink, action) }
        ),
    ).associateBy { it.signal }

    /**
     * 指定されたシグナルに対応する光のパターンを実行する。
     *
     * @param signal `SignalAnalyzer` から受け取った、実行すべきパターンを決定するための信号。
     * @param action 計算された色をUI（または他の描画先）に反映するためのコールバック関数。
     */
    suspend fun execute(signal: Int, action: (Color) -> Unit) {
        // マップから対応するLightActionを検索し、存在すればその振る舞いを実行する。
        val lightAction = patternMap[signal]
        lightAction?.behavior?.execute(action)
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
        val startTime = System.currentTimeMillis()
        var elapsedTime = 0L

        // durationMillisの間、ループを実行
        while (elapsedTime < durationMillis) {
            elapsedTime = System.currentTimeMillis() - startTime

            // 進行度を計算 (0.0 -> 1.0)
            val progress = elapsedTime.toFloat() / durationMillis
            val fraction = SharpBlink.transform(progress)

            // 開始色(offColor)と目的色(color)を線形補間
            val blendedColor = lerp(start = offColor, stop = color, fraction = fraction)
            action(blendedColor)

            // 処理時間を考慮して次のフレームまでの待機時間を計算
            elapsedTime = System.currentTimeMillis() - startTime
            val delayTime = minOf(delayPerStep, durationMillis - elapsedTime)
            if (delayTime>0) {
                delay(delayTime)
            }
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
                    durationMillis = durationMillis,
                    action
                )
                currentColor = color
            }
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
        val startTime = System.currentTimeMillis()
        var elapsedTime = 0L

        // durationMillisの間、ループを実行
        while (elapsedTime < durationMillis) {
            elapsedTime = System.currentTimeMillis() - startTime

            // 進行度を計算 (0.0 -> 1.0)
            val progress = elapsedTime.toFloat() / durationMillis
            val fraction = SharpTransition.transform(progress)
            val blendedColor = lerp(start = from, stop = to, fraction = fraction)
            action(blendedColor)

            // 処理時間を考慮して次のフレームまでの待機時間を計算
            elapsedTime = System.currentTimeMillis() - startTime
            val delayTime = minOf(delayPerStep, durationMillis - elapsedTime)
            if (delayTime>0) {
                delay(delayTime)
            }
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
