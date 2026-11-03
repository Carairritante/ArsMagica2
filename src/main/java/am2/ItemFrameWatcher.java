package am2;

import am2.blocks.BlocksCommonProxy;
import am2.items.ItemsCommonProxy;
import am2.particles.*;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.block.Block;
import net.minecraft.entity.item.EntityItemFrame;
import net.minecraft.init.Items;
import net.minecraft.item.ItemBook;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

/** Each logical side owns its counters; entity callbacks only enqueue work. */
public class ItemFrameWatcher {
    private static final int processTime=800;
    private static final class Progress {int time;}
    private static final class Frames {
        final Map<EntityItemFrame,Progress> watched=new IdentityHashMap<EntityItemFrame,Progress>();
        final ConcurrentLinkedQueue<EntityItemFrame> additions=new ConcurrentLinkedQueue<EntityItemFrame>();
        final ConcurrentLinkedQueue<EntityItemFrame> removals=new ConcurrentLinkedQueue<EntityItemFrame>();
        final ConcurrentLinkedQueue<World> clears=new ConcurrentLinkedQueue<World>();
        boolean processing;
    }
    private final Frames clientFrames=new Frames(),serverFrames=new Frames();
    private final Map<World,Boolean> unloaded=Collections.synchronizedMap(new WeakHashMap<World,Boolean>());

    /** Retained for addons that already call this entry point. */
    public void checkWatchedFrames(){checkWatchedFrames(FMLCommonHandler.instance().getEffectiveSide().isClient());}

    public void checkWatchedFrames(boolean clientSide){
        if(FMLCommonHandler.instance().getEffectiveSide().isClient()!=clientSide)return;
        Frames frames=clientSide?clientFrames:serverFrames;
        synchronized(frames){
            if(frames.processing)return;
            frames.processing=true;
            try {
                clearQueuedWorlds(frames);
                updateQueuedChanges(frames,clientSide);
                for(Iterator<Map.Entry<EntityItemFrame,Progress>> it=frames.watched.entrySet().iterator();it.hasNext();){
                    Map.Entry<EntityItemFrame,Progress> entry=it.next();EntityItemFrame frame=entry.getKey();Progress progress=entry.getValue();
                    if(frame.worldObj==null||frame.worldObj.isRemote!=clientSide||unloaded.containsKey(frame.worldObj)||frame.isDead){it.remove();continue;}
                    boolean remove=!clientSide||progress.time>=processTime;
                    if(frameIsValid(frame)){
                        if(!checkFrameRadius(frame,progress))remove=false;
                    }else progress.time++;
                    if(remove){completeIfReady(frame,progress,clientSide);it.remove();}
                }
            }finally{frames.processing=false;clearQueuedWorlds(frames);}
        }
    }
    private boolean checkFrameRadius(EntityItemFrame frame,Progress progress){
        boolean shouldRemove=true;
        int x=MathHelper.floor_double(frame.posX),y=MathHelper.floor_double(frame.posY),z=MathHelper.floor_double(frame.posZ);
        boolean alternative=AMCore.config.isAlternativeStart();
        for(int dx=-2;dx<=2;dx++)for(int dy=-2;dy<=2;dy++)for(int dz=-2;dz<=2;dz++){
            int bx=x+dx,by=y+dy,bz=z+dz;
            // Reading a recipe must never generate/load adjacent server chunks.
            if(!frame.worldObj.blockExists(bx,by,bz))continue;
            Block block=frame.worldObj.getBlock(bx,by,bz);
            if(alternative?(block!=BlocksCommonProxy.witchwoodLeaves&&block!=BlocksCommonProxy.witchwoodLog):block!=BlocksCommonProxy.liquidEssence)continue;
            progress.time++;
            if(progress.time>=processTime){
                if(!frame.worldObj.isRemote){frame.setDisplayedItem(new ItemStack(ItemsCommonProxy.arcaneCompendium));return true;}
            }else{
                shouldRemove=false;
                if(frame.worldObj.isRemote)spawnCompendiumProgressParticles(frame,bx,by,bz);
            }
        }
        return shouldRemove;
    }
    private boolean frameIsValid(EntityItemFrame frame){return !frame.isDead&&frame.getDisplayedItem()!=null&&frame.getDisplayedItem().getItem() instanceof ItemBook;}
    private void updateQueuedChanges(Frames frames,boolean clientSide){
        EntityItemFrame frame;
        while((frame=frames.additions.poll())!=null){
            if(frame.worldObj==null||frame.worldObj.isRemote!=clientSide||unloaded.containsKey(frame.worldObj))continue;
            if(frame.getDisplayedItem()==null||frame.getDisplayedItem().getItem()!=ItemsCommonProxy.arcaneCompendium)frames.watched.put(frame,new Progress());
        }
        while((frame=frames.removals.poll())!=null){Progress progress=frames.watched.remove(frame);if(progress!=null)completeIfReady(frame,progress,clientSide);}
    }
    private void completeIfReady(EntityItemFrame frame,Progress progress,boolean clientSide){
        if(clientSide&&progress.time>=processTime&&frame.worldObj!=null&&frame.worldObj.isRemote&&!unloaded.containsKey(frame.worldObj)&&!frame.isDead&&frame.getDisplayedItem()!=null&&(frame.getDisplayedItem().getItem()==Items.book||frame.getDisplayedItem().getItem()==ItemsCommonProxy.arcaneCompendium))spawnCompendiumCompleteParticles(frame);
    }
    public void startWatchingFrame(EntityItemFrame frame){if(frame!=null&&frame.worldObj!=null&&!unloaded.containsKey(frame.worldObj))(frame.worldObj.isRemote?clientFrames:serverFrames).additions.offer(frame);}
    public void stopWatchingFrame(EntityItemFrame frame){if(frame==null)return;if(frame.worldObj==null){clientFrames.removals.offer(frame);serverFrames.removals.offer(frame);}else(frame.worldObj.isRemote?clientFrames:serverFrames).removals.offer(frame);}
    public void clearWorld(World world){
        if(world==null)return;unloaded.put(world,true);Frames frames=world.isRemote?clientFrames:serverFrames;frames.clears.offer(world);
        synchronized(frames){if(!frames.processing)clearQueuedWorlds(frames);}
    }
    private static void clearQueuedWorlds(Frames frames){
        World world;while((world=frames.clears.poll())!=null){
            final World gone=world;
            for(Iterator<EntityItemFrame> it=frames.watched.keySet().iterator();it.hasNext();)if(it.next().worldObj==gone)it.remove();
            frames.additions.removeIf(frame->frame.worldObj==gone);frames.removals.removeIf(frame->frame.worldObj==gone);
        }
    }
    @SideOnly(Side.CLIENT)
    public void spawnCompendiumProgressParticles(EntityItemFrame frame,int x,int y,int z){
        AMParticle particle=(AMParticle)AMCore.proxy.particleManager.spawn(frame.worldObj,"symbols",x+.5,y+.5,z+.5);
        if(particle!=null){particle.setIgnoreMaxAge(true);particle.AddParticleController(new ParticleArcToEntity(particle,1,frame,false).SetSpeed(.02f).setKillParticleOnFinish(true));particle.setRandomScale(.05f,.12f);}
    }
    @SideOnly(Side.CLIENT)
    public void spawnCompendiumCompleteParticles(EntityItemFrame frame){
        AMParticle particle=(AMParticle)AMCore.proxy.particleManager.spawn(frame.worldObj,"radiant",frame.posX,frame.posY,frame.posZ);
        if(particle!=null){particle.setIgnoreMaxAge(false);particle.setMaxAge(40);particle.setParticleScale(.3f);particle.AddParticleController(new ParticleHoldPosition(particle,40,1,false));particle.AddParticleController(new ParticleColorShift(particle,1,false).SetShiftSpeed(.2f));}
    }
}
