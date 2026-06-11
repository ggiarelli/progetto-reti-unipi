package server;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import common.User;

/*
 * Questa classe contiene metodi per il salvataggio delle strutture dati degli utenti e delle partite su file persistenti.
 */

public class FileUpdater {

    private final ConcurrentHashMap<String, User> users;
    private final ConcurrentHashMap<Integer, GameStats> games;
    private final File resourcesDirectory;
    private final Gson gson;

    public FileUpdater(ConcurrentHashMap<String, User> users, ConcurrentHashMap<Integer, GameStats> gameStats, File resourcesDirectory) {
        this.users = users;
        this.games = gameStats;
        this.resourcesDirectory = resourcesDirectory;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    public void writeNewUsersJson() {
        writeJsonAtomic(users, "users.json");
        // Dopo la scrittura su file permanente, cancello users_temp.log
        	ServerMain.tempUsersLock.lock();
        	try {
	            File tempFile = new File(resourcesDirectory, "users_temp.log");
	            tempFile.delete();
        	}
        	catch(NullPointerException ex) {}
            finally {
            	ServerMain.tempUsersLock.unlock();
            }
    }

    public void writeNewGameJson() {
        writeJsonAtomic(games, "games.json");
    }

    /* Per aggiornare i file persistenti, serializziamo le strutture users e games in nuovi file JSON.
    Successivamente, effettuo una sostituzione atomica attraverso Files.move, sostituendo i vecchi file con quelli nuovi.
    Questo evita che i file persistenti si corrompano qualora il server si interrompesse durante la sovrascrittura dei file vecchi. */
    private void writeJsonAtomic(Object data, String fileName) {
        File targetFile = new File(resourcesDirectory, fileName);
        File tempFile = new File(resourcesDirectory, fileName + "_new");

        synchronized (data) {
            try (BufferedWriter bw = new BufferedWriter(new FileWriter(tempFile))) {
                gson.toJson(data, bw);
            } catch (IOException e) {
                System.err.println();
                return;
            }

            try {
                Files.move(tempFile.toPath(), targetFile.toPath(),
                        StandardCopyOption.ATOMIC_MOVE, 
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                System.err.println("Errore durante lo spostamento atomico di " + fileName);
                if (tempFile.exists()) tempFile.delete();
            }
        }
    }
}