
package com.gregbot;

import static net.dv8tion.jda.api.interactions.commands.OptionType.STRING;

import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Random;

// https://www.baeldung.com/quartz
import org.quartz.Scheduler;
import org.quartz.SchedulerFactory;
import org.quartz.impl.StdSchedulerFactory;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.IntegrationType;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;

public class App extends ListenerAdapter {

    // TODO add config file for these
    private static final String NAME = "greg";
    private static final String URL = "jdbc:sqlite:" + NAME;
    private static final String SERVER_ID = "1273033154826997790";
    private static final String EVENT_FEED_ID = "1356146914252296237";

    Random rand = new Random();
    private DbController db = new DbController(NAME, URL);
    static SchedulerFactory schedulerFactory = null; 
    static Scheduler scheduler = null;

    // Hashmap of GameEvents stores their info before they are added to the database
    HashMap<String, GameEvent> games = new HashMap<>();


    public static void main(String[] args) throws Exception {


        schedulerFactory = new StdSchedulerFactory(); 
        scheduler = schedulerFactory.getScheduler();

        // Create a new JDA, load token from file, and empty list of intents
        JDA jda = JDABuilder.createLight(Files.readString(java.nio.file.Path.of(".token")), Collections.emptyList())
                .addEventListeners(new App())
                .build();

        // Wait for the bot to be ready
        jda.awaitReady();

        // Register the commands so they're globally visable
        // might need to reload client
        CommandListUpdateAction commands = jda.updateCommands();

        // Slash Commands need to be
        // + added to the CommandListUpdateAction (commands)
        // + grabbed by the listener onSlashCommandInteractionEvent
        // + have some function to actually do something

        // TODO You don't need to create your commands every time your bot starts!

        // Simple commands
        commands.addCommands(
                Commands.slash("say", "Make greg speak")
                        .setContexts(InteractionContextType.ALL) // can be used in DMs, Guild, everywhere
                        .setIntegrationTypes(IntegrationType.ALL) // can be installed anywhere
                        .addOption(STRING, "content", "what greg will say", true) // additional option for command
        );

        commands.addCommands(
                Commands.slash("event_modal", "Create an event with a modal")
                        .setContexts(InteractionContextType.ALL)
                        .setIntegrationTypes(IntegrationType.ALL));

        commands.addCommands(
                Commands.slash("greg_help", "Help with gregbot")
                        .setContexts(InteractionContextType.ALL)
                        .setIntegrationTypes(IntegrationType.ALL)
                        .addOption(STRING, "topic", "The topic to get help with", false));
        
        commands.addCommands(
                Commands.slash("upcoming", "Get a list of upcoming events")
                        .setContexts(InteractionContextType.GUILD)
                        .setIntegrationTypes(IntegrationType.GUILD_INSTALL)
                        .addOption(STRING, "days", "events within X days", true));

        // TODO add a command to get the list of commands
        // TODO delete event command


        // Privileged commands
        commands.addCommands(
                Commands.slash("info", "DESCRIPTION")
                        .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.BAN_MEMBERS)));
        commands.addCommands(
                Commands.slash("reset_db", "Reset the database /srs. This will break everything")
                        .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.BAN_MEMBERS))
                        .setContexts(InteractionContextType.GUILD));

        // Send the commands to Discord
        commands.queue();

    }

    // Slash Command Listener
    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        // only accept commands from guilds (the example had this)
        if (event.getGuild() == null)
            return;

        switch (event.getName()) {
            case "upcoming":
                ArrayList<String> upcoming = null;

                try {
                    upcoming =  db.getUpcomingEvents(event.getOption("days").getAsInt());
                } catch (Exception e) {
                    event.reply(funnyError() + "\n Please Enter a Realistic Integer Value").setEphemeral(true).queue();
                    return;
                }

                if (upcoming.isEmpty()) {
                    event.reply("Sorry, nothing found").setEphemeral(true).queue();
                    return;
                }

                String post = "Here's what we got:\n";
                for (String str : upcoming) {
                    post += "* " + str + "\n";
                }

                event.reply(post).setEphemeral(true).queue();

                break;

            case "greg_help":
                switch (event.getOption("topic").getAsString()) {
                    case "timezones":
                        event.reply("# Timezones\n"
                                + "Visit: https://docs.oracle.com/cd/E72987_01/wcs/tag-ref/MISC/TimeZones.html \n"
                                + "Find your **full** Time Zone ID. You cannot, for example, use \"PST\", you must use \"America/Los_Angeles\"")
                                .setEphemeral(true).queue();
                        break;

                    default:
                        event.reply("I don't know that one.").setEphemeral(true).queue();
                        break;
                }

            case "event_modal":
                createModalOne(event);
                break;

            case "info":
                spitInfo(event);
                break;

            case "reset_db":
                db.resetDB();
                event.reply("# DATABASE RESET").queue();
                break;

            default:
                event.reply(funnyError() + "\nUnknown command").setEphemeral(true).queue();
                break;
        }
    }


    // Modal Listener
    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    @Override
    public void onModalInteraction(ModalInteractionEvent event) {

        switch (event.getModalId()) {
            case "event_modal_1":

                // check if they actually filled out the temp channels correctly
                boolean channels = false;
                try {
                    channels = Boolean.parseBoolean(event.getValue("temp_channels").getAsString().toLowerCase());
                } catch (Exception e) {
                    event.reply("Please enter true or false for temp channels\n" + e.getMessage()).setEphemeral(true)
                            .queue();
                    return;
                }

                // check repeats
                String repeats = event.getValue("repeats").getAsString().toLowerCase();

                if (repeats.equals("never")
                        || repeats.equals("daily")
                        || repeats.equals("weekly")
                        || repeats.equals("biweekly")
                        || repeats.equals("monthly")) {
                    // do nothing
                } else {
                    event.reply("## Please enter a valid repeat value\n" +
                            "Options: *never*, *daily*, *weekly*, *biweekly*, *monthly*").setEphemeral(true).queue();
                    return;
                }

                // random 7 digit number
                int gameID = rand.nextInt(9000000) + 1000000;
                // Hashmap key: userID
                String mapKey = event.getUser().getId();

                String owner = event.getUser().getName();

                // store in the hashmap
                games.put(

                        mapKey,

                        new GameEvent(
                                gameID,
                                event.getValue("name").getAsString(),
                                event.getValue("description").getAsString(),
                                repeats,
                                Integer.parseInt(event.getValue("max_players").getAsString()),
                                owner,
                                Long.parseLong(event.getUser().getId()),
                                channels));

                event.reply("# WAIT, you're not done yet!\n")
                        .addActionRow(
                                Button.success("modal_1_continue", "Continue"),
                                Button.danger("modal_1_stop", "Delete Event"))
                        .setEphemeral(true)
                        .queue();
                break;

            case "event_modal_2":

                GameEvent game = null;

                // TODO check timezone. This isn't working, idk why it's not throwing an
                // exception
                ZoneId zone = null;
                String timezone = "EMPTY";
                try {
                    zone = ZoneId.of(event.getValue("timezone").getAsString());
                    timezone = zone.getId();
                } catch (Exception e) {
                    event.reply("**Incorrect Timezone**").setEphemeral(true).queue();
                    return;
                }

                // convert to unix time
                long start = 0, end = 0, signup = 0;
                try {
                    start = stringToUnixTime(event.getValue("start_time").getAsString(), zone);
                    end = stringToUnixTime(event.getValue("end_time").getAsString(), zone);
                    signup = stringToUnixTime(event.getValue("signup_close_time").getAsString(), zone);

                } catch (Exception e) {
                    event.reply("**Something went wrong parsing the Date-Times you entered**").setEphemeral(true).queue();
                    return;
                }

                if (start == 0 || end == 0 || signup == 0) {
                    event.reply("**Something went wrong parsing the Date-Times you entered**").setEphemeral(true);
                    return;
                }

                if (games.containsKey(event.getUser().getId())) {

                    game = games.get(event.getUser().getId());

                    game.timezone = timezone;
                    game.startTime = start;
                    game.endTime = end;
                    game.closeTime = signup;

                } else {
                    event.reply("This really shouldn't have happened\n"
                            + "the GameEvent wasn't present in hashmap when modal_2 was submited").setEphemeral(true)
                            .queue();
                    return;
                }
                // TODO check if the game is in the database

                // DEBUG
                event.reply(game.toString()).setEphemeral(true).queue();

                // TODO add to the database
                db.addEvent(game);

                // TODO make initial post to the event feed channel
                makeGamePost(game, event.getJDA());

                // Remove the game from the hashmap
                games.remove(event.getUser().getId());
                break;

            default:
                break;
        }
    }


    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String[] split = event.getButton().getId().split(":");
        String buttonID = split[0];
        String gameID = "";
        String userID = event.getUser().getId();
        try {
            gameID = split[1];
        } catch (Exception e) {
            // this means the button didn't have an eventID appended.
            // no worries
        }
        switch (buttonID) {
            case "modal_1_continue":
                createModalTwo(event);
                break;

            case "modal_1_stop":
                event.reply("I'll just delete that then -.-").setEphemeral(true).queue();
                games.remove(userID); // delete from hashmap
                event.getMessage().delete().queue(); // delete the message
                break;

            case "acceptGame":
                event.deferEdit().queue();
                // set status
                db.setAttendee(gameID, userID, "accepted");

                // DEBUG
                //event.reply("DEBUG MESSAGE: accepted, " + gameID + ", " + userID).setEphemeral(true).queue();
                changeMsgAccepted(event.getMessage(), userID);
                break;

            case "declineGame":
                event.deferEdit().queue();
                // set status
                db.setAttendee(gameID, userID, "declined");
                
                // DEBUG
                //event.reply("DEBUG MESSAGE: declined, " + gameID + ", " + userID).setEphemeral(true).queue();
                changeMsgDeclined(event.getMessage(), userID);
                break;

            default:
            // TODO add a default case
                break;
        }
    }

    private void spitInfo(SlashCommandInteractionEvent event) {
        String info = "*Greg is a bot for scheduling events.* \n"
                + "**database:** " + db.getURL() + "\n"
                + "**bot id:** " + event.getJDA().getSelfUser().getId() + "\n"
                + "**bot name:** " + event.getJDA().getSelfUser().getName() + "\n"
                + "**games in memory:** " + games.size() + "\n"
                + "**events in database: **" + db.getEventCount() + "\n";
        // + "users in database: " + db.getUserCount() + "\n"; TODO

        event.reply(info).queue();
    }

    private void createModalOne(SlashCommandInteractionEvent event) {

        event.replyModal(Modal.create("event_modal_1", "Create an event")
                .addComponents(
                        ActionRow.of(TextInput.create("name", "Name", TextInputStyle.SHORT)
                                .setMinLength(1)
                                .setMaxLength(100)
                                .build()),
                        ActionRow.of(TextInput.create("description", "Description", TextInputStyle.PARAGRAPH)
                                .setMinLength(1)
                                .setMaxLength(500)
                                .build()),
                        ActionRow
                                .of(TextInput.create("repeats", "How often does the game repeat?", TextInputStyle.SHORT)
                                        .setPlaceholder("never, daily, weekly, biweekly, or monthly")
                                        .setMinLength(1)
                                        .setMaxLength(20)
                                        .setValue("never")
                                        .build()),
                        ActionRow.of(TextInput.create("max_players", "Max Players, integer 1-999", TextInputStyle.SHORT)
                                .setPlaceholder("not counting yourself")
                                .setMinLength(1)
                                .setMaxLength(3)
                                .build()),
                        ActionRow.of(TextInput.create("temp_channels", "Temp channels", TextInputStyle.SHORT)
                                .setPlaceholder("true or false")
                                .setMinLength(1)
                                .setMaxLength(5)
                                .setValue("false")
                                .build()))
                .build()).queue();
    }

    public void createModalTwo(ButtonInteractionEvent event) {
        event.replyModal(Modal.create("event_modal_2", "Create an event, part 2")
                .addComponents(
                        ActionRow.of(TextInput
                                .create("timezone", "Timezone Code, /greg_help timezones", TextInputStyle.SHORT)
                                .setPlaceholder("UTC")
                                .setMinLength(2)
                                .setMaxLength(20)
                                .build()),
                        ActionRow.of(TextInput
                                .create("start_time", "Starts at 'YYYY-MM-DD-HH-mm' (24 hour time)",
                                        TextInputStyle.SHORT)
                                .setPlaceholder("2026-01-01-13-00")
                                .setMinLength(16)
                                .setMaxLength(16)
                                .build()),
                        ActionRow.of(TextInput
                                .create("end_time", "Ends at 'YYYY-MM-DD-HH-mm' (24 hour time)", TextInputStyle.SHORT)
                                .setPlaceholder("2026-01-01-16-15")
                                .setMinLength(16)
                                .setMaxLength(16)
                                .build()),
                        ActionRow.of(TextInput
                                .create("signup_close_time", "Cut-off time for signing up", TextInputStyle.SHORT)
                                .setPlaceholder("YYYY-MM-DD-HH-mm, same deal, still 24 hour time")
                                .setMinLength(16)
                                .setMaxLength(16)
                                .build()))
                .build()).queue();
    }

    private long stringToUnixTime(String str, ZoneId zone) {
        // TODO check string formatting

        // Formatter that matches the input pattern
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm");

        ZonedDateTime zonedDateTime = LocalDateTime.parse(str, formatter).atZone(zone);

        // Convert to Unix timestamp (seconds since epoch)
        return zonedDateTime.toEpochSecond();
    }

    private void makeGamePost(GameEvent game, JDA jda) {

        String post = "# " + game.name + "\n"
                + "Run by " + game.owner + "\n"
                // + "## Details\n"
                + "Time: <t:" + game.startTime + ":f> to <t:" + game.endTime + ":t>\n"
                + "Game Repeats: " + game.repeats + "\n"
                + "Seats: " + game.maxPlayers + "\n"
                // + "Timezone: " + game.timezone + "\n"
                + "Sign-ups closes at : <t:" + game.closeTime + ":f>\n"
                // + "## Description \n"
                + ">>> " + game.description + "\n"
                + "**Accepted:** ..\n"
                + "**Declined:** ..\n";

        jda.getTextChannelById(EVENT_FEED_ID).sendMessage(post).addActionRow(
            Button.success("acceptGame:" + game.id , "Accept"),
            Button.danger ("declineGame:" + game.id, "Decline")).queue();
    }

// TODO, unfuck these
private void changeMsgAccepted(net.dv8tion.jda.api.entities.Message msg, String userID) {
    String post = msg.getContentRaw();
    String[] lines = post.split("\n");

    StringBuilder newPost = new StringBuilder();

    for (int i = 0; i < lines.length; i++) {
        if (lines[i].startsWith("**Accepted:**")) {
            if (!lines[i].contains(userID)) {
                lines[i] += " <@" + userID + ">,";
            }
        }
        if (lines[i].startsWith("**Declined:**")) {
            lines[i] = lines[i].replace("<@" + userID + ">,", "").replaceAll("\\s+", " ").trim();
        }
        newPost.append(lines[i]).append("\n");
    }

    msg.editMessage(newPost.toString().trim()).queue();
}

private void changeMsgDeclined(net.dv8tion.jda.api.entities.Message msg, String userID) {
    String post = msg.getContentRaw();
    String[] lines = post.split("\n");

    StringBuilder newPost = new StringBuilder();

    for (int i = 0; i < lines.length; i++) {
        if (lines[i].startsWith("**Declined:**")) {
            if (!lines[i].contains(userID)) {
                lines[i] += " <@" + userID + ">,";
            }
        }
        if (lines[i].startsWith("**Accepted:**")) {
            lines[i] = lines[i].replace("<@" + userID + ">,", "").replaceAll("\\s+", " ").trim();
        }
        newPost.append(lines[i]).append("\n");
    }

    msg.editMessage(newPost.toString().trim()).queue();
}

    private String funnyError() {
        String[] errors = {
                "LETMEOUTLETMEOUTLETMEOUT",
                "Oh, fr?",
                "BRUH",
                "breh",
                "I'm afraid I can't let you do that $USER",
                "No clue boss.",
                "What are you even saying rn?",
                "Ok, dropping all tables",
                "HUH?",
                "That's a really cool string of characters and/or numbers boss, sadly, I have no fucking idea what to do with it.", 
                "wut",
                "wat?",
                "$USER is not in te sudoers file. This incident will be reported.",
                "You're scaring me boss.",
                "I'm straight up \"thowing it\", and by \"it\", haha, well. Let's just say an exception."
        };
        return errors[rand.nextInt(errors.length)];

    }

}
