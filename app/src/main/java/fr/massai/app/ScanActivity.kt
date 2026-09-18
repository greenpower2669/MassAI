package fr.massai.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
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

class ScanActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_VIDEO_URI = "fr.massai.app.extra.VIDEO_URI"
    }

    private lateinit var previewView: PreviewView
    private lateinit var recordButton: Button
    private lateinit var statusText: TextView

    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var outputFile: File? = null
    private var cancelling = false

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
        val baseDir = getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: filesDir
        val file = File(baseDir, "MassAI_scan_${System.currentTimeMillis()}.mp4")
        outputFile = file

        val options = FileOutputOptions.Builder(file).build()

        recording = capture.output
            .prepareRecording(this, options)
            .start(ContextCompat.getMainExecutor(this)) { event ->
                when (event) {
                    is VideoRecordEvent.Start -> {
                        recordButton.text = "ARRÊTER ET RECONSTRUIRE"
                        statusText.text = "Enregistrement… tournez lentement sur 360° autour du sujet."
                    }

                    is VideoRecordEvent.Finalize -> {
                        recording = null
                        recordButton.text = "DÉMARRER L'ENREGISTREMENT"

                        if (cancelling) {
                            outputFile?.delete()
                            setResult(Activity.RESULT_CANCELED)
                            finish()
                            return@start
                        }

                        if (!event.hasError()) {
                            val saved = outputFile ?: return@start
                            val result = Intent().putExtra(
                                EXTRA_VIDEO_URI,
                                Uri.fromFile(saved).toString()
                            )
                            setResult(Activity.RESULT_OK, result)
                            finish()
                        } else {
                            statusText.text = "Échec de l'enregistrement (code ${event.error}). Vous pouvez réessayer."
                            outputFile?.delete()
                            recordButton.isEnabled = true
                        }
                    }
                }
            }
    }

    private fun stopRecording() {
        statusText.text = "Finalisation de la vidéo…"
        recordButton.isEnabled = false
        recording?.stop()
    }

    override fun onDestroy() {
        recording?.close()
        recording = null
        super.onDestroy()
    }
}
