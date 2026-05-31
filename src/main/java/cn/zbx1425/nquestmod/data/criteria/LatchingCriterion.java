package cn.zbx1425.nquestmod.data.criteria;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

public class LatchingCriterion implements Criterion {

    protected Criterion base;

    public LatchingCriterion(Criterion base) {
        this.base = base;
    }

    @Override
    public boolean evaluate(ServerPlayer player, CriterionContext ctx) {
        if (ctx.getBoolean("fulfilled", false)) return true;
        if (base.evaluate(player, ctx.child("b"))) {
            ctx.setBoolean("fulfilled", true);
            return true;
        }
        return false;
    }

    @Override
    public boolean evaluateFailureTypes(ServerPlayer player, CriterionContext ctx, List<String> failureTypes) {
        if (ctx.getBoolean("fulfilled", false)) {
            base.collectLeafTypes(failureTypes);
            return true;
        }
        if (base.evaluateFailureTypes(player, ctx.child("b"), failureTypes)) {
            ctx.setBoolean("fulfilled", true);
            return true;
        }
        return false;
    }

    @Override
    public void collectLeafTypes(List<String> failureTypes) {
        base.collectLeafTypes(failureTypes);
    }

    @Override
    public Component getDisplayRepr() {
        return base.getDisplayRepr();
    }

    @Override
    public void propagateManualTrigger(String triggerId, CriterionContext ctx) {
        base.propagateManualTrigger(triggerId, ctx.child("b"));
    }

    @Override
    public Criterion expand() {
        return new LatchingCriterion(base.expand());
    }
}
