package common;

/*
 * Tutti i messaggi di errore e di avviso scambiati tra client e server
 * sono presenti in questa classe.
 */

public enum Error {
	
	 
	SUCCESS(0, "Operazione completata."),
	
	// I seguenti errori sono causati da un username o password mal formattati.
	
	USER_ALREADY_EXISTS(1, "L'username è già esistente."),
	INVALID_USERNAME_LENGTH(2, "L'username è troppo lungo o troppo breve.\nAssicurati che sia di lunghezza compresa tra 3 e 15 caratteri."),
	INVALID_USERNAME_FORMAT(3, "L'username non è valido. Usa solo caratteri alfanumerici."),
	INVALID_PASSWORD(4,"La password inserita è poco sicura.\nAssicurati che sia di lunghezza almeno 5 caratteri."),
	
	// I seguenti errori sono causati da un operazione errata su utenti già registrati.
	
	USER_NOT_FOUND(5,"L'username non è stato trovato."),
	INCORRECT_PASSWORD(6,"La password inserita è errata."),
	USER_ALREADY_LOGGED(7,"L'utente è già loggato."),
	USER_NOT_LOGGED(8,"Per eseguire questa funzione, l'utente deve essere loggato."),
	
	// I seguenti errori sono causati da un errore nella logica di gioco.
	
	INVALID_PROPOSAL_FORMAT(9,"Alcune parole sono errate. Assicurati di digitarle correttamente."),
	CORRECT_PROPOSAL(10,"La proposta è corretta."),	// Non proprio un errore, ma un messaggio di avviso.
	WRONG_PROPOSAL(11,"La proposta è errata."),		// Non proprio un errore, ma un messaggio di avviso.
	GROUP_ALREADY_GUESSED(12,"Questo gruppo è già stato indovinato."),
	GAME_ALREADY_COMPLETED(13,"La partita è già terminata, per sconfitta o per vittoria."),
	
	// I seguenti errori sono causati da errori inerenti a richieste di informazioni.
	
	GAMEID_NOT_FOUND(14,"Partita inesistente."),
	PLAYER_DID_NOT_PARTICIPATE(15,"L'utente non ha partecipato a questa partita."),
	GAME_UNAVAILABLE(16,"La partita è al momento irraggiungibile. Riprova più tardi."),
	
	// I seguenti errori sono di carattere tecnico.

	INVALID_JSON_FORMAT(98,"Il messaggio di richiesta è mal formattato."),
	UNKNOWN_ERROR(99,"Errore sconosciuto. L'operazione non è terminata correttamente.");
	
	
	private int errCode;
	private String errMessage;
	
	Error(int code, String message) {
		this.errCode = code;
		this.errMessage = message;
	}
	
	public int getCode() { return errCode; }
	public String getDescription() { return errMessage; }
	
	public static Error fromCode(int code) {
        for (Error e : Error.values()) {
            if (e.errCode == code) return e;
        }
        return Error.UNKNOWN_ERROR; // Il codice errore non esiste
    }
}

