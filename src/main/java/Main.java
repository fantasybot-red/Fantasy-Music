import com.fasterxml.jackson.databind.deser.std.NumberDeserializers;
import com.google.gson.*;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.lava.common.natives.NativeLibraryLoader;
import com.sun.management.OperatingSystemMXBean;
import lavaplayer.*;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.events.ReadyEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceLeaveEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceMoveEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.JDABuilder;
import javax.security.auth.login.LoginException;

import net.dv8tion.jda.api.hooks.*;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.entities.*;
import com.sedmelluq.discord.lavaplayer.player.*;
import com.sedmelluq.discord.lavaplayer.source.*;
import com.sedmelluq.discord.lavaplayer.track.*;
import okhttp3.*;
import org.apache.hc.core5.http.*;
import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.exceptions.*;
import se.michaelthelin.spotify.model_objects.credentials.*;
import se.michaelthelin.spotify.model_objects.specification.*;
import se.michaelthelin.spotify.requests.authorization.client_credentials.*;
import se.michaelthelin.spotify.requests.data.playlists.*;
import se.michaelthelin.spotify.requests.data.tracks.*;

import oshi.hardware.* ;
import oshi.SystemInfo;

import java.lang.management.ManagementFactory;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.time.Instant;
import java.io.IOException;
import java.util.*;
import java.net.*;
import java.util.concurrent.*;

public class Main extends ListenerAdapter {
  private static final String clientId = "id spotify";
  private static final String clientSecret = "spotify client Secret";

  private static final String statcordtoken = "statcord.com token";

  private static final String bottoken = "discord bot token";
  private static JsonArray active = new JsonArray();

  private static JsonArray commands = new JsonArray();

  private static int commandrun = 0;

  private static long sfex = 0;

  private static final SystemInfo si = new SystemInfo();

  private static long[] cpusave = si.getHardware().getProcessor().getSystemCpuLoadTicks();

  private static final SpotifyApi spotifyApi = new SpotifyApi.Builder()
      .setClientId(clientId)
      .setClientSecret(clientSecret)
      .build();
  private static final ClientCredentialsRequest clientCredentialsRequest = spotifyApi.clientCredentials()
      .build();

  public static void clientCredentials_Sync() {
    long timesec = Instant.now().getEpochSecond();
    if (timesec > sfex) {
      try {
        final CompletableFuture<ClientCredentials> clientCredentialsFuture = clientCredentialsRequest.executeAsync();

        final ClientCredentials clientCredentials = clientCredentialsFuture.join();

        spotifyApi.setAccessToken(clientCredentials.getAccessToken());
        long timenow = Instant.now().getEpochSecond();
        sfex = clientCredentials.getExpiresIn() + timenow;
      } catch (CompletionException e) {
        System.out.println("Error: " + e.getCause().getMessage());
      } catch (CancellationException e) {
        System.out.println("Async operation cancelled.");
      }
    }
  }

  public static void main(String[] args) throws LoginException {
    JDABuilder jda = JDABuilder.createLight(bottoken);
    jda.enableIntents(
        GatewayIntent.GUILD_MEMBERS,
        GatewayIntent.GUILD_MESSAGES,
        GatewayIntent.GUILD_VOICE_STATES,
        GatewayIntent.GUILD_PRESENCES,
        GatewayIntent.GUILD_MESSAGE_REACTIONS,
        GatewayIntent.GUILD_BANS,
        GatewayIntent.GUILD_EMOJIS,
        GatewayIntent.GUILD_MESSAGE_TYPING);
    jda.enableCache(CacheFlag.VOICE_STATE, CacheFlag.MEMBER_OVERRIDES);
    jda.setMemberCachePolicy(MemberCachePolicy.ALL);
    jda.addEventListeners(new Main());
    clientCredentials_Sync();
    int shard = 2;
    for (int i = 0; i < shard; i++)
    {
      jda.useSharding(i, shard).build();
    }
  }

  @Override
  public void onReady(ReadyEvent event) {
    event.getJDA().getPresence().setActivity(Activity.playing("Ping me for help"));
    System.out.println(event.getJDA().getShardInfo().getShardId()+1 + " / " + event.getJDA().getShardInfo().getShardTotal() + " is ready!");
    if (event.getJDA().getShardInfo().getShardId()+1 == event.getJDA().getShardInfo().getShardTotal()) {
      System.out.println("Login as " + event.getJDA().getSelfUser().getAsTag());
      ScheduledExecutorService threadPool = Executors.newSingleThreadScheduledExecutor();
      threadPool.scheduleAtFixedRate(() -> {poststatus(event.getJDA());}, 0, 60, TimeUnit.SECONDS);
    }
  }

  public void poststatus(JDA jda) {
    MediaType JSON = MediaType.get("application/json; charset=utf-8");
    long RAM_TOTAL = Runtime.getRuntime().totalMemory();
    long RAM_FREE = Runtime.getRuntime().freeMemory();
    long RAM_USED = RAM_TOTAL - RAM_FREE;
    DecimalFormat s = new DecimalFormat("0.00");
    s.setRoundingMode(RoundingMode.UP);
    long bandwidth = 0;
    HardwareAbstractionLayer hal = si.getHardware();
    double cpu_data = hal.getProcessor().getSystemCpuLoadBetweenTicks(cpusave);
    cpusave = hal.getProcessor().getSystemCpuLoadTicks();
    String cpu = s.format(cpu_data*100d);
    for (NetworkIF net : hal.getNetworkIFs()) {
      bandwidth += net.getBytesRecv() + net.getBytesSent();
    }
    JsonObject a = new JsonObject();
    a.addProperty("id", jda.getSelfUser().getId());
    a.addProperty("key", statcordtoken);
    a.addProperty("servers", String.valueOf(jda.getGuilds().size()));
    a.addProperty("users", String.valueOf(jda.getUsers().size()));
    a.addProperty("active" , active.toString());
    a.addProperty("commands", String.valueOf(commandrun));
    a.addProperty("popular", commands.toString());
    a.addProperty("memactive", String.valueOf(RAM_USED));
    a.addProperty("memload",  String.valueOf(Math.round(((double) RAM_USED / RAM_TOTAL) * 100)));
    a.addProperty("cpuload", String.valueOf(cpu));
    a.addProperty("bandwidth", String.valueOf(bandwidth));
    OkHttpClient client = new OkHttpClient();
    RequestBody body = RequestBody.create(a.toString().replace("\"[", "[").replace("]\"", "]").replace("\\\"", "\""), JSON);
    Request request = new Request.Builder().url("https://api.statcord.com/v3/stats")
        .post(body)
        .build();
    try {
      Response response = client.newCall(request).execute();
      try {
        if (!response.isSuccessful()) {
          throw new IOException("Unexpected code " + response);
        } else {
          active = new JsonArray();
          commands = new JsonArray();
          commandrun = 0;
        }
      } finally {
        response.body().close();
        response.close();
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public void logdata(MessageReceivedEvent event, String command) {
    boolean isActive = false;
    for (JsonElement s : active.getAsJsonArray()) {
      if (s.getAsString().equals(event.getAuthor().getId())) {
        isActive = true;
        break;
      }
    }
    if (!isActive) {
      active.add(event.getAuthor().getId());
    }

    Boolean check = false;
    int i = 0;
    for (JsonElement o : commands.getAsJsonArray()) {
      JsonObject obj = o.getAsJsonObject();
      if (obj.get("name").getAsString().equals(command)) {
        JsonObject oss = new JsonObject();
        oss.addProperty("name", command);
        oss.addProperty("count", String.valueOf(obj.get("count").getAsInt() + 1));
        commands.getAsJsonArray().set(i, oss);
        check = true;
        break;
      }
      i++;
    }
    commandrun++;
    if (!check) {
      JsonObject o = new JsonObject();
      o.addProperty("name", command);
      o.addProperty("count", 1);
      commands.add(o);
    }
  }

  private final AudioPlayerManager playerManager;
  private final Map<Long, GuildMusicManager> musicManagers;

  private Main() {
    this.musicManagers = new HashMap<>();

    this.playerManager = new DefaultAudioPlayerManager();
    AudioSourceManagers.registerRemoteSources(playerManager);
    AudioSourceManagers.registerLocalSource(playerManager);
  }

  private synchronized GuildMusicManager getGuildAudioPlayer(Guild guild) {
    long guildId = Long.parseLong(guild.getId());
    GuildMusicManager musicManager = musicManagers.get(guildId);

    if (musicManager == null) {
      musicManager = new GuildMusicManager(playerManager);
      musicManagers.put(guildId, musicManager);
    }

    guild.getAudioManager().setSendingHandler(musicManager.getSendHandler());

    return musicManager;
  }

  @Override
  public void onGuildVoiceLeave(GuildVoiceLeaveEvent event) {
    if (event.getChannelLeft() == event.getGuild().getSelfMember().getVoiceState().getChannel()) {
      List<Member> s = event.getChannelLeft().getMembers();
      int i = 0;
      for (Member m : s) {
        if (!m.getUser().isBot()) {
          i++;
        }
      }
      if (i == 0) {
        GuildMusicManager musicManager = getGuildAudioPlayer(event.getGuild());
        musicManager.scheduler.queue.clear();
        musicManager.player.destroy();
        event.getGuild().getAudioManager().closeAudioConnection();
      }
    }
  }

  @Override
  public void onGuildVoiceMove(GuildVoiceMoveEvent event) {
    if (event.getMember() == event.getGuild().getSelfMember()) {
      List<Member> s = event.getChannelJoined().getMembers();
      int i = 0;
      for (Member m : s) {
        if (!m.getUser().isBot()) {
          i++;
        }
      }
      if (i == 0) {
        GuildMusicManager musicManager = getGuildAudioPlayer(event.getGuild());
        musicManager.scheduler.queue.clear();
        musicManager.player.destroy();
        event.getGuild().getAudioManager().closeAudioConnection();
      }
    } else if (event.getChannelLeft() == event.getGuild().getSelfMember().getVoiceState().getChannel()) {      List<Member> s = event.getChannelLeft().getMembers();
      int i = 0;
      for (Member m : s) {
        if (!m.getUser().isBot()) {
          i++;
        }
      }
      if (i == 0) {
        GuildMusicManager musicManager = getGuildAudioPlayer(event.getGuild());
        musicManager.scheduler.queue.clear();
        musicManager.player.destroy();
        event.getGuild().getAudioManager().closeAudioConnection();
      }
    }

  }

  @Override
  public void onMessageReceived(MessageReceivedEvent event) {
    String prefix = "sj!";
    String text = event.getMessage().getContentRaw();
    if (event.getMessage().getContentRaw().startsWith(prefix)) {
      if (!(event.getAuthor().isBot())) {
        String[] args = event.getMessage().getContentRaw().substring(prefix.length()).split(" ");
        String command = args[0];
        String all_cont = "";
        try {
          all_cont = event.getMessage().getContentRaw().substring(prefix.length() + command.length() + 1);
        } catch (Exception e) {
        }
        if (command.equals("help")) {
          EmbedBuilder embed = new EmbedBuilder();
          embed.setTitle("Help Command");
          embed.setDescription("`sj!play`: play music\n`sj!skip`: skip song\n`sj!queue`: xem queue\n`sj!stop`: stop music và thoát voice");
          event.getChannel().sendMessageEmbeds(embed.build()).queue();
          logdata(event,command);
        } else if (command.equals("play") || command.equals("p")) {
          AudioChannel botvoice = event.getGuild().getSelfMember().getVoiceState().getChannel();
          AudioChannel uservoice = event.getMember().getVoiceState().getChannel();
          if (botvoice == uservoice && uservoice != null) {
            pls(event, all_cont);
          } else if (botvoice == null && uservoice != null) {
            pls(event, all_cont);
          } else {
            event.getChannel().sendMessage("Bạn chưa join voice or không cùng voice với bot").queue();
          }
          logdata(event,command);
        } else if (command.equals("skip")) {
          AudioChannel botvoice = event.getGuild().getSelfMember().getVoiceState().getChannel();
          AudioChannel uservoice = event.getMember().getVoiceState().getChannel();
          if (botvoice == uservoice && uservoice != null) {
            skipTrack(event.getTextChannel());
          } else {
            event.getChannel().sendMessage("Bạn không cùng voice với bot").queue();
          }
          logdata(event,command);
        } else if (command.equals("queue")) {
          AudioChannel botvoice = event.getGuild().getSelfMember().getVoiceState().getChannel();
          AudioChannel uservoice = event.getMember().getVoiceState().getChannel();
          if (botvoice == uservoice && uservoice != null) {
            queue(event);
          } else {
            event.getChannel().sendMessage("Bạn không cùng voice với bot").queue();
          }
          logdata(event,command);
        } else if (command.equals("stop")) {
          AudioChannel botvoice = event.getGuild().getSelfMember().getVoiceState().getChannel();
          AudioChannel uservoice = event.getMember().getVoiceState().getChannel();
          if (botvoice == uservoice && uservoice != null) {
            stopsong(event);
            event.getChannel().sendMessage("Bot đã dừng nhạc và thoát").queue();
          }
          logdata(event,command);
        }
      }
    } else if (text.equals("<@954641727833116712>") || text.equals("<@!954641727833116712>")) {
      event.getChannel().sendMessage("**Prefix của tôi là :** `sj!`\nNhập `sj!help` để xem Commands").queue();
    }
  }

  private void pls(MessageReceivedEvent event, String all_cont) {
    if (!all_cont.equals("")) {
      if (!(all_cont.startsWith("https://") || all_cont.startsWith("http://"))) {
        all_cont = "ytsearch:" + all_cont;
        loadAndPlay(event, event.getTextChannel(), all_cont, false, 0);
      } else {
        URL url = null;
        try {
          url = loadurl(all_cont);
        } catch (Exception e) {
          event.getChannel().sendMessage("Lỗi khi load url").queue();
        }
        String a = url.getHost();
        if (a.contains("spotify.com")) {
          if (url.getPath().contains("track")) {

            String id = null;
            try {
              id = getids(url);
            } catch (Exception e) {
              throw new RuntimeException(e);
            }
            try {
              all_cont = loadTrack(id);
            } catch (Exception e) {
              event.getChannel().sendMessage(e.toString()).queue();
            }
            loadAndPlay(event, event.getTextChannel(), all_cont, false, 0);
          } else if (url.getPath().contains("playlist")) {
            String id = null;
            try {
              id = getids(url);
            } catch (Exception e) {
              throw new RuntimeException(e);
            }
            try {
              loadPLaylist(id, event);
            } catch (Exception e) {
              event.getChannel().sendMessage("Lỗi Link không hợp lệ").queue();
            }
          }
        } else {
          loadAndPlay(event, event.getTextChannel(), all_cont, false, 0);
        }
      }
    } else {
      EmbedBuilder embed = new EmbedBuilder();
      embed.setTitle("Play command");
      embed.setDescription("**Link được hỗ trợ**\n- Spotify\n- YouTube\n- SoundCloud\n- Bandcamp\n- Vimeo\n- Twitch streams\n- HTTP Audio URLs\n**Hưỡng dẫn**\n`sj!play <thứ cần tìm/Link được hỗ trợ>`");
      event.getChannel().sendMessageEmbeds(embed.build()).queue();
    }
  }

  public String getids(URL url) throws Exception {
    String[] split = url.getPath().split("/");
    String id = split[split.length - 1];
    return id;
  }
  public URL loadurl(String url) throws MalformedURLException {
    URL urls = new URL(url);
    return urls;
  }

  public String loadTrack(String id) throws IOException, ParseException, SpotifyWebApiException {
    clientCredentials_Sync();
    GetTrackRequest track = spotifyApi.getTrack(id).build();
    String name = track.execute().getName();
    String artist = track.execute().getArtists()[0].getName();
    return "ytsearch:" + name + " - " + artist;
  }

  public void loadPLaylist(String id, MessageReceivedEvent event) throws IOException, ParseException, SpotifyWebApiException {
    clientCredentials_Sync();
    GetPlaylistsItemsRequest playlist = spotifyApi.getPlaylistsItems(id).build();
    PlaylistTrack[] s = playlist.execute().getItems();
    int tun = 0;
    for (PlaylistTrack track : s) {
      String artist = ((Track) track.getTrack()).getArtists()[0].getName();
      String name = ((Track) track.getTrack()).getName();
      String all_cont = "ytsearch:" + name + " - " + artist;
      Boolean isplay = true;
      if (tun == 0) {
        isplay = false;
      }
      loadAndPlay(event, event.getTextChannel(), all_cont, isplay, s.length);
      tun++;
    }
  }
  private void loadAndPlay(MessageReceivedEvent event, final TextChannel channel, final String trackUrl, boolean isplay, int length) {
    GuildMusicManager musicManager = getGuildAudioPlayer(channel.getGuild());

    playerManager.loadItemOrdered(musicManager, trackUrl, new AudioLoadResultHandler() {
      @Override
      public void trackLoaded(AudioTrack track) {
        play(event, channel, musicManager, track, false, 0);
      }

      @Override
      public void playlistLoaded(AudioPlaylist playlist) {
        AudioTrack firstTrack = playlist.getSelectedTrack();

        if (playlist.isSearchResult()) {
          firstTrack = playlist.getTracks().get(0);
          play(event, channel, musicManager, firstTrack, isplay, length);
        } else {
          playlist(event, channel, musicManager, playlist);
        }
      }

      @Override
      public void noMatches() {
        channel.sendMessage("Lỗi Link không hợp lệ").queue();
      }

      @Override
      public void loadFailed(FriendlyException exception) {
        channel.sendMessage("Lỗi Link không hợp lệ").queue();
      }
    });
  }

  private void play(MessageReceivedEvent event, TextChannel channel, GuildMusicManager musicManager, AudioTrack track, boolean isplay, int length) {
    EmbedBuilder embed = new EmbedBuilder();
    String info = "";
    if (length != 0) {
      info = " **(Đã add " + length + " bài vào queue)**";
    }
    if (!isplay) {
      embed.setDescription("Adding to queue [" + track.getInfo().title + "](" + track.getInfo().uri + ")" + info);
      channel.sendMessageEmbeds(embed.build()).queue();
      event.getGuild().getAudioManager().openAudioConnection(event.getMember().getVoiceState().getChannel());
      if (event.getMember().getVoiceState().getChannel().getType() == ChannelType.STAGE) {
        event.getGuild().requestToSpeak();
      }
    }
    musicManager.scheduler.queue(track);
  }

  private void playlist(MessageReceivedEvent event, TextChannel channel, GuildMusicManager musicManager, AudioPlaylist playlist) {
    AudioTrack firstTrack = playlist.getTracks().get(0);
    EmbedBuilder embed = new EmbedBuilder();
    embed.setDescription("**Adding to queue: [" + firstTrack.getInfo().title + "]("+ firstTrack.getInfo().uri + ") (Đã add " + playlist.getTracks().size() + " bài vào queue)**");
    channel.sendMessageEmbeds(embed.build()).queue();
    event.getGuild().getAudioManager().openAudioConnection(event.getMember().getVoiceState().getChannel());
    if (event.getMember().getVoiceState().getChannel().getType() == ChannelType.STAGE) {
      event.getGuild().requestToSpeak();
    }
    for (AudioTrack track : playlist.getTracks()) {
      musicManager.scheduler.queue(track);
    }
  }

  private void stopsong(MessageReceivedEvent event) {
    GuildMusicManager musicManager = getGuildAudioPlayer(event.getGuild());
    musicManager.player.destroy();
    musicManager.scheduler.queue.clear();
    event.getGuild().getAudioManager().closeAudioConnection();
  }

  private void queue(MessageReceivedEvent event) {
    GuildMusicManager musicManager = getGuildAudioPlayer(event.getGuild());
    AudioTrack a = musicManager.player.getPlayingTrack();
    EmbedBuilder embed = new EmbedBuilder();
    if (a == null) {
      embed.setTitle("Đang không play gì cả");
    } else {
      embed.setTitle("Queue List");
      List<String> list = new ArrayList<>();
      int i = 1;
      for (AudioTrack s : musicManager.scheduler.queue) {
        list.add("**" + i + ": ["+s.getInfo().title+"]("+s.getInfo().uri+")**");
        if (i == 5) {
          break;
        }
        i++;
      }
      String cl = "";
      if ((musicManager.scheduler.queue.size() - 5) > 0) {
        cl = "\n**Còn " + (musicManager.scheduler.queue.size() - 5) + " bài nữa ...**";
      }
      embed.setDescription("**Playing now: [" + a.getInfo().title + "](" + a.getInfo().uri + ")**" + "\n" + String.join("\n", list) + cl);
    }
    event.getChannel().sendMessageEmbeds(embed.build()).queue();
  }

  private void skipTrack(TextChannel channel) {
    GuildMusicManager musicManager = getGuildAudioPlayer(channel.getGuild());
    if (!(musicManager.scheduler.queue.size() == 0)) {
      AudioTrack a = (AudioTrack) musicManager.scheduler.queue.toArray()[0];
      AudioTrack p = musicManager.player.getPlayingTrack();
      musicManager.scheduler.nextTrack();
      EmbedBuilder embed = new EmbedBuilder();
      embed.setTitle("Skip Song");
      embed.addField("Đã bỏ qua bài:", "[" + p.getInfo().title + "](" + p.getInfo().uri + ")", false);
      embed.addField("Đang phát:", "[" + a.getInfo().title + "](" + a.getInfo().uri + ")", false);
      channel.sendMessageEmbeds(embed.build()).queue();
    } else {
      musicManager.player.destroy();
      channel.sendMessage("Hết queue rồi anh bạn à :<").queue();
    }
  }
}
