package com.github.oogasawa.slmimecompanion.historyreview;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

/**
 * The choices already judged, so that none is asked twice: a file of lines {@code reading TAB chosen TAB keep|drop
 * TAB date}, appended to. A choice the server forgot and the user made again is not asked again: the second time
 * shows it was meant.
 */
final class Judged {

    private final Path file;
    private final Set<String> keys = new HashSet<>();

    private Judged(Path file) {
        this.file = file;
    }

    static Judged load(Path file) throws IOException {
        Judged judged = new Judged(file);
        if (Files.exists(file)) {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String[] f = line.split("\t", -1);
                if (f.length >= 2 && !f[0].isEmpty()) {
                    judged.keys.add(f[0] + "\t" + f[1]);
                }
            }
        }
        return judged;
    }

    boolean contains(Choice c) {
        return keys.contains(c.key());
    }

    void add(Choice c, boolean slip) throws IOException {
        if (!keys.add(c.key())) {
            return;
        }
        Files.createDirectories(file.toAbsolutePath().getParent());
        Files.writeString(file, c.key() + "\t" + (slip ? "drop" : "keep") + "\t" + LocalDate.now() + "\n",
                StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    int size() {
        return keys.size();
    }
}
