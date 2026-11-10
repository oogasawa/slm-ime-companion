package com.github.oogasawa.slmimecompanion.historyreview;

import com.scivicslab.pojoactor.core.ActorProvider;
import com.scivicslab.pojoactor.core.ActorSystem;

/** Registers the job of the plugin {@code history-review}. */
public final class HistoryReviewProvider implements ActorProvider {

    @Override
    public void registerActors(ActorSystem system) {
        system.actorOf(HistoryReviewJob.NAME, new HistoryReviewJob());
    }

    @Override
    public String getPluginName() {
        return HistoryReviewJob.NAME;
    }
}
