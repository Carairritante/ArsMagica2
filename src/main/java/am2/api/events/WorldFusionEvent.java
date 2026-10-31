package am2.api.events;

import cpw.mods.fml.common.eventhandler.Cancelable;
import cpw.mods.fml.common.eventhandler.Event;
import am2.api.spell.component.interfaces.IRitualInteraction;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.world.World;

/** Server-side interception point for extensions of the World Fusion ritual. */
@Cancelable
public final class WorldFusionEvent extends Event {
 public final World world;
 public final EntityLivingBase caster;
 public final IRitualInteraction ritual;
 public final int x,y,z;
 /** An extension may cancel vanilla handling and report a completed ritual. */
 public boolean completed;
 public WorldFusionEvent(World world,EntityLivingBase caster,IRitualInteraction ritual,int x,int y,int z) {
  this.world=world;this.caster=caster;this.ritual=ritual;this.x=x;this.y=y;this.z=z;
 }
}
