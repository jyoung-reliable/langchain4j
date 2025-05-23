package dev.langchain4j.agentic.internal;

import java.lang.reflect.Method;
import java.util.Map;

public record WorkflowAgentSpecification(Method method, String name, String description) implements AgentSpecification {

    @Override
    public boolean isWorkflowAgent() {
        return true;
    }

    @Override
    public String toCard() {
        return "{" + name + ": " + description + "}";
    }

    @Override
    public Object[] toInvocationArguments(Map<String, ?> arguments) {
        return new Object[] { arguments };
    }
}
