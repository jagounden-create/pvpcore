package com.pvpcore.menu;

/** Slot maths for 9-wide rows. */
final class Layout {
   private Layout() {
   }

   /**
    * {@code count} slots centred in the row starting at {@code rowStart}. An odd count sits in a block around the
    * middle; an even count leaves the middle column empty so both halves mirror each other.
    */
   static int[] centered(int rowStart, int count) {
      if (count < 0 || count > 8) {
         throw new IllegalArgumentException("A row holds 0 to 8 centred items, not " + count);
      }

      int middle = rowStart + 4;
      int[] slots = new int[count];
      if (count % 2 == 1) {
         int first = middle - count / 2;
         for (int i = 0; i < count; i++) {
            slots[i] = first + i;
         }
      } else {
         int half = count / 2;
         int index = 0;
         for (int offset = half; offset >= 1; offset--) {
            slots[index++] = middle - offset;
         }

         for (int offset = 1; offset <= half; offset++) {
            slots[index++] = middle + offset;
         }
      }

      return slots;
   }
}
