// Copyright (c) 2015 K Team. All Rights Reserved.
package org.kframework.kore.compile;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import org.kframework.compile.ConfigurationInfo;
import org.kframework.compile.LabelInfo;
import org.kframework.definition.Context;
import org.kframework.definition.Rule;
import org.kframework.definition.Sentence;
import org.kframework.kore.K;
import org.kframework.kore.KApply;
import org.kframework.kore.KLabel;
import org.kframework.kore.KRewrite;
import org.kframework.kore.KVariable;
import org.kframework.kore.Sort;
import org.kframework.utils.errorsystem.KExceptionManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.kframework.kore.KORE.*;

/**
 * Arrange cell contents and variables to match the klabels declared for cells.
 * In Full K, cell contents can be written in any order, and variables can
 * be written that match multiple cells.
 * <p/>
 * In the input to this pass, parent cells are represented by appling the label directly
 * to a klist of all the children, variables, and rewrites under the cell.
 * Left cells should already be in their final form.
 * In the output each cell will be represented by using the cell labels in agreement
 * with the production declaring it, so parent cells will have a fixed arity with separate
 * argument positions reserved for different types of child cell.
 * <p/>
 * The most complicated part of the transformation is dealing with variables
 * in cells. An occurrence in a cell that might match child cells of different
 * sorts has to be split into several variables in different arguments, and any
 * occurrence of the variable outside of a cell replaced by a suitable
 * expression involving the split variables.
 * <p/>
 * This is currently not implemented in general, just the analysis to identify
 * the simple cases where there is in fact only one or zero (types of) cells
 * that a variable can bind, so it can be handled by either doing nothing,
 * or just deleting it from under cells and replacing it with an empty collection elsewhere.
 */
// TODO handle cell rewrites
public class SortCells {
    private final ConcretizationInfo cfg;
    public SortCells(ConfigurationInfo cfgInfo, LabelInfo labelInfo) {
        this.cfg = new ConcretizationInfo(cfgInfo, labelInfo);
    }

    public synchronized K sortCells(K term) {
        resetVars();
        analyzeVars(term);
        return processVars(term);

    }

    private Rule sortCells(Rule rule) {
        resetVars();
        analyzeVars(rule.body());
        analyzeVars(rule.requires());
        analyzeVars(rule.ensures());
        return new Rule(
                processVars(rule.body()),
                processVars(rule.requires()),
                processVars(rule.ensures()),
                rule.att());
    }

    private Context sortCells(Context context) {
        resetVars();
        analyzeVars(context.body());
        analyzeVars(context.requires());
        return new Context(
                processVars(context.body()),
                processVars(context.requires()),
                context.att());
    }

    public synchronized Sentence sortCells(Sentence s) {
        if (s instanceof Rule) {
            return sortCells((Rule) s);
        } else if (s instanceof Context) {
            return sortCells((Context) s);
        } else {
            return s;
        }
    }

    // Information on uses of a particular variable
    private class VarInfo {
        KVariable var;
        KLabel parentCell;
        Set<Sort> remainingCells;
        Map<Sort, K> split;

        void addOccurances(KLabel cell, KVariable var, List<K> items) {
            this.var = var;
            if (parentCell == null) {
                parentCell = cell;
            } else if (!parentCell.equals(cell)) {
                throw KExceptionManager.criticalError("Cell variable used under two cells, "
                        + parentCell + " and " + cell);
            }
            if (remainingCells == null) {
                remainingCells = new HashSet<>(cfg.getChildren(cell));
            }
            for (K item : items) {
                if (item instanceof KApply) {
                    KApply kApply = (KApply) item;
                    Sort s = cfg.getCellSort(kApply.klabel());
                    if (cfg.getMultiplicity(s) != ConfigurationInfo.Multiplicity.STAR) {
                        remainingCells.remove(s);
                    }
                } else if (item instanceof KVariable) {
                    // only get here if the variable was originally sorted in a semantic cast
                    Sort s = Sort(item.att().<String>get("sort").get());
                    if (cfg.getMultiplicity(s) != ConfigurationInfo.Multiplicity.STAR) {
                        remainingCells.remove(s);
                    }
                }
            }
        }

        K replacementTerm() {
            if (remainingCells.size() == 1) {
                return var;
            }
            throw KExceptionManager.compilerError("Expected exactly one cell remaining at variable position. Found: " + remainingCells, var);
        }

        Map<Sort, K> getSplit(KVariable var) {
            if (remainingCells.size() == 0) {
                return Collections.emptyMap();
            }
            if (remainingCells.size() == 1) {
                return ImmutableMap.of(Iterables.getOnlyElement(remainingCells), var);
            }
            if(split != null) {
                return split;
            }
            split = new HashMap<>();
            for (Sort cell : remainingCells) {
                split.put(cell, newDotVariable());
            }
            return split;
        }
    }

    private int counter = 0;
    KVariable newDotVariable() {
        KVariable newLabel;
        do {
            newLabel = KVariable("_" + (counter++));
        } while (variables.containsKey(newLabel));
        variables.put(newLabel, new VarInfo());
        return newLabel;
    }

    private Map<KVariable, VarInfo> variables = new HashMap<>();

    private void resetVars() {
        variables.clear();
    }

    private void analyzeVars(K term) {
        new VisitKORE() {
            @Override
            public Void apply(KApply k) {
                if (cfg.isParentCell(k.klabel())) {
                    KVariable leftVar = null, rightVar = null;
                    List<K> leftItems = new ArrayList<>(k.klist().size());
                    List<K> rightItems = new ArrayList<>(k.klist().size());
                    for (K item : k.klist().items()) {
                        if (isCellVariable(item)) {
                            leftVar = assignVar(leftVar, item);
                            rightVar = assignVar(rightVar, item);
                        } else {
                            if (item instanceof KRewrite) {
                                KRewrite rw = (KRewrite) item;
                                if (isCellVariable(rw.left())) {
                                    leftVar = assignVar(leftVar, rw.left());
                                } else {
                                    leftItems.add(rw.left());
                                }
                                if (isCellVariable(rw.right())) {
                                    rightVar = assignVar(rightVar, rw.right());
                                } else {
                                    rightItems.add(rw.right());
                                }
                            } else {
                                leftItems.add(item);
                                rightItems.add(item);
                            }
                        }
                    }
                    putVar(k, leftVar, leftItems);
                    putVar(k, rightVar, rightItems);
                }
                return super.apply(k);
            }

            private void putVar(KApply k, KVariable var, List<K> items) {
                if (var != null) {
                    if (!variables.containsKey(var)) {
                        variables.put(var, new VarInfo());
                    }
                    variables.get(var).addOccurances(k.klabel(), var, items);
                }
            }

            private KVariable assignVar(KVariable leftVar, K item) {
                if (leftVar != null) {
                    throw KExceptionManager.compilerError(
                            "AC matching of multiple cell variables not yet supported. "
                                    + "encountered variables " + leftVar.toString() + " and "
                                    + item.toString(), item);
                }
                leftVar = (KVariable) item;
                return leftVar;
            }

            private boolean isCellVariable(K item) {
                if (item instanceof KVariable) {
                    if (item.att().<String>get("sort").isEmpty()) {
                        return true;
                    } else {
                        Sort varSort = Sort(item.att().<String>get("sort").get());
                        if (!cfg.cfg.isCell(varSort)) {
                            return true;
                        } else {
                            return false;
                        }
                    }
                } else {
                    return false;
                }
            }
        }.apply(term);
    }

    private K processVars(K term) {
        return new TransformKORE() {
            @Override
            public K apply(KApply k) {
                if (!cfg.isParentCell(k.klabel())) {
                    if (k.klabel().equals(KLabel("isCells"))) {
                        if (k.klist().items().size() != 1) {
                            throw KExceptionManager.compilerError("Unexpected isCells predicate of arity "
                                    + k.klist().size() + " which cannot be split into individual cells.", k);
                        }
                        K item = k.klist().items().get(0);
                        Map<Sort, K> split = getSplit(item);
                        if (split == null) {
                            // someone typed a variable as Cells but not in a cell context. Therefore, we simply drop
                            // this check.
                            return BooleanUtils.TRUE;
                        }
                        return split.entrySet().stream().map(e -> (K) KApply(KLabel("is" + e.getKey().name()), e.getValue())).reduce(BooleanUtils.TRUE, BooleanUtils::and);
                    }
                    return super.apply(k);
                } else {
                    List<Sort> order = cfg.getChildren(k.klabel());
                    ArrayList<K> ordered = new ArrayList<K>(Collections.nCopies(order.size(), null));
                    for (K item : k.klist().items()) {
                        Map<Sort, K> split = getSplit(item);
                        assert split != null;
                        for (Map.Entry<Sort, K> e : split.entrySet()) {
                            ordered.set(order.indexOf(e.getKey()), e.getValue());
                        }
                    }
                    return KApply(k.klabel(), KList(ordered), k.att());
                }
            }

            private Map<Sort, K> getSplit(K item) {
                if (item instanceof KVariable) {
                    VarInfo info = variables.get(item);
                    if (info == null) {
                        return null;
                    }
                    return info.getSplit((KVariable) item);
                } else if (item instanceof KApply) {
                    if (IncompleteCellUtils.flattenCells(item).size() == 0) {
                        return Collections.emptyMap();
                    }
                    return Collections.singletonMap(cfg.getCellSort(((KApply) item).klabel()), apply(item));
                } else if (item instanceof KRewrite) {
                    KRewrite rw = (KRewrite) item;
                    Map<Sort, K> splitLeft = getSplit(rw.left());
                    Map<Sort, K> splitRight = getSplit(rw.right());
                    if (splitLeft == null || splitRight == null) return null;
                    if (splitLeft.keySet().containsAll(splitRight.keySet())) {
                        for (Sort s : Sets.difference(splitLeft.keySet(), splitRight.keySet())) {
                            switch(cfg.getMultiplicity(s)) {
                            case ONE:
                                throw KExceptionManager.compilerError("Cannot rewrite a multiplicity=\"1\" cell to the cell unit.", item)
                            }
                        }
                    }
                    if (!splitLeft.keySet().equals(splitRight.keySet())) {
                        throw KExceptionManager.compilerError("Cannot compute cell variable split of rewrite in which "
                                + "left and right hand side have different cells. Found: " + splitLeft.keySet()
                                + " and " + splitRight.keySet(), item);
                    }
                    return splitLeft.keySet().stream().collect(Collectors.toMap(sort -> sort,
                            sort -> KRewrite(splitLeft.get(sort), splitRight.get(sort), rw.att())));
                } else {
                    throw KExceptionManager.compilerError("Unexpected kind of term found in cell. Expected variable, "
                            + "apply, or rewrite; found " + item.getClass().getSimpleName(), item);
                }
            }

            @Override
            public K apply(KVariable v) {
                VarInfo info = variables.get(v);
                if (info != null) {
                    return info.replacementTerm();
                } else {
                    return v;
                }
            }
        }.apply(term);
    }
}
