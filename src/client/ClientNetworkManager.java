package client;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;

/*
 * Questa classe si occupa dello scambio di pacchetti TCP con il server
 */

public class ClientNetworkManager {

	ByteBuffer buffer;
	SocketChannel channel;
	
	public ClientNetworkManager(ByteBuffer buffer, SocketChannel channel) {
		this.buffer = buffer;
		this.channel = channel;
	}
	
	public boolean sendRequest(String jsonString) throws IOException{
	        byte[] bytes = jsonString.getBytes(StandardCharsets.UTF_8);
	        // Invia la dimensione
	        buffer.clear();
	        buffer.putInt(bytes.length);
	        buffer.flip();
	        while(buffer.hasRemaining()) channel.write(buffer);

	        // Invia il payload a frammenti
	        int offset = 0;
	        while(offset < bytes.length) {
	            buffer.clear();
	            int chunk = Math.min(buffer.capacity(), bytes.length - offset);
	            buffer.put(bytes, offset, chunk);
	            buffer.flip();
	            while(buffer.hasRemaining()) channel.write(buffer);
	            offset += chunk;
	        }
	        return true;
	}

					// --- Legge il JSON ricevuto dal server ---
	public String receiveReply() throws IOException{
	    
	        // Legge la dimensione
	    	int bytesRead = 0;
	        buffer.clear();
	        while(buffer.position() < Integer.BYTES) bytesRead = channel.read(buffer);
	        buffer.flip();
	        if(bytesRead == -1) { channel.close(); return null; }
	        int size = buffer.getInt();
	        buffer.compact();

	        // Legge il payload
	        byte[] bytes = new byte[size];
	        int offset = 0;

	        while(offset < size) {
	            // Svuota quello che è già presente nel buffer
	            buffer.flip();
	            while(buffer.hasRemaining() && offset < size) {
	                int chunk = Math.min(buffer.remaining(), size - offset);
	                buffer.get(bytes, offset, chunk);
	                offset += chunk;
	            }
	            buffer.compact();

	            // Legge altri dati se non ho finito la lettura
	            if(offset < size) channel.read(buffer);
	        }
	        return new String(bytes, StandardCharsets.UTF_8);
	}
}
