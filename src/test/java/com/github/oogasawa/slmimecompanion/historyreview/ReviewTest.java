package com.github.oogasawa.slmimecompanion.historyreview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReviewTest {

    private static String line(String reading, String chosen, String input, String output) {
        return "{\"reading\":\"" + reading + "\",\"chosen\":\"" + chosen + "\",\"input\":\"" + input
                + "\",\"output\":\"" + output + "\",\"time\":1}";
    }

    private static List<String> suspects(Path dir) throws Exception {
        List<String> found = new ArrayList<>();
        Path inbox = dir.resolve("suspects");
        if (Files.isDirectory(inbox)) {
            try (Stream<Path> files = Files.list(inbox)) {
                for (Path f : files.filter(p -> p.toString().endsWith(".jsonl")).toList()) {
                    for (String l : Files.readAllLines(f)) {
                        JsonObject o = JsonParser.parseString(l).getAsJsonObject();
                        found.add(o.get("reading").getAsString() + "=" + o.get("chosen").getAsString());
                    }
                }
            }
        }
        return found;
    }

    @Test
    void onlyTheSlipsGoToTheServer_andEveryVerdictIsRecorded(@TempDir Path dir) throws Exception {
        Files.write(dir.resolve("choices.jsonl"), List.of(
                line("もう", "望", "もう", "望"),
                line("いこう", "行こう", "はいどんどんいこう", "はいどんどん行こう"),
                "broken"));
        Path state = dir.resolve("state/judged.tsv");
        Map<String, Boolean> verdicts = Map.of("望", true, "行こう", false);

        Review.Summary s = Review.run(dir, Judged.load(state), c -> Optional.of(verdicts.get(c.chosen())), 100);

        assertEquals(new Review.Summary(2, 2, 1, 0), s);
        assertEquals(List.of("もう=望"), suspects(dir));
        assertEquals(2, Judged.load(state).size());
        assertFalse(Files.exists(dir.resolve("choices.jsonl")));
        try (Stream<Path> left = Files.list(dir)) {
            assertTrue(left.noneMatch(p -> p.getFileName().toString().endsWith(".taking")));
        }
    }

    @Test
    void aChoiceJudgedBeforeIsNotAskedAgain_andTheSameChoiceTwiceIsAskedOnce(@TempDir Path dir) throws Exception {
        Path state = dir.resolve("judged.tsv");
        Files.write(dir.resolve("choices.jsonl"), List.of(line("いる", "要る", "いる", "要る")));
        Review.run(dir, Judged.load(state), c -> Optional.of(false), 1);

        List<String> asked = new ArrayList<>();
        Files.write(dir.resolve("choices.jsonl"), List.of(line("いる", "要る", "これっているの", "これって要るの"),
                line("こう", "侯", "こう", "侯"), line("こう", "侯", "こうして", "侯して")));
        Review.Summary s = Review.run(dir, Judged.load(state), c -> {
            asked.add(c.chosen() + "/" + c.output());
            return Optional.of(true);
        }, 2);

        assertEquals(List.of("侯/侯して"), asked);   // the latest text of the choice
        assertEquals(new Review.Summary(2, 1, 1, 0), s);
    }

    @Test
    void aChoiceTheLlmDidNotAnswerIsKeptForTheNextRound(@TempDir Path dir) throws Exception {
        Path state = dir.resolve("judged.tsv");
        Files.write(dir.resolve("choices.jsonl"), List.of(line("そう", "宗", "そう", "宗")));

        Review.Summary first = Review.run(dir, Judged.load(state), c -> Optional.empty(), 1);
        Review.Summary second = Review.run(dir, Judged.load(state), c -> Optional.of(true), 2);
        Review.Summary third = Review.run(dir, Judged.load(state), c -> Optional.of(true), 3);

        assertEquals(new Review.Summary(1, 1, 0, 1), first);
        assertEquals(new Review.Summary(1, 1, 1, 0), second);
        assertEquals(new Review.Summary(0, 0, 0, 0), third);
        assertEquals(List.of("そう=宗"), suspects(dir));
    }

    @Test
    void theLlmIsAskedAtBackgroundPriorityWithoutThinking_andItsVerdictIsRead() throws Exception {
        AtomicReference<String> priority = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", ex -> {
            priority.set(ex.getRequestHeaders().getFirst("X-Job-Priority"));
            body.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] reply = ("{\"choices\":[{\"message\":{\"content\":"
                    + "\"{\\\"verdict\\\": \\\"drop\\\", \\\"reason\\\": \\\"x\\\"}\"}}]}").getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, reply.length);
            try (OutputStream out = ex.getResponseBody()) {
                out.write(reply);
            }
        });
        server.start();
        try {
            LlmJudge judge = new LlmJudge(URI.create("http://127.0.0.1:" + server.getAddress().getPort()
                    + "/v1/chat/completions"), "m");
            Optional<Boolean> slip = judge.isSlip(new Choice("もう", "望", "もうだめ", "望だめ"));

            assertEquals(Optional.of(true), slip);
            assertEquals("background", priority.get());
            JsonObject sent = JsonParser.parseString(body.get()).getAsJsonObject();
            assertFalse(sent.getAsJsonObject("chat_template_kwargs").get("enable_thinking").getAsBoolean());
            String user = sent.getAsJsonArray("messages").get(1).getAsJsonObject().get("content").getAsString();
            assertTrue(user.contains("whole sentence as committed: 望だめ"));
            assertTrue(user.contains("its reading: もうだめ"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void aServerThatDoesNotAnswerGivesNoVerdict() {
        LlmJudge judge = new LlmJudge(URI.create("http://127.0.0.1:1/v1/chat/completions"), "m");
        assertEquals(Optional.empty(), judge.isSlip(new Choice("もう", "望", "", "")));
    }

    @Test
    void theVerdictIsTheLastJsonObjectOfTheReply() {
        assertEquals(Optional.of(false), LlmJudge.verdictOf("ok {\"verdict\": \"keep\", \"reason\": \"fine\"}"));
        assertEquals(Optional.of(true), LlmJudge.verdictOf("```json\n{\"verdict\":\"drop\"}\n```"));
        assertEquals(Optional.empty(), LlmJudge.verdictOf("I think it is fine"));
        assertEquals(Optional.empty(), LlmJudge.verdictOf("{\"verdict\": \"maybe\"}"));
    }

    @Test
    void withoutItsTextTheModelIsToldTheSentenceIsUnknown() {
        assertTrue(LlmJudge.prompt(new Choice("し", "四", "", "")).endsWith("whole sentence: (unknown)"));
    }
}
