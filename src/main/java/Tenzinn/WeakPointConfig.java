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