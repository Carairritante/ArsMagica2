package am2.cinematics.ritual;

import cpw.mods.fml.common.registry.GameRegistry;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.attributes.IAttribute;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraft.world.World;

/**
 * O que o ritual lê e altera no AM2.5, por reflexão: o mod não é modificado.
 * Na criação só se conferem classes e membros; nada estático do AM2 é
 * inicializado antes da hora (o desenho do círculo depende dos blocos dele).
 */
final class Am2Ritual {
    private static final String[] ATTRIBUTES = { "maxManaBonus", "maxBurnoutBonus", "manaRegenTimeModifier",
            "burnoutReductionRate" };

    private final Class<?> api;
    private final Method properties, magicLevel, setUpdateFlag;
    private final Field waterGuardian, earthGuardian, hasRitual, syncFlag;
    private final Field shapesInstance, worldFusion;
    private final Method checkStructure, mutexList, removeMutex, reagents, reagentRadius;
    private final Class<?> manaLinkType;
    private final Constructor<?> silenceEffect;
    private final Item essence;
    private Object manaLink;

    private Am2Ritual(ClassLoader loader, Item essence) throws Exception {
        this.essence = essence;
        Class<?> type = Class.forName("am2.playerextensions.ExtendedProperties", false, loader);
        properties = type.getMethod("For", EntityLivingBase.class);
        magicLevel = type.getMethod("getMagicLevel");
        setUpdateFlag = type.getMethod("setUpdateFlag", int.class);
        waterGuardian = type.getField("guardian1");
        earthGuardian = type.getField("guardian2");
        hasRitual = type.getField("hasRitual");
        syncFlag = type.getField("UPD_CURRENT_MANA_FATIGUE");

        Class<?> shapes = Class.forName("am2.RitualShapeHelper", false, loader);
        shapesInstance = shapes.getField("instance");
        worldFusion = shapes.getField("worldFusion");
        Class<?> structure = Class.forName("am2.api.blocks.MultiblockStructureDefinition", false, loader);
        checkStructure = structure.getMethod("checkStructure", World.class, int.class, int.class, int.class);
        mutexList = structure.getMethod("getMutexList");
        removeMutex = structure.getMethod("removeMutex", int.class, World.class, int.class, int.class, int.class);

        silenceEffect = Class.forName("am2.buffs.BuffEffectSilence", false, loader)
                .getConstructor(int.class, int.class);

        api = Class.forName("am2.api.ArsMagicaApi", false, loader);
        for (String name : ATTRIBUTES) api.getField(name);

        manaLinkType = Class.forName("am2.spell.components.ManaLink", false, loader);
        reagents = manaLinkType.getMethod("getReagents");
        reagentRadius = manaLinkType.getMethod("getReagentSearchRadius");
    }

    /** @return nulo se a versão do AM2 não tiver o que o ritual precisa. */
    static Am2Ritual create(Item orb) {
        try {
            Item essence = GameRegistry.findItem("arsmagica2", "essence");
            if (orb == null || essence == null) throw new IllegalStateException("itens do AM2 não registrados");
            return new Am2Ritual(Am2Ritual.class.getClassLoader(), essence);
        } catch (Exception e) {
            am2.cinematics.AMCinematics.log.warn("Ritual novo indisponível neste AM2: " + e);
            return null;
        }
    }

    Item essence() {
        return essence;
    }

    int magicLevel(EntityPlayer player) {
        return (Integer) call(magicLevel, of(player));
    }

    boolean beatWaterGuardian(EntityPlayer player) {
        return (Boolean) read(waterGuardian, of(player));
    }

    boolean beatEarthGuardian(EntityPlayer player) {
        return (Boolean) read(earthGuardian, of(player));
    }

    boolean fused(EntityPlayer player) {
        return (Boolean) read(hasRitual, of(player));
    }

    /** Marca (ou desmarca) a fusão e manda o estado ao cliente. */
    void setFused(EntityPlayer player, boolean value) {
        try {
            hasRitual.setBoolean(of(player), value);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Falha ao gravar o ritual", e);
        }
        resync(player);
    }

    /** Manda o estado do servidor ao cliente, desfazendo uma previsão dele. */
    void resync(EntityPlayer player) {
        call(setUpdateFlag, of(player), read(syncFlag, null));
    }

    /** Um atributo de jogador da API do AM2 (mana e burnout). */
    IAttribute attribute(String name) {
        try {
            return (IAttribute) read(api.getField(name), null);
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException("Atributo do AM2 ausente: " + name, e);
        }
    }

    /** O Silêncio do AM2, aplicado como qualquer efeito (resistências valem). */
    void silence(EntityPlayer player, int ticks) {
        try {
            player.addPotionEffect((PotionEffect) silenceEffect.newInstance(ticks, 0));
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao silenciar", e);
        }
    }

    boolean circleComplete(World world, int x, int y, int z) {
        return (Boolean) call(checkStructure, circle(), world, x, y, z);
    }

    /** Apaga o giz e as velas, como o ritual original. */
    void consumeCircle(World world, int x, int y, int z) {
        Object circle = circle();
        for (Object mutex : (List<?>) call(mutexList, circle)) {
            call(removeMutex, circle, mutex, world, x, y, z);
        }
    }

    /** Os reagentes do ritual original. */
    ItemStack[] reagents() {
        return (ItemStack[]) call(reagents, manaLink());
    }

    int reagentRadius() {
        return (Integer) call(reagentRadius, manaLink());
    }

    private Object circle() {
        return read(worldFusion, read(shapesInstance, null));
    }

    private Object manaLink() {
        if (manaLink == null) {
            try {
                manaLink = manaLinkType.getConstructor().newInstance();
            } catch (Exception e) {
                throw new IllegalStateException("Falha ao consultar o Vínculo de Mana", e);
            }
        }
        return manaLink;
    }

    private Object of(EntityPlayer player) {
        return call(properties, null, player);
    }

    private static Object read(Field field, Object target) {
        try {
            return field.get(target);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Falha ao ler o AM2: " + field.getName(), e);
        }
    }

    private static Object call(Method method, Object target, Object... args) {
        try {
            return method.invoke(target, args);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao chamar o AM2: " + method.getName(), e);
        }
    }
}
