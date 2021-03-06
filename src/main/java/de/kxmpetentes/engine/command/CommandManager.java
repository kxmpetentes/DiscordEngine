package de.kxmpetentes.engine.command;

import de.kxmpetentes.engine.DiscordCore;
import de.kxmpetentes.engine.manager.GuildCacheManager;
import de.kxmpetentes.engine.model.GuildModel;
import lombok.Data;
import net.dv8tion.jda.api.entities.ChannelType;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * @author kxmpetentes
 * Website: kxmpetentes.de
 * GitLab: gitlab.com/kxmpetentes
 * GitHub: git.kxmpetentes.de
 * Erstellt am: 04.01.2021 um 21:47
 */

@Data
public class CommandManager {

    private GuildCacheManager serverCache;
    private List<Command> commandList;

    /**
     * @param event extends MessageReceivedEvent
     * @return command executed correctly
     * @see net.dv8tion.jda.api.events.message.MessageReceivedEvent
     */
    public boolean performCommand(MessageReceivedEvent event) {

        ChannelType channelType = event.getChannelType();
        Message message = event.getMessage();

        String prefix = DiscordCore.getInstance().getPrefix();

        String contentRaw = message.getContentRaw();
        if (channelType == ChannelType.PRIVATE) {

            Command command = iterateCommands(message, prefix, true);
            if (command == null) {
                return false;
            }

            command.execute(event, contentRaw.split(" "));
            return true;
        }

        if (channelType == ChannelType.TEXT) {
            Guild guild = event.getGuild();

            if (DiscordCore.getInstance().isMongoDBEnabled()) {
                if (serverCache == null) {
                    serverCache = DiscordCore.getInstance().getGuildCacheManager();
                    return false;
                }

                GuildModel server = serverCache.getGuildModel(guild);
                prefix = server.getPrefix();
            }

            Command command = iterateCommands(message, prefix, false);
            if (command == null) {
                return false;
            }

            command.execute(event, contentRaw.split(" "));
            return true;
        }

        return false;
    }

    /**
     * Iterates all commands that in the command list
     *
     * @param message   message from the command
     * @param prefix
     * @param isPrivate
     * @return the command from string, prefix & isPrivate
     */
    public Command iterateCommands(Message message, String prefix, boolean isPrivate) {
        String[] split = message.getContentRaw().split(" ");
        String commandString = split[0].replace(prefix, "");

        for (Command command : commandList) {

            if (command.getAliases().length == 0)
                return getCommandByName(command, commandString, isPrivate);
            else
                return getCommandByAliases(command, commandString, isPrivate);

        }

        return null;
    }

    private Command getCommandByName(Command command, String commandName, boolean isPrivate) {
        if (command.getCommandName().equalsIgnoreCase(commandName)) {
            if (isPrivate && !command.isGuildCommand() || !isPrivate && command.isGuildCommand() || !isPrivate) {
                return command;
            }
        }

        return null;
    }

    private Command getCommandByAliases(Command command, String commandString, boolean isPrivate) {
        for (String alias : command.getAliases()) {
            if (alias.equalsIgnoreCase(commandString) || command.getCommandName().equalsIgnoreCase(commandString)) {
                if (isPrivate && !command.isGuildCommand() || !isPrivate && command.isGuildCommand() || !isPrivate) {
                    return command;
                }
            }
        }

        return null;
    }

    /**
     * Adds a command to the commandlist
     *
     * @param command command extends de.kxmpetentes.engine.command.impl.ICommand
     * @see de.kxmpetentes.engine.command.Command
     */
    public void addCommand(Command command) {
        if (commandList == null) {
            setCommandList(new ArrayList<>());
        }

        commandList.add(command);
    }
}