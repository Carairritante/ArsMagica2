package am2.particles;

public class ParticleExpandingCollapsingRingAtPoint extends ParticleController{

	private double minDistance;
	private double maxDistance;
	private double speed;
	private double targetX;
	private double targetY;
	private double targetZ;
	private boolean collapseOnce;
	private boolean isCollapsing;
	private boolean hasCollapsed;

	public ParticleExpandingCollapsingRingAtPoint(AMParticle particleEffect, double x, double y, double z, double minDistance, double maxDistance, double speed, int priority, boolean exclusive){
		super(particleEffect, priority, exclusive);

		this.minDistance = minDistance;
		this.maxDistance = maxDistance;
		this.speed = speed;
		this.targetX = x;
		this.targetY = y;
		this.targetZ = z;
	}

	public ParticleExpandingCollapsingRingAtPoint setCollapseOnce(){
		collapseOnce = true;
		return this;
	}

	public ParticleExpandingCollapsingRingAtPoint setExpanding(){
		isCollapsing = false;
		return this;
	}

	@Override
	public void doUpdate(){
		double deltax = targetX - particle.posX;
		double deltaz = targetZ - particle.posZ;

		double angle = Math.atan2(deltaz, deltax);
		double distance = Math.sqrt(deltaz * deltaz + deltax * deltax);
		double factor = 0;
		if (isCollapsing){
			factor = speed;
		}else{
			factor = -speed;
		}

		double posX = Math.cos(angle) * factor;
		double posZ = Math.sin(angle) * factor;

		if (distance <= minDistance){
			isCollapsing = false;
			hasCollapsed = true;
		}else if (distance >= maxDistance){
			if (collapseOnce && hasCollapsed){
				this.finish();
			}else{
				isCollapsing = true;
			}
		}

		particle.setPosition(particle.posX + posX, particle.posY, particle.posZ + posZ);
	}

	@Override
	public ParticleController clone(){
		return new ParticleExpandingCollapsingRingAtPoint(particle, targetX, targetY, targetZ, minDistance, maxDistance, speed, priority, collapseOnce);
	}

}
