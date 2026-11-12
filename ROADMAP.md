# Roadmap V1 — GPXdev

## Stato consolidato — 1.21.0

La prima versione è consolidata in **GPXdev 1.21.0** (versionCode 37). Le milestone M0–M11 sotto riportate descrivono il percorso iniziale; le estensioni successive sono registrate nel changelog dell'app.

| Milestone | Stato del progetto |
| --- | --- |
| M0 — Bootstrap | Implementata: build Kotlin/Compose da CLI su Windows |
| M1 — UI | Implementata: Dashboard e navigazione |
| M2 — MapLibre | Implementata: rendering mappa e overlay |
| M3 — PMTiles offline | Implementata: importazione privata e mappa senza rete |
| M4 — Import GPX | Implementata: selettore Android e catalogo di più tracce |
| M5 — Parser | Implementata: segmenti multipli e campi opzionali, con test |
| M6 — Traccia | Implementata: rendering segmenti, panoramica e progresso confermato |
| M7 — GPS | Implementata: permessi e stati di disponibilità |
| M8 — Follow | Implementata: freccia unica, visuale e zoom persistenti |
| M9 — Metriche | Implementata: distanze, velocità, Vmax, quota e arrivo |
| M10 — Robustezza | Implementata: gestione errori, rotazione, background e test automatici |
| M11 — APK V1 | Completata: APK 1.21.0, build e 196 test PASS; firma verificata. Controlli telefono in `1.21.0-test-telefono.txt` |

I test sul telefono della 1.20.1 sono stati confermati dall'utente. Il pacchetto 1.21.0 raccoglie documentazione e pulizia; le verifiche sul nuovo APK restano nella relativa guida, senza attribuire alla nuova build prove fisiche eseguite sulle precedenti.

### Estensioni già sviluppate e approvate

- Archivio GPX persistente, protezione della traccia attiva e ripresa della sessione interrotta.
- Servizio GPS con notifica, navigazione a schermo spento ed esplorazione legata alla visibilità della mappa.
- Riconoscimento delle curve dalla geometria GPX, seconda manovra e avviso fuori traccia.
- Stima di arrivo, Vmax e indicazione del tratto percorso.
- Temi Soft/Cyber, Dashboard adattiva, sidebar landscape e controlli condivisi.
- Nome GPXdev, icona bussola e link sviluppatore nel browser esterno.

Queste estensioni non comprendono un motore di routing stradale o un ricalcolo del percorso.

### Manutenzione di chiusura 1.21.0

- Documentazione allineata alle funzioni e ai nomi attuali.
- Rimozione delle risorse inutilizzate e della vecchia cartella icona vuota.
- Pulizia del manifest e degli stati interi Compose.
- Plurali delle descrizioni accessibili corretti e avvisi intenzionali documentati.
- Nessuna nuova dipendenza o permesso.

---

## Regola

Implementare una milestone alla volta.

Ogni milestone deve terminare con:

1. compilazione riuscita;
2. test automatici pertinenti eseguiti;
3. riepilogo delle modifiche;
4. nessun errore noto bloccante.

Non iniziare automaticamente la milestone successiva se la milestone corrente non compila.

---

# M0 — Bootstrap progetto

## Obiettivo

Creare un progetto Android minimale compilabile da CLI.

## Requisiti

- Kotlin;
- Gradle;
- Jetpack Compose;
- namespace applicazione definito;
- minSdk coerente con Android 10+;
- nessuna dipendenza inutile.

## Done

```bash
gradlew.bat assembleDebug
```

deve completarsi correttamente su Windows.

---

# M1 — UI base

## Obiettivo

Creare la struttura minima dell'app.

Schermate:

- Home / Import GPX;
- Navigation.

## Done

L'app si avvia sul dispositivo e mostra la schermata iniziale.

---

# M2 — MapLibre

## Obiettivo

Integrare MapLibre nella schermata Navigation.

## Done

Una mappa viene renderizzata correttamente.

Nessuna logica GPX richiesta in questa milestone.

---

# M3 — Mappa offline PMTiles

## Obiettivo

Caricare una mappa da storage locale.

## Requisiti

- nessuna richiesta tile via Internet;
- gestione errore file mancante;
- percorso file configurabile.

## Done

La mappa continua a funzionare con rete disabilitata.

---

# M4 — Import GPX

## Obiettivo

Permettere la selezione di un file GPX dal file picker Android.

## Done

L'app riceve correttamente URI e contenuto del file selezionato.

---

# M5 — Parser GPX

## Obiettivo

Convertire il GPX in `Track`.

Supportare:

- trk;
- trkseg;
- trkpt;
- ele opzionale;
- time opzionale.

## Test

Creare test per:

- GPX valido;
- più segmenti;
- GPX senza elevation;
- GPX vuoto;
- GPX malformato.

---

# M6 — Rendering traccia

## Obiettivo

Visualizzare il GPX sulla mappa.

## Requisiti

- polyline;
- camera adattata alla traccia;
- supporto a più segmenti.

## Done

Una traccia di test viene visualizzata interamente.

---

# M7 — GPS

## Obiettivo

Integrare posizione reale del dispositivo.

## Requisiti

- gestione permessi;
- posizione corrente;
- accuratezza;
- velocità se disponibile;
- bearing se disponibile.

## Done

La posizione del dispositivo viene aggiornata in tempo reale.

---

# M8 — Follow mode

## Obiettivo

Seguire automaticamente il dispositivo.

Funzioni:

- centra posizione;
- abilita/disabilita follow;
- aggiorna camera senza movimenti eccessivamente bruschi.

## Done

La mappa segue il dispositivo durante un test reale.

---

# M9 — Metriche traccia

## Obiettivo

Mostrare:

- distanza totale;
- distanza percorsa;
- distanza rimanente;
- velocità corrente;
- quota corrente, se disponibile.

## Nota

Per la V1 è accettabile una logica semplice e deterministica per il progresso, purché separata dalla UI.

---

# M10 — Robustezza

## Obiettivo

Gestire errori e casi limite.

Testare:

- GPX molto corto;
- GPX molto lungo;
- GPS non disponibile;
- permessi negati;
- mappa mancante;
- cambio orientamento;
- app in background e ritorno in foreground.

---

# M11 — Build finale V1

## Obiettivo

Generare APK debug stabile.

Comando:

```powershell
gradlew.bat assembleDebug
```

Verificare:

- installazione tramite ADB;
- avvio;
- import GPX;
- mappa offline;
- GPS;
- follow;
- metriche.

---

# Fuori dalla prima versione

Attività non comprese nel rilascio consolidato:

- routing stradale e navigazione turn-by-turn basata su un motore stradale;
- TTS;
- rerouting;
- routing engine;
- map matching avanzato;
- download automatico regioni;
- cloud;
- account;
- CarPlay;
- Android Auto;
- Wear OS;
- iOS;
- variante monocromatica dell’icona e preparazione dedicata alla distribuzione su store.
