package cn.zbx1425.nquestmod.data.criteria;

import cn.zbx1425.nquestmod.interop.TscStatus;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class OverSpeedCriterion implements Criterion {

    public double maxSpeedKmph;

    public OverSpeedCriterion(double maxSpeedKmph) {
        this.maxSpeedKmph = maxSpeedKmph;
    }

    @Override
    public boolean evaluate(ServerPlayer player, CriterionContext ctx) {
        TscStatus.ClientState state = TscStatus.getClientState(player);
        return state != null && state.trainSpeedKmph() > maxSpeedKmph;
    }

    @Override
    public Component getDisplayRepr() {
        return Component.literal("Move faster than " + String.format("%.1f", maxSpeedKmph) + " km/h");
    }
}
