# GPXdev

Navigatore Android per tracce GPX e mappe PMTiles locali, sviluppato da gianmaria.dev.
Prima versione consolidata nella **1.21.0**.

## Versione e compatibilità

- Android 10 o successivo (API 29+); compilazione e target API 36.
- Versione applicazione: `1.21.0`, versionCode `37`.
- Identificativo Android: `com.gpxnavigator`, mantenuto per la compatibilità degli aggiornamenti.
- Pacchetto distribuito in questo progetto: `app-1.21.0.apk`, build debug con firma compatibile con le versioni precedenti.
- La conferma dei controlli eseguiti è riportata in `1.21.0-test-telefono.txt`; checksum in `app-1.21.0.apk.sha256`.

## Funzioni disponibili

- Importazione di più file GPX dal selettore Android, con copia privata e catalogo persistente Room.
- Selezione ed eliminazione delle tracce con conferma e protezione della traccia in navigazione.
- Parsing di tracce e segmenti multipli, quota e timestamp opzionali.
- Importazione e selezione di una mappa PMTiles locale; stili vettoriali Protomaps/OpenMapTiles, oppure mappe raster supportate dal lettore.
- Posizione, accuratezza GPS, velocità, quota e direzione quando disponibili.
- Follow con un solo pulsante freccia, zoom tramite pizzico e pulsanti zoom in verticale.
- Menu **Visuale**: Nord in alto oppure Senso di marcia. Preferenze di visuale e zoom salvate.
- Navigazione con servizio in primo piano e notifica, anche a schermo spento; modalità Esplora con GPS legato alla visibilità della mappa.
- Ripresa esplicita di una sessione interrotta e comandi Apri/Termina dalla notifica.
- Distanza totale, percorsa e rimanente, velocità massima della sessione, stima del tempo e dell'orario di arrivo.
- Avviso fuori traccia e indicazioni delle curve ricavate dalla geometria del GPX, incluse seconda manovra e barra di avvicinamento.
- Tratto già percorso evidenziato sulla mappa quando il progresso è attendibile.

Le indicazioni seguono la traccia importata. L'app non calcola un percorso stradale e non ricalcola il tragitto quando si esce dal GPX. Le metriche e le indicazioni vengono sospese o sostituite da uno stato esplicito quando il GPS o il riconoscimento della traccia non sono attendibili. La stima di arrivo dipende dall'andatura recente e dalla continuità del GPX.

## Aspetto e orientamento

I temi **Soft** e **Cyber** condividono funzioni, comandi e layout; cambia la palette.

| Tema | Colori principali |
| --- | --- |
| Soft | Fondo `#FDFCFF`, testo nero, rosa `#F2ACE0`, giallo `#FCFF8B`, azzurro `#9AE1E5` |
| Cyber | Fondo `#121218`, testo bianco, rosa fluo `#F2055C`, lime `#B6E536` |

La Dashboard mostra GPXdev sopra il titolo Dashboard e il sottotitolo “Importa una traccia GPX e navigala offline - GPS only”. In landscape dispone comandi e archivio su due pannelli con scorrimento indipendente, quando spazio e dimensione dei caratteri lo consentono. Nelle schede landscape, **Info traccia** apre i metadati. In finestre strette o con caratteri grandi il layout torna a una colonna.

In navigazione landscape, velocità e Vmax, Arrivo, km percorsi e rimanenti sono raccolti nella sidebar a riquadri. Il GPS è nella barra superiore e apre le informazioni complete. Il pulsante Visuale e il Follow sono disponibili in entrambi gli orientamenti; **+ e − compaiono soltanto in verticale**.

Il footer della Dashboard contiene la versione, che apre il changelog, e **App developed by gianmaria.dev**, che apre il sito nel browser esterno Android. L'icona launcher è la bussola GPXdev, adattiva alle forme del launcher.

## Utilizzo offline e dati

1. Trasferire sul telefono i file GPX e una mappa `.pmtiles`.
2. Aprire **Esplora mappa**, scegliere la mappa PMTiles e attendere la copia privata.
3. Importare un GPX dalla Dashboard, selezionarlo e premere **Avvia navigazione**.
4. Concedere i permessi di posizione; il permesso notifiche consente i comandi nel pannello Android.
5. Usare **Termina** per chiudere la sessione.

La mappa non è inclusa nell'APK e va importata separatamente. Il progetto conserva file e note cartografiche in `offline-maps/`; la provenienza è documentata nei relativi file SOURCE e README.

La navigazione funziona senza rete con GPS attivo e dati già importati. L'app non richiede permessi Internet e non carica tile o font remoti. Il link sviluppatore demanda la connessione al browser esterno. Non sono presenti account, backend, cloud, pubblicità o analytics.

GPX e mappe sono copiati nella memoria privata dell'app: verificare lo spazio disponibile prima di importare mappe grandi. Per aggiornare mantenendo il catalogo, installare l'APK sopra la versione precedente con la stessa firma, senza disinstallare.

## Architettura e stack

Kotlin, Jetpack Compose Material 3, MVVM, StateFlow, coroutine, Room, FusedLocationProviderClient e MapLibre Native con PMTiles. Il contenitore applicativo è `NavigatorApplication`; il servizio e il controller di navigazione mantengono la sessione indipendente dalla schermata.

- `data/`: parsing e archivio GPX, database, mappe e preferenze.
- `domain/model/`: modelli di traccia, posizione e preferenze.
- `location/`: provider GPS e stato dei permessi/segnale.
- `navigation/`: sessione, servizio e calcoli di progresso, curve, fuori traccia, arrivo e Vmax.
- `ui/`: Dashboard, navigazione, mappa e temi.

Dettagli in [ARCHITECTURE.md](ARCHITECTURE.md). Stato della prima versione in [ROADMAP.md](ROADMAP.md). Cronologia completa in `app/src/main/assets/CHANGELOG.txt`.

## Build e controlli da PowerShell

Servono JDK 17, Android SDK API 36 con Build Tools 36.0.0 e le dipendenze Gradle. Android Studio non è necessario.

Nella workspace già predisposta:

```powershell
. .\.tools\env.ps1
.\gradlew.bat --offline --no-daemon assembleDebug test lintDebug
```

L'opzione `--offline` richiede le dipendenze già in cache. In un ambiente nuovo configurare JDK/SDK e `local.properties`, poi eseguire la prima build con accesso ai repository Gradle.

Output e report:

- APK compilato: `app/build/outputs/apk/debug/app-debug.apk`.
- Test JUnit: `app/build/test-results/testDebugUnitTest/` e `app/build/reports/tests/testDebugUnitTest/`.
- Lint: `app/build/reports/lint-results-debug.html`.
- Log di preparazione 1.21.0: `.tools/1.21.0-build-test.log`.

Installazione con gli strumenti locali:

```powershell
& .\.tools\android-sdk\platform-tools\adb.exe install -r .\app-1.21.0.apk
Get-FileHash -LiteralPath .\app-1.21.0.apk -Algorithm SHA256
```

I test coprono parser, catalogo, sessione, calcoli di navigazione, policy della mappa, preferenze, contrasto e layout adattivo. Le verifiche su display reale, launcher, GPS e gestione energetica Android sono descritte nelle guide per telefono di ogni versione. I test sul telefono della 1.20.1 sono stati confermati dall'utente; per la 1.21.0 seguire la guida dedicata.

## Limiti e attività future

La prima versione non include routing/rerouting stradale, indicazioni vocali, download automatico delle regioni, Android Auto, iOS o sincronizzazione. La variante monocromatica dell'icona per le icone a tema Android resta un miglioramento grafico separato. L'APK fornito è una build debug per installazione diretta; una distribuzione su store richiederà una preparazione dedicata.
