// Copyright (c) 2012-2014 K Team. All Rights Reserved.
package org.kframework.kil.loader;

import org.kframework.kil.Definition;
import org.kframework.kil.KSorts;
import org.kframework.kil.Production;
import org.kframework.kil.ProductionItem;
import org.kframework.kil.Sort;
import org.kframework.kil.Syntax;
import org.kframework.kil.UserList;
import org.kframework.kil.visitors.BasicVisitor;

public class CollectSubsortsVisitor extends BasicVisitor {

    public CollectSubsortsVisitor(Context context) {
        super(context);
    }

    public Void visit(Definition def, Void _) {
        super.visit(def, _);
        return null;
    }

    public Void visit(Syntax syn, Void _) {
        if (syn.getPriorityBlocks().size() == 0) {
            String sortName = syn.getSort().getName();
            if (!sortName.equals(KSorts.KITEM)) {
                context.subsorts().addRelation(KSorts.KITEM, syn.getSort().getName());
            }
        }
        return super.visit(syn, _);
    }

    public Void visit(Production prd, Void _) {
        if (!Sort.isBasesort(prd.getSort()))
            context.subsorts().addRelation(KSorts.KITEM, prd.getSort());
        if (prd.isSubsort()) {
            if (!prd.containsAttribute("onlyLabel")
                    && !prd.containsAttribute("notInRules")) {
                Sort srt = (Sort) prd.getItems().get(0);
                context.subsorts().addRelation(prd.getSort(), srt.toString());
            }
        } else if (prd.isListDecl()) {
            UserList srt = prd.getListDecl();
            context.subsorts().addRelation(prd.getSort(), srt.getSort());
        } else {
            for (ProductionItem pi : prd.getItems()) {
                if (pi instanceof Sort) {
                    Sort s = (Sort) pi;
                    if (!s.isBaseSort())
                        context.subsorts().addRelation(KSorts.KITEM, s.getName());
                }
            }
        }
        return null;
    }
}
