NEXO MENU BACKGROUNDS AND HOLOGRAM LOGOS
========================================

Eleven menu backgrounds in one clean slate-grey style, plus nine 3D hologram logos.
Everything is pixel art at Minecraft's own scale, so each cubby sits exactly under the item
your menu puts in that slot, and the logos stay crisp at any size.

  MENU               ROWS  ITEMS IN SLOTS                      BACK/CLOSE
  shop_gui           3     11 12 13 14 15                      26
  black_market_gui   3     10 12 13 14 16                      26
  kill_streaks_gui   4     12 13 14 21 22 23                   -
  media_rank_gui     3     12 13 14                            22
  kits_gui           3     10 11 12 13 14 15 16                -
  settings_gui       4     10 11 12 14 15 16 19 20 21 23 24 25 31
  trash_bin_gui      4     every slot stays open to drop items -
  map_switcher_gui   3     10 teleport, 13 map info, 16 cancel -
  booster_gui        3     11 daily, 13 weekly, 15 monthly     -
  coinflip_gui       3     10 11 12 13 14 15 16                -
  quests_gui         3     12 14                               -

  (Slots count from 0 in the top-left, 9 per row - the same numbers DeluxeMenus uses.)

  booster_gui matches the BoostRewards menu's slots (AxRewards / mc-DiscordLink:
  rows 3, daily 11, weekly 13, monthly 15). Only its title changes:
      title: "&f<shift:-8><glyph:booster_gui>"          (AxRewards menus/boost-rewards.yml)
      menu-title: "&f<shift:-8><glyph:booster_gui>"     (mc-DiscordLink guis.yml)

  HOLOGRAM           TEXT
  holo_shop          SHOP
  holo_coinflip      COINFLIP
  holo_quests        QUESTS
  holo_kits          KITS
  holo_black_market  BLACK / market
  holo_kill_streaks  KILL / streaks
  holo_media_rank    MEDIA / rank
  holo_trash_bin     TRASH / bin
  holo_booster       BOOSTER / rewards


INSTALL
-------
1. Unzip nexo-gui-pack.zip into  plugins/Nexo/
   It already has Nexo's folder layout:
       pack/assets/minecraft/textures/menus/*.png
       pack/assets/minecraft/textures/holograms/*.png
       glyphs/menus_glyphs.yml
       glyphs/holograms_glyphs.yml

2. Run  /nexo reload all  and rejoin so the new resource pack loads.


MENUS
-----
Set each menu's title to its picture, pulled 8 pixels left to line up with the menu's edge.
The sign text is part of the picture, so the title is only the glyph:

    <white><shift:-8><glyph:shop_gui>
    <white><shift:-8><glyph:settings_gui>
    ...and so on, with the menu's name from the table above.

If your menu plugin can't pass Nexo's tags through, use Nexo's PlaceholderAPI placeholders:
    %nexo_shift_-8%%nexo_shop_gui%

- Keep the items in the slots listed above, and the row counts the same.
- Leave every other slot EMPTY (no glass-pane filler): the picture already covers the
  menu's slot grid, and any item would sit on top of it. (The trash bin is the exception:
  it shows every slot, so players can drop items anywhere.)
- Your own inventory below the menu is left as it is.


HOLOGRAMS
---------
Each logo is one hologram line. It sits on top of the line, so put the hologram where
you want the bottom of the logo.

  FancyHolograms (text displays, recommended - no dark box behind the logo):
      /hologram create text shop
      /hologram edit shop setLine 1 %nexo_holo_shop%
      /hologram edit shop background transparent
      /hologram edit shop scale 1.5          (optional: bigger)
      /hologram edit shop billboard center   (optional: always faces the player)

  DecentHolograms:
      /dh create shop %nexo_holo_shop%

Both need PlaceholderAPI. Plugins that read Nexo tags can use <glyph:holo_shop> instead.

Size: at scale 1 a one-line logo is about 1 block tall and a two-line logo about 1.6.
Scale the hologram, or lower both ascent and height in holograms_glyphs.yml, to change it.


IF SOMETHING LOOKS OFF
----------------------
- Picture darker than the preview: the text colour is tinting it. Start the title or
  hologram line with <white> (or &f).
- Menu picture a few pixels left/right: change -8 in <shift:-8>.
- Menu picture too high/low: change ascent in menus_glyphs.yml (higher number = higher).
- A dark box behind a hologram: that is the name-tag background of armour-stand holograms.
  Use FancyHolograms with "background transparent", or turn it off in your hologram plugin.


CHANGING THEM
-------------
Everything is drawn by the Python scripts here (Python 3 + Pillow: pip install pillow).

  generate.py    the menus. MENUS lists each menu's rows, slots and sign text; the slate
                 colours are in the palette section near the top.
  holograms.py   the logos. LOGOS lists each logo's lines; the colours are at the top.
  package.py     runs both, writes the glyph configs and builds nexo-gui-pack.zip.

  python3 package.py

To add a menu: add a line to MENUS with its rows, slots and title, then run package.py.
  e.g.  "warps_gui": dict(rows=3, buttons=[11, 13, 15], title="WARPS"),
To add a logo: add a line to LOGOS, e.g.  "holo_spawn": [("SPAWN", BIG)],


FILES
-----
nexo-gui-pack.zip   everything to install, laid out like plugins/Nexo/
textures/           the menu PNGs
holograms/          the logo PNGs
nexo/               the glyph configs
previews/           4x pictures of the menus with stand-in items, and the logos
