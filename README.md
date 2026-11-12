# GPXdev - Navigazione GPX offline

**Segui un percorso. Registra il tuo giro.** GPXdev è un'app gratuita per Android per navigare offline seguendo tracce GPX **in moto, in bici e a piedi**. Importa la traccia e la mappa prima di partire, poi segui il percorso con il GPS del telefono. Puoi iniziare senza creare un account.

**[Scarica GPXdev per Android ↓](https://gianmaria.dev/download/gpxdev/gpxdev_1.37.0.apk)**

Versione **1.37.0** · APK · Android 10 o successivo

![GPXdev in orizzontale: la mappa e i dati del giro nel tema Cyber](https://gianmaria.dev/assets/img/gpxdev/gpxdev-orizzontale.webp)

Dati cartografici © [contributori OpenStreetMap](https://www.openstreetmap.org/copyright).

## Tre modi per usare GPXdev

- **Esplora la mappa.** Guarda dove sei, ingrandisci la zona che ti interessa e scegli se tenere il nord in alto o ruotare la mappa nel senso di marcia.
- **Segui una traccia GPX.** Importa il percorso e avvia la navigazione. Durante il giro vedi velocità, distanza rimanente e indicazioni sulle curve. Se ti allontani dalla traccia, l'app ti avvisa.
- **Registra il tuo giro.** Parti anche senza un itinerario, salva la registrazione ed esportala come GPX. La modalità Cockpit mette in primo piano velocità, distanza, durata e quota.

Puoi ritrovare le tracce con la ricerca, organizzarle con i preferiti e scegliere tra i temi Soft e Cyber.

## Installa l'app e prepara il giro

1. **Installa l'APK.** Apri il file scaricato sul telefono e segui le indicazioni di Android. Se richiesto, abilita **Consenti da questa fonte** per il browser o il gestore file con cui hai aperto l'APK, poi torna all'installazione.
2. **Aggiungi la mappa.** La mappa si scarica a parte. [Scarica la mappa PMTiles dell'Italia da Google Drive](https://drive.google.com/file/d/1cIIloPkRK6lavCgmUsWR27BdaB9i6mbb/view?usp=drive_link), poi apri **Esplora mappa → Scegli mappa PMTiles** e seleziona il file. L'app ne crea una copia nella propria memoria.
3. **Scegli come partire.** Importa un GPX e tocca **Avvia navigazione** per seguire un percorso, oppure scegli **Registrazione libera**. Quando Android te lo chiede, consenti l'accesso alla posizione precisa.

[Visita la pagina di GPXdev su gianmaria.dev](https://gianmaria.dev/progetti/gpxdev.html) per vedere altre schermate e la guida all'installazione.

<details>
<summary>Compila l'app dal sorgente</summary>

Configura JDK 17 e Android SDK con la piattaforma API 36 e i Build Tools 36.0.0. Imposta `JAVA_HOME` e indica il percorso dell'SDK tramite Android Studio o con `sdk.dir` nel file locale `local.properties`. Alla prima compilazione, Gradle scarica le dipendenze.

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat testDebugUnitTest lintDebug
```

Trovi l'APK di sviluppo in `app/build/outputs/apk/debug/app-debug.apk`.

[Architettura](ARCHITECTURE.md) · [Preparazione delle mappe](offline-maps/README.md) · [Changelog](app/src/main/assets/CHANGELOG.txt) · [Licenza MIT](LICENSE)

</details>

## Schermate in verticale

| Dashboard | Navigazione |
| :---: | :---: |
| <img src="https://gianmaria.dev/assets/img/gpxdev/gpxdev-dashboard-cyber.webp" alt="Dashboard di GPXdev in verticale, tema Cyber" width="280"> | <img src="https://gianmaria.dev/assets/img/gpxdev/gpxdev-navigazione.webp" alt="Navigazione GPX in verticale, tema Cyber" width="280"> |

La schermata della dashboard mostra la versione 1.35.1.
