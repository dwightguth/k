// Copyright (c) 2012-2014 K Team. All Rights Reserved.
package org.kframework.parser.concrete.disambiguate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.kframework.compile.utils.MetaK;
import org.kframework.kil.ASTNode;
import org.kframework.kil.Ambiguity;
import org.kframework.kil.Sentence;
import org.kframework.kil.Sort;
import org.kframework.kil.Sort.SortId;
import org.kframework.kil.Term;
import org.kframework.kil.Variable;
import org.kframework.kil.loader.Context;
import org.kframework.kil.visitors.ParseForestTransformer;
import org.kframework.kil.visitors.BasicVisitor;
import org.kframework.kil.visitors.exceptions.ParseFailedException;
import org.kframework.utils.errorsystem.KException;
import org.kframework.utils.errorsystem.KException.ExceptionType;
import org.kframework.utils.errorsystem.KException.KExceptionGroup;
import org.kframework.utils.general.GlobalSettings;

public class VariableTypeInferenceFilter extends ParseForestTransformer {

    public VariableTypeInferenceFilter(Context context) {
        super("Variable type inference", context);
    }

    @Override
    public ASTNode visit(Sentence r, Void _) throws ParseFailedException {
        r = (Sentence) new RemoveDuplicateVariables(context).visitNode(r);

        CollectVariablesVisitor vars = new CollectVariablesVisitor(context);
        vars.visitNode(r);

        Map<String, Variable> varDeclMap = new HashMap<String, Variable>();
        // for each variable name do checks or type errors
        for (Entry<String, java.util.List<Variable>> varEntry : vars.getVars().entrySet()) {
            java.util.List<Variable> varList = varEntry.getValue();

            // check to see if you have variable declarations with two different sorts
            if (varList.size() > 1) {
                for (Variable v1 : varList) {
                    for (Variable v2 : varList)
                        if (v1 != v2)
                            if (!v1.getSort().equals(v2.getSort())) {
                                String msg = "Variable '" + v1.getName() + "' declared with two different sorts: " + v1.getSort() + " and " + v2.getSort();
                                throw new ParseFailedException(new KException(ExceptionType.ERROR, KExceptionGroup.CRITICAL, msg, getName(), v1.getFilename(), v1.getLocation()));
                            }
                    // if there are more than one declaration then prefer the one that is semantically typed
                    if (!v1.isSyntactic()) {
                        varDeclMap.put(v1.getName(), v1);
                    }
                }
            }
            // if no semantic casts were found, then just choose the first syntactic restriction
            Variable var = varList.iterator().next();
            if (!varDeclMap.containsKey(var.getName()))
                varDeclMap.put(var.getName(), var);
        }
        // after finding all of the variable declarations traverse the tree to disambiguate
        r = (Sentence) new VariableTypeFilter(varDeclMap, false, context).visitNode(r);
        r = (Sentence) new TypeSystemFilter(context).visitNode(r);
        r = (Sentence) new TypeInferenceSupremumFilter(context).visitNode(r);

        boolean varTypeInference = true;
        if (varTypeInference) {
            CollectExpectedVariablesVisitor vars2 = new CollectExpectedVariablesVisitor(context);
            vars2.visitNode(r);

            Set<VarHashMap> solutions = new HashSet<VarHashMap>();
            String fails = null;
            Set<String> failsAmb = null;
            String failsAmbName = null;
            for (VarHashMap variant : vars2.vars) {
                // take each solution and do GLB on every variable
                VarHashMap solution = new VarHashMap();
                for (Map.Entry<String, Set<String>> entry : variant.entrySet()) {
                    Set<String> mins = new HashSet<String>();
                    for (SortId sort : context.definedSorts) { // for every declared sort
                        boolean min = true;
                        for (String var : entry.getValue()) {
                            if (!context.isSubsortedEq(Sort.of(var), Sort.of(sort))) {
                                min = false;
                                break;
                            }
                        }
                        if (min)
                            mins.add(sort.getName());
                    }
                    if (mins.size() == 0) {
                        fails = entry.getKey();
                        solution.clear();
                        break;
                    } else if (mins.size() > 1) {
                        java.util.Set<String> maxSorts = new HashSet<String>();

                        for (String vv1 : mins) {
                            boolean maxSort = true;
                            for (String vv2 : mins)
                                if (context.isSubsorted(Sort.of(vv2), Sort.of(vv1)))
                                    maxSort = false;
                            if (maxSort)
                                maxSorts.add(vv1);
                        }

                        if (maxSorts.size() == 1)
                            solution.put(entry.getKey(), maxSorts);
                        else {
                            failsAmb = maxSorts;
                            failsAmbName = entry.getKey();
                            solution.clear();
                            break;
                        }
                    } else {
                        solution.put(entry.getKey(), mins);
                    }
                }
                // I found a solution that fits everywhere, then store it for disambiguation
                if (!solution.isEmpty())
                    solutions.add(solution);
            }
            if (!vars2.vars.isEmpty()) {
                if (solutions.size() == 0) {
                    if (fails != null) {
                        String msg = "Could not infer a sort for variable '" + fails + "' to match every location.";
                        throw new ParseFailedException(new KException(ExceptionType.ERROR, KExceptionGroup.CRITICAL, msg, r.getFilename(), r.getLocation()));
                    } else {
                        // Failure when in the same solution I can't find a unique sort for a specific variable.
                        String msg = "Could not infer a unique sort for variable '" + failsAmbName + "'.";
                        msg += " Possible sorts: ";
                        for (String vv1 : failsAmb)
                            msg += vv1 + ", ";
                        msg = msg.substring(0, msg.length() - 2);
                        throw new ParseFailedException(new KException(ExceptionType.ERROR, KExceptionGroup.CRITICAL, msg, r.getFilename(), r.getLocation()));

                    }
                } else if (solutions.size() == 1) {
                    for (Map.Entry<String, Set<String>> entry : solutions.iterator().next().entrySet()) {
                        Variable var = new Variable(entry.getKey(), null);
                        var.setUserTyped(false);
                        var.setExpectedSort(Sort.of(entry.getValue().iterator().next()));
                        var.setSyntactic(false);
                        varDeclMap.put(entry.getKey(), var);
                    }
                    try {
                        r = (Sentence) new VariableTypeFilter(varDeclMap, true, context).visitNode(r);
                    } catch (ParseFailedException e) {
                        e.report();
                    }
                    // correct the sorts for each variable after type inference
                    CollectRemainingVarsVisitor vars3 = new CollectRemainingVarsVisitor(context);
                    vars3.visitNode(r);

                    varDeclMap.clear();
                    // for each variable name do checks or type inference
                    for (Entry<String, java.util.List<Variable>> varEntry : vars3.vars.entrySet()) {
                        java.util.List<Variable> varList = varEntry.getValue();
                        // It means that this variable has already been defined somewhere
                        // no need to do type inference for it
                        if (vars3.typedVars.contains(varEntry.getKey()))
                            continue;

                        // divide into locations
                        Map<String, java.util.Set<Variable>> varLoc = new HashMap<String, java.util.Set<Variable>>();
                        for (Variable var : varList) {
                            if (varLoc.containsKey(var.getLocation()))
                                varLoc.get(var.getLocation()).add(var);
                            else {
                                java.util.Set<Variable> varss = new HashSet<Variable>();
                                varss.add(var);
                                varLoc.put(var.getLocation(), varss);
                            }
                        }

                        // choose maximum on each location
                        for (Map.Entry<String, Set<Variable>> ent : varLoc.entrySet()) {
                            Variable vmax = ent.getValue().iterator().next();
                            for (Variable vv1 : ent.getValue()) {
                                if (context.isSubsorted(vv1.getSort(), vmax.getSort()))
                                    vmax = vv1;
                            }
                            ent.getValue().clear();
                            ent.getValue().add(vmax);
                        }

                        // choose minimum on all locations
                        Variable vmin = varLoc.entrySet().iterator().next().getValue().iterator().next();
                        for (Map.Entry<String, Set<Variable>> ent : varLoc.entrySet()) {
                            Variable vloc = ent.getValue().iterator().next();
                            if (context.isSubsorted(vmin.getSort(), vloc.getSort()))
                                vmin = vloc;
                        }

                        // store the solution for later disambiguation
                        varDeclMap.put(vmin.getName(), vmin);
                        String msg = "Variable '" + vmin.getName() + "' was not declared. Assuming sort " + vmin.getSort() + " and expected sort " + vmin.getExpectedSort() + ".";
                        GlobalSettings.kem.register(new KException(ExceptionType.HIDDENWARNING, KExceptionGroup.COMPILER, msg, vmin.getFilename(), vmin.getLocation()));
                    }
                    // after type inference for concrete sorts, reject erroneous branches
                    if (!varDeclMap.isEmpty()) {
                        try {
                            r = (Sentence) new VariableTypeFilter(varDeclMap, false, context).visitNode(r);
                        } catch (ParseFailedException e) {
                            e.report();
                        }
                    }
                } else {
                    Map<String, Set<String>> collect = new HashMap<String, Set<String>>();
                    for (VarHashMap sol : solutions) {
                        for (Map.Entry<String, Set<String>> s : sol.entrySet())
                            if (collect.containsKey(s.getKey())) {
                                collect.get(s.getKey()).addAll(s.getValue());
                            } else {
                                collect.put(s.getKey(), new HashSet<String>(s.getValue()));
                            }
                    }
                    for (Map.Entry<String, Set<String>> s : collect.entrySet()) {
                        if (s.getValue().size() > 1) {
                            String msg = "Could not infer a unique sort for variable '" + s.getKey() + "'.";
                            msg += " Possible sorts: ";
                            for (String vv1 : s.getValue())
                                msg += vv1 + ", ";
                            msg = msg.substring(0, msg.length() - 2);
                            throw new ParseFailedException(new KException(ExceptionType.ERROR, KExceptionGroup.CRITICAL, msg, r.getFilename(), r.getLocation()));
                        }
                    }
                    // The above loop looks for variables that can have multiple sorts, collected from multiple solutions.
                    // In rare cases (couldn't think of one right now) it may be that the
                    // solution may be different because of different variable names

                    // Ok, I found one example now. In C with unified-builtins, the follow restriction for ==Set doesn't work
                    // and it creates multiple parses with different amounts of variables
                    // This makes it that I can't disambiguate properly
                    // I can't think of a quick fix... actually any fix. I will delay it for the new parser.
                    String msg = "Unsolvable ambiguities. Please write the rule in labeled form.\n (Generally because of __ productions mixing with variables).";
                    throw new ParseFailedException(new KException(ExceptionType.ERROR, KExceptionGroup.CRITICAL, msg, r.getFilename(), r.getLocation()));
                    //assert false : "An error message should have been thrown here in the above loop.";
                }
            }
        }

        // type inference and error reporting
        // -- Error: type mismatch for variable... (when the declared variable doesn't fit everywhere)
        // -- Error: could not infer a sort for variable... (when there is no solution left)
        // -- Error: could not infer a unique sort for variable... (when there is more than one solution)
        // -- Warning: untyped variable, assuming sort...

        return r;
    }

    /**
     * Removes ambiguities of the type amb(M:Map, M:MapItem)
     * Chose the maximum
     * @author Radu
     *
     */
    public class RemoveDuplicateVariables extends ParseForestTransformer {
        public RemoveDuplicateVariables(Context context) {
            super(RemoveDuplicateVariables.class.toString(), context);
        }

        @Override
        public ASTNode visit(Ambiguity amb, Void _) throws ParseFailedException {
            Set<Term> maxterms = new HashSet<Term>();
            for (Term t : amb.getContents()) {
                if (t instanceof Variable) {
                    // for variables only, find maximum
                    boolean max = true;
                    for (Term t1 : amb.getContents()) {
                        if (t1 != t && t1 instanceof Variable && context.isSubsorted(t1.getSort(), t.getSort())) {
                            max = false;
                            break;
                        }
                    }
                    if (max)
                        maxterms.add(t);
                } else
                    maxterms.add(t);
            }

            if (maxterms.size() == 1) {
                return this.visitNode(maxterms.iterator().next());
            } else if (maxterms.size() > 1)
                amb.setContents(new ArrayList<Term>(maxterms));

            return super.visit(amb, _);
        }
    }

    public class CollectRemainingVarsVisitor extends BasicVisitor {
        public CollectRemainingVarsVisitor(Context context) {
            super(context);
        }

        public java.util.Map<String, java.util.List<Variable>> vars = new HashMap<String, java.util.List<Variable>>();
        public Set<String> typedVars = new HashSet<>();

        @Override
        public Void visit(Variable var, Void _) {
            if (!var.getName().equals(MetaK.Constants.anyVarSymbol)) {
                if (!var.isUserTyped()) {
                    if (vars.containsKey(var.getName()))
                        vars.get(var.getName()).add(var);
                    else {
                        java.util.List<Variable> varss = new ArrayList<Variable>();
                        varss.add(var);
                        vars.put(var.getName(), varss);
                    }
                } else
                    typedVars.add(var.getName());
            }
            return null;
        }
    }
}
