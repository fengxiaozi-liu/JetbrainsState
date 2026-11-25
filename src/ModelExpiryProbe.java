import java.lang.instrument.*;
import java.security.ProtectionDomain;
import org.jetbrains.org.objectweb.asm.*;
import org.jetbrains.org.objectweb.asm.tree.*;

/** Test only: force the window's TrialExpired input; trace its real return object. */
public final class ModelExpiryProbe {
    public static void premain(String ignored, Instrumentation instrumentation) {
        instrumentation.addTransformer(new ClassFileTransformer() {
            public byte[] transform(ClassLoader loader, String name, Class<?> type, ProtectionDomain domain, byte[] bytes) {
                if (!"com/intellij/ide/z/Z/z/T".equals(name)) return null;
                var node = new ClassNode();
                new ClassReader(bytes).accept(node, 0);
                int forced = 0;
                for (var method : node.methods) {
                    if (!method.name.equals("t") || !method.desc.equals("(JLcom/intellij/ide/z/V;Lcom/intellij/ide/z/Z/ta;)Lcom/intellij/ide/z/Z/z/tt;")) continue;
                    for (var instruction : method.instructions.toArray()) {
                        if (instruction instanceof MethodInsnNode call && call.owner.equals("com/intellij/ide/z/Z/ta")
                                && call.desc.equals("(J)Lcom/intellij/ide/z/Z/tu;")) {
                            var code = new InsnList();
                            code.add(new InsnNode(Opcodes.POP));
                            code.add(new FieldInsnNode(Opcodes.GETSTATIC, "com/intellij/ide/z/Z/tu", "TrialExpired", "Lcom/intellij/ide/z/Z/tu;"));
                            method.instructions.insert(call, code);
                            forced++;
                        }
                        if (instruction.getOpcode() == Opcodes.ARETURN) {
                            var code = new InsnList();
                            code.add(new InsnNode(Opcodes.DUP));
                            code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "ModelExpiryProbe", "observe", "(Ljava/lang/Object;)V", false));
                            method.instructions.insertBefore(instruction, code);
                        }
                    }
                    method.maxStack += 1;
                }
                if (forced != 1) throw new IllegalStateException("Unexpected model input: " + forced);
                var writer = new ClassWriter(0);
                node.accept(writer);
                System.err.println("[model-expiry-probe] Forced TrialExpired input for window only.");
                return writer.toByteArray();
            }
        });
    }
    public static void observe(Object result) {
        System.err.println("[model-expiry-probe] Return model=" + result.getClass().getName());
    }
}
