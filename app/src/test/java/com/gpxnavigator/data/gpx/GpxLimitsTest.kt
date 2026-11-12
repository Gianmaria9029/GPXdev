package com.gpxnavigator.data.gpx

import java.io.ByteArrayOutputStream
import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Test

class GpxLimitsTest {
    private val point = "<trkpt lat=\"1\" lon=\"2\"/>"
    private fun xml(points: Int = 1, name: String = "Giro") =
        "<gpx><trk><name>$name</name><trkseg>${point.repeat(points)}</trkseg></trk></gpx>"
    private fun parse(xml: String, limits: GpxLimits) = GpxParser(limits).parse(xml.byteInputStream())

    @Test fun pointBudgetIncludesTheBoundaryAndRejectsOneExtraPoint() {
        val limits = GpxLimits(maxPoints = 3)
        assertEquals(3, parse(xml(3), limits).segments.single().points.size)
        assertThrows(GpxParseException::class.java) { parse(xml(4), limits) }
    }

    @Test fun byteBudgetAppliesToParserAndCopyIncludingIgnoredContent() {
        val source = xml().toByteArray()
        val limits = GpxLimits(maxBytes = source.size.toLong())
        assertEquals(1, parse(xml(), limits).segments.single().points.size)
        assertThrows(GpxParseException::class.java) { parse(xml() + " ", limits) }
        val output = ByteArrayOutputStream()
        copyLimitedGpx(source.inputStream(), output, limits) {}
        assertArrayEquals(source, output.toByteArray())
        assertThrows(GpxParseException::class.java) {
            copyLimitedGpx((xml() + " ").byteInputStream(), ByteArrayOutputStream(), limits) {}
        }
    }

    @Test fun nameAndSegmentBudgetsAreEnforcedWithoutFlatteningSegments() {
        assertEquals("12345678", parse(xml(name = "12345678"), GpxLimits(maxTextChars = 8)).name)
        assertThrows(GpxParseException::class.java) { parse(xml(name = "123456789"), GpxLimits(maxTextChars = 8)) }
        val multiple = "<gpx><trk><trkseg>$point</trkseg><trkseg/></trk></gpx>"
        assertEquals(2, parse(multiple, GpxLimits(maxSegments = 2)).segments.size)
        assertThrows(GpxParseException::class.java) { parse(multiple, GpxLimits(maxSegments = 1)) }
    }

    @Test fun ignoredXmlCannotCreateUnlimitedNesting() {
        val nested = "<gpx><extensions>" + "<x>".repeat(64) + "</x>".repeat(64) + "</extensions><trk><trkseg>$point</trkseg></trk></gpx>"
        assertThrows(GpxParseException::class.java) { parse(nested, GpxLimits()) }
    }

    @Test fun parsingAndCopyingPropagateCancellation() {
        assertThrows(CancellationException::class.java) {
            GpxParser().parse(xml().byteInputStream()) { throw CancellationException("Cancelled") }
        }
        assertThrows(CancellationException::class.java) {
            copyLimitedGpx(xml().byteInputStream(), ByteArrayOutputStream(), GpxLimits()) {
                throw CancellationException("Cancelled")
            }
        }
    }
}
