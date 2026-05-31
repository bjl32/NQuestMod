package cn.zbx1425.nquestmod.data.criteria;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

public interface Criterion {

    boolean evaluate(ServerPlayer player, CriterionContext ctx);

    Component getDisplayRepr();

    default void propagateManualTrigger(String triggerId, CriterionContext ctx) {

    }

    default Criterion expand() {
        return this;
    }

    default boolean evaluateFailureTypes(ServerPlayer player, CriterionContext ctx, List<String> failureTypes) {
        boolean matched = evaluate(player, ctx);
        if (matched) collectLeafTypes(failureTypes);
        return matched;
    }

    default void collectLeafTypes(List<String> failureTypes) {
        failureTypes.add(getClass().getSimpleName());
    }
}
