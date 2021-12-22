package com.javadeobfuscator.deobfuscator.rules.special;

import com.javadeobfuscator.deobfuscator.Deobfuscator;
import com.javadeobfuscator.deobfuscator.rules.Rule;
import com.javadeobfuscator.deobfuscator.transformers.Transformer;
import com.javadeobfuscator.deobfuscator.transformers.special.StringFlowObfTransformer;
import com.javadeobfuscator.deobfuscator.utils.TransformerHelper;
import com.javadeobfuscator.deobfuscator.utils.Utils;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.Collection;
import java.util.Collections;

public class RuleStringFlowObfuscator implements Rule {

	@Override
	public String getDescription() {
		return "Skids use dumb stuff like replacing strings";
	}

	@Override
	public String test(Deobfuscator deobfuscator) {
		for (ClassNode classNode : deobfuscator.getClasses().values()) {
			for (MethodNode methodNode : classNode.methods) {
				for (AbstractInsnNode insn : methodNode.instructions) {
					if (TransformerHelper.isConstantString(insn) && insn.getNext() != null) {
						AbstractInsnNode next = Utils.getNext(insn);
						if (TransformerHelper.isConstantString(next) && next.getNext() != null) {
							next = Utils.getNext(next);
							if (TransformerHelper.isConstantString(next) && next.getNext() != null) {
								next = Utils.getNext(next);
								if (TransformerHelper.isInvokeVirtual(next, "java/lang/String", "replace", "(Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Ljava/lang/String;")) {
									return "Skids like to use dumb stuff like replacing strings";
								}
							}
						}
					}
				}
			}
		}
		return null;
	}

	@Override
	public Collection<Class<? extends Transformer<?>>> getRecommendTransformers() {
		return Collections.singleton(StringFlowObfTransformer.class);
	}
}
