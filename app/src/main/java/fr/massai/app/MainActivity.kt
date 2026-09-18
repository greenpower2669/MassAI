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
import kotlin.math.roundToInt

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
    private var selectedMetadataFile: File? = null

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
                setVideo(
                    uri = it,
                    sourceLabel = "Vidéo importée",
                    metadataFile = null
                )
            }
        }

    private val scanLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val uriString = result.data?.getStringExtra(ScanActivity.EXTRA_VIDEO_URI)
                val metadataPath = result.data?.getStringExtra(ScanActivity.EXTRA_METADATA_PATH)

                if (!uriString.isNullOrBlank()) {
                    setVideo(
                        uri = Uri.parse(uriString),
                        sourceLabel = "Scan filmé",
                        metadataFile = metadataPath?.let(::File)
                    )
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

    private fun setVideo(
        uri: Uri,
        sourceLabel: String,
        metadataFile: File?
    ) {
        selectedVideo = uri
        selectedMetadataFile = metadataFile?.takeIf { it.exists() }

        val metadata = selectedMetadataFile?.let { ScanMetadata.readFrom(it) }
        val angleInfo = when {
            metadata?.sensorAvailable == true && metadata.samples.size >= 8 ->
                "Angles téléphone : ${metadata.coverageDegrees.roundToInt()}° mesurés"
            sourceLabel.startsWith("Vidéo importée") ->
                "Angles : estimation uniforme (vidéo importée)"
            else ->
                "Angles : mode de secours"
        }

        statusText.text = "$sourceLabel ✓\n$angleInfo"
        modelStatusText.visibility = View.VISIBLE
        modelStatusText.text =
            "Vidéo prête ✓\nRenseignez taille et poids puis lancez la reconstruction."

        bodyModelView.clear()
        setModelButtonsEnabled(false)
        metricsText.text =
            "Volume : —\nDensité : —\nQualité technique : —\nCouverture : —"

        analysisButton.isEnabled = true
        pipelineText.text =
            "○ Extraction des candidates\n" +
            "○ Netteté / redondance / angles\n" +
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
        modelStatusText.text = "Préparation et contrôle des vues…"
        statusText.text = "Extraction des images candidates…"
        pipelineText.text =
            "◉ Extraction des candidates…\n" +
            "○ Netteté / redondance / angles\n" +
            "○ Segmentation silhouette\n" +
            "○ Visual hull 3D\n" +
            "○ Réparation voxel\n" +
            "○ Mise à l'échelle\n" +
            "○ Volume et densité"

        Thread {
            try {
                val metadata = selectedMetadataFile?.let { ScanMetadata.readFrom(it) }

                if (
                    metadata?.sensorAvailable == true &&
                    metadata.samples.size >= 8 &&
                    metadata.coverageDegrees < 300.0
                ) {
                    throw IllegalStateException(
                        "Tour incomplet : ${metadata.coverageDegrees.roundToInt()}° mesurés. " +
                            "Refaites le scan en dépassant 300°."
                    )
                }

                val candidates = extractCandidates(uri, 48)
                if (candidates.size < 12) {
                    throw IllegalStateException(
                        "Seulement ${candidates.size} images candidates ont pu être extraites."
                    )
                }

                runOnUiThread {
                    pipelineText.text =
                        "✓ Extraction des candidates (${candidates.size})\n" +
                        "◉ Netteté / redondance / angles…\n" +
                        "○ Segmentation silhouette\n" +
                        "○ Visual hull 3D\n" +
                        "○ Réparation voxel\n" +
                        "○ Mise à l'échelle\n" +
                        "○ Volume et densité"
                    statusText.text = "Sélection des meilleures vues…"
                }

                var frames = FrameSelector.select(
                    candidates = candidates,
                    wanted = 20,
                    metadata = metadata
                )

                if (frames.none { it.measuredAngle } && frames.isNotEmpty()) {
                    val n = frames.size
                    frames = frames.mapIndexed { index, frame ->
                        frame.copy(
                            angleRad = 2.0 * Math.PI * index.toDouble() / n.toDouble()
                        )
                    }
                }

                if (frames.size < 8) {
                    throw IllegalStateException(
                        "Seulement ${frames.size} vues ont passé le contrôle qualité."
                    )
                }

                runOnUiThread {
                    pipelineText.text =
                        "✓ Extraction des candidates (${candidates.size})\n" +
                        "✓ Sélection qualité (${frames.size} vues)\n" +
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
                                        "✓ Extraction des candidates (${candidates.size})\n" +
                                        "✓ Sélection qualité (${frames.size} vues)\n" +
                                        "◉ Segmentation silhouette $current/$total\n" +
                                        "○ Visual hull 3D\n" +
                                        "○ Réparation voxel\n" +
                                        "○ Mise à l'échelle\n" +
                                        "○ Volume et densité"
                                }

                                "Construction du visual hull" -> {
                                    pipelineText.text =
                                        "✓ Extraction des candidates (${candidates.size})\n" +
                                        "✓ Sélection qualité (${frames.size} vues)\n" +
                                        "✓ Segmentation silhouette\n" +
                                        "◉ Visual hull 3D $current/$total\n" +
                                        "○ Réparation voxel\n" +
                                        "○ Mise à l'échelle\n" +
                                        "○ Volume et densité"
                                }

                                "Réparation voxel" -> {
                                    pipelineText.text =
                                        "✓ Extraction des candidates (${candidates.size})\n" +
                                        "✓ Sélection qualité (${frames.size} vues)\n" +
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
                val kgPerLiter = BodyMath.densityKgPerLiter(
                    weightKg,
                    result.repairedVolumeM3
                )
                val kgPerM3 = BodyMath.densityKgPerM3(
                    weightKg,
                    result.repairedVolumeM3
                )
                val repairPct = result.repairFraction * 100.0
                val angleLabel = if (result.anglesMeasured) "mesurée" else "estimée"

                runOnUiThread {
                    bodyModelView.setReconstruction(result)
                    modelStatusText.visibility = View.GONE
                    setModelButtonsEnabled(true)
                    repairedButton.performClick()

                    pipelineText.text =
                        "✓ Extraction des candidates (${candidates.size})\n" +
                        "✓ Sélection qualité (${frames.size} vues)\n" +
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
                            "Couverture : %.0f° (%s)\n" +
                            "Corrections : %.2f %% des voxels",
                        liters,
                        rawLiters,
                        kgPerLiter,
                        kgPerM3,
                        result.quality,
                        result.angularCoverageDeg,
                        angleLabel,
                        repairPct
                    )

                    statusText.text =
                        "Reconstruction terminée ✓ — " +
                            "${result.validViews}/${result.totalViews} vues exploitables."
                    analysisButton.isEnabled = true
                }
            } catch (e: Exception) {
                runOnUiThread {
                    analysisButton.isEnabled = true
                    setModelButtonsEnabled(false)
                    modelStatusText.visibility = View.VISIBLE
                    modelStatusText.text =
                        "Reconstruction impossible\n${e.message ?: "Erreur inconnue"}"
                    statusText.text =
                        "Échec de reconstruction : ${e.message ?: "erreur inconnue"}"
                }
            }
        }.start()
    }

    private fun extractCandidates(
        uri: Uri,
        wanted: Int
    ): List<CandidateFrame> {
        val outputDir = File(cacheDir, "massai_frames").apply {
            mkdirs()
            listFiles()?.forEach { it.delete() }
        }

        val retriever = MediaMetadataRetriever()
        val candidates = ArrayList<CandidateFrame>()

        try {
            retriever.setDataSource(this, uri)
            val durationMs = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?: 0L

            if (durationMs <= 0L) return emptyList()

            for (i in 0 until wanted) {
                val fraction = (i + 0.5).toDouble() / wanted.toDouble()
                val timeMs = (durationMs * fraction).toLong()
                val bitmap = retriever.getFrameAtTime(
                    timeMs * 1000L,
                    MediaMetadataRetriever.OPTION_CLOSEST
                ) ?: continue

                val blurScore = FrameQuality.blurScore(bitmap)
                val signature = FrameQuality.signature(bitmap)

                val file = File(outputDir, "candidate_%03d.jpg".format(i + 1))
                FileOutputStream(file).use { stream ->
                    bitmap.compress(
                        android.graphics.Bitmap.CompressFormat.JPEG,
                        90,
                        stream
                    )
                }
                bitmap.recycle()

                candidates += CandidateFrame(
                    file = file,
                    timeMs = timeMs,
                    blurScore = blurScore,
                    signature = signature
                )
            }
        } finally {
            retriever.release()
        }

        return candidates
    }

    private fun setModelButtonsEnabled(enabled: Boolean) {
        rawButton.isEnabled = enabled
        repairedButton.isEnabled = enabled
        correctionsButton.isEnabled = enabled
    }
}
