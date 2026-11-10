package com.github.oogasawa.slmimecompanion.chatuifeed;

import java.nio.file.Path;
import java.nio.file.Paths;

import com.github.oogasawa.slmimecompanion.Job;
import com.github.oogasawa.slmimecompanion.Settings;

/**
 * The plugin {@code chat-ui-feed}: puts what the user wrote and what the model answered in the conversation logs of
 * chat-ui into the inbox of the predictions of the server ({@link Feed}). Settings: {@code logs} (where the
 * {@code chat-ui-iolog-*.mv.db} files are, 3 levels down; default {@code ~/works}), {@code inbox} (default
 * {@code $XDG_DATA_HOME/slm-ime/learn}), {@code state} (where each log was read to).
 */
public final class ChatUiFeedJob implements Job {

    public static final String NAME = "chat-ui-feed";

    private Path logs;
    private Path inbox;
    private Path state;

    @Override
    public void configure(Settings settings) {
        Path data = Settings.dataHome();
        logs = settings.path("logs", Paths.get(System.getProperty("user.home"), "works"));
        inbox = settings.path("inbox", data.resolve("slm-ime").resolve("learn"));
        state = settings.path("state", data.resolve("slm-ime-companion").resolve(NAME).resolve("state.tsv"));
    }

    @Override
    public String runOnce() throws Exception {
        return Feed.round(logs, inbox, state, System.currentTimeMillis());
    }
}
