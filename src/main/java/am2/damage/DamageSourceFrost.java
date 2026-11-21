package am2.damage;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.EntityDamageSource;

public class DamageSourceFrost extends EntityDamageSource{
	public DamageSourceFrost(EntityLivingBase source){
		this(source, true);
	}

	public DamageSourceFrost(EntityLivingBase source, boolean bypassArmor){
		super("am2.frost", source);
		if (bypassArmor) this.setDamageBypassesArmor();
	}
}
