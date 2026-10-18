# dev-tests

Plain-Java checks for the logic of the 3.7.1 features that does not need a server (no JUnit, no Gradle, no Bukkit):

* `MessageMigrationTest` - legacy / partially migrated / fully migrated language files, idempotency, entry parsing, validator
* `PreloadTest` - `PreloadChunkPosition` parsing and validation, chunk area math, position pool, persistence of damaged files, chunk ticket counting

Run everything with `dev-tests/run.sh` (needs a JDK or a JRE that ships the `jdk.compiler` module).
`fixtures/legacy-en.yml` is the 3.6.x `en.yml`, `convert_lang.py` is the one-off script that converted the bundled language files.

What these tests cannot cover: anything that talks to Bukkit/Paper (sending titles/action bars, chunk generation, chunk tickets,
reading and writing the YAML files through Bukkit). That needs a real server, see the notes in the release summary.
