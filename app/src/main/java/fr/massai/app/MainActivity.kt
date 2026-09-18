package fr.massai.app

import android.app.Activity
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var heightInput: EditText
    private lateinit var weightInput: EditText
    private lateinit var statusText: TextView
    private lateinit var modelStatusText: TextView
    private lateinit var pipelineText: TextView
    private lateinit var metricsText: TextView
    private lateinit var analysisButton: Button
    private lateinit var rawButton: Button
    private lateinit var repairedButton: Button
    private lateinit var correctionsButton: Button
    private lateinit var bodyModelView: BodyModelView

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
        rawButton = findViewById(R.id.rawMeshButton)
        repairedButton = findViewById(R.id.repairedMeshButton)
        correctionsButton = findViewById(R.id.holesButton)
        bodyModelView = findViewById(R.id.bodyModelView)

        findViewById<Button>(R.id.captureButton).setOnClickListener {
            scanLauncher.launch(Intent(this, ScanActivity::class.java))
        }

        findViewById<Button>(R.id.importButton).setOnClickListener {
            importVideoLauncher.launch(arrayOf("video/*"))
        }

        analysisButton.setOnClickListener {
            val uri = selectedVideo ?: return@setOnClickListener
            val measurements = measurementsOrNull() ?: return@setOnClickListener
            reconstruct(uri, measurements.first, measurements.second)
        }

        rawButton.setOnClickListener {
            bodyModelView.setMode(BodyModelView.RenderMode.RAW)
        }
        repairedButton.setOnClickListener {
            bodyModelView.setMode(BodyModelView.RenderMode.REPAIRED)
        }
        correctionsButton.setOnClickListener {
            bodyModelView.setMode(BodyModelView.RenderMode.CORRECTIONS)
        }
    }

    private fun setVideo(uri: Uri, sourceLabel: String) {
        selectedVideo = uri
        statusText.text = "$sourceLabel ✓\nPrêt pour la reconstruction 3D."
        modelStatusText.visibility = View.VISIBLE
        modelStatusText.text =
            "Vidéo prête ✓\nRenseignez taille et poids puis lancez la reconstruction."
        bodyModelView.clear()
        setModelButtonsEnabled(false)
        metricsText.text = "Volume : —\nDensité : —\nQualité technique : —"
        analysisButton.isEnabled = true
        pipelineText.text =
            "○ Extraction des vues\n" +
            "○ Segmentation silhouette\n" +
            "○ Visual hull 3D\n" +
            "○ Réparation voxel\n" +
            "○ Mise à l'échelle\n" +
            "○ Volume et densité"
    }

    private fun measurementsOrNull(): Pair<Double, Double>? {
        val heightCm = heightInput.text.toString().replace(',', '.').toDoubleOrNull()
        val weightKg = weightInput.text.toString().replace(',', '.').toDoubleOrNull()

        if (heightCm == null || heightCm !in 100.0..250.0) {
            heightInput.error = "Taille attendue entre 100 et 250 cm"
            heightInput.requestFocus()
            return null
        }
        if (weightKg == null || weightKg !in 20.0..350.0) {
            weightInput.error = "Poids attendu entre 20 et 350 kg"
            weightInput.requestFocus()
            return null
        }
        return Pair(heightCm / 100.0, weightKg)
    }

    private fun reconstruct(uri: Uri, heightM: Double, weightKg: Double) {
        analysisButton.isEnabled = false
        setModelButtonsEnabled(false)
        modelStatusText.visibility = View.VISIBLE
        modelStatusText.text = "Préparation des vues…"
        statusText.text = "Extraction des vues candidates…"
        pipelineText.text =
            "◉ Extraction des vues…\n" +
            "○ Segmentation silhouette\n" +
            "○ Visual hull 3D\n" +
            "○ Réparation voxel\n" +
            "○ Mise à l'échelle\n" +
            "○ Volume et densité"

        Thread {
            try {
                val frames = extractFrames(uri, 20)
                if (frames.size < 8) {
                    throw IllegalStateException(
                        "Seulement ${frames.size} images ont pu être extraites."
                    )
                }

                runOnUiThread {
                    pipelineText.text =
                        "✓ Extraction des vues (${frames.size})\n" +
                        "◉ Segmentation silhouette…\n" +
                        "○ Visual hull 3D\n" +
                        "○ Réparation voxel\n" +
                        "○ Mise à l'échelle\n" +
                        "○ Volume et densité"
                }

                val result = BodyReconstructor().use { reconstructor ->
                    reconstructor.reconstruct(frames, heightM) { stage, current, total ->
                        runOnUiThread {
                            val progressText = if (total > 1) " $current/$total" else ""
                            statusText.text = "$stage$progressText…"

                            when (stage) {
                                "Segmentation silhouette" -> {
                                    pipelineText.text =
                                        "✓ Extraction des vues (${frames.size})\n" +
                                        "◉ Segmentation silhouette$current/$total\n" +
                                        "○ Visual hull 3D\n" +
                                        "○ Réparation voxel\n" +
                                        "○ Mise à l'échelle\n" +
                                        "○ Volume et densité"
                                }

                                "Construction du visual hull" -> {
                                    pipelineText.text =
                                        "✓ Extraction des vues (${frames.size})\n" +
                                        "✓ Segmentation silhouette\n" +
                                        "◉ Visual hull 3D$current/$total\n" +
                                        "○ Réparation voxel\n" +
                                        "○ Mise à l'échelle\n" +
                                        "○ Volume et densité"
                                }

                                "Réparation voxel" -> {
                                    pipelineText.text =
                                        "✓ Extraction des vues (${frames.size})\n" +
                                        "✓ Segmentation silhouette\n" +
                                        "✓ Visual hull 3D\n" +
                                        "◉ Réparation voxel…\n" +
                                        "○ Mise à l'échelle\n" +
                                        "○ Volume et densité"
                                }
                            }
                        }
                    }
                }

                val liters = BodyMath.liters(result.repairedVolumeM3)
                val rawLiters = BodyMath.liters(result.rawVolumeM3)
                val kgPerLiter = BodyMath.densityKgPerLiter(weightKg, result.repairedVolumeM3)
                val kgPerM3 = BodyMath.densityKgPerM3(weightKg, result.repairedVolumeM3)
                val repairPct = result.repairFraction * 100.0

                runOnUiThread {
                    bodyModelView.setReconstruction(result)
                    modelStatusText.visibility = View.GONE
                    setModelButtonsEnabled(true)
                    repairedButton.performClick()

                    pipelineText.text =
                        "✓ Extraction des vues (${frames.size})\n" +
                        "✓ Segmentation silhouette (${result.validViews} valides)\n" +
                        "✓ Visual hull 3D\n" +
                        "✓ Réparation voxel\n" +
                        "✓ Mise à l'échelle par la taille\n" +
                        "✓ Volume et densité"

                    metricsText.text = String.format(
                        Locale.FRANCE,
                        "Volume réparé : %.1f L\n" +
                            "Volume brut voxel : %.1f L\n" +
                            "Densité : %.3f kg/L  •  %.0f kg/m³\n" +
                            "Qualité technique : %d/100\n" +
                            "Corrections : %.2f %% des voxels",
                        liters,
                        rawLiters,
                        kgPerLiter,
                        kgPerM3,
                        result.quality,
                        repairPct
                    )

                    statusText.text =
                        "Reconstruction 3D terminée ✓ — ${result.validViews}/${result.totalViews} vues exploitables."
                    analysisButton.isEnabled = true
                }
            } catch (e: Exception) {
                runOnUiThread {
                    analysisButton.isEnabled = true
                    setModelButtonsEnabled(false)
                    modelStatusText.visibility = View.VISIBLE
                    modelStatusText.text = "Reconstruction impossible\n${e.message ?: "Erreur inconnue"}"
                    statusText.text = "Échec de reconstruction : ${e.message ?: "erreur inconnue"}"
                }
            }
        }.start()
    }

    private fun extractFrames(uri: Uri, wanted: Int): List<File> {
        val outputDir = File(cacheDir, "massai_frames").apply {
            mkdirs()
            listFiles()?.forEach { it.delete() }
        }

        val retriever = MediaMetadataRetriever()
        val files = ArrayList<File>()

        try {
            retriever.setDataSource(this, uri)
            val durationMs = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?: 0L

            if (durationMs <= 0L) return emptyList()

            for (i in 0 until wanted) {
                val fraction = (i + 0.5).toDouble() / wanted.toDouble()
                val timeUs = (durationMs * 1000.0 * fraction).toLong()
                val bitmap = retriever.getFrameAtTime(
                    timeUs,
                    MediaMetadataRetriever.OPTION_CLOSEST
                ) ?: continue

                val file = File(outputDir, "frame_%03d.jpg".format(i + 1))
                FileOutputStream(file).use { stream ->
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 88, stream)
                }
                bitmap.recycle()
                files.add(file)
            }
        } finally {
            retriever.release()
        }

        return files
    }

    private fun setModelButtonsEnabled(enabled: Boolean) {
        rawButton.isEnabled = enabled
        repairedButton.isEnabled = enabled
        correctionsButton.isEnabled = enabled
    }
}
