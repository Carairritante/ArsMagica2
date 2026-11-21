package am2.damage;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.EntityDamageSource;

public class DamageSourceWind extends EntityDamageSource{
	public DamageSourceWind(EntityLivingBase source){
		this(source, true);
	}

	public DamageSourceWind(EntityLivingBase source, boolean bypassArmor){
		super("am2.wind", source);
		if (bypassArmor) this.setDamageBypassesArmor();
	}
}
