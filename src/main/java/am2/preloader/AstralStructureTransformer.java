package am2.preloader;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.util.*;

/** Narrow optional generation notifications. Never modifies a foreign JAR on disk. */
public final class AstralStructureTransformer {
    public static final String[] ORE_METHODS={"makeDungeon","makeEnormousCastle","makeRotatorStation","makeBeeHive","makeHauntedHouse","makeMantisHive","makeKyuubiDungeon","makeSmallBeeHive","makeShadowDungeon","makeAlienWTFDungeon","makeEnderKnightDungeon","makePlayPool","makeWaterDragonLair","makeCloudSharkDungeon","makeLeafMonsterDungeon","makeMiniDungeon","makeGoldFishBowl","makeEnderReaperGraveyard","makeUrchinSpawner","makeSpitBugLair","makeIgloo","makeEnderDragonHospital","makeCrystalHauntedHouse","makeBouncyCastle","makeEnderCastle","makeDamselInDistress","makeIncaPyramid","makeRobotLab","makeKingAltar","makeLeonNest","makeCephadromeAltar","makeCrystalBattleTower","makeGirlfriendIsland","makeGreenhouseDungeon","makeMonsterIsland","makeNightmareRookery","makeStinkyHouse","makeRubberDuckyPond","makeWhiteHouse","makeQueenAltar","makeFrogPond","makePumpkin","makeRoundRotator","makeRainbow","makeEnormousCastleQ","makeSpiderHangout","makeRedAntHangout"};
    private static final String WORLD="Lnet/minecraft/world/World;";
    private static final String HOOK="am2/compass/AstralLandmarks";
    private static final Set<String> instrumentedOre=new java.util.concurrent.ConcurrentSkipListSet<String>();
    public static List<String> oreCatalog(){return new ArrayList<String>(instrumentedOre);}
    public static boolean accepts(String name) {
        return "mglucas0123.utils.GenericDungeon".equals(name) || "electroblob.wizardry.WizardryWorldGenerator".equals(name)
            || "thaumcraft.common.lib.world.WorldGenMound".equals(name) || "thaumcraft.common.lib.world.WorldGenEldritchRing".equals(name)
            || "thaumcraft.common.lib.world.WorldGenHilltopStones".equals(name);
    }
    public static byte[] transform(String name,byte[] bytes) {
        if(bytes==null || !accepts(name))return bytes;
        ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,ClassReader.EXPAND_FRAMES);int changed=0;
        MethodNode fastCopy=null;
        if(name.equals("mglucas0123.utils.GenericDungeon"))for(Object raw:node.methods) {
            MethodNode original=(MethodNode)raw;
            if(!original.name.equals("FastSetBlock") || !original.desc.equals("("+WORLD+"IIILnet/minecraft/block/Block;)V"))continue;
            MethodNode clone=new MethodNode(Opcodes.ASM5,Opcodes.ACC_PRIVATE,"am2$astralFastSetBlock","("+WORLD+"IIILnet/minecraft/block/Block;)Z",null,null);
            original.accept(clone);
            // Verified original wrapper is setBlockFast(...):boolean; POP; RETURN.
            AbstractInsnNode pop=null,ret=null;int calls=0;
            for(AbstractInsnNode i:clone.instructions.toArray()) {
                if(i instanceof MethodInsnNode && ((MethodInsnNode)i).owner.equals("mglucas0123/worlds/WorldSetBlocks") && ((MethodInsnNode)i).desc.endsWith(")Z"))calls++;
                if(i.getOpcode()==Opcodes.POP)pop=i;if(i.getOpcode()==Opcodes.RETURN)ret=i;
            }
            if(calls==1 && pop!=null && ret!=null){clone.instructions.remove(pop);clone.instructions.set(ret,new InsnNode(Opcodes.IRETURN));fastCopy=clone;}
        }
        for(Object raw:node.methods) {
            MethodNode method=(MethodNode)raw;
            boolean already=false;for(AbstractInsnNode i:method.instructions.toArray()) if(i instanceof MethodInsnNode && HOOK.equals(((MethodInsnNode)i).owner))already=true;
            if(already)continue;
            if(name.equals("mglucas0123.utils.GenericDungeon") && Arrays.asList(ORE_METHODS).contains(method.name) && method.desc.equals("("+WORLD+"III)V")) {
                int dirty=method.maxLocals;boolean tracked=false;
                for(AbstractInsnNode i:method.instructions.toArray())if(i instanceof MethodInsnNode) {
                    MethodInsnNode call=(MethodInsnNode)i;
                    if(fastCopy!=null && call.owner.equals(node.name) && call.name.equals("FastSetBlock") && call.desc.equals("("+WORLD+"IIILnet/minecraft/block/Block;)V")) {
                        call.name=fastCopy.name;call.desc=fastCopy.desc;call.setOpcode(Opcodes.INVOKESPECIAL);
                    } else if(!(call.owner.equals("net/minecraft/world/World") && (call.name.equals("setBlock") || call.name.equals("func_147465_d") || call.name.equals("func_147449_b"))
                        || call.owner.equals("mglucas0123/worlds/WorldSetBlocks") && call.name.equals("setBlockFast")) || !call.desc.endsWith(")Z"))continue;
                    InsnList flag=new InsnList();flag.add(new VarInsnNode(Opcodes.ILOAD,dirty));flag.add(new InsnNode(Opcodes.IOR));flag.add(new VarInsnNode(Opcodes.ISTORE,dirty));
                    // Keep the original call's result for its existing POP/branch/store.
                    flag.insert(new InsnNode(Opcodes.DUP));
                    if(call.name.equals("am2$astralFastSetBlock"))flag.remove(flag.getFirst());
                    method.instructions.insert(i,flag);tracked=true;
                }
                // No per-block callbacks/allocations: only one primitive boolean local.
                AbstractInsnNode last=null;for(AbstractInsnNode i:method.instructions.toArray())if(i.getOpcode()==Opcodes.RETURN)last=i;
                if(last!=null && tracked){
                    InsnList init=new InsnList();init.add(new InsnNode(Opcodes.ICONST_0));init.add(new VarInsnNode(Opcodes.ISTORE,dirty));method.instructions.insert(init);
                    for(AbstractInsnNode i:method.instructions.toArray())if(i instanceof FrameNode){FrameNode frame=(FrameNode)i;frame.local=new ArrayList<Object>(frame.local);int slots=0;for(Object local:frame.local)slots+=local==Opcodes.LONG || local==Opcodes.DOUBLE?2:1;while(slots++<dirty)frame.local.add(Opcodes.TOP);frame.local.add(Opcodes.INTEGER);}
                    method.maxLocals++;
                    InsnList record=record(1,2,3,4,"orespawn:"+method.name,false);record.insert(new VarInsnNode(Opcodes.ILOAD,dirty));
                    ((MethodInsnNode)record.getLast()).name="recordIfChanged";((MethodInsnNode)record.getLast()).desc="(Z"+WORLD+"IIILjava/lang/String;)V";
                    method.instructions.insertBefore(last,record);instrumentedOre.add(method.name);changed++;
                }
            } else if(name.equals("electroblob.wizardry.WizardryWorldGenerator") && method.name.equals("generateWizardTower") && method.desc.equals("("+WORLD+"Ljava/util/Random;II)V")) {
                // Both evil/friendly tower branches spawn only after construction. Preserve spawn success.
                for(AbstractInsnNode i:method.instructions.toArray())if(i instanceof MethodInsnNode) {
                    MethodInsnNode call=(MethodInsnNode)i;
                    if(call.owner.equals("net/minecraft/world/World") && (call.name.equals("spawnEntityInWorld") || call.name.equals("func_72838_d")) && call.desc.equals("(Lnet/minecraft/entity/Entity;)Z")) {
                        method.instructions.insert(i,record(1,5,7,6,"wizardry:wizard_tower",true));changed++;
                    }
                }
            } else if(name.startsWith("thaumcraft.") && (method.name.equals("generate") || method.name.equals("func_76484_a")) && method.desc.equals("("+WORLD+"Ljava/util/Random;III)Z")) {
                String id=name.endsWith("WorldGenMound")?"thaumcraft:mound":name.endsWith("WorldGenEldritchRing")?"thaumcraft:eldritch_ring":"thaumcraft:hilltop_stones";
                for(AbstractInsnNode i:method.instructions.toArray())if(i.getOpcode()==Opcodes.IRETURN){method.instructions.insertBefore(i,record(1,3,4,5,id,true));changed++;}
            }
        }
        if(changed==0)return bytes;
        boolean hasFastCopy=false;for(Object raw:node.methods)if(((MethodNode)raw).name.equals("am2$astralFastSetBlock"))hasFastCopy=true;
        if(fastCopy!=null && !hasFastCopy)node.methods.add(fastCopy);
        ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);node.accept(writer);return writer.toByteArray();
    }
    private static InsnList record(int world,int x,int y,int z,String id,boolean successOnStack) {
        InsnList code=new InsnList();code.add(new VarInsnNode(Opcodes.ALOAD,world));code.add(new VarInsnNode(Opcodes.ILOAD,x));
        code.add(new VarInsnNode(Opcodes.ILOAD,y));code.add(new VarInsnNode(Opcodes.ILOAD,z));code.add(new LdcInsnNode(id));
        code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,HOOK,successOnStack?"recordSuccess":"record",successOnStack?"(Z"+WORLD+"IIILjava/lang/String;)Z":"("+WORLD+"IIILjava/lang/String;)V",false));return code;
    }
}
