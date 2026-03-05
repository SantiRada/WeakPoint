package Tenzinn;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonElement;

import java.io.*;
import java.util.*;
import java.nio.file.*;

public class WeakPointConfig {

    private static final Set<String> VALID_BLOCKS = new HashSet<>();

    public static void load() {
        VALID_BLOCKS.clear();

        try {
            String configPath = getConfigPath();
            if(configPath == null) {
                System.err.println("[WeakPoint] No funciona el PATH" + configPath);
                return;
            }
            Path path = Paths.get(configPath);

            if (!Files.exists(path)) {
                System.err.println("[WeakPoint] No se encontró " + configPath);
                return;
            }

            String content = new String(Files.readAllBytes(path));
            Gson gson = new Gson();
            JsonObject root = gson.fromJson(content, JsonObject.class);
            JsonArray items = root.getAsJsonArray("Items");

            for (JsonElement element : items) {
                JsonObject item = element.getAsJsonObject();
                String itemId = item.get("itemId").getAsString();
                VALID_BLOCKS.add(itemId);
            }

            System.out.println("[WeakPoint] Cargados " + VALID_BLOCKS.size() + " bloques desde items.json");

        } catch (Exception e) {
            System.err.println("[WeakPoint] Error al cargar items.json: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static String getConfigPath () {
        try {
            File jar = new File(WeakPointConfig.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            File configFile = new File(jar.getParentFile(), "WeakPoint/items.json");

            System.out.println("[WeakPoint] Buscando config en: " + configFile.getAbsolutePath());
            return configFile.getAbsolutePath();
        } catch (Exception e) {
            System.err.println("[WeakPoint] Error al resolver ruta del JAR: " + e.getMessage());
            return null;
        }
    }

    public static boolean isValidBlock(String blockId) { return VALID_BLOCKS.contains(blockId); }

    // -------------------------------------------------------------------------
    // Valores default de golpes por tipo de pico (usados si el bloque no
    // tiene la sección "pickaxes" en el JSON).
    // -------------------------------------------------------------------------
    private static final Map<String, Integer> DEFAULT_PICKAXE_HITS = new LinkedHashMap<>();
    static {
        DEFAULT_PICKAXE_HITS.put("crude",     -1);
        DEFAULT_PICKAXE_HITS.put("copper",     8);
        DEFAULT_PICKAXE_HITS.put("iron",       6);
        DEFAULT_PICKAXE_HITS.put("thorium",    4);
        DEFAULT_PICKAXE_HITS.put("cobalt",     2);
        DEFAULT_PICKAXE_HITS.put("adamantite", 1);
        DEFAULT_PICKAXE_HITS.put("mithril",    1);
    }

    /**
     * Dado el ID completo del item en la mano del jugador, devuelve cuántos
     * golpes exitosos necesita para extraer el bloque indicado.
     *
     * Reglas:
     *  - El item debe contener "pickaxe" (case-insensitive) para considerarse pico.
     *  - Se busca en "pickaxes" del bloque la primera clave cuyo texto aparezca
     *    en el ID del item (ej. "adamantite" subset "Weapon_Adamantite_Pickaxe").
     *  - Si el bloque no tiene sección "pickaxes", se usan los defaults.
     *  - Si el item no es un pico o no coincide ninguna clave, devuelve -1.
     */
    public static int getHitsForPickaxe(String blockId, String heldItemId) {
        if (heldItemId == null) return -1;

        String heldLower = heldItemId.toLowerCase();
        if (!heldLower.contains("pickaxe")) return -1;

        try {
            Path path = Paths.get(getConfigPath());
            String content = new String(Files.readAllBytes(path));
            Gson gson = new Gson();
            JsonObject root = gson.fromJson(content, JsonObject.class);
            JsonArray items = root.getAsJsonArray("Items");

            for (JsonElement element : items) {
                JsonObject item = element.getAsJsonObject();
                if (!item.get("itemId").getAsString().equals(blockId)) continue;

                // Usar sección "pickaxes" del bloque si existe, si no usar defaults
                Map<String, Integer> pickaxeMap;
                if (item.has("pickaxes")) {
                    pickaxeMap = new LinkedHashMap<>();
                    JsonObject pickaxes = item.getAsJsonObject("pickaxes");
                    for (Map.Entry<String, com.google.gson.JsonElement> entry : pickaxes.entrySet()) {
                        pickaxeMap.put(entry.getKey().toLowerCase(), entry.getValue().getAsInt());
                    }
                } else {
                    pickaxeMap = DEFAULT_PICKAXE_HITS;
                }

                // Primera clave que aparezca en el ID del item
                for (Map.Entry<String, Integer> entry : pickaxeMap.entrySet()) {
                    if (heldLower.contains(entry.getKey())) {
                        return entry.getValue();
                    }
                }
                break;
            }
        } catch (Exception e) {
            System.err.println("[WeakPoint] Error al obtener hits para pico en: " + blockId);
        }

        return -1;
    }

    /**
     * Retorna el tiempo en segundos que tarda en resetearse un bloque sin acción.
     * Lee el campo "timeToChip" del JSON. Si no existe, usa 30s como default.
     */
    public static float getTimeToChip(String blockId) {
        return getFloatField(blockId, "timeToChip", 30f);
    }

    /**
     * Retorna el bloque "vacío" al que se convierte el mineral al ser extraído.
     * Lee el campo "defaultBlock" del JSON. Si no existe, devuelve "Empty".
     */
    public static String getDefaultBlock(String blockId) {
        try {
            Path path = Paths.get(getConfigPath());
            String content = new String(Files.readAllBytes(path));
            Gson gson = new Gson();
            JsonObject root = gson.fromJson(content, JsonObject.class);
            JsonArray items = root.getAsJsonArray("Items");

            for (JsonElement element : items) {
                JsonObject item = element.getAsJsonObject();
                if (!item.get("itemId").getAsString().equals(blockId)) continue;
                if (item.has("defaultBlock")) return item.get("defaultBlock").getAsString();
                break;
            }
        } catch (Exception e) {
            System.err.println("[WeakPoint] Error al obtener defaultBlock de: " + blockId);
        }
        return "Empty";
    }

    /**
     * Retorna el tiempo de respawn en segundos del mineral una vez extraído.
     * Lee el campo "respawnTime" del JSON. Si no existe, usa 60s como default.
     */
    public static float getRespawnTime(String blockId) {
        return getFloatField(blockId, "respawnTime", 60f);
    }

    /** Helper genérico para leer un campo float del JSON. */
    private static float getFloatField(String blockId, String field, float defaultValue) {
        try {
            Path path = Paths.get(getConfigPath());
            String content = new String(Files.readAllBytes(path));
            Gson gson = new Gson();
            JsonObject root = gson.fromJson(content, JsonObject.class);
            JsonArray items = root.getAsJsonArray("Items");

            for (JsonElement element : items) {
                JsonObject item = element.getAsJsonObject();
                if (!item.get("itemId").getAsString().equals(blockId)) continue;
                if (item.has(field)) return item.get(field).getAsFloat();
                break;
            }
        } catch (Exception e) {
            System.err.println("[WeakPoint] Error al obtener " + field + " de: " + blockId);
        }
        return defaultValue;
    }

    public static class ItemDrop {
        public String itemId;
        public int quantity;

        public ItemDrop(String itemId, int quantity) {
            this.itemId = itemId;
            this.quantity = quantity;
        }
    }

    public static List<ItemDrop> getMainDrops(String blockId) {
        List<ItemDrop> drops = new ArrayList<>();

        try {
            Path path = Paths.get(getConfigPath());
            String content = new String(Files.readAllBytes(path));
            Gson gson = new Gson();
            JsonObject root = gson.fromJson(content, JsonObject.class);
            JsonArray items = root.getAsJsonArray("Items");

            for (JsonElement element : items) {
                JsonObject item = element.getAsJsonObject();
                if (!item.get("itemId").getAsString().equals(blockId)) continue;

                JsonArray mainDrop = item.getAsJsonArray("mainDrop");
                for (JsonElement dropElement : mainDrop) {
                    JsonObject drop = dropElement.getAsJsonObject();
                    String itemId = drop.get("itemId").getAsString();
                    int quantity = drop.get("Quantity").getAsInt();
                    drops.add(new ItemDrop(itemId, quantity));
                }
                break;
            }

        } catch (Exception e) {
            System.err.println("[WeakPoint] Error al obtener drops de: " + blockId);
            e.printStackTrace();
        }

        return drops;
    }

    public static class ExtraDrop {
        public String itemId;
        public int quantity;

        public ExtraDrop(String itemId, int quantity) {
            this.itemId = itemId;
            this.quantity = quantity;
        }
    }

    public static List<ExtraDrop> getExtraDrops(String blockId) {
        List<ExtraDrop> result = new ArrayList<>();
        Random random = new Random();

        try {
            Path path = Paths.get(getConfigPath());
            String content = new String(Files.readAllBytes(path));
            Gson gson = new Gson();
            JsonObject root = gson.fromJson(content, JsonObject.class);
            JsonArray items = root.getAsJsonArray("Items");

            for (JsonElement element : items) {
                JsonObject item = element.getAsJsonObject();
                if (!item.get("itemId").getAsString().equals(blockId)) continue;

                int dropMax = item.get("dropMax").getAsInt();
                JsonArray extraDrops = item.getAsJsonArray("extraDrop");

                for (JsonElement dropElement : extraDrops) {
                    JsonObject drop = dropElement.getAsJsonObject();

                    int dropChance = drop.get("dropChance").getAsInt();
                    int roll = random.nextInt(100) + 1; // 1 a 100

                    if (roll <= dropChance) {
                        int minQty = drop.get("minQuantity").getAsInt();
                        int maxQty = drop.get("maxQuantity").getAsInt();
                        int quantity = minQty + random.nextInt(maxQty - Math.max(minQty, 1) + 1) + (minQty == 0 ? 1 : 0);

                        result.add(new ExtraDrop(drop.get("itemId").getAsString(), quantity));
                    }
                }

                if (result.size() > dropMax) {
                    Collections.shuffle(result, random);
                    result = result.subList(0, dropMax);
                }

                break;
            }

        } catch (Exception e) {
            System.err.println("[WeakPoint] Error al obtener extra drops de: " + blockId);
            e.printStackTrace();
        }

        return result;
    }
}