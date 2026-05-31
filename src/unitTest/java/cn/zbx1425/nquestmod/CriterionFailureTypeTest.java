package cn.zbx1425.nquestmod;

import cn.zbx1425.nquestmod.data.criteria.AndCriterion;
import cn.zbx1425.nquestmod.data.criteria.ConstantCriterion;
import cn.zbx1425.nquestmod.data.criteria.CriterionContext;
import cn.zbx1425.nquestmod.data.criteria.Descriptor;
import cn.zbx1425.nquestmod.data.criteria.OrCriterion;
import cn.zbx1425.nquestmod.data.quest.Step;
import cn.zbx1425.nquestmod.data.quest.StepState;

import java.util.List;

public class CriterionFailureTypeTest {

    public static void main(String[] args) {
        recordsAllMatchingLeafTypesInTreeOrder();
    }

    private static void recordsAllMatchingLeafTypesInTreeOrder() {
        Step step = new Step(
                new ConstantCriterion(false, "unused"),
                new Descriptor(new OrCriterion(List.of(
                        new ConstantCriterion(true, "first"),
                        new AndCriterion(List.of(
                                new ConstantCriterion(true, "second"),
                                new ConstantCriterion(false, "not matched")
                        )),
                        new ConstantCriterion(true, "third")
                )), "wrapped"));

        Step.FailureResult failure = step.evaluateFailure(
                null, new CriterionContext(new StepState(), ""),
                null, new CriterionContext(new StepState(), "")
        ).orElseThrow();

        assertEquals("ConstantCriterion+ConstantCriterion", failure.criterionType());
    }

    private static void assertEquals(Object expected, Object actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError("Expected " + expected + " but got " + actual);
        }
    }
}
