package com.github.oogasawa.slmimecompanion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.scivicslab.pojoactor.core.ActorRef;
import com.scivicslab.pojoactor.core.ActorSystem;

class CompanionTest {

    @Test
    void bothPluginsAreFoundByTheServiceLoader() {
        assertEquals(List.of("chat-ui-feed", "history-review"), List.copyOf(Companion.providers().keySet()));
    }

    @Test
    void onlyTheNamedPluginsAreStarted_andEachGetsItsOwnSettings(@TempDir Path dir) throws Exception {
        Properties p = new Properties();
        p.setProperty("plugins", " history-review ");
        p.setProperty("history-review.review", dir.toString());
        p.setProperty("history-review.state", dir.resolve("judged.tsv").toString());
        p.setProperty("history-review.url", "http://127.0.0.1:1/v1/chat/completions");
        p.setProperty("history-review.model", "m");
        p.setProperty("chat-ui-feed.logs", "/nowhere");
        Files.writeString(dir.resolve("choices.jsonl"), "{\"reading\":\"もう\",\"chosen\":\"望\"}\n");
        ActorSystem system = new ActorSystem("test");
        try {
            Map<String, ActorRef<Job>> jobs = Companion.start(system, Companion.providers(),
                    Companion.enabled(p), p);

            assertEquals(List.of("history-review"), List.copyOf(jobs.keySet()));
            assertFalse(system.hasActor("chat-ui-feed"));
            // the settings reached the job: it read this directory and asked the (dead) endpoint, which did not answer
            String did = jobs.get("history-review").ask(j -> {
                try {
                    return j.runOnce();
                } catch (Exception e) {
                    return e.toString();
                }
            }).join();
            assertEquals("1 choices taken, 1 asked, 0 suspected and handed to the server, 1 left for the next round", did);
        } finally {
            system.terminate();
        }
    }

    @Test
    void aNameNoPluginHasStopsTheStart() {
        Properties p = new Properties();
        ActorSystem system = new ActorSystem("test");
        try {
            assertThrows(IllegalArgumentException.class,
                    () -> Companion.start(system, Companion.providers(), List.of("history-reveiw"), p));
        } finally {
            system.terminate();
        }
    }

    @Test
    void aPluginWithoutTheSettingItNeedsStopsTheStart() {
        Properties p = new Properties();
        ActorSystem system = new ActorSystem("test");
        try {
            Exception e = assertThrows(RuntimeException.class,
                    () -> Companion.start(system, Companion.providers(), List.of("history-review"), p));
            assertTrue(String.valueOf(e.getMessage()).contains("history-review.model is not set"), e.toString());
        } finally {
            system.terminate();
        }
    }

    @Test
    void theSettingsOfAPluginAreItsLinesWithoutThePrefix() {
        Properties p = new Properties();
        p.setProperty("history-review.model", "m");
        p.setProperty("history-review-x.model", "other");
        p.setProperty("plugins", "history-review");
        assertEquals(Map.of("model", "m"), Companion.settingsOf(p, "history-review"));
    }

    @Test
    void schedules() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 10, 22, 0);
        assertEquals(new Schedule(Duration.ZERO, Duration.ofMinutes(60)), Schedule.parse("every 60m", now));
        assertEquals(new Schedule(Duration.ZERO, Duration.ofHours(2)), Schedule.parse(" every  2h ", now));
        assertEquals(new Schedule(Duration.ofMinutes(330), Duration.ofDays(1)), Schedule.parse("daily 03:30", now));
        assertEquals(new Schedule(Duration.ofMinutes(30), Duration.ofDays(1)), Schedule.parse("daily 22:30", now));
        assertEquals(new Schedule(Duration.ofDays(1), Duration.ofDays(1)), Schedule.parse("daily 22:00", now));
        assertThrows(IllegalArgumentException.class, () -> Schedule.parse("hourly", now));
        assertThrows(IllegalArgumentException.class, () -> Schedule.parse("every 0m", now));
    }

    @Test
    void aTildeIsTheHomeDirectory() {
        assertTrue(Settings.expand("~/works").endsWith("/works"));
        assertFalse(Settings.expand("~/works").startsWith("~"));
        assertEquals("/tmp/~x", Settings.expand("/tmp/~x"));
    }
}
