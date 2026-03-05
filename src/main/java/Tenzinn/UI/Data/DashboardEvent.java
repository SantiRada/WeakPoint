package Tenzinn.UI.Data;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

public class DashboardEvent {

    private String action;

    public DashboardEvent() { }

    public String getAction() { return action; }

    public static final BuilderCodec<DashboardEvent> CODEC = BuilderCodec
            .builder(DashboardEvent.class, DashboardEvent::new).append(new KeyedCodec<>("Action", Codec.STRING), (data, value) -> data.action = value,
                    (data) -> data.action).add().build();
}