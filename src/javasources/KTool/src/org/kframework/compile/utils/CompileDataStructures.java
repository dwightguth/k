// Copyright (c) 2013-2014 K Team. All Rights Reserved.
package org.kframework.compile.utils;

import org.kframework.kil.ASTNode;
import org.kframework.kil.DataStructureBuiltin;
import org.kframework.kil.DataStructureSort;
import org.kframework.kil.KApp;
import org.kframework.kil.KLabelConstant;
import org.kframework.kil.KList;
import org.kframework.kil.MapUpdate;
import org.kframework.kil.Production;
import org.kframework.kil.Rewrite;
import org.kframework.kil.Rule;
import org.kframework.kil.Sort;
import org.kframework.kil.Term;
import org.kframework.kil.Variable;
import org.kframework.kil.loader.Context;
import org.kframework.kil.visitors.CopyOnWriteTransformer;
import org.kframework.utils.errorsystem.KException;
import org.kframework.utils.general.GlobalSettings;

import java.util.Collections;
import java.util.List;

/**
 * Transformer class compiling collection (bag, list, map and set) terms into K internal
 * representation. It traverses the AST bottom-up and upon encountering a KLabel hooked to a
 * primitive data structure operation (constructor, element, unit) it creates a {@link
 * DataStructureBuiltin} instance representing the respective data structure. In particular the
 * constructor operation requires both argument to be already compiled into data structures of
 * the same sort.
 *
 * @see DataStructureSort
 * @see DataStructureBuiltin
 *
 * @author AndreiS
 */
public class CompileDataStructures extends CopyOnWriteTransformer {

    private final boolean noRHS;

    private enum Status { LHS, RHS, CONDITION }

    private Status status;
    private String location;
    private String filename;

    /**
     * @param context The context of the rules being compiled
     * @param noRHS  Whether this is supposed to run on rules with no right-hand-side
     *               arising when compiling search patterns for KRun.
     */
    public CompileDataStructures(Context context, boolean noRHS) {
        super("Compile collections to internal K representation", context);
        this.noRHS = noRHS;
    }

    /**
     * Simplified constructor for the common case
     * @param context The context of the rules being compiled
     */
    public CompileDataStructures(Context context) {
         this(context, false);
    }

    @Override
    public ASTNode visit(Rule node, Void _)  {

        location = node.getLocation();
        filename = node.getFilename();
        boolean change = false;

        Term body = node.getBody();
        if (! noRHS) { // Regular rule
            assert body instanceof Rewrite :
                    "expected rewrite at the top of rule\n" + node + "\n"
                            + "CompileDataStructures pass should be applied after ResolveRewrite pass";

            Rewrite rewrite = (Rewrite) body;
            status = Status.LHS;
            Term lhs = (Term) this.visitNode(rewrite.getLeft());
            status = Status.RHS;
            Term rhs = (Term) this.visitNode(rewrite.getRight());
            if (lhs != rewrite.getLeft() || rhs != rewrite.getRight()) {
                change = true;
                rewrite = rewrite.shallowCopy();
                rewrite.setLeft(lhs, context);
                rewrite.setRight(rhs, context);
                body = rewrite;
            }
        } else { // Krun "rule"
            status = Status.LHS;
            body = (Term) this.visitNode(body);
            if (body != node.getBody()) {
                change = true;
            }
        }
        Term requires;
        if (node.getRequires() != null) {
            status = Status.CONDITION;
            requires = (Term) this.visitNode(node.getRequires());
            if (requires != node.getRequires()) {
                change = true;
            }
        } else {
            requires = null;
        }

        //TODO: handle ensures, too.

        if (!change) {
            return node;
        }

        node = node.shallowCopy();
        node.setBody(body);
        node.setRequires(requires);
        return node;
    }

    @Override
    public ASTNode visit(Rewrite node, Void _)  {
        assert false: "CompileDataStructures pass should be applied after ResolveRewrite pass";
        return node;
    }

    @Override
    public ASTNode visit(KApp node, Void _)  {
        node = (KApp) super.visit(node, _);
        if (!(node.getLabel() instanceof KLabelConstant)) {
            /* only consider KLabel constants */
            return node;
        }
        KLabelConstant kLabelConstant = (KLabelConstant) node.getLabel();

        if (!(node.getChild() instanceof KList)) {
            /* only consider KList constants */
            return node;
        }
        KList kList = (KList) node.getChild();

        List<Production> productions = context.productionsOf(kLabelConstant.getLabel());
        if (productions.isEmpty()) {
            return node;
        }
        Production production = productions.iterator().next();

        DataStructureSort sort = context.dataStructureSortOf(production.getSort());
        if (sort == null) {
            return node;
        }

        Term[] arguments = new Term[kList.getContents().size()];
        for (int i = 0; i < kList.getContents().size(); ++i) {
            arguments[i] = (Term) this.visitNode(kList.getContents().get(i));
        }

        if (sort.constructorLabel().equals(kLabelConstant.getLabel())
                || sort.elementLabel().equals(kLabelConstant.getLabel())
                || sort.unitLabel().equals(kLabelConstant.getLabel())
                || sort.sort().equals(Sort.MAP)) {
            // TODO(AndreiS): the lines below should work once KLabelConstant are properly created
            if (productions.size() > 1) {
                GlobalSettings.kem.register(new KException(
                        KException.ExceptionType.WARNING,
                        KException.KExceptionGroup.COMPILER,
                        "unable to transform the KApp: " + node
                        + "\nbecause of multiple productions associated:\n"
                        + productions,
                        getName(),
                        filename,
                        location));
            }
        }

        if (sort.constructorLabel().equals(kLabelConstant.getLabel())) {
            DataStructureBuiltin dataStructure = DataStructureBuiltin.of(sort, arguments);
            if (status == Status.LHS && !dataStructure.isLHSView()) {
                GlobalSettings.kem.register(new KException(
                        KException.ExceptionType.ERROR,
                        KException.KExceptionGroup.CRITICAL,
                        "unexpected left-hand side data structure format; "
                        + "expected elements and at most one variable\n"
                        + node,
                        getName(),
                        filename,
                        location));
                return null;
            }
            return dataStructure;
        } else if (sort.elementLabel().equals(kLabelConstant.getLabel())) {
            /* TODO(AndreiS): check sort restrictions */
            return DataStructureBuiltin.element(sort, arguments);
        } else if (sort.unitLabel().equals(kLabelConstant.getLabel())) {
            if (kList.isEmpty()) {
                return DataStructureBuiltin.empty(sort);
            } else {
                GlobalSettings.kem.register(new KException(
                        KException.ExceptionType.ERROR,
                        KException.KExceptionGroup.CRITICAL,
                        "unexpected non-empty KList applied to constant KLabel " + kLabelConstant,
                        getName(),
                        filename,
                        location));
                return node;
            }
        } else if (sort.sort().equals(Sort.MAP)) {
            /* TODO(AndreiS): replace this with a more generic mechanism */
            if (kLabelConstant.getLabel().equals(sort.operatorLabels().get("update"))
                    && kList.getContents().size() >= 3 && kList.getContents().get(0) instanceof Variable) {
                return new MapUpdate(
                        (Variable) kList.getContents().get(0),
                        Collections.<Term, Term>emptyMap(),
                        Collections.singletonMap(
                                kList.getContents().get(1),
                                kList.getContents().get(2)));
            }
            return node;
        } else {
            /* custom function */
            return node;
        }
    }

}
