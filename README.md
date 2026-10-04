# PvPCore

Smoother PvP for Paper servers: **Sword**, **Mace**, **Spear**, **Cart** and **Diamond SMP** combat, ghost-player and pearl fixes, item nerfs and cooldowns, and server-side cleanups, with every switch in one clean in-game menu.

- **Minecraft:** Paper 1.21 to 1.21.11 and 26.x (Java 21). Compiled against 1.21.11 and checked against the 1.21.4, 1.21.11 and 26.2 APIs. Features newer than the running server are detected at startup and switched off gracefully. Leaf, Purpur and other Paper forks work too.
- **Dependencies:** none. Attribute swapping, ghost fixes, pearl fixes and wind charge tech are built in, so no separate plugins are needed for them. Optional: Geyser/Floodgate (Bedrock detection); if KnockbackSync or PaperAttributeSwapFix is installed, PvPCore steps aside for it.

## Install

1. Put `PvPCore-3.2.0.jar` in `plugins/` and restart (not `/reload`).
2. Run `/pvpcore` in game, or pick a starting point with `/pvpcore preset <diamond-smp|sword|mace|ffa|cart|vanilla>`.

Updating? Keep your `plugins/PvPCore` folder. Your `config.yml` keeps every value and gains the new settings automatically.

## The menu

`/pvpcore` opens a small hub: seven pages (Sword, Mace, Spear, Cart, Diamond SMP, General, Fixes), then Item Rules, Overview and Presets.

On a page each switch is an icon with a status pane under it: green is on, red is off, orange means this server can't run it (with the reason in the tooltip).

| Action | Effect |
| --- | --- |
| Click (icon or pane) | turn on / off |
| Right-click | raise the number |
| Shift + right-click | lower the number |

Every change applies instantly and is saved to `config.yml`. The title, accent colour, filler item and small-caps style are set in the `menu:` section of `config.yml`.

## Features

### Sword
| Switch | What it does | Default |
| --- | --- | --- |
| Knockback Distance | How far hits push players back (1.0 = vanilla) | on, 0.9x |
| Vertical Knockback | Every hit near the ground lifts, even when ping hides the landing | on, 0.45 |
| Instant Knockback | Hits and knockback are sent the moment they land (Leaf only) | on |
| Attribute Swapping | Swap items mid-hit and keep the first item's damage. Uses Paper's switch where there is one, and does it itself where there isn't | on |
| Hit Delay | Invulnerability after a hit (vanilla 20 ticks) | off |
| No Sweep Damage | Sword sweeps don't hurt or push nearby players | off |
| Bedrock Buff | Bedrock players deal +2% damage with swords and axes (never stacks) | on, 2% |

### Mace
| Switch | What it does | Default |
| --- | --- | --- |
| Smash Fall Safety | A smash that connects never costs fall damage, even when lag or hit immunity swallowed the hit | on |
| Smash Damage | Scales the fall bonus of smashes | off, 1.0x |
| Mace Damage Cap | Most damage one mace hit can deal, in hearts | off, 20 |
| Smash Cooldown | After a smash, the next one deals normal damage until it wears off | off, 1.5s |
| No Elytra Smash | Falls that began with an elytra glide get no smash bonus | off |
| Wind Charge Cooldown | Ticks between wind charge throws | off, 10 |
| Wind Charge Stop | Falling and looking down, a wind charge stops the fall instead of being thrown: fall reset, a little lift kept | off, 0.1 |
| Instant Wind Jump | A wind charge thrown at your feet bursts on the ground at once, so wind jumps are the same height at any ping | off |

Wind Charge Stop's fine-tuning (minimum fall speed, minimum pitch, cooldown, sideways speed kept, whether the charge is used up) and Instant Wind Jump's (minimum pitch, highest ground) are under `mace.wind-stop` and `mace.wind-jump` in `config.yml`.

### Spear (1.21.11+)
| Switch | What it does | Default |
| --- | --- | --- |
| Lunge Cooldown | Seconds between spear Lunge dashes; the jab still hits | on, 3s |
| Charge Damage | Scales spear charge damage (the held, running attack) | off, 1.0x |
| Charge Damage Cap | Most damage one spear charge can deal, in hearts. Stops elytra and horse one-shots | off, 15 |
| No Elytra Charge | Spear charges made while gliding deal normal hit damage | off |

### Cart (TNT minecart)
| Switch | What it does | Default |
| --- | --- | --- |
| Cart Hit Reg | A flame arrow that flew through a cart's spot just before the cart appeared still sets it off. Makes up for the placer's ping | on, 250ms |
| Bow Cart Damage | Damage of carts lit by a bow arrow (insta cart) | on, 0.8x |
| Crossbow Cart Damage | Damage of carts lit by a crossbow arrow, nerfed harder | on, 0.6x |
| Consistent Power | Fixed blast power instead of the random 4 to 11.5 roll | off, 6 |
| Self Damage | How much your own cart hurts you | off, 50% |
| Activator Fuse | Fuse of carts lit by an activator rail | off, 40 ticks |
| Blast Merge | Carts going off together in one spot make one blast, not a lag spike | on |
| Cart Limit | Most TNT carts per chunk | on, 12 |
| Cart Cleanup | Unlit placed carts are removed after a while | off, 30s |
| Protect Terrain | Cart blasts hurt players but don't break blocks | off |
| No Blast Drops | Blocks broken by cart blasts drop nothing | on |

### Diamond SMP
| Switch | What it does | Default |
| --- | --- | --- |
| Shield Usage | Shields can be raised | on |
| Shield Delay | Time before a raised shield blocks, set once so shields never "reload" | on, 120ms |
| Shield Stun | Axes always stun a raised shield and the follow-up hit always lands | on, 5s |
| Break Sound | Both players hear the shield break | on |
| Fast Pots | Pots thrown at your feet burst instantly, even mid-jump | on |
| Pot Accuracy | Your own healing pots always heal you fully | on |
| XP Clumps | XP orbs merge and absorb instantly | on |

### General
| Switch | What it does | Default |
| --- | --- | --- |
| Health Under Name | `17 ❤` under every name (works with per-player scoreboards) | on |
| Target Health | Your action bar shows the health of whoever you hit | on |
| No Damage Hearts | The dark heart particles from hits are not sent | on |
| No Hit Particles | Crit and sweep particles are not sent to other players | on |
| Light Explosions | Big blasts show one small puff and no debris; wind charges keep their gusts | on |
| Arrow Cleanup | Arrows stuck in blocks vanish after a while (tridents never) | on, 10s |
| Instant Respawn | Skip the death screen | off |
| Combat Tag | PvP hits tag both players; blocked commands; logging out kills | off, 15s |
| Ban Crystal PvP | End crystals can't be placed, hit or blown up | on |
| Ban Anchor PvP | Respawn anchors can't blow players up | on |
| Item Rules | Apply `rules.yml` | on |

### Fixes
| Switch | What it does | Default |
| --- | --- | --- |
| Ghost Players | No invisible players after a totem pop, respawn, long teleport, world change, join, leaving spectator or dismounting: anyone nearby who lost them is sent them again | on |
| Ghost Scanner | Every few seconds, players close enough to see each other who aren't being sent each other are re-shown (only after two checks in a row, so normal walk-ins never count) | on, 5s |
| Ghost Shields | Everyone sees whether a shield is really up the moment it changes, and which hand it is in | on |
| Golden Apple Fix | Golden hearts, health and apple counts are re-sent after eating, after a totem and when the hearts run out | on |
| Smooth Pearls | Land exactly where the pearl hit | on |
| Pearl Anti-Glitch | A pearl never puts you inside a block (slabs, panes, doors, trapdoors, corners) or into another world. You land in the nearest clear spot the pearl could reach, or the pearl is stopped and given back | on |
| Pearl Lifetime | Pearls older than this vanish, so stasis chambers can't save an escape for later | on, 20s |
| No Pearl Damage | Pearls don't hurt on landing | off |
| Pearl Cooldown | Ticks between pearl throws | off, 20 |

Ghost fixes only ever re-send what a viewer is missing. Re-showing a player skips anyone a vanish plugin hid, and NPCs.

## Item Rules: nerf, cooldown, disable or limit anything

Open **Item Rules** in the menu and click any item in your own inventory. You get an editor for:

- **Damage:** multiplier for melee hits, arrows it shoots, tridents, and the blasts of crystals, TNT, TNT carts, anchors and beds.
- **Max Damage:** most one hit can deal, in hearts.
- **Cooldown:** starts when the item is eaten, thrown, shot, placed, emptied, used to boost an elytra, or popped (totems). Weapons start it when they hit and can't hit again until it ends. Cooldowns survive relogging.
- **Disabled:** the item can't be used at all.
- **Chunk Limit / Player Limit** (blocks only): the most of that block per chunk, or per player at once.

The same by command:

```
/pvpcore rule ender_pearl cooldown 15
/pvpcore rule enchanted_golden_apple cooldown 30
/pvpcore rule netherite_sword damage 0.9
/pvpcore rule mace max-damage 15
/pvpcore rule cobweb player-limit 8
/pvpcore rule end_crystal disabled true
/pvpcore rule end_crystal worlds arena, ffa
/pvpcore rule cobweb remove
```

Rules live in `plugins/PvPCore/rules.yml`. Creative/spectator players and `pvpcore.bypass.rules` are exempt.

## Commands

| Command | |
| --- | --- |
| `/pvpcore` (`/pvpc`) | Open the menu (console: status) |
| `/pvpcore menu <sword\|mace\|spear\|cart\|diamond-smp\|general\|fixes\|rules\|presets>` | Open a page directly |
| `/pvpcore status` | Every switch, and what this server supports |
| `/pvpcore reload` | Re-read `config.yml` and `rules.yml` |
| `/pvpcore toggle <switch> [on\|off]` | Turn a switch on or off |
| `/pvpcore set <switch> <number>` | Set a switch's number |
| `/pvpcore preset <name>` | Apply a preset (item rules are kept) |
| `/pvpcore rules` | Open Item Rules (console: list them) |
| `/pvpcore rule <item> [setting] [value]` | Show, change or remove a rule |
| `/pvpcore fix [player]` | Re-send a player to everyone near them, and everyone near them to the player |

## Permissions

| Permission | Default | |
| --- | --- | --- |
| `pvpcore.admin` | op | Menu and every command |
| `pvpcore.bypass.crystals` | false | Use crystals/anchors where they are banned |
| `pvpcore.bypass.combattag` | false | Never combat tagged |
| `pvpcore.bypass.rules` | false | Item rules don't apply |

## Safe config handling

- A `config.yml` or `rules.yml` with a typo is **never overwritten**. The last good settings stay in use, the console names the problem, and `/pvpcore status` shows it until it is fixed.
- New settings are added to your file automatically on update, and your values are kept.
- Every player-facing message is in the `messages:` section (MiniMessage). Set any to `""` to send nothing.

## Notes

- **Shields on 1.21 to 1.21.4** run in compatibility mode: the delay uses Paper's per-player shield delay (50 ms steps) and stuns build on vanilla's axe disable.
- **Particle switches** filter packets the server sends. They rely on the server's internal network classes, which are checked at startup. If anything is unexpected they switch themselves off and packets go out untouched.
- **Instant Knockback** needs a Leaf server; elsewhere it shows as unavailable.
- **Attribute Swapping** uses Paper's own switch when the server has one. Where it doesn't, PvPCore keeps the swapped-from item's damage and attack speed for the one hit made in the swap tick, then removes it again. If PaperAttributeSwapFix is installed, PvPCore leaves it to that plugin; you can remove it.
- **Ghost Shields and the Golden Apple Fix** re-send just the changed state through the server's internal network classes, checked at startup. If those are not the expected ones, golden hearts are fixed by re-showing the player (a short blink), and shields only re-send the held items. `/pvpcore status` shows which is in use, plus how many fixes ran.
- **Presets:** `ffa` is for mace and spear free-for-all: vanilla knockback distance and lift, smash fall safety, mace and spear-charge damage caps, lunge cooldown, Wind Charge Stop and Instant Wind Jump, combat tag and instant respawn, with every ghost and pearl fix on.

## Performance

PvPCore is built for busy FFA servers. Nothing heavy runs per player per tick:
- Every switch check is a plain array read; handlers bail out before doing any work when their switch is off.
- Item rules cost nothing (no permission checks, no projectile tagging) until at least one rule exists.
- Cart hit reg only follows burning arrows; plain arrows are never tracked.
- Health under names reads each player once per pass, not once per scoreboard, and only re-sends lines that changed.
- Target health bars are batched to one per attacker per tick, so a blast that hits ten players sends one bar.
- Arrow cleanup is one timer, not a task per arrow.
- The ghost scanner reads each player's position and view distance once per scan, honours each world's own tracking range, and only flags a pair missing on two scans in a row.
- Spent cooldowns, notices and block lists of players who left are forgotten, so nothing grows over long uptimes.

## Building

```
mvn package
```

This produces `target/PvPCore-3.2.0.jar`. The build runs 117 tests, including full-plugin tests on a simulated Paper server (MockBukkit).
