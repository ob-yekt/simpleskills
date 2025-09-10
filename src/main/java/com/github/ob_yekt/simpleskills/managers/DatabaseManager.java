package com.github.ob_yekt.simpleskills.managers;

import com.github.ob_yekt.simpleskills.Simpleskills;
import com.github.ob_yekt.simpleskills.Skills;
import net.minecraft.server.MinecraftServer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

/**
 * Manages the SQLite database for storing player skill data and settings.
 * Added leaderboard queries for skills and total levels.
 */
public class DatabaseManager {
    private static final String DATABASE_NAME = "simpleskills.db";
    private static final List<String> SKILLS = Arrays.stream(Skills.values())
            .map(Skills::getId)
            .toList();
    private static DatabaseManager instance;
    private Connection connection;
    private Path currentDatabasePath;
    private static final Map<String, Map<String, SkillData>> skillCache = new HashMap<>();

    // Custom exception for database errors
    public static class DatabaseException extends RuntimeException {
        public DatabaseException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public record SkillData(int xp, int level) {}

    public record LeaderboardEntry(String playerName, int level, int xp) {}

    private DatabaseManager() {
        // Private constructor for singleton pattern
    }

    public static DatabaseManager getInstance() {
        if (instance == null) {
            instance = new DatabaseManager();
        }
        return instance;
    }

    public void initializeDatabase(MinecraftServer server) {
        Path worldDirectory = server.getSavePath(net.minecraft.util.WorldSavePath.ROOT).resolve("data");
        Path newDatabasePath = worldDirectory.resolve(DATABASE_NAME);

        if (currentDatabasePath != null && currentDatabasePath.equals(newDatabasePath) && isConnectionValid()) {
            return;
        }

        closeConnection();

        try {
            Files.createDirectories(worldDirectory);
            connection = DriverManager.getConnection("jdbc:sqlite:" + newDatabasePath);
            connection.setAutoCommit(true);
            currentDatabasePath = newDatabasePath;
            createTables();
            Simpleskills.LOGGER.info("Connected to SQLite database at: {}", newDatabasePath);
        } catch (SQLException | java.io.IOException e) {
            Simpleskills.LOGGER.error("Failed to initialize database at {}.", newDatabasePath, e);
            throw new DatabaseException("Database initialization failed", e);
        }
    }

    private boolean isConnectionValid() {
        try {
            return connection != null && !connection.isClosed() && connection.isValid(5);
        } catch (SQLException e) {
            Simpleskills.LOGGER.error("Error checking database connection validity: {}", e.getMessage());
            return false;
        }
    }

    private void createTables() {
        String createPlayersTable = """
        CREATE TABLE IF NOT EXISTS players (
            player_uuid TEXT PRIMARY KEY,
            is_ironman INTEGER DEFAULT 0,
            is_tab_menu_visible INTEGER DEFAULT 1
        )
    """;
        String createSkillsTable = """
        CREATE TABLE IF NOT EXISTS player_skills (
            player_uuid TEXT,
            skill_id TEXT,
            xp INTEGER DEFAULT 0,
            level INTEGER DEFAULT 1,
            PRIMARY KEY (player_uuid, skill_id)
        )
    """;
        String createPlayerNamesTable = """
        CREATE TABLE IF NOT EXISTS player_names (
            uuid TEXT,
            name TEXT,
            last_seen INTEGER,
            PRIMARY KEY (uuid, last_seen)
        )
    """;

        try (PreparedStatement playersStmt = connection.prepareStatement(createPlayersTable);
             PreparedStatement skillsStmt = connection.prepareStatement(createSkillsTable);
             PreparedStatement playerNamesStmt = connection.prepareStatement(createPlayerNamesTable)) {
            playersStmt.execute();
            skillsStmt.execute();
            playerNamesStmt.execute();
            Simpleskills.LOGGER.debug("Created database tables if they didn't exist.");
        } catch (SQLException e) {
            Simpleskills.LOGGER.error("Failed to create database tables: {}", e.getMessage());
            throw new DatabaseException("Failed to create tables", e);
        }
    }

    public void initializePlayer(String playerUuid) {
        checkConnection();
        String insertPlayerSql = "INSERT OR IGNORE INTO players (player_uuid, is_ironman, is_tab_menu_visible) VALUES (?, 0, 1)";
        String insertSkillSql = "INSERT OR IGNORE INTO player_skills (player_uuid, skill_id, xp, level) VALUES (?, ?, 0, 1)";

        try {
            connection.setAutoCommit(false);

            try (PreparedStatement playerStmt = connection.prepareStatement(insertPlayerSql)) {
                playerStmt.setString(1, playerUuid);
                playerStmt.executeUpdate();
            }

            try (PreparedStatement skillStmt = connection.prepareStatement(insertSkillSql)) {
                for (String skill : SKILLS) {
                    skillStmt.setString(1, playerUuid);
                    skillStmt.setString(2, skill);
                    skillStmt.executeUpdate();
                }
            }

            connection.commit();
            skillCache.remove(playerUuid);
            Simpleskills.LOGGER.debug("Initialized player data for UUID: {}", playerUuid);
        } catch (SQLException e) {
            try {
                connection.rollback();
            } catch (SQLException rollbackEx) {
                Simpleskills.LOGGER.error("Failed to rollback transaction: {}", rollbackEx.getMessage());
            }
            Simpleskills.LOGGER.error("Failed to initialize player UUID {}: {}", playerUuid, e.getMessage());
            throw new DatabaseException("Failed to initialize player", e);
        } finally {
            try {
                connection.setAutoCommit(true);
            } catch (SQLException e) {
                Simpleskills.LOGGER.error("Failed to restore auto-commit: {}", e.getMessage());
            }
        }
    }

    public void ensurePlayerInitialized(String playerUuid) {
        checkConnection();
        String sql = "SELECT COUNT(*) FROM players WHERE player_uuid = ?";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerUuid);
            try (ResultSet result = statement.executeQuery()) {
                if (result.next() && result.getInt(1) == 0) {
                    initializePlayer(playerUuid);
                }
            }
        } catch (SQLException e) {
            Simpleskills.LOGGER.error("Failed to check player initialization for UUID {}: {}", playerUuid, e.getMessage());
            throw new DatabaseException("Failed to check player initialization", e);
        }
    }

    public void updatePlayerName(String playerUuid, String playerName) {
        checkConnection();
        String sql = "INSERT OR REPLACE INTO player_names (uuid, name, last_seen) VALUES (?, ?, ?)";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerUuid);
            statement.setString(2, playerName);
            statement.setLong(3, System.currentTimeMillis() / 1000); // Unix timestamp in seconds
            statement.executeUpdate();
            Simpleskills.LOGGER.debug("Updated player name for UUID {}: {}", playerUuid, playerName);
        } catch (SQLException e) {
            Simpleskills.LOGGER.error("Failed to update player name for UUID {}: {}", playerUuid, e.getMessage());
            throw new DatabaseException("Failed to update player name", e);
        }
    }

    public Map<String, SkillData> getAllSkills(String playerUuid) {
        checkConnection();
        if (skillCache.containsKey(playerUuid)) {
            return Collections.unmodifiableMap(skillCache.get(playerUuid));
        }

        Map<String, SkillData> skills = new HashMap<>();
        String sql = "SELECT skill_id, xp, level FROM player_skills WHERE player_uuid = ?";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerUuid);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    String skillId = result.getString("skill_id");
                    int xp = result.getInt("xp");
                    int level = result.getInt("level");
                    skills.put(skillId, new SkillData(xp, level));
                }
            }
            skillCache.put(playerUuid, skills);
            return Collections.unmodifiableMap(skills);
        } catch (SQLException e) {
            Simpleskills.LOGGER.error("Failed to retrieve skills for UUID {}: {}", playerUuid, e.getMessage());
            throw new DatabaseException("Failed to retrieve skills", e);
        }
    }

    public void savePlayerSkill(String playerUuid, String skillId, int xp, int level) {
        checkConnection();
        String sql = "INSERT OR REPLACE INTO player_skills (player_uuid, skill_id, xp, level) VALUES (?, ?, ?, ?)";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerUuid);
            statement.setString(2, skillId);
            statement.setInt(3, xp);
            statement.setInt(4, level);
            statement.executeUpdate();
            skillCache.remove(playerUuid);
            Simpleskills.LOGGER.debug("Saved skill {} for UUID {}: {} XP, level {}", skillId, playerUuid, xp, level);
        } catch (SQLException e) {
            Simpleskills.LOGGER.error("Failed to save skill {} for UUID {}: {}", skillId, playerUuid, e.getMessage());
            throw new DatabaseException("Failed to save skill data", e);
        }
    }

    public void resetPlayerSkills(String playerUuid) {
        checkConnection();
        String sql = "UPDATE player_skills SET xp = 0, level = 1 WHERE player_uuid = ?";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerUuid);
            statement.executeUpdate();
            skillCache.remove(playerUuid);
            Simpleskills.LOGGER.debug("Reset skills for UUID: {}", playerUuid);
        } catch (SQLException e) {
            Simpleskills.LOGGER.error("Failed to reset skills for UUID {}: {}", playerUuid, e.getMessage());
            throw new DatabaseException("Failed to reset skills", e);
        }
    }

    public void setIronmanMode(String playerUuid, boolean isIronman) {
        checkConnection();
        String sql = "UPDATE players SET is_ironman = ? WHERE player_uuid = ?";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, isIronman ? 1 : 0);
            statement.setString(2, playerUuid);
            statement.executeUpdate();
            Simpleskills.LOGGER.debug("Set Ironman mode to {} for UUID: {}", isIronman, playerUuid);
        } catch (SQLException e) {
            Simpleskills.LOGGER.error("Failed to set Ironman mode for UUID {}: {}", playerUuid, e.getMessage());
            throw new DatabaseException("Failed to set Ironman mode", e);
        }
    }

    public boolean isPlayerInIronmanMode(String playerUuid) {
        checkConnection();
        String sql = "SELECT is_ironman FROM players WHERE player_uuid = ?";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerUuid);
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    return result.getInt("is_ironman") == 1;
                }
            }
        } catch (SQLException e) {
            Simpleskills.LOGGER.error("Failed to check Ironman mode for UUID {}: {}", playerUuid, e.getMessage());
            throw new DatabaseException("Failed to check Ironman mode", e);
        }
        return false;
    }

    public void setTabMenuVisibility(String playerUuid, boolean isVisible) {
        checkConnection();
        String sql = "UPDATE players SET is_tab_menu_visible = ? WHERE player_uuid = ?";

        try {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, isVisible ? 1 : 0);
                statement.setString(2, playerUuid);
                statement.executeUpdate();
                Simpleskills.LOGGER.debug("Set tab menu visibility to {} for UUID: {}", isVisible, playerUuid);
            }
            connection.commit();
        } catch (SQLException e) {
            try {
                connection.rollback();
            } catch (SQLException rollbackEx) {
                Simpleskills.LOGGER.error("Failed to rollback transaction: {}", rollbackEx.getMessage());
            }
            Simpleskills.LOGGER.error("Failed to set tab menu visibility for UUID {}: {}", playerUuid, e.getMessage());
            throw new DatabaseException("Failed to set tab menu visibility", e);
        } finally {
            try {
                connection.setAutoCommit(true);
            } catch (SQLException e) {
                Simpleskills.LOGGER.error("Failed to restore auto-commit: {}", e.getMessage());
            }
        }
    }

    public boolean isTabMenuVisible(String playerUuid) {
        checkConnection();
        String sql = "SELECT is_tab_menu_visible FROM players WHERE player_uuid = ?";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerUuid);
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    return result.getInt("is_tab_menu_visible") == 1;
                }
            }
        } catch (SQLException e) {
            Simpleskills.LOGGER.error("Failed to check tab menu visibility for UUID {}: {}", playerUuid, e.getMessage());
            throw new DatabaseException("Failed to check tab menu visibility", e);
        }
        return true;
    }

    public int getTotalSkillLevel(String playerUuid) {
        checkConnection();
        String sql = "SELECT SUM(level) as total_level FROM player_skills WHERE player_uuid = ?";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerUuid);
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    return result.getInt("total_level");
                }
            }
        } catch (SQLException e) {
            Simpleskills.LOGGER.error("Failed to get total skill level for UUID {}: {}", playerUuid, e.getMessage());
            throw new DatabaseException("Failed to get total skill level", e);
        }
        return 0;
    }

    public List<LeaderboardEntry> getSkillLeaderboard(String skillId, int limit) {
        checkConnection();
        String sql = """
        SELECT p.player_uuid, ps.level, ps.xp, COALESCE(n.player_name, p.player_uuid) as player_name
        FROM player_skills ps
        JOIN players p ON ps.player_uuid = p.player_uuid
        LEFT JOIN (
            SELECT uuid, name as player_name
            FROM player_names
            WHERE (uuid, last_seen) IN (
                SELECT uuid, MAX(last_seen)
                FROM player_names
                GROUP BY uuid
            )
        ) n ON p.player_uuid = n.uuid
        WHERE ps.skill_id = ?
        ORDER BY ps.level DESC, ps.xp DESC
        LIMIT ?
    """;

        List<LeaderboardEntry> leaderboard = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, skillId);
            statement.setInt(2, limit);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    String playerName = result.getString("player_name");
                    int level = result.getInt("level");
                    int xp = result.getInt("xp");
                    leaderboard.add(new LeaderboardEntry(playerName, level, xp));
                }
            }
        } catch (SQLException e) {
            Simpleskills.LOGGER.error("Failed to retrieve leaderboard for skill {}: {}", skillId, e.getMessage());
            throw new DatabaseException("Failed to retrieve skill leaderboard", e);
        }
        return leaderboard;
    }

    public List<LeaderboardEntry> getTotalLevelLeaderboard(int limit) {
        checkConnection();
        String sql = """
        SELECT p.player_uuid, SUM(ps.level) as total_level, COALESCE(n.player_name, p.player_uuid) as player_name
        FROM player_skills ps
        JOIN players p ON ps.player_uuid = p.player_uuid
        LEFT JOIN (
            SELECT uuid, name as player_name
            FROM player_names
            WHERE (uuid, last_seen) IN (
                SELECT uuid, MAX(last_seen)
                FROM player_names
                GROUP BY uuid
            )
        ) n ON p.player_uuid = n.uuid
        GROUP BY p.player_uuid
        ORDER BY total_level DESC
        LIMIT ?
    """;

        List<LeaderboardEntry> leaderboard = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, limit);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    String playerName = result.getString("player_name");
                    int totalLevel = result.getInt("total_level");
                    leaderboard.add(new LeaderboardEntry(playerName, totalLevel, 0));
                }
            }
        } catch (SQLException e) {
            Simpleskills.LOGGER.error("Failed to retrieve total level leaderboard: {}", e.getMessage());
            throw new DatabaseException("Failed to retrieve total level leaderboard", e);
        }
        return leaderboard;
    }

    private void reconnectIfNeeded() {
        if (!isConnectionValid() && currentDatabasePath != null) {
            Simpleskills.LOGGER.info("Reconnecting to database at {}", currentDatabasePath);
            closeConnection();
            try {
                connection = DriverManager.getConnection("jdbc:sqlite:" + currentDatabasePath);
                connection.setAutoCommit(true);
                createTables();
                Simpleskills.LOGGER.info("Successfully reconnected to the database.");
            } catch (SQLException e) {
                Simpleskills.LOGGER.error("Failed to reconnect to the database: {}", e.getMessage());
                throw new DatabaseException("Database reconnection failed", e);
            }
        }
    }

    private void checkConnection() {
        reconnectIfNeeded();
        if (connection == null) {
            Simpleskills.LOGGER.error("No database connection available.");
            throw new DatabaseException("No database connection available", null);
        }
    }

    private void closeConnection() {
        if (connection != null) {
            try {
                connection.close();
                Simpleskills.LOGGER.debug("Database connection closed.");
            } catch (SQLException e) {
                Simpleskills.LOGGER.error("Failed to close database connection: {}", e.getMessage());
            }
        }
    }

    public void close() {
        closeConnection();
        currentDatabasePath = null;
        skillCache.clear();
    }
}