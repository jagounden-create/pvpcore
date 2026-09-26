package com.pvpcore.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class SmallModulesTest {
   @Test
   void combatTagBlocksCommandsWithOrWithoutPrefix() {
      List<String> blocked = List.of("spawn", "home");
      assertTrue(CombatTagModule.blocked("/spawn", blocked));
      assertTrue(CombatTagModule.blocked("/SPAWN now", blocked));
      assertTrue(CombatTagModule.blocked("/essentials:home base", blocked));
      assertTrue(CombatTagModule.blocked("//home", blocked));
      assertFalse(CombatTagModule.blocked("/spawner", blocked));
      assertFalse(CombatTagModule.blocked("/msg spawn hi", blocked));
      assertFalse(CombatTagModule.blocked("/", blocked));
   }

   @Test
   void bedrockBuffIsOneMultiplier() {
      assertEquals(1.02, WeaponsModule.bedrockMultiplier(2.0), 1.0E-12);
      assertEquals(1.0, WeaponsModule.bedrockMultiplier(-5.0), 1.0E-12);
      assertTrue(WeaponsModule.swordOrAxe(Material.NETHERITE_SWORD));
      assertTrue(WeaponsModule.swordOrAxe(Material.WOODEN_AXE));
      assertFalse(WeaponsModule.swordOrAxe(Material.MACE));
      assertFalse(WeaponsModule.swordOrAxe(Material.NETHERITE_PICKAXE));
   }

   @Test
   void onlyHitParticlesCountAsHitParticles() {
      assertTrue(ParticleModule.hitParticle("minecraft:crit"));
      assertTrue(ParticleModule.hitParticle("minecraft:enchanted_hit"));
      assertTrue(ParticleModule.hitParticle("minecraft:sweep_attack"));
      assertFalse(ParticleModule.hitParticle("minecraft:damage_indicator"));
      assertFalse(ParticleModule.hitParticle("minecraft:gust_emitter_large"));
   }

   @Test
   void xpNeverOverflows() {
      assertEquals(Integer.MAX_VALUE, ClumpsModule.add(Integer.MAX_VALUE, 5));
      assertEquals(12, ClumpsModule.add(5, 7));
   }
}
