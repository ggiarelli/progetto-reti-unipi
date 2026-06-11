package client;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.ConnectException;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.channels.UnresolvedAddressException;
import java.rmi.NotBoundException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import common.CustomProperties;
import common.Error;
import common.JsonBuilder;
import common.User;
import server.RegisterInterface;

public class ClientMain {
	
	// Valori di default di alcune variabili di configurazione.	
	private static int serverPort = 7000;
	private static int registerPort = 7001;
	private static int multicastPort = 8000;
	private static String multicastAddress = "233.0.0.1";
	private static String registerService = "REGISTER-SERVICE";
	private static String serverAddress = "localhost";
	private static File rootDirectory = new File(".");
	private static File resourcesDirectory = new File(rootDirectory,"resources");
	private static RegisterInterface serverObject = null;
	private static int bufferSize = 4096;
	
	// Stato dell'applicazione
	private static ClientState state = ClientState.NOT_LOGGED;
	private static SocketChannel channel;
	private static User thisUser = new User("","",0);
	private static ByteBuffer buffer;
	private static MulticastReceiver multicastThread;
	private static UnicastReceiver unicastThread;
	private static Scanner sc = new Scanner(System.in);
	private static Printer printer = new Printer();
	private static ClientNetworkManager network;
	private static MulticastSocket ms;
	private static DatagramSocket ds;
	public static int currentGameID = -1;
	public static AtomicBoolean multicastFlag = new AtomicBoolean(false);
	public static AtomicBoolean unicastFlag = new AtomicBoolean(false);
	private static String pendingUsername;
	private static String pendingPassword;
	
	public static void main(String[] args) {
		
		Menu menu = new Menu(state);
		
		System.out.println("Benvenuto!");
		System.out.println("Preparazione dell'applicazione e connessione al server in corso..");
		
		try {
			// Fase di preparazione del client e connessione al server
			readProperties();
			buffer = ByteBuffer.allocateDirect(bufferSize);
			
			channel = SocketChannel.open();
			channel.connect(new InetSocketAddress(serverAddress, serverPort));
			
			network = new ClientNetworkManager(buffer, channel);
			
			ds = new DatagramSocket();
			ms = new MulticastSocket(multicastPort);
			initializeUdpUnicast();
			initializeUdpMulticast();
			
			serverObject = openRegistry();
		
		
			System.out.println("Connessione riuscita.");
			
			/*
			 * Di seguito possiamo implementare un menù testuale che permette la selezione di svariate funzioni.
			 */
			
			int option;
			int maxOption;
			String requestToSend;
			
			while(true)
			{
				checkAndHandleUdpNotification();
				
				maxOption = menu.print();
				option = readOption(maxOption);
					
				//		--- Ripeto il controllo sulla partita terminata, dal momento che potrei essermi fermato a lungo su readOption ---
			    checkAndHandleUdpNotification();
					
				requestToSend = dispatchMenuOption(option);
				if(requestToSend != null)		// Se non si sono verificati errori sarà diverso da null
				{ 
					if(!network.sendRequest(requestToSend)) break;		// La connessione è stata chiusa.
					if(!parseReply(network.receiveReply())) break;		// La connessione è stata chiusa.
					else menu.update(state);
				}
			}
		}
		catch(GameInterruptedException ex) {/*Il client viene terminato regolarmente*/}
		catch(UnresolvedAddressException ex) { 
		    System.err.println("Impossibile connettersi: l'indirizzo del server non è valido.");
		}
		catch(IllegalArgumentException | IOException ex) { 
		    System.err.println("Connessione interrotta: il server non risponde o la rete è assente.");
		}
		finally {
			System.out.println("Disconnessione in corso...");
		    try {
		    	if (ds != null) ds.close();
		    	if (ms != null) ms.close();
		    	if (unicastThread != null) unicastThread.interrupt();
		    	if (multicastThread != null) multicastThread.interrupt();
		    	if (channel != null) channel.close();
		    	if (sc != null) sc.close();
		    } catch (IOException ex) {}
		}
	}
	
	private static void readProperties()
	{
		File configFile = new File(resourcesDirectory, "client.properties");
		
		try (FileReader reader = new FileReader(configFile)) {
			CustomProperties props = new CustomProperties();
			props.load(reader);
			
			int port = props.getIntProperty(props, "server.register.port", registerPort);
			if(port < 1024 || port > 65535) System.out.println("Porta indisponibile. Impostando la porta di default (7000).");
			else registerPort = port;
			
			port = props.getIntProperty(props, "server.listening.port", serverPort);
			if(port < 1024 || port > 65535 || port == registerPort) System.out.println("Porta indisponibile. Impostando la porta di default (7001).");
			else serverPort = port;
			
			port = props.getIntProperty(props, "multicast.port", multicastPort);
			if(port < 1024 || port > 65535 || port == registerPort || port == serverPort) System.out.println("Porta per il multicast indisponibile."
					+ " Impostando la porta di default (8000).");
			else multicastPort = port;
			
			
			multicastAddress = props.getStringProperty(props, "multicast.address", multicastAddress);
			serverAddress = props.getStringProperty(props, "server.ip.address", serverAddress);
			registerService = props.getStringProperty(props, "server.register.service", registerService);
			
			int size = props.getIntProperty(props, "buffer.size", bufferSize);
			if(size < Integer.BYTES) System.out.println("Dimensione del buffer troppo piccola. Impostando la dimensione di default (4KB).");
			else bufferSize = size;
		}
		catch(IOException e) { /* Errore nella lettura del file di configurazione o file non trovato, si usano i dati di default */ }
	}
	
	private static String dispatchMenuOption(int choice) throws IOException, GameInterruptedException
	{
		
		if(state.equals(ClientState.NOT_LOGGED))
		{
			switch(choice)
			{
			case(1): handleClientRegister(); return null;
			case(2): return handleClientLogin();
			case(3): handleQuit();
			default: break;
			}
		}
		else if(state.equals(ClientState.LOGGED))
		{
			switch(choice)
			{
			case(1): return handleClientUpdateCredentials();	// Aggiornamento credenziali
			case(2): return handleNewProposal();	// Nuova proposta
			case(3): return handleRequestGameInfo();	// Informazioni individuali su una partita
			case(4): return handleRequestGameStats();	// Informazioni globali su una partita
			case(5): return handleRequestLeaderboard();	// Informazioni classifica
			case(6): return handleRequestPlayerStats();	// Informazioni statistiche individuali
			case(7): return handleClientLogout();	// Logout
			case(8): handleQuit();
			default: break;
			}
		}
		return null;
	}
	
	private static String handleClientRegister() throws ConnectException{
		try {
			if(serverObject == null)
			{
				System.err.println("Il servizio di registrazione è offline. Nuovo tentativo in corso..");
				throw new RemoteException();
			}
			
			System.out.print("USERNAME: ");
			String username = sc.nextLine();
			System.out.print("PASSWORD: ");
			String password = sc.nextLine();
			
			String registerJson = JsonBuilder.register(username, password);	// Contiene la stringa da inviare
			registerJson = serverObject.register(registerJson);	// Contiene l'esito della registrazione
			
			parseReply(registerJson);
		}
		catch(RemoteException ex) { System.err.println("Il servizio di registrazione è temporaneamente non disponibile."); }
		return null;
	}
	
	private static String handleClientLogin() {
		
		System.out.print("USERNAME: ");
		pendingUsername = sc.nextLine();
		System.out.print("PASSWORD: ");
		pendingPassword = sc.nextLine();
		return JsonBuilder.login(pendingUsername, pendingPassword, ds.getLocalPort());
	}
	
	private static String handleClientUpdateCredentials() {
	    if (thisUser == null) { System.out.println(Error.USER_NOT_LOGGED); return null; }

	    int maxOption = Menu.printCredentialsMenu();
	    int option = readOption(maxOption);

	    String oldName, newName, oldPsw, newPsw;

	    switch (option) {
	        case 1:
	            System.out.print("Vecchia password: "); oldPsw = sc.nextLine();
	            System.out.print("\nNuova password: ");   newPsw = sc.nextLine();
	            return JsonBuilder.updatePassword(oldPsw, newPsw);
	        case 2:
	            System.out.print("Vecchio username: "); oldName = sc.nextLine();
	            System.out.print("\nNuovo username: ");   newName = sc.nextLine();
	            return JsonBuilder.updateUsername(oldName, newName);
	        case 3:
	            System.out.print("Vecchio username: "); oldName = sc.nextLine();
	            System.out.print("\nNuovo username: ");   newName = sc.nextLine();
	            System.out.print("\nVecchia password: "); oldPsw = sc.nextLine();
	            System.out.print("\nNuova password: ");   newPsw = sc.nextLine();
	            return JsonBuilder.updateBoth(oldName, newName, oldPsw, newPsw);
	        case 4: return null;
	        default: return null;
	    }
	}
	
	private static String handleNewProposal()
	{
		String[] words = new String[4];
		for(int i = 0; i < words.length; i++)
		{
			System.out.print("Parola numero "+(i+1)+": ");
			words[i] = sc.nextLine().trim();
		}
		
		return JsonBuilder.proposal(words);
	}
	
	private static String handleRequestGameInfo()
	{
		int maxOption = Menu.printGameInfoMenu();
		int option = readOption(maxOption);
		switch(option)
		{
		case(1):
			return JsonBuilder.requestGame("requestGameInfo", currentGameID);
		case(2):
			System.out.print("ID partita: ");
			int Id = readOption(Integer.MAX_VALUE);
			System.out.println();
			return JsonBuilder.requestGame("requestGameInfo", Id);
		case(3):
			return null;
		}
		return null;
	}
	
	private static String handleRequestGameStats() {
		int maxOption = Menu.printGameInfoMenu();
		int option = readOption(maxOption);
		
		switch(option)
		{
		case(1):
			return JsonBuilder.requestGame("requestGameStats", currentGameID);
		case(2):
			System.out.print("ID partita: ");
			int Id = readOption(Integer.MAX_VALUE);
			System.out.println();
			return JsonBuilder.requestGame("requestGameStats", Id);
		case(3):
			return null;
		}
		return null;
	}
	
	private static String handleRequestLeaderboard() {
		int maxOption = Menu.printLeaderboardMenu();
		int option = readOption(maxOption);
		
		switch(option)
		{
		case(1):
			System.out.print("USERNAME: ");
			String username = sc.nextLine().trim();
			return JsonBuilder.requestLeaderboard(username);
		case(2):
			System.out.print("K: ");
			int K = readOption(Integer.MAX_VALUE);
			return JsonBuilder.requestLeaderboard(K);
		case(3):
			return JsonBuilder.requestLeaderboard(0);
		case(4): return null;
		}
		return null;
	}
	
	private static String handleRequestPlayerStats() { return JsonBuilder.requestPlayerStats(thisUser.getUsername()); }
	
	private static String handleClientLogout() { return JsonBuilder.logout(); }
	
	/*
	L'utente decide di non prendere parte a una nuova partita.
	Invierà il seguente JSON per terminare la connessione con il server, non attenderà quindi una risposta.
	{
		"operation": "quit"
	}	
	*/
	private static void handleQuit() throws GameInterruptedException{
		String quit = JsonBuilder.quit();
		try {
			network.sendRequest(quit);
		}
		catch(IOException ex) { }
		throw new GameInterruptedException();
	}
	
	//	--- Verifico la validità della scelta nel menù ---
	private static int readOption(int maxOption) {
		int option;
		
		while(true) {
		    try {
		        option = Integer.parseInt(sc.nextLine().trim());
		        if(option < 1 || option > maxOption) 
		            throw new NumberFormatException();
		        else break;
		    }
		    catch(NumberFormatException ex) { 
		        System.err.println("Si prega di inserire un numero valido."); 
		    }
		}
		return option;
	}
	
	//		--- Scansiono il JSON ricevuto dal server, poi lo smisto a una funzione apposita. ---
	private static boolean parseReply(String jsonString) throws ConnectException
	{		
		try (JsonReader jreader = new JsonReader(new StringReader(jsonString)) )
		{
			String operation;
			jreader.beginObject();
			while(jreader.hasNext())
			{
				String name = jreader.nextName();
				if(name.equals("operation"))
				{
					operation = jreader.nextString();
					
					dispatchReply(jreader, operation);
					return true;
				}
			}
		}
		catch(IOException ex) {  }
		return false;
	}
	
	//		--- Dipendentemente dall'operazione, inoltro il json reader a un altra funzione.
	private static void dispatchReply(JsonReader jreader, String operation) throws ConnectException
	{
			switch(operation)
			{
				case "register": 
					printer.printSimpleReply(jreader);
					break;
			    case "login":
			    	if(printer.printSimpleReply(jreader) == 0) {
			            state = ClientState.LOGGED;
			            thisUser.setUsername(pendingUsername);
			            thisUser.setPassword(pendingPassword);
			            
			            // A seguito di un login andato a buon fine devo iscrivermi al gruppo multicast
			            multicastThread.login();
			            printer.printLoginInfo(jreader);
			    		}
			    		pendingUsername = null;
			    		pendingPassword = null;
			    	break;
			    	
			    case "submitProposal": 
			    	printer.printSubmitProposal(jreader);
			    	break;
			    	
			    case "requestGameInfo":
			    	if(printer.printSimpleReply(jreader) == 0) {
			    		printer.printGameInfo(jreader);
			    	}
			    	break;
			    	
			    case "requestGameStats":
			    	if(printer.printSimpleReply(jreader) == 0) {
			    		printer.printGameStatsBlock(jreader);
			    	}
			    	break;			
			    	
			    case "requestLeaderboard":
			    	if(printer.printSimpleReply(jreader) == 0) {
			    		printer.printLeaderboard(jreader);
			    	}
			    	break;
			    
			    case "requestPlayerStats":
			    	if(printer.printSimpleReply(jreader) == 0) {
			    		printer.printPlayerStats(jreader);
			    	}
			    	break;
			    
			    case "logout":			// Fa le stesse cose di Update Credentials
			    case "updateCredentials":
			    	if(printer.printSimpleReply(jreader) == 0)
			    		{
			    			state = ClientState.NOT_LOGGED;	// Logout automatico
			    			thisUser.setUsername("");
			    			thisUser.setPassword("");
			    			
			    			// Effettuo chiusura del multicast UDP.
			    			multicastThread.logout();
			    		}
			        break;
			        
			    case "playAgain":
			        if(printer.printSimpleReply(jreader) == 0) {
			        	try {
			            while(jreader.hasNext()){
			                String field = jreader.nextName();
			                if(field.equals("game")) {
			                	printer.printGameBlock(jreader);
			                } else {
			                    jreader.skipValue();
			                }
			            }
			        	}
			        	catch(IOException ex) {  }
			        }
			        break;
			}
	}
	
	//		--- Apro un collegamento con la funzione per la registrazione risiedente nel server. ---
	private static RegisterInterface openRegistry() {
		try {
			Registry registry = LocateRegistry.getRegistry(registerPort);
			Remote remoteObject = registry.lookup(registerService);
			return (RegisterInterface) remoteObject;
		}
		catch(RemoteException | NotBoundException ex) { System.err.println("Il servizio di registrazione è temporaneamente non disponibile."); }
		return null;
	}
	
	
	//		--- Gestione UDP ---
	private static String handleUDPNotification(String data) throws GameInterruptedException, ConnectException{
			try(StringReader sr = new StringReader(data);
				JsonReader jreader = new JsonReader(sr))
			{
				jreader.beginObject();
				System.out.println("\n\n--- PARTITA TERMINATA ---");
				
				while(jreader.hasNext())
				{
					if(jreader.nextName().equals("scoreboard"))
					{
						jreader.beginArray();
						int rank = 1;
						System.out.println("\n--- CLASSIFICA ---");
						while(jreader.hasNext())
						{
							System.out.println(rank + " - " + jreader.nextString());
							rank++;
						}
						jreader.endArray();
					}
					else jreader.skipValue();
				}
				jreader.endObject();
					
					// Messaggio di avviso della nuova partita
				System.out.println();
				int maxOption = Menu.printNewGameMenu();
				int option = readOption(maxOption);
						
				if(option == 1)
				{
					/*
					Se l'utente vuole giocare ancora, invierà il seguente JSON al server
					{
						"operation": "playAgain"
					}
					*/
					try(StringWriter sw = new StringWriter();
						JsonWriter jwriter = new JsonWriter(sw))
					{
						jwriter.beginObject();
						jwriter.name("operation").value("playAgain");
						jwriter.endObject();
						return sw.toString();
					}
					catch(IOException ex) {  }
				}
					
					// Se ho risposto no devo far disconnettere il client.
				else handleQuit();
			}
			catch(IOException ex) { /* Errore durante la ricezione della classifica globale */ }
			return null;
	}
	
	private static void initializeUdpMulticast() {
		try {
			InetSocketAddress group = new InetSocketAddress(multicastAddress,multicastPort);
			NetworkInterface netIf = NetworkInterface.getByName("wlan1");
			
			multicastThread = new MulticastReceiver(ms, group, netIf, multicastFlag);
			multicastThread.start();
		}
		catch(IOException ex) { System.err.println("Le notifiche in tempo reale non saranno ricevute."); }
	}
	
	private static void initializeUdpUnicast() {
		unicastThread = new UnicastReceiver(ds, unicastFlag);
		unicastThread.start();
	}
	
	private static void checkAndHandleUdpNotification() throws GameInterruptedException, IOException{
		if(unicastFlag.get())
		{
			unicastFlag.set(false);
			String message = unicastThread.getMessage();
			
			// Questo messaggio JSON è solamente composto da playerStats e gameStats
			try(JsonReader jreader = new JsonReader(new StringReader(message)))
			{
				jreader.beginObject();
				printer.printEndGameUnicast(jreader);
			}
		}
		
		if(multicastFlag.get())
		{
			multicastFlag.set(false);
			String message = multicastThread.getMessage();
			String udpString = handleUDPNotification(message);
			if(udpString != null)
			{
				if(!network.sendRequest(udpString)) return;
				if(!parseReply(network.receiveReply())) return;
			}
		}
	}
}