// Copyright (c) 2014 K Team. All Rights Reserved.
package org.kframework.backend.java.builtins;

import org.kframework.backend.java.kil.KItem;
import org.kframework.backend.java.kil.KItem.KItemOperations;
import org.kframework.backend.java.kil.KLabelConstant;
import org.kframework.backend.java.kil.KList;
import org.kframework.backend.java.kil.Sort;
import org.kframework.backend.java.kil.Term;
import org.kframework.backend.java.kil.TermContext;

import com.google.inject.Inject;


/**
 * Implements generation of fresh constants.
 *
 * @author AndreiS
 */
public class FreshOperations {

    private final KItemOperations kItemOps;

    @Inject
    public FreshOperations(KItemOperations kItemOps) {
        this.kItemOps = kItemOps;
    }

    public Term fresh(Sort sort, TermContext context) {
        return fresh(StringToken.of(sort.name()), context);
    }

    public Term fresh(StringToken term, TermContext context) {
        String name = context.definition().context().freshFunctionNames.get(org.kframework.kil.Sort.of(term.stringValue()));
        if (name == null) {
            throw new UnsupportedOperationException();
        }

        KItem freshFunction = kItemOps.newKItem(
                KLabelConstant.of(name, context.definition().context()),
                KList.singleton(IntToken.of(context.incrementCounter())),
                context);
        return freshFunction.evaluateFunction(false, context);
    }

}
