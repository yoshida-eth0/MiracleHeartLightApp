package local.yoshida_eth0.miracleheartlight

import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.Easing
import kotlin.math.PI
import kotlin.math.sin

/**
 * 点滅（Blink）用の輝度カーブを定義するオブジェクト。
 */
object BlinkEasing {
    /**
     * Sinカーブを利用した、自然なフェードイン・フェードアウトの点滅。
     */
    val NormalBlink = Easing { progress ->
        sin(progress * PI.toFloat())
    }

    /**
     * 鋭い立ち上がりと立ち下がりを持つ点滅カーブ。
     *
     * - 0% ~ 25%: フェードイン (0.0 -> 1.0)
     * - 25% ~ 50%: 点灯状態を維持 (1.0)
     * - 50% ~ 75%: フェードアウト (1.0 -> 0.0)
     * - 75% ~ 100%: 消灯状態を維持 (0.0)
     */
    val SharpBlink = Easing { progress ->
        if (progress < 0.25f) {
            // 前半のさらに前半で、NormalBlinkの前半（フェードイン）を再生
            NormalBlink.transform(progress * 2)
        } else if (progress < 0.5f) {
            // 点灯状態を維持
            1.0f
        } else if (progress < 0.75f) {
            // 後半の前半を使って、NormalBlinkの後半（フェードアウト）を再生
            val scaledProgress = (progress - 0.5f) * 2 + 0.5f
            NormalBlink.transform(scaledProgress)
        } else {
            // 消灯状態を維持
            0.0f
        }
    }
}

/**
 * 遷移（Transition）用の変化率カーブを定義するオブジェクト。
 */
object TransitionEasing {
    /**
     * 通常の2倍の速度でEaseOut遷移を行い、アニメーション時間の中間で完了するカーブ。
     * 残りの時間は完了した状態を維持する。
     *
     * - 0% ~ 50%: EaseOutで遷移 (0.0 -> 1.0)
     * - 50% ~ 100%: 完了状態を維持 (1.0)
     */
    val SharpTransition = Easing { progress ->
        val scaledProgress = (progress * 2).coerceIn(0f, 1f)
        EaseOut.transform(scaledProgress)
    }
}
