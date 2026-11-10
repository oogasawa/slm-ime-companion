package com.github.oogasawa.slmimecompanion;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.ServiceLoader;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import com.scivicslab.pojoactor.core.ActorProvider;
import com.scivicslab.pojoactor.core.ActorRef;
import com.scivicslab.pojoactor.core.ActorSystem;
import com.scivicslab.pojoactor.core.scheduler.Scheduler;

/**
 * Runs the jobs that work beside the conversion server of fcitx5-slm-ime, each a plugin: an
 * {@link ActorProvider} found by the service loader, which registers a {@link Job} under its plugin name. The
 * configuration ({@code ~/.config/slm-ime-companion/companion.properties}) names the plugins to run and gives each
 * its schedule and settings:
 *
 * <pre>
 * plugins=chat-ui-feed,history-review
 * chat-ui-feed.schedule=every 60m
 * history-review.schedule=daily 03:30
 * history-review.model=qwen3.8-flash-next-iq3_xxs-radeon
 * </pre>
 *
 * A plugin that is found but not named is not started. The companion and the server meet only in directories, so
 * neither knows how the other works.
 *
 * <pre>
 * java -jar slm-ime-companion.jar [--config FILE]            run the jobs on their schedules
 * java -jar slm-ime-companion.jar [--config FILE] --once NAME run one round of one plugin now
 * java -jar slm-ime-companion.jar --list                     the plugins that are found
 * </pre>
 */
public final class Companion {

    private static final Logger log = Logger.getLogger(Companion.class.getName());

    private Companion() {
    }

    public static void main(String[] args) throws Exception {
        Path config = Paths.get(System.getProperty("user.home"), ".config", "slm-ime-companion", "companion.properties");
        String once = null;
        boolean list = false;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--config" -> config = Paths.get(args[++i]);
                case "--once" -> once = args[++i];
                case "--list" -> list = true;
                default -> {
                    System.err.println("usage: slm-ime-companion [--config FILE] [--once NAME | --list]");
                    System.exit(2);
                }
            }
        }
        Map<String, ActorProvider> found = providers();
        if (list) {
            found.forEach((name, p) -> System.out.println(name + " " + p.getPluginVersion()));
            return;
        }
        Properties properties = load(config);
        List<String> names = once != null ? List.of(once) : enabled(properties);
        ActorSystem system = new ActorSystem("slm-ime-companion");
        Map<String, ActorRef<Job>> jobs = start(system, found, names, properties);
        if (once != null) {
            String did = jobs.get(once).ask(j -> {
                try {
                    return j.runOnce();
                } catch (Exception e) {
                    return "the round failed: " + e;
                }
            }).join();
            System.out.println(once + ": " + did);
            system.terminate();
            return;
        }
        Scheduler scheduler = new Scheduler();
        LocalDateTime now = LocalDateTime.now();
        for (Map.Entry<String, ActorRef<Job>> job : jobs.entrySet()) {
            String name = job.getKey();
            Schedule s = Schedule.parse(properties.getProperty(name + ".schedule", "every 60m"), now);
            scheduler.scheduleAtFixedRate(name, job.getValue(), j -> round(name, j),
                    s.firstAfter().toMillis(), s.period().toMillis(), TimeUnit.MILLISECONDS);
            log.info(name + ": first round in " + s.firstAfter().toMinutes() + " min, then every "
                    + s.period().toMinutes() + " min");
        }
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            scheduler.close();
            system.terminate();
        }));
        Thread.currentThread().join();
    }

    /** The plugins the service loader finds, by name. */
    static Map<String, ActorProvider> providers() {
        Map<String, ActorProvider> found = new LinkedHashMap<>();
        for (ActorProvider p : ServiceLoader.load(ActorProvider.class)) {
            found.put(p.getPluginName(), p);
        }
        return found;
    }

    /**
     * Registers the named plugins in {@code system} and gives each job its settings. A name that no plugin has, or a
     * plugin that registers no job under its name, stops the start: a mistyped name must not pass unseen.
     */
    static Map<String, ActorRef<Job>> start(ActorSystem system, Map<String, ActorProvider> found, List<String> names,
                                            Properties properties) {
        Map<String, ActorRef<Job>> jobs = new LinkedHashMap<>();
        for (String name : names) {
            ActorProvider provider = found.get(name);
            if (provider == null) {
                throw new IllegalArgumentException("no plugin named " + name + "; found: " + found.keySet());
            }
            provider.registerActors(system);
            if (!system.hasActor(name)) {
                throw new IllegalStateException("the plugin " + name + " registered no job named " + name);
            }
            ActorRef<Job> ref = system.getActor(name);
            Settings settings = new Settings(settingsOf(properties, name));
            ref.ask(j -> {
                j.configure(settings);
                return null;
            }).join();
            jobs.put(name, ref);
        }
        return jobs;
    }

    static List<String> enabled(Properties properties) {
        List<String> names = new ArrayList<>();
        for (String n : properties.getProperty("plugins", "").split(",")) {
            if (!n.isBlank()) {
                names.add(n.trim());
            }
        }
        return names;
    }

    static Map<String, String> settingsOf(Properties properties, String name) {
        Map<String, String> values = new HashMap<>();
        String prefix = name + ".";
        for (String key : properties.stringPropertyNames()) {
            if (key.startsWith(prefix)) {
                values.put(key.substring(prefix.length()), properties.getProperty(key));
            }
        }
        return values;
    }

    private static Properties load(Path config) throws IOException {
        Properties properties = new Properties();
        if (Files.exists(config)) {
            try (Reader r = Files.newBufferedReader(config, StandardCharsets.UTF_8)) {
                properties.load(r);
            }
        } else {
            log.warning(config + " does not exist: no plugin runs");
        }
        return properties;
    }

    /** One round of a job; a round that fails is logged and the next one runs on time. */
    private static void round(String name, Job job) {
        try {
            log.info(name + ": " + job.runOnce());
        } catch (Exception e) {
            log.warning(name + ": the round failed: " + e);
        }
    }
}
