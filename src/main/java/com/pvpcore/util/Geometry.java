package com.pvpcore.util;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

public final class Geometry {
   private Geometry() {
   }

   /** Whether {@code source} is within {@code angleDegrees} of where the victim is looking, on the horizontal plane. */
   public static boolean facing(Player victim, Location source, float angleDegrees) {
      Vector look = victim.getLocation().getDirection().setY(0);
      Vector toSource = source.toVector().subtract(victim.getLocation().toVector()).setY(0);
      if (!(look.lengthSquared() < 1.0E-8) && !(toSource.lengthSquared() < 1.0E-8)) {
         double angle = Math.toDegrees(look.normalize().angle(toSource.normalize()));
         return angle <= angleDegrees;
      }

      return true;
   }
}
