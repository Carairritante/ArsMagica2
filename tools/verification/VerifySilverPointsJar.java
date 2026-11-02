import java.util.jar.JarFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/** Keep the native four-balance protocol without addon-specific UI or purchase rules. */
public final class VerifySilverPointsJar {
 public static void main(String[] args) throws Exception {
  try(JarFile jar=new JarFile(args[0])) {
   ClassNode data=read(jar,"am2/playerextensions/SkillData.class");
   ClassNode gui=read(jar,"am2/guis/GuiSkillTrees.class");
   boolean packet=false;
   boolean[] sent=new boolean[4];
   for(MethodNode method:data.methods) {
    if(method.name.equals("canPurchaseWithPoints"))fail("Addon purchase policy remains in SkillData");
    for(AbstractInsnNode instruction:method.instructions.toArray()) {
     rejectAddonReference(instruction);
     if(instruction instanceof MethodInsnNode && ((MethodInsnNode)instruction).name.equals("canPurchaseWithPoints"))
      fail("Addon purchase policy is still called by "+method.name);
     if(method.name.equals("handlePacketData") && silver(instruction)) {
      AbstractInsnNode before=previous(instruction),after=next(instruction);
      if(call(before,"am2/network/AMDataReader","getInt","()I")
        &&call(after,"am2/playerextensions/SkillData","setSpellPoints","(ILam2/api/spell/enums/SkillPointTypes;)V"))packet=true;
     }
     if(method.name.equals("getUpdateData") && instruction instanceof FieldInsnNode) {
      FieldInsnNode field=(FieldInsnNode)instruction;
      if(field.getOpcode()!=Opcodes.GETFIELD || !field.owner.equals("am2/playerextensions/SkillData")
        || !field.name.equals("spellPoints") || !field.desc.equals("[I"))continue;
      AbstractInsnNode index=next(instruction),load=next(index),write=next(load);
      if(index!=null && index.getOpcode()>=Opcodes.ICONST_0 && index.getOpcode()<=Opcodes.ICONST_3
        &&load!=null && load.getOpcode()==Opcodes.IALOAD
        &&call(write,"am2/network/AMDataWriter","add","(I)Lam2/network/AMDataWriter;"))
       sent[index.getOpcode()-Opcodes.ICONST_0]=true;
     }
    }
   }
   for(MethodNode method:gui.methods)for(AbstractInsnNode instruction:method.instructions.toArray()) {
    rejectAddonReference(instruction);
    if(silver(instruction) && call(next(instruction),"am2/playerextensions/SkillData","getSpellPoints","(Lam2/api/spell/enums/SkillPointTypes;)I"))
     fail("Addon Silver counter remains in the native Oculus");
    if(instruction instanceof LdcInsnNode && "am2.gui.silverDiscoveryOnly".equals(((LdcInsnNode)instruction).cst))
     fail("Addon discovery tooltip remains in the native Oculus");
   }
   if(!packet)fail("Client does not read the native fourth balance from the sync packet");
   for(int index=0;index<sent.length;index++)if(!sent[index])fail("Native sync no longer writes balance "+index);
   if(jar.getJarEntry("am2/api/events/WorldFusionEvent.class")==null)
    fail("Backward-compatible optional World Fusion event API is missing");
   System.out.println("Verified: native four-balance synchronization; no addon counter, tooltip or purchase policy in the core.");
  }
 }
 static void rejectAddonReference(AbstractInsnNode instruction) {
  String owner=instruction instanceof MethodInsnNode?((MethodInsnNode)instruction).owner
   :instruction instanceof FieldInsnNode?((FieldInsnNode)instruction).owner
   :instruction instanceof TypeInsnNode?((TypeInsnNode)instruction).desc:null;
  if(owner!=null && owner.startsWith("airburn/am2playground/"))fail("Core skill handling references Playground classes");
 }
 static boolean call(AbstractInsnNode instruction,String owner,String name,String descriptor) {
  if(!(instruction instanceof MethodInsnNode))return false;
  MethodInsnNode method=(MethodInsnNode)instruction;
  return method.owner.equals(owner)&&method.name.equals(name)&&method.desc.equals(descriptor);
 }
 static boolean silver(AbstractInsnNode i) {
  return i instanceof FieldInsnNode && i.getOpcode()==Opcodes.GETSTATIC
   && ((FieldInsnNode)i).owner.equals("am2/api/spell/enums/SkillPointTypes")
   && ((FieldInsnNode)i).name.equals("SILVER");
 }
 static AbstractInsnNode next(AbstractInsnNode i) {if(i==null)return null;do{i=i.getNext();}while(i!=null && i.getOpcode()<0);return i;}
 static AbstractInsnNode previous(AbstractInsnNode i) {if(i==null)return null;do{i=i.getPrevious();}while(i!=null && i.getOpcode()<0);return i;}
 static void fail(String message){throw new IllegalStateException(message);}
 static ClassNode read(JarFile jar,String path)throws Exception {
  ClassNode c=new ClassNode();new ClassReader(jar.getInputStream(jar.getJarEntry(path))).accept(c,0);return c;
 }
}
