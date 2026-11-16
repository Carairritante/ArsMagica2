import java.io.*;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;

/** Prevent production NoSuchMethodError hidden by compile-only API ancestors. */
public final class VerifyAnimationInheritanceJar {
    static final Set<String> MCP = new HashSet<String>(Arrays.asList(
        "shouldExecute", "continueExecuting", "isInterruptible", "startExecuting",
        "resetTask", "updateTask", "setMutexBits", "getMutexBits"));
    static boolean animation(String name, Map<String,ClassNode> nodes) {
        Set<String> seen = new HashSet<String>();
        while (name != null && seen.add(name)) {
            if (name.equals("thehippomaster/AnimationAPI/AIAnimation")) return true;
            ClassNode node=nodes.get(name); name=node==null?null:node.superName;
        }
        return false;
    }
    public static void main(String[] args) throws Exception {
        Map<String,ClassNode> nodes=new HashMap<String,ClassNode>();
        try (JarFile jar=new JarFile(args[0])) {
            Enumeration<JarEntry> entries=jar.entries();
            while(entries.hasMoreElements()) {
                JarEntry entry=entries.nextElement();
                if(entry.getName().startsWith("thehippomaster/AnimationAPI/"))
                    throw new AssertionError("Compile-only AnimationAPI was packaged");
                if(!entry.getName().endsWith(".class"))continue;
                ClassNode node=new ClassNode();
                try(InputStream in=jar.getInputStream(entry)){new ClassReader(in).accept(node,0);}
                nodes.put(node.name,node);
            }
        }
        int classes=0, mutexCalls=0;
        for(ClassNode node:nodes.values())if(animation(node.name,nodes)) {
            classes++;
            for(MethodNode method:node.methods) {
                if(MCP.contains(method.name))throw new AssertionError("Unmapped AI override: "+node.name+"."+method.name);
                for(AbstractInsnNode ins:method.instructions.toArray())if(ins instanceof MethodInsnNode) {
                    MethodInsnNode call=(MethodInsnNode)ins;
                    if((animation(call.owner,nodes)||call.owner.equals("net/minecraft/entity/ai/EntityAIBase"))&&MCP.contains(call.name))
                        throw new AssertionError("Unmapped AI call: "+node.name+" -> "+call.owner+"."+call.name);
                    if(call.name.equals("func_75248_a")&&call.desc.equals("(I)V"))mutexCalls++;
                }
            }
        }
        if(!nodes.containsKey("am2/bosses/ai/EntityAILightningRod")||classes==0||mutexCalls==0)throw new AssertionError("Missing animation AI coverage: classes="+classes+", calls="+mutexCalls);
        System.out.println("Verified: "+classes+" AnimationAPI AI classes and "+mutexCalls+" mapped mutex calls; compile-only API excluded.");
    }
}
