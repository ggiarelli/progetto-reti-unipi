package common;

import java.io.IOException;
import java.io.StringWriter;
import java.util.List;

import com.google.gson.stream.JsonWriter;

import server.GroupWords;
import server.PlayerGameStats;

/*
 * Questa classe comprende i metodi per costruzione di stringhe JSON sia lato server che lato client
 * Sono presenti metodi pubblici, accessibili dall'esterno, e metodi privati, che scriveranno alcuni blocchi di messaggio.
 */

public class JsonBuilder {
	
	//		--- Metodi privati chiamati solo internamente da metodi pubblici di questa classe ---
	
	
    // Scrive l'intestazione standard: operation e codice errore
    private static void writeHeader(JsonWriter jw, String operation, int errCode) throws IOException {
        jw.name("operation").value(operation);
        jw.name("err_code").value(errCode);
    }

    // Scrive il blocco relativo alla partita (tempo, parole e game ID) 
    private static void writeGameData(JsonWriter jw, long timeLeft, List<String> allWords, int gameId) throws IOException {
        jw.name("game").beginObject();
        jw.name("remainingTime").value(timeLeft);
        jw.name("gameId").value(gameId);
        jw.name("words").beginArray();
        for (String s : allWords) {
            jw.value(s);
        }
        jw.endArray();
        jw.endObject();
    }

    // Scrive il blocco delle statistiche globali di una singola partita
    private static void writeGameStats(JsonWriter jw, long time, int currPlayers, int totPlayers, int compPlayers, int winPlayers, float  avgPoints) throws IOException {
    	jw.name("gameStats").beginObject();
    	if(time != -1) jw.name("time").value(time);
    	if(currPlayers != -1) jw.name("currentPlayers").value(currPlayers);
    	if(totPlayers != -1) jw.name("totalPlayers").value(totPlayers);
    	if(compPlayers != -1) jw.name("compPlayers").value(compPlayers);
    	if(winPlayers != -1) jw.name("winPlayers").value(winPlayers);
    	if(avgPoints != -1) jw.name("avgPoints").value((double)avgPoints);
    	jw.endObject();
    }
    
    // Scrive il blocco delle statistiche individuali di una singola partita
    private static void writeGamePlayerStats(JsonWriter jw, int errors, int points, List<GroupWords> guessedGroups) throws IOException {
        jw.name("playerStats").beginObject();
        jw.name("points").value(points);
        jw.name("errors").value(errors);
        
        jw.name("guessedGroups").beginArray();
        for (GroupWords g : guessedGroups) {
            jw.beginObject();
            jw.name("theme").value(g.theme);
            jw.name("words").beginArray();
            for (String w : g.words) { jw.value(w); }
            jw.endArray();
            jw.endObject();
        }
        jw.endArray();
        jw.name("correct").value(guessedGroups.size());
        jw.endObject();
    }

    // Scrrive un singolo gruppo: tema + parole
    private static void writeGroup(JsonWriter jw, GroupWords g) throws IOException
    {
    	jw.beginObject();
		jw.name("theme").value(g.theme);
		jw.name("words").beginArray();
		for(String w : g.words)
		{
			jw.value(w);
		}
		jw.endArray();
		jw.endObject();
    }
    
    // Scrive la classifica
    private static void writeScoreboard(JsonWriter jw, List<User> scoreboard) throws IOException{
    	jw.name("scoreboard");
    	jw.beginArray();
    	for(User u : scoreboard)
    	{
    		jw.value(u.getUsername());
    	}
    	jw.endArray();
    }
    
    // Scrive la posizione in classifica di un utente specifico
    private static void writePlayerScoreboard(JsonWriter jw, String username, int rank) throws IOException{
    	jw.name("rank").value(rank);
    	jw.name("username").value(username);
    }
    
    // Scrive le statistiche di un utente specifico
    private static void writePlayerStats(JsonWriter jw, User user) throws IOException 
    {
        jw.name("playedGames").value(user.getPlayedGames());
        jw.name("wonGames").value(user.getWonGames());
        
        jw.name("winRate").value(user.getWinRate());
        jw.name("lossRate").value(user.getLossRate());
        
        jw.name("currentStreak").value(user.getCurrentStreak());
        jw.name("maxStreak").value(user.getMaxStreak());
        
        jw.name("perfectGames").value(user.getPerfectGames());
        jw.name("points").value(user.getPoints());
        
        jw.name("histogram").beginArray();
        for(int val: user.getHistogram()) { jw.value(val); }
        jw.endArray();
        
    }
    
    // Il client può scrivere json molto semplici che specificano l'operazione e semplici campi  
    private static String buildJson(String operation, String... fields) {
        try (StringWriter sw = new StringWriter(); JsonWriter jw = new JsonWriter(sw)) {
            jw.beginObject();
            jw.name("operation").value(operation);
            for (int i = 0; i < fields.length; i += 2) {
                jw.name(fields[i]).value(fields[i + 1]);
            }
            jw.endObject();
            return sw.toString();
        } catch (IOException ex) { return null; }
    }
    
    /*
    Invio di una proposta da parte di un client
    {
    	  "operation": "submitProposal",
    	  "words": [
    	    STRING,
    	    STRING,
    	    STRING,
    	    STRING
    	  ]
    }
    */
    private static String buildProposal(String[] words) {
        try (StringWriter sw = new StringWriter(); JsonWriter jw = new JsonWriter(sw)) {
            jw.beginObject();
            jw.name("operation").value("submitProposal");
            jw.name("words").beginArray();
            for (String s : words) jw.value(s);
            jw.endArray();
            jw.endObject();
            return sw.toString();
        } catch (IOException ex) { return null; }
    }
    
    //		--- Metodi pubblici chiamati dalle Task ---
    
    
    /*
    Risposta al login nel caso di autenticazione con successo
    {
    	  "operation": "login",
    	  "err_code": 0,
    	  "game": {
    	    "remainingTime": LONG,
    	    "gameId": INT,
    	    "words": [
    	      STRING,
    	      STRING,
    	      STRING,
    	      STRING
    	    ]
    	  },
    	  "playerStats": {
    	    "points": INT,
    	    "errors": INT,
    	    "guessedGroups": [
    	      {
    	        "theme": STRING,
    	        "words": [
    	          STRING,
    	          STRING,
    	          STRING,
    	          STRING
    	        ]
    	      }
    	    ],
    	    "correct": INT
    	  }
    	}
    */
    public static String loginSuccess(long timeLeft, int gameId, PlayerGameStats pStats) {
        try (StringWriter sw = new StringWriter(); JsonWriter jw = new JsonWriter(sw)) {
            jw.beginObject();
            writeHeader(jw, "login", Error.SUCCESS.getCode());
            writeGameData(jw, timeLeft, pStats.getRemainingWords(), gameId);
            writeGamePlayerStats(jw, pStats.getErrors(), pStats.getPoints(), pStats.getGuessedGroups());
            jw.endObject();
            return sw.toString();
        } catch (IOException ex) { return null; }
    }

    /*
    Risposta del server se una proposta termina la partita per vittoria o per sconfitta.
    In tal caso si invieranno anche le statistiche individuali e collettive, rappresentate da playerStats e gameStats.
    Se non fosse l'ultima proposta, il JSON sarebbe composto solo dai campi operation ed err_code.
    {
    	  "operation": "submitProposal",
    	  "err_code": INT,
    	  "esito": STRING,
    	  "playerStats": {
    	    "points": INT,
    	    "errors": INT,
    	    "guessedGroups": [
    	      {
    	        "theme": STRING,
    	        "words": [
    	          STRING,
    	          STRING,
    	          STRING,
    	          STRING
    	        ]
    	      }
    	    ],
    	    "correct": INT
    	  },
    	  "gameStats": {
    	    "currentPlayers": INT,
    	    "totalPlayers": INT,
    	    "compPlayers": INT,
    	    "winPlayers": INT,
    	    "avgPoints": DOUBLE
    	  }
    }
    */
    public static String replyProposal(Error error, String esito, PlayerGameStats pStats, 
            int currPlayers, int totPlayers, int compPlayers, 
            int winPlayers, float avgPoints) {
			try (StringWriter sw = new StringWriter(); JsonWriter jw = new JsonWriter(sw)) {
			jw.beginObject();
			
			writeHeader(jw, "submitProposal", error.getCode());
			
			jw.name("esito").value(esito);
			
			if (pStats != null) {
				writeGamePlayerStats(jw, pStats.getErrors(), pStats.getPoints(), pStats.getGuessedGroups());
			}
			
			writeGameStats(jw, -1, currPlayers, totPlayers, compPlayers, winPlayers, avgPoints);
			
			jw.endObject();
			return sw.toString();
			} catch (IOException ex) { 
			return null; 
			}
		}
    
    // L'utente vuole giocare ancora
    public static String playAgainSuccess(long timeLeft, int gameId, PlayerGameStats pStats) {
        try (StringWriter sw = new StringWriter(); JsonWriter jw = new JsonWriter(sw)) {
            jw.beginObject();
            writeHeader(jw, "playAgain", Error.SUCCESS.getCode()); 
            writeGameData(jw, timeLeft, pStats.getRemainingWords(), gameId);
            jw.name("gameId").value(gameId);
            writeGamePlayerStats(jw, pStats.getErrors(), pStats.getPoints(), pStats.getGuessedGroups());
            jw.endObject();
            return sw.toString();
        } catch (IOException ex) { return null; }
    }
    
    /*
    Risposta a seguito di una richiesta di informazioni di una partita già terminata.
    Sono inviate le statistiche individuali e i gruppi di parole corretti, rappresentati dall'oggetto playerStats e dall'array groups.
    {
    	  "operation": "requestGameInfo",
    	  "err_code": INT,
    	  "groups": [
    	    {
    	      "theme": STRING,
    	      "words": [
    	        STRING,
    	        STRING,
    	        STRING,
    	        STRING
    	      ]
    	    }
    	  ],
    	  "playerStats": {
    	    "points": INT,
    	    "errors": INT,
    	    "guessedGroups": [
    	      {
    	        "theme": STRING,
    	        "words": [
    	          STRING,
    	          STRING,
    	          STRING,
    	          STRING
    	        ]
    	      }
    	    ],
    	    "correct": INT
    	  }
    }
    */
    public static String replyGameInfoFinished(Error err, List<GroupWords> groups, PlayerGameStats pStats)
    {
    	try(StringWriter sw = new StringWriter(); JsonWriter jw = new JsonWriter(sw))
    	{
    		jw.beginObject();
    		writeHeader(jw,"requestGameInfo",err.getCode());
    		jw.name("groups");
    		jw.beginArray();
    		for(GroupWords g : groups)
    		{
    			writeGroup(jw, g);
    		}
    		jw.endArray();
    		writeGamePlayerStats(jw, pStats.getErrors(), pStats.getPoints(), pStats.getGuessedGroups());
    		jw.endObject();
    		return sw.toString();
    	}
    	catch(IOException ex) { return null; }
    }
    
    /*
    Nel caso di partita ancora attiva, il JSON sarà come quello precedente ma al posto dei gruppi corretti
    saranno inviate informazioni sulla partita corrente, rappresentate dall'oggetto game.
    	  "game": {
    	    "remainingTime": LONG,
    	    "gameId": INT,
    	    "words": [
    	      STRING,
    	      STRING,
    	      STRING,
    	      STRING
    	    ]
    	  }
    */
    public static String replyGameInfoActive(Error err, long timeLeft, PlayerGameStats pStats, int gameId)
    {
    	try(StringWriter sw = new StringWriter(); JsonWriter jw = new JsonWriter(sw))
    	{
    		jw.beginObject();
    		writeHeader(jw,"requestGameInfo", err.getCode());
    		writeGameData(jw, timeLeft, pStats.getRemainingWords(), gameId);
    		writeGamePlayerStats(jw, pStats.getErrors(), pStats.getPoints(), pStats.getGuessedGroups());
    		jw.endObject();
    		return sw.toString();
    	}
    	catch(IOException ex) { return null; }
    }
    
    /*
    Risposta del server nel caso di richiesta delle statistiche di una partita già terminata
    {
    	"operation": "requestGameStats",
    	"err_code": 0,
    	"gameStats": {
    	  	"totalPlayers": INT,
    	    "compPlayers": INT,
    	    "winPlayers": INT,
    	    "avgPoints": DOUBLE
    	}
    }
    
    Risposta del server nel caso di richiesta delle statistiche di una partita attiva
    {
  		"operation": "requestGameStats",
  		"err_code": 0,
  		"gameStats": {
	  		"time": LONG,
	    	"currentPlayers": INT,
	    	"compPlayers": INT,
	    	"winPlayers": INT,
  		}
	}
    
    */
    public static String replyGameStats(Error err, long time, int currPlayers, int totPlayers, int compPlayers, int winPlayers, float avgPoints)
    {
    	try (StringWriter sw = new StringWriter(); JsonWriter jw = new JsonWriter(sw)) {
    		jw.beginObject();
    		writeHeader(jw, "requestGameStats", err.getCode());
    		writeGameStats(jw, time, currPlayers, totPlayers, compPlayers, winPlayers, avgPoints);
    		jw.endObject();
    		return sw.toString();
    	}
    	catch(IOException ex) { return null; }
    }
    
    /*
    Classifica dei primi K utenti o classifica globale
    {
    	  "operation": "requestLeaderboard",
    	  "err_code": INT,
    	  "scoreboard": [
    	    STRING,
    	    STRING,
    	    STRING
    	  ]
    }
    */
    public static String replyLeaderboard(Error err, List<User> scoreboard)
    {
    	try(StringWriter sw = new StringWriter(); JsonWriter jw = new JsonWriter(sw)) {
    		jw.beginObject();
    		writeHeader(jw, "requestLeaderboard", err.getCode());
    		writeScoreboard(jw, scoreboard);
    		jw.endObject();
    		return sw.toString();
    	}
    	catch(IOException ex) { return null; }
    }
    
    /*
    Posizione in classifica di un utente specifico
    {
    	  "operation": "requestLeaderboard",
    	  "err_code": INT,
    	  "rank": INT,
    	  "username": STRING
    }
    */
    public static String replyPlayerLeaderboard(Error err, User user, int rank)
    {
    	try(StringWriter sw = new StringWriter(); JsonWriter jw = new JsonWriter(sw)) {
    		jw.beginObject();
    		writeHeader(jw, "requestLeaderboard", err.getCode());
    		writePlayerScoreboard(jw, user.getUsername(), rank);
    		jw.endObject();
    		return sw.toString();
    	}
    	catch(IOException ex) { return null; }
    }
    
    /*
    Statistiche di un utente
    {
    	  "operation": "requestPlayerStats",
    	  "err_code": INT,
    	  "playedGames": INT,
    	  "wonGames": INT,
    	  "winRate": DOUBLE,
    	  "lossRate": DOUBLE,
    	  "currentStreak": INT,
    	  "maxStreak": INT,
    	  "perfectGames": INT,
    	  "points": INT,
    	  "histogram": [
    	    INT,
    	    INT,
    	    INT
    	  ]
    }
    */
    public static String replyPlayerStats(Error err, User user)
    {
    	try(StringWriter sw = new StringWriter(); JsonWriter jw = new JsonWriter(sw)) {
    		jw.beginObject();
    		writeHeader(jw, "requestPlayerStats", err.getCode());
    		writePlayerStats(jw, user);
    		jw.endObject();
    		return sw.toString();
    	}
    	catch(IOException ex) { return null; }
    }
    
    /*
    Generica risposta del server a una richiesta client.
    Utilizzata nel caso di errore o risposte ad operazioni semplici come registrazione, aggiornamento credenziali e logout.
    {
    	  "operation": STRING,
    	  "err_code": INT,
    }
    */
    
    public static String reply(String operation, Error error) {
        try (StringWriter sw = new StringWriter(); JsonWriter jw = new JsonWriter(sw)) {
            jw.beginObject();
            writeHeader(jw, operation, error.getCode());
            jw.endObject();
            return sw.toString();
        } catch (IOException ex) { return null; }
    }
    
    /*
     * Crea un JSON per il file .log degli utenti
     * Esempio: {"operation":"updatePsw", "first":"username", "second":"newPassword"}
     */
    
    public static String addToUsersLog(String operation, String id, String first, String second) {
        return buildJson(operation, "id", id,"first", first, "second", second);
    }
    
    //		--- Metodi chiamati dai client ---

    /*
    Richiesta di login:
    {
    	  "operation": "login",
    	  "username": STRING,
    	  "psw": STRING,
    	  "unicastPort": STRING
    }
    */
    public static String login(String username, String password, int unicastPort) { return buildJson("login", "username", username, "psw", password, "unicastPort", Integer.toString(unicastPort)); }
    
    /*
    Richiesta di registrazione:
    {
    	  "operation": "register",
    	  "name": STRING,
    	  "psw": STRING
    } 
    */
    public static String register(String username, String password) { return buildJson("register", "name", username, "psw", password); }
    
    /*
    Un giocatore decide di prendere parte a una nuova partita. Questa risposta sarà
    inviata a seguito della ricezione di un pacchetto Multicast UDP.
    {
    	"operation": "playAgain"
    }
    */
    public static String requestPlayerStats(String username) { return buildJson("requestPlayerStats"); }
    
    /*
    Richiesta di logout
    {
    	"operation": "logout"
    }
    */
    public static String logout() { return buildJson("logout"); }
    
    public static String quit() { return buildJson("quit"); }
    
    /*
    Solo aggiornamento password
    {
    	  "operation": "updateCredentials",
    	  "oldPsw": STRING,
    	  "newPsw": STRING
    }
    */
    public static String updatePassword(String oldPsw, String newPsw) { 
        return buildJson("updateCredentials", "oldPsw", oldPsw, "newPsw", newPsw); 
    }

    /*
    Solo aggiornamento username
    {
    	  "operation": "updateCredentials",
    	  "oldName": STRING,
    	  "newName": STRING
    }
    */
    public static String updateUsername(String oldName, String newName) { 
        return buildJson("updateCredentials", "oldName", oldName, "newName", newName); 
    }

    /*
    Aggiornamento di entrambi
    {
    	  "operation": "updateCredentials",
    	  "oldName": STRING,
    	  "newName": STRING,
    	  "oldPsw": STRING,
    	  "newPsw": STRING
    }
    */
    public static String updateBoth(String oldName, String newName, String oldPsw, String newPsw) { 
        return buildJson("updateCredentials", 
                         "oldName", oldName, 
                         "newName", newName, 
                         "oldPsw", oldPsw, 
                         "newPsw", newPsw); 
    }
    
    public static String proposal(String[] words) {
        return buildProposal(words);
    }
    
    /*
    Richiesta di informazioni di una partita:
    {
    	"operation": "requestGameInfo",
    	"gameId": INT
    }
    
    Richiesta delle statistiche collettive di una partita:
    {
  		"operation": "requestGameStats",
  		"gameId": INT
	}
    */
    public static String requestGame(String operation, int id) {
        try (StringWriter sw = new StringWriter();
             JsonWriter jw = new JsonWriter(sw)) {
            
            jw.beginObject();
            jw.name("operation").value(operation);
            jw.name("gameId").value((Integer) id);
            jw.endObject();
            return sw.toString();
        } catch (IOException ex) {
            return null;
        }
    }
    
    /*
    Richieste di classifica.
    Nel caso si richieda la posizione precisa di un giocatore, il JSON inviato dal client sarà il seguente:
    {
    	 "operation": "requestLeaderboard"
    	 "playerName": STRING
    }
    
    Nel caso si richieda i primi K giocatori il JSON sarà il seguente:
    {
  		 "operation": "requestLeaderboard"
  		 "topPlayers": INT
	}
	
	Nel caso si desideri la classifica globale, il valore di topPlayers sarà 0.
    */	
    public static String requestLeaderboard(Object T) {
    	try(StringWriter sw = new StringWriter();
    		JsonWriter jw = new JsonWriter(sw))
    	{
    		jw.beginObject();
    		jw.name("operation").value("requestLeaderboard");
    		
    		// Voglio la posizione di un utente specifico
	    	if(T instanceof String) { jw.name("playerName").value((String) T); }
	    	
	    	// Voglio la top K della classifica
	    	else if(T instanceof Integer) { jw.name("topPlayers").value((int) T); }
	    	
	    	else return null;
	    	
	    	jw.endObject();
	    	return sw.toString();
    	}
    	catch(IOException ex) { return null; }
    }
    
    public static String unicastEndGame(PlayerGameStats pStats, int completed, int current, int total, int winning, float average) {
    	try(StringWriter sw = new StringWriter();
    		JsonWriter jw = new JsonWriter(sw))
    	{
    		jw.beginObject();
    		writeGameStats(jw,(long) -1, current, total, completed, winning, average);
    		writeGamePlayerStats(jw, pStats.getErrors(), pStats.getPoints(), pStats.getGuessedGroups());
    		jw.endObject();
    		return sw.toString();
    	}
    	catch(IOException ex) { return null; }
    }
}