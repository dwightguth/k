// Copyright (c) 2012-2014 K Team. All Rights Reserved.
package org.kframework.backend.maude;

import org.kframework.kil.*;
import org.kframework.kil.loader.Context;
import org.kframework.kil.visitors.CopyOnWriteTransformer;
import com.google.inject.Inject;

/**
 * Initially created by: Traian Florin Serbanuta
 * <p/>
 * Date: 12/18/12
 * Time: 12:59 AM
 */
public class MaudeContextExtractor extends CopyOnWriteTransformer {

    private final MaudeFilter maudeFilter;

    @Inject
    public MaudeContextExtractor(Context context, MaudeFilter maudeFilter) {
        super("Maude Contexts Extractor", context);
        this.maudeFilter = maudeFilter;
    }

    public String getResult() {
        return maudeFilter.getResult().toString();
    }

    @Override
    public ASTNode visit(Rule node, Void _)  {
        return node;
    }

    @Override
    public ASTNode visit(org.kframework.kil.Context node, Void _)  {
        maudeFilter.visitNode(node);
        return null;
    }

    @Override
    public ASTNode visit(Configuration node, Void _)  {
        return node;
    }

    @Override
    public ASTNode visit(Syntax node, Void _)  {
        return node;    //To change body of overridden methods use File | Settings | File Templates.
    }
}
