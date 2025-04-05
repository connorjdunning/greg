package com.gregbot;

public class GameEvent {

    // I wrote getters and setters for these, but it seemed silly for such a short-lived thing, so I'll just make em public XD
    public  int id;
    public String owner;
    public long owner_id;
    //public String guildId;
    public String name;
    public String description;
    public long startTime;
    public long endTime;
    public long closeTime;
    public String repeats;
    public int maxPlayers;
    public boolean temp_channels;
    public String timezone;

    // Constructor that only takes the fields from the first modal.
    public GameEvent(int gameID, String name, String Description, String Repeats, int maxPlayers, String owner, long owner_id, boolean temp_channels) {
        this.id = gameID;
        this.name = name;
        this.description = Description;
        this.repeats = Repeats;
        this.maxPlayers = maxPlayers;
        this.owner = owner;
        this.owner_id = owner_id;
        
        // Dummy values, get's updated in the second modal
        this.startTime = -1;
        this.endTime = -1;
        this.closeTime = -1;
        this.timezone = "000";

    }

    @Override
    public String toString() {
        return "GameEvent{" +
                "id=" + id +
                ", owner='" + owner + '\'' +
                ", owner_id=" + owner_id +
                ", name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", startTime=" + startTime +
                ", endTime=" + endTime +
                ", closeTime=" + closeTime +
                ", repeats='" + repeats + '\'' +
                ", maxPlayers=" + maxPlayers +
                ", temp_channels=" + temp_channels +
                ", timezone='" + timezone + '\'' +
                '}';
    }
}
