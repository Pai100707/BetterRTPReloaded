<p align="center">
  <b><a>Welcome to BetterRTPReloaded's repository!</a></b>
</p>

## Where's the Lang files?/Want to Contribute translating?  
All language files are located [here](src/main/resources/lang)
feel free to fork one of the language files and help translate!

## What's new

### 3.7.2
**Fixes**
- `/rtp queue` no longer crashes when it is run from the console (`TerminalConsoleCommandSender cannot be cast to Player`). It now works for everyone.
- `/rtp queue [world]` shows the prepared positions read from `plugins/BetterRTP/preload-positions.yml`: `x`, `y`, `z` per world and how many of the configured `positions` are ready.

**config.yml**
- `config-version` (**do not change it**) - the layout version of the file (3.7.2 = `372`, 3.8.1 = `381`, parts never go above 9). On startup an older `config.yml` is updated automatically and a backup is saved as `config.yml.pre-3.7.2.bak`. A config without the key is treated as 3.6.14. Nothing you configured changes behavior: lists that were in use stay enabled.
- `Overrides` and `DisabledWorlds` got an `enable` switch (default `false`) and a `list`. The examples (`- master_world: 'world'`, `- creative_world: 'world'`, `- prison`, `- creative`) are commented out.
- `CustomWorlds.<world>.enable` - `true` uses the custom settings of that world, `false` uses the `Default` settings. Works for every world including `world`, `world_nether` and `world_the_end` (examples are included, switched off).
- `BlacklistedBlocks` now contains `powder_snow` (added to existing configs by the update).
- `/rtp edit override ...` writes to the new layout and switches `Overrides` on when an override is added.

### 3.7.1
- **Message delivery types** - every message in the language files can be sent as `chat`, `actionbar` and/or `title` (with an independent `subtitle`), in any combination. Placeholders and `&` color codes work in all of them. Only chat is enabled by default.
- **Automatic language file migration** - files from 3.6.x (one plain string per message) are converted on startup. Customized texts are kept and the original is saved as `<name>.pre-3.7.1.bak`.
- **Pre-generated RTP positions** - the `PreloadChunkPosition` section keeps a pool of safe positions per world and generates the chunks around them in the background so `/rtp` does not wait for terrain generation. Positions are saved in `preload-positions.yml` and survive restarts. `keep-loaded` optionally keeps those chunks loaded (Paper only).

## Libraries
BetterRTP uses and is compiled with the following libraries:

- [ParticleLib](https://github.com/ByteZ1337/ParticleLib) (included) - Particles library by ByteZ1337. Find all supported particles [here](https://github.com/ByteZ1337/ParticleLib/blob/master/src/main/java/xyz/xenondevs/particle/ParticleEffect.java)
- [PaperLib](https://github.com/PaperMC/PaperLib) (included) - Library for interfacing with PaperMC specific APIs, used for async chunk loading.
- [FoliaLib](https://github.com/TechnicallyCoded/FoliaLib) (included) - Library for interfacing with Folia specific APIs, used for cross-platform timers.

## Where's the Wiki?  
The wiki is available [here](../../wiki)!
    
<p align="center">
  <b>Java version</b><br/>
  <a href="https://www.oracle.com/java/technologies/downloads/#java21">
    <img alt="Java 21" src="https://img.shields.io/badge/Java-21-007396?style=for-the-badge&logo=openjdk">
  </a><br/>
  <b>Chat with us on Discord</b><br/>
  <a href="https://discord.gg/9upjqmswFY"><img src="https://img.shields.io/discord/1500006030787088404.svg?longCache=true&style=flat-square&label=Discord" alt="Discord" /></a><br/>
  <b>Have a Suggestion? Make an issue!</b><br/>
  <a href="../../issues"><img src="https://img.shields.io/github/issues-raw/Pai100707/BetterRTPReloaded.svg?longCache=true&style=flat-square&label=Issues" alt="GitHub issues" /></a><br/>
  <br/>
  <i><a>Did this wiki help you out? Please give it a <b>Star</b> so I know it's getting use!</a></i><br/>
</p>
