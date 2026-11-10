package com.github.oogasawa.slmimecompanion.chatuifeed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A round puts the turns recorded since the last round into the inbox, read from a copy of the log, and the next
 * round only what came after.
 */
class FeedTest {

    @TempDir
    Path dir;

    private static void write(Path db, String... messages) throws Exception {
        String name = db.toString();
        String url = "jdbc:h2:" + name.substring(0, name.length() - ".mv.db".length());
        try (Connection c = new org.h2.Driver().connect(url, new Properties()); Statement s = c.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS LOGS (ID BIGINT AUTO_INCREMENT PRIMARY KEY, TIMESTAMP TIMESTAMP, "
                    + "LABEL VARCHAR(100), MESSAGE CLOB)");
            try (PreparedStatement insert = c.prepareStatement(
                    "INSERT INTO LOGS (TIMESTAMP, LABEL, MESSAGE) VALUES (CURRENT_TIMESTAMP, ?, ?)")) {
                for (String m : messages) {
                    insert.setString(1, "turn1/conversation");
                    insert.setString(2, m);
                    insert.execute();
                }
                insert.setString(1, "turn1/step1/llm");
                insert.setString(2, "QUESTION: not a turn");
                insert.execute();
            }
        }
    }

    private static List<String> inboxLines(Path inbox) throws Exception {
        try (var files = Files.list(inbox)) {
            List<Path> all = files.toList();
            assertEquals(1, all.size(), "one file per round");
            assertTrue(all.get(0).getFileName().toString().endsWith(".jsonl"));
            assertEquals("rw-------", PosixFilePermissions.toString(Files.getPosixFilePermissions(all.get(0))));
            List<String> lines = Files.readAllLines(all.get(0), StandardCharsets.UTF_8);
            Files.delete(all.get(0)); // as the server does
            return lines;
        }
    }

    @Test
    void aRoundPutsTheNewTurnsIntoTheInbox() throws Exception {
        Path logs = Files.createDirectories(dir.resolve("works"));
        Path inbox = dir.resolve("slm-ime/learn");
        Path state = dir.resolve("chat-ui-to-slm-ime/state.tsv");
        Path db = logs.resolve("chat-ui-iolog-28010.mv.db");
        write(db, "QUESTION: どうなりましたか？\n\nANSWER: まだ学習の途中です。\n「待ち」です。");
        long modified = Files.getLastModifiedTime(db).toMillis();

        assertTrue(Feed.round(logs, inbox, state, 1000).startsWith("2 texts put in"));
        List<String> lines = inboxLines(inbox);
        assertEquals(2, lines.size());
        assertTrue(lines.get(0).startsWith("{\"text\": \"どうなりましたか？\", \"time\": "));
        assertTrue(lines.get(1).startsWith("{\"text\": \"まだ学習の途中です。\\n「待ち」です。\", \"time\": "));
        // the log itself was not opened
        assertEquals(modified, Files.getLastModifiedTime(db).toMillis());
        try (var left = Files.list(logs)) {
            assertEquals(List.of(db), left.toList());
        }

        // nothing new: no file
        assertEquals("no new texts", Feed.round(logs, inbox, state, 2000));
        // a turn more, and the first one again in the merged log of AI-workspace: only the new one
        write(db, "QUESTION: 続きを\nANSWER: はい。");
        write(logs.resolve("chat-ui-iolog-all.mv.db"), "QUESTION: どうなりましたか？\n\nANSWER: まだ学習の途中です。\n「待ち」です。");
        assertTrue(Feed.round(logs, inbox, state, 3000).startsWith("2 texts put in"));
        assertEquals(2, inboxLines(inbox).size());
        // without the logs, nothing, and no error
        assertEquals("no new texts", Feed.round(dir.resolve("gone"), inbox, state, 4000));
    }

    @Test
    void textsAreJsonStrings() {
        assertEquals("\"a\\\"b\\\\c\\nd\\te\\u0001\"", Inbox.quoted("a\"b\\c\nd\te\u0001"));
    }
}
