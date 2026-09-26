MENU BACKGROUNDS FOR NEXO
=========================

Five themed menu backgrounds, drawn as pixel art at Minecraft's own GUI scale, so each
wooden cubby sits exactly under the item your menu puts in that slot.

  shop_gui           3 rows   buttons in slots 11-15, back button in 26
  black_market_gui   3 rows   buttons in slots 10, 12, 13, 14, 16, back button in 26
  quests_gui         3 rows   buttons in slots 12, 14
  kill_streaks_gui   4 rows   buttons in slots 12, 13, 14, 21, 22, 23
  media_rank_gui     3 rows   buttons in slots 12, 13, 14, back button in 22

(Slots count from 0 in the top-left, 9 per row - the same numbers DeluxeMenus uses.)


INSTALL
-------
1. Copy textures/*.png to
       plugins/Nexo/pack/assets/minecraft/textures/menus/
   (If your other glyph textures live somewhere else, put them there instead and change
   the "texture:" lines in step 2 to match.)

2. Copy nexo/menus_glyphs.yml to
       plugins/Nexo/glyphs/

3. Run  /nexo reload all  and rejoin so the new resource pack loads.

4. Set each menu's title. The sign is part of the picture, so the title is only the
   picture, pulled 8 pixels left to line up with the menu's edge:

       shop:          <shift:-8><glyph:shop_gui>
       black market:  <shift:-8><glyph:black_market_gui>
       quests:        <shift:-8><glyph:quests_gui>
       kill streaks:  <shift:-8><glyph:kill_streaks_gui>
       media rank:    <shift:-8><glyph:media_rank_gui>

   DeluxeMenus example:
       menu_title: '<shift:-8><glyph:shop_gui>'

   If your menu plugin can't pass Nexo's tags through, use Nexo's PlaceholderAPI
   placeholders instead:  %nexo_shift_-8%%nexo_shop_gui%
   (Nexo's PlaceholderAPI glyph placeholder needs the glyph on its own font. If it
   prints nothing, add  font: nexo:menus  under each glyph in menus_glyphs.yml.)

5. Keep the buttons in the slots listed above. The kill streaks menu must be 4 rows;
   the others 3 rows.


IF SOMETHING LOOKS OFF
----------------------
- Picture darker than the preview: the title colour is tinting it. Put <white> (or &f)
  at the start of the title.
- Picture a few pixels left/right: change -8 in <shift:-8>.
- Picture too high/low: change ascent in menus_glyphs.yml (higher number = higher).
- Changing button slots: edit the MENUS list in generate.py and run  python3 generate.py
  (needs Python 3 and Pillow: pip install pillow). The cubbies follow the slots you list.


FILES
-----
textures/     the five PNGs to install
nexo/         the glyph config
previews/     4x pictures with stand-in items, as they will look in game
generate.py   draws everything; edit and re-run to change themes, colours or slots
