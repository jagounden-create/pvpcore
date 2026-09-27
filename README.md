# PvPCore

PvP optimization for Paper servers: **Sword**, **Mace**, **Cart** and **Diamond SMP** combat, item nerfs and cooldowns, and server-side cleanups, with every switch in one clean in-game menu.

- **Minecraft:** Paper 1.21 to 1.21.11 and 26.x (Java 21). Compiled against 1.21.11 and checked against the 1.21.4, 1.21.11 and 26.2 APIs. Features newer than the running server are detected at startup and switched off gracefully. Leaf, Purpur and other Paper forks work too.
- **Dependencies:** none. Optional: Geyser/Floodgate (Bedrock detection), KnockbackSync and PaperAttributeSwapFix (PvPCore steps aside for them).

## Install

1. Put `PvPCore-3.0.0.jar` in `plugins/` and restart (not `/reload`).
2. Run `/pvpcore` in game, or pick a starting point with `/pvpcore preset <diamond-smp|sword|mace|cart|vanilla>`.

Coming from PvPCore 2.x? Keep your `plugins/PvPCore` folder. Your `config.yml` keeps every value and gains the new settings automatically.

## The menu

`/pvpcore` opens a small hub: five game-mode pages, then Item Rules, Overview and Presets.

On a game-mode page each switch is an icon with a status pane under it: green is on, red is off, orange means this server can't run it (with the reason in the tooltip).

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
| Attribute Swapping | Swap items mid-hit and keep the first item's damage | on |
| Hit Delay | Invulnerability after a hit (vanilla 20 ticks) | off |
| No Sweep Damage | Sword sweeps don't hurt or push nearby players | off |
| Lunge Cooldown | Seconds between spear Lunge dashes (1.21.11+) | on, 3s |
| Bedrock Buff | Bedrock players deal +2% damage with swords and axes (never stacks) | on, 2% |

### Mace
| Switch | What it does | Default |
| --- | --- | --- |
| Smash Fall Safety | A smash that connects never costs fall damage, even when lag or hit immunity swallowed the hit | on |
| Smash Damage | Scales the fall bonus of smashes | off, 1.0x |
| Mace Damage Cap | Most damage one mace hit can deal, in hearts | off, 20 |
| Smash Cooldown | After a smash, the next one deals normal damage until it wears off | off, 1.5s |
| Wind Charge Cooldown | Ticks between wind charge throws | off, 10 |
| No Elytra Smash | Falls that began with an elytra glide get no smash bonus | off |

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
| Smooth Pearls | Land exactly where the pearl hit | on |
| No Pearl Damage | Pearls don't hurt on landing | off |
| Pearl Cooldown | Ticks between pearl throws | off, 20 |
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
| `/pvpcore menu <sword\|mace\|cart\|diamond-smp\|general\|rules\|presets>` | Open a page directly |
| `/pvpcore status` | Every switch, and what this server supports |
| `/pvpcore reload` | Re-read `config.yml` and `rules.yml` |
| `/pvpcore toggle <switch> [on\|off]` | Turn a switch on or off |
| `/pvpcore set <switch> <number>` | Set a switch's number |
| `/pvpcore preset <name>` | Apply a preset (item rules are kept) |
| `/pvpcore rules` | Open Item Rules (console: list them) |
| `/pvpcore rule <item> [setting] [value]` | Show, change or remove a rule |

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

## Building

```
mvn package
```

This produces `target/PvPCore-3.0.0.jar`. The build runs 92 tests, including full-plugin tests on a simulated Paper server (MockBukkit).
