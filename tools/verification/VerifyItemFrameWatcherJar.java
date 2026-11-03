import java.util.jar.JarFile;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Keep the public addon entry point and explicit, isolated native tick routes. */
public final class VerifyItemFrameWatcherJar {
    public static void main(String[] args)throws Exception{
        try(JarFile jar=new JarFile(args[0])){
            checkRoute(read(jar,"am2/proxy/tick/ClientTickHandler.class"),Opcodes.ICONST_1);
            checkRoute(read(jar,"am2/proxy/tick/ServerTickHandler.class"),Opcodes.ICONST_0);
            ClassNode watcher=read(jar,"am2/ItemFrameWatcher.class");boolean api=false,loaded=false;
            for(MethodNode m:watcher.methods){
                if(m.name.equals("checkWatchedFrames")&&m.desc.equals("()V"))api=true;
                for(AbstractInsnNode i:m.instructions.toArray())if(i instanceof MethodInsnNode){MethodInsnNode call=(MethodInsnNode)i;if(call.name.equals("func_72899_e")||call.name.equals("blockExists"))loaded=true;if(call.owner.startsWith("airburn/")||call.owner.startsWith("arcaneengineering/"))throw new IllegalStateException("Native frame fix depends on an addon");}
            }
            if(!api||!loaded)throw new IllegalStateException("Missing compatible entry point or loaded-block guard");
            boolean unload=false;for(MethodNode m:read(jar,"am2/AMEventHandler.class").methods)for(AbstractInsnNode i:m.instructions.toArray())if(i instanceof MethodInsnNode&&((MethodInsnNode)i).owner.equals("am2/ItemFrameWatcher")&&((MethodInsnNode)i).name.equals("clearWorld"))unload=true;
            if(!unload)throw new IllegalStateException("Frame state survives world unload");
            System.out.println("Verified: explicit frame-watcher sides, addon API retained, loaded-block guard and world cleanup.");
        }
    }
    private static void checkRoute(ClassNode c,int side){
        for(MethodNode m:c.methods)for(AbstractInsnNode i:m.instructions.toArray())if(i instanceof MethodInsnNode){MethodInsnNode call=(MethodInsnNode)i;if(call.owner.equals("am2/ItemFrameWatcher")&&call.name.equals("checkWatchedFrames")&&call.desc.equals("(Z)V")){AbstractInsnNode previous=i.getPrevious();while(previous!=null&&previous.getOpcode()<0)previous=previous.getPrevious();if(previous!=null&&previous.getOpcode()==side)return;}}
        throw new IllegalStateException("Missing side-specific frame route in "+c.name);
    }
    private static ClassNode read(JarFile jar,String name)throws Exception{ClassNode c=new ClassNode();new ClassReader(jar.getInputStream(jar.getJarEntry(name))).accept(c,0);return c;}
}
