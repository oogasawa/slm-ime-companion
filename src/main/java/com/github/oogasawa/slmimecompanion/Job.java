package com.github.oogasawa.slmimecompanion;

/**
 * The work of one plugin. A plugin registers, under its name, an actor whose object is a {@code Job}
 * ({@link com.scivicslab.pojoactor.core.ActorProvider#registerActors}); the companion gives it its settings once and
 * then runs it on its schedule. One round at a time: the rounds of a job are messages to its actor.
 */
public interface Job {

    /** The settings of the plugin: the lines {@code <plugin name>.<key>=<value>} of the configuration, without the prefix. */
    void configure(Settings settings);

    /** One round; what it did, in one line for the log. */
    String runOnce() throws Exception;
}
