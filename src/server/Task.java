package server;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.stream.JsonReader;

import common.Error;
import common.JsonBuilder;
import common.Session;
import common.User;

public class Task implements Runnable{

	private final SelectionKey key;
    private final Session thisSession;
    private final String request;
    private final ConcurrentHashMap<String, User> users;
    private final ConcurrentHashMap<Integer, GameStats> games;
    private final ConcurrentHashMap<Integer, List<InetSocketAddress>> loggedUsersAddresses;
    
    private final GameSession currentGameSession;
	
	Task(SelectionKey key, String request, GameSession currentGameSession, ConcurrentHashMap<String,User> users,
			ConcurrentHashMap<Integer,GameStats> gameStats, ConcurrentHashMap<Integer, List<InetSocketAddress>> loggedUsersAddresses)
	{
		this.key = key;
		this.thisSession = (Session) key.attachment();
		this.users = users;
		this.games = gameStats;
		this.request = request;
		this.currentGameSession = currentGameSession;
		this.loggedUsersAddresses = loggedUsersAddresses;
	}
	
	@Override
	public void run() {
	    String reply = parseJSON(request);	// Parsing della richiesta
	    if(reply != null) prepareReply(reply);	// Preparazione della risposta
	}
	
	/* La risposta al client verrà inviata dal thread principale. La risposta sarà inserita in un byte array associato al SocketChannel. */
	private void prepareReply(String reply) {
		    byte[] bytes = reply.getBytes(StandardCharsets.UTF_8);
		    thisSession.setPendingBytes(bytes);
		    thisSession.setPendingOffset(0);
		    key.interestOps(SelectionKey.OP_WRITE);
		    key.selector().wakeup();
	}
	
	private String parseJSON(String jsonString) {
	    try (JsonReader jreader = new JsonReader(new StringReader(jsonString))) {
	        jreader.beginObject();
	        String name = jreader.nextName();
	        if(name.equals("operation")) {
	            String value = jreader.nextString();
	            switch(value) {
	                case "updateCredentials": return JsonBuilder.reply("updateCredentials", updateCredentials(jreader));
	                case "login": return handleLogin(jreader);
	                case "submitProposal": return handleSubmitProposal(jreader);
	                case "requestGameInfo": return handleRequestGameInfo(jreader);
	                case "requestGameStats": return handleRequestGameStats(jreader);
	                case "requestLeaderboard": return handleRequestLeaderboard(jreader);
	                case "requestPlayerStats": return handleRequestPlayerStats(jreader);
	                case "playAgain": return handlePlayAgain(jreader);
	                case "logout":           return JsonBuilder.reply("logout", logout(jreader));
	                case "quit":			 return handleQuit(jreader);
	                default:                 return JsonBuilder.reply("", Error.INVALID_JSON_FORMAT);
	            }
	        }
	        
	        return JsonBuilder.reply("", Error.INVALID_JSON_FORMAT);
	    }
	    catch(IOException ex) { return JsonBuilder.reply("", Error.INVALID_JSON_FORMAT); }
	}
	
	//		--- Handlers delle operazioni ---
	
	private String handleLogin(JsonReader jreader) {
	    Error err = login(jreader);
	    if (err != Error.SUCCESS) return JsonBuilder.reply("login", err);

	    User user = thisSession.getUser();
	    
	    PlayerGameStats pStats;
	    long timeleft;
	    int gameId;

	    currentGameSession.read.lock();
	    try {
	        if (!currentGameSession.isActive()) {
	            // Login riuscito, ma la partita non è attiva
	            return JsonBuilder.reply("login", Error.GAME_UNAVAILABLE);
	        }

	        pStats = currentGameSession.registerPlayer(user);
	        if (pStats == null) {
	            // Se per qualche motivo non ho registrato l'utente alla partita, errore
	            thisSession.setUser(null);
	            return JsonBuilder.reply("login", Error.UNKNOWN_ERROR);
	        }

	        timeleft = currentGameSession.timeLeft();
	        gameId = currentGameSession.getGame().getGameID();
	    }
	    finally {
	    	currentGameSession.read.unlock();
	    }
	        // Login avvenuto con successo.
	        return JsonBuilder.loginSuccess(timeleft, gameId, pStats);
	}
	
	//		--- Il client ha inviato una proposta ---
	private String handleSubmitProposal(JsonReader jreader) {
	    if (thisSession.getUser() == null) 
	        return JsonBuilder.reply("submitProposal", Error.USER_NOT_LOGGED);
	    
	    List<String> wordsList = parseProposalWords(jreader);
	    if (wordsList == null) return JsonBuilder.reply("submitProposal", Error.INVALID_JSON_FORMAT);
	    
	    Set<String> proposalSet = new HashSet<>(wordsList);
	    if (proposalSet.size() != 4) 
	        return JsonBuilder.reply("submitProposal", Error.INVALID_PROPOSAL_FORMAT);
	    
	    int userId = thisSession.getUser().getId();

	    PlayerGameStats ps = currentGameSession.getPlayerStatsMap().get(userId);
	    if (ps == null) {
	        return JsonBuilder.reply("submitProposal", Error.UNKNOWN_ERROR);
	    }

	    currentGameSession.read.lock();
	    try {
	        if (!currentGameSession.isActive()) 
	            return JsonBuilder.reply("submitProposal", Error.GAME_ALREADY_COMPLETED);

	        synchronized(ps) {
	            
	            if (currentGameSession.isPlayerFinished(userId))
	                return JsonBuilder.reply("submitProposal", Error.GAME_ALREADY_COMPLETED);

	            if (!currentGameSession.getGame().getAllWords().containsAll(proposalSet))
	                return JsonBuilder.reply("submitProposal", Error.INVALID_PROPOSAL_FORMAT);

	            Error error = processGroupComparison(userId, proposalSet, currentGameSession.getGame());

	            if (error == Error.WRONG_PROPOSAL || error == Error.CORRECT_PROPOSAL) {
	                if (ps.isCompleted()) {
	                    String esito = ps.isWon() ? "Complimenti, hai vinto!" : "Mi dispiace, hai perso!";
	                    LiveStats ls = currentGameSession.getLiveStats();
	                    return JsonBuilder.replyProposal(
	                        error, 
	                        esito, 
	                        ps, 
	                        ls.currPlayers,   
	                        ls.totalPlayers,     
	                        ls.completedPlayers, 
	                        ls.winPlayers,   
	                        ls.avgPoints      
	                    );
	                }
	            }
	            return JsonBuilder.reply("submitProposal", error);
	        }
	    } finally {
	        currentGameSession.read.unlock();
	    }
	}
	
	//		--- Un client ha richiesto l'esito/informazioni di una partita ---
	private String handleRequestGameInfo(JsonReader jreader) {
	    if (thisSession.getUser() == null)
	        return JsonBuilder.reply("requestGameInfo", Error.USER_NOT_LOGGED);

	    int targetId = parseGameId(jreader);
	    if (targetId < 0)
	        return JsonBuilder.reply("requestGameInfo", Error.INVALID_JSON_FORMAT);

	    int userId = thisSession.getUser().getId();

	   currentGameSession.read.lock();
	   try {
	        if (currentGameSession.getGame().getGameID() == targetId) {

	            PlayerGameStats pStats = currentGameSession.getPlayerStatsMap().get(userId);
	            if (pStats == null)
	                return JsonBuilder.reply("requestGameInfo", Error.PLAYER_DID_NOT_PARTICIPATE);

	            // Se la partita è ancora attiva E il giocatore non ha finito, invio informazioni partita attiva
	            if (currentGameSession.isActive() && !pStats.isCompleted()) {
	                return JsonBuilder.replyGameInfoActive(
	                    Error.SUCCESS,
	                    currentGameSession.timeLeft(),
	                    pStats,
	                    currentGameSession.getGame().getGameID()
	                );
	            }

	            // Se non è attiva o finita, invio informazioni partita terminata
	            return JsonBuilder.replyGameInfoFinished(
	                Error.SUCCESS,
	                currentGameSession.getGame().getGroups(),
	                pStats
	            );
	        }
	    }
	   finally {
		   currentGameSession.read.unlock();
	   }
	    // Partita già terminata
	    GameStats historicalGame = games.get(targetId);
	    if (historicalGame == null)
	        return JsonBuilder.reply("requestGameInfo", Error.GAMEID_NOT_FOUND);

	    PlayerGameStats pStats = historicalGame.getPlayerStats().get(userId);
	    if (pStats == null)
	        return JsonBuilder.reply("requestGameInfo", Error.PLAYER_DID_NOT_PARTICIPATE);

	    return JsonBuilder.replyGameInfoFinished(
	        Error.SUCCESS,
	        historicalGame.getGroups(),
	        pStats
	    );
	}
	
	//		--- Un client ha richiesto le statistiche collettive di una partita ---
	private String handleRequestGameStats(JsonReader jreader) {
	    int targetId = parseGameId(jreader);
	    if (targetId < 0)
	        return JsonBuilder.reply("requestGameStats", Error.INVALID_JSON_FORMAT);

	    long timeToSend = -1;
	    int currentPlayers = -1;
	    int totalPlayers = -1;
	    int completedPlayers = -1;
	    int winningPlayers = -1;
	    float avgPoints = -1;

	    // Partita corrente
	    currentGameSession.read.lock();
	    try {
	        if (currentGameSession.getGame().getGameID() == targetId && currentGameSession.isActive()) {
	        	
	        	LiveStats ls = currentGameSession.getLiveStats();

	            return JsonBuilder.replyGameStats(Error.SUCCESS, ls.timeLeft, ls.currPlayers,
	                                              -1, ls.completedPlayers, ls.winPlayers, -1);
	        }
	    }
	    finally {
	    	currentGameSession.read.unlock();
	    }

	    // Partita già terminata
	    GameStats gs = games.get(targetId);
	    if (gs == null)
	    return JsonBuilder.reply("requestGameStats", Error.GAMEID_NOT_FOUND);

	    totalPlayers     = gs.getTotalPlayers();
	    completedPlayers = gs.getCompletedPlayers();
	    winningPlayers   = gs.getWinningPlayers();
	    avgPoints        = gs.getAvgPoints();

	    return JsonBuilder.replyGameStats(Error.SUCCESS, timeToSend, currentPlayers,
	                                      totalPlayers, completedPlayers, winningPlayers, avgPoints);
	}
	
	//		--- Un client ha richiesto la classifica ---
	private String handleRequestLeaderboard(JsonReader jreader)
	{
		Result result = requestLeaderboard(jreader);
    	if(result.getError() != Error.SUCCESS) return JsonBuilder.reply("requestLeaderboard", result.getError());
    	
    	// Creo una copia della classifica e opero su di essa
    	List<User> currentScoreboard = ServerMain.scoreboard;
    	
    	// E' stata richiesta la posizione di un utente specifico
    	if( result.getData() instanceof String )
    	{
    		User user = users.get(result.getData());
    		if(user == null) return JsonBuilder.reply("requestLeaderboard", Error.USER_NOT_FOUND);
    		
    		int rank = currentScoreboard.indexOf(user);
    		if(rank == -1) return JsonBuilder.reply("requestLeaderboard", Error.USER_NOT_FOUND);
    		
    		return JsonBuilder.replyPlayerLeaderboard(Error.SUCCESS, user, rank+1);
    	}
    	// E' stata richiesta la classifica dei primi K utenti
    	else if (result.getData() instanceof Integer)
    	{
    		int K = (int) result.getData();
    		if( K > currentScoreboard.size()) K = 0;
    		// Nel caso in cui si voglia la posizione di K > #utenti, darò la classifica intera
    		if( K == 0)
    		{
    			return JsonBuilder.replyLeaderboard(Error.SUCCESS, currentScoreboard);
    		}
    		else
    		{
    				List<User> subList = currentScoreboard.subList(0, K);
    				return JsonBuilder.replyLeaderboard(Error.SUCCESS, subList);
    		}
    	}
    	else return null;
    }
	
	//		--- Un client ha richiesto le proprie statistiche globali ---
	private String handleRequestPlayerStats(JsonReader jreader)
	{
		Result result = requestPlayerStats(jreader);
    	if(result.getError() != Error.SUCCESS) return JsonBuilder.reply("requestPlayerStats", result.getError());
    	
    	User u = (User) result.getData();
    	synchronized(u)
    	{
    		return JsonBuilder.replyPlayerStats(Error.SUCCESS, u);
    	}
	}
	
	/*
	Il server invia al client le informazioni sulla nuova partita, qualora decidesse di prenderne parte.
	Questo JSON è analogo a quello del login.
	{
		  "operation": "playAgain",
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
		  "gameId": INT,
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
	//		--- Un client ha deciso di prendere parte alla nuova partita dopo aver ricevuto una notifica tramite Multicast UDP ---
	private String handlePlayAgain(JsonReader jreader) {
	    User user = thisSession.getUser();
	    if (user == null) return JsonBuilder.reply("playAgain", Error.USER_NOT_LOGGED);

	    currentGameSession.read.lock();
	    try {
	        if (!currentGameSession.isActive())
	            return JsonBuilder.reply("playAgain", Error.UNKNOWN_ERROR);

	        PlayerGameStats pStats = currentGameSession.registerPlayer(user);
	        if (pStats == null)
	            return JsonBuilder.reply("playAgain", Error.UNKNOWN_ERROR);

	        return JsonBuilder.playAgainSuccess(
	            currentGameSession.timeLeft(),
	            currentGameSession.getGame().getGameID(),
	            pStats
	        );
	    }
	    finally {
	    	currentGameSession.read.unlock();
	    }
	}
	
	//		--- Un client ha deciso di NON prendere parte alla nuova partita dopo aver ricevuto una notifica tramite Multicast UDP ---
	private String handleQuit(JsonReader jreader) {
		try {
			while(jreader.hasNext()) jreader.skipValue();
			SocketChannel channel =(SocketChannel) key.channel();
			
			System.out.println(channel.getRemoteAddress().toString().replace("/","") + " si è disconnesso");
			
			key.cancel();
			key.channel().close();
		}
		catch(IOException ex) {}
		return null;
	}
	
	private Error updateCredentials(JsonReader jreader) {
	    if(thisSession.getUser() == null) return Error.USER_NOT_LOGGED;
	    
	    String oldName = null, newName = null, oldPsw = null, newPsw = null;

	    try {
	        while (jreader.hasNext()) {
	            String name = jreader.nextName();
	            switch (name) {
	                case "newName": newName = jreader.nextString(); break;
	                case "oldName": oldName = jreader.nextString(); break;
	                case "oldPsw":  oldPsw  = jreader.nextString(); break;
	                case "newPsw":  newPsw  = jreader.nextString(); break;
	                default:        jreader.skipValue(); break; 
	            }
	        }
	        jreader.endObject();

	        if (oldName == null && oldPsw == null) return Error.INVALID_JSON_FORMAT;
	        
	        return applyCredentialsUpdate(newName, oldName, oldPsw, newPsw);
	    } 
	    catch (IOException | IllegalStateException e) { 
	        return Error.INVALID_JSON_FORMAT; 
	    }
	}

	private Error applyCredentialsUpdate(String newName, String oldName, String oldPsw, String newPsw) {
	    User u = users.get(thisSession.getUser().getUsername());
	    if (u == null) return Error.USER_NOT_FOUND;

	    // Per cambiare username dovrò creare una nuova voce nella mappa e rimuovere quella vecchia.
	    // E' quindi necessario prendere la lock sia sulla mappa degli utenti che sull'utente singolo da cambiare.
	    if (oldName != null && newName != null && !oldName.equals(newName)) {
	            synchronized (u) {
	                if (oldPsw != null) {
	                    Error errPsw = changePassword(u, oldPsw, newPsw);
	                    if (errPsw != Error.SUCCESS) return errPsw;
	                }
	                Error errName = changeUsername(u, oldName, newName);
	                if (errName != Error.SUCCESS) return errName;
	            }
	    } else if (oldPsw != null) {
	        synchronized (u) {
	            Error errPsw = changePassword(u, oldPsw, newPsw);
	            if (errPsw != Error.SUCCESS) return errPsw;
	        }
	    }

	    thisSession.setUser(null); 
	    return Error.SUCCESS;
	}

	private Error changePassword(User u, String oldPsw, String newPsw) {
	    if (newPsw == null) return Error.INVALID_JSON_FORMAT;
	    if (!u.getPassword().equals(oldPsw)) return Error.INCORRECT_PASSWORD;

	    Error error = Validator.checkPasswordValidity(newPsw);
	    if (error != Error.SUCCESS) return error;
	    
	    File tempfile = new File(ServerMain.resourcesDirectory, "users_temp.log");
	    ServerMain.tempUsersLock.lock();
	        try (BufferedWriter bw = new BufferedWriter(new FileWriter(tempfile, true))) {
	            bw.write(JsonBuilder.addToUsersLog("updatePsw", "-1", u.getUsername(), newPsw));
	            bw.newLine();
	            bw.flush();
	            // Aggiorno la password in memoria SOLO dopo il successo del log
	            u.setPassword(newPsw);
	            return Error.SUCCESS;
	        } catch (IOException ex) {
	            return Error.UNKNOWN_ERROR;
	        }
	        finally { ServerMain.tempUsersLock.unlock(); }
	}

	private Error changeUsername(User u, String oldName, String newName) {
	    if (newName == null) return Error.INVALID_JSON_FORMAT;
	    
	    Error error = Validator.checkUsernameValidity(newName);
	    if (error != Error.SUCCESS) return error;

	    if (users.putIfAbsent(newName, u) != null) {
	        return Error.USER_ALREADY_EXISTS;
	    }

	    File tempfile = new File(ServerMain.resourcesDirectory, "users_temp.log");
	    ServerMain.tempUsersLock.lock();
	    try (BufferedWriter bw = new BufferedWriter(new FileWriter(tempfile, true))) {
	        bw.write(JsonBuilder.addToUsersLog("updateUsername", "-1", oldName, newName));
	        bw.newLine();
	        bw.flush();
	            
	        // Aggiorno la mappa e l'oggetto in memoria
	        users.remove(oldName);
	        u.setUsername(newName);
	        return Error.SUCCESS;
	    } catch (IOException ex) {
	        return Error.UNKNOWN_ERROR;
	    }
	    finally {ServerMain.tempUsersLock.unlock();}
	}
	
	private Error login(JsonReader jreader) {
	    String username = null;
	    String password = null;
	    int unicastPort = -1;
	    
	    try {
	        while(jreader.hasNext()) {
	            String name = jreader.nextName();
	            switch(name) {
	                case "username": username = jreader.nextString(); break;
	                case "psw":      password = jreader.nextString(); break;
	                case "unicastPort":		unicastPort = Integer.parseInt(jreader.nextString()); break;
	                default:         jreader.skipValue();             break;
	            }
	        }
	        jreader.endObject();
	        
	        if (username == null || password == null)
	            return Error.INVALID_JSON_FORMAT;

	        return checkLoginValidity(username, password, unicastPort);
	    }
	    catch(IOException | IllegalStateException e) {
	        return Error.INVALID_JSON_FORMAT;
	    }
	}

	private Error checkLoginValidity(String username, String password, int unicastPort) {
	    User u = users.get(username);
	    if (u == null) return Error.USER_NOT_FOUND;

	    synchronized (u) {
	        if(!u.getPassword().equals(password)) {
	            return Error.INCORRECT_PASSWORD;
	        }
	        
	        if(thisSession.getUser() != null) {
	            return Error.USER_ALREADY_LOGGED;
	        }
	        thisSession.setUser(u);
	    }
	    
	    // Se un utente si autentica con successo, salvo il suo indirizzo per Unicast UDP nella struttura apposita.
	    try {
	    	SocketChannel channel = (SocketChannel) key.channel();
	    	InetAddress address = ((InetSocketAddress) channel.getRemoteAddress()).getAddress();
	    	InetSocketAddress unicastAddress = new InetSocketAddress(address, unicastPort);
	    	thisSession.setUnicastAddress(unicastAddress);
	    	
	    	addToLoggedUsers(thisSession.getUser().getId(), unicastAddress);
	    }
	    catch(IOException ex) {return Error.UNKNOWN_ERROR; /* Impossibile registrare l'indirizzo utente */}
        
	    return Error.SUCCESS;
	}
	
	private Error logout(JsonReader jreader) {
	    try {
	        if(thisSession.getUser() == null) return Error.USER_NOT_LOGGED;
	        
	        while(jreader.hasNext()) jreader.skipValue();
	        jreader.endObject();
	        
	        removeFromLoggedUsers(thisSession.getUser().getId(), thisSession.getUnicastAddress());
	        thisSession.setUser(null);
	        return Error.SUCCESS;
	    }
	    catch(IOException | IllegalStateException e) {
	        return Error.INVALID_JSON_FORMAT;
	    }
	}
	
	private List<String> parseProposalWords(JsonReader jreader) {
	    List<String> words = new ArrayList<>();
	    try {
	        if (jreader.nextName().equals("words")) {
	            jreader.beginArray();
	            while (jreader.hasNext()) words.add(jreader.nextString().toUpperCase());
	            jreader.endArray();
	        }
	        jreader.endObject();
	        return words;
	    } catch (IOException | IllegalStateException e) {
	        return null;
	    }
	}

	//		--- Metodi di supporto per le proposte ---
	private Error processGroupComparison(int userId, Set<String> proposal, Game game) {
	    for (GroupWords group : game.getGroups()) {
	        Set<String> groupSet = group.getWords();
	        if (proposal.equals(groupSet)) {
	            return currentGameSession.correctProposal(userId, group);
	        }
	    }
	    return currentGameSession.wrongProposal(userId);
	}
	
	private int parseGameId(JsonReader jreader) {
	    try {
	        while(jreader.hasNext()) {
	            String name = jreader.nextName();
	            if (name.equals("gameId")) {
	                return jreader.nextInt();
	            } else {
	                jreader.skipValue();
	            }
	        }
	        jreader.endObject();
	    } catch(IOException | IllegalStateException e) {
	        return -1;
	    }
	    return -1;
	}
	
	private Result requestLeaderboard(JsonReader jreader) {
		/*
		 * Il campo può essere "playerName" o "topPlayers". In base a ciò eseguo due operazioni differenti.
		 */
		Result r = new Result();
		
		try {
			String name = jreader.nextName();
			if(name.equals("playerName"))
			{
				// Cerco un giocatore singolo
				r.setData(jreader.nextString());
				r.setError(Error.SUCCESS);
			}
			else if(name.equals("topPlayers"))
			{
				// Cerco la top K giocatori
				r.setData(jreader.nextInt());
				r.setError(Error.SUCCESS);
			}
			else
			{
				r.setError(Error.INVALID_JSON_FORMAT);
				return r;
			}
		}
		catch(IOException ex) { r.setError(Error.UNKNOWN_ERROR);}
		return r;
	}
	
	private Result requestPlayerStats(JsonReader jreader) {
		
		Result result = new Result();
		
		try {
			while(jreader.hasNext())
			{
				jreader.skipValue();
			}
			jreader.endObject();
			
			User u = thisSession.getUser();
			if(u == null) {
				result.setError(Error.USER_NOT_LOGGED);
				return result;
			}
			result.setData(u);
			result.setError(Error.SUCCESS);
			return result;
		}
		catch(IOException ex) {}
		return null;
	}
	
	private void addToLoggedUsers(int userId, InetSocketAddress userAddress) {
		List<InetSocketAddress> list = loggedUsersAddresses.computeIfAbsent(userId, k -> Collections.synchronizedList(new ArrayList<>()));
		list.add(userAddress);
	}
	
	private void removeFromLoggedUsers(int userId, InetSocketAddress userAddress) {
	    loggedUsersAddresses.compute(userId, (k, list) -> {
	        if(list == null) return null;
	        synchronized(list) {
	            list.remove(userAddress);
	            return list.isEmpty() ? null : list;
	        }
	    });
	}
}
