package com.gpxnavigator.domain.model

const val MAX_RECORDING_NAME_LENGTH = 80

enum class RecordingNameError { TOO_LONG, INVALID_CHARACTERS }

fun normalizedRecordingName(value: String?): String? {
    var name = value?.trim() ?: return null
    while (name.endsWith(".gpx", ignoreCase = true)) name = name.dropLast(4).trim()
    return name.takeIf { it.isNotEmpty() }
}

fun recordingNameError(value: String?): RecordingNameError? {
    val name = normalizedRecordingName(value) ?: return null
    if (name.length > MAX_RECORDING_NAME_LENGTH) return RecordingNameError.TOO_LONG
    // The same name is offered to Android's file picker and may be copied onto a PC.
    if (name.any { it in "\\/:*?\"<>|" || it.isISOControl() } ||
        name.codePoints().anyMatch { it in 0xD800..0xDFFF || it == 0xFFFE || it == 0xFFFF })
        return RecordingNameError.INVALID_CHARACTERS
    return null
}
