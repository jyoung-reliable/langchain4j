package dev.langchain4j.agentic;

import dev.langchain4j.agentic.internal.AgentInvocation;
import dev.langchain4j.agentic.workflow.LoopAgentService;
import dev.langchain4j.agentic.workflow.SequentialAgentService;
import dev.langchain4j.agentic.workflow.UntypedAgent;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static dev.langchain4j.model.ollama.AbstractOllamaLanguageModelInfrastructure.ollama;
import static dev.langchain4j.model.ollama.AbstractOllamaLanguageModelInfrastructure.ollamaBaseUrl;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

public class WorkflowAgentsIT {

    private static final String OLLAMA_BASE_URL = ollamaBaseUrl(ollama);

    static final ChatModel model = OllamaChatModel.builder()
            .baseUrl(OLLAMA_BASE_URL)
            .modelName("qwen2.5:7b")
            .timeout(Duration.ofMinutes(10))
            .temperature(0.0)
            .logRequests(true)
            .logResponses(true)
            .build();

    public interface CreativeWriter {

        @UserMessage("""
            You are a creative writer.
            Generate a draft of a story long no more than 3 sentence around the given topic.
            Return only the story and nothing else.
            The topic is {{topic}}.
            """)
        String generateStory(@V("topic") String topic);
    }

    public interface AudienceEditor {

        @UserMessage("""
            You are a professional editor.
            Analyze and rewrite the following story to better align with the target audience of {{audience}}.
            Return only the story and nothing else.
            The story is "{{story}}".
            """)
        String editStory(@V("story") String story, @V("audience") String audience);
    }

    public interface StyleEditor {

        @UserMessage("""
            You are a professional editor.
            Analyze and rewrite the following story to better fit and be more coherent with the {{style}} style.
            Return only the story and nothing else.
            The story is "{{story}}".
            """)
        String editStory(@V("story") String story, @V("style") String style);
    }

    @Test
    void sequential_agents_tests() {
        CreativeWriter creativeWriter = spy(AgentServices.builder(CreativeWriter.class)
                .chatModel(model)
                .outputName("story")
                .build());

        AudienceEditor audienceEditor = spy(AgentServices.builder(AudienceEditor.class)
                .chatModel(model)
                .outputName("story")
                .build());

        StyleEditor styleEditor = spy(AgentServices.builder(StyleEditor.class)
                .chatModel(model)
                .outputName("story")
                .build());

        UntypedAgent novelCreator = SequentialAgentService.builder()
                .subAgents(creativeWriter, audienceEditor, styleEditor)
                .build();

        Map<String, Object> input = Map.of(
                "topic", "dragons and wizards",
                "style", "fantasy",
                "audience", "young adults"
        );

        Map<String, Object> output = novelCreator.invoke(input);
        System.out.println(output.get("story"));

        verify(creativeWriter).generateStory("dragons and wizards");
        verify(audienceEditor).editStory(any(), eq("young adults"));
        verify(styleEditor).editStory(any(), eq("fantasy"));
    }

    public interface StyleScorer {

        @UserMessage("""
            You are a critical reviewer.
            Give a review score between 0.0 and 1.0 for the following story based on how well it aligns with the style '{{style}}'.
            Return only the score and nothing else.

            The story is: "{{story}}"
            """)
        double scoreStyle(@V("story") String story, @V("style") String style);
    }

    @Test
    void loop_agents_tests() {
        CreativeWriter creativeWriter = AgentServices.builder(CreativeWriter.class)
                .chatModel(model)
                .outputName("story")
                .build();

        StyleEditor styleEditor = AgentServices.builder(StyleEditor.class)
                .chatModel(model)
                .outputName("story")
                .build();

        StyleScorer styleScorer = AgentServices.builder(StyleScorer.class)
                .chatModel(model)
                .outputName("score")
                .build();

        Object styleReviewLoop = LoopAgentService.builder()
                .subAgents(styleScorer, styleEditor)
                .maxIterations(5)
                .exitCondition( cognisphere -> cognisphere.readState("score", 0.0) >= 0.8)
                .build();

        UntypedAgent styledWriter = SequentialAgentService.builder()
                .subAgents(creativeWriter, styleReviewLoop)
                .build();

        Map<String, Object> input = Map.of(
                "topic", "dragons and wizards",
                "style", "comedy"
        );

        Map<String, Object> output = styledWriter.invoke(input);
        String story = (String) output.get("story");
        System.out.println(story);

        Cognisphere cognisphere = ((CognisphereOwner) styledWriter).cognisphere();
        assertThat(story).isEqualTo(cognisphere.readState("story"));
        assertThat(cognisphere.readState("score", 0.0)).isGreaterThanOrEqualTo(0.8);
    }

    public interface StyledWriter {

        @Agent
        String writeStoryWithStyle(@V("topic") String topic, @V("style") String style);
    }

    @Test
    void typed_loop_agents_tests() {
        CreativeWriter creativeWriter = AgentServices.builder(CreativeWriter.class)
                .chatModel(model)
                .outputName("story")
                .build();

        StyleEditor styleEditor = AgentServices.builder(StyleEditor.class)
                .chatModel(model)
                .outputName("story")
                .build();

        StyleScorer styleScorer = AgentServices.builder(StyleScorer.class)
                .chatModel(model)
                .outputName("score")
                .build();

        Object styleReviewLoop = LoopAgentService.builder()
                .subAgents(styleScorer, styleEditor)
                .maxIterations(5)
                .exitCondition( cognisphere -> cognisphere.readState("score", 0.0) >= 0.8)
                .build();

        StyledWriter styledWriter = SequentialAgentService.builder(StyledWriter.class)
                .subAgents(creativeWriter, styleReviewLoop)
                .outputName("story")
                .build();

        String story = styledWriter.writeStoryWithStyle("dragons and wizards", "comedy");
        System.out.println(story);

        Cognisphere cognisphere = ((CognisphereOwner) styledWriter).cognisphere();
        assertThat(cognisphere.readState("topic")).isEqualTo("dragons and wizards");
        assertThat(cognisphere.readState("style")).isEqualTo("comedy");
        assertThat(story).isEqualTo(cognisphere.readState("story"));
        assertThat(cognisphere.readState("score", 0.0)).isGreaterThanOrEqualTo(0.8);

        assertThat(cognisphere.getAgentInvocations("generateStory")).hasSize(1);

        List<AgentInvocation> scoreAgentInvocations = cognisphere.getAgentInvocations("scoreStyle");
        assertThat(scoreAgentInvocations).hasSizeBetween(1, 5);
        System.out.println("Score agent invocations: " + scoreAgentInvocations);
        assertThat((Double) scoreAgentInvocations.get(scoreAgentInvocations.size() - 1).response()).isGreaterThanOrEqualTo(0.8);
    }
}
