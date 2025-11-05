package local.yoshida_eth0.miracleheartlight

/**
 * 音声処理とFFT解析に関する設定を保持するクラス。
 *
 * @property sampleRate オーディオのサンプリングレート（Hz）。デフォルトは44100Hz。
 * @property fftSize FFT（高速フーリエ変換）のサイズ。一度に解析するサンプル数を表す。デフォルトは1024。
 * @property fftNeighborCount FFT解析時に、中心周波数の周囲で平均を取るための近傍インデックスの数。デフォルトは2。
 */
class Config(
    val sampleRate: Int = 44100,
    val fftSize: Int = 1024,
    var fftNeighborCount: Int = 2
) {
    companion object {
        /**
         * アプリケーション全体で共有されるConfigの単一インスタンス。
         * これにより、どこからでも同じ設定値にアクセスできる。
         */
        val sharedInstance: Config = Config()
    }
}
