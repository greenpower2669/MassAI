package fr.massai.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.Spinner
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
    private lateinit var manualLevelsInput: EditText
    private lateinit var captureProfileSpinner: Spinner
    private lateinit var cameraPreferenceSpinner: Spinner
    private lateinit var captureHelpText: TextView
    private lateinit var statusText: TextView
    private lateinit var modelStatusText: TextView
    private lateinit var pipelineText: TextView
    private lateinit var metricsText: TextView
    private lateinit var methodText: TextView
    private lateinit var modeHelpText: TextView
    private lateinit var turboReconstructionSwitch: android.widget.Switch
    private lateinit var analysisButton: Button
    private lateinit var rawButton: Button
    private lateinit var repairedButton: Button
    private lateinit var meshButton: Button
    private lateinit var wireButton: Button
    private lateinit var correctionsButton: Button
    private lateinit var saveModelButton: Button
    private lateinit var exportObjButton: Button
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

    private val saveModelLauncher =
        registerForActivityResult(
            ActivityResultContracts.CreateDocument("application/octet-stream")
        ) { uri ->
            val model = bodyModelView.getReconstruction()
            if (uri != null && model != null) {
                Thread {
                    try {
                        contentResolver.openOutputStream(uri, "w")?.use { stream ->
                            ModelArchive.write(model, stream)
                        } ?: error("Impossible d'ouvrir le fichier de destination.")
                        runOnUiThread {
                            statusText.text = "Modèle .massai sauvegardé ✓"
                        }
                    } catch (e: Exception) {
                        runOnUiThread {
                            statusText.text =
                                "Échec de sauvegarde : ${e.message ?: "erreur inconnue"}"
                        }
                    }
                }.start()
            }
        }

    private val exportObjLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
            val model = bodyModelView.getReconstruction()
            if (uri != null && model != null) {
                Thread {
                    try {
                        contentResolver.openOutputStream(uri, "w")?.use { stream ->
                            ModelArchive.writeObj(model.repairedMesh, stream)
                        } ?: error("Impossible d'ouvrir le fichier OBJ.")
                        runOnUiThread {
                            statusText.text = "Mesh OBJ exporté ✓"
                        }
                    } catch (e: Exception) {
                        runOnUiThread {
                            statusText.text =
                                "Échec export OBJ : ${e.message ?: "erreur inconnue"}"
                        }
                    }
                }.start()
            }
        }

    private val loadModelLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                Thread {
                    try {
                        val model = contentResolver.openInputStream(uri)?.use {
                            ModelArchive.read(it)
                        } ?: error("Impossible d'ouvrir le modèle.")

                        runOnUiThread {
                            showLoadedModel(model)
                        }
                    } catch (e: Exception) {
                        runOnUiThread {
                            statusText.text =
                                "Impossible d'ouvrir le modèle : ${e.message ?: "erreur inconnue"}"
                        }
                    }
                }.start()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        heightInput = findViewById(R.id.heightInput)
        weightInput = findViewById(R.id.weightInput)
        manualLevelsInput = findViewById(R.id.manualLevelsInput)
        captureProfileSpinner = findViewById(R.id.captureProfileSpinner)
        cameraPreferenceSpinner = findViewById(R.id.cameraPreferenceSpinner)
        captureHelpText = findViewById(R.id.captureHelpText)
        statusText = findViewById(R.id.statusText)
        modelStatusText = findViewById(R.id.modelStatusText)
        pipelineText = findViewById(R.id.pipelineText)
        metricsText = findViewById(R.id.metricsText)
        methodText = findViewById(R.id.methodText)
        modeHelpText = findViewById(R.id.modeHelpText)
        turboReconstructionSwitch = findViewById(R.id.turboReconstructionSwitch)
        analysisButton = findViewById(R.id.analysisButton)
        rawButton = findViewById(R.id.rawMeshButton)
        repairedButton = findViewById(R.id.repairedMeshButton)
        meshButton = findViewById(R.id.meshButton)
        wireButton = findViewById(R.id.wireButton)
        correctionsButton = findViewById(R.id.holesButton)
        saveModelButton = findViewById(R.id.saveModelButton)
        exportObjButton = findViewById(R.id.exportObjButton)
        bodyModelView = findViewById(R.id.bodyModelView)
        segmentationModeGroup = findViewById(R.id.segmentationModeGroup)

        setupCaptureOptions()

        segmentationModeGroup.setOnCheckedChangeListener { _, _ ->
            updateModeUi()
        }
        updateModeUi()

        findViewById<Button>(R.id.captureButton).setOnClickListener {
            hideKeyboard()
            val levels = selectedTargetLevels() ?: return@setOnClickListener
            val cameraPreference = CameraPreference.entries[
                cameraPreferenceSpinner.selectedItemPosition
            ]

            scanLauncher.launch(
                Intent(this, ScanActivity::class.java)
                    .putExtra(ScanActivity.EXTRA_TARGET_LEVELS, levels)
                    .putExtra(
                        ScanActivity.EXTRA_CAMERA_PREFERENCE,
                        cameraPreference.name
                    )
            )
        }

        findViewById<Button>(R.id.importButton).setOnClickListener {
            importVideoLauncher.launch(arrayOf("video/*"))
        }

        findViewById<Button>(R.id.loadModelButton).setOnClickListener {
            loadModelLauncher.launch(arrayOf("application/octet-stream", "*/*"))
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
        meshButton.setOnClickListener {
            bodyModelView.setMode(BodyModelView.RenderMode.MESH)
        }
        wireButton.setOnClickListener {
            bodyModelView.setMode(BodyModelView.RenderMode.WIREFRAME)
        }
        correctionsButton.setOnClickListener {
            bodyModelView.setMode(BodyModelView.RenderMode.CORRECTIONS)
        }
        saveModelButton.setOnClickListener {
            if (bodyModelView.getReconstruction() != null) {
                saveModelLauncher.launch("MassAI_modele.massai")
            }
        }
        exportObjButton.setOnClickListener {
            if (bodyModelView.getReconstruction() != null) {
                exportObjLauncher.launch("MassAI_mesh.obj")
            }
        }
    }

    private fun setupCaptureOptions() {
        captureProfileSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            CaptureProfile.entries.map { it.label }
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        captureProfileSpinner.setSelection(CaptureProfile.STANDARD.ordinal)

        cameraPreferenceSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            CameraPreference.entries.map { it.label }
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        cameraPreferenceSpinner.setSelection(CameraPreference.AUTO.ordinal)

        captureProfileSpinner.onItemSelectedListener =
            object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: android.widget.AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    val profile = CaptureProfile.entries[position]
                    manualLevelsInput.visibility =
                        if (profile == CaptureProfile.MANUAL) View.VISIBLE else View.GONE
                    updateCaptureHelp()
                }

                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
            }

        cameraPreferenceSpinner.onItemSelectedListener =
            object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: android.widget.AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    updateCaptureHelp()
                }

                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
            }

        updateCaptureHelp()
    }

    private fun updateCaptureHelp() {
        val profile = CaptureProfile.entries[
            captureProfileSpinner.selectedItemPosition.coerceAtLeast(0)
        ]
        val camera = CameraPreference.entries[
            cameraPreferenceSpinner.selectedItemPosition.coerceAtLeast(0)
        ]

        captureHelpText.text =
            "${profile.label} • ${camera.label}. " +
                "L'ultra grand-angle est utilisé seulement s'il est exposé par CameraX."
    }

    private fun selectedTargetLevels(): Int? {
        val profile = CaptureProfile.entries[
            captureProfileSpinner.selectedItemPosition.coerceAtLeast(0)
        ]

        if (profile != CaptureProfile.MANUAL) return profile.defaultLevels

        val levels = manualLevelsInput.text.toString().toIntOrNull()
        if (levels == null || levels !in 1..5) {
            manualLevelsInput.error = "Choisissez entre 1 et 5 passages"
            manualLevelsInput.requestFocus()
            return null
        }
        return levels
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
                    "Mode humain : silhouettes multi-vues, visual hull, nettoyage puis mesh triangulé."
            }

            SegmentationMode.OBJECT -> {
                heightInput.hint = "Hauteur objet (cm)"
                weightInput.hint = "Poids objet (kg, facultatif)"
                modeHelpText.text =
                    "Objet / humanoïde : segmentation générique du sujet, sans supposer un humain."
                analysisButton.text = "RECONSTRUIRE LE SUJET 3D"
                methodText.text =
                    "Mode objet : segmentation générique puis visual hull et mesh triangulé."
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
            metadata?.sensorAvailable == true && metadata.samples.size >= 8 -> {
                val levelInfo = if (metadata.targetLevels > 1) {
                    " • guide ${metadata.completedLevels}/${metadata.targetLevels} niveaux"
                } else {
                    ""
                }
                val cameraInfo = metadata.cameraLabel
                    ?.takeIf { it.isNotBlank() }
                    ?.let { " • $it" }
                    ?: ""
                "Angles téléphone : ${metadata.coverageDegrees.roundToInt()}° mesurés$levelInfo$cameraInfo"
            }
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
            "○ Nettoyage voxel\n" +
            "○ Mesh triangulé\n" +
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
        val turboEnabled = turboReconstructionSwitch.isChecked
        turboReconstructionSwitch.isEnabled = false
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
            "○ Nettoyage voxel\n" +
            "○ Mesh triangulé\n" +
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

                val multiLevelScan =
                    metadata?.sensorAvailable == true && metadata.targetLevels > 1
                val turboLevels = if (multiLevelScan) metadata!!.targetLevels.coerceIn(1, 5) else 1
                val candidateTarget = if (turboEnabled && multiLevelScan) {
                    turboLevels * 60
                } else when {
                    metadata?.targetLevels != null && metadata.targetLevels >= 4 -> 96
                    multiLevelScan -> 72
                    else -> 48
                }
                val wantedViews = if (turboEnabled && multiLevelScan) {
                    turboLevels * 25
                } else when {
                    metadata?.targetLevels != null && metadata.targetLevels >= 4 -> 28
                    multiLevelScan -> 24
                    else -> 20
                }

                val candidates = extractCandidates(uri, candidateTarget)
                if (candidates.size < 12) {
                    throw IllegalStateException(
                        "Seulement ${candidates.size} images candidates ont pu être extraites."
                    )
                }

                runOnUiThread {
                    statusText.text = "Sélection des meilleures vues…"
                }

                var frames = FrameSelector.select(
                    candidates = candidates,
                    wanted = wantedViews,
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

                val result = BodyReconstructor().use { reconstructor ->
                    reconstructor.reconstruct(
                        frames = frames,
                        bodyHeightM = heightM,
                        mode = mode,
                        turbo = turboEnabled && multiLevelScan
                    ) { stage, current, total ->
                        runOnUiThread {
                            val progressText = if (total > 1) " $current/$total" else ""
                            statusText.text = "$stage$progressText…"

                            if (stage == "Génération du mesh") {
                                pipelineText.text =
                                    "✓ Extraction et sélection des vues\n" +
                                    "✓ Segmentation silhouette\n" +
                                    "✓ Visual hull 3D\n" +
                                    "✓ Nettoyage voxel\n" +
                                    "◉ Génération du mesh…\n" +
                                    "○ Volume et densité"
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
                    meshButton.performClick()

                    pipelineText.text =
                        "✓ Extraction candidates (${candidates.size})\n" +
                        "✓ Sélection qualité (${frames.size} vues)\n" +
                        "✓ Segmentation (${result.validViews} valides)\n" +
                        "✓ Visual hull + nettoyage\n" +
                        "✓ Mesh triangulé (${result.repairedMesh.triangleCount} triangles)\n" +
                        "✓ Volume" +
                        if (weightKg != null) " et densité" else ""

                    metricsText.text = String.format(
                        Locale.FRANCE,
                        "Volume réparé : %.1f L\n" +
                            "Volume brut voxel : %.1f L\n" +
                            "%s\n" +
                            "Mesh : %d triangles\n" +
                            "Qualité technique : %d/100\n" +
                            "Couverture : %.0f° (%s)\n" +
                            "Corrections : %.2f %%\n" +
                            "Artefacts supprimés : %d voxels\n" +
                            "Composantes : %d → %d\n" +
                            "Niveaux exploités : %d",
                        liters,
                        rawLiters,
                        densityLine,
                        result.repairedMesh.triangleCount,
                        result.quality,
                        result.angularCoverageDeg,
                        angleLabel,
                        repairPct,
                        result.removedIslandVoxels,
                        result.componentsBeforeCleanup,
                        result.componentsAfterCleanup,
                        frames.map { it.scanLevel }.distinct().size
                    )

                    val modeName = if (mode == SegmentationMode.OBJECT) {
                        "objet"
                    } else {
                        "humain"
                    }
                    statusText.text =
                        "Reconstruction $modeName terminée ✓ — " +
                            "${result.validViews}/${result.totalViews} vues."
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

    private fun showLoadedModel(model: BodyReconstruction) {
        selectedVideo = null
        selectedMetadataFile = null
        analysisButton.isEnabled = false

        bodyModelView.setReconstruction(model)
        modelStatusText.visibility = View.GONE
        setModelButtonsEnabled(true)
        meshButton.performClick()

        metricsText.text = String.format(
            Locale.FRANCE,
            "Modèle sauvegardé chargé ✓\n" +
                "Volume réparé : %.1f L\n" +
                "Volume brut : %.1f L\n" +
                "Mesh : %d triangles\n" +
                "Qualité technique : %d/100\n" +
                "Couverture : %.0f°",
            BodyMath.liters(model.repairedVolumeM3),
            BodyMath.liters(model.rawVolumeM3),
            model.repairedMesh.triangleCount,
            model.quality,
            model.angularCoverageDeg
        )

        pipelineText.text =
            "✓ Modèle .massai chargé\n" +
            "✓ Surface et mesh disponibles\n" +
            "✓ Export OBJ disponible"

        statusText.text = "Modèle MassAI chargé ✓"
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
        meshButton.isEnabled = enabled
        wireButton.isEnabled = enabled
        correctionsButton.isEnabled = enabled
        saveModelButton.isEnabled = enabled
        exportObjButton.isEnabled = enabled
    }

    private fun hideKeyboard() {
        currentFocus?.let { view ->
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(view.windowToken, 0)
            view.clearFocus()
        }
    }
}
