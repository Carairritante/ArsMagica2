import java.util.jar.JarFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/** Guard the Silver packet field and its Oculus counter in the distributable JAR. */
public final class VerifySilverPointsJar {
 public static void main(String[] args) throws Exception {
  try(JarFile jar=new JarFile(args[0])) {
   ClassNode data=read(jar,"am2/playerextensions/SkillData.class");
   ClassNode gui=read(jar,"am2/guis/GuiSkillTrees.class");
   boolean packet=false,counter=false;
   for(MethodNode m:data.methods) if(m.name.equals("handlePacketData"))
    for(AbstractInsnNode i:m.instructions.toArray()) if(silver(i)) {
     AbstractInsnNode next=next(i);
     if(next instanceof MethodInsnNode && ((MethodInsnNode)next).name.equals("setSpellPoints")
       && ((MethodInsnNode)next).desc.equals("(ILam2/api/spell/enums/SkillPointTypes;)V")) packet=true;
    }
   for(MethodNode m:gui.methods) if(m.name.equals("drawScreen") || m.name.equals("func_73863_a"))
    for(AbstractInsnNode i:m.instructions.toArray()) if(silver(i)) {
     AbstractInsnNode next=next(i);
     if(next instanceof MethodInsnNode && ((MethodInsnNode)next).name.equals("getSpellPoints"))counter=true;
    }
   if(!packet)throw new IllegalStateException("Client does not read Silver points from sync packet");
   if(!counter)throw new IllegalStateException("Oculus does not read the Silver counter");
   System.out.println("Verified: Silver points sync field and Oculus counter.");
  }
 }
 static boolean silver(AbstractInsnNode i) {
  return i instanceof FieldInsnNode && i.getOpcode()==Opcodes.GETSTATIC
   && ((FieldInsnNode)i).owner.equals("am2/api/spell/enums/SkillPointTypes")
   && ((FieldInsnNode)i).name.equals("SILVER");
 }
 static AbstractInsnNode next(AbstractInsnNode i) {do{i=i.getNext();}while(i!=null && i.getOpcode()<0);return i;}
 static ClassNode read(JarFile jar,String path)throws Exception {
  ClassNode c=new ClassNode();new ClassReader(jar.getInputStream(jar.getJarEntry(path))).accept(c,0);return c;
 }
}
