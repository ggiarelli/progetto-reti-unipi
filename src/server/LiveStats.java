package server;

/*
 * Classe di supporto per ottenere atomicamente le statistiche collettive di una partita.
 */

public class LiveStats {
	int currPlayers;
	int winPlayers;
	int totalPlayers;
	int completedPlayers;
	float avgPoints;
	long timeLeft;
	
	public LiveStats(int curr, int win, int tot, int comp, float avg, long time)
	{
		this.currPlayers = curr;
		this.winPlayers = win;
		this.totalPlayers = tot;
		this.completedPlayers = comp;
		this.timeLeft = time;
	}
}
