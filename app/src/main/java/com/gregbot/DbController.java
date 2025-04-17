package com.gregbot;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;

class DbController {
    private final String NAME;
    private final String URL;

    public DbController(String name, String url) {
        this.NAME = name;
        this.URL = "jdbc:sqlite:" + name + ".db";
    }

    public String getURL() {
        return this.URL;
    }

    public void resetDB() {
        Statement stmt = null;
        Connection conn = null;

        try {
            conn = DriverManager.getConnection(URL);
            stmt = conn.createStatement();
            stmt.setQueryTimeout(60);

            // Drop and create the events table
            stmt.executeUpdate("DROP TABLE IF EXISTS events;");
            stmt.executeUpdate(
                    "CREATE TABLE events (id STRING PRIMARY KEY, owner STRING, owner_id INTEGER, name STRING, description STRING, start_time INTEGER, end_time INTEGER, close_time INTEGER, repeats STRING, max_players INTEGER, channels BOOLEAN, timezone STRING);");

            // Drop and create the attendees table
            stmt.executeUpdate("DROP TABLE IF EXISTS attendees");
            stmt.executeUpdate(
                    "CREATE TABLE attendees (event_id STRING, user_id STRING, status STRING, PRIMARY KEY (event_id, user_id), FOREIGN KEY(event_id) REFERENCES events(id));");

            // Drop and create the users table
            stmt.executeUpdate("DROP TABLE IF EXISTS users");
            stmt.executeUpdate(
                    "CREATE TABLE users (user_id STRING PRIMARY KEY, name STRING, timezone STRING, games_ran INTEGER, games_played INTEGER, hours_gamed INTEGER);");

            // use an entry in the users table to tracker global stats
            stmt.executeUpdate("INSERT INTO users VALUES ('0', 'Server Stats', 'UTC', 0, 0, 0);");

            // TODO index things
            // TODO add constraints

        } catch (Exception e) {
            e.printStackTrace(System.err);
        } finally {
            try {
                stmt.close();
                conn.close();
            } catch (Exception e) {
                e.printStackTrace(System.err);
            }
        }
    }

    public String[] getServerStats() {
        Connection conn = null;
        PreparedStatement stats = null;

        try {
            conn = DriverManager.getConnection(URL);
            stats = conn.prepareStatement("SELECT * FROM users WHERE user_id = '0';");

            return stats.executeQuery().getString(1).split(",");

        } catch (Exception e) {
            e.printStackTrace(System.err);
        } finally {
            try {
                stats.close();
                conn.close();
            } catch (Exception e) {
                e.printStackTrace(System.err);
            }
        }
        return null;
    }

    public void addEvent(GameEvent game) {
        Connection conn = null;
        PreparedStatement toInsert = null;

        try {
            conn = DriverManager.getConnection(URL);
            toInsert = conn.prepareStatement(
                    "INSERT INTO events (id, owner, owner_id, name, description, start_time, end_time, close_time, repeats, max_players, channels, timezone) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);");

            toInsert.setInt(1, game.id);
            toInsert.setString(2, game.owner);
            toInsert.setLong(3, game.owner_id);
            toInsert.setString(4, game.name);
            toInsert.setString(5, game.description);
            toInsert.setLong(6, game.startTime);
            toInsert.setLong(7, game.endTime);
            toInsert.setLong(8, game.closeTime);
            toInsert.setString(9, game.repeats);
            toInsert.setInt(10, game.maxPlayers);
            toInsert.setBoolean(11, game.temp_channels);
            toInsert.setString(12, game.timezone);

            toInsert.executeUpdate();

        } catch (Exception e) {
            e.printStackTrace(System.err);
        } finally {
            try {
                toInsert.close();
                conn.close();
            } catch (Exception e) {
                e.printStackTrace(System.err);
            }
        }

    }

    public void setAttendee(String eventID, String userID, String status) {
        Connection conn = null;
        PreparedStatement toInsert = null;
        // (event_id STRING, user_id STRING, status STRING, PRIMARY KEY (event_id,
        // user_id)

        try {
            conn = DriverManager.getConnection(URL);

            // Check if the user is already attending
            toInsert = conn.prepareStatement("SELECT * FROM attendees WHERE event_id = ? AND user_id = ?;");
            toInsert.setString(1, eventID);
            toInsert.setString(2, userID);
            ResultSet rs = toInsert.executeQuery();

            if (rs.next()) {
                // Update status
                toInsert = conn.prepareStatement("UPDATE attendees SET status = ? WHERE event_id = ? AND user_id = ?;");
                toInsert.setString(1, status);
                toInsert.setString(2, eventID);
                toInsert.setString(3, userID);
                toInsert.executeUpdate();
                return;

            } else {
                toInsert = conn.prepareStatement("INSERT INTO attendees (event_id, user_id, status) VALUES(?, ?, ?);");

                toInsert.setString(1, eventID);
                toInsert.setString(2, userID);
                toInsert.setString(3, status);
                toInsert.executeUpdate();
                return;
            }

        } catch (Exception e) {
            e.printStackTrace(System.err);
        } finally {
            try {
                toInsert.close();
                conn.close();
            } catch (Exception e) {
                e.printStackTrace(System.err);
            }
        }
    }

    public int getEventCount() {
        Connection conn = null;
        Statement stmt = null;

        try {
            conn = DriverManager.getConnection(URL);
            stmt = conn.createStatement();
            stmt.setQueryTimeout(30); // set timeout to 30 sec.

            ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM events;");

            return rs.getInt(1);

        } catch (SQLException e) {
            System.err.println(e.getMessage());

        } finally {
            try {
                stmt.close();
                conn.close();

            } catch (SQLException e) {
                System.err.println(e.getMessage());
            }
        }
        return -34404;
    }

    // Returns a list of events that are upcoming within the next x days
    // or null
    ArrayList<String> getUpcomingEvents(int days) {
        Connection conn = null;
        Statement stmt = null;

        try {
            conn = DriverManager.getConnection(URL);
            stmt = conn.createStatement();

            long lowerBound = System.currentTimeMillis() / 1000;
            long upperBound = lowerBound + (days * 86400);

            
            ResultSet rs = stmt.executeQuery(
                    "SELECT * FROM events WHERE start_time > " + lowerBound + " AND start_time < " + upperBound + ";");

            
            // Doing some of the foratting here is admittedly a bit wasck. TODO revisit
            ArrayList<String> events = new ArrayList<>();
            while (rs.next()) {
                events.add(rs.getString("name") + ", with " + rs.getString("owner") + ", <t:" + rs.getString("start_time") + ":R>");
            }
            return events;

        } catch (SQLException e) {
            System.err.println(e.getMessage());

        } finally {
            try {
                stmt.close();
                conn.close();

            } catch (SQLException e) {
                System.err.println(e.getMessage());
            }
        }
        return null;
    }

    // utter garbage, probably remove
    ResultSet CallandReturn(String statement) {
        Connection conn = null;
        Statement stmt = null;
        try {
            conn = DriverManager.getConnection(URL);
            stmt = conn.createStatement();
            stmt.setQueryTimeout(30); // set timeout to 30 sec.

            ResultSet rs = stmt.executeQuery(statement);

            return rs;

        } catch (SQLException e) {
            System.err.println(e.getMessage());

        } finally {
            try {
                stmt.close();
                conn.close();

            } catch (SQLException e) {
                System.err.println(e.getMessage());
            }
        }
        return null;
    }

}
