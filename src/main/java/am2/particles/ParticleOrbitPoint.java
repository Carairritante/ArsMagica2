package am2.particles;

import java.util.Random;

public final class ParticleOrbitPoint extends ParticleController{

	private double targetX;
	private double targetY;
	private double targetZ;
	private double distance;
	private boolean rotateClockwise;
	private double targetOffsetY;
	private double targetDistance;
	private double orbitSpeed;
	private double orbitAngle;
	private double orbitY = -512;
	private boolean useCurrentDistance;
	private boolean ignoreYCoord;

	private boolean shrinkingOrbit;
	private double shrinkSpeed = 0;
	private double shrinkTargetDistance = 0;

	public ParticleOrbitPoint(AMParticle particleEffect, double orbitX, double orbitY, double orbitZ, int priority, boolean exclusive){
		super(particleEffect, priority, exclusive);
		this.targetX = orbitX;
		this.targetY = orbitY;
		this.targetZ = orbitZ;
		orbitAngle = particle.worldObj.rand.nextInt(360);
		rotateClockwise = particle.worldObj.rand.nextInt(10) < 5;
		generateNewTargetY();
		targetDistance = 1 + (particle.worldObj.rand.nextDouble() * 0.5);
	}

	public ParticleOrbitPoint setOrbitY(double orbitY){
		this.orbitY = orbitY;
		return this;
	}

	public ParticleOrbitPoint setIgnoreYCoordinate(boolean ignore){
		this.ignoreYCoord = ignore;
		return this;
	}

	public ParticleOrbitPoint SetTargetDistance(double targetDistance){
		this.targetDistance = targetDistance;
		this.useCurrentDistance = false;
		return this;
	}

	public ParticleOrbitPoint SetUseCurrentDistance(){
		this.useCurrentDistance = true;
		return this;
	}

	public ParticleOrbitPoint SetOrbitSpeed(double speed){
		this.orbitSpeed = speed;
		return this;
	}

	public ParticleOrbitPoint SetShrinkingOrbit(double shrinkSpeed, double newTargetDistance){
		this.shrinkingOrbit = true;
		this.shrinkSpeed = shrinkSpeed;
		this.shrinkTargetDistance = newTargetDistance;
		return this;
	}

	private void generateNewTargetY(){
		targetOffsetY = particle.worldObj.rand.nextDouble() * 2;
	}

	private void generateNewDistance(){
		targetDistance = particle.worldObj.rand.nextDouble() * 2;
	}

	/**
	 * Set true for clockwise, false for counterclockwise.
	 * Not calling this method will cause the direction to be random.
	 *
	 * @param clockwise
	 */
	public ParticleOrbitPoint setRotateDirection(boolean clockwise){
		this.rotateClockwise = clockwise;
		return this;
	}

	/**
	 * Sets the start angle of rotation, in radians
	 *
	 * @param angle
	 * @return
	 */
	public ParticleOrbitPoint setStartAngle(float angle){
		this.orbitAngle = angle;
		return this;
	}

	@Override
	public void doUpdate(){

		double posX;
		double posZ;
		double posY = particle.posY;

		double relativeTargetY = targetY + targetOffsetY;

		if (!ignoreYCoord){
			if (Math.abs(particle.posY - relativeTargetY) < 0.1){
				generateNewTargetY();
			}
		}

		if (useCurrentDistance){
			double deltaz = targetZ - particle.posZ;
			double deltax = targetX - particle.posX;
			double currentDistance = Math.sqrt(deltaz * deltaz + deltax * deltax);
			posX = targetX + (Math.cos(orbitAngle) * currentDistance);
			posZ = targetZ + (Math.sin(orbitAngle) * currentDistance);
		}else{
			if (shrinkingOrbit){
				if (targetDistance <= shrinkTargetDistance){
					shrinkingOrbit = false;
				}else{
					if (targetDistance < shrinkTargetDistance + shrinkSpeed * 10){
						double delta = targetDistance - shrinkTargetDistance;
						targetDistance -= delta * shrinkSpeed;
					}else{
						targetDistance -= shrinkSpeed;
					}
				}
			}
			posX = targetX + (Math.cos(orbitAngle) * targetDistance);
			posZ = targetZ + (Math.sin(orbitAngle) * targetDistance);
		}

		if (!ignoreYCoord){
			if (particle.posY < relativeTargetY){
				particle.posY += 0.1;
			}else if (particle.posY > relativeTargetY){
				particle.posY -= 0.1;
			}
		}

		if (rotateClockwise){
			orbitAngle += orbitSpeed;
		}else{
			orbitAngle -= orbitSpeed;
		}
		if (orbitAngle > 360){
			orbitAngle -= 360;
		}else if (orbitAngle < 0){
			orbitAngle += 360;
		}

		if (orbitY != -512){
			posY = targetY + orbitY;
		}

		particle.setPosition(posX, posY, posZ);
		if (firstTick){
			particle.prevPosX = posX;
			particle.prevPosY = posY;
			particle.prevPosZ = posZ;
		}
	}

	@Override
	public ParticleController clone(){
		ParticleOrbitPoint clone = new ParticleOrbitPoint(particle, targetX, targetY, targetZ, priority, exclusive);
		if (useCurrentDistance) clone.SetUseCurrentDistance();
		else clone.SetTargetDistance(targetDistance);

		if (orbitY != -512) clone.setOrbitY(orbitY);

		clone.SetOrbitSpeed(orbitSpeed);

		clone.setIgnoreYCoordinate(this.ignoreYCoord);

		clone.setRotateDirection(this.rotateClockwise);

		clone.setStartAngle((float)this.orbitAngle);

		if (this.shrinkingOrbit) clone.SetShrinkingOrbit(this.shrinkSpeed, this.shrinkTargetDistance);

		return clone;
	}

}
