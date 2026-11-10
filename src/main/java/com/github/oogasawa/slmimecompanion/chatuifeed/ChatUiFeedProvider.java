package com.github.oogasawa.slmimecompanion.chatuifeed;

import com.scivicslab.pojoactor.core.ActorProvider;
import com.scivicslab.pojoactor.core.ActorSystem;

/** Registers the job of the plugin {@code chat-ui-feed}. */
public final class ChatUiFeedProvider implements ActorProvider {

    @Override
    public void registerActors(ActorSystem system) {
        system.actorOf(ChatUiFeedJob.NAME, new ChatUiFeedJob());
    }

    @Override
    public String getPluginName() {
        return ChatUiFeedJob.NAME;
    }
}
