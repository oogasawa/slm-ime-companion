package com.github.oogasawa.slmimecompanion.historyreview;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * One round of the review of the user history of fcitx5-slm-ime.
 *
 * <ol>
 *   <li>Takes the choices the server noted since the last round: renames {@code choices.jsonl} of the review
 *       directory to {@code choices-<time>.taking} (the server then starts a new file) and reads every
 *       {@code choices-*.taking}, including those a failed round left.</li>
 *   <li>Asks the LLM about each choice not judged before ({@link Judged}), once per reading and text.</li>
 *   <li>Puts the choices it takes for slips into {@code suspects/<time>.jsonl}, which the server reads and forgets
 *       those made only once; records every verdict; keeps the choices the LLM did not answer for the next round.</li>
 * </ol>
 *
 * <p>{@link HistoryReviewJob} runs it.</p>
 */
public final class Review {

    static final String DEFAULT_URL = "http://127.0.0.1:8000/v1/chat/completions";

    /** What a round did. */
    public record Summary(int taken, int asked, int slips, int unanswered) {
    }

    private Review() {
    }

    /** One round with the given judge; {@code now} names the files it writes. */
    public static Summary run(Path dir, Judged judged, Function<Choice, Optional<Boolean>> judge, long now) throws IOException {
        Path current = dir.resolve("choices.jsonl");
        if (Files.exists(current)) {
            Files.move(current, dir.resolve("choices-" + now + ".taking"), StandardCopyOption.ATOMIC_MOVE);
            sleepQuietly(1000);   // a line the server was appending at that moment lands in the renamed file
        }
        List<Path> taking = new ArrayList<>();
        if (Files.isDirectory(dir)) {
            try (Stream<Path> found = Files.list(dir)) {
                found.filter(p -> p.getFileName().toString().startsWith("choices-")
                        && p.getFileName().toString().endsWith(".taking")).sorted().forEach(taking::add);
            }
        }
        // The latest text of each choice: the same reading and text are asked once
        Map<String, Choice> choices = new LinkedHashMap<>();
        for (Path file : taking) {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                Choice c = Choice.parse(line);
                if (c != null) {
                    choices.remove(c.key());
                    choices.put(c.key(), c);
                }
            }
        }
        List<Choice> slips = new ArrayList<>();
        List<Choice> unanswered = new ArrayList<>();
        int asked = 0;
        for (Choice c : choices.values()) {
            if (judged.contains(c)) {
                continue;
            }
            asked++;
            Optional<Boolean> slip = judge.apply(c);
            if (slip.isEmpty()) {
                unanswered.add(c);
                continue;
            }
            if (slip.get()) {
                slips.add(c);
            }
            judged.add(c, slip.get());
        }
        if (!slips.isEmpty()) {
            Path inbox = Files.createDirectories(dir.resolve("suspects"));
            Path temporary = inbox.resolve(now + ".jsonl.tmp");
            Files.write(temporary, slips.stream().map(Choice::suspectLine).toList(), StandardCharsets.UTF_8);
            Files.move(temporary, inbox.resolve(now + ".jsonl"), StandardCopyOption.ATOMIC_MOVE);
        }
        if (!unanswered.isEmpty()) {
            Path temporary = dir.resolve("choices-" + now + "-retry.tmp");
            Files.write(temporary, unanswered.stream().map(Choice::choiceLine).toList(), StandardCharsets.UTF_8);
            Files.move(temporary, dir.resolve("choices-" + now + "-retry.taking"), StandardCopyOption.ATOMIC_MOVE);
        }
        for (Path file : taking) {
            Files.delete(file);
        }
        return new Summary(choices.size(), asked, slips.size(), unanswered.size());
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
