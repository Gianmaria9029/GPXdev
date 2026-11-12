package com.gpxnavigator.navigation

import com.gpxnavigator.domain.model.MAX_RECORDING_NAME_LENGTH
import com.gpxnavigator.domain.model.RecordingNameError
import com.gpxnavigator.domain.model.normalizedRecordingName
import com.gpxnavigator.domain.model.recordingNameError
import org.junit.Assert.*
import org.junit.Test

class RecordingNameTest {
    @Test fun trimsNamesAndAvoidsRepeatedFileExtensions() {
        assertEquals("Giro del lago", normalizedRecordingName("  Giro del lago.GPX "))
        assertEquals("Giro", normalizedRecordingName("Giro.gpx.GPX"))
        assertEquals("Giro & caffè", normalizedRecordingName("Giro & caffè"))
        assertNull(recordingNameError("Giro & caffè"))
    }

    @Test fun emptyNamesKeepAutomaticNamingAvailable() {
        for (value in listOf(null, "", "  ", ".gpx")) {
            assertNull(normalizedRecordingName(value))
            assertNull(recordingNameError(value))
        }
    }

    @Test fun rejectsInvalidPortableFileNamesAndBrokenXmlCharacters() {
        for (symbol in "\\/:*?\"<>|") assertEquals(RecordingNameError.INVALID_CHARACTERS, recordingNameError("Giro${symbol}lago"))
        for (value in listOf("Giro\u0001lago", "Giro\uD800lago", "Giro\uFFFFlago"))
            assertEquals(RecordingNameError.INVALID_CHARACTERS, recordingNameError(value))
        assertEquals(RecordingNameError.TOO_LONG, recordingNameError("A".repeat(MAX_RECORDING_NAME_LENGTH + 1)))
        assertNull(recordingNameError("A".repeat(MAX_RECORDING_NAME_LENGTH)))
    }
}
