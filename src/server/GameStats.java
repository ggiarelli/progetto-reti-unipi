package server;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

// Questa classe rappresenta le informazioni su statistiche collettive ed individuali su una partita già terminata.

public class GameStats {
    
	final List<GroupWords> groups;
    
    //		--- Statistiche collettive ---
    private final int currentPlayers;      // Giocatori che stanno ancora giocando
    private final int completedPlayers;    // Giocatori che hanno terminato la partita
    private final int winningPlayers;      // Giocatori che hanno vinto la partita
    private final int totalPlayers;        // Giocatori che hanno partecipato alla partita
    
    private final float avgPoints;
    
    //		--- Statistiche individuali ---
    
    // La mappa sarà in sola lettura, non è necessaria sincronizzazione.
    private final HashMap<Integer, PlayerGameStats> playerStats;
    
    public GameStats(List<GroupWords> groups,
                     Map<Integer, PlayerGameStats> finalStats, 
                     AtomicInteger completedPlayers,
                     AtomicInteger currentPlayers, 
                     AtomicInteger totalPlayers, 
                     AtomicInteger totalPoints, 
                     AtomicInteger winningPlayers) {
        
        this.currentPlayers = currentPlayers.get();
        this.completedPlayers = completedPlayers.get();    
        this.totalPlayers = totalPlayers.get();
        this.winningPlayers = winningPlayers.get();
        
        if(this.totalPlayers != 0) { this.avgPoints = (float) totalPoints.get() / this.totalPlayers; } 
        else { this.avgPoints = 0; }
        
        this.groups = groups;
        this.playerStats = new HashMap<>(finalStats);
    }

    public Map<Integer, PlayerGameStats> getPlayerStats() { 
        return playerStats; 
    }
    
    public List<GroupWords> getGroups() { return groups; }
    
    public int getCurrentPlayers() {
        return currentPlayers;
    }

    public int getCompletedPlayers() {
        return completedPlayers;
    }

    public int getWinningPlayers() {
        return winningPlayers;
    }

    public int getTotalPlayers() {
        return totalPlayers;
    }

    public float getAvgPoints() {
        return avgPoints;
    }
}