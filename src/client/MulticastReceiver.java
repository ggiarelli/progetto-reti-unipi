package client;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

/*
 * Classe dedicata all'ascolto di traffico Multicast UDP
 */

public class MulticastReceiver extends Thread{

	MulticastSocket ms;
	DatagramPacket dp;
	InetSocketAddress group;
	NetworkInterface netIf;
	AtomicBoolean multicastFlag;
	
	private volatile String lastMessage;
	
	MulticastReceiver(MulticastSocket ms, InetSocketAddress group, NetworkInterface netIf, AtomicBoolean multicastFlag) {
		this.ms = ms;
		this.group = group;
		this.netIf = netIf;
		this.multicastFlag = multicastFlag;
		}
	
	@Override
	public void run() {
		try {
			byte[] buf = new byte[4096];
			
			dp = new DatagramPacket(buf,buf.length);
			
			while(true) {
				ms.receive(dp);
				this.lastMessage = new String(dp.getData(), 0, dp.getLength(), StandardCharsets.UTF_8);
				multicastFlag.set(true);
			}
		}
		catch(IOException ex) {/* Il thread è stato interrotto */}
	}
	
	public void login() {
		try { ms.joinGroup(group, netIf); }
		catch(IOException ex) { System.err.println("Impossibile ricevere le notifiche in tempo reale"); }
	}
	
	public void logout() {
		try { ms.leaveGroup(group, netIf); }
		catch(IOException ex) {  }
	}
	
	public String getMessage() { return this.lastMessage; }
}
