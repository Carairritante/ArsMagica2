package architectprobe;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLServerStartedEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.event.world.BlockEvent;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

/** Temporary integration mod. Never installed in the user's actual instance. */
@Mod(modid="architectprobe",name="Temporary AM2 Ritual Architect Probe",version="1",dependencies="required-after:arsmagica2")
public final class ArchitectProbe {
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
 static void drain()throws Exception{stat("am2.ritual.RitualArchitectServer","drain");}
 static void action(int action,int index,int rotation,int slot,long token)throws Exception{stat(ITEM,"handleAction",player,action,index,rotation,slot,token);drain();}

 @Mod.EventHandler public void started(FMLServerStartedEvent event){int exit=0;try{
  server=call(cpw.mods.fml.common.FMLCommonHandler.instance(),"getMinecraftServerInstance");world=call(server,"func_71218_a",0);
  player=stat("net.minecraftforge.common.util.FakePlayerFactory","getMinecraft",world);inventory=get(player,"field_71071_by");props=stat("am2.playerextensions.ExtendedProperties","For",player);
  stone=field("net.minecraft.init.Blocks","field_150348_b");air=field("net.minecraft.init.Blocks","field_150350_a");
  item=field("am2.items.ItemsCommonProxy","ritualArchitect");require(item!=null,"Item not registered");
  tool=stack(item,1,0);set(inventory,"field_70461_c",0);call(inventory,"func_70299_a",0,tool);
  // Item update assigns the server-owned GUI identity token.
  Class.forName("net.minecraft.item.Item").getMethod("func_77663_a",Class.forName("net.minecraft.item.ItemStack"),Class.forName("net.minecraft.world.World"),Class.forName("net.minecraft.entity.Entity"),int.class,boolean.class).invoke(item,tool,world,player,0,true);
  List<?> entries=(List<?>)stat(ENGINE,"entries");require(entries.size()>=5,"Too few native ritual entries: "+entries.size());
  Object fusion=null;for(Object entry:entries)if("ManaLink".equals(get(entry,"controller")))fusion=entry;
  require(fusion!=null,"World Fusion absent from catalog");
  int fusionIndex=number(get(fusion,"index"));

  int recipeCount=0;Object found=null;
  for(Object recipe:(List<?>)call(stat("net.minecraft.item.crafting.CraftingManager","func_77594_a"),"func_77592_b")){
   Object result=call(recipe,"func_77571_b");if(result!=null&&call(result,"func_77973_b")==item){recipeCount++;found=recipe;}
  }
  require(recipeCount==1,"Expected one registered architect recipe; found "+recipeCount);
  Object[] inputs=(Object[])call(found,"getInput");require(inputs.length==9,"Architect recipe must use all nine crafting cells");
  Object star=field("net.minecraft.init.Items","field_151156_bN"),chalk=field("am2.items.ItemsCommonProxy","wizardChalk");int stars=0,chalks=0;
  for(Object input:inputs){List<?> alternatives=input instanceof List?(List<?>)input:Collections.singletonList(input);for(Object alternative:alternatives){if(alternative==null)continue;Object ingredient=call(alternative,"func_77973_b");if(ingredient==star){stars++;break;}if(ingredient==chalk){chalks++;break;}}}
  require(stars==1&&chalks==1,"Recipe does not contain exactly one Nether Star and Wizard Chalk");
  log.add("ITEM_AND_REAL_NETHER_STAR_RECIPE_REGISTERED=PASS");

  Object config=field("am2.AMCore","config");set(config,"stagedCompendium",true);creative(false);
  ((Map<?,?>)call(props,"getAllCompendiumEntries")).clear();
  require(!yes(stat(ENGINE,"canUse",player,fusion)),"Survival catalog bypassed undiscovered Compendium entry");
  call(props,"setCompendiumEntryNoSync",get(fusion,"id"),"U");
  require(yes(stat(ENGINE,"canUse",player,fusion)),"Discovered ritual remains inaccessible");
  log.add("SURVIVAL_COMPENDIUM_DISCOVERY_GATE=PASS");

  prepare(fusion,0);Object beforeStatus=status();require(number(get(beforeStatus,"missing"))>0,"Empty area incorrectly complete");
  String before=snapshot();int countBefore=number(get(tool,"field_77994_a")),damageBefore=number(call(tool,"func_77960_j"));
  for(int n=0;n<4;n++)status();require(before.equals(snapshot()),"Read-only status changed world");
  require(!built(build())&&before.equals(snapshot()),"Survival direct build mutated world");
  action(1,fusionIndex,0,0,token());require(before.equals(snapshot()),"Forged survival build packet mutated world");
  require(number(get(tool,"field_77994_a"))==countBefore&&number(call(tool,"func_77960_j"))==damageBefore,"Preview consumed or damaged tool");
  log.add("SURVIVAL_PREVIEW_AND_FORGED_BUILD_DO_NOT_MUTATE_WORLD=PASS");

  creative(true);int variants=0;
  for(Object entry:entries)for(int rotation=0;rotation<4;rotation++){
   prepare(entry,rotation);require(built(build()),"Creative build rejected "+get(entry,"id")+" rotation "+rotation);
   Object interaction=call(entry,"interaction");require(yes(call(call(interaction,"getRitualShape"),"checkStructure",world,X,Y,Z)),"Built native ritual is invalid: "+get(entry,"id")+" rotation "+rotation);
   Object center=get(entry,"centerBlock");if(center!=null)require(blockAt(X,Y,Z)==center,"Wrong center block for "+get(entry,"id"));
   Object ready=status();require(yes(get(ready,"structureReady")),"Status rejected generated structure "+get(entry,"id"));
   String completed=snapshot();require(built(build())&&completed.equals(snapshot()),"Repeated creative build mutated completed structure");variants++;
  }
  log.add("ALL_NATIVE_CATALOG_ENTRIES_FOUR_ROTATIONS_VALID="+variants+"_PASS");

  prepare(fusion,0);List<?> cells=(List<?>)stat(ENGINE,"plan",tool);Object first=cells.get(0);int cx=number(get(first,"x")),cy=number(get(first,"y")),cz=number(get(first,"z"));
  block(cx,cy,cz,stone);String collision=snapshot();require(!built(build())&&collision.equals(snapshot()),"Collision overwrote blocks or placed partial structure");
  prepare(fusion,0);cells=(List<?>)stat(ENGINE,"plan",tool);first=cells.get(0);cx=number(get(first,"x"));cy=number(get(first,"y"));cz=number(get(first,"z"));
  block(cx,cy-1,cz,air);String unsupported=snapshot();require(!built(build())&&unsupported.equals(snapshot()),"Missing floor allowed structure placement");
  Object purification=null;for(Object entry:entries)if("purification".equals(get(entry,"id")))purification=entry;
  require(purification!=null,"Purification absent");prepare(purification,0);block(X,Y+1,Z,stone);String noHeadroom=snapshot();require(!built(build())&&noHeadroom.equals(snapshot()),"Tall generator center ignored blocked headroom");
  log.add("COLLISION_AND_MISSING_SUPPORT_PRESERVE_WORLD=PASS");

  prepare(fusion,0);String nearby=snapshot();position(X+128,Y,Z);require(!built(build())&&nearby.equals(snapshot()),"Distant build was allowed");position(X,Y+1,Z);
  Object nether=call(server,"func_71218_a",-1);stat(ENGINE,"setAnchor",tool,nether,X,Y,Z);require(!built(build())&&nearby.equals(snapshot()),"Cross-dimension build was allowed");
  stat(ENGINE,"setAnchor",tool,world,100000,Y,100000);Object unloaded=status();for(Object cell:(List<?>)get(unloaded,"cells"))require(number(call(cell,"state",world))==4,"Unloaded preview unexpectedly accessed terrain");
  log.add("DISTANCE_DIMENSION_AND_UNLOADED_PREVIEW_GUARDS=PASS");

  prepare(fusion,0);List<Object> reagents=spawnReagents(fusion,true);String beforeItems=itemSnapshot(reagents),beforeGround=snapshot();
  for(int n=0;n<4;n++)status();require(beforeItems.equals(itemSnapshot(reagents))&&beforeGround.equals(snapshot()),"Repeated reagent inspection changed item stacks/world");
  require(built(build())&&beforeItems.equals(itemSnapshot(reagents)),"Creative construction consumed/destroyed ground reagents");
  require(!yes(get(props,"hasRitual")),"Construction automatically executed World Fusion");
  log.add("STACKED_REAGENT_PREVIEW_AND_BUILD_PRESERVE_ITEMS_AND_DO_NOT_CAST=PASS");

  clearItems();spawnReagents(fusion,false);Object exact=status();require(yes(get(exact,"reagentsReady")),"Exact original reagent set not accepted by preview");
  spawn(stack(field("net.minecraft.init.Items","field_151032_g"),1,0),X+0.5,Y+0.1,Z+0.5);Object extra=status();require(number(get(extra,"extraItems"))>0&&!yes(get(extra,"reagentsReady")),"Extra ground reagent not reported");
  clearItems();Object missing=status();require(!yes(get(missing,"reagentsReady")),"Missing ground reagents reported ready");
  log.add("EXACT_MISSING_AND_EXTRA_GROUND_REAGENT_STATUS=PASS");

  Object gateway=null,recallOther=null;for(Object entry:entries){if("recall_to_gateway".equals(get(entry,"id")))gateway=entry;if("recall_other".equals(get(entry,"id")))recallOther=entry;}
  require(gateway!=null&&recallOther!=null,"Dynamic Recall entries absent");Object rune=field("am2.items.ItemsCommonProxy","rune");
  prepare(gateway,0);Object gatewayStatus=status();Object runePlaceholder=((List<?>)get(gatewayStatus,"reagents")).get(0);
  require(number(get(runePlaceholder,"required"))==3&&number(call(get(runePlaceholder,"stack"),"func_77960_j"))==number(field("am2.items.ItemRune","META_BLANK")),"Recall placeholder uses invalid wildcard item metadata");
  for(int meta=2;meta<=4;meta++)spawn(stack(rune,1,meta),X+0.5,Y+0.1,Z+0.5);
  gatewayStatus=status();require(number(get(((List<?>)get(gatewayStatus,"reagents")).get(0),"present"))==3&&number(get(gatewayStatus,"extraItems"))==0&&!yes(get(gatewayStatus,"reagentsReady")),"Gateway rune colors should be counted without falsely guaranteeing a matching destination");
  spawn(stack(rune,1,5),X+0.5,Y+0.1,Z+0.5);require(number(get(status(),"extraItems"))==1,"Fourth Gateway rune not reported as extra");
  prepare(recallOther,0);spawn(stack(rune,5,2),X+0.5,Y+0.1,Z+0.5);spawn(stack(field("am2.items.ItemsCommonProxy","itemOre"),1,number(field("am2.items.ItemOre","META_VINTEUMDUST"))),X+0.5,Y+0.1,Z+0.5);
  Object otherStatus=status();require(number(get(((List<?>)get(otherStatus,"reagents")).get(0),"present"))==5&&number(get(otherStatus,"extraItems"))==0&&!yes(get(otherStatus,"reagentsReady")),"Recall Other variable rune combination incorrectly treated as a fixed three-rune recipe");
  log.add("DYNAMIC_RECALL_RUNE_PLACEHOLDERS_AND_DESTINATION_UNCERTAINTY=PASS");

  prepare(fusion,0);Object bus=field("net.minecraftforge.common.MinecraftForge","EVENT_BUS");call(bus,"register",veto);veto.enabled=true;String protectedBefore=snapshot();
  try{require(!built(build()),"Canceled Forge placement was accepted");}finally{veto.enabled=false;call(bus,"unregister",veto);}
  require(veto.events>0&&protectedBefore.equals(snapshot()),"Forge placement cancellation did not preserve entire structure");
  log.add("FORGE_CLAIM_CANCELLATION_PREVENTS_PARTIAL_BUILD=PASS");

  prepare(fusion,0);long oldToken=token();String staleBefore=snapshot();stat(ITEM,"handleAction",player,1,fusionIndex,0,0,oldToken);
  require(staleBefore.equals(snapshot()),"Queued packet edited world before server tick");Object replacement=stack(item,1,0);call(inventory,"func_70299_a",0,replacement);drain();require(staleBefore.equals(snapshot()),"Queued GUI built with replacement item");
  require(stat(ENGINE,"anchor",replacement)==null,"Stale GUI changed replacement item");call(inventory,"func_70299_a",0,tool);
  action(0,number(get(entries.get(1),"index")),1,0,oldToken);require(number(get(stat(ENGINE,"selected",tool),"index"))==number(get(entries.get(1),"index"))&&number(stat(ENGINE,"rotation",tool))==1,"Valid queued GUI selection was not applied");
  stat(ITEM,"handleAction",player,0,fusionIndex,0,0,oldToken);stat(ITEM,"handleAction",player,0,fusionIndex,3,0,oldToken);drain();require(number(get(stat(ENGINE,"selected",tool),"index"))==fusionIndex&&number(stat(ENGINE,"rotation",tool))==3,"Rapid GUI requests discarded the latest complete selection");
  prepare(fusion,0);String queuedBefore=snapshot();stat(ITEM,"handleAction",player,0,number(get(entries.get(1),"index")),1,0,oldToken);stat(ITEM,"handleAction",player,1,fusionIndex,3,0,oldToken);require(queuedBefore.equals(snapshot()),"Rapid queued selection/build edited world before drain");drain();require(number(get(stat(ENGINE,"selected",tool),"index"))==fusionIndex&&number(stat(ENGINE,"rotation",tool))==3&&yes(get(status(),"structureReady")),"Rapid selection followed by build lost the final requested ritual/orientation");
  action(0,-999,999,0,oldToken);require(number(get(stat(ENGINE,"selected",tool),"index"))>=0,"Malformed selection corrupted item");
  Object saved=compound();call(tool,"func_77955_b",saved);Object loaded=stat("net.minecraft.item.ItemStack","func_77949_a",saved);Object anchor=stat(ENGINE,"anchor",loaded);
  require(anchor!=null&&number(get(anchor,"x"))==X&&number(get(anchor,"y"))==Y&&number(get(anchor,"z"))==Z,"Anchor lost in NBT item roundtrip");
  log.add("STALE_GUI_TOKEN_INVALID_SELECTION_AND_ANCHOR_PERSISTENCE=PASS");
  log.add("RITUAL_ARCHITECT=PASS");
 }catch(Throwable error){exit=1;log.add("ERROR="+error);error.printStackTrace();}
  try{Files.write(Paths.get("architect-probe-result.txt"),log);}catch(Exception error){error.printStackTrace();}
  for(String line:log)System.out.println("ARCHITECT_PROBE "+line);
  cpw.mods.fml.common.FMLCommonHandler.instance().exitJava(exit,false);
 }
}
