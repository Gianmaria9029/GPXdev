# Mappe offline Italia

Ogni versione della mappa è un archivio PMTiles autonomo, importabile nell'app tramite **Scegli mappa PMTiles**. Il file `italia-AAAA-MM-GG.SOURCE.txt` riporta fonte, data effettiva dei dati OpenStreetMap, dimensione, SHA-256 e verifiche eseguite.

Le date nel nome identificano l'archivio Protomaps di origine. Non indicano la data dell'ultima modifica di ogni strada. Sicilia, Sardegna e isole minori rientrano nel contorno Italia usato per l'estrazione; ai bassi zoom le tile comprendono anche aree esterne al contorno.

## Installazione sul telefono

1. Copiare il file `.pmtiles` nella cartella Download del telefono, tramite USB.
2. Aprire **Esplora mappa** e premere **Scegli mappa PMTiles**.
3. Selezionare il nuovo file e attendere la fine dell'importazione.
4. Verificare la mappa in modalità aereo con GPS attivo.

L'app crea una copia privata della mappa. Per una prima importazione prevedere circa 5,4 GB per file scaricato e copia privata. Durante un aggiornamento che conserva la precedente mappa importata può servire spazio aggiuntivo equivalente alla vecchia mappa (circa 8,1 GB complessivi con due versioni da 2,7 GB). Terminata l'importazione, la copia nella cartella Download può essere rimossa. La mappa precedente sul PC resta disponibile per tornare alla versione precedente.

## Preparazione di un aggiornamento

Scaricare `pmtiles` dalle [release ufficiali di Protomaps](https://github.com/protomaps/go-pmtiles/releases), scegliendo il pacchetto per il proprio sistema e processore. Estrarre l'eseguibile (`pmtiles.exe` su Windows), aggiungere la sua cartella al `PATH` e aprire un nuovo terminale. Il comando `pmtiles --help` mostra i sottocomandi disponibili. Per usare la stessa versione impiegata nell'estrazione originale, scaricare [go-pmtiles 1.31.2](https://github.com/protomaps/go-pmtiles/releases/tag/v1.31.2).

Eseguire da PowerShell, nella radice del repository, con connessione a Internet.

1. Consultare l'[elenco degli archivi Protomaps](https://maps.protomaps.com/builds/) e scegliere una data disponibile.
2. Controllare la data dei dati (`planetiler:osm:osmosisreplicationtime`), la versione e i layer della fonte. L'archivio vettoriale usato qui contiene i layer Protomaps `earth`, `roads` e `water` e usa la compressione Gzip.
3. Estrarre l'Italia in un file temporaneo e verificare l'archivio prima di rinominarlo. L'esempio usa la mappa del 25 ottobre 2026. Per usare una data diversa, aggiornare l'URL e il nome di destinazione.

```powershell
$source = 'https://build.protomaps.com/20261025.pmtiles'
$target = 'offline-maps/italia-2026-10-25.pmtiles'
$partial = [System.IO.Path]::ChangeExtension($target, '.partial.pmtiles')
if ((Test-Path -LiteralPath $target) -or (Test-Path -LiteralPath $partial)) {
    throw 'Esiste gia un file con questa data. Controllarlo o scegliere un altro nome.'
}

pmtiles show $source
if ($LASTEXITCODE -ne 0) { throw 'Lettura della fonte fallita.' }
pmtiles extract $source $partial --region=sample-data/italy.geojson --download-threads=12 --overfetch=0.005
if ($LASTEXITCODE -ne 0) { throw 'Estrazione fallita. Controllare il file temporaneo.' }
pmtiles verify $partial
if ($LASTEXITCODE -ne 0) { throw 'Verifica archivio fallita.' }
pmtiles show $partial
if ($LASTEXITCODE -ne 0) { throw 'Lettura dei metadati fallita.' }

Move-Item -LiteralPath $partial -Destination $target -ErrorAction Stop
Get-FileHash -LiteralPath $target -Algorithm SHA256
```

4. Registrare un nuovo `.SOURCE.txt` con i risultati, la dimensione e il comando usato. Prima dell'importazione controllare anche tile campione a zoom di dettaglio, includendo terraferma, Sicilia e Sardegna: `verify` verifica la struttura, non tutti i contenuti delle tile.
5. Copiare e importare la nuova mappa sul telefono. L'aggiornamento richiede l'intero nuovo archivio; l'app non applica aggiornamenti incrementali.

Il contorno riproducibile è `sample-data/italy.geojson`, già derivato da [Geofabrik Italia](https://download.geofabrik.de/europe/italy.poly). Gli archivi PMTiles dell'Italia sono esclusi da Git. La mappa di Firenze in `sample-data/` serve ai test automatici.

Fonti: [download Protomaps](https://docs.protomaps.com/basemaps/downloads), [comandi PMTiles](https://docs.protomaps.com/pmtiles/cli). Dati © contributori OpenStreetMap, [ODbL 1.0](https://www.openstreetmap.org/copyright).
