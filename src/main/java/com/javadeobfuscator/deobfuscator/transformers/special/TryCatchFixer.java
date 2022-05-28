package com.javadeobfuscator.deobfuscator.transformers.special;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;

import com.javadeobfuscator.deobfuscator.utils.TransformerHelper;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.TypeAnnotationNode;

import com.javadeobfuscator.deobfuscator.analyzer.FlowAnalyzer;
import com.javadeobfuscator.deobfuscator.config.TransformerConfig;
import com.javadeobfuscator.deobfuscator.transformers.Transformer;

public class TryCatchFixer extends Transformer<TransformerConfig> {
    @Override
    public boolean transform() throws Throwable {
        System.out.println("[Special] [TryCatchFixer] Starting");
        AtomicInteger count = new AtomicInteger();
        for (ClassNode classNode : classNodes()) {
            for (MethodNode method : classNode.methods) {
                if (method.tryCatchBlocks.size() <= 0)
                    continue;
                FlowAnalyzer.Result result = new FlowAnalyzer(method).analyze();
                List<TryCatchChain> chains = new ArrayList<>();
                Map<LabelNode, List<TryCatchBlockNode>> pass = new HashMap<>();
                List<LabelNode> labels = new ArrayList<>(result.trycatchMap.keySet());
                for (Entry<LabelNode, List<TryCatchBlockNode>> entry : result.trycatchMap.entrySet()) {
                    for (TryCatchBlockNode tcbn : entry.getValue()) {
                        if (!pass.containsKey(entry.getKey()) || !pass.get(entry.getKey()).contains(tcbn)) {
                            TryCatchChain chain = new TryCatchChain(tcbn.handler, tcbn.type,
                                    tcbn.visibleTypeAnnotations, tcbn.visibleTypeAnnotations);
                            chains.add(chain);
                            chain.covered.add(entry.getKey());
                            chain.end = labels.get(labels.indexOf(entry.getKey()) + 1);
                            pass.putIfAbsent(entry.getKey(), new ArrayList<>());
                            pass.get(entry.getKey()).add(tcbn);
                            for (int i = labels.indexOf(entry.getKey()) + 1; i < labels.size(); i++) {
                                List<TryCatchBlockNode> list = result.trycatchMap.get(labels.get(i));
                                boolean found = false;
                                for (TryCatchBlockNode tcbn2 : list) {
                                    if (tcbn.handler.equals(tcbn2.handler)
                                        && Objects.equals(tcbn.type, tcbn2.type)
                                        && Objects.equals(tcbn.visibleTypeAnnotations, tcbn2.visibleTypeAnnotations)
                                        && Objects.equals(tcbn.invisibleTypeAnnotations, tcbn2.invisibleTypeAnnotations))
                                    {
                                        chain.covered.add(labels.get(i));
                                        chain.end = labels.get(i + 1);
                                        pass.putIfAbsent(labels.get(i), new ArrayList<>());
                                        pass.get(labels.get(i)).add(tcbn2);
                                        found = true;
                                        break;
                                    }
                                }
                                if (!found)
                                    break;
                            }
                        }
                    }
                }
                Map<TryCatchChain, Set<LabelNode>> splits = new HashMap<>();
                for (int i = 0; i < chains.size(); i++) {
                    for (int i2 = i + 1; i2 < chains.size(); i2++) {
                        TryCatchChain chain1 = chains.get(i);
                        TryCatchChain chain2 = chains.get(i2);
                        LabelNode start = labels.indexOf(chain1.covered.get(0)) > labels.indexOf(chain2.covered.get(0))
                                ? chain1.covered.get(0) : chain2.covered.get(0);
                        LabelNode end = labels.indexOf(chain1.end) > labels.indexOf(chain2.end)
                                ? chain2.end : chain1.end;
                        if (labels.indexOf(start) >= labels.indexOf(end))
                            continue;
                        int index1 = -1;
                        for (int ii = 0; ii < result.trycatchMap.get(start).size(); ii++) {
                            TryCatchBlockNode tcbn = result.trycatchMap.get(start).get(ii);
                            if (tcbn.handler.equals(chain1.handler)
                                && Objects.equals(tcbn.type, chain1.type)
                                && Objects.equals(tcbn.visibleTypeAnnotations, chain1.visibleTypeAnnotations)
                                && tcbn.invisibleTypeAnnotations.equals(chain1.invisibleTypeAnnotations))
                            {
                                index1 = ii;
                                break;
                            }
                        }
                        int index2 = -1;
                        for (int ii = 0; ii < result.trycatchMap.get(start).size(); ii++) {
                            TryCatchBlockNode tcbn = result.trycatchMap.get(start).get(ii);
                            if (TransformerHelper.tryCatchChainFitting(chain2, tcbn))
                            {
                                index2 = ii;
                                break;
                            }
                        }
                        boolean oneOnTop = index1 > index2;
                        index1 = -1;
                        index2 = -1;
                        for (int ii = labels.indexOf(start); ii < labels.indexOf(end); ii++) {
                            LabelNode now = labels.get(ii);
                            for (int iii = 0; iii < result.trycatchMap.get(now).size(); iii++) {
                                TryCatchBlockNode tcbn = result.trycatchMap.get(now).get(iii);
                                if (TransformerHelper.tryCatchChainFitting(chain1, tcbn))
                                {
                                    index1 = iii;
                                    break;
                                }
                            }
                            for (int iii = 0; iii < result.trycatchMap.get(now).size(); iii++) {
                                TryCatchBlockNode tcbn = result.trycatchMap.get(now).get(iii);
                                if (TransformerHelper.tryCatchChainFitting(chain2, tcbn))
                                {
                                    index2 = iii;
                                    break;
                                }
                            }
                            boolean oneOnTopTemp = index1 > index2;
                            if (oneOnTop != oneOnTopTemp) {
                                splits.putIfAbsent(chain1, new HashSet<>());
                                splits.get(chain1).add(now);
                                oneOnTop = oneOnTopTemp;
                            }
                        }
                    }
                }
                if (!splits.isEmpty())
                    System.out.println("Irregular exception table at " + classNode.name + ", " + method.name + method.desc);
                for (Entry<TryCatchChain, Set<LabelNode>> entry : splits.entrySet()) {
                    List<LabelNode> orderedSplits = new ArrayList<>(entry.getValue());
                    orderedSplits.sort(Comparator.comparingInt(labels::indexOf));
                    List<TryCatchChain> replacements = new ArrayList<>();
                    replacements.add(entry.getKey());
                    for (LabelNode l : orderedSplits) {
                        int lIndex = labels.indexOf(l);
                        TryCatchChain toModify = null;
                        for (TryCatchChain ch : replacements) {
                            if (labels.indexOf(ch.covered.get(0)) <= lIndex
                                && labels.indexOf(ch.covered.get(ch.covered.size() - 1)) >= lIndex)
                            {
                                toModify = ch;
                                break;
                            }
                        }
                        TryCatchChain split1 = new TryCatchChain(toModify.handler,
                                toModify.type, toModify.visibleTypeAnnotations,
                                toModify.invisibleTypeAnnotations);
                        for (LabelNode lbl : toModify.covered) {
                            if (lbl == l)
                                break;
                            split1.covered.add(lbl);
                        }
                        split1.end = l;
                        TryCatchChain split2 = new TryCatchChain(toModify.handler,
                                toModify.type, toModify.visibleTypeAnnotations,
                                toModify.invisibleTypeAnnotations);
                        for (int iii = toModify.covered.indexOf(l); iii < toModify.covered.size(); iii++) {
                            split2.covered.add(toModify.covered.get(iii));
                        }
                        split2.end = toModify.end;
                        int toModifyIndex = replacements.indexOf(toModify);
                        replacements.set(toModifyIndex, split2);
                        replacements.add(toModifyIndex, split1);
                    }
                    int chainIndex = chains.indexOf(entry.getKey());
                    chains.set(chainIndex, replacements.get(replacements.size() - 1));
                    replacements.remove(replacements.size() - 1);
                    chains.addAll(chainIndex, replacements);
                }
                List<TryCatchBlockNode> exceptions = new ArrayList<>();
                boolean modified;
                do {
                    modified = false;
                    TryCatchChain remove = null;
                    for (TryCatchChain chain : chains) {
                        boolean failed = false;
                        for (LabelNode lbl : chain.covered) {
                            List<TryCatchBlockNode> list = result.trycatchMap.get(lbl);
                            if (list.isEmpty()) {
                                failed = true;
                                break;
                            }
                            TryCatchBlockNode tcbn = list.get(0);
                            if (!TransformerHelper.tryCatchChainFitting(chain, tcbn)) {
                                failed = true;
                                break;
                            }
                        }
                        if (!failed) {
                            TryCatchBlockNode tcbn = new TryCatchBlockNode(chain.covered.get(0), chain.end,
                                    chain.handler, chain.type);
                            tcbn.visibleTypeAnnotations = chain.visibleTypeAnnotations;
                            tcbn.invisibleTypeAnnotations = tcbn.invisibleTypeAnnotations;
                            exceptions.add(tcbn);
                            remove = chain;
                            for (LabelNode lbl : chain.covered) {
                                result.trycatchMap.get(lbl).remove(0);
                            }
                            break;
                        }
                    }
                    if (remove != null) {
                        modified = true;
                        chains.remove(remove);
                    }
                } while (modified);
                if (!chains.isEmpty())
                    throw new IllegalStateException("Impossible exception table at " + classNode.name + ", " + method.name + method.desc);

                boolean same = method.tryCatchBlocks.size() == exceptions.size();
                if (same)
                    for (int i = 0; i < method.tryCatchBlocks.size(); i++) {
                        TryCatchBlockNode tcbn1 = method.tryCatchBlocks.get(i);
                        TryCatchBlockNode tcbn2 = exceptions.get(i);
                        if (tcbn1.start != tcbn2.start)
                            same = false;
                        else if (tcbn1.end != tcbn2.end)
                            same = false;
                        else if (tcbn1.handler != tcbn2.handler)
                            same = false;
                        else if (!Objects.equals(tcbn1.type, tcbn2.type))
                            same = false;
                        else if (!Objects.equals(tcbn1.invisibleTypeAnnotations, tcbn2.invisibleTypeAnnotations))
                            same = false;
                        else if (!Objects.equals(tcbn1.visibleTypeAnnotations, tcbn2.visibleTypeAnnotations))
                            same = false;
                        if (!same)
                            break;
                    }
                if (!same)
                    count.incrementAndGet();
                method.tryCatchBlocks = exceptions;
            }
        }
        System.out.println("[Special] [TryCatchFixer] Fixed " + count + " methods");
        System.out.println("[Special] [TryCatchFixer] Done");
        return count.get() > 0;
    }

    public static class TryCatchChain {
        public List<LabelNode> covered;
        public LabelNode end;
        public LabelNode handler;
        public String type;
        public List<TypeAnnotationNode> visibleTypeAnnotations;
        public List<TypeAnnotationNode> invisibleTypeAnnotations;

        public TryCatchChain(LabelNode handler, String type, List<TypeAnnotationNode> visibleTypeAnnotations,
                List<TypeAnnotationNode> invisibleTypeAnnotations) {
            this.handler = handler;
            this.type = type;
            this.visibleTypeAnnotations = visibleTypeAnnotations;
            this.invisibleTypeAnnotations = invisibleTypeAnnotations;
            covered = new ArrayList<>();
        }
    }
}
    
