Questo progetto è stato svolto per l'esame di Laboratorio 3 presso l'Università di Pisa nella facoltà di Informatica superato con la valutazione di 30 e lode.

Lo scopo del progetto è stato di creare un applicazione client-server che simulasse il gioco "Connections" presente sul sito del New York Times.
I requisiti implementativi richiedevano l'utilizzo di Java NIO per i Client, trasmissione di messaggi JSON tra client e server, salvataggio di dati su file persistenti in formato .json, l'utilizzo di Java RMI e un servizio di notifiche asincrone tramite il protocollo UDP.
Il client implementa una TUI per favorire la navigabilità.
Ulteriori dettagli implementativi e guida all'utilizzo sono presenti nella Relazione.

Struttura del progetto:
  - src: contiene i file sorgente, suddivisi tra client, server e cartella comune.
  - lib: contiene librerie aggiuntive, ad esempio un file .jar per il parsing JSON tramite la libreria gson.
  - resources: contiene i file persistenti, .properties per i client e il server, un pool di partite.
