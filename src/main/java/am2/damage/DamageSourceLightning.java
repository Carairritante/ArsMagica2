package am2.damage;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.EntityDamageSource;

public class DamageSourceLightning extends EntityDamageSource{
	public DamageSourceLightning(EntityLivingBase source){
		this(source, true);
	}

	public DamageSourceLightning(EntityLivingBase source, boolean bypassArmor){
		super("am2.lightning", source);
		if (bypassArmor) this.setDamageBypassesArmor();
	}
}
