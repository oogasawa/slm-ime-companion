package com.github.oogasawa.slmimecompanion.historyreview;

import java.net.URI;
import java.nio.file.Path;

import com.github.oogasawa.slmimecompanion.Job;
import com.github.oogasawa.slmimecompanion.Settings;

/**
 * The plugin {@code history-review}: asks a local LLM which choices of the user history are slips and hands them to
 * the server to forget ({@link Review}). Settings: {@code review} (the review directory of the server; default
 * {@code $XDG_DATA_HOME/slm-ime/review}), {@code state} (the choices judged), {@code url} (an OpenAI-compatible chat
 * endpoint, such as a GPU broker; default {@code http://127.0.0.1:8000/v1/chat/completions}) and {@code model} (the
 * model to ask; required).
 */
public final class HistoryReviewJob implements Job {

    public static final String NAME = "history-review";

    private Path dir;
    private Path state;
    private LlmJudge judge;

    @Override
    public void configure(Settings settings) {
        Path data = Settings.dataHome();
        dir = settings.path("review", data.resolve("slm-ime").resolve("review"));
        state = settings.path("state", data.resolve("slm-ime-companion").resolve(NAME).resolve("judged.tsv"));
        String model = settings.get("model", "");
        if (model.isEmpty()) {
            throw new IllegalArgumentException(NAME + ".model is not set: the model to ask");
        }
        judge = new LlmJudge(URI.create(settings.get("url", Review.DEFAULT_URL)), model);
    }

    @Override
    public String runOnce() throws Exception {
        Review.Summary s = Review.run(dir, Judged.load(state), judge::isSlip, System.currentTimeMillis());
        return s.taken() + " choices taken, " + s.asked() + " asked, " + s.slips()
                + " suspected and handed to the server, " + s.unanswered() + " left for the next round";
    }
}
