package am2.items.renderers;

import am2.client.vfx.BlockbenchRigModel;
import am2.guis.controls.InstrumentStyle;
import cpw.mods.fml.relauncher.*;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.IItemRenderer;
import org.lwjgl.opengl.GL11;

/** Dedicated item poses; the placed machine and its tile renderer stay independent. */
@SideOnly(Side.CLIENT)
public final class ArcaneStrikerItemRenderer implements IItemRenderer {
    private static BlockbenchRigModel model;
    private static boolean attempted;
    public boolean handleRenderType(ItemStack stack,ItemRenderType type){return type==ItemRenderType.INVENTORY||type==ItemRenderType.EQUIPPED_FIRST_PERSON||type==ItemRenderType.EQUIPPED||type==ItemRenderType.ENTITY;}
    public boolean shouldUseRenderHelper(ItemRenderType type,ItemStack stack,ItemRendererHelper helper){return type==ItemRenderType.ENTITY&&(helper==ItemRendererHelper.ENTITY_ROTATION||helper==ItemRendererHelper.ENTITY_BOBBING);}
    public void renderItem(ItemRenderType type,ItemStack stack,Object...data){
        if(type==ItemRenderType.INVENTORY){InstrumentStyle.icon(true,0,0,16);return;}
        if(!attempted){attempted=true;model=BlockbenchRigModel.load(new ResourceLocation("arsmagica2","models/blocks/arcane_striker_machine.bbmodel"),"am2_striker_item","tube_glass");}
        if(model==null)return;
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);GL11.glPushMatrix();
        try{
            if(type==ItemRenderType.EQUIPPED_FIRST_PERSON){GL11.glTranslatef(0.66f,0.08f,0.34f);GL11.glRotatef(14,1,0,0);GL11.glRotatef(-28,0,1,0);GL11.glRotatef(-6,0,0,1);GL11.glScalef(0.58f,0.58f,0.58f);}
            else if(type==ItemRenderType.EQUIPPED){GL11.glTranslatef(0.52f,0.12f,0.38f);GL11.glRotatef(-22,0,1,0);GL11.glScalef(0.48f,0.48f,0.48f);}
            else{GL11.glTranslatef(0,-0.23f,0);GL11.glScalef(0.54f,0.54f,0.54f);}
            model.render(null,0,null,1,1,1,1);
        }finally{GL11.glPopMatrix();GL11.glPopAttrib();}
    }
}
