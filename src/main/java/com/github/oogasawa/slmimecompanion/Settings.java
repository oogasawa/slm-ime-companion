package com.github.oogasawa.slmimecompanion;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

/** The settings of one plugin, with the directories every plugin uses. */
public record Settings(Map<String, String> values) {

    public String get(String key, String otherwise) {
        String v = values.get(key);
        return v == null || v.isBlank() ? otherwise : v.trim();
    }

    public Path path(String key, Path otherwise) {
        String v = values.get(key);
        return v == null || v.isBlank() ? otherwise : Paths.get(expand(v.trim()));
    }

    /** {@code $XDG_DATA_HOME}, or {@code ~/.local/share}. */
    public static Path dataHome() {
        String data = System.getenv("XDG_DATA_HOME");
        return data == null || data.isBlank() ? Paths.get(System.getProperty("user.home"), ".local", "share")
                : Paths.get(data);
    }

    /** {@code ~} at the start of a path is the home directory. */
    static String expand(String path) {
        return path.equals("~") || path.startsWith("~/") ? System.getProperty("user.home") + path.substring(1) : path;
    }
}
