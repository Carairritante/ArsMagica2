package am2.compass;

import am2.preloader.AstralStructureTransformer;
import org.junit.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.util.*;
import static org.junit.Assert.*;

public class AstralStructureTransformerTest {
    private byte[] fixture(boolean placement) {
        ClassWriter w=new ClassWriter(0);w.visit(50,1,"mglucas0123/utils/GenericDungeon",null,"java/lang/Object",null);
        MethodVisitor m=w.visitMethod(1,"makeKingAltar","(Lnet/minecraft/world/World;III)V",null,null);m.visitCode();
        if(placement){m.visitVarInsn(25,0);m.visitVarInsn(25,1);m.visitVarInsn(21,2);m.visitVarInsn(21,3);m.visitVarInsn(21,4);m.visitInsn(1);m.visitMethodInsn(182,"mglucas0123/utils/GenericDungeon","FastSetBlock","(Lnet/minecraft/world/World;IIILnet/minecraft/block/Block;)V",false);}
        m.visitInsn(177);m.visitMaxs(6,5);m.visitEnd();
        m=w.visitMethod(1,"FastSetBlock","(Lnet/minecraft/world/World;IIILnet/minecraft/block/Block;)V",null,null);m.visitCode();
        m.visitVarInsn(25,1);m.visitVarInsn(21,2);m.visitVarInsn(21,3);m.visitVarInsn(21,4);m.visitVarInsn(25,5);m.visitInsn(3);m.visitInsn(5);
        m.visitMethodInsn(184,"mglucas0123/worlds/WorldSetBlocks","setBlockFast","(Lnet/minecraft/world/World;IIILnet/minecraft/block/Block;II)Z",false);
        m.visitInsn(87);m.visitInsn(177);m.visitMaxs(7,6);m.visitEnd();w.visitEnd();return w.toByteArray();
    }
    private int calls(byte[] bytes) {
        ClassNode n=new ClassNode();new ClassReader(bytes).accept(n,0);int calls=0;
        for(Object raw:n.methods)for(AbstractInsnNode i:((MethodNode)raw).instructions.toArray())if(i instanceof MethodInsnNode && ((MethodInsnNode)i).owner.equals("am2/compass/AstralLandmarks"))calls++;
        return calls;
    }
    @Test public void onlyCompletedPlacementsAreRecordedAndTransformIsIdempotent() {
        String name="mglucas0123.utils.GenericDungeon";
        assertEquals(0,calls(AstralStructureTransformer.transform(name,fixture(false))));
        byte[] transformed=AstralStructureTransformer.transform(name,fixture(true));assertEquals(1,calls(transformed));
        assertArrayEquals(transformed,AstralStructureTransformer.transform(name,transformed));
        assertArrayEquals(fixture(true),AstralStructureTransformer.transform("unrelated.Generator",fixture(true)));
    }
    @Test public void realOptionalJarsHaveValidatedHooks() throws Exception {
        String dir=System.getProperty("astral.pack.mods");org.junit.Assume.assumeTrue(dir!=null);
        java.io.File root=new java.io.File(dir);int ore=0,wizard=0,thaum=0;
        for(java.io.File f:root.listFiles())if(f.getName().endsWith(".jar"))try(java.util.jar.JarFile jar=new java.util.jar.JarFile(f)) {
            String[] classes={"mglucas0123.utils.GenericDungeon","electroblob.wizardry.WizardryWorldGenerator","thaumcraft.common.lib.world.WorldGenMound","thaumcraft.common.lib.world.WorldGenEldritchRing","thaumcraft.common.lib.world.WorldGenHilltopStones"};
            for(String c:classes){java.util.jar.JarEntry e=jar.getJarEntry(c.replace('.','/')+".class");if(e==null)continue;
                java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();try(java.io.InputStream in=jar.getInputStream(e)){byte[] b=new byte[8192];int n;while((n=in.read(b))>0)out.write(b,0,n);}
                byte[] patched=AstralStructureTransformer.transform(c,out.toByteArray());int n=calls(patched);
                ClassNode parsed=new ClassNode();new ClassReader(patched).accept(parsed,ClassReader.EXPAND_FRAMES);
                for(Object raw:parsed.methods){MethodNode method=(MethodNode)raw;if((method.access&(Opcodes.ACC_ABSTRACT|Opcodes.ACC_NATIVE))==0)new org.objectweb.asm.tree.analysis.Analyzer(new org.objectweb.asm.tree.analysis.BasicVerifier()).analyze(parsed.name,method);}
                if(c.startsWith("mglucas"))ore=n;else if(c.startsWith("electroblob"))wizard=n;else thaum+=n;
                assertArrayEquals(patched,AstralStructureTransformer.transform(c,patched));
            }
        }
        System.out.println("REAL_JAR_HOOKS OreSpawn="+ore+" Wizardry="+wizard+" Thaumcraft="+thaum+" oreMethods="+am2.preloader.AstralStructureTransformer.oreCatalog());
        assertTrue(ore>=20);assertEquals(2,wizard);assertTrue(thaum>=3);
    }
}
