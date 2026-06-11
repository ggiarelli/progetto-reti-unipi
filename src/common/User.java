package common;

import com.google.gson.annotations.Expose;

public class User {
	
	private int Id;
	
	private String username;
	private String password;
	
@Expose	int playedGames;
@Expose	int wonGames;
	
@Expose	float winRate;
@Expose	float lossRate;
	
@Expose	int currentStreak;
@Expose	int maxStreak;
@Expose	int perfectGames;
	
@Expose	int points;
	
	/*
	 * Struttura dell' array per istogramma:
	 * histogram[0]: vinto con 0 errori
	 * histogram[1]: vinto con 1 errore
	 * histogram[2]: vinto con 2 errori
	 * histogram[3]: vinto con 3 errori
	 * histogram[4]: perso per errori
	 * histogram[5]: perso per timeout
	 */
	
@Expose	int histogram[] = new int[6];
	
	public User()
	{
		super();
	}
	
	public User(String username, String password, int Id)
	{
		this.username = username;
		this.password = password;
		this.points = 0;
		this.Id = Id;
	}
	
	public int getId() { return this.Id; }
	
	public String getPassword() { return password; }
	public String getUsername() { return username; }
	
	public void setPassword(String psw) { this.password = psw; }
	public void setUsername(String username) { this.username = username; }
	
	public int getPlayedGames() { return playedGames; }
	public void setPlayedGames(int playedGames) { this.playedGames = playedGames; }

	public int getWonGames() { return wonGames; }
	public void setWonGames(int wonGames) { this.wonGames = wonGames; }

	public float getWinRate() { return winRate; }
	public void setWinRate(float winRate) { this.winRate = winRate; }

	public float getLossRate() { return lossRate; }
	public void setLossRate(float lossRate) { this.lossRate = lossRate; }

	public int getCurrentStreak() { return currentStreak; }
	public void setCurrentStreak(int currentStreak) { this.currentStreak = currentStreak; }

	public int getMaxStreak() { return maxStreak; }
	public void setMaxStreak(int maxStreak) { this.maxStreak = maxStreak; }

	public int getPerfectGames() { return perfectGames; }
	public void setPerfectGames(int perfectGames) { this.perfectGames = perfectGames; }
	
	public int getPoints() { return points; }
	
	public int[] getHistogram() { return histogram; }
	
	public void incrementPlayedGames() { playedGames++; }
	
	public void incrementWonGames() { wonGames++; }
	
	public void incrementStreak() {
			currentStreak++;
			if(currentStreak > maxStreak) maxStreak = currentStreak;
		}
	
	public void incrementPerfectGames() { perfectGames++; }
	
	public void calculateNewRate() {
		if(playedGames > 0) {
			winRate = ((float) wonGames / playedGames) * 100.0f;
			lossRate = 100.0f - winRate;
		}
	}
	
	public void addToHistogram(int index) { histogram[index]++;	}
	
	public void addPoints(int points) { this.points += points; }
	
	public void resetStreak() { currentStreak = 0; }
}
