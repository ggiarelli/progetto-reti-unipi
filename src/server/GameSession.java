package server;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.*;


import common.Error;
import common.User;

/* Questa classe rappresenta l'istanza di una partita attualmente attiva attraverso il flag "active".
Un'istanza verrà creata dal GameManager quando deve rendere attiva una partita, e ne gestirà anche la chiusura. */

public class GameSession {
    
	ReentrantReadWriteLock readWriteLock = new ReentrantReadWriteLock(true);	// Applico una politica fair
	Lock read = readWriteLock.readLock();
	Lock write = readWriteLock.writeLock();
	
    //		--- Statistiche collettive ---
    AtomicInteger currentPlayers = new AtomicInteger();
    AtomicInteger totalPlayers = new AtomicInteger();
    AtomicInteger completedPlayers = new AtomicInteger();
    AtomicInteger winningPlayers = new AtomicInteger();
    AtomicInteger totalPoints = new AtomicInteger();
    
    private final Game game;
    private final long endTime;
    private volatile boolean active = false;
    
    private final Map<Integer, PlayerGameStats> playerStats = new ConcurrentHashMap<>();

    public GameSession(Game game) {
        this.game = game;
        this.endTime = (System.currentTimeMillis() / 1000) + ServerMain.gameTime * 60; // Tempo di fine partita
    }

    public float getAveragePoints() {
        int total = totalPlayers.get();
        if (total == 0) return 0;
        // Calcoliamo la media basandoci sui punti totali accumulati nella sessione
        return (float) totalPoints.get() / total;
    }
    
    public boolean isActive() { return active; }
    public void setActive() { active = true; }
    public void setInactive() { active = false; }
    public Game getGame() { return game; }
    
    public long timeLeft() {
        if(!active) return 0;
        return Math.max(0, this.endTime - System.currentTimeMillis() / 1000);
    }

    //		--- Gestione Giocatori ---
    public PlayerGameStats registerPlayer(User user) {
        if (!active || user == null) return null;
        
        int userId = user.getId();
        PlayerGameStats newStats = new PlayerGameStats(game.getAllWords(), user);
        PlayerGameStats existing = playerStats.putIfAbsent(userId, newStats);
        
        if (existing == null) {
            totalPlayers.incrementAndGet();
            currentPlayers.incrementAndGet();
            return newStats;
        }
        return existing;
    }
    
    //		--- Logica di Gioco ---
    
    //		--- Caso in cui una proposta sia corretta ---
    public Error correctProposal(int userId, GroupWords group) {
        if(!active) return Error.GAME_ALREADY_COMPLETED;
        
        PlayerGameStats ps = playerStats.get(userId);
        if(ps == null) return Error.USER_NOT_FOUND;
        if(ps.isCompleted()) return Error.GAME_ALREADY_COMPLETED;
        if(ps.getGuessedGroups().contains(group)) return Error.GROUP_ALREADY_GUESSED;
        
        ps.addSuccessfulGroup(group);
        ps.addPoints();
        totalPoints.addAndGet(6);
        
        if(ps.isCompleted()) {
            winningPlayers.incrementAndGet();
            completedPlayers.incrementAndGet();
            currentPlayers.decrementAndGet();
        }
        
        return Error.CORRECT_PROPOSAL;
    }

    //		--- Caso in cui una proposta sia errata ---
    public Error wrongProposal(int userId) {
        if(!active) return Error.GAME_ALREADY_COMPLETED;
        
        PlayerGameStats ps = playerStats.get(userId);
        if(ps == null) return Error.USER_NOT_FOUND;
        if(ps.isCompleted()) return Error.GAME_ALREADY_COMPLETED;
        
        ps.removePoints();
        ps.incrementError();
        totalPoints.addAndGet(-4);
        
        if(ps.isCompleted()) {
            completedPlayers.incrementAndGet();
            currentPlayers.decrementAndGet();
        }
        
        return Error.WRONG_PROPOSAL;
    }

    public boolean isPlayerFinished(int userId) {
        PlayerGameStats ps = playerStats.get(userId);
        return (ps != null) && ps.isCompleted();
    }

    public boolean hasAlreadyGuessed(int userId, GroupWords group) {
        PlayerGameStats ps = playerStats.get(userId);
        if (ps == null) return false;
        return ps.getGuessedGroups().contains(group);
    }
    
    // Ritorna la mappa per le classifiche finali
    public Map<Integer, PlayerGameStats> getPlayerStatsMap() { 
        return playerStats; 
    }
    
    public LiveStats getLiveStats()
    {
    	synchronized(this) {
    		return new LiveStats(currentPlayers.get(),winningPlayers.get(),totalPlayers.get(),completedPlayers.get(),(totalPoints.get() / totalPlayers.get()), this.timeLeft());
    	}
    }
}