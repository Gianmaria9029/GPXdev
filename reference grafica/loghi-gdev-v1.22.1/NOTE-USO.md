# GPXdev / gdev — proposte logo

## Riferimenti analizzati

- Versione attuale nel codice: 1.22.1, versionCode 39 (app/build.gradle.kts). APK 1.22.1 presente.
- DashboardContent.kt: brand testuale GPXdev monospaziato e Dashboard; layout verticale e orizzontale. In landscape il blocco brand parte da 136 dp e condivide la riga con descrizione e selettore tema: serve contenere l'altezza e verificare le firme piccole.
- DashboardFooter.kt: credito App developed by gianmaria.dev con apertura browser.
- Tema Soft: fondo #FDFCFF e testo nero; Cyber: #121218 e rosa #F2055C. Il rosa dell'icona rimane il riferimento del marchio.
- CONTESTO_CODEX_GPXDEV_WEB_1.22.0.md: pagina /progetti/gpxdev.html, temi Light/Dark/EVA01 e font Inter, IBM Plex Mono, Space Grotesk. Analisi basata sul documento locale, senza nuova visita al sito.
- Icona attiva: risorse mipmap adattive, verificata visivamente nella tavola .tools/launcher-preview-1.20.1.png. Bussola, freccia rosa verso nord-est, GPXdev e gdev.

## Varianti

1. Firma orizzontale: bussola, GPXdev e by gdev. Direzione consigliata per dashboard e intestazione web: nome prodotto prioritario e firma autore esplicita. A 136 dp va verificata la leggibilità della firma e preparata una composizione compatta.
2. gdev integrato: bussola nella g, GPXdev sotto. Più caratterizzante per il marchio autore; prodotto in secondo piano.
3. Emblema: bussola e gdev nello stesso simbolo, GPXdev sotto. Adatto a hero e presentazione; richiede più altezza.

## Stato e integrazione

Le immagini sono proposte raster generate con image_gen, con confronto scuro/chiaro. Dopo la scelta preparare asset separati trasparenti e sorgente vettoriale, verificati alle dimensioni reali di dashboard e sito. Le tavole non vanno inserite intere nella UI.

Solo nuovi materiali grafici e note; codice, launcher e pagina web non modificati. Build e test non eseguiti: non pertinenti alle proposte grafiche. Controllo visivo delle tre immagini eseguito: testi presenti, simbolo coerente e trattamento chiaro/scuro.

