package pk.ajneb97.database;
import com.tcoded.folialib.impl.PlatformScheduler;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import pk.ajneb97.PlayerKits2;
import pk.ajneb97.managers.MessagesManager;
import pk.ajneb97.model.PlayerData;
import pk.ajneb97.model.PlayerDataKit;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class MySQLConnection {

    private final PlayerKits2 plugin;
    private final PlatformScheduler scheduler;
    private HikariConnection connection;
    private String database;

    public MySQLConnection(PlayerKits2 plugin){
        this.plugin = plugin;
        this.scheduler = plugin.getFoliaLib().getScheduler();
    }

    public void setupMySql(){
        FileConfiguration config = plugin.getConfigsManager().getMainConfigManager().getConfig();
        try {
            String host = config.getString("mysql_database.host");
            int port = Integer.parseInt(Objects.requireNonNull(config.getString("mysql_database.port")));
            database = config.getString("mysql_database.database");
            String username = config.getString("mysql_database.username");
            String password = config.getString("mysql_database.password");
            connection = new HikariConnection(host, port, database, username, password);
            connection.getHikari().getConnection();
            createTables();
            loadData();
            Bukkit.getConsoleSender().sendMessage(MessagesManager.getColoredMessage(PlayerKits2.prefix + " &aSuccessfully connected to the Database."));
        } catch(Exception e) {
            Bukkit.getConsoleSender().sendMessage(MessagesManager.getColoredMessage(PlayerKits2.prefix + " &cError while connecting to the Database."));
            e.fillInStackTrace();
        }
    }

    public String getDatabase() {
        return this.database;
    }

    public Connection getConnection() {
        try {
            return connection.getHikari().getConnection();
        } catch (Exception e) {
            e.fillInStackTrace();
            return null;
        }
    }

    public void loadData(){
        Map<UUID, PlayerData> playerMap = new HashMap<>();
        try(Connection connection = getConnection()){
            PreparedStatement statement = connection.prepareStatement(
                    "SELECT playerkits_players.UUID, playerkits_players.PLAYER_NAME, " +
                            "playerkits_players_kits.NAME, " +
                            "playerkits_players_kits.COOLDOWN, " +
                            "playerkits_players_kits.ONE_TIME, " +
                            "playerkits_players_kits.BOUGHT " +
                            "FROM playerkits_players LEFT JOIN playerkits_players_kits " +
                            "ON playerkits_players.UUID = playerkits_players_kits.UUID");

            ResultSet result = statement.executeQuery();

            while(result.next()){
                UUID uuid = UUID.fromString(result.getString("UUID"));
                String playerName = result.getString("PLAYER_NAME");
                String kitName = result.getString("NAME");
                long cooldown = result.getLong("COOLDOWN");
                boolean oneTime = result.getBoolean("ONE_TIME");
                boolean bought = result.getBoolean("BOUGHT");

                PlayerData player = playerMap.computeIfAbsent(uuid, u -> new PlayerData(u, playerName));

                if(kitName != null){
                    PlayerDataKit playerDataKit = new PlayerDataKit(kitName);
                    playerDataKit.setCooldown(cooldown);
                    playerDataKit.setOneTime(oneTime);
                    playerDataKit.setBought(bought);
                    player.addKit(playerDataKit);
                }
            }
        } catch (SQLException e) {
            e.fillInStackTrace();
        }

        plugin.getPlayerDataManager().setPlayers(playerMap);
    }

    public void createTables() {
        try(Connection connection = getConnection()){
            PreparedStatement statement1 = connection.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS playerkits_players" +
                            " (UUID varchar(200) NOT NULL, " +
                            " PLAYER_NAME varchar(50), " +
                            " PRIMARY KEY (UUID))");
            statement1.executeUpdate();

            PreparedStatement statement2 = connection.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS playerkits_players_kits" +
                            " (ID int NOT NULL AUTO_INCREMENT, " +
                            " UUID varchar(200) NOT NULL, " +
                            " NAME varchar(100), " +
                            " COOLDOWN BIGINT, " +
                            " ONE_TIME BOOLEAN, " +
                            " BOUGHT BOOLEAN, " +
                            " PRIMARY KEY (ID), " +
                            " FOREIGN KEY (UUID) REFERENCES playerkits_players(UUID))");
            statement2.executeUpdate();
        } catch (SQLException e) {
            e.fillInStackTrace();
        }
    }

    public void getPlayer(String uuid, PlayerCallback callback){
        scheduler.runAsync(task -> {
            PlayerData player = null;
            try(Connection connection = getConnection()){
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT playerkits_players.UUID, playerkits_players.PLAYER_NAME, " +
                                "playerkits_players_kits.NAME, " +
                                "playerkits_players_kits.COOLDOWN, " +
                                "playerkits_players_kits.ONE_TIME, " +
                                "playerkits_players_kits.BOUGHT " +
                                "FROM playerkits_players LEFT JOIN playerkits_players_kits " +
                                "ON playerkits_players.UUID = playerkits_players_kits.UUID " +
                                "WHERE playerkits_players.UUID = ?");

                statement.setString(1, uuid);
                ResultSet result = statement.executeQuery();

                while(result.next()){
                    UUID uuidParsed = UUID.fromString(result.getString("UUID"));
                    String playerName = result.getString("PLAYER_NAME");
                    String kitName = result.getString("NAME");
                    long cooldown = result.getLong("COOLDOWN");
                    boolean oneTime = result.getBoolean("ONE_TIME");
                    boolean bought = result.getBoolean("BOUGHT");
                    if(player == null){
                        player = new PlayerData(uuidParsed, playerName);
                    }
                    if(kitName != null){
                        PlayerDataKit playerDataKit = new PlayerDataKit(kitName);
                        playerDataKit.setCooldown(cooldown);
                        playerDataKit.setOneTime(oneTime);
                        playerDataKit.setBought(bought);
                        player.addKit(playerDataKit);
                    }
                }
            } catch (SQLException e) {
                e.fillInStackTrace();
            }

            PlayerData finalPlayer = player;
            assert finalPlayer != null;
            scheduler.runAtEntity(Bukkit.getEntity(finalPlayer.getUuid()), task1 -> callback.onDone(finalPlayer));
        });
    }

    public void createPlayer(PlayerData player, SimpleCallback callback){
        scheduler.runAsync(task -> {
            try(Connection connection = getConnection()){
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO playerkits_players (UUID, PLAYER_NAME) VALUE (?,?)");
                statement.setString(1, player.getUuid().toString());
                statement.setString(2, player.getName());
                statement.executeUpdate();
                scheduler.runAtEntity(Bukkit.getEntity(player.getUuid()), task1 -> callback.onDone());
            } catch (SQLException e) {
                e.fillInStackTrace();
            }
        });
    }

    public void updatePlayerName(PlayerData player){
        scheduler.runAsync(task -> {
            try(Connection connection = getConnection()){
                PreparedStatement statement = connection.prepareStatement(
                        "UPDATE playerkits_players SET PLAYER_NAME=? WHERE UUID=?");
                statement.setString(1, player.getName());
                statement.setString(2, player.getUuid().toString());
                statement.executeUpdate();
            } catch (SQLException e) {
                e.fillInStackTrace();
            }
        });
    }

    public void updateKit(PlayerData player, PlayerDataKit kit, boolean mustCreate){
        scheduler.runAsync(task -> {
            try(Connection connection = getConnection()){
                PreparedStatement statement;
                if(mustCreate){
                    statement = connection.prepareStatement(
                            "INSERT INTO playerkits_players_kits (UUID, NAME, COOLDOWN, ONE_TIME, BOUGHT) VALUE (?,?,?,?,?)");
                    statement.setString(1, player.getUuid().toString());
                    statement.setString(2, kit.getName());
                    statement.setLong(3, kit.getCooldown());
                    statement.setBoolean(4, kit.isOneTime());
                    statement.setBoolean(5, kit.isBought());
                } else {
                    statement = connection.prepareStatement(
                            "UPDATE playerkits_players_kits SET COOLDOWN=?, ONE_TIME=?, BOUGHT=? WHERE UUID=? AND NAME=?");
                    statement.setLong(1, kit.getCooldown());
                    statement.setBoolean(2, kit.isOneTime());
                    statement.setBoolean(3, kit.isBought());
                    statement.setString(4, player.getUuid().toString());
                    statement.setString(5, kit.getName());
                }
                statement.executeUpdate();
            } catch (SQLException e) {
                e.fillInStackTrace();
            }
        });
    }

    public void resetKit(String uuid, String kitName, boolean all){
        scheduler.runAsync(task -> {
            try(Connection connection = getConnection()){
                PreparedStatement statement;
                if(all){
                    statement = connection.prepareStatement(
                            "DELETE FROM playerkits_players_kits WHERE NAME=?");
                    statement.setString(1, kitName);
                } else {
                    statement = connection.prepareStatement(
                            "DELETE FROM playerkits_players_kits WHERE UUID=? AND NAME=?");
                    statement.setString(1, uuid);
                    statement.setString(2, kitName);
                }
                statement.executeUpdate();
            } catch (SQLException e) {
                e.fillInStackTrace();
            }
        });
    }
}
