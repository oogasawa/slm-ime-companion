package com.github.oogasawa.slmimecompanion.chatuifeed;

import java.nio.file.Path;
import java.util.List;

/**
 * One round: the turns that the conversation logs of chat-ui recorded since the last round (all of them at the
 * first), what the user wrote and what the model answered, put into the inbox of the predictions of
 * fcitx5-slm-ime as one file ({@link ChatUiFeedJob} runs it).
 *
 * The logs are read in copies and never opened where they are, and the program knows nothing of fcitx5-slm-ime
 * but the form of its inbox; neither chat-ui nor fcitx5-slm-ime knows of it.
 */
public final class Feed {

    /** Puts the new texts of the logs into the inbox and keeps where the logs were read to; what it did. */
    public static String round(Path logs, Path inbox, Path state, long now) throws Exception {
        ConversationLogs conversations = new ConversationLogs(logs);
        conversations.loadState(state);
        List<ConversationLogs.Said> said = conversations.newTexts();
        // the texts first, then where the logs were read to: a round that stops between the two puts the texts in
        // again next time rather than losing them
        Path file = new Inbox(inbox).put(said, now);
        conversations.saveState(state);
        return file == null ? "no new texts" : said.size() + " texts put in " + file;
    }
}
