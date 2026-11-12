# GPXdev

GPXdev è un navigatore Android per seguire tracce GPX con il GPS e mappe offline. La versione del codice è **1.37.0**. Richiede Android 10 o successivo.

- Importa, cerca, ordina e rinomina le tracce GPX. Puoi anche contrassegnarle come preferite.
- Segui una traccia su una mappa PMTiles locale. L'app segnala le curve e quando ti allontani dal percorso. Le indicazioni seguono la traccia importata.
- Registra ed esporta un giro in GPX. La modalità **Cockpit** mostra velocità, distanza, durata e quota durante la registrazione.
- Scegli tra i temi Soft e Cyber e tra mappa chiara e scura.

L'app usa il GPS del telefono e i file già importati. Tracce, registrazioni e mappe restano nella memoria dell'app.

## Primo utilizzo

1. Trasferisci sul telefono una traccia GPX e una mappa `.pmtiles` compatibile.
2. Da **Impostazioni → Mappe offline**, importa la mappa. L'app ne crea una copia: verifica lo spazio disponibile prima di importarla.
3. Importa il GPX dalla Home, apri la traccia e avvia la navigazione. Concedi il permesso di posizione quando Android lo chiede.

La mappa va importata separatamente dall'APK. La [guida alle mappe offline](offline-maps/README.md) spiega come preparare una mappa dell'Italia.

## Compilazione e test

Servono JDK 17 e Android SDK con la piattaforma API 36 e i Build Tools 36.0.0. Imposta `JAVA_HOME` sul JDK e indica il percorso dell'SDK in Android Studio o nel file locale `local.properties` con `sdk.dir`. Per la prima compilazione Gradle deve poter scaricare le dipendenze.

Da PowerShell, nella cartella del progetto:

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat testDebugUnitTest lintDebug
```

L'APK di sviluppo viene generato in `app/build/outputs/apk/debug/app-debug.apk`. Puoi installarlo su un telefono collegato con `adb`, incluso negli Android SDK Platform Tools:

```powershell
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Per aggiornare un'installazione esistente, il nuovo APK deve avere la stessa firma di quello già installato.

GPXdev usa Kotlin, Jetpack Compose, Room e MapLibre Native. Per approfondire, consulta l'[architettura](ARCHITECTURE.md), il [changelog](app/src/main/assets/CHANGELOG.txt) e la [licenza MIT](LICENSE).
