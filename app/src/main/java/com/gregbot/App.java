package com.gregbot;

import static net.dv8tion.jda.api.interactions.commands.OptionType.INTEGER;
import static net.dv8tion.jda.api.interactions.commands.OptionType.STRING;

import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.Random;

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
    private static final long SERVER_ID = 1273033154826997790L;
    private static final String NAME = "greg";
    private static final String URL = "jdbc:sqlite:" + NAME;

    Random rand = new Random();

    private DbController db = new DbController(NAME, URL);

    // Hashmap of GameEvents stores their info before they are added to the database
    HashMap<String, GameEvent> games = new HashMap<>();

    public static void main(String[] args) throws Exception {

        // Create a new JDABuilder, load token from file, and empty list of intents
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
                Commands.slash("gregvent", "Create an event")
                        .setContexts(InteractionContextType.ALL)
                        .setIntegrationTypes(IntegrationType.ALL) // can be installed anywhere
                        .addOption(STRING, "name", "The Title", true)
                        .addOption(STRING, "description", "A short description", true)
                        .addOption(INTEGER, "max_players", "integer value", true)
                        .addOption(STRING, "start_time", "begining", true)
                        .addOption(STRING, "end_time", "the close", true)
                        .addOption(STRING, "signup_close_time", "optional", false)
                        .addOption(STRING, "timezone", "optional if user timezone is set. '/greg_help tz'", false)
                        .addOption(STRING, "repeats", "daily, weekly, biweekly, or monthly (optional)", false));

        commands.addCommands(
                Commands.slash("event_modal", "Create an event with a modal")
                        .setContexts(InteractionContextType.ALL)
                        .setIntegrationTypes(IntegrationType.ALL));

        // TODO help command
        commands.addCommands(
                Commands.slash("greg_help", "Help with gregbot")
                        .setContexts(InteractionContextType.ALL)
                        .setIntegrationTypes(IntegrationType.ALL)
                        .addOption(STRING, "topic", "The topic to get help with", false));

        // Privileged commands
        // !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
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
            case "say":
                say(event, event.getOption("content").getAsString());
                break;

            case "greg_help":
                switch (event.getOption("topic").getAsString()) {
                    case "timezones":
                        event.reply("# Timezones\n" 
                            + "Visit: https://docs.oracle.com/cd/E72987_01/wcs/tag-ref/MISC/TimeZones.html \n"
                            + "Find your **full** Time Zone ID. You cannot, for example, use \"PST\", you must use \"America/Los_Angeles\"").setEphemeral(true).queue();
                        break;

                    default:

                        event.reply("I don't know that one.").setEphemeral(true).queue();
                        break;
                }

            case "gregvent":
                createEvent(event);
                break;

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
                event.reply("Boss, idk what that command is.").setEphemeral(true).queue();
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
                                Button.success("modal_1_continue", "Cotinue"),
                                Button.danger("modal_1_stop", "Delete Event"))
                        .setEphemeral(true)
                        .queue();
                break;

            case "event_modal_2":

                // TODO check timezone
                ZoneId zone = null;
                String timezone = "EMPTY";
                try {
                    zone = ZoneId.of(event.getValue("timezone").getAsString());
                    timezone = zone.getId();
                } catch (Exception e) {
                    event.reply("**Incorrect Timezone**").setEphemeral(true);
                }

                // convert to unix time
                long start = 0, end = 0, signup = 0;
                try {
                    start = stringToUnixTime(event.getValue("start_time").getAsString(), zone);
                    end = stringToUnixTime(event.getValue("end_time").getAsString(), zone);
                    signup = stringToUnixTime(event.getValue("signup_close_time").getAsString(), zone);

                } catch (Exception e) {
                    event.reply("**Something went wrong parsing the Date-Times you entered**").setEphemeral(true);
                }

                if (games.containsKey(event.getUser().getId())) {

                    // TODO update the hashmap with the new values
                    GameEvent game = games.get(event.getUser().getId());

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
                event.reply(games.get(event.getUser().getId()).toString()).setEphemeral(true).queue();

                // TODO add to the database

                // Remove the game from the hashmap
                games.remove(event.getUser().getId());
                break;

            default:
                break;
        }

    }

    private void say(SlashCommandInteractionEvent event, String content) {
        event.reply(content).queue();
    }

    private void spitInfo(SlashCommandInteractionEvent event) {
        String info = "*Greg is a bot for scheduling events.* \n"
                + "**database:** " + db.getURL() + "\n"
                + "**bot id:** " + event.getJDA().getSelfUser().getId() + "\n"
                + "**bot name:** " + event.getJDA().getSelfUser().getName() + "\n"
                + "**events in memory:** " + games.size() + "\n";
        // + "events in database: " + db.getEventCount() + "\n" // TODO
        // + "users in database: " + db.getUserCount() + "\n";

        event.reply(info).queue();
    }

    // TODO this function is unused
    private void createEvent(SlashCommandInteractionEvent event) {
        String name = event.getOption("name").getAsString();
        String description = event.getOption("description").getAsString();

        // TODO convert to unix time
        String start_time = event.getOption("start_time").getAsString();
        String end_time = event.getOption("end_time").getAsString();
        String signup_close_time = event.getOption("signup_close_time").getAsString();

        String repeats = event.getOption("repeats").getAsString();
        int max_players = event.getOption("max_players").getAsInt();
        String channel_id = event.getChannel().getId();
        String guild_id = event.getGuild().getId();
        String owner = event.getUser().getId();

        // event.reply(name + "\n" +
        // description + "\n" +
        // start_time + "\n" +
        // end_time + "\n" +
        // signup_close_time + "\n" +
        // repeats + "\n" +
        // max_players + "\n" +
        // channel_id + "\n" +
        // guild_id + "\n" +
        // owner).queue();
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String[] split = event.getButton().getId().split(":");
        String buttonID = split[0];
        String eventID = "";
        try {
            eventID = split[1];
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
                games.remove(event.getUser().getId()); // delete from hashmap
                event.getMessage().delete().queue(); // delete the message
                break;

            default:
                break;
        }
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

        // first modal
        // name
        // description
        // repeats dropdown
        // max players

        // second modal
        // timezone
        // start time
        // end time
        // signup close time

    }

    public void createModalTwo(ButtonInteractionEvent event) {
        event.replyModal(Modal.create("event_modal_2", "Create an event, part 2")
                .addComponents(
                        ActionRow.of(TextInput.create("timezone", "Timezone Code, /greg_help timezones", TextInputStyle.SHORT)
                                .setPlaceholder("UTC")
                                .setMinLength(2)
                                .setMaxLength(20)
                                .build()),
                        ActionRow.of(TextInput
                                .create("start_time", "Starts at 'YY-MM-DD-HH-mm' (24 hour time)", TextInputStyle.SHORT)
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

    // TODO format this
    public long stringToUnixTime(String str, ZoneId zone) {
        // YYYY-MM-DD-HH-mm

        // Define a formatter that matches the input pattern
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm");

        // Parse the input date string to LocalDateTime
        LocalDateTime localDateTime = LocalDateTime.parse(str, formatter);

        // Convert to ZonedDateTime
        ZonedDateTime zonedDateTime = localDateTime.atZone(zone);

        // Convert to Unix timestamp (seconds since epoch)
        return zonedDateTime.toEpochSecond();

    }
}
