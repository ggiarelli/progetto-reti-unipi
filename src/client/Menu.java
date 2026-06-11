package client;

import java.util.Arrays;
import java.util.List;

/*
 * I menù che può visualizzare il client sono presenti
 * in questa classe, assieme ai metodi che li stampano
 */

public class Menu {
    private List<String> options;
    
    private static final List<String> NOT_LOGGED_OPTIONS = Arrays.asList(
        "Registra un nuovo utente",
        "Login",
        "Disconnetti dal server"
    );
    
    private static final List<String> LOGGED_OPTIONS = Arrays.asList(
        "Aggiorna credenziali",
        "Invia una nuova risposta",
        "Visualizza informazioni su una partita specifica",
        "Visualizza statistiche su una partita specifica",
        "Visualizza classifica generale",
        "Visualizza statistiche personali",
        "Logout",
        "Disconnetti dal server"
    );
    
    private static final List<String> CREDENTIALS_OPTIONS = Arrays.asList(
    	"Aggiorna password",
    	"Aggiorna nome utente",
    	"Aggiorna entrambi" ,
    	"Indietro"
    );
    		
    private static final List<String> GAME_INFO_OPTIONS = Arrays.asList(
    	"Partita corrente",
    	"Partita già terminata",
    	"Indietro"
    );
    
    private static final List<String> NEW_GAME_OPTIONS = Arrays.asList(
    	"Sì",
    	"No"
    );
    
    private static final List<String> LEADERBOARD_OPTIONS = Arrays.asList(
    	"Posizione di un utente specifico",
    	"Classifica dei primi K giocatori",
    	"Classifica globale" ,
    	"Indietro"
    );
    
    public Menu(ClientState state) {
        update(state);
    }
    
    public void update(ClientState state) {
        this.options = state.equals(ClientState.LOGGED) ? LOGGED_OPTIONS : NOT_LOGGED_OPTIONS;
    }
    
    public int print() {
        System.out.println("\n--- MENU ---");
        System.out.println("Scegliere un numero per svolgere la relativa funzione.");
        for (int i = 0; i < options.size(); i++) {
            System.out.println((i + 1) + " - " + options.get(i));
        }
        return options.size();
    }
    
    public static int printCredentialsMenu() {
    	System.out.println("Quali credenziali vuoi aggiornare?");
    	for (int i = 0; i < CREDENTIALS_OPTIONS.size(); i++)
    	{
    		System.out.println((i+1) + " - " + CREDENTIALS_OPTIONS.get(i));
    	}
    	return CREDENTIALS_OPTIONS.size();
    }
    
    public static int printGameInfoMenu() {
    	System.out.println("Di quale partita vuoi ricevere informazioni?");
    	for(int i = 0; i < GAME_INFO_OPTIONS.size(); i++)
    	{
    		System.out.println((i+1) + " - " + GAME_INFO_OPTIONS.get(i));
    	}
    	return GAME_INFO_OPTIONS.size();
    }
    
    public static int printNewGameMenu() {
    	System.out.println("E' disponibile una nuova partita. Vuoi partecipare?");
    	for(int i = 0; i < NEW_GAME_OPTIONS.size(); i++)
    	{
    		System.out.println((i+1) + " - " + NEW_GAME_OPTIONS.get(i));
    	}
    	return NEW_GAME_OPTIONS.size();
    }
    
    public static int printLeaderboardMenu() {
    	System.out.println("Classifica:");
    	for(int i = 0; i < LEADERBOARD_OPTIONS.size(); i++)
    	{
    		System.out.println((i+1) + " - " + LEADERBOARD_OPTIONS.get(i));
    	}
    	return LEADERBOARD_OPTIONS.size();
    }
}