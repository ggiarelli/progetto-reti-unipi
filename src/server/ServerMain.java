package server;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.Type;
import java.net.BindException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.*;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import common.CustomProperties;
import common.JsonBuilder;
import common.Session;
import common.User;

public class ServerMain {

	//		--- Variabili di configurazione di default ---
	private static int listeningPort = 7000;
	private static int registerPort = 7001;		// Porta su cui aprire il register.
	private static int multicastPort = 8000;	// Porta del multicast UDP
	private static String multicastAddress = "233.0.0.1";
	private static String registerService = "REGISTER-SERVICE";
	private static int bufferSize = 4096;
	static int gameTime = 5;
	static int lastGameIndex = 0;
	static AtomicInteger nextUserId = new AtomicInteger();
	
	//		--- Riferimenti alle directory principali
	static File rootDirectory = new File(".");
	static File resourcesDirectory = new File(rootDirectory, "resources");
	
	static DatagramSocket ds;
	
	static Lock tempUsersLock = new ReentrantLock();	// Lock necessaria per sincronizzare le scritture sul file users_temp.log
	
	static ConcurrentHashMap<String,User> users;		// Struttura in cui terrò gli utenti.
	static ConcurrentHashMap<Integer,GameStats> gameStats;		// Struttura in cui terrò le partite.
	static volatile ArrayList<User> scoreboard = new ArrayList<>();		// Struttura che rappresenta la classifica.
	
	/* Struttura che tiene traccia degli indirizzi su cui inviare pacchetti UDP qualora servisse.
	 * Ogni utente può autenticarsi da diversi dispositivi, quindi per ognuno è segnata una lista di indirizzi. */
	 
	static ConcurrentHashMap<Integer, List<InetSocketAddress>> loggedUsersAddresses = new ConcurrentHashMap<>();
	
	public static void main(String[] args) {
		
		/* Threadpool dinamico: i thread vengono creati all'aumentare delle richieste e rilasciati quando inattivi */
		ExecutorService threadpool = Executors.newCachedThreadPool();
		
		//		--- Un oggetto GameManager si occuperà della gestione delle partite. ---
	    GameManager gm;
		
	    try {
	        //		--- Inizializzo e carico dati dai file ---
	        readProperties();
	        
	        Type userType = new TypeToken<ConcurrentHashMap<String,User>>(){}.getType();
	        users = readFromJson(new File(resourcesDirectory, "users.json"), userType, new ConcurrentHashMap<String,User>());

	        /* A ogni utente sarà associato un ID. Imposto inizialmente il contatore al numero di utenti già presente. */
	        nextUserId.set(users.size());
	        
	        Type gamesType = new TypeToken<ConcurrentHashMap<Integer,GameStats>>(){}.getType();
	        gameStats = readFromJson(new File(resourcesDirectory, "games.json"), gamesType, new ConcurrentHashMap<Integer,GameStats>());
	        
	        FileUpdater updater = new FileUpdater(users, gameStats, resourcesDirectory);
	        
	        /* "users_temp.log" funge da log delle operazioni più recenti sugli utenti.
	         * Se presente, viene esaminato per recuperare aggiornamenti non ancora
	         * salvati nel file principale, utile in caso di interruzione del server. */
	        File tempFile = new File(resourcesDirectory, "users_temp.log");
	        if (tempFile.exists() && tempFile.length() > 0) {
	        	// Se il file esiste e non è vuoto, devo ricostruire users
	            recoverUsers(tempFile);
	            updater.writeNewUsersJson();
	        }
	        
	        scoreboardLoading();
	        
	        ds = new DatagramSocket();
	        gm = new GameManager(resourcesDirectory, lastGameIndex, users, gameStats);
	        
	        //		--- Thread schedulato che si eseguirà ogni fine partita ---
	        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
	        scheduler.scheduleAtFixedRate(() -> {
	        	// Effettua la chiusura della partita, rende persistenti le strutture sugli utenti e partite, carica una nuova partita.
	            gm.endGame();
	            sendUdpUnicast(gm.getCurrentGameSession());
	            updater.writeNewUsersJson();
	            updater.writeNewGameJson();
	            gm.newGame();
	            
	            // Invio UDP Multicast sulla fine partita, con classifica integrata.
	            sendUdpMulticast();
	        }, gameTime, gameTime, TimeUnit.MINUTES);
	        
	        //		--- Registrazione tramite RMI e NIO con Selector ---
	        registerSetup();
	        
	        ServerSocketChannel serverchannel = ServerSocketChannel.open();
	        serverchannel.configureBlocking(false);
	        serverchannel.socket().bind(new InetSocketAddress(listeningPort));
	        
	        Selector selector = Selector.open();
	        serverchannel.register(selector, SelectionKey.OP_ACCEPT);
	        
	        System.out.println("Server operativo.");
	        
	        
	        //		--- Configurazione completata. ---
	        while(true) {
	            int readKeys = selector.select();
	            if(readKeys == 0) continue;
	            
	            Set<SelectionKey> selected_keys = selector.selectedKeys();
	            Iterator<SelectionKey> iterator = selected_keys.iterator();
	            
	            while(iterator.hasNext()) {
	                SelectionKey key = iterator.next();
	                iterator.remove();
	                
	                if(key.isAcceptable()) {
	                    handleAccept(selector, key);
	                } else if(key.isReadable()) {
	                    String request = handleRead(key);
	                    if(request != null) {
	                        key.interestOps(0);
	                        /* Le richieste JSON del client vengono lasciate elaborare a un threadpool */
	                        threadpool.execute(new Task(key, request, gm.getCurrentGameSession(), users, gameStats, loggedUsersAddresses));
	                    }
	                } else if(key.isWritable()) {
	                    handleWrite(key);
	                }
	            }
	        }
	    }
	    catch(FileNotFoundException ex) {
	        System.err.println("File delle partite non trovato.");
	        System.exit(1);
	    }
	    catch (BindException ex) {
	    System.err.println("Porta già in uso.");
	    System.exit(1); 
	    }
	    catch(IOException ex) {
	    	System.err.println("Errore durante la configurazione del server.");
	        System.exit(1);
	    }
	}

	private static void handleAccept(Selector selector, SelectionKey key) 
	{
		try {
			ServerSocketChannel server = (ServerSocketChannel) key.channel();
			SocketChannel client = server.accept();
			client.configureBlocking(false);
			
			System.out.println(client.getRemoteAddress().toString().replace("/","") + " si è connesso");
			
			SelectionKey clientKey = client.register(selector, SelectionKey.OP_READ);
			
			Session s = new Session();
			
			s.setBuff(ByteBuffer.allocateDirect(bufferSize));
			
			clientKey.attach(s);
		}
		catch(IOException ex) {
			System.err.println("Impossibile accettare la connessione con un client.");
		}
	}
	
	public static String handleRead(SelectionKey key) {
		
		SocketChannel channel = (SocketChannel) key.channel();
	    Session s = (Session) key.attachment();
		
		try {
		    ByteBuffer buffer = s.getBuff();
	     
	        int bytesRead = channel.read(buffer);
	        if(bytesRead == -1) { 
	        	channel.close(); 
	        	key.cancel(); 
	        	
	        	// Se l'utente chiude la connessione, devo anche rimuovere il suo indirizzo per il traffico UDP.
	        	if(s.getUser() != null && s.getUnicastAddress() != null)
	        	{ removeUserAddress(s.getUser().getId(), s.getUnicastAddress()); }
	        	
	        	return null; 
	        	}
	        s.setCount(s.getCount() + bytesRead);
	        
	        // Non ho ancora i 4 byte della lunghezza
	        if(s.getCount() < Integer.BYTES) return null;

	        // Estraggo la lunghezza
	        if(s.getLength() == 0) {
	            buffer.flip();
	            s.setLength(buffer.getInt());
	            s.setPendingBytes(new byte[s.getLength()]);
	            s.setPendingOffset(0);
	            buffer.compact();
	        }

	        // Svuoto il buffer nell'array di accumulo
	        buffer.flip();
	        int chunk = Math.min(buffer.remaining(), s.getLength() - s.getPendingOffset());
	        buffer.get(s.getPendingBytes(), s.getPendingOffset(), chunk);
	        s.setPendingOffset(s.getPendingOffset() + chunk);
	        buffer.compact();

	        // Se non ho letto tutta la stringa, effettuerò un'altra lettura in futuro
	        if(s.getPendingOffset() < s.getLength()) return null;
	        String result = new String(s.getPendingBytes(), StandardCharsets.UTF_8);

	        // Reset per le prossime letture
	        s.setCount(0);
	        s.setLength(0);
	        s.setPendingBytes(null);
	        s.setPendingOffset(0);
	        buffer.clear();

	        return result;
	    }
		catch (IOException ex) {
	        System.err.println("Errore di I/O con un client");
	        
	        if(s.getUser() != null && s.getUnicastAddress() != null)
        	{ removeUserAddress(s.getUser().getId(), s.getUnicastAddress()); }
	        
	        try{key.channel().close(); key.cancel();} catch(IOException e) {}
	    }
	    return null;
	}
	
	private static void handleWrite(SelectionKey key) {
		
		SocketChannel channel = (SocketChannel) key.channel();
        Session s = (Session) key.attachment();
        
		try {
	        ByteBuffer buf = s.getBuff();
	        byte[] pending = s.getPendingBytes();
	        int offset = s.getPendingOffset();

	        // Prima invio la dimensione se sono all'inizio
	        if(offset == 0) {
	            buf.clear();
	            buf.putInt(pending.length);
	            buf.flip();
	            channel.write(buf);
	        }

	        // Invio un chunk del payload
	        buf.clear();
	        int chunk = Math.min(buf.capacity(), pending.length - offset);
	        buf.put(pending, offset, chunk);
	        buf.flip();
	        channel.write(buf);
	        offset += chunk;
	        s.setPendingOffset(offset);

	        // Se ho finito faccio un reset
	        if(offset >= pending.length) {
	            s.setPendingBytes(null);
	            s.setPendingOffset(0);
	            key.interestOps(SelectionKey.OP_READ);
	        }
	        buf.clear();
	    }
	    catch (IOException ex) {
	        System.err.println("Errore di I/O con un client.");
	        removeUserAddress(s.getUser().getId(), s.getUnicastAddress());
	        try{key.channel().close(); key.cancel(); } catch(IOException e) {}
	    }
	}
	
	private static void readProperties()
	{
		File configFile = new File(resourcesDirectory, "server.properties");
		
		try (FileReader reader = new FileReader(configFile)) {
			CustomProperties props = new CustomProperties();
			props.load(reader);
			registerPort = props.getIntProperty(props, "register.port", registerPort);		// Assegno la porta specificata nel .properties, altrimenti quella di default.
			listeningPort = props.getIntProperty(props, "listening.port", listeningPort);	// Analogo
			multicastPort = props.getIntProperty(props, "multicast.port", multicastPort);
			multicastAddress = props.getStringProperty(props, "multicast.address", multicastAddress);
			registerService = props.getStringProperty(props, "register.service", registerService);
			lastGameIndex = props.getIntProperty(props, "last.game.id", lastGameIndex);
			int lastUserId = props.getIntProperty(props, "last.user.id", 0);
			nextUserId.set(lastUserId);
			
			int gt = props.getIntProperty(props, "game.time", gameTime);
			int MAX_DURATION = 1440; // 24 ore in minuti
			int MIN_DURATION = 5;
			
			if(gt < MIN_DURATION || gt > MAX_DURATION) { System.out.println("La durata della partita deve essere almeno\n"
					+ "5 minuti, o massimo 24 ore. Valore di default: 10 minuti."); }
			else gameTime = gt;	
			
			int size = props.getIntProperty(props, "server.buffer.size", bufferSize);
			if(size < Integer.BYTES) System.out.println("Dimensione del buffer troppo piccola. Impostando la dimensione di default (4KB).");
			else bufferSize = size;
		}
		catch(IOException e) {System.err.println("File di configurazione non trovato.");}
	}
	
	//		--- Deserializza un file JSON nel tipo specificato ---
	private static <T> T readFromJson(File file, Type type, T defaultValue) {
	    try (BufferedReader br = new BufferedReader(new FileReader(file))) {
	        Gson gson = new Gson();
	        T result = gson.fromJson(br, type);
	        return result != null ? result : defaultValue;
	    }
	    catch(FileNotFoundException ex) { return defaultValue; }
	    catch(IOException ex) { return defaultValue; }
	}
	
	/* Recupera lo stato degli utenti dal file di log temporaneo.
	Chiamato all'avvio se il server si è interrotto prima di rendere persistenti le ultime modifiche sugli utenti */
	private static void recoverUsers(File tempFile)
	{
		String jsonString;
		try(BufferedReader br = new BufferedReader(new FileReader(tempFile)))
		{
			while( (jsonString = br.readLine()) != null )
			{
				RecoveryOperation recoveryOp = recoverJson(jsonString);
				
				switch(recoveryOp.getOperation())
				{
				case("register"):
					User u = new User(recoveryOp.getFirst(), recoveryOp.getSecond(), recoveryOp.getId());
					users.putIfAbsent(recoveryOp.getFirst(), u);
					break;
				case "updateUsername":
				    User toRename = users.remove(recoveryOp.getFirst());
				    if (toRename != null) {
				        toRename.setUsername(recoveryOp.getSecond());
				        users.put(recoveryOp.getSecond(), toRename);
				    }
				    break;
				case "updatePsw":
				    User toUpdate = users.get(recoveryOp.getFirst());
				    if (toUpdate != null) toUpdate.setPassword(recoveryOp.getSecond());
				    break;
				}
			}
		}
		catch(IOException ex) { System.err.println("Impossibile recuperare gli ultimi aggiornamenti sugli utenti."); }
	}
	
	private static RecoveryOperation recoverJson(String jsonString) {
		RecoveryOperation ro = new RecoveryOperation();
		
		try(StringReader sr = new StringReader(jsonString); JsonReader jr = new JsonReader(sr))
		{
			// Esempio di json in questo file:
			// {"operation":"register","id":"0","first":"username","second":"password"}
			// {"operation":"updatePsw","id":"-1","first":"oldPsw","second":"newPsw"}
			// {"operation":"updateUsername","id":"-1","first":"oldName","second":"newName"}
			
			jr.beginObject();
			while(jr.hasNext())
			{
				switch(jr.nextName())
				{
				case("operation"):
					ro.setOperation(jr.nextString());
					break;
				case("id"):
					ro.setId(Integer.parseInt(jr.nextString()));
					break;
				case("first"):
					ro.setFirst(jr.nextString());
					break;
				case("second"):
					ro.setSecond(jr.nextString());
					break;
				default: jr.skipValue(); break;
				}
			}
			jr.endObject();
		}
		catch(IOException ex) {System.err.println("Impossibile recuperare gli ultimi aggiornamenti sugli utenti.");}
		
		return ro;
	}
	
	private static void registerSetup()
	{
		RegisterImpl register = new RegisterImpl(resourcesDirectory, users);
		
		try{
			RegisterInterface stub = (RegisterInterface) UnicastRemoteObject.exportObject(register, 0);
			
			LocateRegistry.createRegistry(registerPort);
			Registry r = LocateRegistry.getRegistry(registerPort);
			r.rebind(registerService, stub);
		}
		catch(RemoteException ex) { System.err.println("Impossibile avviare il modulo di registrazione."); System.exit(1); }
	}
	
	/* Ad ogni giocatore che ha preso parte all'ultima partita, il server invierà un datagramma UDP
	contenente le statistiche collettive ed individuali dell'ultima partita */
	private static void sendUdpUnicast(GameSession session)
	{
	    session.getPlayerStatsMap().forEach((userId, pStats) -> {
	        List<InetSocketAddress> addresses = loggedUsersAddresses.get(userId);
	        if(addresses == null) return;
	        
	        String json = JsonBuilder.unicastEndGame(
	            pStats,
	            session.completedPlayers.get(),
	            session.currentPlayers.get(),
	            session.totalPlayers.get(),
	            session.winningPlayers.get(),
	            session.getAveragePoints()
	        );
	        
	        byte[] data = json.getBytes(StandardCharsets.UTF_8);
	        
	            for(InetSocketAddress addr : addresses) {
	                try {
	                    DatagramPacket dp = new DatagramPacket(data, data.length, addr);
	                    ds.send(dp);
	                } catch(IOException ex) { System.err.println("Errore invio unicast userId: " + userId); }
	            }
	    });
	}
	
	/* Invia in multicast UDP la classifica aggiornata a tutti i client autenticati, al termine di ogni partita.
	 Inoltre, viene loro chiesto se vogliono prendere parte alla nuova partita. */
	private static void sendUdpMulticast()
	{
		List<User> currentScoreboard = scoreboard;
		try (StringWriter sw = new StringWriter();
			 JsonWriter jwriter = new JsonWriter(sw)){
			
			jwriter.beginObject();
			jwriter.name("scoreboard");
			jwriter.beginArray();
			for(User u: currentScoreboard)
			{
				jwriter.value(u.getUsername());
			}
			jwriter.endArray();
			jwriter.endObject();
			
			InetAddress address=InetAddress.getByName(multicastAddress);
			byte[] data = (sw.toString()).getBytes(StandardCharsets.UTF_8);
			
			DatagramPacket dp = new DatagramPacket(data, data.length, address, multicastPort);
			ds.send(dp);
		}
		catch(IOException ex) {System.err.println("Errore durante l'invio del pacchetto UDP.");}
	}
	
	// Caricamento della classifica all'avvio del server
	private static void scoreboardLoading() {
	    ArrayList<User> loaded = new ArrayList<>(users.values());
	    loaded.sort((u1, u2) -> {
	        int res = Integer.compare(u2.getPoints(), u1.getPoints());
	        if (res != 0) return res;
	        return u1.getUsername().compareTo(u2.getUsername());
	    });
	    scoreboard = loaded;
	}
	
	/* Rimuove l'indirizzo di un client dalla struttura dei logged users,
	chiamato in caso di logout o disconnessione.
	Se la lista risulta vuota, la voce viene rimossa dalla mappa. */
	private static void removeUserAddress(int userId, InetSocketAddress userAddress) {
		loggedUsersAddresses.compute(userId, (k, list) -> {
	        if(list == null) return null;
	        synchronized(list) {
	            list.remove(userAddress);
	            return list.isEmpty() ? null : list;
	        }
	    });
	}
	
}
