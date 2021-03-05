package de.kxmpetentes.engine.manager;

import com.mongodb.client.model.Filters;
import de.kxmpetentes.engine.DiscordCore;
import de.kxmpetentes.engine.model.GuildModel;
import lombok.Getter;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.TextChannel;
import org.bson.Document;

import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;

/**
 * @author kxmpetentes
 * Website: kxmpetentes.de
 * GitLab: gitlab.com/kxmpetentes
 * GitHub: git.kxmpetentes.de
 * Erstellt am: 05.01.2021 um 15:08
 */

@Getter
public class GuildCacheManager {

    private final HashMap<Long, GuildModel> guildCache = new HashMap<>();
    private final DiscordCore discordCore;

    private Timer timer;

    public GuildCacheManager(DiscordCore discordCore, JDA jda) {
        this.discordCore = discordCore;

        initCache(jda);
        timer = getBackupTimer();
    }

    private void initCache(JDA jda) {
        for (Guild guild : jda.getGuilds()) {
            for (Document document : MongoAPI.getCollection("DiscordEngine").find(Filters.eq("settings", guild.getId()))) {
                if (document != null) {

                    String prefix = getPrefixFromDocument(document);
                    TextChannel joinChannel = getChannelFromDocument(guild, document, "joinChannel");
                    TextChannel quitChannel = getChannelFromDocument(guild, document, "quitChannel");
                    Role autoRole = getRoleFromDocument(guild, document, "autoRole");

                    GuildModel guildModel = new GuildModel(guild, prefix, joinChannel, quitChannel, autoRole);
                    guildModel.setGuildDocument(document);
                    guildCache.put(guild.getIdLong(), guildModel);

                } else {
                    GuildModel guildModel = new GuildModel(guild, discordCore.getPrefix());
                    addNewGuild(guildModel);
                    guildCache.put(guild.getIdLong(), guildModel);
                }
            }
        }
    }

    private String getPrefixFromDocument(Document document) {
        if (document.containsKey("prefix"))
            return document.getString("prefix");
        else
            return DiscordCore.getInstance().getPrefix();
    }

    private TextChannel getChannelFromDocument(Guild guild, Document document, String key) {
        if (document.containsKey(key)) {
            try {
                return guild.getTextChannelById(document.getString(key));
            } catch (NumberFormatException e) {
                return guild.getTextChannelById(Long.MIN_VALUE);
            }
        }

        return guild.getTextChannelById(Long.MIN_VALUE);
    }

    private Role getRoleFromDocument(Guild guild, Document document, String key) {
        if (document.containsKey(key)) {
            try {
                return guild.getRoleById(document.getString(key));
            } catch (NumberFormatException e) {
                return guild.getRoleById(Long.MIN_VALUE);
            }
        }

        return guild.getRoleById(Long.MIN_VALUE);
    }

    public GuildModel getGuildModel(Guild guild) {
        return getGuildModel(guild.getIdLong());
    }

    public GuildModel getGuildModel(long guildId) {

        if (guildCache.containsKey(guildId)) {
            return guildCache.get(guildId);
        } else {
            addGuildToCache(discordCore.getJda().getGuildById(guildId));
            getGuildModel(guildId);

        }

        return null;
    }

    public void addGuildToCache(Guild guild) {
        GuildModel guildModel = new GuildModel(guild, discordCore.getPrefix());
        guildCache.put(guild.getIdLong(), guildModel);

        addNewGuild(guildModel);
    }

    public void removeGuildFromCache(Guild guild) {
        GuildModel guildModel = guildCache.get(guild.getIdLong());

        updateGuild(guildModel);
        guildCache.remove(guild.getIdLong());
    }

    public void addNewGuild(GuildModel guildModel) {

        Document document = MongoAPI.getCollection("DiscordCore").find(Filters.eq("settings", guildModel.getGuildId())).first();

        if (document == null) {
            document = new Document();
            document.append("settings", guildModel.getGuildId());
            document.append("prefix", guildModel.getPrefix());
        }
        MongoAPI.getCollection("DiscordEngine").insertOne(updateGuildChannels(guildModel, document));
    }

    public void updateGuild(GuildModel guildModel) {

        MongoAPI.getCollection("DiscordEngine").findOneAndDelete(Filters.eq("settings", guildModel.getGuildId()));

        Document document = new Document();
        document.append("settings", guildModel.getGuildId());
        document.append("prefix", guildModel.getPrefix());

        MongoAPI.getCollection("DiscordEngine").insertOne(updateGuildChannels(guildModel, document));
    }

    public void updateGuilds() {
        for (GuildModel guildModel : guildCache.values()) {
            updateGuild(guildModel);
        }
    }

    private Document updateGuildChannels(GuildModel guildModel, Document document) {

        if (guildModel.getJoinChannel() == null) {
            document.append("joinChannel", String.valueOf(Long.MAX_VALUE));
        } else
            document.append("joinChannel", guildModel.getJoinChannel().getId());

        if (guildModel.getQuitChannel() == null) {
            document.append("quitChannel", String.valueOf(Long.MAX_VALUE));
        } else
            document.append("quitChannel", guildModel.getQuitChannel().getId());

        if (guildModel.getAutoRole() == null) {
            document.append("autoRole", String.valueOf(Long.MAX_VALUE));
        } else
            document.append("autoRole", guildModel.getAutoRole().getId());

        guildModel.setGuildDocument(document);

        return document;
    }

    public Timer getBackupTimer() {

        timer = new Timer();

        timer.schedule(new TimerTask() {
            @Override
            public void run() {

                updateGuilds();

            }
        }, 1, 1000L * 60);

        return timer;
    }
}