package common;

import java.util.Properties;

/*
 * Classe personalizzata che permette la lettura di file Properties con inclusi
 * controlli su proprietà rappresentate come Stringhe o Interi.
 */

public class CustomProperties extends Properties{
	
	private static final long serialVersionUID = 1L;

	public CustomProperties() { super(); }
	
	public int getIntProperty(Properties props, String key, int defaultValue) {
	    String value = props.getProperty(key);
	    if (value == null) return defaultValue; // La chiave non esiste nel file
	    try {
	        return Integer.parseInt(value);
	    } catch (NumberFormatException e) {
	        return defaultValue;
	    }
	}
	
	public String getStringProperty(Properties props, String key, String defaultValue)
	{
		String value = props.getProperty(key);
		if (value == null) return defaultValue; // La chiave non esiste nel file
		return value;
	}
}