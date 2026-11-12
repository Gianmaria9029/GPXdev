package com.gpxnavigator.ui.map

import com.gpxnavigator.domain.model.MapAppearance
import com.gpxnavigator.ui.theme.MapPalette
import com.gpxnavigator.ui.theme.SoftMapPalette

private val LightMapPalette = SoftMapPalette.copy(
    background = "#F7F3E8", landcover = "#D3E5BF", landuse = "#DDD7CF",
    water = "#78BCDC", buildings = "#BDB4A9", transit = "#526B80",
    roads = "#FFFCF4", roadBorder = "#4B5561", boundaries = "#665A78",
    labels = "#20272D", labelHalo = "#FFFDF6",
    track = "#A00049", trackPassed = "#58616B", trackOutline = "#FFFFFF",
    majorRoads = "#F4C15B", trails = "#705742",
    transportDashLength = 6f,
)

private val DarkMapPalette = LightMapPalette.copy(
    background = "#131922", landcover = "#254031", landuse = "#30343C",
    water = "#153956", buildings = "#414B57", transit = "#BA9767",
    roads = "#687584", roadBorder = "#111721", boundaries = "#A798C0",
    labels = "#F4F7FA", labelHalo = "#141A23",
    track = "#FF94C2", trackPassed = "#707C8C", trackOutline = "#171923",
    majorRoads = "#96703B", trails = "#BCA98F",
    location = "#64CFFF", locationOutline = "#131922", locationHalo = "rgba(100, 207, 255, 0.18)",
    transportDashLength = 2f,
)

internal fun navigationMapPalette(appearance: MapAppearance): MapPalette = when (appearance) {
    MapAppearance.LIGHT -> LightMapPalette
    MapAppearance.DARK -> DarkMapPalette
}
