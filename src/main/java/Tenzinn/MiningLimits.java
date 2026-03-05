package Tenzinn;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;

import java.awt.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MiningLimits {

    public static class PlayerConfig {
        public int totalMinerals;
        public int timeToReset;
        public int idleResetSeconds;
        public Map<String, Integer> minerals;

        PlayerConfig(int totalMinerals, int timeToReset, int idleResetSeconds, Map<String, Integer> minerals) {
            this.totalMinerals    = totalMinerals;
            this.timeToReset      = timeToReset;
            this.idleResetSeconds = idleResetSeconds;
            this.minerals         = minerals;
        }
    }

    public static class PlayerData {
        public final String uuid;
        public int totalCollected;
        public final Map<String, Integer> byType = new HashMap<>();
        public long lastCollectionTime;
        public long quotaFullTime;

        PlayerData(String uuid) {
            this.uuid              = uuid;
            this.totalCollected    = 0;
            this.lastCollectionTime = System.currentTimeMillis();
            this.quotaFullTime     = -1;
        }

        public void reset(int value) {
            try {
                PlayerRef playerRef = Universe.get().getPlayer(UUID.fromString(this.uuid));
                if (playerRef != null) {
                    if      (value == 1) playerRef.sendMessage(Message.raw("Por tiempo: Se resetean los minerales").color(Color.cyan));
                    else if (value == 2) playerRef.sendMessage(Message.raw("Por inactividad: Se resetean los minerales").color(Color.cyan));
                }
            } catch (Exception ignored) {}

            totalCollected      = 0;
            byType.clear();
            quotaFullTime       = -1;

            lastCollectionTime  = System.currentTimeMillis();
        }
    }

    private static PlayerConfig globalConfig = defaultConfig();
    private static final Map<String, PlayerConfig> playerOverrides = new ConcurrentHashMap<>();
    private static final Map<String, PlayerData> playerData = new ConcurrentHashMap<>();
    private static ScheduledExecutorService resetScheduler;

    public static void load() {
        loadGlobalConfig();
        loadPlayerOverrides();
        startResetTicker();
        System.out.println("[MiningLimits] Sistema de cuotas iniciado.");
    }

    private static PlayerConfig defaultConfig() {
        Map<String, Integer> minerals = new LinkedHashMap<>();
        minerals.put("copper",     -1);
        minerals.put("iron",       -1);
        minerals.put("silver",     -1);
        minerals.put("gold",       -1);
        minerals.put("thorium",    -1);
        minerals.put("cobalt",     -1);
        minerals.put("adamantite", -1);
        minerals.put("mithril",    -1);
        minerals.put("onyxium",    -1);
        return new PlayerConfig(32, 300, 600, minerals);
    }

    private static void loadGlobalConfig() {
        try {
            Path path = Paths.get(getMiningConfigPath());
            if (!Files.exists(path)) {
                System.err.println("[MiningLimits] No se encontró mining.json, usando defaults.");
                return;
            }

            String content = new String(Files.readAllBytes(path));
            JsonObject root = new Gson().fromJson(content, JsonObject.class);

            int total       = root.has("totalMinerals")    ? root.get("totalMinerals").getAsInt()    : 32;
            int resetTime   = root.has("timeToReset")      ? root.get("timeToReset").getAsInt()      : 300;
            int idleReset   = root.has("idleResetSeconds") ? root.get("idleResetSeconds").getAsInt() : 600;

            Map<String, Integer> minerals = new LinkedHashMap<>();
            minerals.putAll(defaultConfig().minerals);

            // Sobrescribir con lo que haya en el JSON
            if (root.has("minerals")) {
                JsonObject min = root.getAsJsonObject("minerals");
                for (Map.Entry<String, JsonElement> entry : min.entrySet()) {
                    minerals.put(entry.getKey().toLowerCase(), entry.getValue().getAsInt());
                }
            }

            globalConfig = new PlayerConfig(total, resetTime, idleReset, minerals);
            System.out.println("[MiningLimits] mining.json cargado: total=" + total + ", reset=" + resetTime + "s");

        } catch (Exception e) {
            System.err.println("[MiningLimits] Error al cargar mining.json: " + e.getMessage());
        }
    }

    private static void loadPlayerOverrides() {
        try {
            Path path = Paths.get(getPlayersPath());
            if (!Files.exists(path)) return; // sin overrides es normal

            String content = new String(Files.readAllBytes(path));
            JsonObject root = new Gson().fromJson(content, JsonObject.class);
            if (!root.has("players")) return;

            JsonObject players = root.getAsJsonObject("players");
            for (Map.Entry<String, JsonElement> entry : players.entrySet()) {
                String     uuid    = entry.getKey();
                JsonObject cfg     = entry.getValue().getAsJsonObject();

                int total     = cfg.has("totalMinerals")    ? cfg.get("totalMinerals").getAsInt()    : globalConfig.totalMinerals;
                int resetTime = cfg.has("timeToReset")      ? cfg.get("timeToReset").getAsInt()      : globalConfig.timeToReset;
                int idleReset = cfg.has("idleResetSeconds") ? cfg.get("idleResetSeconds").getAsInt() : globalConfig.idleResetSeconds;

                Map<String, Integer> minerals = new LinkedHashMap<>(globalConfig.minerals);
                if (cfg.has("minerals")) {
                    JsonObject min = cfg.getAsJsonObject("minerals");
                    for (Map.Entry<String, JsonElement> m : min.entrySet()) { minerals.put(m.getKey().toLowerCase(), m.getValue().getAsInt()); }
                }

                playerOverrides.put(uuid, new PlayerConfig(total, resetTime, idleReset, minerals));
            }

            System.out.println("[MiningLimits] players.json cargado: " + playerOverrides.size() + " overrides.");

        } catch (Exception e) {
            System.err.println("[MiningLimits] Error al cargar players.json: " + e.getMessage());
        }
    }

    private static void startResetTicker() {
        if (resetScheduler != null && !resetScheduler.isShutdown()) resetScheduler.shutdownNow();
        resetScheduler = Executors.newSingleThreadScheduledExecutor();
        resetScheduler.scheduleWithFixedDelay(() -> {
            long now = System.currentTimeMillis();
            for (Map.Entry<String, PlayerData> entry : playerData.entrySet()) {
                PlayerData  data = entry.getValue();
                PlayerConfig cfg = getEffectiveConfig(entry.getKey());

                // Reset por cupo lleno
                if (data.quotaFullTime > 0) {
                    long secondsSinceFull = (now - data.quotaFullTime) / 1000;
                    if (secondsSinceFull >= cfg.timeToReset) {
                        data.reset(1);
                        continue;
                    }
                }

                // Reset por inactividad
                long secondsIdle = (now - data.lastCollectionTime) / 1000;
                if (secondsIdle >= cfg.idleResetSeconds) {
                    data.reset(2);
                }
            }
        }, 1, 1, TimeUnit.SECONDS);
    }

    public enum CollectResult { ALLOWED, BLOCKED_TOTAL, BLOCKED_TYPE }

    public static CollectResult tryCollect(String playerUuid, String blockId) {
        PlayerData   data = playerData.computeIfAbsent(playerUuid, PlayerData::new);
        PlayerConfig cfg  = getEffectiveConfig(playerUuid);

        String mineralType = resolveMineralType(blockId, cfg);

        // 1. Verificar límite por tipo
        if (mineralType != null) {
            int typeLimit = cfg.minerals.getOrDefault(mineralType, -1);
            if (typeLimit > 0) {
                int typeCount = data.byType.getOrDefault(mineralType, 0);
                if (typeCount >= typeLimit) return CollectResult.BLOCKED_TYPE;
            }
        }

        // 2. Verificar límite total
        if (data.totalCollected >= cfg.totalMinerals) return CollectResult.BLOCKED_TOTAL;

        // 3. Registrar
        data.totalCollected++;
        data.lastCollectionTime = System.currentTimeMillis();
        if (mineralType != null) {
            data.byType.merge(mineralType, 1, Integer::sum);
        }

        // Marcar si se alcanzó el cupo total
        if (data.totalCollected >= cfg.totalMinerals && data.quotaFullTime < 0) {
            data.quotaFullTime = System.currentTimeMillis();
        }

        return CollectResult.ALLOWED;
    }

    public static PlayerData getPlayerData(String playerUuid) {
        return playerData.get(playerUuid);
    }

    public static PlayerConfig getEffectiveConfig(String playerUuid) { return playerOverrides.getOrDefault(playerUuid, globalConfig); }

    public static void savePlayerOverride(String playerUuid, PlayerConfig config) {
        playerOverrides.put(playerUuid, config);
        persistPlayersJson();
    }

    public static void removePlayerOverride(String playerUuid) {
        playerOverrides.remove(playerUuid);
        persistPlayersJson();
    }

    public static int getSecondsUntilReset(String playerUuid) {
        PlayerData   data = playerData.get(playerUuid);
        PlayerConfig cfg  = getEffectiveConfig(playerUuid);
        if (data == null) return 0;

        long now = System.currentTimeMillis();

        if (data.quotaFullTime > 0) {
            long elapsed = (now - data.quotaFullTime) / 1000;
            return (int) Math.max(0, cfg.timeToReset - elapsed);
        }

        return 0;
    }

    private static String resolveMineralType(String blockId, PlayerConfig cfg) {
        if (blockId == null) return null;
        String lower = blockId.toLowerCase();
        for (String key : cfg.minerals.keySet()) {
            if (lower.contains(key)) return key;
        }
        return null;
    }

    private static void persistPlayersJson() {
        try {
            JsonObject root    = new JsonObject();
            JsonObject players = new JsonObject();

            for (Map.Entry<String, PlayerConfig> entry : playerOverrides.entrySet()) {
                PlayerConfig cfg  = entry.getValue();
                JsonObject   pcfg = new JsonObject();
                pcfg.addProperty("totalMinerals",    cfg.totalMinerals);
                pcfg.addProperty("timeToReset",      cfg.timeToReset);
                pcfg.addProperty("idleResetSeconds", cfg.idleResetSeconds);

                JsonObject minerals = new JsonObject();
                for (Map.Entry<String, Integer> m : cfg.minerals.entrySet()) {
                    minerals.addProperty(m.getKey(), m.getValue());
                }
                pcfg.add("minerals", minerals);
                players.add(entry.getKey(), pcfg);
            }

            root.add("players", players);

            String json = new GsonBuilder().setPrettyPrinting().create().toJson(root);
            Files.write(Paths.get(getPlayersPath()), json.getBytes());

        } catch (Exception e) {
            System.err.println("[MiningLimits] Error al persistir players.json: " + e.getMessage());
        }
    }

    private static String getMiningConfigPath() {
        return getFolder() + File.separator + "mining.json";
    }

    private static String getPlayersPath() {
        return getFolder() + File.separator + "players.json";
    }

    private static String getFolder() {
        try {
            File jar = new File(MiningLimits.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            return new File(jar.getParentFile(), "WeakPoint").getAbsolutePath();
        } catch (Exception e) {
            return "WeakPoint";
        }
    }
}