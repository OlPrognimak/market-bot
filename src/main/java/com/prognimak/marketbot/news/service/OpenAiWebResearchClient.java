package com.prognimak.marketbot.news.service;

import com.prognimak.marketbot.news.model.NewsResearchSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.JsonNode;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class OpenAiWebResearchClient {
    private final WebClient webClient;
    private final String apiKey;
    private final String model;

    public OpenAiWebResearchClient(
            WebClient.Builder builder,
            @Value("${spring.ai.openai.api-key:}") String apiKey,
            @Value("${market-bot.news-monitoring.web-research-model:gpt-5-mini}") String model
    ) {
        this.webClient = builder.baseUrl("https://api.openai.com/v1").build();
        this.apiKey = apiKey;
        this.model = model;
    }

    public WebResearchResult research(String prompt) {
        JsonNode response = webClient.post()
                .uri("/responses")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "model", model,
                        "tools", List.of(Map.of("type", "web_search")),
                        "tool_choice", "auto",
                        "include", List.of("web_search_call.action.sources"),
                        "input", prompt
                ))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();
        if (response == null) {
            throw new IllegalStateException("OpenAI Responses API returned no response.");
        }

        String content = extractOutputText(response);
        if (content.isBlank()) {
            throw new IllegalStateException("OpenAI Responses API returned no research text.");
        }
        return new WebResearchResult(content, extractSources(response));
    }

    public String model() {
        return model;
    }

    private String extractOutputText(JsonNode response) {
        StringBuilder text = new StringBuilder();
        JsonNode output = response.path("output");
        if (!output.isArray()) {
            return response.path("output_text").asText("");
        }
        for (JsonNode item : output) {
            if (!"message".equals(item.path("type").asText())) {
                continue;
            }
            for (JsonNode content : item.path("content")) {
                if ("output_text".equals(content.path("type").asText())) {
                    if (!text.isEmpty()) {
                        text.append("\n\n");
                    }
                    text.append(content.path("text").asText(""));
                }
            }
        }
        return text.toString();
    }

    private List<NewsResearchSource> extractSources(JsonNode response) {
        Map<String, String> sources = new LinkedHashMap<>();
        collectSources(response.path("output"), sources, new LinkedHashSet<>());
        return sources.entrySet().stream()
                .map(entry -> new NewsResearchSource(entry.getValue(), entry.getKey()))
                .toList();
    }

    private void collectSources(JsonNode node, Map<String, String> sources, Set<JsonNode> visited) {
        if (node == null || node.isMissingNode() || node.isNull() || !visited.add(node)) {
            return;
        }
        if (node.isObject()) {
            String url = node.path("url").asText("");
            if (url.startsWith("http://") || url.startsWith("https://")) {
                String title = node.path("title").asText(url);
                sources.putIfAbsent(url, title.isBlank() ? url : title);
            }
            node.values().forEach(child -> collectSources(child, sources, visited));
        } else if (node.isArray()) {
            node.values().forEach(child -> collectSources(child, sources, visited));
        }
    }

    public record WebResearchResult(String content, List<NewsResearchSource> sources) {
    }
}
