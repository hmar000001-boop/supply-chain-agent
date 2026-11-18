package dev.supplychain.agent.runtime.cognitive.lifecycle;

import dev.supplychain.agent.actions.api.ActionContext;
import dev.supplychain.agent.actions.api.ActionDispatcher;
import dev.supplychain.agent.actions.api.ActionId;
import dev.supplychain.agent.actions.api.ActionResult;

import java.util.Objects;

/** A trusted, typed M3 action invocation created by the domain adapter for one planned action. */
public record ActionDispatchCommand<I, O>(String planningActionId, ActionId actionId,
                                          Class<I> inputType, Class<O> outputType, I input) {
    public ActionDispatchCommand {
        planningActionId = required(planningActionId, "planningActionId");
        Objects.requireNonNull(actionId, "actionId");
        Objects.requireNonNull(inputType, "inputType");
        Objects.requireNonNull(outputType, "outputType");
        Objects.requireNonNull(input, "input");
        if (!inputType.isInstance(input)) throw new IllegalArgumentException("input does not match declared inputType");
    }

    public ActionResult<O> dispatch(ActionDispatcher dispatcher, ActionContext context) {
        return Objects.requireNonNull(dispatcher, "dispatcher")
                .dispatch(actionId, inputType, outputType, context, input);
    }

    private static String required(String value, String name) {
        String result = Objects.requireNonNull(value, name).trim();
        if (result.isEmpty()) throw new IllegalArgumentException(name + " cannot be blank");
        return result;
    }
}
