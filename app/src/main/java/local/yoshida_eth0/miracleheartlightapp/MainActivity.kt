package local.yoshida_eth0.miracleheartlightapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import local.yoshida_eth0.miracleheartlight.AudioSynthesizer
import local.yoshida_eth0.miracleheartlight.FrequenciesCapture
import local.yoshida_eth0.miracleheartlight.LightAction
import local.yoshida_eth0.miracleheartlight.LightPattern
import local.yoshida_eth0.miracleheartlight.SignalAnalyzer
import local.yoshida_eth0.miracleheartlightapp.ui.theme.MiracleHeartLightAppTheme

class MainActivity : ComponentActivity() {

    private lateinit var capture: FrequenciesCapture
    private lateinit var analyzer: SignalAnalyzer
    private lateinit var lightPattern: LightPattern
    private lateinit var synthesizer: AudioSynthesizer

    // --- UIの状態を保持するStateを定義 ---
    // 周波数とその強度を保持するState
    private val _frequencyMagnitudes = mutableStateOf<Map<Int, Float>>(emptyMap())
    private val frequencyMagnitudes: State<Map<Int, Float>> = _frequencyMagnitudes
    // 検出されたシグナルに対応するLightActionを保持するState
    private val _detectedLightAction = mutableStateOf<LightAction?>(null)
    private val detectedLightAction: State<LightAction?> = _detectedLightAction

    // 現在実行中のLightActionを保持するState
    private val _activeLightAction = mutableStateOf<LightAction?>(null)
    private val activeLightAction: State<LightAction?> = _activeLightAction

    // 背景色とアニメーションジョブの状態
    // 背景色を保持するState
    private val _lightColor = mutableStateOf(Color.Black)
    private val lightColor: State<Color> = _lightColor
    // 色変更アニメーションのコルーチンジョブ
    private var colorChangeJob: Job? = null

    // 画面の元の明るさを保持する変数
    private var originalBrightness: Float = -1f
    private val enabledFullBrightness: Boolean = false // ビルド時に適宜変更

    // 権限リクエストの結果をハンドリングするランチャー
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                // 権限が許可された場合はキャプチャを開始
                capture.start()
                synthesizer.start()
            } else {
                // 権限が拒否された場合はエラーログを出力
                Log.e("MainActivity", "Permission for RECORD_AUDIO was denied.")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 音声処理関連のクラスを初期化
        capture = FrequenciesCapture(this)
        analyzer = SignalAnalyzer()
        lightPattern = LightPattern()
        synthesizer = AudioSynthesizer()

        // --- リスナー内でStateを更新 ---
        // FrequenciesCaptureからのコールバックを設定
        capture.onFrequenciesCaptured = { newMagnitudes ->
            // 音声合成クラスに周波数データを渡す
            synthesizer.synthesizeAndPlay(newMagnitudes)
            // SignalAnalyzerに新しい周波数データを渡して更新
            analyzer.update(newMagnitudes)
            // UI表示用にStateを更新
            _frequencyMagnitudes.value = newMagnitudes
        }

        // SignalAnalyzerからのコールバックを設定
        analyzer.onSignalChanged = { signal ->
            // UIの更新はメインスレッドのCoroutineScopeで実行
            lifecycleScope.launch {
                val lightAction = lightPattern.patternMap[signal]
                if (lightAction != null) {
                    // 定義済みのシグナルの場合
                    Log.d("MainActivity", "defined signal: $signal")
                    // シグナル名を表示用に更新
                    _detectedLightAction.value = lightAction

                    if (_activeLightAction.value?.signal != lightAction.signal) {
                        // 異なるシグナルが検出された場合
                        Log.d("MainActivity", "change LightAction: ${lightAction.signal}: ${lightAction.name}")
                        _activeLightAction.value = lightAction

                        // 実行中のアニメーションがあればキャンセル
                        colorChangeJob?.cancel()

                        // 新しいアニメーションのコルーチンを開始
                        colorChangeJob = launch {
                            // LightPatternに処理を委譲し、色の更新をコールバックで受け取る
                            lightPattern.execute(signal) { color ->
                                _lightColor.value = color
                            }
                        }
                    }
                } else {
                    // 未定義のシグナルの場合
                    Log.d("MainActivity", "undefined signal: $signal")
                    _detectedLightAction.value = LightAction(
                        signal = signal,
                        name = "未定義",
                        behavior = null
                    )
                }
            }
        }

        enableEdgeToEdge()
        setContent {
            AppUI(
                lightColor = lightColor.value,
                activeLightAction = activeLightAction.value,
                detectedLightAction = detectedLightAction.value,
                frequencyMagnitudes = frequencyMagnitudes.value
            )
        }
    }

    override fun onResume() {
        super.onResume()
        // 画面を常にオンに設定
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // 画面の明るさを最大に設定
        if (enabledFullBrightness) {
            val layoutParams = window.attributes
            originalBrightness = layoutParams.screenBrightness // 元の明るさを保存
            layoutParams.screenBrightness = 1.0f
            window.attributes = layoutParams
        }

        // 各状態をリセット
        _lightColor.value = Color.Black
        _detectedLightAction.value = null
        _activeLightAction.value = null

        // 権限を確認し、音声キャプチャを開始
        checkPermissionAndStartCapture()
    }

    override fun onPause() {
        super.onPause()
        // 画面常時オンを解除
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // 画面の明るさを元に戻す
        if (enabledFullBrightness) {
            val layoutParams = window.attributes
            layoutParams.screenBrightness = originalBrightness
            window.attributes = layoutParams
        }

        // 実行中のアニメーションをキャンセル
        colorChangeJob?.cancel()
        // 音声キャプチャを停止
        capture.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        synthesizer.release()
    }

    private fun checkPermissionAndStartCapture() {
        when (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)) {
            PackageManager.PERMISSION_GRANTED -> {
                // すでに権限がある場合はキャプチャを開始
                capture.start()
                synthesizer.start()
            }
            else -> {
                // 権限がない場合はリクエストダイアログを表示
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }
}

/**
 * アプリケーションのメインUIを定義するコンポーザブル関数。
 * プレビューと実際のUI描画の両方から利用される。
 */
@Composable
fun AppUI(
    lightColor: Color,
    activeLightAction: LightAction?,
    detectedLightAction: LightAction?,
    frequencyMagnitudes: Map<Int, Float>
) {
    MiracleHeartLightAppTheme {
        // 画面全体を上下に分割するColumn
        Column(modifier = Modifier.fillMaxSize()) {

            // 上半分：ライトの色を表示する領域
            Box(
                modifier = Modifier
                    .weight(2f) // 上半分を占める
                    .fillMaxWidth()
                    .background(lightColor) // Stateに連動した背景色
            )

            // 中間：LightAction名表示
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.DarkGray)
                    .padding(horizontal = 16.dp, vertical = 8.dp), // 全体のパディング
                verticalArrangement = Arrangement.spacedBy(4.dp) // Row間のスペース
            ) {
                // 表示中のシグナル名を表示
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "表示",
                        color = Color.LightGray, // ラベルの色を薄いグレーに
                        fontSize = 16.sp,
                        modifier = Modifier.width(50.dp) // ラベルに固定幅を指定
                    )
                    Text(
                        text = activeLightAction?.let { "${it.signal}: ${it.name}" } ?: "---",
                        color = Color.White,
                        fontSize = 16.sp
                    )
                }

                // 検出されたシグナル名を表示
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "検出",
                        color = Color.LightGray, // ラベルの色を薄いグレーに
                        fontSize = 16.sp,
                        modifier = Modifier.width(50.dp) // ラベルに同じ固定幅を指定
                    )
                    Text(
                        text = detectedLightAction?.let { "${it.signal}: ${it.name}" } ?: "---",
                        color = Color.White,
                        fontSize = 16.sp
                    )
                }
            }

            // 下半分：周波数の強度を可視化する棒グラフ
            Scaffold(
                modifier = Modifier
                    .weight(1f) // 下半分を占める
                    .fillMaxWidth(),
                containerColor = Color(0xFF1C1C1E)
            ) { innerPadding ->
                FrequencyBarGraph(
                    magnitudes = frequencyMagnitudes,
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize()
                )
            }
        }
    }
}

@Composable
fun FrequencyBarGraph(
    magnitudes: Map<Int, Float>,
    modifier: Modifier = Modifier
) {
    val normalizationCap = 40f

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(300.dp)
            .padding(16.dp, 0.dp, 16.dp, 16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom
    ) {
        magnitudes.toSortedMap().forEach { (freq, magnitude) ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.BottomCenter // Box内の要素を下揃えにする
                ) {
                    val normalizedHeight = (magnitude / normalizationCap).coerceIn(0f, 1f)
                    Box(
                        modifier = Modifier
                            .width(30.dp)
                            .fillMaxHeight(normalizedHeight)
                            .background(Color(0xFF008B8B))
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${freq}Hz",
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    color = Color.White
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    // プレビュー用のダミーデータを定義
    val dummyLightColor = Color(0xFFC0CB)
    val dummyActiveAction = LightAction(95, "薄ピンク点滅(白)", null)
    val dummyDetectedAction = LightAction(95, "薄ピンク点滅(白)", null)
    val dummyMagnitudes = FrequenciesCapture.targetFrequencies.associateWith {
        (10..40).random().toFloat()
    }

    // AppUIにダミーデータを渡してプレビューする
    AppUI(
        lightColor = dummyLightColor,
        activeLightAction = dummyActiveAction,
        detectedLightAction = dummyDetectedAction,
        frequencyMagnitudes = dummyMagnitudes
    )
}
