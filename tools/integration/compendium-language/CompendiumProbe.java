package compendiumprobe;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import java.lang.reflect.*;
import java.nio.file.*;
import java.io.File;
import java.util.*;
@Mod(modid="compendiumprobe",name="Disposable Compendium Language Probe",version="1",dependencies="required-after:arsmagica2;required-after:am2pg")
public class CompendiumProbe {
 static Object mc,player,book;static int state,ticks;static boolean finished;static int entryCount;
 static List<String> log=new ArrayList<String>();static Map<String,String> expected;
 static Object call(Object o,String n,Object...a)throws Exception{for(Method m:o.getClass().getMethods())if(m.getName().equals(n)&&m.getParameterTypes().length==a.length)try{return m.invoke(o,a);}catch(IllegalArgumentException e){}throw new NoSuchMethodException(n);}
 static Object stat(String c,String n,Object...a)throws Exception{for(Method m:Class.forName(c).getMethods())if(Modifier.isStatic(m.getModifiers())&&m.getName().equals(n)&&m.getParameterTypes().length==a.length)try{return m.invoke(null,a);}catch(IllegalArgumentException e){}throw new NoSuchMethodException(n);}
 static Object field(String c,String n)throws Exception{return Class.forName(c).getField(n).get(null);}
 static Object get(Object o,String n)throws Exception{for(Class<?> c=o.getClass();c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(n);f.setAccessible(true);return f.get(o);}catch(NoSuchFieldException e){}throw new NoSuchFieldException(n);}
 static void set(Object o,String n,Object v)throws Exception{for(Class<?> c=o.getClass();c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(n);f.setAccessible(true);f.set(o,v);return;}catch(NoSuchFieldException e){}throw new NoSuchFieldException(n);}
 static void require(boolean ok,String why){if(!ok)throw new AssertionError(why);}
 static Object entry(String id)throws Exception{Object e=call(book,"getEntry",id);require(e!=null,"Missing entry "+id);return e;}
 static void open()throws Exception{stat("am2.guis.AMGuiHelper","OpenCompendiumGui",(Object)null);}
 static void language(String code)throws Exception{
  Object lm=call(mc,"func_135016_M"),chosen=null;for(Object l:(Set<?>)call(lm,"func_135040_d"))if(code.equals(call(l,"func_135034_a")))chosen=l;
  require(chosen!=null,"Language not available "+code);call(lm,"func_135045_a",chosen);set(get(mc,"field_71474_y"),"field_74363_ab",code);call(mc,"func_110436_a");
  Object font=get(mc,"field_71466_p");call(font,"func_78264_a",Boolean.TRUE.equals(call(lm,"func_135042_a")));call(font,"func_78275_b",Boolean.TRUE.equals(call(lm,"func_135044_b")));
 }
 static Map<String,String> flags()throws Exception{Map<String,String> m=new TreeMap<String,String>();for(Object e:((Map<?,?>)get(book,"compendium")).values())flags(e,m);return m;}
 static void flags(Object e,Map<String,String> m)throws Exception{m.put((String)call(e,"getID"),call(e,"isLocked")+"/"+call(e,"isNew"));for(Object sub:(Object[])call(e,"getSubItems"))flags(sub,m);}
 static void shot(String n)throws Exception{stat("net.minecraft.util.ScreenShotHelper","func_148259_a",new File("."),n,((Number)get(mc,"field_71443_c")).intValue(),((Number)get(mc,"field_71440_d")).intValue(),call(mc,"func_147110_a"));}
 static void page(String id)throws Exception{Object gui=Class.forName("am2.guis.GuiArcaneCompendium").getConstructor(String.class).newInstance(id);call(mc,"func_147108_a",gui);}
 static void finish(int code){finished=true;try{Files.write(Paths.get("compendium-language-result.txt"),log);}catch(Exception e){e.printStackTrace();}for(String s:log)System.out.println("LANGUAGE_PROBE "+s);cpw.mods.fml.common.FMLCommonHandler.instance().exitJava(code,false);}
 @Mod.EventHandler public void init(FMLInitializationEvent e){cpw.mods.fml.common.FMLCommonHandler.instance().bus().register(this);}
 @SubscribeEvent public void client(TickEvent.ClientTickEvent event){if(finished||event.phase!=TickEvent.Phase.END)return;try{
  if(mc==null)mc=stat("net.minecraft.client.Minecraft","func_71410_x");ticks++;
  if(state==0){Object screen=get(mc,"field_71462_r");if(screen==null||!screen.getClass().getName().contains("GuiMainMenu"))return;Object type=Enum.valueOf((Class)Class.forName("net.minecraft.world.WorldSettings$GameType"),"CREATIVE"),flat=field("net.minecraft.world.WorldType","field_77138_c");Object ws=Class.forName("net.minecraft.world.WorldSettings").getConstructor(long.class,type.getClass(),boolean.class,boolean.class,flat.getClass()).newInstance(42L,type,false,false,flat);state=1;ticks=0;call(mc,"func_71371_a","compendium-disposable","Compendium disposable",ws);return;}
  player=get(mc,"field_71439_g");if(player==null)return;
  if(state==1){if(ticks<40)return;book=field("am2.lore.ArcaneCompendium","instance");language("en_US");open();require("Projectile".equals(call(entry("Projectile"),"getName")),"Initial English core failed");require("Advanced Silver Requirements".equals(call(entry("am2pg_advanced_silver"),"getName")),"Initial English PG failed");entryCount=flags().size();call(entry("Projectile"),"setIsLocked",false);call(entry("Projectile"),"setIsNew",false);call(entry("am2pg_silver_nullify"),"setIsLocked",true);expected=flags();log.add("INITIAL_ENGLISH_ENTRIES="+entryCount);language("pt_BR");open();require(expected.equals(flags()),"Language changed discovery flags");require("Projétil".equals(call(entry("Projectile"),"getName")),"Portuguese core failed");require("Requisitos Prateados Avançados".equals(call(entry("am2pg_advanced_silver"),"getName")),"Portuguese addon failed");Object cat=field("am2.lore.CompendiumEntryTypes","instance");require("Guias".equals(call(get(cat,"GUIDE"),"getCategoryLabel")),"Categories not localized");require(entryCount==flags().size(),"Translation duplicated or removed entries");require("Sem formato fixo".equals(stat("net.minecraft.util.StatCollector","func_74838_a","am2.compendium.shapeless")),"Recipe label not localized");log.add("PT_BR_BOTH_BOOKS_CATEGORIES_AND_UNLOCKS=PASS");state=2;ticks=0;return;}
  if(state==2&&ticks>15){shot("01-indice-portugues.png");page("yourFirstSpell");state=3;ticks=0;return;}
  if(state==3&&ticks>15){shot("02-primeira-magia.png");page("am2pg_silver_nullify");state=4;ticks=0;return;}
  if(state==4&&ticks>15){shot("03-playground-requisitos.png");language("en_US");open();require("Projectile".equals(call(entry("Projectile"),"getName")),"Return English core failed");require("Nullify: additional requirements".equals(call(entry("am2pg_silver_nullify"),"getName")),"Return English addon failed");require(entryCount==flags().size(),"Entry count changed on return");log.add("RETURN_TO_ENGLISH_WITHOUT_RESTART=PASS");page("yourFirstSpell");state=5;ticks=0;return;}
  if(state==5&&ticks>15){shot("04-retorno-ingles.png");expected=flags();call(mc,"func_110436_a");open();require(expected.equals(flags()),"Resource reload changed flags");log.add("RESOURCE_RELOAD_PRESERVES_DISCOVERY=PASS");language("pt_BR");open();log.add("COMPENDIUM_LANGUAGE=PASS");finish(0);}
 }catch(Throwable t){t.printStackTrace();log.add("ERROR="+t);finish(1);}}
}
