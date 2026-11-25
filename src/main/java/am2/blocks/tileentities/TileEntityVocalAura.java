package am2.blocks.tileentities;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;

public class TileEntityVocalAura extends TileEntity {

	private String placedByUsername = "";

	@Override
	public boolean canUpdate() {
		return false;
	}

	public String getPlacedByUsername() {
		return placedByUsername;
	}

	public void setPlacedByUsername(String username) {
		this.placedByUsername = username != null ? username : "";
	}

	@Override
	public void writeToNBT(NBTTagCompound compound) {
		super.writeToNBT(compound);
		compound.setString("PlacedBy", placedByUsername);
	}

	@Override
	public void readFromNBT(NBTTagCompound compound) {
		super.readFromNBT(compound);
		this.placedByUsername = compound.getString("PlacedBy");
	}
}
