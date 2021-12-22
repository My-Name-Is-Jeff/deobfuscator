package com.javadeobfuscator.deobfuscator.transformers.special;

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
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.HashSet;
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
                        for (AbstractInsnNode insn : method.instructions.toArray()) {
                            if (TransformerHelper.isConstantString(insn) && insn.getNext() != null) {
                                AbstractInsnNode next = Utils.getNext(insn);
                                if (TransformerHelper.isConstantString(next) && next.getNext() != null) {
                                    LdcInsnNode replacing = (LdcInsnNode) next;
                                    next = Utils.getNext(next);
                                    if (TransformerHelper.isConstantString(next) && next.getNext() != null) {
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
                        }
                    } while (modified);
                }
            } catch (Throwable t) {
                t.printStackTrace();
                erroredClasses.add(classNode.name);
            }
        }
        System.out.println("[Special] [StringFlowObfTransformer] Undid " + stringReplacements + " string replace instructions");
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
