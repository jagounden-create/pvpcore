NEXO MENU BACKGROUNDS AND HOLOGRAM LOGOS
========================================

Six menu backgrounds in one wooden-stall style, plus six 3D hologram logos. Everything is
pixel art at Minecraft's own scale, so each cubby sits exactly under the item your menu puts
in that slot, and the logos stay crisp at any size.

  MENU               ROWS  BUTTON SLOTS              BACK BUTTON
  coinflip_gui       3     10 11 12 13 14 15 16      -
  shop_gui           3     11 12 13 14 15            26
  black_market_gui   3     10 12 13 14 16            26
  kill_streaks_gui   4     12 13 14 21 22 23         -
  quests_gui         3     12 14                     -
  media_rank_gui     3     12 13 14                  22

  (Slots count from 0 in the top-left, 9 per row - the same numbers DeluxeMenus uses.)

  HOLOGRAM           TEXT
  holo_shop          SHOP
  holo_coinflip      COINFLIP
  holo_quests        QUESTS
  holo_black_market  BLACK / market
  holo_kill_streaks  KILL / streaks
  holo_media_rank    MEDIA / rank


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

    coinflip:      <white><shift:-8><glyph:coinflip_gui>
    shop:          <white><shift:-8><glyph:shop_gui>
    black market:  <white><shift:-8><glyph:black_market_gui>
    kill streaks:  <white><shift:-8><glyph:kill_streaks_gui>
    quests:        <white><shift:-8><glyph:quests_gui>
    media rank:    <white><shift:-8><glyph:media_rank_gui>

If your menu plugin can't pass Nexo's tags through, use Nexo's PlaceholderAPI placeholders:
    %nexo_shift_-8%%nexo_coinflip_gui%

- Keep the buttons in the slots listed above. Kill streaks must be 4 rows, the rest 3 rows.
- Leave every other slot of the menu EMPTY (no glass-pane filler): the picture already
  covers the menu's slot grid, and any item would sit on top of it.
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

  generate.py    the menus. MENUS lists each menu's rows, button slots and sign text;
                 THEMES sets its wood, roof, cubby colours and decorations.
  holograms.py   the logos. LOGOS lists each logo's lines; the colours are at the top.
  package.py     runs both, writes the glyph configs and builds nexo-gui-pack.zip.

  python3 package.py

To add a menu: add a line to MENUS with its slots (and a theme), then run package.py.
To add a logo: add a line to LOGOS, e.g.  "holo_spawn": [("SPAWN", BIG)],


FILES
-----
nexo-gui-pack.zip   everything to install, laid out like plugins/Nexo/
textures/           the menu PNGs
holograms/          the logo PNGs
nexo/               the glyph configs
previews/           4x pictures of the menus with stand-in items, and the logos
