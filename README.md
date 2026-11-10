# slm-ime-companion

Jobs that work beside the conversion server of [fcitx5-slm-ime](https://github.com/oogasawa/fcitx5-slm-ime), each a
plugin, run on their schedules by one process.

The companion and the server meet only in directories under `$XDG_DATA_HOME/slm-ime`: the server knows nothing of
how a job works, and a job knows nothing of the server but the form of those directories.

## Plugins

| Name | What one round does |
|---|---|
| `chat-ui-feed` | Reads the conversation logs of chat-ui (`chat-ui-iolog-*.mv.db`, from copies) and puts the new texts, what the user wrote and what the model answered, into the inbox of the predictions (`slm-ime/learn`). |
| `history-review` | Reads the choices the server's user history learned (`slm-ime/review/choices.jsonl`, each with the whole text it was chosen in), asks a local LLM about each choice not judged before whether it is a slip (a candidate taken by mistake), and hands those to the server (`slm-ime/review/suspects/`). The server forgets a suspected choice if the user made it only once; made again, it is learned again. The LLM is asked through an OpenAI-compatible endpoint at background priority (`X-Job-Priority: background`) and without its thinking. |

A plugin is an `ActorProvider` of [POJO-actor](https://github.com/scivicslab/POJO-actor), found by the service
loader, that registers under its plugin name an actor whose object is a `Job` (`configure(Settings)`, `runOnce()`).
The rounds of a job are messages to its actor, so a job never runs two rounds at once.

## Configuration

`~/.config/slm-ime-companion/companion.properties` names the plugins to run, the schedule of each (`every <n>m`,
`every <n>h` or `daily HH:MM`) and its settings, `<plugin>.<key>=<value>`. See `companion.properties.example`.
A name that no plugin has, or a plugin without a setting it needs, stops the start.

## Build and install

```bash
rm -rf target && mvn package
mkdir -p ~/.local/lib/slm-ime-companion ~/.config/slm-ime-companion ~/.config/systemd/user
cp target/slm-ime-companion.jar ~/.local/lib/slm-ime-companion/
cp companion.properties.example ~/.config/slm-ime-companion/companion.properties   # then edit it
cp systemd/slm-ime-companion.service ~/.config/systemd/user/
systemctl --user daemon-reload
systemctl --user enable --now slm-ime-companion.service
```

`java -jar slm-ime-companion.jar --list` prints the plugins found; `--once <name>` runs one round of one plugin now.

## License

Apache License 2.0.
