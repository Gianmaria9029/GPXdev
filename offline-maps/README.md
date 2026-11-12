# Mappe offline Italia

Ogni versione della mappa è un archivio PMTiles autonomo, importabile nell'app tramite **Scegli mappa PMTiles**. Il file `italia-AAAA-MM-GG.SOURCE.txt` riporta fonte, data effettiva dei dati OpenStreetMap, dimensione, SHA-256 e verifiche eseguite.

Le date nel nome identificano l'archivio Protomaps di origine. Non indicano la data dell'ultima modifica di ogni strada. Sicilia, Sardegna e isole minori rientrano nel contorno Italia usato per l'estrazione; ai bassi zoom le tile comprendono anche aree esterne al contorno.

## Installazione sul telefono

1. Copiare il file `.pmtiles` nella cartella Download del telefono, tramite USB.
2. Aprire Navigazione e premere **Scegli mappa PMTiles**.
3. Selezionare il nuovo file e attendere la fine dell'importazione.
4. Verificare la mappa in modalità aereo con GPS attivo.

L'app crea una copia privata della mappa. Per una prima importazione prevedere circa 5,4 GB per file scaricato e copia privata. Durante un aggiornamento che conserva la precedente mappa importata può servire spazio aggiuntivo equivalente alla vecchia mappa (circa 8,1 GB complessivi con due versioni da 2,7 GB). Terminata l'importazione, la copia nella cartella Download può essere rimossa. La mappa precedente sul PC resta disponibile per tornare alla versione precedente.

## Preparazione di un aggiornamento

Eseguire dalla cartella principale del progetto in PowerShell, con rete disponibile. La navigazione nell'app continua a usare soltanto file locali.

1. Consultare l'[elenco ufficiale degli archivi](https://maps.protomaps.com/builds/) e scegliere una data disponibile.
2. Leggere i metadati e verificare `planetiler:osm:osmosisreplicationtime`, versione e layer prima di scaricare. L'app attuale accetta i layer Protomaps `earth`, `roads`, `water` e compressione Gzip.
3. Estrarre l'Italia in un nuovo file `.partial`, senza sovrascrivere versioni precedenti. L'esempio riproduce la versione del 25 ottobre 2026; sostituire entrambe le date per un aggiornamento successivo.

```powershell
$pmtiles = '.\.tools\go-pmtiles-1.31.2\pmtiles.exe'
$source = 'https://build.protomaps.com/20261025.pmtiles'
$target = '.\offline-maps\italia-2026-10-25.pmtiles'
if ((Test-Path -LiteralPath $target) -or (Test-Path -LiteralPath "$target.partial")) {
    throw 'Esiste gia un file con questa data: scegliere un nuovo nome o controllare quello presente.'
}
& $pmtiles show $source
if ($LASTEXITCODE -ne 0) { throw 'Lettura della fonte fallita.' }
& $pmtiles extract $source "$target.partial" --region=sample-data/italy.geojson --download-threads=12 --overfetch=0.005
if ($LASTEXITCODE -ne 0) { throw 'Estrazione fallita: non importare il file parziale.' }
& $pmtiles verify "$target.partial"
if ($LASTEXITCODE -ne 0) { throw 'Verifica archivio fallita.' }
& $pmtiles show "$target.partial"
if ($LASTEXITCODE -ne 0) { throw 'Lettura dei metadati fallita.' }
Move-Item -LiteralPath "$target.partial" -Destination $target
Get-FileHash -LiteralPath $target -Algorithm SHA256
```

4. Registrare un nuovo `.SOURCE.txt` con i risultati, la dimensione e il comando usato. Prima dell'importazione controllare anche tile campione a zoom di dettaglio, includendo terraferma, Sicilia e Sardegna: `verify` verifica la struttura, non tutti i contenuti delle tile.
5. Copiare e importare la nuova mappa sul telefono. L'aggiornamento richiede l'intero nuovo archivio; l'app non applica aggiornamenti incrementali.

Il contorno riproducibile è `sample-data/italy.geojson`, già derivato da [Geofabrik Italia](https://download.geofabrik.de/europe/italy.poly). I file `.pmtiles` sono esclusi da Git.

Fonti: [download Protomaps](https://docs.protomaps.com/basemaps/downloads), [comandi PMTiles](https://docs.protomaps.com/pmtiles/cli). Dati © contributori OpenStreetMap, [ODbL 1.0](https://www.openstreetmap.org/copyright).
