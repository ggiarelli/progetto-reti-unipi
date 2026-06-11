package client;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

/*
 * Classe dedicata all'ascolto di traffico Unicast UDP
 */

public class UnicastReceiver extends Thread{

	DatagramSocket ds;
	AtomicBoolean flag;
	String message;
	
	public UnicastReceiver(DatagramSocket ds, AtomicBoolean flag)
	{
		this.ds = ds;
		this.flag = flag;
	}
	
	@Override
	public void run() {
		
		byte[] buf = new byte[4096];
		DatagramPacket dp = new DatagramPacket(buf, buf.length);
		try {
		    while(true)
		    {
		        ds.receive(dp);
		        this.message = new String(dp.getData(), 0, dp.getLength(), StandardCharsets.UTF_8);
		        flag.set(true);
		    }
		}
		catch(IOException e) { /* thread interrotto */ }
	}
	
	public String getMessage() {return message;}
}
