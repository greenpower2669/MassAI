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
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.SystemClock
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
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
        private const val SENSOR_SAMPLE_PERIOD_NS = 50_000_000L
    }

    private lateinit var previewView: PreviewView
    private lateinit var recordButton: Button
    private lateinit var statusText: TextView
    private lateinit var coverageText: TextView

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

        previewView = findViewById(R.id.previewView)
        recordButton = findViewById(R.id.recordButton)
        statusText = findViewById(R.id.recordStatusText)
        coverageText = findViewById(R.id.coverageText)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        coverageText.text = if (rotationSensor != null) {
            "Couverture angulaire : prête"
        } else {
            "Capteur d'orientation indisponible — mode de secours"
        }

        recordButton.setOnClickListener {
            if (recording == null) startRecording() else stopRecording()
        }

        findViewById<Button>(R.id.cancelScanButton).setOnClickListener {
            if (recording != null) {
                cancelling = true
                statusText.text = "Annulation…"
                recordButton.isEnabled = false
                recording?.stop()
            } else {
                setResult(Activity.RESULT_CANCELED)
                finish()
            }
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
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

    private fun startCamera() {
        statusText.text = "Initialisation de la caméra…"
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()

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
                cameraProvider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    videoCapture
                )

                recordButton.isEnabled = true
                statusText.text = "Caméra prête — corps entier visible, faites un tour complet."
            } catch (e: Exception) {
                recordButton.isEnabled = false
                statusText.text = "Impossible d'ouvrir la caméra : ${e.message ?: "erreur inconnue"}"
            }
        }, ContextCompat.getMainExecutor(this))
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
                        statusText.text = "Enregistrement… tournez lentement autour du sujet."
                        coverageText.text = if (rotationSensor != null) {
                            "Couverture angulaire : 0° / 360°"
                        } else {
                            "Angles non mesurés — estimation par la vidéo"
                        }
                    }

                    is VideoRecordEvent.Finalize -> {
                        recording = null
                        recordButton.text = "DÉMARRER L'ENREGISTREMENT"
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
                                sensorAvailable = rotationSensor != null
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
        statusText.text = if (rotationSensor != null && coverage < 300.0) {
            "Finalisation… couverture ${coverage.toInt()}°, le contrôle qualité décidera."
        } else {
            "Finalisation de la vidéo…"
        }
        recordButton.isEnabled = false
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

        if (lastSensorSampleNs == 0L ||
            event.timestamp - lastSensorSampleNs >= SENSOR_SAMPLE_PERIOD_NS
        ) {
            val timeMs = ((event.timestamp - recordingStartNs) / 1_000_000L)
                .coerceAtLeast(0L)
            orientationSamples += OrientationSample(timeMs, unwrappedYaw)
            lastSensorSampleNs = event.timestamp

            val coverage = currentCoverageDegrees().coerceAtMost(360.0)
            coverageText.text = "Couverture angulaire : ${coverage.toInt()}° / 360°"
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun currentCoverageDegrees(): Double {
        return Math.toDegrees(maxYaw - minYaw).coerceAtLeast(0.0)
    }

    override fun onDestroy() {
        sensorManager.unregisterListener(this)
        recording?.close()
        recording = null
        super.onDestroy()
    }
}
