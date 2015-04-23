// Copyright (c) 2014-2015 K Team. All Rights Reserved.
package org.kframework.backend.java.rewritemachine;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.apache.commons.collections4.ListUtils;
import org.kframework.backend.java.kil.*;
import org.kframework.backend.java.kil.CellCollection.Cell;
import org.kframework.backend.java.rewritemachine.RHSInstruction.Constructor;
import org.kframework.backend.java.symbolic.DeepCloner;
import org.kframework.backend.java.symbolic.NonACPatternMatcher;
import org.kframework.backend.java.symbolic.RuleAuditing;
import org.kframework.backend.java.symbolic.Substitution;
import org.kframework.backend.java.util.Profiler;
import org.kframework.backend.java.util.RewriteEngineUtils;

import java.util.Collection;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * K abstract rewrite machine. Given a subject term and a rewrite rule, the
 * rewrite machine attempts to apply the rewrite rule by executing the
 * instructions generated by the rewrite rule. When the rule matches the subject
 * term, the rewrite machine performs efficient destructive updates to the
 * subject term such that its previous state before rewriting is not preserved.
 *
 * @author YilongL
 *
 */
public class KAbstractRewriteMachine {

    private final Rule rule;
    private final CellCollection.Cell subject;
    private final List<MatchingInstruction> instructions;

    private ExtendedSubstitution fExtSubst = new ExtendedSubstitution();
    private List<List<ExtendedSubstitution>> fMultiExtSubsts = Lists.newArrayList();

    // program counter
    private int pc = 1;
    private MatchingInstruction nextInstr;
    private boolean success = true;
    private boolean isStarNested = false;

    private final NonACPatternMatcher patternMatcher;

    private final TermContext context;

    private KAbstractRewriteMachine(Rule rule, CellCollection.Cell subject, TermContext context) {
        this.rule = rule;
        this.subject = subject;
        this.instructions = rule.matchingInstructions();
        this.context = context;
        this.patternMatcher = new NonACPatternMatcher(context);
    }

    public static boolean rewrite(Rule rule, CellCollection.Cell subject, TermContext context) {
        KAbstractRewriteMachine machine = new KAbstractRewriteMachine(rule, subject, context);
        return machine.rewrite();
    }

    private boolean rewrite() {
        match(subject);
        if (success) {
            List<ExtendedSubstitution> normalizedExtSubsts = getCNFExtendedSubstitutions(
                    fExtSubst, fMultiExtSubsts);

            Profiler.startTimer(Profiler.EVALUATE_SIDE_CONDITIONS_TIMER);
            /* take the first match that also satisfies the side-condition as solution */
            ExtendedSubstitution solution = null;
            for (ExtendedSubstitution extSubst : normalizedExtSubsts) {
                Substitution<Variable, Term> updatedSubst = RewriteEngineUtils.
                        evaluateConditions(rule, extSubst.substitution(), context);
                if (updatedSubst != null) {
                    /* update the substitution according to the result of evaluation */
                    extSubst.setSubst(updatedSubst);
                    solution = extSubst;
                    break;
                }
            }
            Profiler.stopTimer(Profiler.EVALUATE_SIDE_CONDITIONS_TIMER);

            if (solution != null) {
                Profiler.startTimer(Profiler.LOCAL_REWRITE_BUILD_RHS_TIMER);
                // YilongL: cannot use solution.keySet() as variablesToReuse
                // because read-only cell may have already used up the binding
                // term
                Set<Variable> reusableVariables = Sets.newHashSet(rule.reusableVariables().elementSet());

                /* perform local rewrites under write cells */
                for (CellCollection.Cell cell : solution.writeCells()) {
                    List<RHSInstruction> instructions = getWriteCellInstructions(cell.cellLabel());
                    cell.setContent(construct(instructions, solution.substitution(), reusableVariables, context, rule.cellsToCopy().contains(cell.cellLabel())));
                }
                Profiler.stopTimer(Profiler.LOCAL_REWRITE_BUILD_RHS_TIMER);
            } else {
                success = false;
            }
        }
        return success;
    }

    public static Term construct(List<RHSInstruction> rhsInstructions,
            Map<Variable, Term> solution, Set<Variable> reusableVariables, TermContext context,
            boolean doClone) {

        if (rhsInstructions.size() == 1) {
            RHSInstruction instruction = rhsInstructions.get(0);
            switch (instruction.type()) {
            case PUSH:
                return instruction.term();
            case SUBST:
                Term var = instruction.term();
                Term content = solution.get(var);
                if (content == null) {
                    content = var;
                }
                return content;
            default:
                throw new AssertionError("unreachable");
            }
        }

        Deque<Term> stack = new LinkedList<>();
        for (RHSInstruction instruction : rhsInstructions) {
            switch (instruction.type()) {
            case PUSH:
                Term t = instruction.term();
                if (doClone) {
                    stack.push(DeepCloner.clone(t));
                } else {
                    stack.push(t);
                }
                break;
            case CONSTRUCT:
                Constructor constructor = instruction.constructor();
                switch (constructor.type()) {
                case BUILTIN_LIST:
                    BuiltinList.Builder builder = BuiltinList.builder(context);
                    for (int i = 0; i < constructor.size1(); i++) {
                        builder.addItem(stack.pop());
                    }
                    for (int i = 0; i < constructor.size2(); i++) {
                        builder.concatenate(stack.pop());
                    }
                    for (int i = 0; i < constructor.size3(); i++) {
                        builder.addItem(stack.pop());
                    }
                    stack.push(builder.build());
                    break;
                case BUILTIN_MAP:
                    BuiltinMap.Builder builder1 = BuiltinMap.builder(context);
                    for (int i = 0; i < constructor.size1(); i++) {
                        Term key = stack.pop();
                        Term value = stack.pop();
                        builder1.put(key, value);
                    }
                    for (int i = 0; i < constructor.size2(); i++) {
                        builder1.concatenate(stack.pop());
                    }
                    stack.push(builder1.build());
                    break;
                case BUILTIN_SET:
                    BuiltinSet.Builder builder2 = BuiltinSet.builder(context);
                    for (int i = 0; i < constructor.size1(); i++) {
                        builder2.add(stack.pop());
                    }
                    for (int i = 0; i < constructor.size2(); i++) {
                        builder2.concatenate(stack.pop());
                    }
                    stack.push(builder2.build());
                    break;
                case KITEM:
                    Term kLabel = stack.pop();
                    Term kList = stack.pop();
                    stack.push(KItem.of(kLabel, kList, context, constructor.getSource(), constructor.getLocation()));
                    break;
                case KITEM_PROJECTION:
                    stack.push(new KItemProjection(constructor.kind(), stack.pop()));
                    break;
                case KLABEL_FREEZER:
                    stack.push(new KLabelFreezer(stack.pop()));
                    break;
                case KLABEL_INJECTION:
                    stack.push(new KLabelInjection(stack.pop()));
                    break;
                case INJECTED_KLABEL:
                    stack.push(new InjectedKLabel(stack.pop()));
                    break;
                case KLIST:
                    KList.Builder builder3 = KList.builder();
                    for (int i = 0; i < constructor.size1(); i++) {
                        builder3.concatenate(stack.pop());
                    }
                    stack.push(builder3.build());
                    break;
                case KSEQUENCE:
                    KSequence.Builder builder4 = KSequence.builder();
                    for (int i = 0; i < constructor.size1(); i++) {
                        builder4.concatenate(stack.pop());
                    }
                    stack.push(builder4.build());
                    break;
                case CELL_COLLECTION:
                    CellCollection.Builder builder5 = CellCollection.builder(context.definition());
                    for (CellLabel cellLabel : constructor.cellLabels()) {
                        builder5.add(new Cell(cellLabel, stack.pop()));
                    }
                    for (int i = 0; i < constructor.size1(); i++) {
                        builder5.concatenate(stack.pop());
                    }
                    stack.push(builder5.build());
                    break;
                default:
                    throw new AssertionError("unreachable");
                }
                break;
            case SUBST:
                Variable var = (Variable) instruction.term();
                Term term = solution.get(var);
                if (term == null) {
                    term = var;
                } else if (reusableVariables != null && reusableVariables.contains(var)) {
                    reusableVariables.remove(var);
                } else if (reusableVariables != null && term.isMutable()) {
                    term = DeepCloner.clone(term);
                }
                stack.push(term);
                break;
            case EVAL:
                KItem kItem = (KItem) stack.pop();
                stack.push(kItem.resolveFunctionAndAnywhere(true, context));
                break;
            case PROJECT:
                KItemProjection projection = (KItemProjection) stack.pop();
                stack.push(projection.evaluateProjection());
                break;
            }
        }
        assert stack.size() == 1;
        return stack.pop();
    }

    private void match(CellCollection.Cell crntCell) {
        CellLabel cellLabel = crntCell.cellLabel();
        if (isReadCell(cellLabel)) {
            /* 1) perform matching under read cell;
             * 2) record the reference if it is also a write cell. */

            Profiler.startTimer(Profiler.PATTERN_MATCH_TIMER);
            /* there should be no AC-matching under the crntCell (violated rule
             * has been filtered out by the compiler) */
            Map<Variable, Term> subst = patternMatcher.patternMatch(
                    crntCell.content(),
                    getReadCellLHS(cellLabel));

            if (subst == null) {
                success = false;
            } else {
                Substitution<Variable, Term> composedSubst = fExtSubst.substitution().plusAll(subst);
                if (composedSubst == null) {
                    success = false;
                } else {
                    fExtSubst.setSubst(composedSubst);
                    if (isWriteCell(cellLabel)) {
                        fExtSubst.addWriteCell(crntCell);
                    }
                }
            }
            Profiler.stopTimer(Profiler.PATTERN_MATCH_TIMER);

            if (!success) {
                return;
            }
        }

        while (true) {
            nextInstr = nextInstruction();

            if (nextInstr == MatchingInstruction.UP) {
                return;
            }

            if (nextInstr == MatchingInstruction.CHOICE) {
                assert !isStarNested : "nested cells with multiplicity='*' not supported";
                isStarNested = true; // start of AC-matching

                ExtendedSubstitution oldExtSubst = fExtSubst;
                fExtSubst = new ExtendedSubstitution();
                List<ExtendedSubstitution> extSubsts = Lists.newArrayList();

                nextInstr = nextInstruction();
                int oldPC = pc; // pgm counter before AC-matching
                int newPC = -1; // pgm counter on success
                for (CellCollection.Cell cell : getSubCellsByLabel(crntCell.content(), nextInstr.cellLabel())) {
                    pc = oldPC;
                    match(cell);
                    if (success) {
                        newPC = pc;
                        extSubsts.add(fExtSubst);
                    }

                    /* clean up side-effects of the previous match attempt */
                    success = true;
                    fExtSubst = new ExtendedSubstitution();
                }

                isStarNested = false; // end of AC-matching

                if (extSubsts.isEmpty()) {
                    success = false;
                    return;
                } else {
                    pc = newPC;
                    fMultiExtSubsts.add(extSubsts);
                    fExtSubst = oldExtSubst;
                }
            } else {
                Iterator<CellCollection.Cell> iter = getSubCellsByLabel(crntCell.content(), nextInstr.cellLabel()).iterator();
                if (iter.hasNext()) {
                    match(iter.next());
                } else {
                    if (RuleAuditing.isAuditBegun()) {
                        System.err.println("Cell " + crntCell.cellLabel()
                        + " does not contain required cell " + nextInstr.cellLabel());
                    }
                    success = false;
                }

                if (!success) {
                    return;
                }
            }
        }
    }

    private MatchingInstruction nextInstruction() {
        return instructions.get(pc++);
    }

    private boolean isReadCell(CellLabel cellLabel) {
        return rule.lhsOfReadCell().keySet().contains(cellLabel);
    }

    private boolean isWriteCell(CellLabel cellLabel) {
        return rule.rhsOfWriteCell().keySet().contains(cellLabel);
    }

    private Term getReadCellLHS(CellLabel cellLabel) {
        return rule.lhsOfReadCell().get(cellLabel);
    }

    private Term getWriteCellRHS(CellLabel cellLabel) {
        return rule.rhsOfWriteCell().get(cellLabel);
    }

    private List<RHSInstruction> getWriteCellInstructions(CellLabel cellLabel) {
        return rule.instructionsOfWriteCell().get(cellLabel);
    }

    private static Collection<CellCollection.Cell> getSubCellsByLabel(Term content, CellLabel label) {
        if (content instanceof CellCollection) {
            return ((CellCollection) content).cells().get(label);
        } else {
            assert false : "expected contents of cell with label " + label + " to be a cell but found " + content.getClass().getSimpleName();
            return null;
        }
    }

    /**
     * Similar to {@link org.kframework.backend.java.symbolic.ConjunctiveFormula#getDisjunctiveNormalForm}
     * except that this method operates on {@code ExtendedSubstitution}.
     */
    private static List<ExtendedSubstitution> getCNFExtendedSubstitutions(
            ExtendedSubstitution fSubst,
            List<List<ExtendedSubstitution>> multiExtSubsts) {
        List<ExtendedSubstitution> result = Lists.newArrayList();

        if (!multiExtSubsts.isEmpty()) {
            assert multiExtSubsts.size() <= 2;

            if (multiExtSubsts.size() == 1) {
                for (ExtendedSubstitution extSubst : multiExtSubsts.get(0)) {
                    Substitution<Variable, Term> composedSubst =
                            fSubst.substitution().plusAll(extSubst.substitution());
                    if (composedSubst != null) {
                        result.add(new ExtendedSubstitution(
                                composedSubst,
                                ListUtils.union(fSubst.writeCells(), extSubst.writeCells())));
                    }
                }
            } else {
                for (ExtendedSubstitution subst1 : multiExtSubsts.get(0)) {
                    for (ExtendedSubstitution subst2 : multiExtSubsts.get(1)) {
                        Substitution<Variable, Term> composedSubst = fSubst.substitution()
                                .plusAll(subst1.substitution())
                                .plusAll(subst2.substitution());
                        if (composedSubst != null) {
                            result.add(new ExtendedSubstitution(
                                    composedSubst,
                                    ListUtils.union(
                                            fSubst.writeCells(),
                                            ListUtils.union(
                                                    subst1.writeCells(),
                                                    subst2.writeCells()))));
                        }
                    }
                }
            }
        } else {
            result.add(new ExtendedSubstitution(
                    fSubst.substitution(),
                    Lists.newArrayList(fSubst.writeCells())));
        }

        return result;
    }

}
