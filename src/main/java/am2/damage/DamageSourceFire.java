package am2.damage;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.EntityDamageSource;

public class DamageSourceFire extends EntityDamageSource{
	public DamageSourceFire(EntityLivingBase source){
		this(source, true);
	}

	public DamageSourceFire(EntityLivingBase source, boolean bypassArmor){
		super("am2.fire", source);
		this.setFireDamage();
		if (bypassArmor) this.setDamageBypassesArmor();
	}
}
