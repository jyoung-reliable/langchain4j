package dev.langchain4j.agentic.internal;

import java.util.Arrays;

public record AgentInvocation(AgentSpecification agentSpecification, Object[] input, Object response) {

    @Override
    public String toString() {
        return "AgentInvocation{" +
                "agentName=" + agentSpecification.name() +
                ", input=" + Arrays.toString(input) +
                ", response=" + response +
                '}';
    }
}
