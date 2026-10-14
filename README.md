# Server Minigames

Three minigames for a Minecraft 26.3 Fabric server. Players do not need a client mod.

| Game | How to win |
| --- | --- |
| **Sound Guess** | Be the first to identify a Minecraft sound in chat. |
| **Hide and Seek** | Find the hidden player before time runs out, or stay hidden until the end. |
| **Item Race** | Be the first to collect every item on a shared random list. |

## Installation

You need a dedicated Minecraft **26.3** server, **Java 25**, **Fabric Loader 0.19.5**, and **Fabric API 0.162.0+26.3**.

1. Install JDK 25 and set `JAVA_HOME` to its folder.
2. Build the mod from this repository:

   ```powershell
   # Windows
   .\gradlew.bat build
   ```

   ```sh
   # Linux / macOS
   ./gradlew build
   ```

3. Copy `build/libs/server-minigames-1.4.1.jar` and the matching Fabric API JAR into your server's `mods/` folder.
4. Restart the server. When updating, replace the old mod JAR. Do not install the `-sources.jar`.

## Getting started

Use `/minigame` to list the games, or `/minigame <game>` for help.

All `start` and `stop` commands require operator level 2 or the server console. Player commands are available to everyone. In the console, omit the leading `/`.

All mod messages, hints, revealed answers, and Item Race item names are displayed in English, regardless of the client's language. Commands and Sound Guess answers accept both English and German input.

Sound Guess and Item Race support `easy`, `normal`, `hard`, and `mixed` difficulty. `mixed` combines all difficulty pools. The German names `leicht`, `normal`, `schwer`, and `gemischt` also work.

| English command | German alias |
| --- | --- |
| `join` (Sound Guess) | `beitreten` |
| `leave` (all games) | `verlassen` |
| `hint` (Sound Guess) | `tipp` |
| `replay` (Sound Guess) | `nochmal` |
| `vote` (Sound Guess) | `abstimmen` |
| `ready` (Hide and Seek) | `bereit` |
| `list` (Item Race) | `liste` |

## Sound Guess

Start a game, then let players join during the signup period:

```text
/minigame sound start
/minigame sound join
```

Everyone who joined hears the same sound. Type your answer in public chat, such as `cow`. The first correct answer wins the round. English and German answers are accepted; capitalization and punctuation do not matter. Commands such as `/msg` do not count.

For five hard rounds:

```text
/minigame sound start hard 5
```

Players stay in the game between rounds. The answer is revealed when a round ends, and the next sound plays after a short countdown. The catalog contains 44 sounds, shuffled without repeating until the selected pool has been used.

| Command | Action |
| --- | --- |
| `/minigame sound start [signup_seconds] [guess_seconds]` | Start one easy round. |
| `/minigame sound start <difficulty> [rounds] [signup_seconds] [guess_seconds] [countdown_seconds]` | Start a series with custom settings. |
| `/minigame sound join` | Join during signup. |
| `/minigame sound leave` | Leave the game. |
| `/minigame sound hint` | Get a private hint. |
| `/minigame sound replay` | Hear the sound again privately; five-second cooldown. |
| `/minigame sound vote` | Vote to replay the sound for everyone; more than half the players must agree. |
| `/minigame sound status` | Show the current round and remaining time. |
| `/minigame sound stop` | Cancel the entire game. |

| Setting | Default | Allowed range |
| --- | --- | --- |
| Difficulty | `easy` | `easy`, `normal`, `hard`, `mixed` |
| Rounds | 1 | 1–100 |
| Signup time | 15 seconds | 1–300 seconds |
| Guess time | 60 seconds | 1–600 seconds |
| Countdown between rounds | 10 seconds | 1–60 seconds |

Hints and replay votes reset for each sound. You cannot join after signup closes. If everyone leaves after the game starts, it ends. Agree on subtitle and resource-pack settings beforehand, since they can reveal or change sounds.

## Hide and Seek

Choose two players: Alice seeks and Bob hides.

```text
/minigame hideandseek start Alice Bob
```

Both players must be alive, awake, not riding anything, and in the same dimension. Survival and Adventure modes are allowed.

Bob has 60 seconds to start the hiding phase:

```text
/minigame hideandseek ready
```

Bob then has **30 seconds** to hide. Alice waits at the starting position with Blindness and cannot see Bob's character during this phase. Afterward, Alice has **180 seconds** to find him. Bob's character becomes visible, but his name tag and location marker stay hidden.

Alice wins by left- or right-clicking Bob from at most three blocks away, with no wall between them. These interactions do not deal combat damage. If time runs out, Bob wins.

| Command | Action |
| --- | --- |
| `/minigame hideandseek start <seeker> <hider> [hide_seconds] [seek_seconds]` | Select the players and wait for the hider to be ready. |
| `/minigame hideandseek ready` | Start hiding as the selected hider. |
| `/minigame hideandseek status` | Show roles, phase, and remaining time. |
| `/minigame hideandseek leave` | Cancel the round as a participant. |
| `/minigame hideandseek stop` | Cancel the round as an operator. |

Hiding time can be **1–300 seconds**; seeking time can be **1–1800 seconds**. For example:

```text
/minigame hideandseek start Alice Bob 45 240
```

Leaving, disconnecting, dying, changing dimension, sleeping, riding, or switching to an unsupported game mode cancels the round. Temporary effects and visibility changes are removed afterward. Players choose their own play area; no arena is created.

## Item Race

Start a race with ten normal items:

```text
/minigame itemrace start Alice Bob 10 normal
```

Both players must be online, alive, and in Survival mode. The race starts immediately, and both receive the **same random list** without duplicates. Collect one of each item to win.

Without settings, the game uses **five easy items**. You can also choose only a difficulty:

```text
/minigame itemrace start Alice Bob
/minigame itemrace start Alice Bob hard
```

| Command | Action |
| --- | --- |
| `/minigame itemrace start <player1> <player2> [item_count] [difficulty]` | Start a race with 1–30 items. |
| `/minigame itemrace list` | Show your list and checked-off items. |
| `/minigame itemrace status` | Show both players' progress. |
| `/minigame itemrace leave` | Cancel the race as a participant. |
| `/minigame itemrace stop` | Cancel the race as an operator. |

Each difficulty pool contains 36 items; `mixed` uses all 108. Easy items are common materials and simple crafts. Normal items need more mining, farming, or crafting. Hard items can require the Nether, the End, or boss fights. All targets are obtainable in vanilla Survival, but the selection does not check your world's progress or nearby resources.

- **Existing items count.** The game checks your inventory, hotbar, offhand, and equipped items at the end of each server tick.
- **Progress stays checked off.** You can use, drop, or lose an item after collecting it. You do not need every item at once.
- **Stored contents do not count.** Take items out of chests, bundles, or shulker boxes and put them in your inventory. Items held on the inventory cursor do not count either.
- **Death does not reset progress.** Respawning, changing dimension, sleeping, and riding are allowed.
- **Leaving cancels the race.** Disconnecting or leaving Survival mode also cancels it without a winner.
- **Same-tick finishes are a draw.** If both players complete their lists in the same server tick, neither wins.

There is no time limit. For equal starting conditions, put away existing items before starting.

## Development

Run `./gradlew test` or `.\gradlew.bat test` to run the automated tests. They cover game rules, difficulty pools, commands, Minecraft sound and item IDs, inventory checks, and Hide and Seek visibility settings. A live test with two players is still needed.

Source code is in `src/main/java/de/vali/minigames/`. To add a game, implement `api.ServerMinigame` and register it in `ServerMinigamesMod`. The `game` package contains the rules; `sound`, `hideandseek`, and `itemrace` connect them to Minecraft.

Only one instance of each game can run at a time. Games end when the server shuts down. There are no automatic rewards or scoreboards.
