// Copyright (c) 2012-2014 K Team. All Rights Reserved.
package org.kframework.compile.checks;

import org.kframework.compile.utils.MetaK;
import org.kframework.kil.*;
import org.kframework.kil.loader.Context;
import org.kframework.kil.visitors.BasicVisitor;
import org.kframework.kompile.KompileOptions;
import org.kframework.utils.errorsystem.KException;
import org.kframework.utils.general.GlobalSettings;

import java.util.HashMap;
import java.util.Map;

/*
 * Initially created by: Traian Florin Serbanuta
 * <p/>
 * Date: 11/21/12
 * Time: 3:13 PM
 */

/**
 * Checks Variable consistency.
 *
 * Generic variables:
 * 1. Variables must be bound in the pattern
 * 2. Variables unused in the rhs should be anonymous.
 *
 * Fresh variables:
 * 1. fresh can only appear as a side condition
 * 2. fresh can only be applied to a variable
 * 3. the fresh variable can only appear as a replacement variable
 *
 * Matching logic (option -ml): named variables may appear in the rhs
 */
public class CheckVariables extends BasicVisitor {

    public static final String UNBOUND_VARS = "hasUnboundVars";

    KompileOptions options;

    public CheckVariables(Context context) {
        super(context);
        options = context.kompileOptions();
    }

    HashMap<Variable, Integer> left = new HashMap<Variable, Integer>();
    HashMap<Variable, Integer> right = new HashMap<Variable, Integer>();
    HashMap<Variable, Integer> fresh = new HashMap<Variable, Integer>();
    HashMap<Variable, Integer> current = left;
    boolean inCondition = false;

    @Override
    public Void visit(Rewrite node, Void _) {
        this.visitNode(node.getLeft());
        current = right;
        this.visitNode(node.getRight());
        current = left;
        return null;
    }

    @Override
    public Void visit(Variable node, Void _) {
        boolean freshConstant = node.isFreshConstant();
        if (node.isFreshVariable() || freshConstant) {
            if (freshConstant && !context.freshFunctionNames().containsKey(node.getSort())) {
                GlobalSettings.kem.register(new KException(
                        KException.ExceptionType.ERROR,
                        KException.KExceptionGroup.COMPILER,
                        "Unsupported sort of fresh variable: " + node.getSort()
                                + "\nOnly sorts "
                                + context.freshFunctionNames().keySet()
                                + " admit fresh variables.", getName(), node
                                .getFilename(), node.getLocation()));
            }

            if (current == right  && !inCondition) {
                 Integer i = fresh.get(node);
                 if (i == null) i = new Integer(1);
                 else i = new Integer(i.intValue());
                 fresh.put(node, i);
                 return null;
             }
             //nodes are ok to be found in rhs
             GlobalSettings.kem.register(new KException(KException.ExceptionType.ERROR,
                    KException.KExceptionGroup.COMPILER,
                    "Fresh variable \"" + node + "\" is bound in the " + "rule pattern.",
                    getName(), node.getFilename(), node.getLocation()
            ));
        }
//        System.out.println("Variable: " + node);
        Integer i = current.remove(node);
        if (i == null) {
            i = new Integer(1);
        } else {
            i = new Integer(i.intValue() + 1);
        }
        current.put(node, i);
        return null;
    }

    @Override
    public Void visit(Configuration node, Void _) {
        return null;
    }

    @Override
    public Void visit(Syntax node, Void _) {
        return null;
    }

    @Override
    public Void visit(Sentence node, Void _) {
        inCondition = false;
        left.clear();
        right.clear();
        fresh.clear();
        current = left;
        this.visitNode(node.getBody());
        if (node.getRequires() != null) {
            current = right;
            inCondition = true;
            this.visitNode(node.getRequires());
        }
        //TODO: add checks for Ensures, too.
        for (Variable v : right.keySet()) {
            if (MetaK.isAnonVar(v) && !(v.isFreshVariable() || v.isFreshConstant())) {
                GlobalSettings.kem.register(new KException(KException
                        .ExceptionType.ERROR,
                        KException.KExceptionGroup.COMPILER,
                        "Anonymous variable found in the right hand side of a rewrite.",
                        getName(), v.getFilename(), v.getLocation()));
            }
            if (!left.containsKey(v)) {
                node.addAttribute(UNBOUND_VARS, "");
                GlobalSettings.kem.register(new KException(KException.ExceptionType.ERROR,
                        KException.KExceptionGroup.COMPILER,
                        "Unbounded variable " + v.toString() + "should start with ? or !.",
                        getName(), v.getFilename(), v.getLocation()));
            }
        }
        for (Map.Entry<Variable,Integer> e : left.entrySet()) {
            final Variable key = e.getKey();
            if (fresh.containsKey(key)) {
                GlobalSettings.kem.register(new KException(KException
                        .ExceptionType.ERROR,
                        KException.KExceptionGroup.COMPILER,
                        "Variable " + key + " has the same name as a fresh variable.",
                        getName(), key.getFilename(), key.getLocation()));
            }
            if (MetaK.isAnonVar(key)) continue;
            if (e.getValue().intValue() > 1) continue;
            if (!right.containsKey(key)) {
                GlobalSettings.kem.register(new KException(KException.ExceptionType.HIDDENWARNING,
                        KException.KExceptionGroup.COMPILER,
                        "Singleton variable " + key.toString() + ".\n" +
                        "    If this is not a spelling mistake, please consider using anonymous variables.",
                        getName(), key.getFilename(), key.getLocation()));
            }
        }
        return null;
    }
}
