package Tenzinn.UI;

import Tenzinn.MiningLimits;
import Tenzinn.UI.Data.DashboardEvent;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;

import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

import java.util.Map;
import java.util.Collection;

public class DashboardPage extends InteractiveCustomUIPage<DashboardEvent> {

    private UICommandBuilder uiBuilder;

    public DashboardPage(PlayerRef playerRef) { super(playerRef, CustomPageLifetime.CanDismiss, DashboardEvent.CODEC); }

    @Override
    public void build(@NonNullDecl Ref<EntityStore> ref, @NonNullDecl UICommandBuilder uiCommandBuilder,
                      @NonNullDecl UIEventBuilder uiEventBuilder, @NonNullDecl Store<EntityStore> store) {

        uiCommandBuilder.append("Dashboard.ui");
        uiBuilder = uiCommandBuilder;

        Collection<PlayerRef> onlinePlayers = Universe.get().getPlayers();
        for (PlayerRef online : onlinePlayers) {
            appendPlayerRow(online);
        }

        sendUpdate();
    }

    private void appendPlayerRow(PlayerRef online) {
        String uuid     = online.getUuid().toString();
        String username = online.getUsername();

        MiningLimits.PlayerData   data = MiningLimits.getPlayerData(uuid);
        MiningLimits.PlayerConfig cfg  = MiningLimits.getEffectiveConfig(uuid);

        StringBuilder mineralLabels = new StringBuilder();
        int number = 1;
        for (Map.Entry<String, Integer> entry : cfg.minerals.entrySet()) {
            int typeCount = (data != null) ? data.byType.getOrDefault(entry.getKey(), 0) : 0;
            int typeLimit = entry.getValue();

            String text  = (typeLimit > 0) ? typeCount + "/" + typeLimit : String.valueOf(typeCount);
            String color = (typeLimit > 0 && typeCount >= typeLimit) ? "#FF4444" : "#FFFFFF(0.75)";

            mineralLabels.append("Label #M").append(number).append(" { ").append("FlexWeight: 1; ")
                    .append("Text: \"").append(text).append("\"; ").append("Style: (TextColor: ").append(color).append(", FontSize: 14, Alignment: Center); ")
                    .append("Anchor: (Right: 12); }\n");

            number++;
        }

        int    collected  = (data != null) ? data.totalCollected : 0;
        int    total      = cfg.totalMinerals;
        String totalText  = collected + "/" + total;
        String totalColor = (collected >= total) ? "#FFCC00" : "#FFFFFF(0.75)";

        String inlineUI =
                "Group {\n" +
                        "    FlexWeight: 1;\n" +
                        "    LayoutMode: Middle;\n" +
                        "    Padding: (Horizontal: 24);\n" +
                        "    Anchor: (Height: 60, Bottom: 6);\n" +
                        "    Background: (Color: #FFFFFF(0.05));\n" +
                        "\n" +
                        "    Group {\n" +
                        "        LayoutMode: Left;\n" +
                        "\n" +
                        "        Label {\n" +
                        "            Text: \"" + escapeUiString(username) + "\";\n" +
                        "            Anchor: (Right: 12, Width: 200);\n" +
                        "            Style: (TextColor: #FFFFFF, RenderBold: true, FontSize: 18);\n" +
                        "        }\n" +
                        "\n" +
                        "        " + mineralLabels +
                        "Label { FlexWeight: 2; Text: \"" + totalText + "\"; " +
                        "Style: (TextColor: " + totalColor + ", FontSize: 14, Alignment: Center); }\n" +
                        "    }\n" +
                        "}";

        uiBuilder.appendInline("#ListUsers", inlineUI);
    }

    // Escapa comillas dobles en strings que van dentro del .ui inline
    private static String escapeUiString(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}