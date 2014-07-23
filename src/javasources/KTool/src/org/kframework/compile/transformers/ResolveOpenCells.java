// Copyright (c) 2012-2014 K Team. All Rights Reserved.
package org.kframework.compile.transformers;

import org.kframework.compile.utils.MetaK;
import org.kframework.kil.*;
import org.kframework.kil.Cell.Ellipses;
import org.kframework.kil.visitors.CopyOnWriteTransformer;
import org.kframework.utils.errorsystem.KException;
import org.kframework.utils.errorsystem.KException.ExceptionType;
import org.kframework.utils.errorsystem.KException.KExceptionGroup;
import org.kframework.utils.general.GlobalSettings;

import java.util.ArrayList;
import java.util.HashMap;

public class ResolveOpenCells extends CopyOnWriteTransformer {

    public ResolveOpenCells(org.kframework.kil.loader.Context context) {
        super("Resolve Open Cells", context);
    }

    @Override
    public ASTNode visit(Cell node, Void _)  {
        node = (Cell) super.visit(node, _);
        Ellipses ellipses = node.getEllipses();
        if (ellipses == Ellipses.NONE)
            return node;

        node = node.shallowCopy();
        node.setCellAttributes(new HashMap<String, String>(node.getCellAttributes()));
        node.setEllipses(Ellipses.NONE);

        DataStructureSort dataStructureSort
                = context.dataStructureSorts().get(context.configurationStructureMap().get(node.getLabel()).cell.getContents().getSort());
        if (dataStructureSort != null) {
            /* data structure sort */
            if (ellipses == Ellipses.BOTH && !dataStructureSort.type().equals(KSorts.LIST)) {
                ellipses = Ellipses.RIGHT;
            }

            Term content = node.getContents();
            if (ellipses == Ellipses.BOTH || ellipses == Ellipses.LEFT) {
                content = KApp.of(
                        KLabelConstant.of(dataStructureSort.constructorLabel()),
                        Variable.getFreshVar(dataStructureSort.name()),
                        content);
            }
            if (ellipses == Ellipses.BOTH || ellipses == Ellipses.RIGHT) {
                content = KApp.of(
                        KLabelConstant.of(dataStructureSort.constructorLabel()),
                        content,
                        Variable.getFreshVar(dataStructureSort.name()));
            }

            node.setContents(content);
            return node;
        }

        KSort kind = KSort.getKSort(node.getContents().getSort()).mainSort();
        Collection col;
        if (node.getContents() instanceof Collection) {
            col = (Collection) node.getContents().shallowCopy();
            col.setContents(new ArrayList<Term>(col.getContents()));
        } else {
            col = MetaK.createCollection(node.getContents(), kind);
            if (col == null) {
                GlobalSettings.kem.register(new KException(ExceptionType.ERROR,
                        KExceptionGroup.COMPILER,
                        "Expecting a collection item here but got " + node.getContents() + " which is of sort " + kind, getName(),
                        node.getFilename(), node.getLocation()));

            }
        }
        node.setContents(col);

        if (ellipses == Ellipses.BOTH && kind != KSort.K) {
            ellipses = Ellipses.RIGHT;
        }
        if (ellipses == Ellipses.BOTH || ellipses == Ellipses.LEFT) {
            col.getContents().add(0, Variable.getFreshVar(kind.toString()));
        }
        if (ellipses == Ellipses.BOTH || ellipses == Ellipses.RIGHT) {
            col.getContents().add(Variable.getFreshVar(kind.toString()));
        }

        return node;
    }

    @Override
    public ASTNode visit(Configuration node, Void _)  {
        return node;
    }

    @Override
    public ASTNode visit(Syntax node, Void _)  {
        return node;
    }

    @Override
    public ASTNode visit(org.kframework.kil.Context node, Void _)  {
        return node;
    }

}
