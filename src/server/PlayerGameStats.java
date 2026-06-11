package server;

import java.util.ArrayList;
import java.util.List;

import common.User;

/*
 * Questa classe rappresenta la statistica individuale di un giocatore, riferita a una singola partita.
 */

public class PlayerGameStats {
	
	private volatile int errors = 0;
    private volatile int points = 0;
    private volatile boolean won = false;
    private volatile boolean completed = false;
    
    private transient User userRef;
	
	private final List<GroupWords> guessedGroups = new ArrayList<>();
	private final transient List<String> remainingWords;
	
	public PlayerGameStats(List<String> allWords, User user) {
		this.userRef = user;
        this.remainingWords = new ArrayList<>(allWords);
    }
	
	public boolean isCompleted( ) { return completed; }
	
	public List<GroupWords> getGuessedGroups() { return guessedGroups; }
 
	public List<String> getRemainingWords() { return remainingWords; }
	
	public int getPoints() {return points;}
	
	public int getErrors() {return errors;}
	
	public User getUser() { return userRef; }
	
	public void addSuccessfulGroup(GroupWords group) {
		
        guessedGroups.add(group);
        
        if(guessedGroups.size() >= 3) { won = true; completed = true; }
        
        remainingWords.removeAll(group.words);
    }
	
	public void incrementError() {
        errors++;
        if(errors >= 4) { completed = true; }
    }
	
	public void addPoints() { points += 6; }
	
	public void removePoints() { points -= 4; }
	
	public boolean isWon() { return won; }
	
	public User getUserRef() { return userRef; }
}
