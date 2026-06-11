package server;

import common.Error;

/* Classe di utilità per la validazione delle credenziali utente.
Fornisce metodi statici per verificare la conformità di username e password. */
public class Validator {

	/* Verifica la validità dell'username.
	Controlla che la lunghezza sia compresa tra 3 e 15 caratteri e che
	contenga esclusivamente caratteri alfanumerici. */
	public static Error checkUsernameValidity(String username) {
		// Rimozione di eventuali spazi
		username = username.trim();

		if (username.length() < 3 || username.length() > 15) {
			return Error.INVALID_USERNAME_LENGTH;
		}

		for (char c : username.toCharArray()) {
			if (!Character.isLetterOrDigit(c)) {
				return Error.INVALID_USERNAME_FORMAT;
			}
		}

		return Error.SUCCESS;
	}

	/* Verifica la validità della password.
	Controlla che rispetti il requisito minimo di lunghezza di 5 caratteri. */
	public static Error checkPasswordValidity(String password) {
		if (password.length() < 5) { return Error.INVALID_PASSWORD; }
		return Error.SUCCESS;
	}
}