package server;

/*
 * Classe di supporto per lo scorrere di nuove partite
 */

public class GameOffset {
	int ID;
	long offset;
	
	public GameOffset() {super();}
	public GameOffset(int ID, long offset) { this.ID = ID; this.offset = offset; }
	
	public void setOffset(long offset) { this.offset = offset; }
	public void setID(int ID) { this.ID = ID; }
	
	public long getOffset() {return offset;}
	public int getID() {return ID;}
}
