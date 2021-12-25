package com.javadeobfuscator.deobfuscator.transformers.special;

import com.google.common.io.BaseEncoding;
import com.javadeobfuscator.deobfuscator.config.TransformerConfig;
import com.javadeobfuscator.deobfuscator.executor.Context;
import com.javadeobfuscator.deobfuscator.executor.defined.JVMMethodProvider;
import com.javadeobfuscator.deobfuscator.executor.defined.MappedFieldProvider;
import com.javadeobfuscator.deobfuscator.executor.defined.MappedMethodProvider;
import com.javadeobfuscator.deobfuscator.executor.defined.PrimitiveFieldProvider;
import com.javadeobfuscator.deobfuscator.executor.providers.ComparisonProvider;
import com.javadeobfuscator.deobfuscator.executor.providers.DelegatingProvider;
import com.javadeobfuscator.deobfuscator.executor.values.JavaObject;
import com.javadeobfuscator.deobfuscator.executor.values.JavaValue;
import com.javadeobfuscator.deobfuscator.transformers.Transformer;
import com.javadeobfuscator.deobfuscator.utils.TransformerHelper;
import com.javadeobfuscator.deobfuscator.utils.Utils;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.util.Base64;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

@TransformerConfig.ConfigOptions(configClass = StringFlowObfTransformer.Config.class)
public class StringFlowObfTransformer extends Transformer<StringFlowObfTransformer.Config> {

    public static class Config extends TransformerConfig {
        public Config() {
            super(StringFlowObfTransformer.class);
        }
    }

    @Override
    public boolean transform() throws Throwable {
        DelegatingProvider provider = new DelegatingProvider();
        provider.register(new MappedFieldProvider());
        provider.register(new PrimitiveFieldProvider());
        provider.register(new JVMMethodProvider());
        provider.register(new MappedMethodProvider(classes));
        provider.register(new ComparisonProvider() {
            @Override
            public boolean instanceOf(JavaValue target, Type type,
                    Context context) {
                if (!(target.value() instanceof JavaObject)) {
                    return false;
                }
                return type.getInternalName().equals(((JavaObject) target.value()).type());
            }

            @Override
            public boolean checkcast(JavaValue target, Type type,
                    Context context) {
                return true;
            }

            @Override
            public boolean checkEquality(JavaValue first, JavaValue second,
                    Context context) {
                return false;
            }

            @Override
            public boolean canCheckInstanceOf(JavaValue target, Type type,
                    Context context) {
                return true;
            }

            @Override
            public boolean canCheckcast(JavaValue target, Type type,
                    Context context) {
                return true;
            }

            @Override
            public boolean canCheckEquality(JavaValue first, JavaValue second,
                    Context context) {
                return false;
            }
        });

        System.out.println("[Special] [StringFlowObfTransformer] Starting");
        AtomicInteger stringReplacements = new AtomicInteger();
        AtomicInteger base64 = new AtomicInteger();
        AtomicInteger base32 = new AtomicInteger();

        Set<String> erroredClasses = new HashSet<>();
        //Fold numbers
        for (ClassNode classNode : classNodes()) {
            if (erroredClasses.contains(classNode.name)) {
                continue;
            }
            try {
                for (MethodNode method : classNode.methods) {
                    boolean modified;
                    do {
                        modified = false;
                        Base64.Decoder b64 = Base64.getDecoder();
                        BaseEncoding b32 = BaseEncoding.base32();
                        for (AbstractInsnNode insn : method.instructions.toArray()) {
                            if (TransformerHelper.isInvokeStatic(insn, "java/util/Base64", "getDecoder", "()Ljava/util/Base64$Decoder;")) {
                                AbstractInsnNode next = Utils.getNext(insn);
                                if (TransformerHelper.isConstantString(next) && next.getNext() != null) {
                                    String base64String = TransformerHelper.getConstantString(next);
                                    next = Utils.getNext(next);
                                    if (TransformerHelper.isInvokeVirtual(next, "java/util/Base64$Decoder", "decode", "(Ljava/lang/String;)[B")) {
                                        method.instructions.remove(Utils.getNext(insn, 2));
                                        method.instructions.remove(Utils.getNext(insn));
                                        method.instructions.insertBefore(insn, new LdcInsnNode(new String(b64.decode(base64String))));
                                        method.instructions.insert(insn, new MethodInsnNode(INVOKEVIRTUAL, "java/lang/String", "getBytes", "()[B", false));
                                        method.instructions.remove(insn);
                                        base64.incrementAndGet();
                                        modified = true;
                                    }
                                }
                            }
                            if (TransformerHelper.isConstantString(insn) && insn.getNext() != null) {
                                AbstractInsnNode next = Utils.getNext(insn);
                                AbstractInsnNode prev = Utils.getPrevious(insn);
                                if (TransformerHelper.isInvokeVirtual(next, "org/apache/commons/codec/binary/Base32", "decode", "(Ljava/lang/String;)[B")) {
                                    method.instructions.remove(next);
                                    if (prev.getOpcode() == ALOAD || prev.getOpcode() == GETFIELD) method.instructions.remove(prev);
                                    else method.instructions.insertBefore(insn, new InsnNode(POP));
                                    method.instructions.insertBefore(insn, new LdcInsnNode(new String(b32.decode(TransformerHelper.getConstantString(insn)))));
                                    method.instructions.insert(insn, new MethodInsnNode(INVOKEVIRTUAL, "java/lang/String", "getBytes", "()[B", false));
                                    method.instructions.remove(insn);
                                    base32.incrementAndGet();
                                    modified = true;
                                }
                                if (TransformerHelper.isInvokeVirtual(next, "java/lang/String", "getBytes", null) && TransformerHelper.isInvokeVirtual(Utils.getNext(next), "org/apache/commons/codec/binary/Base32", "decode", "([B)[B")) {
                                    method.instructions.remove(Utils.getNext(next));
                                    method.instructions.remove(next);
                                    if (prev.getOpcode() == ALOAD || prev.getOpcode() == GETFIELD) method.instructions.remove(prev);
                                    method.instructions.insertBefore(insn, new LdcInsnNode(new String(b32.decode(TransformerHelper.getConstantString(insn)))));
                                    method.instructions.insert(insn, new MethodInsnNode(INVOKEVIRTUAL, "java/lang/String", "getBytes", "()[B", false));
                                    method.instructions.remove(insn);
                                    base32.incrementAndGet();
                                    modified = true;
                                }
                                if (TransformerHelper.isConstantString(next) && next.getNext() != null) {
                                    LdcInsnNode replacing = (LdcInsnNode) next;
                                    next = Utils.getNext(next);
                                    if (TransformerHelper.isInvokeVirtual(next, "java/lang/String", "replace", "(Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Ljava/lang/String;")) {
                                        String junk = TransformerHelper.getConstantString(insn);
                                        String junk1 = TransformerHelper.getConstantString(replacing);
                                        if (Objects.equals(junk, junk1)) {
                                            method.instructions.remove(insn);
                                            method.instructions.remove(replacing);
                                            method.instructions.remove(next);
                                            stringReplacements.incrementAndGet();
                                            modified = true;
                                        }
                                    } else if (TransformerHelper.isConstantString(next) && next.getNext() != null) {
                                        LdcInsnNode replaced = (LdcInsnNode) next;
                                        next = Utils.getNext(next);
                                        if (TransformerHelper.isInvokeVirtual(next, "java/lang/String", "replace", "(Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Ljava/lang/String;")) {
                                            String orig = TransformerHelper.getConstantString(insn);
                                            String replacingSeq = TransformerHelper.getConstantString(replacing);
                                            String replacedSeq = TransformerHelper.getConstantString(replaced);

                                            method.instructions.remove(replacing);
                                            method.instructions.remove(replaced);
                                            method.instructions.remove(next);
                                            method.instructions.insert(insn, new LdcInsnNode(orig.replace(replacingSeq, replacedSeq)));
                                            method.instructions.remove(insn);
                                            stringReplacements.incrementAndGet();
                                            modified = true;
                                        }
                                    }
                                }
                            }
                            if (insn instanceof TypeInsnNode && insn.getOpcode() == NEW && ((TypeInsnNode) insn).desc.equals("java/lang/String")) {
                                AbstractInsnNode next = Utils.getNext(insn);
                                if (next != null && next.getOpcode() == DUP) {
                                    next = Utils.getNext(next);
                                    if (TransformerHelper.isConstantString(next)) {
                                        String value = TransformerHelper.getConstantString(next);
                                        next = Utils.getNext(next);
                                        if (TransformerHelper.isInvokeVirtual(next, "java/lang/String", "getBytes", null)) {
                                            next = Utils.getNext(next);
                                            if (TransformerHelper.isInvokeSpecial(next, "java/lang/String", "<init>", "([B)V")) {
                                                method.instructions.insertBefore(insn, new LdcInsnNode(value));
                                                method.instructions.remove(Utils.getNext(insn, 4));
                                                method.instructions.remove(Utils.getNext(insn, 3));
                                                method.instructions.remove(Utils.getNext(insn, 2));
                                                method.instructions.remove(Utils.getNext(insn));
                                                method.instructions.remove(insn);
                                                modified = true;
                                            }
                                        } else if (TransformerHelper.isInvokeSpecial(next, "java/lang/String", "<init>", "(Ljava/lang/String;)V")) {
                                            method.instructions.insertBefore(insn, new LdcInsnNode(value));
                                            method.instructions.remove(Utils.getNext(insn, 3));
                                            method.instructions.remove(Utils.getNext(insn, 2));
                                            method.instructions.remove(Utils.getNext(insn));
                                            method.instructions.remove(insn);
                                            modified = true;
                                        }
                                    }
                                }
                            }
                        }
                    } while (modified);
                }
            } catch (Throwable t) {
                t.printStackTrace();
                erroredClasses.add(classNode.name);
            }
        }
        System.out.println("[Special] [StringFlowObfTransformer] Undid " + stringReplacements + " string replace instructions");
        System.out.println("[Special] [StringFlowObfTransformer] Undid " + base64 + " base64 instructions");
        System.out.println("[Special] [StringFlowObfTransformer] Undid " + base32 + " base32 instructions");
        if (!erroredClasses.isEmpty()) {
            System.out.println("[Special] [StringFlowObfTransformer] Errors occurred during decryption of " + erroredClasses.size() + " classes:");
            for (String erroredClass : erroredClasses) {
                System.out.println("[Special] [StringFlowObfTransformer]   - " + erroredClass);
            }
        }
        System.out.println("[Special] [StringFlowObfTransformer] Done");
        return stringReplacements.get() > 0;
    }
}
