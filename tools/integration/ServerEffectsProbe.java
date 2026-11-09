package servereffectsprobe;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartedEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

/** Run only in a disposable game directory, never in a player's world. */
@Mod(modid="servereffectsprobe",name="Disposable Server Effects Regression",version="1",dependencies="required-after:arsmagica2")
public final class ServerEffectsProbe {
    private static final List<String> results = new ArrayList<String>();
    private static final boolean legacy = Boolean.getBoolean("am2.probe.legacy");
    private static Object world, storm, mc;
    private static boolean ready, checked, finished, opening;
    private static int elapsed;
    private static int anchorX, anchorZ;

    private static Object get(Object o, String name) throws Exception {
        for (Class<?> c = o instanceof Class ? (Class<?>)o : o.getClass(); c != null; c = c.getSuperclass()) {
            try { Field f=c.getDeclaredField(name); f.setAccessible(true); return f.get(o instanceof Class ? null : o); }
            catch (NoSuchFieldException ignored) {}
        }
        throw new NoSuchFieldException(name);
    }
    private static Object call(Object o, String name, Object... args) throws Exception {
        for (Class<?> c = o instanceof Class ? (Class<?>)o : o.getClass(); c != null; c = c.getSuperclass()) {
            for (Method m:c.getDeclaredMethods()) {
                if (!m.getName().equals(name) || m.getParameterTypes().length != args.length) continue;
                try { m.setAccessible(true); return m.invoke(o instanceof Class ? null : o, args); }
                catch (IllegalArgumentException ignored) {}
            }
        }
        throw new NoSuchMethodException(name);
    }
    private static Object make(String name, Object... args) throws Exception {
        for (Constructor<?> c:Class.forName(name).getConstructors()) {
            if (c.getParameterTypes().length != args.length) continue;
            try { return c.newInstance(args); } catch (IllegalArgumentException ignored) {}
        }
        throw new NoSuchMethodException(name);
    }
    private static Object stat(String cls, String name, Object... args) throws Exception { return call(Class.forName(cls), name, args); }
    private static void need(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    private static void pass(String name) { results.add(name+"=PASS"); System.out.println("SERVER_EFFECTS "+name+"=PASS"); }
    private static void finish(int code) {
        if (finished) return;
        finished=true;
        try { Files.write(Paths.get("server-effects-result.txt"),results); } catch (Exception e) { e.printStackTrace(); }
        FMLCommonHandler.instance().exitJava(code, false);
    }
    private static void fail(Throwable error) {
        error.printStackTrace(); results.add("ERROR="+error); finish(1);
    }
    private static Object effect() throws Exception { return make("am2.entities.EntitySpellEffect",world); }
    private static void anonymousContext(Object e) throws Exception {
        need(((Number)call(e,"getCasterEntityID")).intValue()==-1,"unowned effect has an entity owner");
        Object context=get(e,"dummycaster");
        need(context!=null,"unowned effect lacks a neutral context");
        need(!((Boolean)get(get(context,"field_71075_bZ"),"field_75098_d")),"neutral context has creative privileges");
        need(!((Boolean)call(context,"func_70003_b",4,"op")),"neutral context can run commands");
    }
    private interface Attempt { void run(Object e) throws Exception; }
    private static Object checkInitialization(String name, Attempt action) throws Exception {
        Object e=effect();
        if (legacy) {
            try { action.run(e); throw new AssertionError("CR24 failure not reproduced: "+name); }
            catch (InvocationTargetException ex) {
                Throwable cause=ex.getCause();
                need(cause instanceof NullPointerException,"unexpected CR24 failure: "+cause);
                need(cause.getStackTrace()[0].getClassName().equals("am2.utility.DummyEntityPlayer"),"different CR24 NPE");
            }
            pass("CR24_REPRODUCED_"+name);
        } else { action.run(e); anonymousContext(e); pass("NULL_CASTER_"+name); }
        return e;
    }
    private static void checks() throws Exception {
        Object spawn=call(world,"func_72861_E");
        anchorX=((Number)get(spawn,"field_71574_a")).intValue();
        anchorZ=((Number)get(spawn,"field_71573_c")).intValue();
        String version=Loader.instance().getIndexedModList().get("arsmagica2").getVersion();
        need(version.equals(legacy ? "1.6.7" : "1.6.11-LE-CR25"),"unexpected mod version: "+version);
        results.add("VERSION="+version);
        results.add("PHYSICAL_SIDE="+FMLCommonHandler.instance().getSide());
        if (FMLCommonHandler.instance().getSide()==Side.SERVER) {
            try { Class.forName("net.minecraft.client.Minecraft"); throw new AssertionError("client Minecraft is on server classpath"); }
            catch (ClassNotFoundException expected) {}
            Class.forName("am2.armor.ArmorEventHandler").getDeclaredMethods();
            pass("DEDICATED_SERVER_WITHOUT_CLIENT_CLASSES");
        }
        checkInitialization("AREA_BINDING", e -> call(e,"SetCasterAndStack",null,null));
        Object explosion=checkInitialization("EXPLOSION",e -> {
            call(e,"func_70107_b",anchorX+32.5,80.5,anchorZ+32.5);
            call(e,"setExplosionFusion",null,null,2.0f,anchorX+32,80,anchorZ+32);
        });
        Object lightning=checkInitialization("LIGHTNING",e -> {
            call(e,"func_70107_b",anchorX+48.5,80.5,anchorZ+48.5);
            call(e,"setLightningFusion",null,null,anchorX+48.5,80.5,anchorZ+48.5,anchorX+48,80,anchorZ+48);
        });
        storm=checkInitialization("STORM",e -> {
            call(e,"func_70107_b",anchorX+0.5,80.5,anchorZ+0.5);
            call(e,"setOrbitalStorm",null,null,anchorX+0.5,80.5,anchorZ+0.5,false,10.0f,48.0f,60,false);
        });
        if (legacy) { pass("SERVER_EFFECTS_BASELINE"); finish(0); return; }

        Object zombie=make("net.minecraft.entity.monster.EntityZombie",world);
        Object owned=effect(); call(owned,"SetCasterAndStack",zombie,null);
        need(call(owned,"getCasterEntityID").equals(call(zombie,"func_145782_y")),"owned caster ID changed");
        need(get(get(owned,"dummycaster"),"trackEntity")==zombie,"owned caster context changed");
        pass("REAL_CASTER_OWNERSHIP_PRESERVED");

        call(explosion,"func_70071_h_"); need((Boolean)get(explosion,"field_70128_L"),"explosion failed to complete");
        call(lightning,"func_70071_h_"); need((Boolean)get(lightning,"field_70128_L"),"lightning failed to complete");
        Object damage=stat("am2.damage.DamageSources","causeEntityLightningDamage",get(lightning,"dummycaster"));
        need(call(damage,"func_151519_b",zombie)!=null,"unowned lightning death message failed");
        pass("SERVER_EXPLOSION_LIGHTNING_AND_DEATH_MESSAGE");
        call(world,"func_72838_d",storm);
        checked=true;
    }
    @Mod.EventHandler public void init(FMLInitializationEvent e) { FMLCommonHandler.instance().bus().register(this); }
    @Mod.EventHandler public void started(FMLServerStartedEvent e) { ready=true; }
    @SubscribeEvent public void server(TickEvent.ServerTickEvent e) {
        if (finished || !ready || e.phase!=TickEvent.Phase.END) return;
        try {
            if (!checked) {
                Object server=stat("net.minecraft.server.MinecraftServer","func_71276_C");
                world=call(server,"func_71218_a",0);
                checks();
                return;
            }
            if (++elapsed>=120) {
                need((Boolean)get(storm,"field_70128_L"),"anonymous storm did not expire; age="+get(storm,"field_70173_aa"));
                pass("ANONYMOUS_STORM_SERVER_TICKS_AND_EXPIRY");
                pass("SERVER_EFFECTS_REGRESSION"); finish(0);
            }
        } catch (Throwable error) { fail(error); }
    }
    @SideOnly(Side.CLIENT)
    @SubscribeEvent public void client(TickEvent.ClientTickEvent e) {
        if (finished || opening || e.phase!=TickEvent.Phase.END) return;
        try {
            if (mc==null) mc=stat("net.minecraft.client.Minecraft","func_71410_x");
            Object screen=get(mc,"field_71462_r");
            if (screen==null) return;
            boolean menu=false;
            for (Class<?> c=screen.getClass();c!=null;c=c.getSuperclass()) {
                String n=c.getName();
                if (n.equals("net.minecraft.client.gui.GuiMainMenu") || n.equals("br.com.redeakashic.cinematic.client.CinematicMenu") || n.startsWith("DummyCore.Client.GuiMainMenu") || n.equals("com.gildedgames.the_aether.client.gui.menu.AetherMainMenu")) menu=true;
            }
            if (!menu) return;
            opening=true;
            Object type=Enum.valueOf((Class)Class.forName("net.minecraft.world.WorldSettings$GameType"),"CREATIVE");
            Object settings=make("net.minecraft.world.WorldSettings",42L,type,false,false,get(Class.forName("net.minecraft.world.WorldType"),"field_77138_c"));
            call(mc,"func_71371_a","server-effects-disposable","Server Effects Regression",settings);
        } catch (Throwable error) { fail(error); }
    }
}
