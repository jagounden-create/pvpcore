package com.pvpcore.menu;

import com.pvpcore.Settings;
import com.pvpcore.util.Text;
import net.kyori.adventure.text.Component;

/** The menu's look: one accent colour, soft grey text, small capitals for names and labels. */
final class Style {
   static final String MUTED = "<" + Text.MUTED + ">";
   static final String SOFT = "<" + Text.SOFT + ">";
   static final String GOOD = "<" + Text.GOOD + ">";
   static final String BAD = "<" + Text.BAD + ">";
   static final String WARN = "<" + Text.WARN + ">";
   private final String accent;
   private final boolean caps;

   Style(Settings settings) {
      this.accent = "<" + settings.accent() + ">";
      this.caps = settings.smallCaps();
   }

   String caps(String text) {
      return this.caps ? Text.caps(text) : text;
   }

   String accent() {
      return this.accent;
   }

   Component title(String miniMessage) {
      return Text.mm(this.caps(miniMessage));
   }

   /** An item name: accent colour when active, grey when not. */
   Component name(String text, boolean active) {
      return Text.mm((active ? this.accent : SOFT) + this.caps(Text.escape(text)));
   }

   Component line(String text) {
      return Text.mm(SOFT + Text.escape(text));
   }

   Component warn(String text) {
      return Text.mm(WARN + Text.escape(text));
   }

   /** "LABEL value" */
   Component label(String label, String value) {
      return Text.mm(MUTED + this.caps(label) + "  <white>" + Text.escape(value));
   }

   /** "LABEL value   LABEL value" */
   Component label(String label, String value, String label2, String value2) {
      return Text.mm(MUTED + this.caps(label) + "  <white>" + Text.escape(value) + "   " + MUTED + this.caps(label2) + "  " + SOFT + Text.escape(value2));
   }

   /** "ACTION what" hint lines at the bottom of an item. */
   Component hint(String action, String what) {
      return Text.mm(MUTED + this.caps(action) + "  " + SOFT + Text.escape(what));
   }

   Component hint(String action, String what, String action2, String what2) {
      return Text.mm(MUTED + this.caps(action) + "  " + SOFT + Text.escape(what) + "   " + MUTED + this.caps(action2) + "  " + SOFT + Text.escape(what2));
   }

   Component status(Status.State state) {
      if (!state.available()) {
         return Text.mm(WARN + Text.DOT + " " + this.caps("Unavailable"));
      }

      return state.on() ? Text.mm(GOOD + Text.DOT + " " + this.caps("Enabled")) : Text.mm(BAD + Text.DOT + " " + this.caps("Disabled"));
   }
}
