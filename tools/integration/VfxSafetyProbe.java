package bookanimationprobe;

import cpw.mods.fml.common.*;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import java.lang.reflect.*;
import java.io.File;
import java.nio.file.*;
import java.util.*;

/** Disposable visual/client regression. Never package this class in the mod. */
@Mod(modid="vfxsafetyprobe",name="Disposable VFX Safety Probe",version="1",dependencies="required-after:arsmagica2")
public final class VfxSafetyProbe {
    static final boolean baseline=false;
    static final boolean before=Boolean.getBoolean("vfxprobe.before");
    static final boolean galleryOnly=Boolean.getBoolean("vfxprobe.galleryOnly");
    static final boolean gallery=Boolean.getBoolean("vfxprobe.gallery");
    static final boolean testFreecam=Boolean.getBoolean("vfxprobe.freecam");
    static final List<Object> casters=new ArrayList<Object>();
    static boolean particleChecked,benchmarkDone,freecamChecked,particleOriginChecked;
    static final Set<String> fallingKinds=new HashSet<String>(),impactKinds=new HashSet<String>(),boltKinds=new HashSet<String>();
    static final String ATM="am2.client.vfx.CastingAtmosphereVFX";
    static int weatherPhases, peakFalling, peakSky;
    static double widestFallRadius;
    static boolean atmosphereFrameChecked;
    static Object freecam;
    static double bodyX,bodyY,bodyZ;
    static final String[] affinities={"NONE","ARCANE","WATER","FIRE","EARTH","AIR","LIGHTNING","ICE","NATURE","LIFE","ENDER"};
    static final List<String> results=new ArrayList<String>();
    static volatile boolean prepared;
    static Object mc,player,world,remote,renderer,model;
    static boolean launched,finished,keyDown;
    static int ticks,captureTick=-1;
    static String pendingCapture;
    static Object get(Object o,String n)throws Exception{for(Class<?> c=o instanceof Class?(Class<?>)o:o.getClass();c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(n);f.setAccessible(true);return f.get(o instanceof Class?null:o);}catch(NoSuchFieldException ignored){}throw new NoSuchFieldException(n);}
    static void set(Object o,String n,Object v)throws Exception{for(Class<?> c=o instanceof Class?(Class<?>)o:o.getClass();c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(n);f.setAccessible(true);f.set(o instanceof Class?null:o,v);return;}catch(NoSuchFieldException ignored){}throw new NoSuchFieldException(n);}
    static Object call(Object o,String n,Object...a)throws Exception{for(Class<?> c=o instanceof Class?(Class<?>)o:o.getClass();c!=null;c=c.getSuperclass())for(Method m:c.getDeclaredMethods())if(m.getName().equals(n)&&m.getParameterTypes().length==a.length)try{m.setAccessible(true);return m.invoke(o instanceof Class?null:o,a);}catch(IllegalArgumentException ignored){}throw new NoSuchMethodException(n);}
    static Object make(String n,Object...a)throws Exception{for(Constructor<?> c:Class.forName(n).getConstructors())if(c.getParameterTypes().length==a.length)try{return c.newInstance(a);}catch(IllegalArgumentException ignored){}throw new NoSuchMethodException(n);}
    static Object stat(String c,String n,Object...a)throws Exception{return call(Class.forName(c),n,a);}
    static Object field(String c,String n)throws Exception{return get(Class.forName(c),n);}
    static void need(boolean b,String s){if(!b)throw new AssertionError(s);}
    static void pass(String s){results.add(s+"=PASS");System.out.println("VFX_PROBE "+s+"=PASS");}
    static void finish(int code){if(finished)return;finished=true;try{Files.write(Paths.get("vfx-safety-result.txt"),results);}catch(Exception e){e.printStackTrace();}FMLCommonHandler.instance().exitJava(code,false);}
    static void fail(Throwable e){e.printStackTrace();results.add("ERROR="+e);finish(1);}
    static Object stack(Object item)throws Exception{return make("net.minecraft.item.ItemStack",item);}
    static Object book(String item,boolean filled)throws Exception{
        Object b=stack(field("am2.items.ItemsCommonProxy",item));
        if(filled){Object spell=stack(field("am2.items.ItemsCommonProxy","spell"));call(spell,"func_77982_d",make("net.minecraft.nbt.NBTTagCompound"));call(field("am2.spell.SpellUtils","instance"),"addSpellStageToScroll",spell,"Touch",new String[]{"FireDamage"},new String[0]);call(spell,"func_151001_c","Ember Primer");Object[] slots=(Object[])Array.newInstance(Class.forName("net.minecraft.item.ItemStack"),8);slots[0]=spell;call(call(b,"func_77973_b"),"UpdateStackTagCompound",b,slots);}
        return b;
    }
    @Mod.EventHandler public void init(FMLInitializationEvent e){
        ModContainer am2=Loader.instance().getIndexedModList().get("arsmagica2");
        if(!baseline)need(am2.getVersion().equals(am2.getDisplayVersion()),"maintenance version hidden in Mods UI");
        results.add("VERSION="+am2.getVersion()+"; DISPLAY="+am2.getDisplayVersion());
        FMLCommonHandler.instance().bus().register(this);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(this);
    }
    @SubscribeEvent public void server(TickEvent.ServerTickEvent e){if(prepared||finished||e.phase!=TickEvent.Phase.END)return;try{
        Object server=stat("net.minecraft.server.MinecraftServer","func_71276_C");if(server==null)return;List<?> players=(List<?>)get(call(server,"func_71203_ab"),"field_72404_b");if(players.isEmpty())return;
        Object p=players.get(0),w=get(p,"field_70170_p"),inv=get(p,"field_71071_by");Object[] slots=(Object[])get(inv,"field_70462_a");slots[0]=book("spellBook",true);slots[1]=book("arcaneSpellbook",true);slots[2]=book("spellBook",false);
        call(call(w,"func_82736_K"),"func_82764_b","doMobSpawning","false");call(w,"func_72877_b",6000L);
        Object stone=field("net.minecraft.init.Blocks","field_150348_b");for(int x=-12;x<85;x++)for(int z=-12;z<85;z++)call(w,"func_147465_d",x,79,z,stone,0,3);
        call(p,"func_70634_a",32.5,80.0,32.5);
        Object table=field("am2.blocks.BlocksCommonProxy","inscriptionTable");call(w,"func_147465_d",36,80,36,table,0,3);
        Object container=call(make("am2.proxy.gui.ServerGuiManager"),"getServerGuiElement",1,p,w,36,80,36);
        need(container!=null&&container.getClass().getName().equals("am2.containers.ContainerInscriptionTable"),"server inscription container failed with Playground");
        need(((List<?>)get(container,"field_75151_b")).size()>=37,"server inscription slots missing");pass("INSCRIPTION_CONTAINER_WITH_PLAYGROUND");prepared=true;
    }catch(Throwable t){fail(t);}}
    static void select(int slot,int camera)throws Exception{set(get(player,"field_71071_by"),"field_70461_c",slot);set(get(mc,"field_71474_y"),"field_74320_O",camera);set(player,"field_70177_z",0.0f);set(player,"field_70126_B",0.0f);set(player,"field_70125_A",8.0f);set(player,"field_70127_C",8.0f);}
    static void use(boolean down)throws Exception{keyDown=down;Object key=get(get(mc,"field_71474_y"),"field_74313_G");int code=((Number)call(key,"func_151463_i")).intValue();stat("net.minecraft.client.settings.KeyBinding","func_74510_a",code,down);if(down)call(mc,"func_147121_ag");}
    static void capture(String name){pendingCapture=name;captureTick=ticks;}
    static float angle()throws Exception{return ((Number)get(get(model,"field_78100_b"),"field_78796_g")).floatValue();}
    static void reportState(String name)throws Exception{results.add(name+": countdown="+call(player,"func_71052_bv")+", modelAngle="+angle());}
    @SubscribeEvent(priority=cpw.mods.fml.common.eventhandler.EventPriority.HIGHEST)
    public void prepareLocalAtmosphere(TickEvent.ClientTickEvent e){
        if(finished||player==null||e.phase!=TickEvent.Phase.END||ticks<1030||ticks>=1370)return;
        try{Object held=call(player,"func_70694_bm");if(held!=null)call(player,"func_71008_a",held,71900);}catch(Throwable t){fail(t);}
    }
    @SubscribeEvent public void client(TickEvent.ClientTickEvent e){if(finished||e.phase!=TickEvent.Phase.END)return;try{
        if(mc==null)mc=stat("net.minecraft.client.Minecraft","func_71410_x");
        if(!launched){Object screen=get(mc,"field_71462_r");if(screen==null)return;String n=screen.getClass().getName();if(!n.endsWith("GuiMainMenu")&&!n.endsWith("CinematicMenu")&&!n.startsWith("DummyCore.Client.GuiMainMenu")&&!n.endsWith("AetherMainMenu"))return;launched=true;Object type=Enum.valueOf((Class)Class.forName("net.minecraft.world.WorldSettings$GameType"),"CREATIVE");call(mc,"func_71371_a","book-animation-disposable","Book Animation Regression",make("net.minecraft.world.WorldSettings",42L,type,false,false,field("net.minecraft.world.WorldType","field_77138_c")));return;}
        player=get(mc,"field_71439_g");world=get(mc,"field_71441_e");if(!prepared||player==null||world==null)return;
        Object[] readySlots=(Object[])get(get(player,"field_71071_by"),"field_70462_a");
        if(readySlots[0]==null)return;
        if(ticks==0&&(readySlots[1]==null||call(readySlots[0],"func_77973_b")!=field("am2.items.ItemsCommonProxy","spellBook")
                ||call(readySlots[1],"func_77973_b")!=field("am2.items.ItemsCommonProxy","arcaneSpellbook")
                ||((Number)get(player,"field_70163_u")).doubleValue()<79.5))return;
        // Packs can auto-open starter manuals after joining. They obscure the
        // render and release all keys, so close them only in this disposable test.
        if(get(mc,"field_71462_r")!=null&&!(ticks>=391&&ticks<396)){call(mc,"func_147108_a",(Object)null);call(mc,"func_71381_h");}
        if(renderer==null){renderer=field("am2.items.renderers.SpellScrollRenderer","instance");model=get(renderer,"modelBook");}
        ticks++;if(galleryOnly&&ticks==1)ticks=1430;set(mc,"field_71415_G",true);
        if(ticks==1){select(0,0);call(mc,"func_71381_h");}
        if(ticks==20)capture("01-ordinary-closed");
        if(ticks==30)use(true);
        if(ticks==52)capture("02-ordinary-casting");
        if(ticks==62){reportState("ORDINARY_CAST");need(angle()<-.5f,"ordinary book did not open");use(false);}
        if(ticks==84)capture("03-ordinary-released");
        if(ticks==92)select(1,0);
        if(ticks==114)capture("04-arcane-closed");
        if(ticks==124)use(true);
        if(ticks==148)capture("05-arcane-casting");
        if(ticks==158){reportState("ARCANE_CAST");if(baseline)need(Math.abs(angle()-777)<0.01,"baseline arcane route unexpectedly animates");else need(angle()<-.5f&&angle()>-2,"arcane book is not opening");pass(baseline?"BASELINE_ARCANE_STATIC_ROUTE_REPRODUCED":"ARCANE_BOOK_ANIMATED_ROUTE");select(1,2);}
        if(ticks==186)capture("06-arcane-third-person");
        if(ticks==196){use(false);select(0,2);}
        if(ticks==204)use(true);
        if(ticks==230)capture("07-ordinary-third-person");
        if(ticks==240){use(false);select(2,0);}
        if(ticks==262)capture("08-empty-closed");
        if(ticks==272)use(true);
        if(ticks==298)capture("09-empty-use-key");
        if(ticks==308){reportState("EMPTY_USE_KEY");if(!baseline){need(angle()<-.5f,"empty book ignores the use key");pass("EMPTY_BOOK_OPENS_ON_BOUND_USE_KEY");}use(false);}
        if(ticks==330)capture("10-empty-released");
        if(ticks==340){
            select(3,0);remote=make("net.minecraft.client.entity.EntityOtherPlayerMP",world,make("com.mojang.authlib.GameProfile",UUID.nameUUIDFromBytes("BookRemote".getBytes("UTF-8")),"BookRemote"));call(remote,"func_70107_b",32.5,80.0,35.5);set(remote,"field_70177_z",180.0f);set(remote,"field_70126_B",180.0f);Object[] items=(Object[])get(get(remote,"field_71071_by"),"field_70462_a");items[0]=book("arcaneSpellbook",true);call(world,"func_73027_a",880003,remote);
        }
        if(ticks==374)capture("11-remote-player-casting");
        if(ticks==390){if(!baseline){Object held=call(remote,"func_70694_bm");need((Boolean)stat("am2.client.vfx.CastingAnimationState","isCasting",remote,held),"remote synchronized use flag ignored");float open=((Number)stat("am2.client.vfx.CastingAnimationState","getOpenProgress",remote,held,1.0f)).floatValue();need(open>.9f,"remote book stayed closed");pass("REMOTE_BOOK_USES_SYNCHRONIZED_FLAG");}pass("BOOK_ANIMATION_REGRESSION");use(false);select(3,0);call(world,"func_73028_b",880003);remote=null;}
        if(ticks==391){
            Object settings=get(mc,"field_71474_y");
            String original="key.categories.misc",translated=(String)stat("net.minecraft.client.resources.I18n","func_135052_a",original,new Object[0]);
            need(!original.equals(translated),"category collision setup requires translated controls");
            String[] names={"audit.a","audit.b","audit.c","audit.d"};
            for(int i=0;i<4;i++)stat("cpw.mods.fml.client.registry.ClientRegistry","registerKeyBinding",make("net.minecraft.client.settings.KeyBinding",names[i],0,i%2==0?original:translated));
            Object controls=make("net.minecraft.client.gui.GuiControls",make("net.minecraft.client.gui.GuiOptions",null,settings),settings);
            call(mc,"func_147108_a",controls);pass("CONTROLS_TRANSLATED_CATEGORY_COLLISION");capture("controls-category-collision");
        }
        if(ticks==396){call(mc,"func_147108_a",(Object)null);call(mc,"func_71381_h");}
        if(ticks==405)setupCasters();
        if(ticks>=405&&ticks<1005&&((ticks-405)%120)==0){
            int scene=(ticks-405)/120;
            for(Object p:casters)equipCaster(p,scene);
            results.add("SCENE="+scene);
        }
        if(ticks>=405&&ticks<1005&&((ticks-405)%120)==110)capture("vfx-tier-"+((ticks-405)/120));
        if(ticks==1010){
            need(fallingKinds.containsAll(Arrays.asList("FIRESTORM","BLIZZARD","STARFALL")),"missing falling families: "+fallingKinds);
            need(impactKinds.containsAll(Arrays.asList("FIRESTORM","BLIZZARD","STARFALL")),"missing impact families: "+impactKinds);
            need(boltKinds.containsAll(Arrays.asList("LUNAR_LIGHTNING","STORM_LIGHTNING")),"missing bolt families: "+boltKinds);
            need(weatherPhases==7,"missing weather stages: "+weatherPhases);pass("ALL_FIVE_ATMOSPHERIC_FAMILIES_AND_THREE_WEATHER_PHASES");
            need(peakFalling>192&&peakSky>0&&widestFallRadius>48,"apocalypse density/sky not exercised");results.add("PEAK_FALLING="+peakFalling);results.add("WIDEST_FALL_RADIUS="+widestFallRadius);results.add("PEAK_SKY_CANOPIES="+peakSky);pass("APOCALYPSE_DENSITY_AND_SHARED_SKY_BUDGET");
            for(String type:Arrays.asList("FIRESTORM","BLIZZARD","STARFALL","LUNAR_LIGHTNING","STORM_LIGHTNING")){
                Object value=Enum.valueOf((Class)Class.forName("am2.client.vfx.CastingCircleVFX$SuperTierType"),type);
                need(((Boolean)stat(ATM,"usesStormClouds",value)).booleanValue()==type.equals("STORM_LIGHTNING"),"wrong storm cloud style: "+type);
            }pass("ONLY_STORM_LIGHTNING_KEEPS_ORIGINAL_CLOUDS");
            if(!before){
                Object bad=book("spellBook",true),spell=call(call(bad,"func_77973_b"),"GetActiveItemStack",bad);
                Object tag=call(spell,"func_77978_p");call(tag,"func_74768_a","NumStages",Integer.MAX_VALUE);
                long start=System.nanoTime();need(((Number)stat("am2.client.vfx.CastingCircleVFX","getLunarCount",spell)).intValue()==0,"unbounded invalid spell");
                need(System.nanoTime()-start<100000000L,"invalid spell did not fail quickly");pass("MALFORMED_STAGE_COUNT_BOUNDED");
                call(tag,"func_74768_a","NumStages",1);call(tag,"func_74768_a","ForcedAffinity",Integer.MAX_VALUE);
                need(stat("am2.client.vfx.CastingCircleVFX","safeAffinity",spell).toString().equals("NONE"),"bad affinity not contained");pass("MALFORMED_AFFINITY_CONTAINED");
                stat("am2.client.vfx.CastingCircleVFX","clearAll");
                need(((Map<?,?>)field("am2.client.vfx.CastingCircleVFX","visualStates")).isEmpty(),"world visual cache retained players");pass("WORLD_VISUAL_CACHE_CLEANUP");
            }
            need(particleChecked&&benchmarkDone,"performance checks incomplete");pass("VFX_SAFETY_REGRESSION");
            for(int i=0;i<casters.size();i++)call(world,"func_73028_b",880100+i);casters.clear();
        }
        if(ticks>=1030&&ticks<1250&&(ticks-1030)%20==0){
            int affinity=(ticks-1030)/20;Object b=book("arcaneSpellbook",true),spell=call(call(b,"func_77973_b"),"GetActiveItemStack",b);
            call(call(spell,"func_77978_p"),"func_74768_a","ForcedAffinity",affinity);
            Object[] entries=(Object[])Array.newInstance(Class.forName("net.minecraft.item.ItemStack"),8);entries[0]=spell;
            call(call(b,"func_77973_b"),"UpdateStackTagCompound",b,entries);localBook(b);select(8,2);
        }
        if(ticks>=1030&&ticks<1250&&(ticks-1030)%20==14){
            int i=(ticks-1030)/20;Object held=call(player,"func_70694_bm"),spell=call(call(held,"func_77973_b"),"GetActiveItemStack",held);
            need(stat("am2.client.vfx.CastingCircleVFX","safeAffinity",spell).toString().equals(affinities[i]),"affinity mismatch");
            capture("normal-affinity-"+affinities[i]);
        }
        if(ticks==1250&&!testFreecam){
            pass("ALL_ELEVEN_AFFINITY_PALETTES");testWeatherRestoration();stat(ATM,"reset");stat("am2.client.vfx.CastingCircleVFX","clearAll");
            need(((Map<?,?>)field("am2.client.vfx.CastingCircleVFX","bodyPositions")).isEmpty(),"body cache retained players");pass("BODY_POSITION_CACHE_CLEANUP");finish(0);return;
        }
        if(ticks==1250&&testFreecam){pass("ALL_ELEVEN_AFFINITY_PALETTES");select(8,0);freecam=field("com.zergatul.freecam.FreeCam","INSTANCE");
            bodyX=((Number)get(player,"field_70165_t")).doubleValue();bodyY=((Number)get(player,"field_70163_u")).doubleValue();bodyZ=((Number)get(player,"field_70161_v")).doubleValue();
            call(freecam,"enable");need((Boolean)call(freecam,"isActive"),"Freecam did not activate");cameraOffset(14,9,-16);
            localBook(book("arcaneSpellbook",true));
        }
        if(ticks==1275)capture("freecam-normal-body-anchor");
        if(ticks==1290)localBook(tierBook(2));
        if(ticks==1320)capture("freecam-solar-body-anchor");
        if(ticks==1330)cameraOffset(-16,10,-14);
        if(ticks==1360)capture("freecam-moved-camera-body-anchor");
        if(ticks==1370){
            call(player,"func_71034_by");select(3,0);
            call(freecam,"disable");need(!(Boolean)call(freecam,"isActive"),"Freecam did not deactivate");
            need(Math.abs(((Number)get(player,"field_70165_t")).doubleValue()-bodyX)<.05,"Freecam did not restore body");
            need(freecamChecked&&particleOriginChecked,"Freecam anchor checks incomplete");pass("FREECAM_RESTORED_BODY_AND_PARTICLES");
        }
        if(ticks==1372)set(get(mc,"field_71474_y"),"field_74362_aa",2);
        if(ticks==1375){
            for(String list:Arrays.asList("falling","impacts","bolts"))need(((List<?>)field(ATM,list)).isEmpty(),"minimal particles kept scenery");
            pass("MINIMAL_PARTICLES_CLEAR_ATMOSPHERIC_SCENERY");set(get(mc,"field_71474_y"),"field_74362_aa",1);
        }
        if(ticks==1420){
            need(((Number)field(ATM,"currentIntensity")).floatValue()==0,"cancel did not fade sky");pass("ATMOSPHERE_FADES_AFTER_CANCEL");
            testWeatherRestoration();
            stat(ATM,"reset");for(String list:Arrays.asList("falling","impacts","bolts","visibleSky"))need(((List<?>)field(ATM,list)).isEmpty(),"atmosphere reset retained objects");
            need(((Map<?,?>)field(ATM,"skyCells")).isEmpty(),"sky cells retained after reset");
            pass("ATMOSPHERE_UNLOAD_CLEANUP");
            stat("am2.client.vfx.CastingCircleVFX","clearAll");need(((Map<?,?>)field("am2.client.vfx.CastingCircleVFX","bodyPositions")).isEmpty(),"body cache retained players");
            pass("BODY_POSITION_CACHE_CLEANUP");pass("FREECAM_COMPATIBILITY_REGRESSION");if(!gallery)finish(0);
        }
        if(gallery&&ticks==1430){
            select(3,0);remote=make("net.minecraft.client.entity.EntityOtherPlayerMP",world,make("com.mojang.authlib.GameProfile",UUID.nameUUIDFromBytes("ElementMage".getBytes("UTF-8")),"ElementMage"));
            call(remote,"func_70107_b",32.5,80.0,48.5);set(remote,"field_70177_z",180.0f);set(remote,"field_70126_B",180.0f);call(world,"func_73027_a",888900,remote);
            freecam=field("com.zergatul.freecam.FreeCam","INSTANCE");bodyX=((Number)get(player,"field_70165_t")).doubleValue();bodyY=((Number)get(player,"field_70163_u")).doubleValue();bodyZ=((Number)get(player,"field_70161_v")).doubleValue();
            call(freecam,"enable");cameraOffset(0,14,-36);set(freecam,"pitch",5.0f);call(freecam,"calculateVectors");set(get(mc,"field_71474_y"),"field_74319_N",true);
        }
        if(gallery&&ticks>=1430&&ticks<1880&&(ticks-1430)%90==0){
            equipCaster(remote,new int[]{1,2,3,4,0}[(ticks-1430)/90]);set(freecam,"pitch",5.0f);call(freecam,"calculateVectors");set(get(mc,"field_71474_y"),"field_74362_aa",0);
        }
        if(gallery&&ticks>=1430&&ticks<1880&&(ticks-1430)%90==75){set(freecam,"pitch",-10.0f);call(freecam,"calculateVectors");}
        if(gallery&&ticks>=1430&&ticks<1880&&((ticks-1430)%90==65||(ticks-1430)%90==80))capture("gallery-"+new String[]{"ice","fire","stars","storm","lunar"}[(ticks-1430)/90]+"-"+((ticks-1430)%90));
        if(gallery&&ticks==1890){call(world,"func_73028_b",888900);remote=null;call(freecam,"disable");set(get(mc,"field_71474_y"),"field_74319_N",false);}
        if(gallery&&ticks==1930){need(((Number)field(ATM,"currentIntensity")).floatValue()==0,"gallery cast did not fade");need(((Map<?,?>)field(ATM,"skyCells")).isEmpty()&&((List<?>)field(ATM,"visibleSky")).isEmpty(),"clouds did not fade");pass("ATMOSPHERE_GALLERY");finish(0);}
    }catch(Throwable t){fail(t);}}
    static void atmosphereFrame(float partial)throws Exception{
        for(String name:Arrays.asList("falling","impacts","bolts")){
            List<?> objects=(List<?>)field(ATM,name);int limit=name.equals("falling")?384:name.equals("impacts")?96:40;
            need(objects.size()<=limit,"atmosphere budget exceeded");
            Set<String> kinds=name.equals("falling")?fallingKinds:name.equals("impacts")?impactKinds:boltKinds;
            for(Object object:objects)kinds.add(get(object,"type").toString());
        }
        if(ticks>=430&&ticks<1005)for(Object drop:(List<?>)field(ATM,"falling")){
            Object owner=call(world,"func_73045_a",((Number)get(drop,"owner")).intValue());
            if(owner!=null){double dx=((Number)get(drop,"x")).doubleValue()-((Number)get(owner,"field_70165_t")).doubleValue();double dz=((Number)get(drop,"z")).doubleValue()-((Number)get(owner,"field_70161_v")).doubleValue();widestFallRadius=Math.max(widestFallRadius,Math.sqrt(dx*dx+dz*dz));}
        }
        peakFalling=Math.max(peakFalling,((List<?>)field(ATM,"falling")).size());
        int sky=((Map<?,?>)field(ATM,"skyCells")).size(),visible=((List<?>)field(ATM,"visibleSky")).size();
        peakSky=Math.max(peakSky,visible);need(sky<=16&&visible<=3,"sky budget exceeded");
        if(ticks>=460&&ticks<=1004)need(visible<=1,"nearby equal storms did not share clouds");
        float target=((Number)field(ATM,"targetIntensity")).floatValue();
        if(Math.abs(target-.35f)<.01f)weatherPhases|=1;if(Math.abs(target-.70f)<.01f)weatherPhases|=2;if(target>.99f)weatherPhases|=4;
        if(!atmosphereFrameChecked&&!((List<?>)field(ATM,"falling")).isEmpty()){
            while(org.lwjgl.opengl.GL11.glGetError()!=0){}
            int count=((List<?>)field(ATM,"falling")).size();
            org.lwjgl.opengl.GL11.glPushAttrib(org.lwjgl.opengl.GL11.GL_SCISSOR_BIT);org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_SCISSOR_TEST);org.lwjgl.opengl.GL11.glScissor(0,0,0,0);
            for(int i=0;i<30;i++)stat(ATM,"renderAll",partial);
            org.lwjgl.opengl.GL11.glPopAttrib();
            need(((List<?>)field(ATM,"falling")).size()==count,"frame rendering spawned objects");
            need(org.lwjgl.opengl.GL11.glGetError()==0,"atmospheric renderer leaked OpenGL error");
            pass("ATMOSPHERIC_DRAW_IS_FPS_INDEPENDENT_AND_GL_VALID");atmosphereFrameChecked=true;
        }
    }
    static void testWeatherRestoration()throws Exception{
        Field[] fields=(Field[])field(ATM,"WEATHER_FIELDS");float[] original=new float[4],expected={.23f,.42f,.61f,.68f};
        for(int i=0;i<4;i++){original[i]=fields[i].getFloat(world);fields[i].setFloat(world,expected[i]);}
        set(Class.forName(ATM),"currentIntensity",.8f);set(Class.forName(ATM),"previousIntensity",.75f);
        stat(ATM,"beginRender",.5f);need(((Number)call(world,"func_72867_j",1.0f)).floatValue()>=.8f,"render weather overlay missing");
        stat(ATM,"endRender");
        for(int i=0;i<4;i++)need(fields[i].getFloat(world)==expected[i],"natural weather field not restored exactly: "+i);
        float thunder=((Number)call(world,"func_72819_i",1.0f)).floatValue();need(Math.abs(thunder-(.68f*.42f))<.000001,"natural thunder mapping incorrect");
        for(int i=0;i<4;i++)fields[i].setFloat(world,original[i]);set(Class.forName(ATM),"currentIntensity",0.0f);set(Class.forName(ATM),"previousIntensity",0.0f);
        pass("NATURAL_WEATHER_RESTORED_EXACTLY_AFTER_RENDER");
    }
    static void localBook(Object b)throws Exception{
        call(player,"func_71034_by");Object[] slots=(Object[])get(get(player,"field_71071_by"),"field_70462_a");slots[8]=b;set(get(player,"field_71071_by"),"field_70461_c",8);
    }
    static void cameraOffset(double x,double y,double z)throws Exception{
        set(freecam,"x",bodyX+x);set(freecam,"y",bodyY+y);set(freecam,"z",bodyZ+z);
        set(freecam,"yaw",(float)(Math.toDegrees(Math.atan2(-z,-x))-90.0));
        set(freecam,"pitch",(float)(-Math.toDegrees(Math.atan2(-y-1.0,Math.sqrt(x*x+z*z)))));call(freecam,"calculateVectors");
    }
    static void checkFreecam()throws Exception{
        if(ticks<1252||ticks>=1370)return;
        need(get(freecam,"overriddenEntity")==player,"Freecam is not overriding local entity at RenderWorldLast");
        double liveX=((Number)get(player,"field_70165_t")).doubleValue();
        need(Math.abs(liveX-bodyX)>10,"Freecam coordinates did not separate from body");
        Object body=((Map<?,?>)field("am2.client.vfx.CastingCircleVFX","bodyPositions")).get(player);
        need(body!=null,"body position was not sampled before camera override");
        double x=((Number)get(body,"x")).doubleValue(),y=((Number)get(body,"y")).doubleValue(),z=((Number)get(body,"z")).doubleValue();
        need(Math.abs(x-bodyX)<.05&&Math.abs(y-bodyY)<.05&&Math.abs(z-bodyZ)<.05,"circle snapshot moved to Freecam");
        if(!freecamChecked){results.add("FREECAM_LIVE_X="+liveX+"; BODY_X="+x+"; BODY_Y="+y+"; BODY_Z="+z);pass("FREECAM_RENDER_USES_REAL_BODY_SNAPSHOT");freecamChecked=true;}
        if(!particleOriginChecked&&ticks<1290){
            Object pr=get(get(field("am2.AMCore","proxy"),"particleManager"),"particleRenderer");
            List<?> particles=(List<?>)get(pr,"deferredParticles");
            for(Object particle:particles){
                double px=((Number)get(particle,"field_70165_t")).doubleValue(),pz=((Number)get(particle,"field_70161_v")).doubleValue();
                double dx=px-bodyX,dz=pz-bodyZ;
                need(dx*dx+dz*dz<5,"normal circle particle followed camera");particleOriginChecked=true;
            }
            if(particleOriginChecked)pass("FREECAM_PARTICLES_USE_BODY_ORIGIN");
        }
    }
    static Object tierBook(int scene)throws Exception{
        String[] components={"LightningDamage","Blizzard","FireRain","FallingStar","Storm"};
        String modifier=scene==0?"Lunar":"Solar";
        Object b=stack(field("am2.items.ItemsCommonProxy","arcaneSpellbook")),spell=stack(field("am2.items.ItemsCommonProxy","spell"));
        call(spell,"func_77982_d",make("net.minecraft.nbt.NBTTagCompound"));
        call(field("am2.spell.SpellUtils","instance"),"addSpellStageToScroll",spell,"Touch",new String[]{components[scene]},new String[]{modifier,modifier,modifier});
        Object[] slots=(Object[])Array.newInstance(Class.forName("net.minecraft.item.ItemStack"),8);slots[0]=spell;
        call(call(b,"func_77973_b"),"UpdateStackTagCompound",b,slots);return b;
    }
    static void equipCaster(Object p,int scene)throws Exception{
        Object[] slots=(Object[])get(get(p,"field_71071_by"),"field_70462_a");slots[0]=tierBook(scene);
    }
    static void setupCasters()throws Exception{
        for(int i=0;i<8;i++){
            Object p=make("net.minecraft.client.entity.EntityOtherPlayerMP",world,make("com.mojang.authlib.GameProfile",UUID.nameUUIDFromBytes(("Caster"+i).getBytes("UTF-8")),"Caster"+i));
            call(p,"func_70107_b",27.5+(i%4)*3,80.0,37.5+(i/4)*3);equipCaster(p,0);casters.add(p);call(world,"func_73027_a",880100+i,p);
        }
        set(get(mc,"field_71474_y"),"field_74362_aa",0);
    }
    static int particleCount()throws Exception{
        Object pr=get(get(field("am2.AMCore","proxy"),"particleManager"),"particleRenderer");
        return ((List<?>)get(pr,"deferredParticles")).size()+((List<?>)get(pr,"deferredRadiants")).size()+((List<?>)get(pr,"deferredBlocks")).size();
    }
    @SubscribeEvent(priority=cpw.mods.fml.common.eventhandler.EventPriority.LOWEST)
    public void worldRender(net.minecraftforge.client.event.RenderWorldLastEvent event){
        if(finished||ticks<430)return;
        try{
            checkFreecam();atmosphereFrame(event.partialTicks);
            if(casters.isEmpty())return;
            if(!particleChecked&&((Number)get(casters.get(0),"field_70173_aa")).intValue()%6==0){
                int a=particleCount();Object fire=Enum.valueOf((Class)Class.forName("am2.api.spell.enums.Affinity"),"FIRE");
                for(int i=0;i<30;i++)stat("am2.client.vfx.CastingCircleVFX","renderNormalPlayerCircle",100000.0,0.0,100000.0,casters.get(0),fire,1.0f,0.0f);
                int delta=particleCount()-a;results.add("PARTICLES_FROM_30_RENDERS_SAME_TICK="+delta);
                need(before?delta>=20:delta<=1,"particle emission still depends on frame count");particleChecked=true;pass(before?"PARTICLE_FPS_LEAK_REPRODUCED":"PARTICLES_LIMITED_BY_TICK");
            }
            if(!benchmarkDone&&ticks>=500){
                Object settings=get(mc,"field_71474_y");int particles=((Number)get(settings,"field_74362_aa")).intValue();set(settings,"field_74362_aa",2);
                Object bean=java.lang.management.ManagementFactory.getThreadMXBean();long thread=Thread.currentThread().getId();
                long a=((Number)call(bean,"getThreadAllocatedBytes",thread)).longValue();long start=System.nanoTime();
                org.lwjgl.opengl.GL11.glPushAttrib(org.lwjgl.opengl.GL11.GL_SCISSOR_BIT);org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_SCISSOR_TEST);org.lwjgl.opengl.GL11.glScissor(0,0,0,0);
                for(int i=0;i<50;i++)stat("am2.client.vfx.CastingCircleVFX","renderAll",0.0f);
                org.lwjgl.opengl.GL11.glPopAttrib();
                long elapsed=System.nanoTime()-start,allocated=((Number)call(bean,"getThreadAllocatedBytes",thread)).longValue()-a;
                results.add("CACHED_50_RENDERS_CPU_MS="+(elapsed/1000000.0));results.add("CACHED_50_RENDERS_ALLOCATED_BYTES="+allocated);
                set(settings,"field_74362_aa",particles);benchmarkDone=true;pass("EIGHT_CASTER_REPEATED_RENDER_COMPLETED");
            }
        }catch(Throwable t){fail(t);}
    }
    @SubscribeEvent public void render(TickEvent.RenderTickEvent e){if(finished||renderer==null)return;try{
        if(e.phase==TickEvent.Phase.START){
            // A real ModelBook render must overwrite this sentinel every frame.
            if(ticks>=124&&ticks<=158)set(get(model,"field_78100_b"),"field_78796_g",777.0f);
            if(remote!=null)call(remote,"func_70052_a",4,true);
            if(gallery&&remote!=null&&ticks>=1430&&ticks<1890){Object held=call(remote,"func_70694_bm");if(held!=null)call(remote,"func_71008_a",held,71900);}
            if(ticks>=405&&ticks<1005)for(Object p:casters){
                Object held=call(p,"func_70694_bm");call(p,"func_71008_a",held,72000-((ticks-405)%120));call(p,"func_70052_a",4,true);
            }
            if(ticks>=1030&&ticks<1370){Object held=call(player,"func_70694_bm");call(player,"func_71008_a",held,71900);}
            if(player!=null)set(mc,"field_71415_G",true);
            Object useKey=get(get(mc,"field_71474_y"),"field_74313_G");
            stat("net.minecraft.client.settings.KeyBinding","func_74510_a",((Number)call(useKey,"func_151463_i")).intValue(),keyDown);
        }else if(pendingCapture!=null&&ticks>=captureTick){
            stat("net.minecraft.util.ScreenShotHelper","func_148259_a",new File("."),pendingCapture+".png",((Number)get(mc,"field_71443_c")).intValue(),((Number)get(mc,"field_71440_d")).intValue(),call(mc,"func_147110_a"));results.add("SCREENSHOT="+pendingCapture);pendingCapture=null;
        }
    }catch(Throwable t){fail(t);}}
}
