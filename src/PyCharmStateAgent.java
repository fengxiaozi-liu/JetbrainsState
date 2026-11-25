import java.lang.instrument.*;
import java.security.*;
import java.util.*;
import org.jetbrains.org.objectweb.asm.*;
import org.jetbrains.org.objectweb.asm.tree.*;

/** Explicit profile for PY-263.7307.351. No heuristic matching at runtime. */
public final class PyCharmStateAgent {
    static final String STATE = "com/intellij/ide/M/G/j";
    static final String MANAGER = "com/intellij/ide/M/G/Zm";
    static final String TITLE = "com/intellij/ide/M/V/i/k";
    static final String MODEL = "com/intellij/ide/M/V/Z/Zm";
    static final String STATE_METHOD = "(Lcom/intellij/ide/M/u;Lcom/intellij/ide/M/w;SII)Lcom/intellij/ide/M/G/j;";
    static final Map<String,String> HASHES = Map.of(
        STATE,"e555ab930785a0cc6546459216520a32fab134cdc47f4219cdcee792fa4ab1ea",
        MANAGER,"9218cd01fb29ab4a655ecd462e61c44973364e540b0271e7a1892cef9650deb5",
        TITLE,"5be8217442dd8d71e520625ae79d682740b3fc2988f305b9c21b665a405fbf22",
        MODEL,"caf19e567ebd71d322d398c1e8ac59cd3758f17584a22bf802cd3737a9363f01",
        LocalStateAgent.ABOUT_TARGET,"01dc6b38187b84c42f90c8cac875fece1438a29a0ba2ac19312b189bb1638a48");

    public static void premain(Instrumentation instrumentation) {
        instrumentation.addTransformer(new ClassFileTransformer() {
            public byte[] transform(ClassLoader loader, String name, Class<?> type, ProtectionDomain domain, byte[] bytes) {
                if (!HASHES.containsKey(name)) return null;
                try {
                    byte[] result = rewrite(bytes);
                    System.err.println("[local-state-lab] Applied PyCharm profile in " + name);
                    return result;
                } catch (Exception error) {
                    System.err.println("[local-state-lab] PyCharm NOT applied: " + error);
                    return null;
                }
            }
        });
        System.err.println("[local-state-lab] Armed for PyCharm PY-263.7307.351 only.");
    }

    static byte[] rewrite(byte[] bytes) throws Exception {
        var reader = new ClassReader(bytes);
        String name = reader.getClassName();
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        if (!hash.equals(HASHES.get(name))) throw new IllegalArgumentException("Unsupported PyCharm class: " + name);
        var node = new ClassNode();
        reader.accept(node,0);
        if (node.name.equals(LocalStateAgent.ABOUT_TARGET)) return LocalStateAgent.rewrite(bytes);
        int changed = 0;
        for (var method : node.methods) {
            if (node.name.equals(STATE) && method.name.equals("Z") && method.desc.equals("(J)Z")) {
                method.instructions.clear();
                method.tryCatchBlocks.clear();
                if (method.localVariables != null) method.localVariables.clear();
                method.visibleLocalVariableAnnotations = null;
                method.invisibleLocalVariableAnnotations = null;
                method.instructions.add(new InsnNode(Opcodes.ICONST_1));
                method.instructions.add(new InsnNode(Opcodes.IRETURN));
                method.maxStack=1;
                method.maxLocals=3;
                changed++;
            }
            if (node.name.equals(MANAGER) && method.name.equals("Z") && method.desc.equals(STATE_METHOD)) {
                for (var instruction : method.instructions.toArray()) {
                    if (instruction instanceof FieldInsnNode f && f.getOpcode()==Opcodes.GETSTATIC
                            && f.owner.equals(STATE) && f.name.equals("i") && f.desc.equals("L"+STATE+";")) {
                        f.name="D";
                        changed++;
                    }
                }
            }
            if (node.name.equals(TITLE) && method.name.equals("Z")
                    && method.desc.equals("(SILcom/intellij/ide/M/V/Z/Zp;I)Ljava/lang/String;")) {
                int messages=0;
                for (var instruction : method.instructions.toArray()) {
                    if (instruction instanceof MethodInsnNode call && call.owner.equals("com/intellij/ide/M/k")
                            && call.name.equals("Z") && call.desc.equals("(JLjava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;")) {
                        if (++messages != 2) continue;
                        var blank=new InsnList();
                        blank.add(new InsnNode(Opcodes.POP));
                        blank.add(new LdcInsnNode(""));
                        method.instructions.insert(call,blank);
                        changed++;
                    }
                }
                if (messages!=2) throw new IllegalStateException("Unexpected PyCharm title sites");
            }
            if (node.name.equals(MODEL) && method.name.equals("Z")
                    && method.desc.equals("(Lcom/intellij/ide/M/C;Lcom/intellij/ide/M/V/s;J)Lcom/intellij/ide/M/V/Z/Zp;")) {
                int returns=0;
                for (var instruction : method.instructions.toArray()) {
                    if (!(instruction instanceof TypeInsnNode n) || n.getOpcode()!=Opcodes.NEW
                            || !n.desc.equals("com/intellij/ide/M/V/Z/Zh")) continue;
                    if (++returns==3) continue; // Missing-date error, not an explicit expiry result.
                    var dup=n.getNext();
                    if (dup.getOpcode()!=Opcodes.DUP || !(dup.getNext() instanceof MethodInsnNode init)
                            || !init.owner.equals(n.desc) || !init.name.equals("<init>") || !init.desc.equals("()V"))
                        throw new IllegalStateException("Unexpected PyCharm model construction");
                    n.desc="com/intellij/ide/M/V/Z/Z0";
                    init.owner=n.desc;
                    init.desc="(I)V";
                    method.instructions.insert(dup,new LdcInsnNode(Integer.MAX_VALUE));
                    method.maxStack=Math.max(method.maxStack,3);
                    changed++;
                }
                if (returns!=4) throw new IllegalStateException("Unexpected PyCharm expiry sites");
            }
        }
        if (changed!=(node.name.equals(MODEL)?3:1)) throw new IllegalStateException("Unexpected PyCharm changes: "+changed);
        var writer=new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }
}
