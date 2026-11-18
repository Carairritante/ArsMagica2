package am2.compass;

import am2.items.ItemAstralCompass;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import java.lang.ref.WeakReference;
import java.util.*;

/** Network requests contain values only; all world/mana/inventory work runs on server ticks. */
public final class AstralCompassService {
    private static final Map<UUID,Request> pending=new LinkedHashMap<UUID,Request>();
    private static final Map<UUID,Job> jobs=new LinkedHashMap<UUID,Job>();
    private static WeakReference<MinecraftServer> lastServer=new WeakReference<MinecraftServer>(null);
    private static final class Request {
        final UUID player;final int slot,action,dimension;final String id;
        Request(EntityPlayerMP p,int slot,int action,String id){player=p.getUniqueID();dimension=p.dimension;this.slot=slot;this.action=action;this.id=id;}
    }
    private static final class Job {
        final UUID owner;final int slot,dimension;final ItemStack compass;
        final AstralStructureLocator.Search search;
        int age;
        Job(EntityPlayerMP p,ItemStack stack,StructureTargetInfo target){owner=p.getUniqueID();slot=p.inventory.currentItem;dimension=p.dimension;compass=stack;search=new AstralStructureLocator.Search(p.worldObj,p,target);}
    }
    public static synchronized void enqueue(EntityPlayerMP player,int slot,int action,String id) {
        if(player==null || slot<0 || slot>8 || action<0 || action>3 || id==null || id.length()>128
                || action==ItemAstralCompass.ACT_SEARCH && !AstralStructureLocator.validId(id))return;
        UUID uuid=player.getUniqueID();
        if(action==ItemAstralCompass.ACT_CANCEL) {
            // Replace this player's queued action; cancellation cannot evict another player.
            if(pending.containsKey(uuid) || pending.size()<64) pending.put(uuid,new Request(player,slot,action,id));
            return;
        }
        Request previous=pending.get(uuid);
        if(previous!=null && previous.action==ItemAstralCompass.ACT_REQUEST_CATALOG && action==ItemAstralCompass.ACT_SEARCH) {
            pending.put(uuid,new Request(player,slot,action,id));return;
        }
        if(pending.size()>=64 || previous!=null)return;
        pending.put(uuid,new Request(player,slot,action,id));
    }
    public static void start(EntityPlayerMP player,ItemStack stack,StructureTargetInfo target) {
        Job existing=jobs.get(player.getUniqueID());
        if(existing!=null) {
            ItemAstralCompass.sendSearchStatus(player,ItemAstralCompass.STATUS_SEARCHING,existing.search.target.id);return;
        }
        if(jobs.size()>=32) {
            ItemAstralCompass.sendSearchStatus(player,ItemAstralCompass.STATUS_REJECTED,target.id); return;
        }
        jobs.put(player.getUniqueID(),new Job(player,stack,target));
        ItemAstralCompass.sendSearchStatus(player,ItemAstralCompass.STATUS_SEARCHING,target.id);
        player.addChatMessage(new net.minecraft.util.ChatComponentTranslation("am2.astral_compass.searching"));
    }
    public static void cancel(EntityPlayerMP player) {
        Job removed=jobs.remove(player.getUniqueID());
        // A late cancel cannot claim a refund after a completed search already spent mana.
        ItemAstralCompass.sendSearchStatus(player,removed==null?ItemAstralCompass.STATUS_IDLE:ItemAstralCompass.STATUS_CANCELLED,"");
    }
    @SubscribeEvent public void tick(TickEvent.ServerTickEvent event) {
        if(event.phase!=TickEvent.Phase.END)return;
        MinecraftServer server=MinecraftServer.getServer();
        if(lastServer.get()!=server){synchronized(AstralCompassService.class){pending.clear();}jobs.clear();lastServer=new WeakReference<MinecraftServer>(server);return;}
        if(server==null)return;
        long deadline=System.nanoTime()+2000000L;
        for(int i=0;i<4 && System.nanoTime()<deadline;i++) {
            Request request;
            synchronized(AstralCompassService.class){Iterator<Request> it=pending.values().iterator();if(!it.hasNext())break;request=it.next();it.remove();}
            EntityPlayerMP player=find(server,request.player);
            if(player==null || player.dimension!=request.dimension)continue;
            try {ItemAstralCompass.processServerAction(player,request.slot,request.action,request.id);}
            catch(RuntimeException e){ItemAstralCompass.sendSearchStatus(player,ItemAstralCompass.STATUS_FAILED,request.id);am2.LogHelper.warn("Astral Compass: request rejected: %s",e.toString());}
            catch(LinkageError e){ItemAstralCompass.sendSearchStatus(player,ItemAstralCompass.STATUS_FAILED,request.id);am2.LogHelper.warn("Astral Compass: optional integration unavailable: %s",e.toString());}
        }
        // Round-robin one global sample budget, not a fresh budget per player.
        int budget=512;
        Iterator<Job> it=jobs.values().iterator();
        List<Job> rotate=new ArrayList<Job>();
        while(it.hasNext()) {
            Job job=it.next();EntityPlayerMP player=find(server,job.owner);
            if(player==null || player.isDead || player.dimension!=job.dimension || player.worldObj!=job.search.world
                || player.inventory.currentItem!=job.slot || player.getCurrentEquippedItem()!=job.compass || ++job.age>2400) {
                it.remove(); if(player!=null)ItemAstralCompass.sendSearchStatus(player,ItemAstralCompass.STATUS_CANCELLED,job.search.target.id); continue;
            }
            if(budget>0 && System.nanoTime()<deadline) {
                int quota=Math.max(1,512/Math.max(1,jobs.size()));
                try {job.search.step(Math.min(quota,budget),deadline);budget-=quota;}
                catch(RuntimeException e){
                    it.remove();ItemAstralCompass.sendSearchStatus(player,ItemAstralCompass.STATUS_FAILED,job.search.target.id);
                    am2.LogHelper.warn("Astral Compass: search failed for %s: %s",job.search.target.id,e.toString());continue;
                }
                catch(LinkageError e){
                    it.remove();ItemAstralCompass.sendSearchStatus(player,ItemAstralCompass.STATUS_FAILED,job.search.target.id);
                    am2.LogHelper.warn("Astral Compass: optional search integration unavailable: %s",e.toString());continue;
                }
                if(job.search.done) {it.remove();ItemAstralCompass.finishSearch(player,job.compass,job.search.result());}
                else {it.remove();rotate.add(job);}
            }
        }
        for(Job job:rotate)jobs.put(job.owner,job);
    }
    private static EntityPlayerMP find(MinecraftServer server,UUID id) {
        for(Object p:server.getConfigurationManager().playerEntityList) if(p instanceof EntityPlayerMP && id.equals(((EntityPlayerMP)p).getUniqueID()))return (EntityPlayerMP)p;
        return null;
    }
    @SubscribeEvent public void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id=event.player.getUniqueID();jobs.remove(id);synchronized(AstralCompassService.class){pending.remove(id);}
    }
    @SubscribeEvent public void unload(WorldEvent.Unload event) {
        if(event.world.isRemote)return;
        Iterator<Job> it=jobs.values().iterator();while(it.hasNext())if(it.next().search.world==event.world)it.remove();
        if(event.world.provider.dimensionId==0){synchronized(AstralCompassService.class){pending.clear();}jobs.clear();}
    }
}
