import java.nio.file.*;
import java.util.*;
import java.util.jar.JarFile;
import org.jetbrains.org.objectweb.asm.*;
import org.jetbrains.org.objectweb.asm.tree.*;

public final class VerifyPyCharm {
    static byte[] methodBytes(MethodNode m) {
        var w=new ClassWriter(0);
        w.visit(Opcodes.V17,Opcodes.ACC_PUBLIC,"Snapshot",null,"java/lang/Object",null);
        m.accept(w);w.visitEnd();return w.toByteArray();
    }
    public static void main(String[] args)throws Exception {
        AgentCompatibility.main(new String[]{"PY-263.7307.351",args[0]});
        for(var target:PyCharmStateAgent.HASHES.keySet()) {
            String jarName=target.equals(LocalStateAgent.ABOUT_TARGET)?"intellij.platform.ide.impl.jar":"intellij.platform.cs.jar";
            byte[] original;
            try(var jar=new JarFile(Path.of(args[0],"lib",jarName).toFile())) {
                original=jar.getInputStream(jar.getJarEntry(target+".class")).readAllBytes();
            }
            byte[] patched=PyCharmStateAgent.rewrite(original);
            var a=new ClassNode();var b=new ClassNode();
            new ClassReader(original).accept(a,0);new ClassReader(patched).accept(b,0);
            if (!a.name.equals(b.name)||a.methods.size()!=b.methods.size()||a.fields.size()!=b.fields.size()) throw new AssertionError("Schema changed");
            int edits=0;
            for(int n=0;n<a.methods.size();n++) {
                var old=a.methods.get(n);var m=b.methods.get(n);
                if(target.equals(PyCharmStateAgent.STATE)&&m.name.equals("Z")&&m.desc.equals("(J)Z")) {
                    if(m.instructions.size()!=2||m.instructions.getFirst().getOpcode()!=Opcodes.ICONST_1
                            ||m.instructions.getLast().getOpcode()!=Opcodes.IRETURN)throw new AssertionError("Predicate incorrect");
                    edits++;continue;
                }
                if(target.equals(PyCharmStateAgent.MANAGER)&&m.name.equals("Z")&&m.desc.equals(PyCharmStateAgent.STATE_METHOD)) {
                    var x=old.instructions.toArray();var y=m.instructions.toArray();
                    if(x.length!=y.length)throw new AssertionError("Manager shape changed");
                    for(int i=0;i<x.length;i++) {
                        if(x[i] instanceof FieldInsnNode f&&f.owner.equals(PyCharmStateAgent.STATE)&&f.name.equals("i")) {
                            if(!(y[i] instanceof FieldInsnNode g)||!g.owner.equals(f.owner)||!g.name.equals("D")||!g.desc.equals(f.desc))throw new AssertionError("State result incorrect");
                            g.name="i";edits++;
                        }
                    }
                }
                for(var instruction:m.instructions.toArray()) {
                    if(target.equals(LocalStateAgent.ABOUT_TARGET)&&instruction instanceof MethodInsnNode call
                            &&call.owner.equals("LocalStateAgent")&&call.name.equals("shiftAboutMessages")) {
                        m.instructions.remove(call);edits++;
                    }
                    if(target.equals(PyCharmStateAgent.TITLE)&&instruction instanceof InsnNode pop&&pop.getOpcode()==Opcodes.POP
                            &&pop.getNext() instanceof LdcInsnNode blank&&"".equals(blank.cst)) {
                        if(!(pop.getPrevious() instanceof MethodInsnNode call)||!call.owner.equals("com/intellij/ide/M/k"))throw new AssertionError("Wrong title site");
                        m.instructions.remove(blank);m.instructions.remove(pop);edits++;
                    }
                    if(target.equals(PyCharmStateAgent.MODEL)&&instruction instanceof TypeInsnNode allocation
                            &&allocation.getOpcode()==Opcodes.NEW&&allocation.desc.equals("com/intellij/ide/M/V/Z/Z0")) {
                        var value=allocation.getNext().getNext();
                        if(!(value instanceof LdcInsnNode ldc))continue; // Original non-expired construction.
                        if(!Integer.valueOf(Integer.MAX_VALUE).equals(ldc.cst))throw new AssertionError("Incorrect model days");
                        var init=(MethodInsnNode)value.getNext();
                        allocation.desc="com/intellij/ide/M/V/Z/Zh";init.owner=allocation.desc;init.desc="()V";
                        m.instructions.remove(value);edits++;
                    }
                }
                if(!Arrays.equals(methodBytes(old),methodBytes(m)))throw new AssertionError("Unrelated instructions changed: "+target+" "+m.name+m.desc);
            }
            if(edits!=(target.equals(PyCharmStateAgent.MODEL)?3:1))throw new AssertionError("Unexpected edit count: "+edits);
            var altered=original.clone();altered[altered.length-1]^=1;
            try {PyCharmStateAgent.rewrite(altered);throw new AssertionError("Fingerprint guard bypassed");}catch(IllegalArgumentException expected){}
            var path=Path.of(args[1]).resolve(target+".class");Files.createDirectories(path.getParent());Files.write(path,patched);
            System.out.println("PASS: scoped transform and fingerprint rejection: "+target);
        }
        var input=List.of("Subscription is active until December 10, 2026.");
        if(!LocalStateAgent.shiftAboutMessages(input).get(0).equals("Subscription is active until December 10, 2125.")
                ||!input.get(0).endsWith("2026."))throw new AssertionError("Display input mutated");
    }
}
