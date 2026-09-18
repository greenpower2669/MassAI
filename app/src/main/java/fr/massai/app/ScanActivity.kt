package fr.massai.app

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.SystemClock
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.io.File
import kotlin.math.max
import kotlin.math.min

class ScanActivity : AppCompatActivity(), SensorEventListener {

    companion object {
        const val EXTRA_VIDEO_URI = "fr.massai.app.extra.VIDEO_URI"
        const val EXTRA_METADATA_PATH = "fr.massai.app.extra.METADATA_PATH"
        const val EXTRA_TARGET_LEVELS = "fr.massai.app.extra.TARGET_LEVELS"
        const val EXTRA_CAMERA_PREFERENCE = "fr.massai.app.extra.CAMERA_PREFERENCE"

        private const val SENSOR_SAMPLE_PERIOD_NS = 50_000_000L
    }

    private data class CameraChoice(
        val selector: CameraSelector,
        val requested: CameraPreference,
        val actual: CameraPreference,
        val fallbackUsed: Boolean
    )

    private lateinit var previewView: PreviewView
    private lateinit var recordButton: Button
    private lateinit var nextLevelButton: Button
    private lateinit var statusText: TextView
    private lateinit var coverageText: TextView
    private lateinit var spiralGuideView: SpiralGuideView

    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var outputFile: File? = null
    private var cancelling = false

    private lateinit var sensorManager: SensorManager
    private var rotationSensor: Sensor? = null
    private var recordingStartNs = 0L
    private var lastSensorSampleNs = 0L
    private var lastRawYaw: Double? = null
    private var unwrappedYaw = 0.0
    private var minYaw = 0.0
    private var maxYaw = 0.0
    private val orientationSamples = ArrayList<OrientationSample>()

    private var targetLevels = 2
    private var requestedCameraPreference = CameraPreference.AUTO
    private var actualCameraPreference = CameraPreference.AUTO
    private var selectedCameraId: String? = null
    private var selectedFocalLengthMm: Float? = null
    private var cameraFallbackUsed = false

    private var currentGuideLevel = 0
    private var completedGuideLevels = 0
    private var waitingForLevelAlignment = false
    private var allGuideLevelsComplete = false

    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startCamera()
            } else {
                statusText.text = "Permission caméra refusée."
                recordButton.isEnabled = false
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scan)

        targetLevels = intent.getIntExtra(EXTRA_TARGET_LEVELS, 2)
            .coerceIn(1, SpiralGuideView.MAX_LEVELS)
        requestedCameraPreference = CameraPreference.fromName(
            intent.getStringExtra(EXTRA_CAMERA_PREFERENCE)
        )

        previewView = findViewById(R.id.previewView)
        recordButton = findViewById(R.id.recordButton)
        nextLevelButton = findViewById(R.id.nextLevelButton)
        statusText = findViewById(R.id.recordStatusText)
        coverageText = findViewById(R.id.coverageText)
        spiralGuideView = findViewById(R.id.spiralGuideView)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        spiralGuideView.configure(targetLevels, rotationSensor != null)

        coverageText.text = if (rotationSensor != null) {
            "Guide : niveau 1/$targetLevels prêt"
        } else {
            "Capteur d'orientation indisponible — mode de secours"
        }

        recordButton.setOnClickListener {
            if (recording == null) startRecording() else stopRecording()
        }

        nextLevelButton.setOnClickListener {
            advanceGuideLevel()
        }

        findViewById<Button>(R.id.cancelScanButton).setOnClickListener {
            if (recording != null) {
                cancelling = true
                statusText.text = "Annulation…"
                recordButton.isEnabled = false
                nextLevelButton.visibility = View.GONE
                recording?.stop()
            } else {
                setResult(Activity.RESULT_CANCELED)
                finish()
            }
        }

        if (
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    override fun onResume() {
        super.onResume()
        rotationSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    override fun onPause() {
        sensorManager.unregisterListener(this)
        super.onPause()
    }

    @androidx.annotation.OptIn(ExperimentalCamera2Interop::class)
    private fun startCamera() {
        statusText.text = "Initialisation de la caméra…"
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                val choice = selectCamera(cameraProvider, requestedCameraPreference)
                actualCameraPreference = choice.actual
                cameraFallbackUsed = choice.fallbackUsed

                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }

                val qualitySelector = QualitySelector.fromOrderedList(
                    listOf(Quality.FHD, Quality.HD, Quality.SD),
                    FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)
                )

                val recorder = Recorder.Builder()
                    .setQualitySelector(qualitySelector)
                    .build()

                videoCapture = VideoCapture.withOutput(recorder)

                cameraProvider.unbindAll()
                val camera = cameraProvider.bindToLifecycle(
                    this,
                    choice.selector,
                    preview,
                    videoCapture
                )

                val camera2Info = Camera2CameraInfo.from(camera.cameraInfo)
                selectedCameraId = camera2Info.cameraId
                selectedFocalLengthMm = camera2Info
                    .getCameraCharacteristic(
                        CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS
                    )
                    ?.minOrNull()

                recordButton.isEnabled = true

                val focalText = selectedFocalLengthMm?.let {
                    " · %.1f mm".format(it)
                } ?: ""

                val fallbackText = if (cameraFallbackUsed) {
                    " · ultra indisponible, caméra normale"
                } else {
                    ""
                }

                statusText.text =
                    "Caméra prête — ${actualCameraPreference.label}$focalText$fallbackText"
            } catch (e: Exception) {
                if (requestedCameraPreference == CameraPreference.ULTRA_WIDE) {
                    try {
                        bindDefaultCamera(cameraProviderFuture.get())
                    } catch (fallbackError: Exception) {
                        recordButton.isEnabled = false
                        statusText.text =
                            "Impossible d'ouvrir la caméra : " +
                                (fallbackError.message ?: "erreur inconnue")
                    }
                } else {
                    recordButton.isEnabled = false
                    statusText.text =
                        "Impossible d'ouvrir la caméra : ${e.message ?: "erreur inconnue"}"
                }
            }
        }, ContextCompat.getMainExecutor(this))
    }

    @androidx.annotation.OptIn(ExperimentalCamera2Interop::class)
    private fun bindDefaultCamera(cameraProvider: ProcessCameraProvider) {
        val preview = Preview.Builder().build().also {
            it.surfaceProvider = previewView.surfaceProvider
        }

        val recorder = Recorder.Builder()
            .setQualitySelector(
                QualitySelector.fromOrderedList(
                    listOf(Quality.FHD, Quality.HD, Quality.SD),
                    FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)
                )
            )
            .build()

        videoCapture = VideoCapture.withOutput(recorder)
        cameraProvider.unbindAll()

        val camera = cameraProvider.bindToLifecycle(
            this,
            CameraSelector.DEFAULT_BACK_CAMERA,
            preview,
            videoCapture
        )

        val camera2Info = Camera2CameraInfo.from(camera.cameraInfo)
        selectedCameraId = camera2Info.cameraId
        selectedFocalLengthMm = camera2Info
            .getCameraCharacteristic(
                CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS
            )
            ?.minOrNull()

        actualCameraPreference = CameraPreference.NORMAL
        cameraFallbackUsed = true
        recordButton.isEnabled = true
        statusText.text =
            "Caméra prête — ultra indisponible, caméra arrière normale."
    }

    @androidx.annotation.OptIn(ExperimentalCamera2Interop::class)
    private fun selectCamera(
        provider: ProcessCameraProvider,
        preference: CameraPreference
    ): CameraChoice {
        if (preference != CameraPreference.ULTRA_WIDE) {
            return CameraChoice(
                selector = CameraSelector.DEFAULT_BACK_CAMERA,
                requested = preference,
                actual = preference,
                fallbackUsed = false
            )
        }

        val backInfos = CameraSelector.DEFAULT_BACK_CAMERA
            .filter(provider.availableCameraInfos)

        if (backInfos.size < 2) {
            return CameraChoice(
                selector = CameraSelector.DEFAULT_BACK_CAMERA,
                requested = preference,
                actual = CameraPreference.NORMAL,
                fallbackUsed = true
            )
        }

        val withFocal = backInfos.mapNotNull { info ->
            val focal = focalLength(info) ?: return@mapNotNull null
            info to focal
        }

        if (withFocal.size < 2) {
            return CameraChoice(
                selector = CameraSelector.DEFAULT_BACK_CAMERA,
                requested = preference,
                actual = CameraPreference.NORMAL,
                fallbackUsed = true
            )
        }

        val ultra = withFocal.minByOrNull { it.second }
            ?: return CameraChoice(
                CameraSelector.DEFAULT_BACK_CAMERA,
                preference,
                CameraPreference.NORMAL,
                true
            )

        val sorted = withFocal.sortedBy { it.second }
        val distinctEnough =
            sorted.size >= 2 && sorted[0].second < sorted[1].second * 0.90f

        return if (distinctEnough) {
            CameraChoice(
                selector = ultra.first.cameraSelector,
                requested = preference,
                actual = CameraPreference.ULTRA_WIDE,
                fallbackUsed = false
            )
        } else {
            CameraChoice(
                selector = CameraSelector.DEFAULT_BACK_CAMERA,
                requested = preference,
                actual = CameraPreference.NORMAL,
                fallbackUsed = true
            )
        }
    }

    @androidx.annotation.OptIn(ExperimentalCamera2Interop::class)
    private fun focalLength(info: CameraInfo): Float? {
        return Camera2CameraInfo.from(info)
            .getCameraCharacteristic(
                CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS
            )
            ?.minOrNull()
    }

    private fun startRecording() {
        val capture = videoCapture ?: return

        cancelling = false
        resetOrientationCapture()

        val baseDir = getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: filesDir
        val file = File(baseDir, "MassAI_scan_${System.currentTimeMillis()}.mp4")
        outputFile = file

        val options = FileOutputOptions.Builder(file).build()

        recording = capture.output
            .prepareRecording(this, options)
            .start(ContextCompat.getMainExecutor(this)) { event ->
                when (event) {
                    is VideoRecordEvent.Start -> {
                        recordingStartNs = SystemClock.elapsedRealtimeNanos()
                        recordButton.text = "ARRÊTER ET RECONSTRUIRE"
                        statusText.text =
                            "Niveau 1/$targetLevels — suivez l'anneau autour du sujet."
                        updateCoverageText()
                    }

                    is VideoRecordEvent.Finalize -> {
                        recording = null
                        recordButton.text = "DÉMARRER L'ENREGISTREMENT"
                        nextLevelButton.visibility = View.GONE
                        val saved = outputFile

                        if (cancelling) {
                            saved?.delete()
                            setResult(Activity.RESULT_CANCELED)
                            finish()
                            return@start
                        }

                        if (!event.hasError() && saved != null) {
                            val metadataFile = File(
                                saved.parentFile ?: filesDir,
                                "${saved.nameWithoutExtension}.massai.json"
                            )

                            ScanMetadata(
                                samples = orientationSamples.toList(),
                                sensorAvailable = rotationSensor != null,
                                targetLevels = if (rotationSensor != null) {
                                    targetLevels
                                } else {
                                    1
                                },
                                completedLevels = completedGuideLevels,
                                cameraPreference = actualCameraPreference.name,
                                cameraId = selectedCameraId,
                                focalLengthMm = selectedFocalLengthMm
                            ).writeTo(metadataFile)

                            val result = Intent()
                                .putExtra(EXTRA_VIDEO_URI, Uri.fromFile(saved).toString())
                                .putExtra(EXTRA_METADATA_PATH, metadataFile.absolutePath)

                            setResult(Activity.RESULT_OK, result)
                            finish()
                        } else {
                            statusText.text =
                                "Échec de l'enregistrement (code ${event.error}). Vous pouvez réessayer."
                            saved?.delete()
                            recordButton.isEnabled = true
                            recordingStartNs = 0L
                        }
                    }
                }
            }
    }

    private fun stopRecording() {
        val coverage = currentCoverageDegrees()
        statusText.text = when {
            rotationSensor == null ->
                "Finalisation de la vidéo…"

            completedGuideLevels == 0 && coverage < 300.0 ->
                "Finalisation… première boucle incomplète (${coverage.toInt()}°)."

            completedGuideLevels < targetLevels ->
                "Finalisation… $completedGuideLevels/$targetLevels passages terminés."

            else ->
                "Finalisation… guide multi-passage complet."
        }

        recordButton.isEnabled = false
        nextLevelButton.visibility = View.GONE
        recording?.stop()
    }

    private fun resetOrientationCapture() {
        recordingStartNs = 0L
        lastSensorSampleNs = 0L
        lastRawYaw = null
        unwrappedYaw = 0.0
        minYaw = 0.0
        maxYaw = 0.0
        orientationSamples.clear()

        currentGuideLevel = 0
        completedGuideLevels = 0
        waitingForLevelAlignment = false
        allGuideLevelsComplete = false
        nextLevelButton.visibility = View.GONE
        spiralGuideView.configure(targetLevels, rotationSensor != null)
    }

    private fun advanceGuideLevel() {
        if (recording == null || !waitingForLevelAlignment) return
        if (currentGuideLevel >= targetLevels - 1) return

        currentGuideLevel++
        waitingForLevelAlignment = false
        nextLevelButton.visibility = View.GONE
        spiralGuideView.setActiveLevel(currentGuideLevel)

        statusText.text =
            "Niveau ${currentGuideLevel + 1}/$targetLevels — refaites une boucle complète."
        updateCoverageText()
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return
        if (recordingStartNs <= 0L || recording == null) return
        if (event.timestamp < recordingStartNs) return

        val rotationMatrix = FloatArray(9)
        val orientation = FloatArray(3)
        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
        SensorManager.getOrientation(rotationMatrix, orientation)
        val rawYaw = orientation[0].toDouble()

        val previous = lastRawYaw
        if (previous == null) {
            lastRawYaw = rawYaw
            unwrappedYaw = 0.0
            minYaw = 0.0
            maxYaw = 0.0
        } else {
            unwrappedYaw += OrientationMath.unwrapDelta(rawYaw, previous)
            lastRawYaw = rawYaw
            minYaw = min(minYaw, unwrappedYaw)
            maxYaw = max(maxYaw, unwrappedYaw)
        }

        if (
            lastSensorSampleNs == 0L ||
            event.timestamp - lastSensorSampleNs >= SENSOR_SAMPLE_PERIOD_NS
        ) {
            val timeMs = ((event.timestamp - recordingStartNs) / 1_000_000L)
                .coerceAtLeast(0L)

            val sampleLevel = if (waitingForLevelAlignment) {
                -1
            } else {
                currentGuideLevel
            }

            orientationSamples += OrientationSample(
                timeMs = timeMs,
                yawRad = unwrappedYaw,
                level = sampleLevel
            )
            lastSensorSampleNs = event.timestamp

            if (waitingForLevelAlignment) {
                spiralGuideView.updateYaw(unwrappedYaw)
            } else if (!allGuideLevelsComplete) {
                spiralGuideView.markAngle(currentGuideLevel, unwrappedYaw)
                handleGuideProgress()
            } else {
                spiralGuideView.updateYaw(unwrappedYaw)
            }

            updateCoverageText()
        }
    }

    private fun handleGuideProgress() {
        if (!spiralGuideView.isLevelComplete(currentGuideLevel)) return

        completedGuideLevels = max(completedGuideLevels, currentGuideLevel + 1)

        if (currentGuideLevel < targetLevels - 1) {
            waitingForLevelAlignment = true
            val next = currentGuideLevel + 1
            spiralGuideView.showPendingLevel(next)
            nextLevelButton.text = "ALIGNÉ — PASSER AU NIVEAU ${next + 1}"
            nextLevelButton.visibility = View.VISIBLE
            statusText.text =
                "Boucle ${currentGuideLevel + 1} terminée ✓ — montez le téléphone."
        } else {
            allGuideLevelsComplete = true
            nextLevelButton.visibility = View.GONE
            statusText.text =
                "$targetLevels passage(s) couvert(s) ✓ — vous pouvez arrêter."
        }
    }

    private fun updateCoverageText() {
        if (rotationSensor == null) {
            coverageText.text = "Angles non mesurés — estimation par la vidéo"
            return
        }

        if (allGuideLevelsComplete) {
            coverageText.text =
                "Couverture : $targetLevels/$targetLevels passages complets ✓"
            return
        }

        val degrees = spiralGuideView
            .coverageDegrees(currentGuideLevel)
            .coerceAtMost(360.0)

        coverageText.text = if (waitingForLevelAlignment) {
            "Niveau ${currentGuideLevel + 1} terminé • alignez le niveau ${currentGuideLevel + 2}"
        } else {
            "Niveau ${currentGuideLevel + 1}/$targetLevels : ${degrees.toInt()}° / ~330°"
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun currentCoverageDegrees(): Double {
        if (rotationSensor == null) return 0.0
        return (0 until targetLevels)
            .maxOf { spiralGuideView.coverageDegrees(it) }
    }

    override fun onDestroy() {
        sensorManager.unregisterListener(this)
        recording?.close()
        recording = null
        super.onDestroy()
    }
}
