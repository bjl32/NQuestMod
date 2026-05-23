package cn.zbx1425.nquestmod;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class QuestHudNetworking {

    public static final ResourceLocation QUEST_HUD = NQuestMod.id("quest_hud");

    public record HudState(
            boolean active,
            Component objective,
            boolean debugMode,
            int currentStepNumber,
            int totalSteps,
            long totalElapsedMillis,
            long segmentElapsedMillis
    ) {

        public static HudState inactive() {
            return new HudState(false, Component.empty(), false, 0, 0, 0, 0);
        }
    }

    public static void write(FriendlyByteBuf buf, HudState state) {
        buf.writeBoolean(state.active);
        if (!state.active) return;
        buf.writeComponent(state.objective);
        buf.writeBoolean(state.debugMode);
        buf.writeVarInt(state.currentStepNumber);
        buf.writeVarInt(state.totalSteps);
        buf.writeLong(state.totalElapsedMillis);
        buf.writeLong(state.segmentElapsedMillis);
    }

    public static HudState read(FriendlyByteBuf buf) {
        boolean active = buf.readBoolean();
        if (!active) {
            return HudState.inactive();
        }
        return new HudState(
                true,
                buf.readComponent(),
                buf.readBoolean(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readLong(),
                buf.readLong()
        );
    }
}
