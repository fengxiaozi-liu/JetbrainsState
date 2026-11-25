import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.MessageDigest;
import java.security.ProtectionDomain;
import java.util.HexFormat;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.Locale;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;

/** A build-specific, in-memory experiment. Never writes into the IDE installation. */
public final class LocalStateAgent {
    static final String TARGET = "com/intellij/ide/z/L/tH";
    static final String ABOUT_TARGET = "com/intellij/ide/actions/AboutDialog";
    static final String COUNTDOWN_TARGET = "com/intellij/ide/z/Z/t/J";
    static final String COUNTDOWN_DESCRIPTOR = "(JLcom/intellij/ide/z/Z/z/tt;)Ljava/lang/String;";
    static final String EXPIRY_MODEL_TARGET = "com/intellij/ide/z/Z/z/T";
    static final String EXPIRY_MODEL_DESCRIPTOR = "(JLcom/intellij/ide/z/V;Lcom/intellij/ide/z/Z/ta;)Lcom/intellij/ide/z/Z/z/tt;";
    static final String LICENSE_STATE_DESCRIPTOR =
            "(Lcom/intellij/ide/z/S;Lcom/intellij/ide/z/c;J)Lcom/intellij/ide/z/L/tH;";
    static final Map<String, String> EXPECTED_SHA256 = Map.of(
            EXPIRY_MODEL_TARGET, "f0a8de999b9609e18f4026350e5924c54a5286c8aaaa18daa5888779053661db",
            COUNTDOWN_TARGET, "cfd21560f6b38af09267575592153cacaf84d7be818aa5c62fc30718da730c83",
            ABOUT_TARGET, "01dc6b38187b84c42f90c8cac875fece1438a29a0ba2ac19312b189bb1638a48",
            "com/intellij/ide/z/L/p8", "2f84a793e12142983b972b4fa136a92681522e5e19a3b68b640444fb67aaea1a",
            TARGET, "f104ffad427589c1f0a2274c22d9c11c93d5c9281f7d5d784a2f8225eab9c175");

    public static void premain(String options, Instrumentation instrumentation) {
        if ("PY-263.7307.351".equals(options)) {
            PyCharmStateAgent.premain(instrumentation);
            return;
        }
        if (options != null && !options.isEmpty() && !"GO-263.7307.211".equals(options)) {
            throw new IllegalArgumentException("Unsupported product profile: " + options);
        }
        instrumentation.addTransformer(new Transformer());
        System.err.println("[local-state-lab] Armed for GoLand 263.7307.211 only; revision 5 (local expiry window state).");
    }

    /** Presentation only. Never mutates the facade, its messages, or license dates. */
    public static List<String> shiftAboutMessages(List<String> original) {
        if (original == null || original.isEmpty()) return original;
        var formatter = DateTimeFormatter.ofPattern("MMMM d, uuuu", Locale.ENGLISH)
                .withResolverStyle(ResolverStyle.STRICT);
        String prefix = "Subscription is active until ";
        List<String> result = null;
        for (int i = 0; i < original.size(); i++) {
            String message = original.get(i);
            if (message == null || !message.startsWith(prefix) || !message.endsWith(".")) continue;
            try {
                LocalDate date = LocalDate.parse(message.substring(prefix.length(), message.length() - 1), formatter);
                String shifted = prefix + date.plusYears(99).format(formatter) + ".";
                if (result == null) result = new ArrayList<>(original);
                result.set(i, shifted);
            } catch (DateTimeParseException | ArithmeticException ignored) {
                // Unrecognized/localized messages are kept verbatim.
            }
        }
        return result == null ? original : result;
    }

    static byte[] rewrite(byte[] original) throws Exception {
        String name = new org.jetbrains.org.objectweb.asm.ClassReader(original).getClassName();
        String actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(original));
        if (!actual.equals(EXPECTED_SHA256.get(name))) {
            throw new IllegalArgumentException("Unsupported class fingerprint: " + actual);
        }
        return Bytecode.rewrite(original);
    }

    private static final class Transformer implements ClassFileTransformer {
        @Override
        public byte[] transform(ClassLoader loader, String name, Class<?> redefining,
                                ProtectionDomain domain, byte[] bytes) {
            if (!EXPECTED_SHA256.containsKey(name)) return null;
            try {
                byte[] rewritten = rewrite(bytes);
                System.err.println("[local-state-lab] Applied local patch in " + name);
                return rewritten;
            } catch (Exception error) {
                System.err.println("[local-state-lab] NOT applied: " + error);
                return null;
            }
        }
    }

    private static final class Bytecode {
        static byte[] rewrite(byte[] original) {
            var node = new org.jetbrains.org.objectweb.asm.tree.ClassNode();
            new org.jetbrains.org.objectweb.asm.ClassReader(original).accept(node, 0);
            int changed = 0;
            if (node.name.equals(EXPIRY_MODEL_TARGET)) {
                for (var method : node.methods) {
                    if (!method.name.equals("t") || !method.desc.equals(EXPIRY_MODEL_DESCRIPTOR)) continue;
                    int expiredReturns = 0;
                    for (var instruction : method.instructions.toArray()) {
                        if (!(instruction instanceof org.jetbrains.org.objectweb.asm.tree.TypeInsnNode allocation)
                                || allocation.getOpcode() != org.jetbrains.org.objectweb.asm.Opcodes.NEW
                                || !allocation.desc.equals("com/intellij/ide/z/Z/z/t2")) continue;
                        expiredReturns++;
                        // The third return represents a missing expiry date; leave that error intact.
                        if (expiredReturns == 3) continue;
                        var duplicate = allocation.getNext();
                        var init = duplicate.getNext();
                        if (duplicate.getOpcode() != org.jetbrains.org.objectweb.asm.Opcodes.DUP
                                || !(init instanceof org.jetbrains.org.objectweb.asm.tree.MethodInsnNode constructor)
                                || !constructor.owner.equals(allocation.desc) || !constructor.name.equals("<init>")
                                || !constructor.desc.equals("()V")) throw new IllegalStateException("Unexpected expired model construction");
                        allocation.desc = "com/intellij/ide/z/Z/z/tL";
                        constructor.owner = allocation.desc;
                        constructor.desc = "(I)V";
                        method.instructions.insert(duplicate, new org.jetbrains.org.objectweb.asm.tree.LdcInsnNode(Integer.MAX_VALUE));
                        changed++;
                    }
                    if (expiredReturns != 4) throw new IllegalStateException("Expected four expiry/error returns, found " + expiredReturns);
                    method.maxStack = Math.max(method.maxStack, 3);
                }
                if (changed != 3) throw new IllegalStateException("Expected three explicit expiry returns, found " + changed);
                var writer = new org.jetbrains.org.objectweb.asm.ClassWriter(0);
                node.accept(writer);
                return writer.toByteArray();
            }
            if (node.name.equals(COUNTDOWN_TARGET)) {
                for (var method : node.methods) {
                    if (!method.name.equals("t") || !method.desc.equals(COUNTDOWN_DESCRIPTOR)) continue;
                    int messages = 0;
                    for (var instruction : method.instructions.toArray()) {
                        if (instruction instanceof org.jetbrains.org.objectweb.asm.tree.MethodInsnNode call
                                && call.owner.equals("com/intellij/ide/z/J") && call.name.equals("t")
                                && call.desc.equals("(JLjava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;")) {
                            if (++messages != 2) continue;
                            // Second message is the live countdown title. The expired title and all data stay intact.
                            var blank = new org.jetbrains.org.objectweb.asm.tree.InsnList();
                            blank.add(new org.jetbrains.org.objectweb.asm.tree.InsnNode(org.jetbrains.org.objectweb.asm.Opcodes.POP));
                            blank.add(new org.jetbrains.org.objectweb.asm.tree.LdcInsnNode(""));
                            method.instructions.insert(call, blank);
                            changed++;
                        }
                    }
                    if (messages != 2) throw new IllegalStateException("Unexpected title message sites: " + messages);
                }
                if (changed != 1) throw new IllegalStateException("Expected one countdown title, found " + changed);
                var writer = new org.jetbrains.org.objectweb.asm.ClassWriter(0);
                node.accept(writer);
                return writer.toByteArray();
            }
            if (node.name.equals(ABOUT_TARGET)) {
                for (var method : node.methods) {
                    if (!method.name.equals("getText") || !method.desc.equals("()Ljavax/swing/Box;")) continue;
                    for (var instruction : method.instructions.toArray()) {
                        if (instruction instanceof org.jetbrains.org.objectweb.asm.tree.MethodInsnNode call
                                && call.owner.equals("com/intellij/ui/LicensingFacade")
                                && call.name.equals("getLicenseRestrictionsMessages")
                                && call.desc.equals("()Ljava/util/List;")) {
                            method.instructions.insert(call, new org.jetbrains.org.objectweb.asm.tree.MethodInsnNode(
                                    org.jetbrains.org.objectweb.asm.Opcodes.INVOKESTATIC,
                                    "LocalStateAgent", "shiftAboutMessages", "(Ljava/util/List;)Ljava/util/List;", false));
                            changed++;
                        }
                    }
                }
                if (changed != 1) throw new IllegalStateException("Expected one About display site, found " + changed);
                var writer = new org.jetbrains.org.objectweb.asm.ClassWriter(0);
                node.accept(writer);
                return writer.toByteArray();
            }
            if (node.name.equals("com/intellij/ide/z/L/p8")) {
                int expiryBranches = 0;
                for (var method : node.methods) {
                    if (method.name.equals("t") && method.desc.equals(LICENSE_STATE_DESCRIPTOR)) {
                        for (var instruction : method.instructions.toArray()) {
                            if (instruction instanceof org.jetbrains.org.objectweb.asm.tree.FieldInsnNode field
                                    && field.getOpcode() == org.jetbrains.org.objectweb.asm.Opcodes.GETSTATIC
                                    && field.owner.equals(TARGET) && field.name.equals("z")
                                    && field.desc.equals("L" + TARGET + ";")) {
                                // Only the explicit TrialExpired result in this build's
                                // license-state calculation. Other failure states and
                                // the UI's comparisons against TrialExpired stay intact.
                                field.name = "E";
                                expiryBranches++;
                            }
                        }
                    }
                    if (!method.name.equals("F") || !method.desc.equals("(JZ)V")) continue;
                    for (var instruction : method.instructions.toArray()) {
                        if (!(instruction instanceof org.jetbrains.org.objectweb.asm.tree.MethodInsnNode call)
                                || !call.owner.equals("com/intellij/ide/z/I/a") || !call.name.equals("t")) continue;
                        if (!call.desc.equals("(JLjava/lang/String;)V")
                                && !call.desc.equals("(JLcom/intellij/ide/z/L/p1;Ljava/lang/String;)V")) {
                            throw new IllegalStateException("Unexpected dialog call: " + call.desc);
                        }
                        var discard = new org.jetbrains.org.objectweb.asm.tree.InsnList();
                        discard.add(new org.jetbrains.org.objectweb.asm.tree.InsnNode(org.jetbrains.org.objectweb.asm.Opcodes.POP));
                        if (call.desc.contains("/p1;")) discard.add(new org.jetbrains.org.objectweb.asm.tree.InsnNode(org.jetbrains.org.objectweb.asm.Opcodes.POP));
                        discard.add(new org.jetbrains.org.objectweb.asm.tree.InsnNode(org.jetbrains.org.objectweb.asm.Opcodes.POP2));
                        method.instructions.insertBefore(call, discard);
                        method.instructions.remove(call);
                        changed++;
                    }
                }
                if (changed != 2) throw new IllegalStateException("Expected two startup dialogs, found " + changed);
                if (expiryBranches != 1) throw new IllegalStateException("Expected one trial-expiry result, found " + expiryBranches);
                var writer = new org.jetbrains.org.objectweb.asm.ClassWriter(0);
                node.accept(writer);
                return writer.toByteArray();
            }
            String targetMethod = "t";
            for (var method : node.methods) {
                if (!method.name.equals(targetMethod) || !method.desc.equals("(J)Z")) continue;
                method.instructions.clear();
                method.tryCatchBlocks.clear();
                if (method.localVariables != null) method.localVariables.clear();
                method.visibleLocalVariableAnnotations = null;
                method.invisibleLocalVariableAnnotations = null;
                method.instructions.add(new org.jetbrains.org.objectweb.asm.tree.InsnNode(
                        org.jetbrains.org.objectweb.asm.Opcodes.ICONST_1));
                method.instructions.add(new org.jetbrains.org.objectweb.asm.tree.InsnNode(
                        org.jetbrains.org.objectweb.asm.Opcodes.IRETURN));
                method.maxStack = 1;
                method.maxLocals = 3;
                changed++;
            }
            if (changed != 1) throw new IllegalStateException("Expected one predicate, found " + changed);
            var writer = new org.jetbrains.org.objectweb.asm.ClassWriter(0);
            node.accept(writer);
            return writer.toByteArray();
        }
    }
}
