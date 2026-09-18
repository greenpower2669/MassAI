package fr.massai.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    private data class Measurements(
        val heightM: Double,
        val weightKg: Double?
    )

    private lateinit var heightInput: EditText
    private lateinit var weightInput: EditText
    private lateinit var statusText: TextView
    private lateinit var modelStatusText: TextView
    private lateinit var pipelineText: TextView
    private lateinit var metricsText: TextView
    private lateinit var methodText: TextView
    private lateinit var modeHelpText: TextView
    private lateinit var analysisButton: Button
    private lateinit var rawButton: Button
    private lateinit var repairedButton: Button
    private lateinit var correctionsButton: Button
    private lateinit var bodyModelView: BodyModelView
    private lateinit var segmentationModeGroup: RadioGroup

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
        methodText = findViewById(R.id.methodText)
        modeHelpText = findViewById(R.id.modeHelpText)
        analysisButton = findViewById(R.id.analysisButton)
        rawButton = findViewById(R.id.rawMeshButton)
        repairedButton = findViewById(R.id.repairedMeshButton)
        correctionsButton = findViewById(R.id.holesButton)
        bodyModelView = findViewById(R.id.bodyModelView)
        segmentationModeGroup = findViewById(R.id.segmentationModeGroup)

        segmentationModeGroup.setOnCheckedChangeListener { _, _ ->
            updateModeUi()
        }
        updateModeUi()

        findViewById<Button>(R.id.captureButton).setOnClickListener {
            scanLauncher.launch(Intent(this, ScanActivity::class.java))
        }

        findViewById<Button>(R.id.importButton).setOnClickListener {
            importVideoLauncher.launch(arrayOf("video/*"))
        }

        analysisButton.setOnClickListener {
            hideKeyboard()
            val uri = selectedVideo ?: return@setOnClickListener
            val mode = currentMode()
            val measurements = measurementsOrNull(mode) ?: return@setOnClickListener
            reconstruct(
                uri = uri,
                heightM = measurements.heightM,
                weightKg = measurements.weightKg,
                mode = mode
            )
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

    private fun currentMode(): SegmentationMode {
        return if (segmentationModeGroup.checkedRadioButtonId == R.id.modeObjectRadio) {
            SegmentationMode.OBJECT
        } else {
            SegmentationMode.HUMAN
        }
    }

    private fun updateModeUi() {
        when (currentMode()) {
            SegmentationMode.HUMAN -> {
                heightInput.hint = "Taille (cm)"
                weightInput.hint = "Poids (kg)"
                modeHelpText.text =
                    "Humain : segmentation personne, adaptée au futur squelette anatomique."
                analysisButton.text = "RECONSTRUIRE LE CORPS 3D"
                methodText.text =
                    "Mode humain : segmentation personne puis visual hull multi-vues. " +
                        "Les scans filmés utilisent les angles mesurés du téléphone."
            }

            SegmentationMode.OBJECT -> {
                heightInput.hint = "Hauteur objet (cm)"
                weightInput.hint = "Poids objet (kg, facultatif)"
                modeHelpText.text =
                    "Objet / humanoïde : segmentation générique du sujet, sans supposer un humain."
                analysisButton.text = "RECONSTRUIRE LE SUJET 3D"
                methodText.text =
                    "Mode objet test : segmentation générique du premier plan puis visual hull. " +
                        "Le modèle ML peut être téléchargé par Google Play Services au premier usage."
            }
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
            "Vidéo prête ✓\nRenseignez les mesures puis lancez la reconstruction."

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

    private fun measurementsOrNull(mode: SegmentationMode): Measurements? {
        val heightCm = heightInput.text.toString().replace(',', '.').toDoubleOrNull()
        val weightText = weightInput.text.toString().trim()
        val weightKg = weightText.replace(',', '.').toDoubleOrNull()

        val validHeightRange = when (mode) {
            SegmentationMode.HUMAN -> 100.0..250.0
            SegmentationMode.OBJECT -> 5.0..250.0
        }

        if (heightCm == null || heightCm !in validHeightRange) {
            heightInput.error = when (mode) {
                SegmentationMode.HUMAN -> "Taille attendue entre 100 et 250 cm"
                SegmentationMode.OBJECT -> "Hauteur attendue entre 5 et 250 cm"
            }
            heightInput.requestFocus()
            return null
        }

        when (mode) {
            SegmentationMode.HUMAN -> {
                if (weightKg == null || weightKg !in 20.0..350.0) {
                    weightInput.error = "Poids attendu entre 20 et 350 kg"
                    weightInput.requestFocus()
                    return null
                }
            }

            SegmentationMode.OBJECT -> {
                if (weightText.isNotEmpty() && (weightKg == null || weightKg !in 0.01..350.0)) {
                    weightInput.error = "Poids objet facultatif, en kg"
                    weightInput.requestFocus()
                    return null
                }
            }
        }

        return Measurements(
            heightM = heightCm / 100.0,
            weightKg = if (mode == SegmentationMode.OBJECT && weightText.isEmpty()) {
                null
            } else {
                weightKg
            }
        )
    }

    private fun reconstruct(
        uri: Uri,
        heightM: Double,
        weightKg: Double?,
        mode: SegmentationMode
    ) {
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
                    reconstructor.reconstruct(
                        frames = frames,
                        bodyHeightM = heightM,
                        mode = mode
                    ) { stage, current, total ->
                        runOnUiThread {
                            val progressText = if (total > 1) " $current/$total" else ""
                            statusText.text = "$stage$progressText…"

                            when (stage) {
                                "Préparation segmentation objet" -> {
                                    pipelineText.text =
                                        "✓ Extraction des candidates (${candidates.size})\n" +
                                        "✓ Sélection qualité (${frames.size} vues)\n" +
                                        "◉ Préparation du modèle objet…\n" +
                                        "○ Segmentation silhouette\n" +
                                        "○ Visual hull 3D\n" +
                                        "○ Réparation voxel\n" +
                                        "○ Mise à l'échelle\n" +
                                        "○ Volume et densité"
                                }

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

                                "Nettoyage des artefacts" -> {
                                    pipelineText.text =
                                        "✓ Extraction des candidates (${candidates.size})\n" +
                                        "✓ Sélection qualité (${frames.size} vues)\n" +
                                        "✓ Segmentation silhouette\n" +
                                        "✓ Visual hull 3D\n" +
                                        "◉ Nettoyage des îlots…\n" +
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
                                        "✓ Nettoyage des îlots\n" +
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
                val repairPct = result.repairFraction * 100.0
                val angleLabel = if (result.anglesMeasured) "mesurée" else "estimée"

                val densityLine = if (weightKg != null) {
                    val kgPerLiter = BodyMath.densityKgPerLiter(
                        weightKg,
                        result.repairedVolumeM3
                    )
                    val kgPerM3 = BodyMath.densityKgPerM3(
                        weightKg,
                        result.repairedVolumeM3
                    )
                    String.format(
                        Locale.FRANCE,
                        "Densité : %.3f kg/L  •  %.0f kg/m³",
                        kgPerLiter,
                        kgPerM3
                    )
                } else {
                    "Densité : — (poids non renseigné)"
                }

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
                        "✓ Nettoyage des îlots\n" +
                        "✓ Réparation voxel\n" +
                        "✓ Mise à l'échelle par la hauteur\n" +
                        "✓ Volume" +
                        if (weightKg != null) " et densité" else ""

                    metricsText.text = String.format(
                        Locale.FRANCE,
                        "Volume réparé : %.1f L\n" +
                            "Volume brut voxel : %.1f L\n" +
                            "%s\n" +
                            "Qualité technique : %d/100\n" +
                            "Couverture : %.0f° (%s)\n" +
                            "Corrections : %.2f %% des voxels\n" +
                            "Artefacts supprimés : %d voxels\n" +
                            "Composantes : %d → %d",
                        liters,
                        rawLiters,
                        densityLine,
                        result.quality,
                        result.angularCoverageDeg,
                        angleLabel,
                        repairPct,
                        result.removedIslandVoxels,
                        result.componentsBeforeCleanup,
                        result.componentsAfterCleanup
                    )

                    val modeName = if (mode == SegmentationMode.OBJECT) {
                        "objet"
                    } else {
                        "humain"
                    }
                    statusText.text =
                        "Reconstruction $modeName terminée ✓ — " +
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

    private fun hideKeyboard() {
        currentFocus?.let { view ->
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(view.windowToken, 0)
            view.clearFocus()
        }
    }
}
