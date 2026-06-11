package client;

import java.io.IOException;

import com.google.gson.stream.JsonReader;

import common.Error;

/*
 * Tutti i metodi di stampa dei messaggi ricevuti dal server
 * sono presenti in questa classe.
 */

public class Printer {
    public int printSimpleReply(JsonReader jreader) {
        try {
            while (jreader.hasNext()) {
                String name = jreader.nextName();
                if (name.equals("err_code")) {
                    int errCode = jreader.nextInt();
                    printErrorMessage(errCode);
                    return errCode;
                } else {
                    jreader.skipValue();
                }
            }
        } catch (IOException ex) {
        	System.err.println("Impossibile leggere la risposta del server");
        }
        return -1;
    }

    public void printLoginInfo(JsonReader jreader) {
        processGenericObject(jreader, "Login");
    }

    public void printGameInfo(JsonReader jreader) {
        processGenericObject(jreader, "Game Info");
    }

    private void processGenericObject(JsonReader jreader, String context) {
        try {
            while (jreader.hasNext()) {
                String name = jreader.nextName();
                switch (name) {
                    case "game":        printGameBlock(jreader); break;
                    case "playerStats": printPlayerStatsBlock(jreader); break;
                    case "groups":      printGroupsBlock(jreader); break;
                    case "scoreboard":  printScoreboardArray(jreader); break;
                    case "err_code":    printErrorMessage(jreader.nextInt()); break;
                    default:            jreader.skipValue(); break;
                }
            }
        } catch (IOException ex) {
        	System.err.println("Impossibile leggere la risposta del server");
        }
    }

    public void printGameBlock(JsonReader jreader) throws IOException {
        jreader.beginObject();
        while (jreader.hasNext()) {
            String name = jreader.nextName();
            switch (name) {
                case "remainingTime": printFormattedTime(jreader.nextLong()); break;
                case "words":         printWordsGrid(jreader); break;
                case "gameId":        ClientMain.currentGameID = jreader.nextInt(); break;
                default:              jreader.skipValue(); break;
            }
        }
        jreader.endObject();
    }

    public void printPlayerStatsBlock(JsonReader jreader) throws IOException {
        System.out.println("\n--- STATISTICHE PARTITA ---");
        jreader.beginObject();
        while (jreader.hasNext()) {
            String name = jreader.nextName();
            switch (name) {
                case "points":         System.out.println("Punteggio: " + jreader.nextInt()); break;
                case "errors":         System.out.println("Errori: " + jreader.nextInt()); break;
                case "correct":        System.out.println("Risposte corrette: " + jreader.nextInt()); break;
                case "guessedGroups":  printGroupsArray(jreader, "GRUPPI INDOVINATI"); break;
                default:               jreader.skipValue(); break;
            }
        }
        jreader.endObject();
    }

    public void printSubmitProposal(JsonReader jreader) {
        try {
            while (jreader.hasNext()) {
                String name = jreader.nextName();
                switch (name) {
                    case "err_code":   printErrorMessage(jreader.nextInt()); break;
                    case "esito":      System.out.println(jreader.nextString()); break;
                    case "playerStats":  printPlayerStatsBlock(jreader); break;
                    case "gameStats":  jreader.beginObject(); printGameStatsBlock(jreader); break;
                    default: jreader.skipValue(); break;
                }
            }
        } catch (IOException ex) { 
            System.err.println("Errore lettura risposta."); 
        }
    }

    public void printGameStatsBlock(JsonReader jreader) {
        try {
        	System.out.println("\n--- STATISTICHE GLOBALI PARTITA ---");
        	
            while (jreader.hasNext()) {
                String name = jreader.nextName();
                switch (name) {
                    case "time":           printFormattedTime(jreader.nextLong()); break;
                    case "currentPlayers": System.out.println("Giocatori attivi: " + jreader.nextInt()); break;
                    case "totalPlayers":   System.out.println("Giocatori totali: " + jreader.nextInt()); break;
                    case "compPlayers":    System.out.println("Partite completate: " + jreader.nextInt()); break;
                    case "winPlayers":     System.out.println("Vincitori: " + jreader.nextInt()); break;
                    case "avgPoints":      System.out.printf("Punteggio medio: %.2f\n", jreader.nextDouble()); break;
                    case "gameStats":	   jreader.beginObject();  break;
                    default:               jreader.skipValue(); break;
                }
            }
            jreader.endObject();
        } catch (IOException ex) { System.err.println("Impossibile leggere la risposta del server"); }
    }

    public void printLeaderboard(JsonReader jreader) {
        try {
            while (jreader.hasNext()) {
                String name = jreader.nextName();
                switch (name) {
                    case "scoreboard": printScoreboardArray(jreader); break;
                    case "rank":       System.out.print("\nPosizione: " + jreader.nextInt()); break;
                    case "username":   System.out.println(" - Utente: " + jreader.nextString()); break;
                    default:           jreader.skipValue(); break;
                }
            }
        } catch (IOException ex) { System.err.println("Impossibile leggere la risposta del server"); }
    }

    public void printPlayerStats(JsonReader jreader) {
        try {
            System.out.println("\n--- PROFILO GIOCATORE ---");
            while (jreader.hasNext()) {
                String name = jreader.nextName();
                switch (name) {
                    case "playedGames":   System.out.println("Partite giocate: " + jreader.nextInt()); break;
                    case "wonGames":      System.out.println("Partite vinte: " + jreader.nextInt()); break;
                    case "winRate":       System.out.printf("Win Rate: %.2f%%\n", jreader.nextDouble()); break;
                    case "lossRate":      System.out.printf("Loss Rate: %.2f%%\n", jreader.nextDouble()); break;
                    case "currentStreak": System.out.println("Streak attuale: " + jreader.nextInt()); break;
                    case "maxStreak":     System.out.println("Streak massima: " + jreader.nextInt()); break;
                    case "points":        System.out.println("Punteggio totale: " + jreader.nextInt()); break;
                    case "histogram":	  printHistogram(jreader); break;
                    default:              jreader.skipValue(); break;
                }
            }
        } catch (IOException ex) { System.err.println("Impossibile leggere la risposta del server"); }
    }
    
    public void printEndGameUnicast(JsonReader jr) {
    	try {
	    	while(jr.hasNext())
	    	{
	    		String name = jr.nextName();
	    		switch(name)
	    		{
		    		case "gameStats" :	jr.beginObject(); printGameStatsBlock(jr); break;
		    		case "playerStats" : 	printPlayerStatsBlock(jr); break;
		    		default: jr.skipValue();
	    		}
	    	}
	    	jr.endObject();
    	}
    	catch(IOException ex ) {}
    }
    
    private void printErrorMessage(int errCode) {
    	Error error = Error.fromCode(errCode);
    	// Se è un messaggio di errore stampo ERRORE(codice): messaggio.
    	// Se non è propriamente un errore, stampo solo il messaggio.
        if (errCode == Error.SUCCESS.getCode() ||
        	errCode == Error.WRONG_PROPOSAL.getCode() ||
        	errCode == Error.CORRECT_PROPOSAL.getCode())
        {
        	System.out.println(error.getDescription());
        }
        else System.err.println("ERRORE (" + errCode + "): " + Error.fromCode(errCode).getDescription());
     // Separo il testo da quello precedente
     		System.out.println("\n\n\n\n\n");
    }

    private void printFormattedTime(long seconds) {
        System.out.printf("Tempo: %d:%02d\n", seconds / 60, seconds % 60);
    }

    private void printWordsGrid(JsonReader jreader) throws IOException {
        System.out.println("\n--- PAROLE IN GIOCO ---");
        jreader.beginArray();
        int count = 0;
        while (jreader.hasNext()) {
            System.out.printf("%-15s", jreader.nextString());
            if (++count % 4 == 0) System.out.println();
        }
        jreader.endArray();
        System.out.println();
    }

    private void printScoreboardArray(JsonReader jreader) throws IOException {
        System.out.println("\n--- CLASSIFICA ---");
        jreader.beginArray();
        int rank = 1;
        while (jreader.hasNext()) {
            System.out.println(rank++ + " - " + jreader.nextString());
        }
        jreader.endArray();
    }

    private void printGroupsArray(JsonReader jreader, String title) throws IOException {
        System.out.println("\n--- " + title + " ---");
        jreader.beginArray();
        while (jreader.hasNext()) {
            printSingleGroup(jreader);
        }
        jreader.endArray();
    }
    
    private void printSingleGroup(JsonReader jreader) throws IOException {
        jreader.beginObject();
        while (jreader.hasNext()) {
            String key = jreader.nextName();
            if (key.equals("theme")) {
                System.out.print("[" + jreader.nextString() + "]: ");
            } else if (key.equals("words")) {
                jreader.beginArray();
                while (jreader.hasNext()) {
                    System.out.print(jreader.nextString() + (jreader.hasNext() ? ", " : ""));
                }
                jreader.endArray();
            } else jreader.skipValue();
        }
        System.out.println();
        jreader.endObject();
    }
    
    private void printGroupsBlock(JsonReader jreader) throws IOException {
    	printGroupsArray(jreader, "GRUPPI CORRETTI");
    }
    
    private void printHistogram(JsonReader jreader) throws IOException {
        int[] histogram = new int[6];
        int index = 0;
        
        jreader.beginArray();
        while (jreader.hasNext() && index < 6) {
            histogram[index++] = jreader.nextInt();
        }
        jreader.endArray();

        System.out.println("\n--- ISTOGRAMMA ---");
        
        String[] labels = {
            "Vinte con 0 errori  ",
            "Vinte con 1 errore  ",
            "Vinte con 2 errori  ",
            "Vinte con 3 errori  ",
            "Perse per Errori    ",
            "Perse per Timeout   "
        };

        for (int i = 0; i < histogram.length; i++) {
            
            StringBuilder row = new StringBuilder();
            for(int j = 0; j < histogram[i]; j++) {
            	row.append('*');
            }
            
            System.out.printf("%s | %s\n", labels[i], row);
        }
    }
}