package com.github.oogasawa.slmimecompanion.historyreview;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

/**
 * Asks a local LLM, through an OpenAI-compatible chat endpoint (the GPU broker), whether one choice of the history is
 * a slip. The request is sent at background priority ({@code X-Job-Priority: background}), so that the conversations
 * of the people using the same model go first, and without the model's thinking: with it, the model found a way for
 * almost every slip to have been meant.
 */
final class LlmJudge {

    /** The instructions, as they were measured on the user's own history (2026-11-09). */
    static final String SYSTEM = """
            You review entries of a Japanese input method's user dictionary.
            Each entry is a reading in hiragana and the text the user committed for it, sometimes with the text the user wrote just before it.
            Some entries are accidents: the user pressed Enter on a wrong candidate. Others are deliberate choices the user wants learned.
            Answer "drop" only when the committed text is a mis-conversion: a different word than the user could have meant, a meaningless string, or wrong characters in the context.
            Answer "keep" for anything a writer could have chosen on purpose, including:
            - kanji or kana spelling preferences (要る, 効く, 速く, 下さい, 無い, ナントカ, ひらがなのまま),
            - proper nouns, technical terms, abbreviations, rare or old-fashioned spellings,
            - choices that are correct in some context even if the given context is missing.
            Reply with JSON only: {"verdict": "keep" or "drop", "reason": "<short reason in English>"}""";

    private final URI url;
    private final String model;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    LlmJudge(URI url, String model) {
        this.url = url;
        this.model = model;
    }

    /** What the model is shown for one choice. */
    static String prompt(Choice c) {
        StringBuilder user = new StringBuilder();
        user.append("reading: ").append(c.reading()).append('\n');
        user.append("committed: ").append(c.chosen()).append('\n');
        if (c.output().isEmpty()) {
            user.append("whole sentence: (unknown)");
        } else {
            user.append("whole sentence as committed: ").append(c.output()).append('\n');
            user.append("its reading: ").append(c.input());
        }
        return user.toString();
    }

    /**
     * Whether the model takes {@code c} for a slip; empty when it gave no answer that can be read (the server did not
     * answer, or answered something else), so that the choice is asked again another time.
     */
    Optional<Boolean> isSlip(Choice c) {
        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.addProperty("temperature", 0);
        body.addProperty("max_tokens", 200);
        JsonObject kwargs = new JsonObject();
        kwargs.addProperty("enable_thinking", false);
        body.add("chat_template_kwargs", kwargs);
        JsonArray messages = new JsonArray();
        messages.add(message("system", SYSTEM));
        messages.add(message("user", prompt(c)));
        body.add("messages", messages);
        HttpRequest request = HttpRequest.newBuilder(url)
                .timeout(Duration.ofMinutes(15))
                .header("Content-Type", "application/json")
                .header("X-Job-Priority", "background")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                return Optional.empty();
            }
            String content = JsonParser.parseString(response.body()).getAsJsonObject()
                    .getAsJsonArray("choices").get(0).getAsJsonObject()
                    .getAsJsonObject("message").get("content").getAsString();
            return verdictOf(content);
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    /** The verdict of the model's reply: the last JSON object in it, {@code "drop"} or {@code "keep"}. */
    static Optional<Boolean> verdictOf(String reply) {
        int open = reply.lastIndexOf('{');
        int close = reply.lastIndexOf('}');
        if (open < 0 || close < open) {
            return Optional.empty();
        }
        try {
            String verdict = JsonParser.parseString(reply.substring(open, close + 1)).getAsJsonObject()
                    .get("verdict").getAsString();
            return switch (verdict) {
                case "drop" -> Optional.of(true);
                case "keep" -> Optional.of(false);
                default -> Optional.empty();
            };
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private static JsonObject message(String role, String content) {
        JsonObject m = new JsonObject();
        m.addProperty("role", role);
        m.addProperty("content", content);
        return m;
    }
}
