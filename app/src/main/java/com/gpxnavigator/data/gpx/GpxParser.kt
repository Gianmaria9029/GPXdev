package com.gpxnavigator.data.gpx

import com.gpxnavigator.domain.model.Track
import com.gpxnavigator.domain.model.TrackPoint
import com.gpxnavigator.domain.model.TrackSegment
import java.io.IOException
import java.io.InputStream
import java.time.Instant
import java.time.format.DateTimeParseException
import javax.xml.parsers.ParserConfigurationException
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import org.xml.sax.ext.DefaultHandler2

class GpxParseException(message: String, cause: Throwable? = null) : IOException(message, cause)

class GpxParser(private val limits: GpxLimits = GpxLimits()) {
    fun parse(input: InputStream, checkActive: () -> Unit = {}): Track {
        val handler = TrackHandler(limits, checkActive)
        try {
            val factory = SAXParserFactory.newInstance().apply { isNamespaceAware = true }
            val reader = factory.newSAXParser().xmlReader
            // Android's Expat and the JVM SAX parser both expose these standard features.
            reader.setFeature("http://xml.org/sax/features/external-general-entities", false)
            reader.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            reader.setProperty("http://xml.org/sax/properties/lexical-handler", handler)
            reader.contentHandler = handler
            reader.errorHandler = handler
            reader.parse(InputSource(LimitedGpxInputStream(input, limits.maxBytes, checkActive)))
        } catch (exception: ParserConfigurationException) {
            throw GpxParseException("Parser XML non disponibile.", exception)
        } catch (exception: SAXException) {
            throw GpxParseException("GPX non valido: ${exception.message ?: "errore XML"}.", exception)
        }
        return handler.track()
    }

    private class TrackHandler(private val limits: GpxLimits, private val checkActive: () -> Unit) : DefaultHandler2() {
        private val path = ArrayList<String>()
        private var rootNamespace: String? = null
        private val segments = ArrayList<TrackSegment>()
        private var points: MutableList<TrackPoint>? = null
        private var point: TrackPoint? = null
        private var text: StringBuilder? = null
        private var name: String? = null
        private var pointCount = 0
        private var segmentCount = 0

        override fun startDTD(name: String?, publicId: String?, systemId: String?) {
            throw SAXException("I documenti con DTD non sono supportati")
        }

        override fun startElement(uri: String, localName: String, qName: String, attributes: Attributes) {
            checkActive()
            if (path.size >= limits.maxDepth) throw SAXException("Struttura GPX troppo annidata")
            if (path.isEmpty()) {
                if (localName != "gpx") throw SAXException("Elemento principale GPX mancante")
                rootNamespace = uri
            }
            path.add(if (uri == rootNamespace) localName else "#extension")
            when {
                at("gpx", "trk", "trkseg") -> {
                    if (++segmentCount > limits.maxSegments) throw SAXException("Troppi segmenti GPX. Limite: ${limits.maxSegments}")
                    points = ArrayList()
                }
                at("gpx", "trk", "trkseg", "trkpt") -> {
                    if (++pointCount > limits.maxPoints) throw SAXException("Troppi punti GPX. Limite: ${limits.maxPoints}. Dividi o semplifica la traccia")
                    val latitude = coordinate(attributes.getValue("lat"), -90.0, 90.0, "latitudine")
                    val longitude = coordinate(attributes.getValue("lon"), -180.0, 180.0, "longitudine")
                    point = TrackPoint(latitude, longitude)
                }
                at("gpx", "trk", "name") ||
                    at("gpx", "trk", "trkseg", "trkpt", "ele") ||
                    at("gpx", "trk", "trkseg", "trkpt", "time") -> text = StringBuilder()
            }
        }

        override fun characters(ch: CharArray, start: Int, length: Int) {
            checkActive()
            if (text?.let { length > limits.maxTextChars - it.length } == true)
                throw SAXException("Campo GPX troppo lungo. Limite: ${limits.maxTextChars} caratteri")
            text?.append(ch, start, length)
        }

        override fun endElement(uri: String, localName: String, qName: String) {
            val value = text?.toString()?.trim()
            when {
                at("gpx", "trk", "name") -> {
                    if (name == null && !value.isNullOrEmpty()) name = value
                    text = null
                }
                at("gpx", "trk", "trkseg", "trkpt", "ele") -> {
                    if (!value.isNullOrEmpty()) {
                        val elevation = value.toDoubleOrNull()?.takeIf(Double::isFinite)
                            ?: throw SAXException("Quota non valida")
                        point = point!!.copy(elevation = elevation)
                    }
                    text = null
                }
                at("gpx", "trk", "trkseg", "trkpt", "time") -> {
                    if (!value.isNullOrEmpty()) {
                        val timestamp = try { Instant.parse(value) } catch (_: DateTimeParseException) {
                            throw SAXException("Orario non valido")
                        }
                        point = point!!.copy(timestamp = timestamp)
                    }
                    text = null
                }
                at("gpx", "trk", "trkseg", "trkpt") -> {
                    points!!.add(point!!)
                    point = null
                }
                at("gpx", "trk", "trkseg") -> {
                    segments.add(TrackSegment(points!!.toList()))
                    points = null
                }
            }
            path.removeAt(path.lastIndex)
        }

        fun track(): Track {
            if (segments.none { it.points.isNotEmpty() }) throw GpxParseException("Il GPX non contiene punti traccia.")
            return Track(name, segments.toList())
        }

        private fun at(vararg elements: String): Boolean =
            path.size == elements.size && elements.indices.all { path[it] == elements[it] }

        private fun coordinate(value: String?, min: Double, max: Double, label: String): Double =
            value?.toDoubleOrNull()?.takeIf { it.isFinite() && it in min..max }
                ?: throw SAXException("${label.replaceFirstChar { it.uppercase() }} non valida")
    }
}
