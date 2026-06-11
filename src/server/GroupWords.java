package server;

import java.util.HashSet;
import java.util.Set;

/*
 * Questa classe rappresenta un gruppo di parole, accompagnato dal tema.
 */

public class GroupWords {
	
	public String theme;
	
	public Set<String> words = new HashSet<>();
	
	public GroupWords() {}
	
	public void setTheme(String theme) { this.theme = theme; }
	
	public Set<String> getWords() { return words; }
}
