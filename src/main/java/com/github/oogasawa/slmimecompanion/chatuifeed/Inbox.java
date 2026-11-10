package com.github.oogasawa.slmimecompanion.chatuifeed;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;

/**
 * The inbox of the predictions of fcitx5-slm-ime: a directory where a file {@code *.jsonl} of
 * {@code {"text": "...", "time": <milliseconds since 1970>}} lines is a set of texts for the predictions to learn.
 * The file is written under another name and then renamed, so that the server never reads half of it; it is
 * readable by the user only, as the texts are what the user wrote.
 */
final class Inbox {

    private final Path dir;

    Inbox(Path dir) {
        this.dir = dir;
    }

    /** Writes the texts in one new file of the inbox; nothing when there are none. The file written, or null. */
    Path put(List<ConversationLogs.Said> said, long now) throws IOException {
        if (said.isEmpty()) {
            return null;
        }
        Files.createDirectories(dir, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        String name = "chat-ui-" + now;
        Path tmp = dir.resolve(name + ".jsonl.tmp");
        Files.deleteIfExists(tmp);
        Files.createFile(tmp, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        try (BufferedWriter out = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
            for (ConversationLogs.Said s : said) {
                out.write("{\"text\": " + quoted(s.text()) + ", \"time\": " + s.when() + "}\n");
            }
        }
        Path file = dir.resolve(name + ".jsonl");
        Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE);
        return file;
    }

    /** A JSON string. */
    static String quoted(String s) {
        StringBuilder out = new StringBuilder(s.length() + 2).append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }
}
