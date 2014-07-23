// Copyright (c) 2014 K Team. All Rights Reserved.
package org.kframework.kil.loader;

import org.kframework.kil.Production;
import org.kframework.kil.visitors.BasicVisitor;

public class CollectBracketsVisitor extends BasicVisitor {

    public CollectBracketsVisitor(Context context) {
        super(context);
    }

    @Override
    public Void visit(Production node, Void _) {
        if (node.isBracket()) {
            context.canonicalBracketForSort().put(node.getSort(), node);
        }
        return null;
    }
}
