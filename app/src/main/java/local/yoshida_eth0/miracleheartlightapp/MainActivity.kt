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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import local.yoshida_eth0.miracleheartlight.FrequenciesCapture
import local.yoshida_eth0.miracleheartlight.LightAction
import local.yoshida_eth0.miracleheartlight.LightPattern
import local.yoshida_eth0.miracleheartlight.SignalAnalyzer
import local.yoshida_eth0.miracleheartlightapp.ui.theme.MiracleHeartLightAppTheme

class MainActivity : ComponentActivity() {

    private lateinit var capture: FrequenciesCapture
    private lateinit var analyzer: SignalAnalyzer
    private lateinit var lightPattern: LightPattern
    private var currentLightAction: LightAction? = null

    // --- UIの状態を保持するStateを定義 ---
    private val _frequencyMagnitudes = mutableStateOf<Map<Int, Float>>(emptyMap())
    private val frequencyMagnitudes: State<Map<Int, Float>> = _frequencyMagnitudes
    private val _signalName = mutableStateOf("")
    private val signalName: State<String> = _signalName

    // 背景色とアニメーションジョブの状態
    private val _backgroundColor = mutableStateOf(Color.Black)
    private val backgroundColor: State<Color> = _backgroundColor
    private var colorChangeJob: Job? = null

    private var originalBrightness: Float = -1f

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                capture.start()
            } else {
                Log.e("MainActivity", "Permission for RECORD_AUDIO was denied.")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        capture = FrequenciesCapture(this)
        analyzer = SignalAnalyzer()
        lightPattern = LightPattern()

        // --- リスナー内でStateを更新 ---
        capture.onFrequenciesCaptured = { newMagnitudes ->
            // DoubleをFloatに変換し、Stateを更新する
            val newIntMagnitudes = newMagnitudes.mapValues { it.value.toInt() }
            //Log.d("MainActivity", "Captured frequencies: $newIntMagnitudes")
            analyzer.update(newMagnitudes)
            _frequencyMagnitudes.value = newMagnitudes.mapValues { it.value.toFloat() }
        }

        // --- シグナル変更リスナーを設定 ---
        analyzer.onSignalChanged = { signal ->
            // UIスレッドで実行
            lifecycleScope.launch {
                if (lightPattern.patternMap.containsKey(signal)) {
                    // 定義されたシグナルの場合
                    Log.d("MainActivity", "defined signal: $signal")
                    val lightAction = lightPattern.patternMap[signal]!!
                    _signalName.value = "${lightAction.signal}: ${lightAction.name}"

                    if (currentLightAction?.signal != lightAction.signal) {
                        Log.d("MainActivity", "change LightAction: ${lightAction.signal}: ${lightAction.name}")
                        currentLightAction = lightAction

                        // 既存の色変更ジョブがあればキャンセル
                        colorChangeJob?.cancel()

                        // 新しい色変更ジョブを開始
                        colorChangeJob = launch {
                            // LightPattern.executeを呼び出してアニメーション処理を委譲
                            lightPattern.execute(signal) { color ->
                                _backgroundColor.value = color
                            }
                        }
                    }
                } else {
                    // 未定義のシグナルの場合
                    Log.d("MainActivity", "undefined signal: $signal")
                    _signalName.value = "$signal: 未定義"
                }
            }
        }


        enableEdgeToEdge()
        setContent {
            MiracleHeartLightAppTheme {
                // --- 画面全体を上下に分割するColumn ---
                Column(modifier = Modifier.fillMaxSize()) {

                    // --- 上半分：背景色 --- (元は下半分)
                    Box(
                        modifier = Modifier
                            .weight(1f) // 上半分を占める
                            .fillMaxWidth()
                            .background(backgroundColor.value) // Stateに連動した背景色
                    )

                    // --- 中間：シグナル名表示 ---
                    Text(
                        text = signalName.value,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.DarkGray) // 背景色で見やすくする
                            .padding(8.dp),
                        color = Color.White, // 文字色
                        textAlign = TextAlign.Center,
                        fontSize = 16.sp
                    )

                    // --- 下半分：棒グラフ --- (元は上半分)
                    Scaffold(
                        modifier = Modifier
                            .weight(1f) // 下半分を占める
                            .fillMaxWidth()
                    ) { innerPadding ->
                        FrequencyBarGraph(
                            magnitudes = frequencyMagnitudes.value,
                            modifier = Modifier
                                .padding(innerPadding)
                                .fillMaxSize()
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val layoutParams = window.attributes
        originalBrightness = layoutParams.screenBrightness
        layoutParams.screenBrightness = 1.0f
        window.attributes = layoutParams

        // 背景色を黒に戻す
        _backgroundColor.value = Color.Black
        _signalName.value = ""
        currentLightAction = null

        checkPermissionAndStartCapture()
    }

    override fun onPause() {
        super.onPause()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val layoutParams = window.attributes
        layoutParams.screenBrightness = originalBrightness
        window.attributes = layoutParams

        // 色変更ジョブをキャンセル
        colorChangeJob?.cancel()
        capture.stop()
    }

    private fun checkPermissionAndStartCapture() {
        when (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)) {
            PackageManager.PERMISSION_GRANTED -> {
                capture.start()
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }
}

// --- 以下は変更なし ---
@Composable
fun FrequencyBarGraph(
    magnitudes: Map<Int, Float>,
    modifier: Modifier = Modifier
) {
    val normalizationCap = 20_000f

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(300.dp)
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom
    ) {
        magnitudes.toSortedMap().forEach { (freq, magnitude) ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
                modifier = Modifier.weight(1f)
            ) {
                val normalizedHeight = (magnitude / normalizationCap).coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .width(30.dp)
                        .fillMaxHeight(normalizedHeight)
                        .background(Color.Cyan)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${freq}Hz",
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    MiracleHeartLightAppTheme {
        val dummyData = mapOf(
            18500 to 0f, 18750 to 0f, 19000 to 0f, 19250 to 0f, 19500 to 0f
        )
        FrequencyBarGraph(
            magnitudes = dummyData,
            modifier = Modifier.fillMaxSize()
        )
    }
}
