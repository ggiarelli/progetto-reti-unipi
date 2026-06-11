package server;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface RegisterInterface extends Remote{
	
	/*
	 * Questa interfaccia riporta il metodo di registrazione offerto dal server
	 */
	
	public String register(String jsonRegister) throws RemoteException;
}
