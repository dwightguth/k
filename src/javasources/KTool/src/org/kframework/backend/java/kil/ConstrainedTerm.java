// Copyright (c) 2013-2014 K Team. All Rights Reserved.
package org.kframework.backend.java.kil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.kframework.backend.java.symbolic.SymbolicConstraint;
import org.kframework.backend.java.symbolic.SymbolicConstraint.Equality;
import org.kframework.backend.java.symbolic.SymbolicConstraint.TruthValue;
import org.kframework.backend.java.symbolic.Transformer;
import org.kframework.backend.java.symbolic.UninterpretedConstraint;
import org.kframework.backend.java.symbolic.Visitor;
import org.kframework.backend.java.util.Debug;
import org.kframework.backend.java.util.GroupProductionsBySort;
import org.kframework.backend.java.util.Subsorts;
import org.kframework.backend.java.util.Utils;
import org.kframework.kil.ASTNode;
import org.kframework.krun.K;


/**
 * A K term associated with symbolic constraints.
 *
 * @author AndreiS
 */
public class ConstrainedTerm extends JavaSymbolicObject {

    public static class Data {
        public final Term term;
        /**
         * Represents key lookups of builtin data-structures as a symbolic
         * constraint.
         */
        public final SymbolicConstraint.Data lookupsData;
        public final SymbolicConstraint.Data constraintData;
        public Data(Term term, SymbolicConstraint.Data lookups,
                SymbolicConstraint.Data constraint) {
            super();
            this.term = term;
            this.lookupsData = lookups;
            this.constraintData = constraint;
        }
        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((constraintData == null) ? 0 : constraintData.hashCode());
            result = prime * result + ((lookupsData == null) ? 0 : lookupsData.hashCode());
            result = prime * result + ((term == null) ? 0 : term.hashCode());
            return result;
        }
        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            Data other = (Data) obj;
            if (constraintData == null) {
                if (other.constraintData != null)
                    return false;
            } else if (!constraintData.equals(other.constraintData))
                return false;
            if (lookupsData == null) {
                if (other.lookupsData != null)
                    return false;
            } else if (!lookupsData.equals(other.lookupsData))
                return false;
            if (term == null) {
                if (other.term != null)
                    return false;
            } else if (!term.equals(other.term))
                return false;
            return true;
        }


    }

    private static final Map<Definition, GroupProductionsBySort> cachedGroupProductionsBySort =
            new HashMap<Definition, GroupProductionsBySort>();

    private Data data;

    private final TermContext context;

    private final SymbolicConstraint lookups;

    private final SymbolicConstraint constraint;

    public ConstrainedTerm(Data data, TermContext context) {
        this.data = data;
        this.context = context;
        this.lookups = new SymbolicConstraint(data.lookupsData, context);
        this.constraint = new SymbolicConstraint(data.constraintData, context);
    }

    public ConstrainedTerm(Term term, SymbolicConstraint lookups, SymbolicConstraint constraint,
            TermContext context) {
        this(new Data(term, lookups.data, constraint.data), context);
    }

    public ConstrainedTerm(Term term, SymbolicConstraint constraint, TermContext context) {
        this(term, new SymbolicConstraint(context), constraint, context);
    }

    public ConstrainedTerm(Term term, TermContext context) {
        this(term, new SymbolicConstraint(context), new SymbolicConstraint(context), context);
    }

    public TermContext termContext() {
        return context;
    }

    public SymbolicConstraint constraint() {
        return constraint;
    }

    public boolean implies(ConstrainedTerm constrainedTerm) {
        return matchImplies(constrainedTerm) != null;
    }

    public SymbolicConstraint lookups() {
        return lookups;
    }
    /*
    public SymbolicConstraint match(ConstrainedTerm constrainedTerm, Definition definition) {
        SymbolicConstraint unificationConstraint = new SymbolicConstraint(definition);
        unificationConstraint.add(term, constrainedTerm.term);
        unificationConstraint.simplify();
        if (unificationConstraint.isFalse() || !unificationConstraint.isSubstitution()) {
            return null;
        }

        unificationConstraint.addAll(constrainedTerm.lookups);
        unificationConstraint.simplify();
        if (unificationConstraint.isFalse() || !unificationConstraint.isSubstitution()) {
            return null;
        }


    }
    */

    public SymbolicConstraint matchImplies(ConstrainedTerm constrainedTerm) {
        SymbolicConstraint unificationConstraint = new SymbolicConstraint(constrainedTerm.termContext());
        unificationConstraint.add(data.term, constrainedTerm.data.term);
        unificationConstraint.simplify();
        Set<Variable> variables = constrainedTerm.variableSet();
        variables.removeAll(variableSet());
        unificationConstraint.orientSubstitution(variables);
        if (unificationConstraint.isFalse() || !unificationConstraint.isSubstitution()) {
            return null;
        }

        SymbolicConstraint implicationConstraint = new SymbolicConstraint(constrainedTerm.termContext());
        implicationConstraint.addAll(unificationConstraint);
        implicationConstraint.addAll(constrainedTerm.lookups);
        implicationConstraint.addAll(constrainedTerm.constraint);
        implicationConstraint.simplify();
        implicationConstraint.orientSubstitution(variables);
        implicationConstraint = implicationConstraint.substituteWithBinders(implicationConstraint.substitution(), context);

        unificationConstraint.addAll(constraint);
        unificationConstraint.simplify();
        if (!unificationConstraint.implies(implicationConstraint)) {
            return null;
        }

        unificationConstraint.addAll(implicationConstraint);

        return unificationConstraint;
    }

    ///**
    // * Simplify map lookups.
    // */
    //public ConstrainedTerm simplifyLookups() {
    //    for (SymbolicConstraint.Equality equality : constraint.equalities())
    //}

    public Term term() {
        return data.term;
    }

    /**
     * Unifies this constrained term with another constrained term.
     *
     * @param constrainedTerm
     *            another constrained term
     * @return solutions to the unification problem
     */
    public List<SymbolicConstraint> unify(ConstrainedTerm constrainedTerm) {
        int numOfInvoc = Debug.incDebugMethodCounter();
        if (numOfInvoc == Integer.MAX_VALUE) {
            Debug.setBreakPointHere();
        }

        List<SymbolicConstraint> solutions = unifyImpl(constrainedTerm);

        Debug.printUnifyResult(numOfInvoc, this, constrainedTerm, solutions);
        return solutions;
    }

    /**
     * The actual implementation of the unify() method.
     *
     * @param constrainedTerm
     *            another constrained term
     * @return solutions to the unification problem
     */
    private List<SymbolicConstraint> unifyImpl(ConstrainedTerm constrainedTerm) {
        if (!data.term.kind.equals(constrainedTerm.data.term.kind)) {
            return Collections.emptyList();
        }

        /* unify the subject term and the pattern term without considering those associated constraints */
        SymbolicConstraint unificationConstraint = new SymbolicConstraint(constrainedTerm.termContext());
        unificationConstraint.add(data.term, constrainedTerm.data.term);
        unificationConstraint.simplify();
        if (unificationConstraint.isFalse()) {
            return Collections.emptyList();
        }

        List<SymbolicConstraint> solutions = new ArrayList<SymbolicConstraint>();
        for (SymbolicConstraint candidate : unificationConstraint.getMultiConstraints()) {
            if (SymbolicConstraint.TruthValue.FALSE == candidate.addAll(constrainedTerm.lookups)) continue;
            if (SymbolicConstraint.TruthValue.FALSE == candidate.addAll(constrainedTerm.constraint)) continue;
            if (SymbolicConstraint.TruthValue.FALSE == candidate.addAll(constraint)) continue;

            candidate.simplify();
            if (candidate.isFalse()) {
                continue;
            }

            if (K.tool() != K.Tool.KOMPILE) {
                /*
                 * YilongL: had to disable checkUnsat in kompilation because the
                 * KILtoZ3 transformer often crash the Java backend; besides,
                 * this method may not be necessary for kompilation
                 */
                if (candidate.checkUnsat()) {
                    continue;
                }
            }

            solutions.add(candidate);
        }

        if (context.definition().context().javaExecutionOptions().generateTests
                && !solutions.isEmpty()) {
            // TODO(AndreiS): deal with KLabel variables
            boolean changed;
            List<SymbolicConstraint> tmpSolutions = solutions;
            Set<Variable> sortIntersectionVariables = new HashSet<Variable>();
            Map<SymbolicConstraint, Set<Variable>> orientedVarsOfCnstr = new HashMap<SymbolicConstraint, Set<Variable>>();

            do {
                changed = false;
                solutions = tmpSolutions;
                tmpSolutions = new ArrayList<SymbolicConstraint>();
//                System.out.printf("sols=%s\n", solutions);

            iteratingSymbCnstr:
                for (SymbolicConstraint cnstr : solutions) {
                    Set<Variable> orientedVars = orientedVarsOfCnstr.get(cnstr);
                    orientedVarsOfCnstr.remove(cnstr);
                    if (orientedVars == null) orientedVars = new HashSet<Variable>();
//                    System.out.printf("cnstr=%s\n", cnstr);

                    for (Equality eq1 : cnstr.equalities()) {
                        // dissolve negative membership predicates
                        Term lhsOfEq = eq1.leftHandSide();
                        if (lhsOfEq instanceof KItem && ((KItem) lhsOfEq).kLabel().toString().equals("'_=/=K_")) {
                            Term mbPredicate = ((KList) ((KItem) lhsOfEq).kList()).get(0);
                            if (!(mbPredicate instanceof KItem)) continue;
                            if (!((KLabelConstant) ((KItem) mbPredicate).kLabel()).isSortPredicate())
                                continue;

                            // retrieve the predicate sort
                            Sort predSort = ((KLabelConstant) ((KItem) mbPredicate).kLabel()).getPredicateSort();

                            // retrieve the argument; which must be a variable
                            Variable arg = (Variable) ((KList) ((KItem) mbPredicate).kList()).get(0);

                            // construct common part of the new constraints
                            UninterpretedConstraint templCnstr = new UninterpretedConstraint();
                            Collection<UninterpretedConstraint> uninterpretedCnstrs = new ArrayList<UninterpretedConstraint>();
                            for (Equality eq2 : cnstr.equalities())
                                if (eq2 != eq1)
                                    templCnstr.add(eq2.leftHandSide(), eq2.rightHandSide());
                            for (Map.Entry<Variable, Term> entry : cnstr.substitution().entrySet()) {
                                templCnstr.add(entry.getKey(), entry.getValue());
                            }

                            // compute difference of two sorts, e.g., AExp \ KResult
                            for (Term term : computeSortDifference(arg.sort(), predSort)) {
                                UninterpretedConstraint uninterpretedCnstr = templCnstr.deepCopy();
                                uninterpretedCnstr.add(arg, term);
                                uninterpretedCnstrs.add(uninterpretedCnstr);
                            }

                            // get the interpreted version of the constraint
                            for (UninterpretedConstraint uninterpretedCnstr : uninterpretedCnstrs) {
                                SymbolicConstraint newCnstr = uninterpretedCnstr.getSymbolicConstraint(context);
                                if (newCnstr.simplify() != TruthValue.FALSE) {
                                    tmpSolutions.add(newCnstr);
                                    orientedVarsOfCnstr.put(newCnstr, new HashSet<Variable>(orientedVars));
                                }
                            }
                            changed = true;
                            continue iteratingSymbCnstr;
                        }

                        // dissolve positive membership predicates
                        if (eq1.toString().startsWith("isKResult(")) {
                            KItem mbPredicate = (KItem) eq1.leftHandSide();
                            Sort predSort = ((KLabelConstant) mbPredicate.kLabel()).getPredicateSort();
                            Variable arg = (Variable) ((KList) mbPredicate.kList()).get(0);

                            // construct common part of the new constraints
                            UninterpretedConstraint templCnstr = new UninterpretedConstraint();
                            Collection<UninterpretedConstraint> uninterpretedCnstrs = new ArrayList<UninterpretedConstraint>();
                            for (Equality eq2 : cnstr.equalities())
                                if (eq2 != eq1)
                                    templCnstr.add(eq2.leftHandSide(), eq2.rightHandSide());
                            for (Map.Entry<Variable, Term> entry : cnstr.substitution().entrySet()) {
                                templCnstr.add(entry.getKey(), entry.getValue());
                            }

                            // compute intersection of two sorts, e.g., AExp /\ KResult
                            for (Variable var : computeSortIntersection(arg.sort(), predSort)) {
                                UninterpretedConstraint uninterpretedCnstr = templCnstr.deepCopy();
                                uninterpretedCnstr.add(arg, var);
                                uninterpretedCnstrs.add(uninterpretedCnstr);
                            }

                            // get the interpreted version of the constraint
                            for (UninterpretedConstraint uninterpretedCnstr : uninterpretedCnstrs) {
                                SymbolicConstraint newCnstr = uninterpretedCnstr.getSymbolicConstraint(context);
                                if (newCnstr.simplify() != TruthValue.FALSE) {
                                    tmpSolutions.add(newCnstr);
                                    orientedVarsOfCnstr.put(newCnstr, new HashSet<Variable>(orientedVars));
                                }
                            }
                            changed = true;
                            continue iteratingSymbCnstr;
                        }
                    }

                    cnstr.orientSubstitution(orientedVars);
                    for (Entry<Variable, Term> subst : cnstr.substitution().entrySet()) {
                        // handle equality involving two variables with different
                        // sorts, e.g. x1:sort1 =? x2:sort2
                        if (subst.getValue() instanceof Variable) {
                            Variable lhs = subst.getKey();
                            Variable rhs = (Variable) subst.getValue();

                            if (!lhs.sort().equals(rhs.sort())) {
                                if (sortIntersectionVariables.contains(lhs) || sortIntersectionVariables.contains(rhs))
                                    continue;

                                // construct common part of the new constraints
                                UninterpretedConstraint templCnstr = new UninterpretedConstraint();
                                Collection<UninterpretedConstraint> uninterpretedCnstrs = new ArrayList<UninterpretedConstraint>();
                                for (Equality eq : cnstr.equalities())
                                    templCnstr.add(eq.leftHandSide(), eq.rightHandSide());
                                for (Map.Entry<Variable, Term> entry : cnstr.substitution().entrySet()) {
                                    templCnstr.add(entry.getKey(), entry.getValue());
                                }

                                for (Variable var : computeSortIntersection(lhs.sort(), rhs.sort())) {
                                    sortIntersectionVariables.add(var);
                                    UninterpretedConstraint uninterpretedCnstr = templCnstr.deepCopy();
                                    uninterpretedCnstr.add(rhs, var);
                                    uninterpretedCnstrs.add(uninterpretedCnstr);
                                }

                                // get the interpreted version of the constraint
                                for (UninterpretedConstraint uninterpretedCnstr : uninterpretedCnstrs) {
                                    SymbolicConstraint newCnstr = uninterpretedCnstr.getSymbolicConstraint(context);
                                    if (newCnstr.simplify() != TruthValue.FALSE) {
                                        tmpSolutions.add(newCnstr);
                                        orientedVarsOfCnstr.put(newCnstr, new HashSet<Variable>(orientedVars));
                                        orientedVarsOfCnstr.get(newCnstr).add(lhs);
                                        orientedVarsOfCnstr.get(newCnstr).add(rhs);
                                    }
                                }
                                changed = true;
                                continue iteratingSymbCnstr;
                            }
                        }

                        // TODO: dissolve data-structure lookups
                        if (subst.getValue() instanceof MapLookup) {
                            MapLookup mapLookup = (MapLookup) subst.getValue();
                            BuiltinMap map = (BuiltinMap) mapLookup.map();
                            Variable key = (Variable) mapLookup.key();

                            UninterpretedConstraint templCnstr = new UninterpretedConstraint();
                            Collection<UninterpretedConstraint> uninterpretedCnstrs = new ArrayList<UninterpretedConstraint>();
                            for (Equality eq : cnstr.equalities())
                                templCnstr.add(eq.leftHandSide(), eq.rightHandSide());
                            for (Map.Entry<Variable, Term> entry : cnstr.substitution().entrySet())
                                templCnstr.add(entry.getKey(), entry.getValue());

                            for (Map.Entry<Term, Term> mapItem : map) {
                                UninterpretedConstraint uninterpretedCnstr = templCnstr.deepCopy();
                                uninterpretedCnstr.add(key, mapItem.getKey());
                                uninterpretedCnstrs.add(uninterpretedCnstr);
                            }

                            // get the interpreted version of the constraint
                            for (UninterpretedConstraint uninterpretedCnstr : uninterpretedCnstrs) {
                                SymbolicConstraint newCnstr = uninterpretedCnstr.getSymbolicConstraint(context);
                                if (newCnstr.simplify() != TruthValue.FALSE) {
                                    tmpSolutions.add(newCnstr);
                                    orientedVarsOfCnstr.put(newCnstr, new HashSet<Variable>(orientedVars));
                                }
                            }
                            changed = true;
                            continue iteratingSymbCnstr;
                        }
                    }

                    tmpSolutions.add(cnstr);
                }
            } while (changed);
        }

        return solutions;
    }

    private Set<Variable> computeSortIntersection(Sort sort1, Sort sort2) {
        // TODO(YilongL): call Context#getCommonSubsorts to simplify the code
        Set<Variable> results = new HashSet<>();
        Subsorts subsorts = context.definition().subsorts();

        Set<Sort> intersect = new HashSet<>();
        for (Sort sort : subsorts.allSorts()) {
            if (subsorts.isSubsortedEq(sort1, sort) && subsorts.isSubsortedEq(sort2, sort)) {
                intersect.add(sort);
            }
        }

        Set<Sort> sortsToRemove = new HashSet<>();
        for (Sort s1 : intersect)
            for (Sort s2 : intersect)
                if (subsorts.isSubsorted(s1, s2)) {
                    sortsToRemove.add(s2);
                }
        intersect.removeAll(sortsToRemove);

        for (Sort sort : intersect) {
            results.add(Variable.getFreshVariable(sort));
        }
        return results;
    }

    private Set<Term> computeSortDifference(Sort sort1, Sort sort2) {
        Set<Term> results = new HashSet<>();
        Definition definition = context.definition();
        Subsorts subsorts = definition.subsorts();

        Set<Sort> whiteSorts = new HashSet<>();
        for (Sort sort : definition.allSorts()) {
            if (subsorts.isSubsortedEq(sort1, sort)) {
                whiteSorts.add(sort);
            }
        }

        Set<Sort> blackSorts = new HashSet<>();
        for (Sort sort : whiteSorts) {
            if (subsorts.isSubsortedEq(sort1, sort) && subsorts.isSubsortedEq(sort2, sort)) {
                blackSorts.add(sort);
            }
        }
        whiteSorts.removeAll(blackSorts);

        Set<Sort> greySorts = new HashSet<>();
        for (Sort sort : whiteSorts) {
            if (subsorts.isSubsortedEq(sort1, sort)) {
                for (Sort blackSort : blackSorts) {
                    if (subsorts.isSubsorted(sort, blackSort)) {
                        greySorts.add(sort);
                    }
                }
            }
        }
        whiteSorts.removeAll(greySorts);

        Set<Sort> sortsToRemove = new HashSet<>();
        for (Sort s1 : whiteSorts)
            for (Sort s2 : whiteSorts)
                if (subsorts.isSubsorted(s1, s2)) {
                    sortsToRemove.add(s2);
                }
        whiteSorts.removeAll(sortsToRemove);

        for (Sort whiteSort : whiteSorts)
            results.add(Variable.getFreshVariable(whiteSort));
        for (Sort greySort : greySorts)
            results.addAll(getProductionsAsTerms(greySort));

        return results;
    }

    private List<KItem> getProductionsAsTerms(Sort sort) {
        Definition def = context.definition();
        GroupProductionsBySort gpbs = cachedGroupProductionsBySort.get(def);
        if (gpbs == null) {
            gpbs = new GroupProductionsBySort(def);
            cachedGroupProductionsBySort.put(def, gpbs);
        }

        return gpbs.getProductionsAsTerms(sort, context);
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }

        if (!(object instanceof ConstrainedTerm)) {
            return false;
        }

        ConstrainedTerm constrainedTerm = (ConstrainedTerm) object;
        return data.equals(constrainedTerm.data);
    }

    @Override
    public int hashCode() {
        // TODO(YilongL): I don't think ConstrainedTerm should derive Term
        hashCode = 1;
        hashCode = hashCode * Utils.HASH_PRIME + data.hashCode();
        return hashCode;
    }

    @Override
    public String toString() {
        return data.term + SymbolicConstraint.SEPARATOR + constraint + SymbolicConstraint.SEPARATOR + lookups;
    }

    @Override
    public ASTNode accept(Transformer transformer) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void accept(Visitor visitor) {
        visitor.visit(this);
    }
}
