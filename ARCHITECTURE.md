# Architettura di GPXdev

Questo documento descrive GPXdev 1.40.1. L'app è scritta in Kotlin, usa Jetpack Compose per l'interfaccia, Room per i dati strutturati e MapLibre Native per la mappa. Le tracce, le registrazioni e le mappe sono elaborati sul dispositivo. Il link al sito apre il browser esterno.

## Componenti principali

| Componente | Responsabilità |
| --- | --- |
| `MainActivity` e `ui/NavigatorApp.kt` | Avviano l'interfaccia Compose e gestiscono il passaggio tra Home, mappa e Cockpit. |
| `NavigatorApplication` | Crea il database Room, i repository e il controller condiviso. |
| `data/gpx/` e `data/database/` | Leggono e scrivono GPX e gestiscono l'archivio delle tracce e delle registrazioni. |
| `data/maps/` e `ui/map/` | Importano e convalidano le mappe PMTiles, costruiscono lo stile locale e gestiscono camera, posizione e tracce in MapLibre. |
| `location/` e `navigation/` | Gestiscono il GPS, il ciclo di vita della sessione, le indicazioni lungo la traccia e le statistiche del giro. |
| `ui/importgpx/`, `ui/navigation/`, `ui/cockpit/` e `ui/settings/` | Presentano archivio, navigazione, registrazione e impostazioni. I ViewModel espongono lo stato con `StateFlow` e Compose lo osserva in base al ciclo di vita della schermata. |

I componenti sono nel [sorgente Android](app/src/main/java/com/gpxnavigator/).

## Tracce GPX e archivio

L'utente seleziona un file tramite il selettore di Android. `LocalGpxRepository` copia il file nello spazio privato dell'app. `GpxParser` convalida le coordinate e legge i segmenti, mantenendo quota e orario quando presenti. Il catalogo Room conserva i metadati, la selezione, i preferiti e l'ordinamento. L'archivio permette di cercare e rinominare le tracce. L'esportazione usa il percorso scelto dall'utente con il selettore di Android.

Il database usa lo **schema 6**. Le migrazioni automatiche collegano gli schemi dalla versione 1 alla 6. Le versioni esportate dello schema sono in [app/schemas](app/schemas/).

## GPS, sessione e navigazione

`AndroidLocationProvider` usa `FusedLocationProviderClient` e comunica lo stato del segnale e dei permessi. `NavigationController` coordina la posizione, la sessione e la registrazione. Durante la navigazione o la registrazione, `NavigationService` mantiene attiva la sessione con un servizio in primo piano e una notifica con i comandi per aprirla o terminarla. L'esplorazione usa il GPS mentre la mappa è visibile. La Home offre la ripresa esplicita di una sessione interrotta.

`TrackPositionMatcher` e `TrackProgressCalculator` associano la posizione alla traccia e stimano il progresso. Le indicazioni delle curve, l'avviso fuori traccia e la stima di arrivo derivano dal GPX e dai dati del giro. Il percorso da seguire è quello importato dall'utente.

## Registrazione libera e Cockpit

Il controller distingue navigazione, registrazione libera e Cockpit. La navigazione può registrare il giro mentre l'utente segue una traccia. La registrazione libera permette di partire senza un GPX. Avviare il Cockpit avvia anche la registrazione e mostra i dati del giro in una schermata dedicata.

`RecordingRepository` salva la bozza e i campioni GPS in Room. Alla fine del giro, l'utente può salvare la registrazione come traccia GPX nell'archivio oppure scartarla. Il Cockpit mostra il riepilogo prima della scelta. Le bozze e le decisioni in sospeso sono recuperabili dopo un'interruzione.

## Cartografia e preferenze

`OfflineMapRepository` copia il PMTiles selezionato nello spazio privato, controlla intestazione e struttura e attiva la mappa dopo la verifica. MapLibre usa l'archivio locale e i font inclusi nell'app. I controlli della mappa gestiscono lo zoom, il Follow e la visuale con nord in alto o orientata nel senso di marcia.

Le preferenze di tema Soft o Cyber, aspetto della mappa, zoom, orientamento della mappa e impostazioni dello schermo sono salvate in locale. Le palette chiara e scura della mappa sono distinte dai temi dell'interfaccia.

## Compilazione e test

Il modulo Android è `app/` e l'identificativo è `com.gpxnavigator`. I test JVM con JUnit e Robolectric coprono parsing GPX, archivio e migrazioni, importazione delle mappe, calcoli di navigazione, registrazione e interfaccia. Le risorse dei test includono i file in [sample-data](sample-data/).

Per i comandi su Windows, Linux e macOS, consulta il [README](README.md). Le tappe della prima versione sono conservate nella [roadmap storica](docs/archive/ROADMAP.md).
