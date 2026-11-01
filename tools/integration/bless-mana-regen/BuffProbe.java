package buffprobe;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLServerStartedEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

/** Temporary integration mod. Never installed in the user's actual instance. */
@Mod(modid="buffprobe",name="Temporary Bless and Mana Regen Probe",version="1",dependencies="required-after:arsmagica2")
public final class BuffProbe {
 static Object world,player,props,server;
 static final List<String> log=new ArrayList<String>();
 static Object call(Object target,String name,Object...args)throws Exception{for(Method m:target.getClass().getMethods())if(m.getName().equals(name)&&m.getParameterTypes().length==args.length)try{return m.invoke(target,args);}catch(IllegalArgumentException ignored){}throw new NoSuchMethodException(name);}
 static Object stat(String type,String name,Object...args)throws Exception{for(Method m:Class.forName(type).getMethods())if(Modifier.isStatic(m.getModifiers())&&m.getName().equals(name)&&m.getParameterTypes().length==args.length)try{return m.invoke(null,args);}catch(IllegalArgumentException ignored){}throw new NoSuchMethodException(name);}
 static Object field(String type,String name)throws Exception{return Class.forName(type).getField(name).get(null);}
 static Object get(Object target,String name)throws Exception{for(Class<?> type=target.getClass();type!=null;type=type.getSuperclass())try{Field f=type.getDeclaredField(name);f.setAccessible(true);return f.get(target);}catch(NoSuchFieldException ignored){}throw new NoSuchFieldException(name);}
 static void set(Object target,String name,Object value)throws Exception{for(Class<?> type=target.getClass();type!=null;type=type.getSuperclass())try{Field f=type.getDeclaredField(name);f.setAccessible(true);f.set(target,value);return;}catch(NoSuchFieldException ignored){}throw new NoSuchFieldException(name);}
 static int number(Object value){return ((Number)value).intValue();}
 static Object compound()throws Exception{return Class.forName("net.minecraft.nbt.NBTTagCompound").newInstance();}
 static Object stack(Object item,int count,int meta)throws Exception{return Class.forName("net.minecraft.item.ItemStack").getConstructor(Class.forName("net.minecraft.item.Item"),int.class,int.class).newInstance(item,count,meta);}
 static void creative(boolean enabled)throws Exception{set(get(player,"field_71075_bZ"),"field_75098_d",enabled);}
 static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
 static boolean baseline=Boolean.getBoolean("buffprobe.baseline");
 boolean launched;
 @Mod.EventHandler public void init(cpw.mods.fml.common.event.FMLInitializationEvent event){if(cpw.mods.fml.common.FMLCommonHandler.instance().getSide().isClient())cpw.mods.fml.common.FMLCommonHandler.instance().bus().register(this);}
 @SubscribeEvent public void client(cpw.mods.fml.common.gameevent.TickEvent.ClientTickEvent event)throws Exception{
  if(launched||event.phase!=cpw.mods.fml.common.gameevent.TickEvent.Phase.END)return;
  Object mc=stat("net.minecraft.client.Minecraft","func_71410_x"),screen=get(mc,"field_71462_r");if(screen==null||!screen.getClass().getName().contains("GuiMainMenu"))return;
  launched=true;Object type=Enum.valueOf((Class)Class.forName("net.minecraft.world.WorldSettings$GameType"),"SURVIVAL"),flat=field("net.minecraft.world.WorldType","field_77138_c");
  Object settings=Class.forName("net.minecraft.world.WorldSettings").getConstructor(long.class,type.getClass(),boolean.class,boolean.class,flat.getClass()).newInstance(42L,type,false,false,flat);
  call(mc,"func_71371_a","buff-disposable","Buff disposable",settings);
 }

 static void check(boolean fixed,String label){if(baseline)log.add(label+"="+(fixed?"NOT_REPRODUCED":"REPRODUCED"));else{require(fixed,label);log.add(label+"=PASS");}}
 static void clear()throws Exception{call(player,"func_70674_bp");}
 static Object effect(Object potion,int duration,int amplifier)throws Exception{return Class.forName("net.minecraft.potion.PotionEffect").getConstructor(int.class,int.class,int.class).newInstance(number(get(potion,"field_76415_H")),duration,amplifier);}
 static Object active(Object potion)throws Exception{return call(player,"func_70660_b",potion);}
 static Object blessSpell(int copies)throws Exception {
  Object spell=stack(field("am2.items.ItemsCommonProxy","spell"),1,0);call(spell,"func_77982_d",compound());
  String[] mods=new String[copies];Arrays.fill(mods,"BuffPower");
  call(field("am2.spell.SpellUtils","instance"),"addSpellStageToScroll",spell,"Touch",new String[]{"Bless"},mods);return spell;
 }
 static float regen(int amp,int ticks)throws Exception {
  clear();call(player,"func_70690_d",effect(field("am2.buffs.BuffList","manaRegen"),1200,amp));
  call(props,"setCurrentMana",500.0f);set(props,"ticksForFullRegen",ticks);set(props,"ticksSinceLastRegen",19);set(props,"ticksToRegen",1);
  call(props,"manaBurnoutTick");return ((Number)call(props,"getCurrentMana")).floatValue();
 }
 @Mod.EventHandler public void started(FMLServerStartedEvent event){int exit=0;try{
  server=call(cpw.mods.fml.common.FMLCommonHandler.instance(),"getMinecraftServerInstance");world=call(server,"func_71218_a",0);
  player=stat("net.minecraftforge.common.util.FakePlayerFactory","getMinecraft",world);props=stat("am2.playerextensions.ExtendedProperties","For",player);
  Object network=Class.forName("net.minecraft.network.NetworkManager").getConstructor(boolean.class).newInstance(false);
  Object handler=Class.forName("net.minecraft.network.NetHandlerPlayServer").getConstructor(Class.forName("net.minecraft.server.MinecraftServer"),Class.forName("net.minecraft.network.NetworkManager"),Class.forName("net.minecraft.entity.player.EntityPlayerMP")).newInstance(server,network,player);
  set(player,"field_71135_a",handler);
  Object data=stat("am2.playerextensions.SkillData","For",player);call(data,"clearAllKnowledge");creative(false);call(props,"setMagicLevel",1);call(props,"setMaxMana",1000.0f);
  for(int amp:(baseline?new int[]{0,1,2,4,5,20,Integer.MAX_VALUE}:new int[]{0,1,2,3,4,5,20,Integer.MAX_VALUE})){
   System.out.println("BUFF_PROBE testing amplifier="+amp);float after=regen(amp,10000);log.add("MANA_LEVEL_"+((long)amp+1)+"=500 -> "+after);
   check(Float.isFinite(after)&&after>=500&&after<=1000,"REGEN_NONNEGATIVE_"+amp);
  }
  if(!baseline){
   float a=regen(0,10000),b=regen(1,10000),c=regen(2,10000),d=regen(3,10000),e=regen(4,10000);
   require(a<b&&b<c&&c<d&&Math.abs(d-e)<0.05,"Regen tiers/cap inconsistent");log.add("REGEN_PROGRESSIVE_AND_CAPPED=PASS");
   for(int ticks:new int[]{0,1,-100})check(Float.isFinite(regen(4,ticks))&&regen(4,ticks)>=500,"REGEN_INVALID_DURATION_"+ticks);
  }
  Object manaPotion=field("am2.buffs.BuffList","manaRegen");
  Object skill=call(field("am2.spell.SkillManager","instance"),"getSkill","Bless");
  for(int duration:new int[]{100,1200}){
   clear();Object buff=stat("am2.buffs.BuffList","buffEffectFromPotionID",number(get(manaPotion,"field_76415_H")),duration,3);call(player,"func_70690_d",buff);
   call(skill,"applyEffectEntity",blessSpell(0),world,player,player);Object remaining=active(manaPotion);
   check(remaining!=null&&number(call(remaining,"func_76458_c"))>=2&&number(call(remaining,"func_76459_b"))>=duration,"BLESS_PRESERVES_STRONG_BUFF_"+duration);
  }
  clear();call(player,"func_70690_d",stat("am2.buffs.BuffList","buffEffectFromPotionID",number(get(manaPotion,"field_76415_H")),1200,21));call(skill,"applyEffectEntity",blessSpell(0),world,player,player);
  check(active(manaPotion)!=null&&number(call(active(manaPotion),"func_76458_c"))==20,"BLESS_PRESERVES_UNAFFORDABLE_BUFF");
  clear();call(player,"func_70690_d",stat("am2.buffs.BuffList","buffEffectFromPotionID",number(get(manaPotion,"field_76415_H")),100,1));
  call(skill,"applyEffectEntity",blessSpell(3),world,player,player);Object high=active(manaPotion);
  require(high!=null,"Bless lost weak buff");log.add("BLESS_WITH_3_BUFF_POWER_AMPLIFIER="+call(high,"func_76458_c"));
  if(!baseline){require(number(call(high,"func_76458_c"))==4,"Bless lost a level during reconstruction");call(props,"setCurrentMana",500.0f);set(props,"ticksForFullRegen",10000);set(props,"ticksSinceLastRegen",19);call(props,"manaBurnoutTick");check(((Number)call(props,"getCurrentMana")).floatValue()>500,"BLESS_LEVEL_V_ACTUAL_REGEN");}
  for(String name:new String[]{"haste","frostSlowed"}){
   clear();Object potion=field("am2.buffs.BuffList",name);Object attr=call(player,"func_110148_a",field("net.minecraft.entity.SharedMonsterAttributes","field_111263_d"));
   double before=((Number)call(attr,"func_111126_e")).doubleValue();Object buff=stat("am2.buffs.BuffList","buffEffectFromPotionID",number(get(potion,"field_76415_H")),1200,5);call(buff,"applyEffect",player);double after=((Number)call(attr,"func_111126_e")).doubleValue();
   check(name.equals("haste")?after>before:after<before,"HIGH_LEVEL_"+name.toUpperCase()+"_STILL_WORKS");call(buff,"stopEffect",player);
  }
  if(!baseline){
   clear();Object speed=field("net.minecraft.potion.Potion","field_76424_c");call(player,"func_70690_d",effect(speed,1200,5));call(skill,"applyEffectEntity",blessSpell(0),world,player,player);check(active(speed)!=null&&number(call(active(speed),"func_76458_c"))==5,"BLESS_PRESERVES_STRONG_VANILLA_BUFF");
   clear();Object swim=field("am2.buffs.BuffList","swiftSwim");for(Object potion:new Object[]{manaPotion,swim})call(player,"func_70690_d",stat("am2.buffs.BuffList","buffEffectFromPotionID",number(get(potion,"field_76415_H")),100,6));
   call(skill,"applyEffectEntity",blessSpell(0),world,player,player);int extended=0;for(Object potion:new Object[]{manaPotion,swim}){Object effect=active(potion);require(effect!=null&&number(call(effect,"func_76458_c"))==5,"Bless removed/reduced mixed buffs");if(number(call(effect,"func_76459_b"))>=300)extended++;}check(extended==1,"BLESS_MAGNITUDE_BUDGET_SHARED_ACROSS_BUFFS");
   clear();stat("am2.spell.components.Bless","magnifyPotions",world,player,0,1,100,number(get(manaPotion,"field_76415_H")),1200,2,true);check(active(manaPotion)!=null&&number(call(active(manaPotion),"func_76458_c"))==2,"SHARED_CURSE_HELPER_RESTORES_UNCHANGED_EFFECT");
  }
  if(!baseline){
   clear();stat("airburn.am2playground.utils.helpers.PGLearnHelper","learnSkill",player,field("airburn.am2playground.spell.PGSpells","manaRegenArch"));check(regen(4,10000)>500,"ARCHMAGE_HIGH_BUFF_REGEN");
   stat("airburn.am2playground.utils.helpers.PGLearnHelper","learnSkill",player,field("airburn.am2playground.spell.PGSpells","atronach"));float suppressed=regen(4,10000);check(suppressed>=500&&suppressed<500.01,"ATRONACH_SUPPRESSION_PRESERVED");
  }
  log.add("BUFF_REGEN_PROBE=PASS");
 }catch(Throwable error){exit=1;log.add("ERROR="+error);error.printStackTrace();}
  try{Files.write(Paths.get("buff-regen-result.txt"),log);}catch(Exception error){error.printStackTrace();}
  for(String line:log)System.out.println("BUFF_PROBE "+line);
  cpw.mods.fml.common.FMLCommonHandler.instance().exitJava(exit,false);
 }
}
