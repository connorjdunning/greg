package com.gregbot;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.ResultSet;

class DbController {
    private final String NAME;
    private final String URL;

    // private Connection connection;
    // private Statement statement;

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
                    "CREATE TABLE events (id INTEGER PRIMARY KEY, owner STRING, owner_id INTEGER, name STRING, description STRING, start_time INTEGER, end_time INTEGER, close_time INTEGER, repeats STRING, max_players INTEGER, channels BOOLEAN, timezone STRING);");

            // Drop and create the attendees table
            stmt.executeUpdate("DROP TABLE IF EXISTS attendees");
            stmt.executeUpdate(
                    "CREATE TABLE attendees (id INTEGER PRIMARY KEY, event_id INTEGER, user_id STRING, FOREIGN KEY(event_id) REFERENCES events(id));");

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
