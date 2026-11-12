# Architettura — GPXdev

## Obiettivo

Descrivere l’architettura della prima versione di GPXdev, consolidata nella 1.21.0.

L'applicazione deve essere:

- offline-first;
- modulare;
- testabile;
- semplice da mantenere;
- priva di backend;
- sviluppabile e compilabile da CLI.

---

# Architettura generale

```text
                GPX FILE
                   │
                   ▼
           LocalGpxRepository
                   │
                   ▼
               GpxParser
                   │
                   ▼
               Track Model
                   │
          ┌────────┴────────┐
          ▼                 ▼
       Map UI          Navigation State
          │                 │
          ▼                 ▼
      MapLibre        LocationProvider
          │                 │
          ▼                 ▼
       PMTiles              GPS
```

---

# Pattern

Usare:

- MVVM;
- ViewModel;
- StateFlow;
- repository pattern dove utile;
- dipendenze tra layer orientate dal basso verso l'alto.

Evitare:

- singleton globali non necessari;
- logica applicativa dentro Activity;
- parsing GPX direttamente nella UI;
- accesso al GPS direttamente nei Composable;
- dipendenze circolari.

---

# Struttura implementata

```text
app/src/main/java/com/gpxnavigator/
├── MainActivity.kt
├── NavigatorApplication.kt
├── data/
│   ├── database/       # Room: NavigatorDatabase, TrackDao, TrackEntity
│   ├── gpx/            # GpxParser, LocalGpxRepository
│   ├── maps/           # OfflineMapRepository, PmTilesHeader
│   ├── navigation/     # NavigationSessionStore
│   └── settings/       # Preferenze di tema, orientamento e zoom
├── domain/model/       # Track, TrackSegment, TrackPoint, LocationPoint, SavedTrack
├── location/           # LocationProvider, AndroidLocationProvider, LocationState
├── navigation/         # Controller, servizio e calcoli indipendenti dalla UI
└── ui/
    ├── importgpx/      # Dashboard, catalogo, footer e policy adattiva
    ├── navigation/     # Schermata, ViewModel, manovre e strumentazione
    ├── map/            # MapView, stile offline, overlay e policy camera
    ├── components/     # Controlli condivisi e changelog
    └── theme/          # Palette Soft/Cyber e preferenza del tema
```

`NavigatorApplication` crea il database Room, il repository GPX e il controller di navigazione condivisi. I ViewModel espongono StateFlow; i Composable osservano lo stato e invocano azioni. Non sono presenti layer usecase vuoti né accesso al GPS nei Composable.

---

# Modelli dominio

## TrackPoint

Rappresenta un singolo punto GPX.

Campi minimi:

```kotlin
data class TrackPoint(
    val latitude: Double,
    val longitude: Double,
    val elevation: Double? = null,
    val timestamp: Instant? = null
)
```

Il timestamp usa `java.time.Instant`, disponibile sulle versioni Android supportate.

---

## TrackSegment

```kotlin
data class TrackSegment(
    val points: List<TrackPoint>
)
```

---

## Track

```kotlin
data class Track(
    val name: String?,
    val segments: List<TrackSegment>
)
```

---

## Posizione, progresso e stato della schermata

`LocationPoint` conserva coordinate e, quando disponibili, accuratezza, velocità, direzione, quota, tempo monotono e accuratezza della direzione. `LocationState` esplicita gli stati STOPPED, WAITING, ACTIVE, UNAVAILABLE, DISABLED, DENIED ed ERROR.

`TrackProgress`, `TrackGuidance`, `OffTrackState` e `ArrivalEstimate` sono prodotti dal layer navigation. `NavigationUiState`, definito insieme a `NavigationViewModel`, li combina con mappa, sessione, Follow, zoom, orientamento ed errori. I valori non affidabili restano assenti o vengono rappresentati da stati espliciti.

---

# GPX layer

## GpxParser

Responsabilità:

- ricevere uno stream o contenuto XML;
- leggere GPX;
- convertire il contenuto nei modelli dominio;
- gestire GPX con più segmenti;
- validare coordinate;
- ignorare elementi non supportati senza interrompere il parsing quando possibile.

Non deve conoscere:

- MapLibre;
- Compose;
- ViewModel;
- GPS.

---

## LocalGpxRepository

Responsabilità:

- importare GPX;
- caricare GPX;
- restituire oggetti `Track`;
- gestire eventuali file GPX locali.

Il catalogo usa Room con `NavigatorDatabase`, `TrackDao` e `TrackEntity`. File GPX e metadati vengono coordinati durante importazione ed eliminazione; il repository protegge la traccia agganciata alla sessione attiva. La cancellazione di una coroutine non deve rimuovere un file già registrato nel database.

---

# Location layer

## LocationProvider

Definire un'astrazione per la posizione.

Contratto implementato:

```kotlin
interface LocationProvider {
    val state: StateFlow<LocationState>
    fun hasPermission(): Boolean
    fun start()
    fun stop()
}
```

`AndroidLocationProvider` usa `FusedLocationProviderClient` e osserva i cambiamenti delle impostazioni GPS.

La UI non deve accedere direttamente al provider Android.

---

# Map layer

MapLibre deve essere incapsulato il più possibile nel layer UI/map.

Responsabilità:

- mostrare la mappa;
- caricare lo style;
- usare sorgenti locali;
- visualizzare polyline GPX;
- visualizzare posizione corrente;
- controllare camera e follow mode.

La logica delle distanze non deve essere implementata dentro MapLibre.

---

# PMTiles

I file PMTiles devono essere considerati risorse locali.

Flusso:

```text
file PMTiles
    ↓
OfflineMapRepository
    ↓
MapLibre
    ↓
render mappa
```

Per visualizzare la cartografia occorre importare una mappa PMTiles dal dispositivo; la schermata gestisce anche l'assenza della mappa.

Il file viene scelto con Storage Access Framework, copiato nella memoria privata e verificato prima di renderlo attivo. La scelta persistente controlla il risultato di commit. Tile e font necessari al rendering sono locali; non è previsto download automatico nell’app.

---

# Calcolo distanza

Usare coordinate GPS e una formula geografica appropriata.

La logica deve essere centralizzata in una classe dedicata, ad esempio:

```text
DistanceCalculator
```

Evitare duplicazione dei calcoli in ViewModel o Composable.

---

# Sessione e calcoli di navigazione

`NavigationController` coordina il provider GPS e i dati della navigazione. La sessione distingue RESTORING, IDLE, STARTING, ACTIVE e INTERRUPTED. `NavigationService` è un foreground service di tipo location, con notifica e comandi Apri/Termina. In esplorazione, la raccolta GPS segue la visibilità della mappa; in navigazione viene mantenuta dal servizio. Dopo un'interruzione la ripresa è esplicita dalla Dashboard.

`TrackProgressCalculator` e `TrackPositionMatcher` calcolano e confermano la posizione lungo il GPX. `OffTrackMonitor`, `TrackGuidanceCalculator`, `ArrivalTimeEstimator`, `MovementBearingEstimator` e `SessionMaximumSpeed` gestiscono rispettivamente fuori traccia, curve, stima arrivo, direzione di movimento e Vmax. La geometria dei segmenti e i casi ambigui restano sotto controllo del layer navigation.

Le indicazioni delle curve derivano dal GPX; non costituiscono un motore di routing stradale. Non sono presenti rerouting o TTS.

# Stato UI e preferenze

`ImportGpxViewModel` espone catalogo, selezione, operazioni e sessione. `NavigationViewModel` combina sessione e calcoli con stato della mappa, zoom e orientamento. Entrambi espongono StateFlow osservati con lifecycle.

Il tema Soft/Cyber, l'orientamento e lo zoom sono salvati in SharedPreferences. L'identificativo storico `standard` continua a rappresentare Soft per conservare la preferenza degli aggiornamenti precedenti. Il salvataggio controlla il risultato di commit e comunica eventuali errori.

I due temi condividono i Composable e differiscono per i ruoli della palette. La Dashboard usa `DashboardLayoutPolicy` per scegliere due pannelli o una colonna in base a spazio disponibile e font. La navigazione mantiene layout adattivi e riserva spazio della camera ai controlli sovrapposti. Il footer sviluppatore invia un Intent HTTPS a un browser esterno; nessuna WebView o connessione HTTP viene aggiunta all'app.

---

# Threading

Operazioni che possono essere costose:

- parsing GPX;
- lettura file;
- calcoli su tracce grandi;

non devono bloccare il main thread.

Usare coroutine Kotlin.

---

# Error handling

Gli errori devono essere rappresentati esplicitamente.

Gestire almeno:

- GPX non valido;
- GPX vuoto;
- file non leggibile;
- mappa offline mancante;
- permesso posizione negato;
- GPS disabilitato;
- posizione non disponibile.

Non nascondere eccezioni con `catch(Exception)` senza gestione.

---

# Logging

Usare logging Android solo per informazioni utili al debug.

Non loggare continuamente ogni aggiornamento GPS in produzione. Gli avvisi eccezionali di arresto GPS e pulizia file usano intenzionalmente il logger Android. Le esclusioni LogNotTimber sono circoscritte ai metodi coinvolti e motivate, senza aggiungere un framework di logging. Analogamente, UseKtx è escluso dove serve conservare il risultato Boolean di commit.

---

# Dipendenze

Aggiungere una dipendenza solo se:

1. risolve una necessità concreta;
2. è compatibile con Android;
3. non introduce dipendenze cloud obbligatorie;
4. non duplica funzionalità facilmente implementabili con API standard.

Codex deve motivare nel riepilogo ogni nuova dipendenza aggiunta.

---

# Test prioritari

La V1 deve avere test automatici almeno per:

- parsing GPX;
- GPX con più segmenti;
- GPX vuoto o non valido;
- calcolo distanza;
- progresso lungo traccia.

UI e MapLibre possono essere testati inizialmente principalmente sul dispositivo reale.


# Verifica della versione consolidata

La distribuzione locale 1.21.0 è un APK debug, con lo stesso application ID `com.gpxnavigator` e certificato dei precedenti aggiornamenti. Prima della consegna si eseguono assembleDebug, test e lintDebug e si verificano versione, firma e checksum. Il risultato effettivo della preparazione è in `1.21.0-test-telefono.txt`.

Il catalogo Room rimane allo schema 1. La chiusura della prima versione non introduce migrazioni, nuove dipendenze o permessi. L'icona adattiva mantiene la grafica approvata; la variante monocromatica per le icone a tema rimane un'attività separata.
