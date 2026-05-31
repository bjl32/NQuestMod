package cn.zbx1425.nquestmod.data.quest;

import cn.zbx1425.nquestmod.data.criteria.Criterion;
import cn.zbx1425.nquestmod.data.criteria.CriterionContext;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class Step {

    public Criterion criteria;
    public Criterion failureCriteria;

    public record FailureResult(Component displayRepr, String criterionType) {}

    public Step(Criterion criteria, Criterion failureCriteria) {
        this.criteria = criteria;
        this.failureCriteria = failureCriteria;
    }

    public boolean evaluate(ServerPlayer player, CriterionContext ctx) {
        return criteria != null && criteria.evaluate(player, ctx);
    }

    public Optional<FailureResult> evaluateFailure(
            ServerPlayer player, CriterionContext failCtx,
            Step defaultCriteria, CriterionContext defaultFailCtx) {
        if (failureCriteria != null) {
            List<String> failureTypes = new ArrayList<>();
            if (failureCriteria.evaluateFailureTypes(player, failCtx, failureTypes)) {
                return Optional.of(new FailureResult(
                        failureCriteria.getDisplayRepr(),
                        String.join("+", failureTypes)));
            }
            return Optional.empty();
        } else if (defaultCriteria != null && defaultCriteria.failureCriteria != null) {
            // Step-wide failure criteria overrides quest-wide failure criteria
            List<String> failureTypes = new ArrayList<>();
            if (defaultCriteria.failureCriteria.evaluateFailureTypes(player, defaultFailCtx, failureTypes)) {
                return Optional.of(new FailureResult(
                        defaultCriteria.failureCriteria.getDisplayRepr(),
                        String.join("+", failureTypes)));
            }
        }
        return Optional.empty();
    }

    public Component getDisplayRepr() {
        return criteria != null ? criteria.getDisplayRepr() : Component.literal("Impossible Step");
    }

    public void propagateManualTrigger(String triggerId, CriterionContext criteriaCtx, CriterionContext failureCtx) {
        if (criteria != null) criteria.propagateManualTrigger(triggerId, criteriaCtx);
        if (failureCriteria != null) failureCriteria.propagateManualTrigger(triggerId, failureCtx);
    }

    public Step expand() {
        return new Step(
            criteria != null ? criteria.expand() : null,
            failureCriteria != null ? failureCriteria.expand() : null);
    }
}
