# Contesto per Codex — pagina web GPXdev 1.22.0

Documento preparato il **27 ottobre 2026** analizzando il progetto Android locale, l'APK 1.22.0 e il sito pubblico gianmaria.dev.

## 1. Incarico e uso di questo file

Realizzare la pagina di presentazione di **GPXdev** sul sito personale di Gianmaria Gori, con spiegazione dell'applicazione, funzionalità, download dell'APK e istruzioni per autorizzarne l'installazione da fonti esterne a Google Play.

Questo file è una base di conoscenza e una guida di implementazione, **non il testo definitivo della pagina**. L'utente fornirà separatamente un prompt per tarare pubblico, registro, tono, profondità e lunghezza dei testi. Applicare quel prompt alla forma dei contenuti, mantenendo corretti i fatti qui documentati. Non fissare in anticipo un target esclusivo o un numero di sezioni, parole o FAQ.

Il risultato richiesto al futuro Codex è la pagina web nel progetto del sito. Il presente documento non autorizza modifiche all'app Android e non richiede di ricompilarla. Adattare l'implementazione alle istruzioni e agli strumenti effettivamente presenti nel repository del sito.

## 2. Stato reale del sito e destinazione corretta

### Pagine visitate e osservazioni verificate

| Pagina | Stato osservato il 27/10/2026 |
| --- | --- |
| https://gianmaria.dev/ | Hub personale di Gianmaria Gori; presenta attività di formazione AI/IT, consulenza digitale e sviluppo web. |
| https://gianmaria.dev/progetti/index.html | Archivio «Progetti & Lavori», con filtri e schede navigabili. GPXdev è già presente come seconda scheda. |
| **https://gianmaria.dev/progetti/gpxdev.html** | Pagina già esistente: intestazione GPXdev, etichetta «// App Android» e grande riquadro «UNDER CONSTRUCTION». Da completare. |

La destinazione già collegata dal sito è quindi **`/progetti/gpxdev.html`**, non un nuovo `/gpxdev.html` alla radice. Conservare questo indirizzo salvo istruzione esplicita dell'utente. Il canonical e `og:url` attuali puntano già all'indirizzo corretto.

La scheda nel portfolio contiene già:

- Nome: **GPXdev**.
- Etichetta: **App Android**.
- Azione: **SCOPRI APP**; il collegamento di apertura è «Apri GPXdev».
- Descrizione: «App Android per navigare offline su tracce GPX e registrare i propri percorsi.»
- Parole chiave: Android · GPX · Navigazione offline · Registrazione percorsi.

Non creare una seconda scheda identica. Verificare che quella esistente continui ad aprire la pagina completata.

### Identità visiva del sito

Il sito osservato ha un'impostazione da console/portfolio: intestazioni ampie, etichette monospaziate con `//`, linee sottili, pannelli geometrici, accenti lime e molto spazio tra i blocchi. Il brand in alto è **GIANMARIA.DEV**. La pagina GPXdev ha già il ritorno **← Progetti**, il selettore DARK, il controllo EVA01 e il pulsante per tornare in alto.

Sono disponibili tre temi web, gestiti dall'attributo `data-theme` sull'elemento `html`: `light`, `dark`, `eva01`. Conservare il sistema di selezione e persistenza già presente; esaminarne il codice nel repository prima di intervenire. Le palette effettive sono sovrascritte dai fogli dedicati caricati dopo `style.css`.

| Tema web | Fondo | Pannelli | Testo | Accenti osservati nei CSS |
| --- | --- | --- | --- | --- |
| Light | `#e7eaed` | `#ffffff` | `#202526` | lime `#c5f544`, testo accento `#4b6510` |
| Dark | `#1c1c1c` | `#292929` | `#f1f4f2` | lime `#c5f544`, hover `#b6e536` |
| EVA01 | `#000000` | `#2d223b` | `#f2eef6` | viola `#765899`, verde `#41bb42`, azioni arancio `#e8790c` |

Font caricati: **Inter**, **IBM Plex Mono**, **Space Grotesk**. Il testo del body osservato usa Inter con fallback di sistema. Riutilizzare le variabili e le classi del sito; non duplicare tutte queste palette in un nuovo sistema.

I temi **Soft/Cyber dell'app Android** sono un'altra cosa rispetto ai temi **Light/Dark/EVA01 del sito**. Si possono mostrare e spiegare nell'area prodotto senza sostituire il tema globale della pagina con quello dell'app.

### Riferimenti di implementazione osservati nella pagina esistente

Percorsi relativi alla radice del sito, da verificare nel checkout disponibile:

- `progetti/gpxdev.html`: pagina da completare.
- `progetti/index.html`: collegamento dal portfolio già presente.
- `assets/css/style.css`: stili di base.
- `progetti/progetti.css`: stili dell'area progetti.
- `assets/css/dashboard.css`: componenti console condivisi.
- `assets/css/light-lime.css`, `assets/css/dark-lime.css`, `assets/css/eva01-tv.css`: temi web.
- `assets/css/back-to-top.css`, `assets/js/back-to-top.js`: ritorno in alto.
- `assets/js/main.js`: script condiviso presente nella pagina, da leggere per il comportamento dei temi.
- `progetti/ai/ai.css`: attualmente caricato anche dal placeholder GPXdev; verificare quali regole servono davvero dopo la rimozione del riquadro provvisorio.

Il body attuale ha le classi `console-page ai-page back-to-top-page`. L'hero usa `hero`, `eyebrow`, `bigname`, `redline`. Il selettore tema ha ID `themeToggle`; quello EVA01 ha ID `evaThemeToggle`. Preservare i contratti richiesti dagli script condivisi. Isolare gli eventuali nuovi stili con un contenitore o prefisso specifico GPXdev per evitare regressioni sulle altre pagine.

Il titolo attuale è «GPXdev — Gianmaria Gori». Description e Open Graph contengono ancora «Pagina in costruzione»: aggiornarli insieme al contenuto visibile. Il footer attuale riporta © 2026 Gianmaria Gori e `// STATUS: ONLINE`. Il contatto pubblico verificato nella home è **info@gianmaria.dev**; usarlo se serve un riferimento per assistenza o segnalazioni, senza inventare canali dedicati.

## 3. Identità dell'applicazione

**Nome pubblico:** GPXdev, con questa combinazione di maiuscole/minuscole.

**Autore:** Gianmaria Gori / gianmaria.dev.

**Descrizione funzionale di riferimento:** applicazione Android per seguire tracce GPX su mappe offline locali e registrare il percorso realmente compiuto, anche senza un itinerario da seguire.

Tre attività distinte aiutano a spiegare il prodotto:

1. **Esplorare:** consultare la mappa locale e la propria posizione mentre la schermata mappa è visibile.
2. **Seguire una traccia:** importare e selezionare un GPX, avviare la navigazione e consultare posizione, progresso, indicazioni e dati del giro.
3. **Registrare:** salvare il percorso reale mentre si segue un GPX, oppure avviare una registrazione libera senza itinerario.

L'utilizzo durante il percorso funziona senza connessione Internet, con GPS/posizione attivi e i file necessari già disponibili sul telefono. Il download iniziale di APK, mappe e GPX è una fase distinta dalla navigazione offline.

Il sottotitolo attuale nell'app è «Importa una traccia GPX e navigala offline - GPS only». È un riferimento dell'interfaccia: non esaurisce le funzioni della 1.22.0, che ora registra anche i giri.

## 4. Versione e pacchetto verificati

| Dato | Valore |
| --- | --- |
| Versione di riferimento di questo documento | **1.22.0** |
| Android versionCode | **38** |
| Nome nell'APK | **GPXdev** |
| Application ID | `com.gpxnavigator` |
| Android minimo | **Android 10**, API 29 |
| Target / compile SDK | API 36; è un dato tecnico, non il requisito minimo |
| File distribuito localmente | `app-1.22.0.apk` |
| Dimensione esatta | **75.945.265 byte** |
| Dimensione da poter visualizzare | circa **75,9 MB** oppure **72,4 MiB**, specificando coerentemente l'unità |
| Tipo build | **debug**, firmata, installabile direttamente come APK |
| Architetture native incluse | `arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64` |
| Distribuzione documentata | APK diretto; non è stato verificato un collegamento Google Play |

SHA-256 dell'APK, ricalcolato e corrispondente al file di checksum:

```text
4F5564F366987DA91432A3F22E1CBCE2E150454F47B040D993893A8885E183D2
```

Il pacchetto si trova nella workspace Android di origine:

```text
C:\Users\gianm\Desktop\navigatore_v1\app-1.22.0.apk
C:\Users\gianm\Desktop\navigatore_v1\app-1.22.0.apk.sha256
```

Firma verificata con `apksigner`: certificato Android Debug. La 1.22.0 e la 1.21.0 hanno lo stesso certificato. Questo permette l'aggiornamento della precedente installazione compatibile. Non presentare il pacchetto come build release/store, certificato da Google o sottoposto a una revisione di sicurezza indipendente.

Il 27/10/2026 è la data di questa analisi e della preparazione locale del pacchetto osservato. **Non inventare una data di pubblicazione ufficiale**: il changelog non documenta le date di rilascio.

## 5. Funzionalità disponibili nella 1.22.0

### Archivio GPX

- Importazione di uno o più file GPX tramite il selettore documenti Android.
- Copia privata dei file e catalogo persistente, consultabile in **Le mie tracce**.
- Selezione del percorso con **Usa traccia**.
- Eliminazione con conferma; la traccia collegata a una navigazione attiva è protetta.
- Metadati delle tracce e comando **Info traccia** nelle schede landscape.
- Lettura di GPX con più tracce e segmenti; quota e timestamp possono mancare.
- Il parser legge tracce `trk/trkseg/trkpt`: non promettere supporto a tutti gli elementi GPX, a file contenenti soltanto route `rte` o waypoint `wpt`, né ad altri formati come FIT, TCX o KML.

### Mappe offline

- Importazione di un file locale **`.pmtiles`**, tramite **Scegli mappa PMTiles**.
- Mappa selezionata conservata nella memoria privata dell'app; è possibile sostituirla importandone un'altra.
- Rendering con MapLibre Native, con mappe vettoriali compatibili Protomaps/OpenMapTiles e supporto raster previsto dal lettore.
- Nomi di strade e località disponibili anche offline nelle mappe vettoriali compatibili; i font necessari sono inclusi nell'app.
- Nessun caricamento di tile o font remoti durante l'uso.
- **La mappa non è inclusa nell'APK.** Deve essere procurata, trasferita e importata separatamente.
- Non esiste un catalogo di regioni scaricabili dall'app e non ci sono aggiornamenti automatici delle mappe.

Non promettere compatibilità con qualsiasi archivio PMTiles o una copertura mondiale fornita di serie: formato, schema dei layer e contenuto devono essere compatibili.

### GPS e comandi della mappa

- Posizione e accuratezza GPS; velocità, quota e direzione quando disponibili.
- Comando **Follow** per seguire la posizione; trascinare la mappa lo disattiva, riattivarlo ricentra sulla posizione mantenendo lo zoom scelto.
- Zoom tramite gesto a pizzico in entrambi gli orientamenti; pulsanti **+ / −** soltanto in verticale.
- Menu **Visuale** con **Nord in alto** e **Senso di marcia**.
- Preferenze di visuale e zoom salvate.
- Gestione esplicita di permesso negato, GPS disattivato, attesa della posizione e segnale non attendibile.

«GPS only» descrive l'uso senza rete durante il giro. Non trasformarlo in una garanzia di funzionamento su qualsiasi dispositivo privo di Google Play Services: il provider impiegato è FusedLocationProviderClient e questa compatibilità non è stata verificata.

### Navigazione e dati del percorso

- Distanza totale del GPX, distanza percorsa e rimanente lungo la traccia quando il riconoscimento è attendibile.
- Velocità corrente, velocità massima della sessione (**Vmax**) e quota disponibile.
- Stima del tempo rimanente e dell'orario di arrivo, dipendente da andatura recente, segnale e continuità della traccia.
- Indicazioni delle curve ricavate dalla **geometria del GPX**: prossima manovra, seconda manovra e barra di avvicinamento.
- Avviso **Fuori traccia**.
- Evidenziazione del tratto già percorso quando il progresso è attendibile.
- Stati espliciti al posto di valori non affidabili: non promettere stime sempre disponibili o precisione assoluta.

**Distinzione essenziale:** l'app segue un percorso importato. Non calcola un itinerario stradale da un indirizzo, non ricalcola un percorso alternativo quando si esce dal GPX e non fornisce istruzioni vocali. Le indicazioni geometriche non sono un motore di routing stradale.

### Sessione e uso con schermo spento

- Navigazione attiva tramite servizio Android in primo piano con notifica e comandi **Apri / Termina**.
- Navigazione e registrazione possono proseguire con schermo spento o un'altra applicazione aperta.
- La modalità **Esplora mappa** usa invece il GPS soltanto mentre la mappa è visibile.
- Dopo un'interruzione viene proposta una ripresa esplicita; il GPS non riparte automaticamente all'apertura.
- Per chiudere il giro usare **Termina**. Tornare alla Dashboard non equivale a terminarlo.

Descrivere il supporto in background come funzionalità implementata, senza promettere continuità assoluta su ogni telefono o dopo un arresto forzato del sistema. Non sono disponibili misurazioni universali di autonomia o consumo batteria.

### Aspetto e orientamenti

- Due temi dell'app: **Soft** e **Cyber**, con le stesse funzionalità e gli stessi comandi.
- **Soft:** fondo `#FDFCFF`, testo nero, rosa `#F2ACE0`, giallo `#FCFF8B`, azzurro `#9AE1E5`.
- **Cyber:** fondo `#121218`, pannelli `#1C1C24`, rosa fluo `#F2055C`, accenti lime `#C5F544` / `#B6E536`.
- Dashboard adattiva: in orizzontale due pannelli con scorrimento indipendente quando spazio e dimensione dei caratteri lo consentono; ritorno a una colonna negli spazi stretti.
- In navigazione landscape: strumentazione a riquadri con velocità, Vmax, arrivo, distanza percorsa e rimanente; dati completi apribili dal GPS.
- Versione nel footer cliccabile per aprire il changelog; credito sviluppatore che apre gianmaria.dev nel browser esterno.

## 6. Novità centrale della 1.22.0: registrazione dei giri

Questa funzione è implementata nel codice e nell'APK 1.22.0. Il README generale è ancora alla 1.21.0 e non la descrive: non usare quel README come elenco completo della versione attuale.

### A. Seguire un GPX e registrare il giro reale

1. Selezionare una traccia.
2. Attivare **Registra GPX** prima di avviare la navigazione.
3. Premere **Avvia navigazione**.
4. L'app segue il GPX scelto e registra separatamente le posizioni realmente rilevate.

La registrazione non modifica il GPX originale. Se si fa una deviazione, la traccia registrata conserva il percorso effettivo rilevato, non una copia dell'itinerario pianificato. Gli avvisi e le metriche dell'itinerario restano disponibili.

### B. Registrazione libera

- **Registrazione libera** avvia il giro senza dover selezionare un GPX.
- Mostra la mappa e il tracciato registrato, senza un itinerario precedente né il riquadro delle svolte.
- Dati pertinenti: distanza registrata, velocità, Vmax e quota disponibile. Non attribuire a questa modalità distanza rimanente o arrivo a una destinazione inesistente.

### Indicatore, salvataggio ed esportazione

- Durante la registrazione: pallino e scritta **REC** rossi lampeggianti nella barra superiore.
- Percorso registrato **blu**, distinto dalla traccia da seguire; colore implementato `#0057A8` con contorno bianco.
- Registrazione e navigazione condividono la stessa sorgente di posizione.
- Premere **Termina** ferma il giro prima della domanda **«Vuoi salvare il giro?»**.
- **Sì:** salva il nuovo GPX nell'archivio **Le mie tracce**.
- **No:** scarta la registrazione.
- Se non ci sono posizioni valide, il salvataggio non è disponibile.
- Nome automatico: `Giro_aaaa-mm-gg_001.gpx`, con data di inizio e progressivo persistente. Il numero viene assegnato solo al salvataggio; eliminare un giro precedente non ne riutilizza il progressivo.
- Il messaggio **Giro salvato** offre il comando **Esporta GPX**, che apre il selettore Android per scegliere la destinazione.
- Il file esportato è GPX 1.1 e contiene coordinate, tempi e quota quando disponibile; i segmenti distinti vengono conservati.

**Limite preciso dell'esportazione:** nella UI verificata l'azione è offerta dal messaggio subito dopo il salvataggio. Non descrivere un pulsante di esportazione su ogni scheda dell'archivio, una condivisione social o una sincronizzazione automatica: non risultano implementati.

### Permessi, filtri e recupero

- Per registrare occorre la **posizione precisa**; la sola posizione approssimativa non basta.
- I punti sono salvati progressivamente nel database locale.
- Sono presenti filtri per punti vecchi o duplicati, scarsa accuratezza, oscillazioni da fermo e salti GPS implausibili. Descriverli come filtri, senza promettere l'eliminazione di tutti gli errori GPS.
- Ripresa e interruzioni generano segmenti separati, evitando di unire i vuoti con una retta artificiale.
- Dopo un'interruzione è possibile riprendere la registrazione o chiuderla scegliendo se salvare. Anche una decisione di salvataggio ancora aperta viene riproposta.
- Con una registrazione attiva, **Termina** dalla notifica riapre l'app e porta alla decisione di salvataggio.

Non risultano comandi dedicati Pausa/Riprendi durante una registrazione normale: distinguere il recupero di una sessione interrotta da un pulsante pausa.

## 7. Download e installazione: contenuto necessario

### Dati da mostrare vicino al download

- Nome **GPXdev** e versione **1.22.0**.
- **Android 10 o successivo**.
- Formato **APK**, installazione diretta esterna a Google Play.
- Dimensione corretta, se prevista dal layout.
- Collegamento effettivo all'APK, con etichetta comprensibile.
- Indicazione visibile che per il primo utilizzo serve anche una mappa PMTiles separata.
- Eventuale checksum in un'area secondaria o in un file scaricabile; è un dato di integrità, non una certificazione di sicurezza.

### Procedura tecnica da tradurre nel linguaggio scelto dall'utente

1. Scaricare `app-1.22.0.apk` dal collegamento fornito nella pagina.
2. Aprire il file scaricato dal browser o dalla cartella Download.
3. Se Android blocca l'installazione da quella sorgente, aprire le impostazioni proposte e abilitare **Consenti da questa fonte** per l'app che sta aprendo il file, ad esempio il browser oppure il gestore file.
4. Tornare al pacchetto e scegliere **Installa**.
5. Aprire GPXdev e concedere i permessi di posizione richiesti; per registrare, scegliere la posizione precisa. Consentire le notifiche per avere visibili gli avvisi e i comandi della sessione.
6. Dopo l'installazione si può disattivare nuovamente l'autorizzazione a installare da quella fonte.

Il percorso manuale tipico è **Impostazioni → App → Accesso speciale alle app → Installa app sconosciute → [browser o gestore file] → Consenti da questa fonte**. Nomi e collocazione cambiano tra produttori/versioni: presentarlo come riferimento, privilegiando il pulsante delle impostazioni mostrato da Android.

È un'autorizzazione del **browser/gestore file a installare APK**, distinta dai permessi GPS di GPXdev. Non dire di concedere a GPXdev il permesso di installare app sconosciute. Non servono root, ADB o Android Studio per il normale utente.

Non invitare a disattivare Play Protect o altre protezioni globali e non descrivere ogni avviso come innocuo. Autorizzare una fonte di installazione e disabilitare i controlli antimalware sono operazioni diverse. Se cambia la procedura Android, verificare le istruzioni ufficiali invece di promettere una sequenza universale.

Fonti ufficiali consultate per questa distinzione: [download di app su Android](https://support.google.com/android/answer/9457058?hl=it) e [funzionamento di Google Play Protect](https://support.google.com/googleplay/answer/2812853?hl=it). Le procedure di verifica degli sviluppatori dipendono anche da periodo, paese e dispositivo: non aggiungere passaggi avanzati come requisito generale per tutti.

### Aggiornamento da una versione precedente

- Installare la 1.22.0 **sopra la precedente versione compatibile, senza disinstallare**.
- Con la 1.21.0 la firma è stata verificata compatibile; l'application ID resta invariato.
- La migrazione del database da schema 1 a 2 mantiene catalogo e selezione. Mappa, temi, zoom e altre preferenze non richiedono una nuova importazione per questo aggiornamento.
- Non suggerire di disinstallare come procedura standard: può rimuovere i dati privati dell'app. Le esportazioni esterne vanno gestite separatamente.
- Non promettere aggiornamenti automatici: la distribuzione qui documentata è tramite APK.

## 8. Primo utilizzo e preparazione offline

La pagina deve permettere di capire che **APK**, **GPX** e **mappa PMTiles** hanno ruoli diversi:

| File | Funzione | Quando serve |
| --- | --- | --- |
| APK | Installa l'app Android | Installazione e aggiornamenti |
| GPX | Itinerario da seguire oppure giro registrato da conservare | Necessario come input per seguire un percorso; non serve per iniziare una registrazione libera |
| PMTiles | Cartografia da visualizzare offline | Da importare separatamente per vedere la mappa della zona |

Flusso di riferimento:

1. Procurarsi una mappa PMTiles compatibile e, se si vuole seguire un itinerario, il relativo GPX. Trasferirli sul telefono prima del giro.
2. In GPXdev aprire **Esplora mappa**, scegliere **Scegli mappa PMTiles**, selezionare il file e attendere la copia nella memoria privata.
3. Per seguire una traccia: **Importa GPX → Usa traccia → Avvia navigazione**. Attivare prima **Registra GPX** se si vuole salvare anche il giro reale.
4. In alternativa usare **Registrazione libera**.
5. Lasciare attiva la posizione; la connessione dati non è necessaria per navigare o registrare con i file locali già preparati.
6. Chiudere con **Termina** e, se si stava registrando, scegliere se salvare.

Non presentare il download dell'APK come sufficiente a ottenere automaticamente tutta la cartografia.

### Mappa Italia presente nel progetto di origine

Esiste localmente `offline-maps/italia-2026-10-25.pmtiles`, separato dall'APK:

- Dimensione: **2.695.102.611 byte**, circa **2,70 GB / 2,51 GiB**.
- Estratto Italia da archivio Protomaps; PMTiles v3, vettoriale MVT, zoom 0–15.
- Il contorno comprende terraferma, Sicilia, Sardegna e isole minori; a basso zoom possono comparire territori circostanti.
- Il nome indica l'archivio sorgente del 25/10/2026, non l'ultimo aggiornamento di ogni singola strada.
- La prima importazione richiede spazio per file scaricato più copia privata: circa **5,4 GB** per questa specifica mappa, oltre all'app e agli altri dati. Un aggiornamento può richiedere temporaneamente anche lo spazio della vecchia copia, circa **8,1 GB complessivi** con mappe di pari dimensione.
- Dopo un'importazione riuscita, il file sorgente nella cartella Download può essere rimosso conservando la copia privata dell'app.

Questa disponibilità locale **non dimostra che esista già un URL pubblico per scaricare la mappa**. Non aggiungere un pulsante di download cartografico senza una destinazione effettiva. Se la mappa viene distribuita o mostrata, mantenere le attribuzioni pertinenti: **© contributori OpenStreetMap**, ODbL 1.0; la fonte documenta anche Natural Earth. Riferimenti: [OpenStreetMap copyright](https://www.openstreetmap.org/copyright) e [documentazione PMTiles](https://docs.protomaps.com/pmtiles/).

La pagina non deve necessariamente contenere la procedura tecnica di estrazione del pianeta Protomaps: il punto utile per chi usa l'app è procurarsi e importare un file compatibile. Definire dove reperirlo è un elemento della consegna del sito, distinto dal solo collegamento APK.

## 9. Dati, privacy e limiti delle affermazioni

Fatti verificati nel progetto:

- Il manifest dell'APK non contiene il permesso **INTERNET**; non contiene neppure i permessi di lettura dello stato rete/Wi-Fi rimossi dal manifest sorgente.
- Permessi applicativi pertinenti: posizione approssimativa/precisa, servizio in primo piano di tipo posizione, notifiche. La registrazione non ha aggiunto nuovi permessi rispetto alla versione precedente.
- GPX, mappa importata, catalogo, preferenze e punti registrati sono gestiti localmente nella memoria privata dell'app.
- Non risultano account, backend applicativo, sincronizzazione cloud, pubblicità o analytics.
- Il collegamento all'autore apre il browser esterno; la rete eventualmente usata dal browser è distinta dall'app.
- Esportare un GPX è un'azione esplicita: il selettore documenti Android può offrire anche destinazioni esterne/cloud a scelta dell'utente.

Formulare i contenuti privacy con questo perimetro. Evitare promesse assolute come «nessun dato può mai lasciare il telefono»: esportazioni volontarie, servizi del sistema operativo e loro impostazioni hanno un ambito diverso. La pagina web può avere inoltre una propria gestione di font, hosting e privacy, da rispettare senza attribuirle automaticamente le caratteristiche offline dell'app.

Non risultano una licenza pubblica del progetto, un repository pubblico specifico di GPXdev o condizioni commerciali formalizzate in queste fonti. Non affermare automaticamente che l'app sia open source, gratuita per sempre, priva di qualunque futura limitazione o presente sugli store. Il GitHub personale dell'autore non è prova dell'esistenza di un repository pubblico dell'app.

## 10. Funzioni da non attribuire alla versione attuale

- Routing/rerouting stradale, ricerca di una destinazione per indirizzo, generazione automatica di itinerari.
- Indicazioni vocali/TTS.
- Mappe incluse nell'APK, scaricamento automatico delle regioni, aggiornamenti incrementali o automatici della cartografia.
- iOS, Android Auto o app companion per smartwatch.
- Account, cloud, sincronizzazione, community o condivisione automatica su Strava/altre piattaforme.
- Editor di tracce, importazione di qualsiasi formato sportivo, statistiche di allenamento o sensori esterni non documentati.
- Registrazione infallibile, accuratezza garantita al metro, durata batteria misurata o resistenza a qualunque arresto del sistema.
- Esportazione generica di qualsiasi traccia dell'archivio o comando pausa, oltre a quanto descritto nella UI della 1.22.0.

Questi punti servono a evitare contenuti inventati. Non trasformarli necessariamente in un lungo elenco negativo nella pagina: scegliere la forma in base al prompt editoriale, rendendo comunque chiari i limiti che incidono sull'uso.

## 11. Materiali grafici disponibili

Nel progetto Android di origine sono presenti:

```text
C:\Users\gianm\Desktop\navigatore_v1\reference grafica\icone-v1.20.0\proposta-2-bussola-GPXdev.png
C:\Users\gianm\Desktop\navigatore_v1\app\src\main\res\mipmap-xxxhdpi\ic_launcher_foreground.png
C:\Users\gianm\Desktop\navigatore_v1\app\src\main\res\mipmap-anydpi\ic_launcher.xml
C:\Users\gianm\Desktop\navigatore_v1\.tools\launcher-preview-1.20.1.png
```

L'icona attuale è una bussola su fondo scuro con freccia di navigazione rosa fluo, nome GPXdev e piccola firma gdev. Il file in `.tools` è una tavola di anteprima con tre ritagli del launcher: utile come riferimento, non da usare automaticamente come immagine principale della pagina. L'icona adattiva Android combina risorse e fondo: verificare il risultato prima di usare il solo foreground sul web.

Le altre proposte nella cartella grafica sono alternative storiche. Non cambiarne arbitrariamente il marchio e non trattarle come schermate dell'app.

Non sono state individuate in questa analisi schermate pronte e verificate della UI 1.22.0. Se si vogliono mostrare screenshot, procurarli dalla versione reale, in Soft/Cyber e negli orientamenti utili. Non usare schermate generate o di versioni precedenti come prova della funzione REC. Eventuali illustrazioni schematiche devono essere riconoscibili come tali. Non inserire coordinate o tracce personali reali senza sceglierle intenzionalmente per la pubblicazione.

Se Codex lavora solo sul repository del sito, i file Android sopra elencati potrebbero non essere accessibili: trasferire i materiali effettivamente necessari nel progetto web e assegnare loro percorsi pubblici coerenti. Nessun percorso Windows deve finire in un `href` o `src` pubblico.

## 12. Organizzazione dei contenuti e implementazione

Questi sono blocchi informativi da coprire, non un ordine o un formato obbligatorio:

- Identità dell'app e attività che permette di svolgere.
- Navigazione offline su GPX e dati visibili durante il percorso.
- Registrazione del giro e registrazione libera introdotte nella 1.22.0.
- Mappa da importare separatamente e preparazione prima di uscire.
- Compatibilità, download e istruzioni per l'APK da fonti esterne.
- Aggiornamento senza disinstallare.
- Gestione locale dei dati, permessi pertinenti e distinzione dal routing stradale.
- Aspetto Soft/Cyber, immagini disponibili ed eventuali risposte ai dubbi pratici.

Gli elementi essenziali non devono dipendere soltanto da caroselli, hover o JavaScript. Il download deve essere un vero collegamento, utilizzabile anche da tastiera. Su desktop l'APK va comunque scaricato come file Android, senza suggerire che sia un programma Windows/macOS.

Usare HTML semantico, un solo H1, gerarchia dei titoli chiara, focus visibile, descrizioni pertinenti per le immagini e contrasto nei tre temi. Verificare layout mobile e testi ingranditi. Le eventuali animazioni devono rispettare `prefers-reduced-motion`. Evitare dipendenze o framework aggiunti soltanto per questa pagina se il progetto esistente usa HTML/CSS/JS condivisi.

Mantenere ritorno al portfolio, brand, footer e temi. Rimuovere il riquadro UNDER CONSTRUCTION e i relativi riferimenti nei metadati. Valutare rimozione di classi/stili del placeholder soltanto dopo averne verificate le dipendenze.

SEO: title, description, canonical e Open Graph coerenti con `/progetti/gpxdev.html`; immagine social soltanto se esiste un asset adatto. Un eventuale dato strutturato `SoftwareApplication` deve contenere solo dati verificati, per esempio nome, Android, versione e URL. Non aggiungere valutazioni, prezzi, recensioni o date inventate.

Non copiare questo documento tecnico integralmente nella pagina. API level, versionCode, hash del certificato, struttura Room e percorsi sorgenti sono contesto per lavorare correttamente; selezionare cosa è utile al lettore in base al prompt successivo.

## 13. Informazioni ancora da completare nel progetto web

| Elemento | Stato alla preparazione di questo file | Azione per Codex |
| --- | --- | --- |
| URL pubblico dell'APK | **Non fornito e non verificato**; il placeholder web non contiene link di download | Individuare il file nel progetto o collocarlo nel percorso di distribuzione scelto dall'utente/progetto; verificare il collegamento effettivo. |
| URL pubblico del checksum | File locale disponibile; URL non verificato | Pubblicarlo se previsto oppure riportare il checksum corretto in una sezione secondaria. |
| Distribuzione della mappa | Estratto Italia locale disponibile; nessun URL pubblico verificato | Definire una fonte raggiungibile prima di promettere un download diretto della mappa. |
| Screenshot 1.22.0 | Non disponibili tra i materiali verificati | Usare solo screenshot reali e aggiornati se forniti o acquisibili. |
| Repository pubblico GPXdev | Non verificato | Inserire un collegamento solo se individuato e pertinente. |
| Testi finali e target | Saranno indicati dall'utente | Applicare il prompt editoriale senza alterare i fatti tecnici. |

Usare durante la lavorazione, se necessario, identificatori espliciti come `APK_DOWNLOAD_URL`, `APK_SHA256_URL` e `MAP_DOWNLOAD_URL`. **Non sono indirizzi reali** e non devono arrivare in produzione come collegamenti attivi. Non inventare un URL plausibile fingendo che sia stato verificato. Se il binario non è disponibile nel checkout, completare il lavoro indipendente e segnalare con precisione l'unico materiale mancante.

Per il download preferire una destinazione HTTPS stabile e coerente con il sito, con nome file riconoscibile. Evitare URL temporanei o pagine HTML al posto del binario. Se si controlla l'hosting, verificare la corretta consegna dell'APK come file (`application/vnd.android.package-archive` o tipo binario appropriato), senza alterarne firma e contenuto. Il checksum di questo documento vale esclusivamente per il pacchetto qui identificato.

## 14. Verifica prima della consegna della pagina

- La pagina completata è raggiungibile all'indirizzo già collegato nel portfolio e non mostra più il placeholder.
- Light, Dark ed EVA01 mantengono aspetto, contrasto, comandi e persistenza coerenti con il resto del sito.
- Non ci sono regressioni sui CSS/JS condivisi; su mobile non compaiono overflow o comandi inaccessibili.
- È chiara la differenza tra seguire un GPX, registrare un giro e consultare una mappa.
- La funzione di registrazione è quella della 1.22.0, con salvare/scartare ed esportazione nel punto corretto del flusso.
- Sono espliciti Android 10+, installazione APK e autorizzazione della fonte usata per aprire il file.
- La pagina chiarisce che le mappe non sono incluse e non promette routing/rerouting o istruzioni vocali.
- Il pulsante scarica davvero l'APK atteso: niente 404, redirect a login, HTML di errore, `href="#"` o placeholder. Dimensione e hash corrispondono al file distribuito.
- Collegamenti interni, ritorno a Progetti, eventuali immagini e metadati usano percorsi corretti dal sottopercorso `/progetti/`.
- Eventuali nuovi collegamenti a mappa, sorgenti e screenshot sono reali e verificati; non ci sono promesse commerciali, recensioni o risultati di test inventati.

Usare i controlli appropriati del repository web e verificare visivamente la pagina. Riferire ciò che è stato davvero controllato; non dichiarare pubblicato un sito solo perché sono stati creati i file locali.

## 15. Fonti del progetto e attendibilità delle verifiche

Radice del progetto Android analizzato: `C:\Users\gianm\Desktop\navigatore_v1`.

| Fonte, relativa a questa radice | Informazioni principali |
| --- | --- |
| `app/build.gradle.kts` e metadati dell'APK con `aapt2` | Versione 1.22.0, code 38, Android minimo, nome, build debug e architetture. |
| `app/src/main/AndroidManifest.xml` e permessi estratti dall'APK | Posizione, servizio e notifiche; assenza di permesso Internet. |
| `app/src/main/assets/CHANGELOG.txt` | Evoluzione funzionale; prima voce dedicata alla registrazione 1.22.0. |
| `1.22.0-test-telefono.txt` | Flussi della nuova funzione, verifiche automatiche riportate e prove fisiche ancora da eseguire. |
| `README.md`, `ARCHITECTURE.md`, `V1-1.21.0.txt` | Base consolidata 1.21.0: navigazione, mappe, temi e limiti. Non completi per le novità 1.22.0. |
| `app/src/main/res/values/strings.xml` | Nomi reali dei pulsanti, avvisi e stati. |
| `data/gpx/GpxParser.kt`, `data/gpx/GpxWriter.kt` | GPX supportato e contenuto dell'esportazione. |
| `data/navigation/RecordingRepository.kt`, `navigation/RecordingAccumulator.kt`, `navigation/NavigationController.kt` | Registrazione, filtri, salvataggio progressivo, recupero e sessione. |
| `ui/components/RecordingDecision.kt`, `ui/components/RecordingViewModel.kt` | Dialogo Sì/No ed esportazione dal messaggio di conferma. |
| `data/maps/OfflineMapRepository.kt`, `ui/map/OfflineMapStyle.kt` | Importazione privata, compatibilità cartografica, tile e font locali. |
| `ui/map/RecordingOverlay.kt`, `ui/theme/NavigatorTheme.kt`, UI Dashboard e navigazione | Aspetto, percorso blu, temi e adattamento agli orientamenti. |
| `offline-maps/README.md`, `offline-maps/italia-2026-10-25.SOURCE.txt` | Origine, dimensione, spazio richiesto e attribuzioni della mappa Italia. |
| `.tools/1.22.0-build-test.log`, `app/build/test-results/testDebugUnitTest/`, report lint | Riscontri della preparazione locale del pacchetto. |
| `app-1.22.0.apk.sha256` e hash ricalcolato | Integrità del pacchetto descritto. |

I percorsi abbreviati `data/`, `navigation/` e `ui/` della tabella sono sotto `app/src/main/java/com/gpxnavigator/`.

Durante questa analisi sono stati eseguiti di nuovo: lettura dei metadati e dei permessi dell'APK, verifica delle firme delle versioni 1.21.0/1.22.0, calcolo SHA-256 e conteggio dei report dei test esistenti. **Non è stata eseguita una nuova build né una nuova sessione di test Android.**

Il log di preparazione 1.22.0 riporta `assembleDebug`, `test` e `lintDebug` completati; i report esistenti contengono **212 test, zero fallimenti, errori o test saltati**. Rimane l'avviso facoltativo sull'icona launcher monocromatica. La guida 1.22.0 specifica che le prove fisiche su telefono per layout, GPS e gestione energetica sono ancora da eseguire. Non trasformare questi riscontri in dichiarazioni pubbliche di collaudo su tutti i dispositivi.

Per l'architettura, solo se utile al contenuto richiesto: Kotlin, Jetpack Compose/Material 3, MVVM, StateFlow, coroutine, Room, FusedLocationProviderClient, MapLibre Native e PMTiles. Questi dettagli non devono dominare la pagina se il prompt editoriale non li richiede.

Le osservazioni del sito derivano dalla consultazione reale in browser della home, del portfolio e della pagina GPXdev, incluso il DOM e i CSS caricati. Il primo accesso tramite strumenti web/HTTP non riusciva a raggiungere il dominio; il browser ha consentito l'analisi diretta. Al momento dell'implementazione rileggere il checkout del sito perché contenuti e asset potrebbero essere cambiati.
