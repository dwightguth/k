// Copyright (c) 2013-2014 K Team. All Rights Reserved.
package org.kframework.compile.transformers;

import org.kframework.kil.ASTNode;
import org.kframework.kil.Attribute;
import org.kframework.kil.KApp;
import org.kframework.kil.KLabelConstant;
import org.kframework.kil.Production;
import org.kframework.kil.Rewrite;
import org.kframework.kil.Rule;
import org.kframework.kil.Term;
import org.kframework.kil.TermCons;
import org.kframework.kil.loader.Context;
import org.kframework.kil.visitors.CopyOnWriteTransformer;

import java.util.Collection;
import java.util.Set;

/**
 * Initially created by: Traian Florin Serbanuta
 * <p/>
 * Date: 6/12/13
 * Time: 8:21 AM
 *
 * Delete all rules which define the hooked functions specified in the given set of hooks.
 */
public class DeleteFunctionRules extends CopyOnWriteTransformer {
    private final Set<String> hooks;

    public DeleteFunctionRules(Set<String> hooks, Context context) {
        super("Delete function rules for given function symbols", context);
        this.hooks = hooks;
    }

    @Override
    public ASTNode visit(Rule node, Void _)  {
        Term body = node.getBody();
        if (body instanceof Rewrite) {
            body = ((Rewrite) body).getLeft();
        }
        Production prod = null;
        if (body instanceof TermCons) {
            prod = ((TermCons) body).getProduction();
        } else if (body instanceof KApp) {
            Term l = ((KApp) body).getLabel();
            if (!(l instanceof KLabelConstant)) return node;
            String label = ((KLabelConstant) l).getLabel();
            Collection<Production> prods = context.klabels().get(label);
            if (prods.size() != 1) {
                return node;
            } // Hooked functions should not be overloaded
            prod = prods.iterator().next();
        }
        if (prod == null || !prod.containsAttribute(Attribute.HOOK_KEY)) {
            return node;
        }
        final String hook = prod.getAttribute(Attribute.HOOK_KEY);
        if (!hooks.contains(hook)) {
            return node;
        }
        if (node.containsAttribute(Attribute.SIMPLIFICATION_KEY)) {
            return node;
        }
        return null;
    }
}
