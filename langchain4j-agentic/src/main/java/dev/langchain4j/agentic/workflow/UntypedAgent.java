package dev.langchain4j.agentic.workflow;

import dev.langchain4j.agentic.Agent;
import java.util.Map;

public interface UntypedAgent {
    @Agent
    Map<String, Object> invoke(Map<String, Object> input);
}
