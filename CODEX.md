# Codex Instructions

Queste regole sono vincolanti per ogni modifica al repository.

---

# 1. Obiettivo del progetto

Stiamo sviluppando una app Android per navigare tracce GPX offline.

La V1 deve essere semplice, stabile e completamente utilizzabile senza rete durante la navigazione.

Non espandere autonomamente lo scope.

---

# 2. Stack obbligatorio

Usare:

- Kotlin;
- Jetpack Compose;
- Gradle;
- MapLibre Native Android;
- PMTiles;
- Android SDK;
- FusedLocationProviderClient;
- Kotlin Coroutines;
- StateFlow;
- MVVM.

Non sostituire lo stack senza richiesta esplicita.

---

# 3. Ambiente

Il progetto viene sviluppato senza Android Studio.

Assumere:

- Windows;
- VS Code;
- terminale PowerShell;
- Android SDK Command Line Tools;
- ADB;
- Codex.

Ogni comando deve poter essere eseguito da terminale.

Quando proponi comandi Windows, preferisci sintassi PowerShell compatibile.

---

# 4. Build obbligatoria

Dopo modifiche significative eseguire:

```powershell
gradlew.bat assembleDebug
```

Se sono presenti test:

```powershell
gradlew.bat test
```

Non dichiarare completata una modifica se il progetto non compila.

Se la build fallisce:

1. analizzare l'errore;
2. correggere la causa;
3. rilanciare la build;
4. riportare eventuali problemi residui.

---

# 5. Scope V1

Implementare solo:

- import GPX;
- parsing GPX;
- rendering traccia;
- mappe offline;
- GPS;
- follow location;
- distanza totale;
- distanza percorsa;
- distanza rimanente;
- velocità;
- quota.

Non implementare senza richiesta:

- routing;
- rerouting;
- turn-by-turn;
- TTS;
- account;
- login;
- server;
- backend;
- cloud;
- analytics;
- advertising;
- sincronizzazione;
- Android Auto;
- iOS.

---

# 6. Offline-first

La navigazione deve funzionare senza Internet.

Durante la navigazione:

- non effettuare richieste HTTP;
- non dipendere da API remote;
- non caricare tile online;
- non richiedere autenticazione;
- non usare SDK proprietari che richiedano servizi cloud.

Mappe, GPX e dati necessari devono essere locali.

---

# 7. Architettura

Seguire `ARCHITECTURE.md`.

Separare:

- data;
- domain;
- location;
- navigation;
- ui.

Non mettere logica dominio nei Composable.

Non mettere parsing GPX dentro ViewModel se può essere isolato in parser/repository.

Non mettere accesso GPS direttamente nella UI.

---

# 8. Dimensione modifiche

Preferire modifiche piccole e verificabili.

Per ogni task:

1. leggere i file coinvolti;
2. modificare solo ciò che serve;
3. evitare refactoring non richiesti;
4. eseguire build/test;
5. descrivere brevemente cosa è cambiato.

Non riscrivere interi file se bastano modifiche locali.

---

# 9. Dipendenze

Non aggiungere librerie senza necessità.

Quando aggiungi una dipendenza:

- specificare quale problema risolve;
- verificare che sia necessaria;
- evitare librerie obsolete;
- evitare duplicati;
- evitare SDK cloud.

Non aggiungere una libreria GPX se il parsing richiesto è sufficientemente semplice da implementare in modo affidabile con parser XML standard, salvo vantaggio concreto.

---

# 10. Qualità codice

Richieste:

- nomi chiari;
- responsabilità singole;
- nullability esplicita;
- immutabilità dove possibile;
- funzioni brevi quando ragionevole;
- niente magic numbers non documentati;
- niente codice morto;
- niente TODO generici non spiegati.

Preferire codice leggibile a astrazioni premature.

---

# 11. Commenti

Aggiungere commenti solo quando spiegano:

- una scelta tecnica;
- un workaround;
- un algoritmo non ovvio;
- un vincolo Android.

Non commentare codice autoesplicativo.

---

# 12. Error handling

Non ignorare eccezioni.

Evitare:

```kotlin
catch (e: Exception) {
}
```

Gestire almeno:

- GPX invalido;
- file non accessibile;
- mappa mancante;
- permesso GPS negato;
- GPS disabilitato;
- posizione temporaneamente non disponibile.

La UI deve ricevere errori in forma controllata.

---

# 13. GPS

Non assumere che:

- bearing sia sempre disponibile;
- velocità sia sempre disponibile;
- quota sia sempre disponibile;
- accuratezza sia sempre buona.

Gestire valori mancanti.

Non inventare dati.

---

# 14. GPX

Considerare GPX con:

- uno o più `trk`;
- uno o più `trkseg`;
- molti `trkpt`;
- elevation mancante;
- timestamp mancante.

Non appiattire automaticamente segmenti separati se questo modifica semanticamente la traccia.

---

# 15. Test

Dare priorità a test deterministici per:

- parser GPX;
- calcoli geografici;
- distanze;
- progresso traccia.

Non creare test fittizi solo per aumentare il numero di test.

---

# 16. Sicurezza file

Usare correttamente URI e Storage Access Framework Android.

Non assumere che un URI Android corrisponda sempre a un path filesystem tradizionale.

Non richiedere permessi storage più ampi del necessario.

---

# 17. UI

La V1 deve avere UI funzionale e minimale.

Priorità:

1. leggibilità;
2. informazioni essenziali;
3. facilità d'uso durante movimento;
4. stabilità.

Non introdurre animazioni o componenti grafici complessi se non necessari.

---

# 18. Performance

Le tracce GPX possono contenere migliaia di punti.

Evitare:

- elaborazioni pesanti ad ogni recomposition;
- ricostruzione completa della polyline ad ogni aggiornamento GPS;
- calcoli O(n) inutili ad ogni frame;
- accesso disco nel main thread.

Usare coroutine per I/O e parsing.

---

# 19. Git

Ogni milestone dovrebbe poter corrispondere a un commit autonomo.

Non mischiare feature indipendenti nello stesso task.

Messaggi commit suggeriti:

```text
feat: add GPX import
feat: add offline map support
feat: render GPX track
feat: add location tracking
feat: add navigation metrics
fix: handle invalid GPX files
```

Non eseguire operazioni Git distruttive senza richiesta esplicita.

---

# 20. Output richiesto a Codex

Al termine di ogni task riportare sinteticamente:

```text
Modifiche:
- ...

File modificati:
- ...

Build:
- PASS / FAIL

Test:
- PASS / FAIL / non presenti

Problemi residui:
- ...
```

Non dichiarare `PASS` senza aver eseguito il comando corrispondente.

---

# 21. Priorità

In caso di conflitto:

1. requisiti espliciti dell'utente;
2. `CODEX.md`;
3. `ARCHITECTURE.md`;
4. `ROADMAP.md`;
5. `README.md`.

Non modificare questi file per adattarli automaticamente al codice.

Se il codice richiede una deviazione architetturale, segnalarla prima di applicarla.
