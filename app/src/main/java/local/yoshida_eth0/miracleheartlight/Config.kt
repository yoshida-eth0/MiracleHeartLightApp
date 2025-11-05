package local.yoshida_eth0.miracleheartlight

class Config(
    val sampleRate: Int = 44100,
    val fftSize: Int = 1024,
    var fftNeighborCount: Int = 2
) {
    companion object {
        /**
         * アプリケーション全体で共有されるConfigの単一インスタンス。
         */
        val sharedInstance: Config = Config()
    }
}
