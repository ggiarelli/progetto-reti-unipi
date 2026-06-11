package common;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

/*
 * Questa classe rappresenta l'attachment di ogni channel
 * Gestisce la lettura di molteplici segmenti TCP, tiene traccia dell'utente loggato sul channel.
 */

public class Session {

    User u;
    ByteBuffer buff;
    InetSocketAddress unicastAddress;
    
    // Stato di lettura
    int count = 0;          // Byte letti finora
    int length = 0;         // Dimensione della stringa
    byte[] pendingBytes;    // Byte array per la lettura
    int pendingOffset = 0;  // Offset nell'array di byte

    public Session() { super(); }

    public void setUnicastAddress(InetSocketAddress unicastAddress) {this.unicastAddress = unicastAddress;}
    public InetSocketAddress getUnicastAddress() {return unicastAddress;}
    public void setBuff(ByteBuffer buff) { this.buff = buff; }
    public ByteBuffer getBuff() { return buff; }
    public void setUser(User u) { this.u = u; }
    public User getUser() { return u; }
    public int getCount() { return count; }
    public void setCount(int count) { this.count = count; }
    public int getLength() { return length; }
    public void setLength(int length) { this.length = length; }
    public byte[] getPendingBytes() { return pendingBytes; }
    public void setPendingBytes(byte[] pendingBytes) { this.pendingBytes = pendingBytes; }
    public int getPendingOffset() { return pendingOffset; }
    public void setPendingOffset(int pendingOffset) { this.pendingOffset = pendingOffset; }
}