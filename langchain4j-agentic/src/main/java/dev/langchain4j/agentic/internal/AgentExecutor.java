package dev.langchain4j.agentic.internal;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.Cognisphere;
import dev.langchain4j.agentic.CognisphereOwner;
import dev.langchain4j.service.UserMessage;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static dev.langchain4j.internal.Utils.getAnnotatedMethod;

public record AgentExecutor(AgentSpecification agentSpecification, AgentInstance agent) {

    public boolean isWorkflowAgent() {
        return agentSpecification.isWorkflowAgent();
    }

    public Object invoke(Cognisphere cognisphere) {
        Object invokedAgent = agent instanceof CognisphereOwner co ? co.withCognisphere(cognisphere) : agent;
        Object[] args = agentSpecification.toInvocationArguments(cognisphere.getState());

        try {
            Object response = agentSpecification.method().invoke(invokedAgent, args);
            String outputName = agent.outputName();
            if (outputName != null) {
                cognisphere.writeState(outputName, response);
            }
            cognisphere.registerAgentInvocation(agentSpecification, args, response);
            return response;
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    public static List<AgentExecutor> agentsToExecutors(List<AgentInstance> agents) {
        List<AgentExecutor> agentExecutors = new ArrayList<>();
        for (AgentInstance agent : agents) {
            for (Method method : agent.getClass().getDeclaredMethods()) {
                getAnnotatedMethod(method, Agent.class)
                        .or(() -> getAnnotatedMethod(method, UserMessage.class))
                        .ifPresent(agentMethod -> agentExecutors.add(new AgentExecutor(AgentSpecification.fromMethod(agentMethod), agent)) );
            }
        }
        return agentExecutors;
    }
}
