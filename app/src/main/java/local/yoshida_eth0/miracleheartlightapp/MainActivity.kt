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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import io.morfly.compose.bottomsheet.material3.BottomSheetScaffold
import io.morfly.compose.bottomsheet.material3.rememberBottomSheetScaffoldState
import io.morfly.compose.bottomsheet.material3.rememberBottomSheetState
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
                frequencyMagnitudes = frequencyMagnitudes.value,
                initialSensitivity = capture.sensitivity,
                onSensitivityChanged = { newSensitivity ->
                    capture.sensitivity = newSensitivity
                },
                initialGain = synthesizer.gain,
                onGainChanged = { newGain ->
                    synthesizer.gain = newGain
                }
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

enum class SheetValue { InfoPanelExpanded, FrequencyBarGraphExpanded, ControlPanelExpanded }

/**
 * アプリケーションのメインUIを定義するコンポーザブル関数。
 * プレビューと実際のUI描画の両方から利用される。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AppUI(
    lightColor: Color,
    activeLightAction: LightAction?,
    detectedLightAction: LightAction?,
    frequencyMagnitudes: Map<Int, Float>,
    initialSensitivity: Float,
    onSensitivityChanged: (Float) -> Unit,
    initialGain: Float,
    onGainChanged: (Float) -> Unit
) {
    // コントロールパネルのスライダーの状態を保持するState
    var sensitivity by remember { mutableFloatStateOf(initialSensitivity) }
    var gain by remember { mutableFloatStateOf(initialGain) }

    // ボトムシートの状態を保持するState
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.FrequencyBarGraphExpanded,
        defineValues = {
            SheetValue.InfoPanelExpanded at height(115.dp)
            SheetValue.FrequencyBarGraphExpanded at height(315.dp)
            SheetValue.ControlPanelExpanded at contentHeight
        }
    )
    val scaffoldState = rememberBottomSheetScaffoldState(sheetState)

    MiracleHeartLightAppTheme {
        BottomSheetScaffold(
            scaffoldState = scaffoldState,
            sheetDragHandle = null,
            sheetShape = RoundedCornerShape(0.dp),
            sheetContent = {
                Column(
                    modifier = Modifier
                    .windowInsetsPadding(WindowInsets.navigationBars)
                ) {
                    // LightAction名表示
                    InfoPanel(
                        activeLightAction = activeLightAction,
                        detectedLightAction = detectedLightAction
                    )

                    // 周波数の強度を可視化する棒グラフ
                    FrequencyBarGraph(
                        magnitudes = frequencyMagnitudes,
                        modifier = Modifier
                            .height(200.dp)
                            .fillMaxSize()
                            .background(Color(0xFF1C1C1E))
                    )

                    // コントロールパネル
                    ControlPanel(
                        sensitivity = sensitivity,
                        onSensitivityChanged = { newValue ->
                            sensitivity = newValue
                            onSensitivityChanged(newValue)
                        },
                        gain = gain,
                        onGainChanged = { newValue ->
                            gain = newValue
                            onGainChanged(newValue)
                        }
                    )
                }
            },
            content = {
                // ライトの色を表示する領域
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .background(lightColor) // Stateに連動した背景色
                )
            }
        )
    }
}

/**
 * 検出された信号と現在アクティブな信号の情報を表示するパネル。
 *
 * @param activeLightAction 現在再生中のライトパターンの情報。
 * @param detectedLightAction 直近で検出された信号の情報。
 */
@Composable
private fun InfoPanel(
    activeLightAction: LightAction?,
    detectedLightAction: LightAction?
) {
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
}

@Composable
fun FrequencyBarGraph(
    magnitudes: Map<Int, Float>,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom
    ) {
        FrequenciesCapture.targetFrequencies.forEach { freq ->
            val magnitude = magnitudes[freq] ?: 0f
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
                    val normalizedHeight = magnitude.coerceIn(0f, 1f)
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

/**
 * 感度と音量を調整するためのスライダーUIパネル。
 */
@Composable
private fun ControlPanel(
    sensitivity: Float,
    onSensitivityChanged: (Float) -> Unit,
    gain: Float,
    onGainChanged: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp) // スライダー間のスペースを追加
    ) {
        // 感度スライダー
        Column {
            Text(
                text = "感度: ${String.format("%.1f", sensitivity)}",
                color = Color.Black,
                fontSize = 14.sp
            )
            Slider(
                value = sensitivity,
                onValueChange = onSensitivityChanged,
                valueRange = 1.0f..1000.0f
            )
        }

        // 音量スライダー
        Column {
            Text(
                text = "音量: ${String.format("%.1f", gain)}",
                color = Color.Black,
                fontSize = 14.sp
            )
            Slider(
                value = gain,
                onValueChange = onGainChanged,
                valueRange = 0.0f..50.0f
            )
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
        frequencyMagnitudes = dummyMagnitudes,
        initialSensitivity = 100.0f,
        onSensitivityChanged = {},
        initialGain = 1.0f,
        onGainChanged = {}
    )
}
