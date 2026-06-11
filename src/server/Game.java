package server;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Game {
	
	public int gameId;
	
	public List<GroupWords> groups = new ArrayList<>();
	
	public List<String> allWords = new ArrayList<>();
	
	public int getGameID() { return gameId; }
	
	public List<GroupWords> getGroups() { return groups; }
	
	public List<String> getAllWords() { return allWords; }
	
	public void initializeWords() {
		if (allWords.isEmpty()) {
		        groups.forEach(g -> allWords.addAll(g.words));
		        Collections.shuffle(allWords); 		// Mescolo le parole.
	    }
	}
}