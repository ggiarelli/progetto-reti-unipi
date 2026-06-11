package server;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringReader;
import java.rmi.RemoteException;
import java.rmi.server.RemoteServer;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.stream.JsonReader;

import common.Error;
import common.JsonBuilder;
import common.User;

/*
 * Questa classe implementa l'interfaccia della registrazione tramite RMI.
 */

public class RegisterImpl extends RemoteServer implements RegisterInterface{

	private static final long serialVersionUID = 1L;
	
	private final ConcurrentHashMap<String,User> users;
	private final File resourcesDirectory;
	
	RegisterImpl(File resourcesDirectory, ConcurrentHashMap<String,User> users)
	{
		this.resourcesDirectory = resourcesDirectory;	// Mantengo un riferimento alla cartella "resources"
		this.users = users;
	}
		
	public String register(String jsonRegister) throws RemoteException{
		
		/*
		 * Ricevo una stringa json che implementa la funzione di registrazione.
		 * Apro un reader sulla stringa passata, e successivamente un Json Reader.
		 */
		
		Error error = Error.SUCCESS;	 // Qui sarà contenuto il codice di un eventuale errore.
		String username = null;
		String password = null;
		
		User user;
		
		try ( StringReader reader = new StringReader(jsonRegister);
				JsonReader jreader = new JsonReader(reader); )
		{
			jreader.beginObject();
			while(jreader.hasNext()) {
			    String nextName = jreader.nextName();
			    switch(nextName) {
			        case "name": username = jreader.nextString(); break;
			        case "psw":  password = jreader.nextString(); break;
			        default:     jreader.skipValue();
			    }
			}
			jreader.endObject();

			// Verifico la validità della stringa json e dell'unicità dell'username
			if(username == null || password == null) return JsonBuilder.reply("register", Error.INVALID_JSON_FORMAT);
			if(users.containsKey(username)) return JsonBuilder.reply("register", Error.USER_ALREADY_EXISTS);
			
			error = Validator.checkUsernameValidity(username);
			if(error != Error.SUCCESS) return JsonBuilder.reply("register", error);

			error = Validator.checkPasswordValidity(password);
			if(error != Error.SUCCESS) return JsonBuilder.reply("register", error);

			user = new User(username, password, ServerMain.nextUserId.getAndIncrement());
			if(users.putIfAbsent(username, user) != null) return JsonBuilder.reply("register", Error.USER_ALREADY_EXISTS);
		}
		catch(IOException ex) { System.err.println("Errore nel parsing JSON"); error = Error.UNKNOWN_ERROR; return JsonBuilder.reply("register",error); }
		
		// Segno nel file users_temp.log la nuova registrazione effettuata.
		newRegisterEntryBuilder(username, password, user.getId());
		
		return JsonBuilder.reply("register",error);
	}
	
	private void newRegisterEntryBuilder(String username, String password, int Id)
	{
		File tempfile = new File(resourcesDirectory, "users_temp.log");
		
			ServerMain.tempUsersLock.lock();
			try(BufferedWriter bw = new BufferedWriter(new FileWriter(tempfile,true)))
			{
				// Nel file .log segnerò un operazione con sintassi json.
				bw.write(JsonBuilder.addToUsersLog("register", Integer.toString(Id), username, password));
				bw.newLine();
			}
			catch(IOException ex) { System.err.println("Impossibile scrivere sul file .log"); }
			finally {
				ServerMain.tempUsersLock.unlock();
			}
	}
}