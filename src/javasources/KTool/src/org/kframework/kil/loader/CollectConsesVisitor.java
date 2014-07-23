// Copyright (c) 2012-2014 K Team. All Rights Reserved.
package org.kframework.kil.loader;

import org.kframework.kil.Production;
import org.kframework.kil.visitors.BasicVisitor;

public class CollectConsesVisitor extends BasicVisitor {
    public CollectConsesVisitor(Context context) {
        super(context);
    }

    @Override
    public Void visit(Production node, Void _) {
        context.productions().add(node);
        context.klabels().put(node.getKLabel(), node);
        if (node.isListDecl()) {
            context.listSorts().put(node.getSort(), node);
            context.listKLabels().put(node.getSort(), node);
        }
        return null;
    }
}
