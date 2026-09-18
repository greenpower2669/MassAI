package fr.massai.app

import android.app.Activity
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var heightInput: EditText
    private lateinit var weightInput: EditText
    private lateinit var statusText: TextView
    private lateinit var modelStatusText: TextView
    private lateinit var pipelineText: TextView
    private lateinit var metricsText: TextView
    private lateinit var analysisButton: Button

    private var selectedVideo: Uri? = null

    private val importVideoLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let {
                try {
                    contentResolver.takePersistableUriPermission(
                        it,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: SecurityException) {
                    // Certains fournisseurs ne proposent pas de permission persistante.
                }
                setVideo(it, "Vidéo importée")
            }
        }

    private val scanLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val uriString = result.data?.getStringExtra(ScanActivity.EXTRA_VIDEO_URI)
                if (!uriString.isNullOrBlank()) {
                    setVideo(Uri.parse(uriString), "Scan filmé")
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        heightInput = findViewById(R.id.heightInput)
        weightInput = findViewById(R.id.weightInput)
        statusText = findViewById(R.id.statusText)
        modelStatusText = findViewById(R.id.modelStatusText)
        pipelineText = findViewById(R.id.pipelineText)
        metricsText = findViewById(R.id.metricsText)
        analysisButton = findViewById(R.id.analysisButton)

        findViewById<Button>(R.id.captureButton).setOnClickListener {
            scanLauncher.launch(Intent(this, ScanActivity::class.java))
        }

        findViewById<Button>(R.id.importButton).setOnClickListener {
            importVideoLauncher.launch(arrayOf("video/*"))
        }

        analysisButton.setOnClickListener {
            val uri = selectedVideo ?: return@setOnClickListener
            if (!measurementsAreValid()) return@setOnClickListener
            extractCandidateFrames(uri)
        }
    }

    private fun setVideo(uri: Uri, sourceLabel: String) {
        selectedVideo = uri
        statusText.text = "$sourceLabel ✓\nPrête pour l'extraction des images."
        modelStatusText.text = "Aucun maillage 3D généré\nLancez l'analyse du scan."
        analysisButton.isEnabled = true
        pipelineText.text =
            "○ Extraction des images\n" +
            "○ Sélection qualité\n" +
            "○ Segmentation silhouette\n" +
            "○ Reconstruction 3D\n" +
            "○ Sol et pieds\n" +
            "○ Réparation / watertight\n" +
            "○ Mise à l'échelle\n" +
            "○ Volume et densité"
    }

    private fun measurementsAreValid(): Boolean {
        val height = heightInput.text.toString().replace(',', '.').toDoubleOrNull()
        val weight = weightInput.text.toString().replace(',', '.').toDoubleOrNull()

        if (height == null || height !in 100.0..250.0) {
            heightInput.error = "Taille attendue entre 100 et 250 cm"
            heightInput.requestFocus()
            return false
        }
        if (weight == null || weight !in 20.0..350.0) {
            weightInput.error = "Poids attendu entre 20 et 350 kg"
            weightInput.requestFocus()
            return false
        }
        return true
    }

    private fun extractCandidateFrames(uri: Uri) {
        analysisButton.isEnabled = false
        statusText.text = "Extraction des images en cours…"
        modelStatusText.text = "Préparation des vues pour la reconstruction…"
        pipelineText.text =
            "◉ Extraction des images…\n" +
            "○ Sélection qualité\n" +
            "○ Segmentation silhouette\n" +
            "○ Reconstruction 3D\n" +
            "○ Sol et pieds\n" +
            "○ Réparation / watertight\n" +
            "○ Mise à l'échelle\n" +
            "○ Volume et densité"

        Thread {
            try {
                val count = extractFrames(uri, 18)
                runOnUiThread {
                    analysisButton.isEnabled = true
                    if (count > 0) {
                        statusText.text = "$count images candidates extraites ✓"
                        pipelineText.text =
                            "✓ Extraction des images ($count candidates)\n" +
                            "○ Sélection qualité / flou / redondance\n" +
                            "○ Segmentation silhouette\n" +
                            "○ Reconstruction 3D\n" +
                            "○ Sol et pieds\n" +
                            "○ Réparation / watertight\n" +
                            "○ Mise à l'échelle\n" +
                            "○ Volume et densité"
                        modelStatusText.text =
                            "Images prêtes ✓\n" +
                            "Le moteur de reconstruction 3D est la prochaine brique à brancher."
                    } else {
                        statusText.text = "Aucune image exploitable n'a pu être extraite."
                        modelStatusText.text = "Reconstruction impossible sans images."
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    analysisButton.isEnabled = true
                    statusText.text = "Échec de lecture vidéo : ${e.message ?: "erreur inconnue"}"
                    modelStatusText.text = "Aucun maillage 3D généré."
                }
            }
        }.start()
    }

    private fun extractFrames(uri: Uri, wanted: Int): Int {
        val outputDir = File(cacheDir, "massai_frames").apply {
            mkdirs()
            listFiles()?.forEach { it.delete() }
        }

        val retriever = MediaMetadataRetriever()
        var saved = 0
        try {
            retriever.setDataSource(this, uri)
            val durationMs = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?: 0L

            if (durationMs <= 0L) return 0

            for (i in 0 until wanted) {
                val fraction = (i + 1).toDouble() / (wanted + 1).toDouble()
                val timeUs = (durationMs * 1000.0 * fraction).toLong()
                val bitmap = retriever.getFrameAtTime(
                    timeUs,
                    MediaMetadataRetriever.OPTION_CLOSEST
                ) ?: continue

                val file = File(outputDir, "frame_%03d.jpg".format(saved + 1))
                FileOutputStream(file).use { stream ->
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 88, stream)
                }
                bitmap.recycle()
                saved++
            }
        } finally {
            retriever.release()
        }
        return saved
    }
}
