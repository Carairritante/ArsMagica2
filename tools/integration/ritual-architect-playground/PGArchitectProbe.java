package pgarchitectprobe;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLServerStartedEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.event.world.BlockEvent;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

/** Temporary integration mod. Never installed in the user's actual instance. */
@Mod(modid="pgarchitectprobe",name="Temporary Playground Architect Compat Probe",version="1",dependencies="required-after:arsmagica2")
public final class PGArchitectProbe {
 static final String ENGINE="am2.ritual.RitualArchitect", ITEM="am2.items.ItemRitualArchitect";
 static Object world,player,inventory,tool,item,props,server,stone,air;
 static final List<String> log=new ArrayList<String>();
 static final int X=40,Y=84,Z=40;
 static final PlaceVeto veto=new PlaceVeto();
 static final Map<String,List<Method>> methodCache=new HashMap<String,List<Method>>();

 public static final class PlaceVeto {
  public boolean enabled;
  public int events;
  @SubscribeEvent public void placed(BlockEvent.PlaceEvent event){if(enabled){events++;event.setCanceled(true);}}
 }
 static List<Method> methods(Class<?> type,String name,int count,boolean onlyStatic){
  String key=type.getName()+"#"+name+"#"+count+"#"+onlyStatic;
  List<Method> result=methodCache.get(key);if(result!=null)return result;
  result=new ArrayList<Method>();for(Method m:type.getMethods())if(m.getName().equals(name)&&m.getParameterTypes().length==count&&(!onlyStatic||Modifier.isStatic(m.getModifiers())))result.add(m);
  methodCache.put(key,result);return result;
 }
 static Object call(Object target,String name,Object...args)throws Exception {
  for(Method m:methods(target.getClass(),name,args.length,false))
   try{return m.invoke(target,args);}catch(IllegalArgumentException ignored){}
  throw new NoSuchMethodException(target.getClass().getName()+"."+name);
 }
 static Object stat(String type,String name,Object...args)throws Exception {
  for(Method m:methods(Class.forName(type),name,args.length,true))
   try{return m.invoke(null,args);}catch(IllegalArgumentException ignored){}
  throw new NoSuchMethodException(type+"."+name);
 }
 static Object field(String type,String name)throws Exception{return Class.forName(type).getField(name).get(null);}
 static Object get(Object target,String name)throws Exception {
  for(Class<?> type=target.getClass();type!=null;type=type.getSuperclass())try{Field f=type.getDeclaredField(name);f.setAccessible(true);return f.get(target);}catch(NoSuchFieldException ignored){}
  throw new NoSuchFieldException(name);
 }
 static void set(Object target,String name,Object value)throws Exception {
  for(Class<?> type=target.getClass();type!=null;type=type.getSuperclass())try{Field f=type.getDeclaredField(name);f.setAccessible(true);f.set(target,value);return;}catch(NoSuchFieldException ignored){}
  throw new NoSuchFieldException(name);
 }
 static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
 static int number(Object object){return ((Number)object).intValue();}
 static boolean yes(Object object){return Boolean.TRUE.equals(object);}
 static Object compound()throws Exception{return Class.forName("net.minecraft.nbt.NBTTagCompound").newInstance();}
 static Object stack(Object value,int count,int meta)throws Exception{return Class.forName("net.minecraft.item.ItemStack").getConstructor(Class.forName("net.minecraft.item.Item"),int.class,int.class).newInstance(value,count,meta);}
 static void block(int x,int y,int z,Object b)throws Exception{require(yes(call(world,"func_147465_d",x,y,z,b,0,3))||call(world,"func_147439_a",x,y,z)==b,"Could not set fixture block");}
 static Object blockAt(int x,int y,int z)throws Exception{return call(world,"func_147439_a",x,y,z);}
 static void creative(boolean enabled)throws Exception{set(get(player,"field_71075_bZ"),"field_75098_d",enabled);}
 static void position(double x,double y,double z)throws Exception{call(player,"func_70107_b",x,y,z);}
 static void clearItems()throws Exception {
  for(Object entity:new ArrayList<Object>((List<?>)get(world,"field_72996_f")))
   if(Class.forName("net.minecraft.entity.item.EntityItem").isInstance(entity)){call(entity,"func_70106_y");call(world,"func_72900_e",entity);}
 }
 static void prepare(Object entry,int rotation)throws Exception {
  clearItems();
  for(int dx=-5;dx<=5;dx++)for(int dz=-5;dz<=5;dz++){
   block(X+dx,Y-1,Z+dz,stone);block(X+dx,Y,Z+dz,air);block(X+dx,Y+1,Z+dz,air);
  }
  position(X+0.5,Y+1,Z+0.5);
  stat(ENGINE,"select",tool,number(get(entry,"index")),rotation);
  stat(ENGINE,"setAnchor",tool,world,X,Y,Z);
 }
 static String snapshot()throws Exception {
  StringBuilder s=new StringBuilder();
  for(int dx=-5;dx<=5;dx++)for(int dy=-1;dy<=1;dy++)for(int dz=-5;dz<=5;dz++)
   s.append(stat("net.minecraft.block.Block","func_149682_b",blockAt(X+dx,Y+dy,Z+dz))).append(':').append(call(world,"func_72805_g",X+dx,Y+dy,Z+dz)).append(',');
  return s.toString();
 }
 static Object status()throws Exception{return stat(ENGINE,"status",world,tool);}
 static String build()throws Exception{return (String)stat(ENGINE,"build",player,tool);}
 static boolean built(String result){return "am2.architect.built".equals(result)||"am2.architect.already_built".equals(result);}
 static Object spawn(Object s,double x,double y,double z)throws Exception {
  Object entity=Class.forName("net.minecraft.entity.item.EntityItem").getConstructor(Class.forName("net.minecraft.world.World"),double.class,double.class,double.class,Class.forName("net.minecraft.item.ItemStack")).newInstance(world,x,y,z,s);
  if(yes(call(world,"func_72838_d",entity)))return entity;
  // Forge replaces ordinary EntityItems for AM2 runes with EntityItemRune and
  // cancels the original spawn. Follow that real replacement rather than
  // treating the successful custom-entity hook as a failed fixture.
  for(Object replacement:(List<?>)get(world,"field_72996_f"))
   if(Class.forName("net.minecraft.entity.item.EntityItem").isInstance(replacement)&&!yes(get(replacement,"field_70128_L"))&&call(replacement,"func_92059_d")==s)return replacement;
  throw new AssertionError("Could not spawn fixture reagent or locate Forge custom replacement");
 }
 static List<Object> spawnReagents(Object entry,boolean doubled)throws Exception {
  List<Object> result=new ArrayList<Object>();
  Object expected=call(call(entry,"interaction"),"getReagents");
  for(int n=0;n<Array.getLength(expected);n++){
   Object s=call(Array.get(expected,n),"func_77946_l");if(doubled)set(s,"field_77994_a",number(get(s,"field_77994_a"))*2);
   result.add(spawn(s,X+0.5,Y+0.1,Z+0.5));
  }
  return result;
 }
 static String itemSnapshot(List<Object> entities)throws Exception {
  StringBuilder out=new StringBuilder();for(Object e:entities){Object nbt=compound();call(call(e,"func_92059_d"),"func_77955_b",nbt);out.append(nbt.toString()).append(':').append(get(e,"field_70128_L")).append('|');}return out.toString();
 }
 static long token()throws Exception{return ((Number)stat(ENGINE,"token",tool)).longValue();}
 static void drain()throws Exception{cpw.mods.fml.common.FMLCommonHandler.instance().bus().post(new cpw.mods.fml.common.gameevent.TickEvent.ServerTickEvent(cpw.mods.fml.common.gameevent.TickEvent.Phase.END));}
 static void action(int action,int index,int rotation,int slot,long token)throws Exception{stat(ITEM,"handleAction",player,action,index,rotation,slot,token);drain();}

 static Object find(List<?> entries,String key)throws Exception{
  for(Object entry:entries){Object c=get(entry,"compat");if(c!=null&&key.equals(get(c,"key")))return entry;}
  throw new AssertionError("Missing PG entry "+key);
 }
 static int present(Object result)throws Exception{
  int count=0;for(Object reagent:(List<?>)get(result,"reagents"))count+=number(get(reagent,"present"));return count;
 }
 static List<?> warnings(Object result)throws Exception{return (List<?>)get(result,"warnings");}
 static Object filledPhylactery(String entity)throws Exception{
  Object s=stack(field("am2.items.ItemsCommonProxy","crystalPhylactery"),1,3),tag=compound();
  call(tag,"func_74778_a","SpawnClassName",entity);call(s,"func_77982_d",tag);return s;
 }
 static List<Object> spawnWithPhylactery(Object entry,Object phylactery)throws Exception{
  List<Object> result=new ArrayList<Object>();Object expected=call(call(entry,"interaction"),"getReagents");
  for(int i=0;i<Array.getLength(expected);i++){
   Object s=call(Array.get(expected,i),"func_77946_l");
   if(call(s,"func_77973_b")==field("am2.items.ItemsCommonProxy","crystalPhylactery"))s=call(phylactery,"func_77946_l");
   result.add(spawn(s,X+0.5,Y+0.1,Z+0.5));
  }return result;
 }
 static void containment()throws Exception{block(X-1,Y,Z,stone);block(X+1,Y,Z,stone);block(X,Y,Z-1,stone);block(X,Y,Z+1,stone);}
 static void ticking(Object b,int x,int y,int z)throws Exception{
  Class.forName("net.minecraft.block.Block").getMethod("func_149674_a",Class.forName("net.minecraft.world.World"),int.class,int.class,int.class,Random.class).invoke(b,world,x,y,z,new Random(42));
 }
 static int intendedCells(Object entry)throws Exception{
  Object shape=call(call(entry,"interaction"),"getRitualShape");Set<String> occupied=new HashSet<String>();
  for(Object mutex:(List<?>)call(shape,"getMutexList")){
   Map<?,?> chosen=null;
   for(Object group:(List<?>)call(shape,"getGroupsForMutex",number(mutex))){Map<?,?> parts=(Map<?,?>)call(group,"getAllowedBlocks");if(chosen==null||parts.size()>chosen.size())chosen=parts;}
   if(chosen!=null)for(Object point:chosen.keySet())occupied.add(get(point,"x")+":"+get(point,"y")+":"+get(point,"z"));
  }
  if(get(entry,"centerBlock")!=null)occupied.add("0:0:0");return occupied.size();
 }

 @Mod.EventHandler public void started(FMLServerStartedEvent event){int exit=0;try{
  String mode=System.getProperty("pgprobe.mode","full");
  server=call(cpw.mods.fml.common.FMLCommonHandler.instance(),"getMinecraftServerInstance");world=call(server,"func_71218_a",0);
  player=stat("net.minecraftforge.common.util.FakePlayerFactory","getMinecraft",world);inventory=get(player,"field_71071_by");props=stat("am2.playerextensions.ExtendedProperties","For",player);
  stone=field("net.minecraft.init.Blocks","field_150348_b");air=field("net.minecraft.init.Blocks","field_150350_a");
  item=field("am2.items.ItemsCommonProxy","ritualArchitect");tool=stack(item,1,0);set(inventory,"field_70461_c",0);call(inventory,"func_70299_a",0,tool);stat(ENGINE,"ensureToken",tool);
  List<?> entries=(List<?>)stat(ENGINE,"entries");List<Object> pg=new ArrayList<Object>();int natives=0,dynamic=0,registered=0;
  for(Object entry:entries){if(yes(get(entry,"playground"))){pg.add(entry);String key=(String)get(get(entry,"compat"),"key");if(key.startsWith("potionBuff_"))dynamic++;else registered++;}else natives++;}
  require(natives==30,"Native catalog changed: "+natives);
  require("world_fusion".equals(get(entries.get(0),"id"))&&"otherworldly_ritual".equals(get(entries.get(29),"id")),"Legacy native indices changed");
  log.add("NATIVE_THIRTY_ENTRIES_AND_LEGACY_INDICES_PRESERVED=PASS");
  if(mode.equals("am2only")){
   require(pg.isEmpty(),"PG entries were added without addon");
   require(((List<?>)stat("am2.ritual.PlaygroundRitualCompat","descriptors")).isEmpty(),"Optional provider does not fail cleanly without addon");
   log.add("OPTIONAL_PLAYGROUND_ABSENT_NO_LINKAGE_FAILURE=PASS");
  }else{
   Field actualField=Class.forName("airburn.am2playground.rituals.PGRituals").getDeclaredField("rituals");actualField.setAccessible(true);Map<?,?> actual=(Map<?,?>)actualField.get(null);
   require(registered==actual.size(),"Static coverage differs from live PG registry: "+registered+" / "+actual.size());
   require(registered==(mode.equals("minimal")?15:32),"Wrong conditional registry size: "+registered);
   Set<String> keys=new HashSet<String>();for(Object entry:pg){String key=(String)get(get(entry,"compat"),"key");require(keys.add(key),"Duplicate PG key "+key);if(!key.startsWith("potionBuff_"))require(call(entry,"interaction")==actual.get(key),"Static ritual is not the live registered object: "+key);}
   require(dynamic>0,"No dynamic Hourglass potion infusions discovered");
   log.add("LIVE_PG_REGISTRY_COVERAGE="+registered+"; DYNAMIC_INFUSIONS="+dynamic+"_PASS");

   Object config=field("am2.AMCore","config");set(config,"stagedCompendium",true);creative(false);((Map<?,?>)call(props,"getAllCompendiumEntries")).clear();
   Object target=find(pg,"potionInfusion_heal"),descriptor=get(target,"compat");
   call(props,"setCompendiumEntryNoSync","rituals_overview","U");
   require(!yes(stat(ENGINE,"canUse",player,target)),"Native chapter unlocked undiscovered PG ritual");
   call(props,"setCompendiumEntryNoSync",get(descriptor,"parentId"),"U");
   require(yes(stat(ENGINE,"canUse",player,target)),"PG parent chapter did not unlock its ritual");
   prepare(target,0);String survivalBefore=snapshot();Object statusBefore=status();List<Object> offered=spawnReagents(target,true);String itemBefore=itemSnapshot(offered);
   for(int n=0;n<4;n++)status();require(survivalBefore.equals(snapshot())&&itemBefore.equals(itemSnapshot(offered)),"PG preview modified blocks or item stacks");
   require(!built(build()),"Survival PG structure could be built directly");action(1,number(get(target,"index")),0,0,token());
   require(survivalBefore.equals(snapshot())&&itemBefore.equals(itemSnapshot(offered)),"Forged PG build altered survival world/items");
   log.add("PG_COMPENDIUM_PARENT_GATE_AND_SURVIVAL_READ_ONLY=PASS");

   creative(true);int variants=0;
   for(Object entry:pg)for(int rotation=0;rotation<4;rotation++){
    prepare(entry,rotation);String key=(String)get(get(entry,"compat"),"key");require(((List<?>)stat(ENGINE,"plan",tool)).size()==intendedCells(entry),"Projection skipped nonempty intended shape due empty main group: "+key);
    if(key.equals("breakthrough"))containment();
    if(key.equals("hellspawnCall_hellCow"))block(X,Y-1,Z,field("net.minecraft.init.Blocks","field_150424_aL"));
    String result=build();require(built(result),"PG build rejected "+get(entry,"id")+" variant "+rotation+": "+result);
    Object interaction=call(entry,"interaction");require(yes(call(call(interaction,"getRitualShape"),"checkStructure",world,X,Y,Z)),"PG checker rejected planned legal group: "+get(entry,"id")+" variant "+rotation);
    Object center=get(entry,"centerBlock");if(center!=null)require(blockAt(X,Y,Z)==center,"PG center mismatch "+get(entry,"id"));
    require(yes(get(status(),"structureReady")),"PG completed shape not ready "+get(entry,"id"));variants++;
   }
   log.add("ALL_PG_LEGAL_GROUP_VARIANTS_AND_CENTERS="+variants+"_PASS");
   require(!yes(get(props,"hasRitual")),"PG creative build executed initiation ritual");

   Object breakthrough=find(pg,"breakthrough");prepare(breakthrough,0);String freeLiquidBefore=snapshot();require(!built(build())&&freeLiquidBefore.equals(snapshot()),"Breakthrough builder allowed uncontained fluid");
   // Reproduce why containment is needed without invoking the ritual or using the builder.
   Object liquid=get(breakthrough,"centerBlock");for(Object cell:(List<?>)stat(ENGINE,"plan",tool))block(number(get(cell,"x")),number(get(cell,"y")),number(get(cell,"z")),get(cell,"block"));
   Object breakthroughShape=call(call(breakthrough,"interaction"),"getRitualShape");int intact=number(get(status(),"correct"));require(intact==((List<?>)stat(ENGINE,"plan",tool)).size(),"Manual uncontained fixture did not initially match intended pieces");
   for(int pass=0;pass<3;pass++)for(int dx=-4;dx<=4;dx++)for(int dz=-4;dz<=4;dz++)if(blockAt(X+dx,Y,Z+dz)==liquid)ticking(liquid,X+dx,Y,Z+dz);
   require(number(get(status(),"correct"))<intact,"Uncontained fluid did not reproduce chalk displacement");
   prepare(breakthrough,0);containment();require(built(build()),"Contained Breakthrough build rejected");for(int n=0;n<3;n++)ticking(liquid,X,Y,Z);require(yes(get(status(),"structureReady")),"Contained fluid still destroyed intended structure");
   Object hellCow=find(pg,"hellspawnCall_hellCow");prepare(hellCow,0);block(X,Y-1,Z,field("net.minecraft.init.Blocks","field_150424_aL"));require(built(build()),"Hell Cow fixture failed");Object fire=get(hellCow,"centerBlock");ticking(fire,X,Y,Z);require(blockAt(X,Y,Z)==fire,"Hell Cow fire did not persist over netherrack");
   log.add("FLUID_CONTAINMENT_AND_PERSISTENT_FIRE_REAL_BLOCK_UPDATES=PASS");

   prepare(target,0);Object required=call(call(target,"interaction"),"getReagents");require(Array.getLength(required)==1,"Boundary fixture needs one reagent");Object reagent=call(Array.get(required,0),"func_77946_l");
   Object boundary=spawn(reagent,X-3.5,Y+1.5,Z+0.1);String boundaryBefore=itemSnapshot(Collections.singletonList(boundary));
   require(present(status())==1,"PG extended x/y reagent region excluded valid reagent");
   Object box=call(target,"reagentBounds",X,Y,Z);require(((Number)get(box,"field_72340_a")).doubleValue()==X-4&&((Number)get(box,"field_72338_b")).doubleValue()==Y&&((Number)get(box,"field_72336_d")).doubleValue()==X+4&&((Number)get(box,"field_72337_e")).doubleValue()==Y+2,"PG reagent bounds differ from live helper");
   require(boundaryBefore.equals(itemSnapshot(Collections.singletonList(boundary))),"Boundary count mutated reagent");
   clearItems();spawn(call(reagent,"func_77946_l"),X+4.5,Y+0.1,Z+0.5);require(present(status())==0,"PG region accepted reagent beyond positive x boundary");
   clearItems();spawn(call(reagent,"func_77946_l"),X+0.5,Y+2.5,Z+0.5);require(present(status())==0,"PG region accepted reagent above height boundary");
   log.add("PG_EXACT_REAGENT_VOLUME_AND_NONCONSUMING_BOUNDARIES=PASS");

   prepare(target,0);require(built(build()),"Potion structure fixture failed");spawnReagents(target,false);Object emptyPotion=status();require(!warnings(emptyPotion).isEmpty()&&!yes(get(emptyPotion,"reagentsReady")),"Empty brewing stand falsely reported fully ready");
   Object stand=call(world,"func_147438_o",X,Y,Z);require(stand!=null,"Brewing stand has no tile entity");
   call(stand,"func_70299_a",0,stack(field("net.minecraft.init.Items","field_151068_bn"),1,0));
   Object filledPotion=status();require(warnings(filledPotion).size()<warnings(emptyPotion).size(),"Potion contents did not remove missing-potion warning");
   log.add("POTION_INFUSION_CONTENTS_INSPECTED_WITHOUT_EFFECTS=PASS");

   Object revival=find(pg,"soultrapRevival"),emptyPhy=stack(field("am2.items.ItemsCommonProxy","crystalPhylactery"),1,3),badPhy=filledPhylactery("missing:entity"),validPhy=filledPhylactery("Pig");
   require(!yes(stat("am2.ritual.PlaygroundRitualCompat","validPhylactery",emptyPhy))&&!yes(stat("am2.ritual.PlaygroundRitualCompat","validPhylactery",badPhy))&&yes(stat("am2.ritual.PlaygroundRitualCompat","validPhylactery",validPhy)),"Filled phylactery validation incorrect");
   prepare(revival,0);require(built(build()),"Soultrap fixture build failed");List<Object> emptyOffer=spawnWithPhylactery(revival,emptyPhy);String emptyBefore=itemSnapshot(emptyOffer);Object emptyStatus=status();
   require(!yes(get(emptyStatus,"reagentsReady"))&&!warnings(emptyStatus).isEmpty(),"Soultrap accepted empty phylactery");require(emptyBefore.equals(itemSnapshot(emptyOffer)),"Soultrap preview mutated empty phylactery");
   clearItems();List<Object> validOffer=spawnWithPhylactery(revival,validPhy);String validBefore=itemSnapshot(validOffer);Object validStatus=status();require(yes(get(validStatus,"reagentsReady")),"Soultrap rejected valid Pig-filled phylactery");require(validBefore.equals(itemSnapshot(validOffer)),"Soultrap preview mutated filled phylactery");
   log.add("SOULTRAP_FILLED_PHYLACTERY_NBT_AND_ITEM_PRESERVATION=PASS");

   Object targetMark=find(pg,"target");prepare(targetMark,0);require(yes(get(get(targetMark,"compat"),"activationUnverified")),"Known original activation issue not marked");require(!warnings(status()).isEmpty(),"Known original activation issue missing from display warnings");
   log.add("PREEXISTING_ACTIVATION_ISSUE_REPORTED_NOT_EXECUTED=PASS");

   prepare(target,0);Object bus=field("net.minecraftforge.common.MinecraftForge","EVENT_BUS");call(bus,"register",veto);veto.enabled=true;String protectedBefore=snapshot();
   try{require(!built(build()),"PG build ignored canceled Forge placement");}finally{veto.enabled=false;call(bus,"unregister",veto);}
   require(veto.events>0&&protectedBefore.equals(snapshot()),"Canceled PG build left partial structure");
   log.add("PG_CLAIM_CANCELLATION_ROLLS_BACK_ENTIRE_BUILD=PASS");

   stat(ENGINE,"select",tool,number(get(target,"index")),3);Object saved=compound();call(tool,"func_77955_b",saved);Object loaded=stat("net.minecraft.item.ItemStack","func_77949_a",saved);
   Object data=call(call(loaded,"func_77978_p"),"func_74775_l","am2RitualArchitect");call(data,"func_74768_a","selected",99999);
   require(get(stat(ENGINE,"selected",loaded),"id").equals(get(target,"id"))&&number(stat(ENGINE,"rotation",loaded))==3,"PG stable string selection did not survive changed legacy index");
   log.add("PG_STABLE_SELECTION_ID_AND_NBT_ROUNDTRIP=PASS");
  }
  log.add("PG_ARCHITECT_COMPAT=PASS");
 }catch(Throwable error){exit=1;log.add("ERROR="+error);error.printStackTrace();}
  try{Files.write(Paths.get("pg-architect-probe-result.txt"),log);}catch(Exception error){error.printStackTrace();}
  for(String line:log)System.out.println("PG_ARCHITECT_PROBE "+line);
  cpw.mods.fml.common.FMLCommonHandler.instance().exitJava(exit,false);
 }
}
