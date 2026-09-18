package fr.massai.app

enum class CaptureProfile(val label: String, val defaultLevels: Int) {
    SIMPLE("Simple · 1 passage", 1),
    STANDARD("Standard · 2 passages", 2),
    PRECISE("Précis · 3 passages", 3),
    MANUAL("Manuel", 3);

    companion object {
        fun fromName(value: String?): CaptureProfile =
            entries.firstOrNull { it.name == value } ?: STANDARD
    }
}

enum class CameraPreference(val label: String) {
    AUTO("Auto"),
    NORMAL("Caméra normale"),
    ULTRA_WIDE("Ultra grand-angle");

    companion object {
        fun fromName(value: String?): CameraPreference =
            entries.firstOrNull { it.name == value } ?: AUTO
    }
}
