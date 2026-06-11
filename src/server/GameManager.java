package server;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;

import common.User;

/*
 * Classe principale che gestisce il ciclo di vita delle partite.
 */

public class GameManager {

	private final File resourcesDirectory;
	
	//		--- Partita in corso ---
    private volatile GameSession currentGameSession;
    
    //		--- Variabili per la gestione delle partite ---
    private volatile ArrayList<GameOffset> gamesOffsets = new ArrayList<>();
    private int nextGamePointer;
    
    //		--- Variabili per l'aggiornamento delle statistiche utente e partite
    private final ConcurrentHashMap<String,User> users;
    private final ConcurrentHashMap<Integer,GameStats> games;
    
    public GameManager(File resourcesDirectory, int lastGameIndex, ConcurrentHashMap<String,User> users, 
    		ConcurrentHashMap<Integer,GameStats> games) throws FileNotFoundException {
    	this.users = users;
    	this.games = games;
        this.resourcesDirectory = resourcesDirectory;
        this.nextGamePointer = lastGameIndex;
        getGameIDs();
        newGame();
    }

    public GameSession getCurrentGameSession() { return currentGameSession; }
    
    private void getGameIDs() {
    	
    	/*
    	 * Analizza il file JSON delle partite per generare un indice degli offset.
    	 * L'algoritmo traccia la profondità dell'annidamento degli oggetti attraverso il parsing delle parentesi graffe
    	 * per identificare univocamente l'inizio di ogni sessione di gioco.
    	 * 
    	 * L'obiettivo è quello di garantire la scalabilità del sistema permettendo il caricamento 
    	 * delle partite solo su richiesta, senza dover caricare tutte le partite in memoria, ma solo all'occorrenza.
    	 */
    	
        File gamesFile = new File(resourcesDirectory, "Connections_Data.json");
        this.gamesOffsets = new ArrayList<>();
        
        // Carattere separatore '\n'.
        int lineSepLength = 1; 
        
        try (BufferedReader br = new BufferedReader(new FileReader(gamesFile))) {

            long offset = 0;
            String line;
            // Ricerca per profondità.
            // La prima { che incontrerò rappresenterà la partita.
            int depth = 0;
            long objectStart = -1;
            int currentId = -1;

            while ((line = br.readLine()) != null) {
                String trimmed = line.trim();

                //	Aumento di profondità a ogni '{'
                if (trimmed.startsWith("{")) {
                    if (depth == 0) {
                        objectStart = offset;
                        currentId = -1;
                    }
                    depth++;
                }

                // Cerchiamo gameId solo quando siamo al secondo livello di profondità
                if (depth == 1 && trimmed.contains("\"gameId\"")) {
                	
                	// Rimuovo eventuali caratteri. Voglio solo i numeri di Game ID
                    String numericPart = trimmed.split(":")[1].replaceAll("[^0-9]", "");
                    if (!numericPart.isEmpty()) {
                        currentId = Integer.parseInt(numericPart);
                    }
                }
                // Diminuisco di profondità a ogni '}'
                if (trimmed.startsWith("}")) {
                    depth--;
                    if (depth == 0 && objectStart != -1 && currentId != -1) {
                        gamesOffsets.add(new GameOffset(currentId, objectStart));
                        objectStart = -1;
                        currentId = -1;
                    }
                }

                // Aggiorno l'offset sommandogli la lunghezza della stringa appena letta
                offset += line.getBytes(StandardCharsets.UTF_8).length + lineSepLength;
            }

        } catch (IOException e) {
            System.err.println("Impossibile leggere il file delle partite.");
        }
    }


    public void newGame() {
        if (gamesOffsets.isEmpty()) {
            System.err.println("Nessuna partita disponibile.");
            return;
        }

        // Prendiamo la partita corrente e avanziamo il puntatore
        GameOffset nextGame = gamesOffsets.get(nextGamePointer);
        
        // Segno nel properties che la partita attiva è nextGamePointer.
        // In caso di crash, ricarico questa partita. 
        writeGameCheckpointProperties();
        
        nextGamePointer++;
        if (nextGamePointer >= gamesOffsets.size()) nextGamePointer = 0;

        File gamesFile = new File(resourcesDirectory, "Connections_Data.json");

        try (FileChannel fc = FileChannel.open(gamesFile.toPath(), StandardOpenOption.READ)) {
            fc.position(nextGame.getOffset());
            JsonReader jReader = new JsonReader(Channels.newReader(fc, StandardCharsets.UTF_8.name()));
            Game activeGame = new Gson().fromJson(jReader, Game.class);
            activeGame.initializeWords();
            GameSession newSession = new GameSession(activeGame);
            newSession.setActive();
            
            this.currentGameSession = newSession;
        } catch (IOException ex) {
        	System.err.println("Errore durante il caricamento di una nuova partita.");
        }
    }
    
    public void endGame() {
    	/* Salvo le statistiche collettive e individuali della partita appena terminata,
    	successivamente aggiorno le statistiche globali di ogni utente. */
        	currentGameSession.write.lock();
            currentGameSession.setInactive();
            currentGameSession.write.unlock();
            
            Game gameRef = currentGameSession.getGame();
            
            Map<Integer, PlayerGameStats> finalStats = currentGameSession.getPlayerStatsMap();
            
            GameStats history = new GameStats(
                    gameRef.getGroups(), 
                    finalStats, 
                    currentGameSession.completedPlayers,
                    currentGameSession.currentPlayers,
                    currentGameSession.totalPlayers,
                    currentGameSession.totalPoints,
                    currentGameSession.winningPlayers
            );
            
            games.put(gameRef.getGameID(), history);

            finalStats.forEach((userId, stats) -> {
                User u = stats.getUserRef(); 
                if (u != null) {
                    update(u, stats);
                }
            });
            
            // Aggiorno la classifica
            refreshScoreboard();
    }

    private void update(User utente, PlayerGameStats stats) {
    	try {
        synchronized(utente) {
            utente.incrementPlayedGames();
            
            if(stats.isWon()) {
                utente.incrementWonGames();
                utente.incrementStreak();
                utente.addToHistogram(stats.getErrors());
                if(stats.getErrors() == 0) {
                    utente.incrementPerfectGames();
                }
            } else {
                utente.resetStreak();
                utente.addToHistogram(stats.getErrors() == 4 ? 4 : 5);
            }
            
            utente.calculateNewRate();
            utente.addPoints(stats.getPoints());
        }
    	}
    	catch(Exception ex) {ex.printStackTrace();}
    }
    
    //		--- Apro il file properties ed aggiorno l'indice che indica quale sarà la prossima partita da giocare ---
    private void writeGameCheckpointProperties() {
    	File configFile = new File(resourcesDirectory, "server.properties");
		try (FileReader reader = new FileReader(configFile)) {
			Properties props = new Properties();
			props.load(reader);
			
			props.setProperty("last.game.id", Integer.toString(nextGamePointer));
			
			try (FileWriter writer = new FileWriter(configFile)) {
				props.store(writer, null);
			}
		}
    	catch(IOException ex) { System.err.println("Impossibile aggiornare il file di configurazione"); }
    }
    
    //		--- Aggiorno la classifica globale ---
    private void refreshScoreboard() {
        ArrayList<User> updatedData = new ArrayList<>(users.values());

        updatedData.sort((u1, u2) -> {
            int res = Integer.compare(u2.getPoints(), u1.getPoints());
            if (res != 0) return res;
            return u1.getUsername().compareTo(u2.getUsername());
        });
        // Sostituisco atomicamente il riferimento alla classifica. Non sarà possibile quindi per eventuali Task recuperare una classifica incompleta
        ServerMain.scoreboard = updatedData;
    }
}