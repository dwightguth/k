package org.kframework.kil.loader;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.kframework.backend.java.symbolic.JavaExecutionOptions;
import org.kframework.compile.transformers.CompleteSortLatice;
import org.kframework.compile.utils.ConfigurationStructureMap;
import org.kframework.kil.ASTNode;
import org.kframework.kil.CellDataStructure;
import org.kframework.kil.DataStructureSort;
import org.kframework.kil.KSorts;
import org.kframework.kil.Production;
import org.kframework.kompile.KompileOptions;
import org.kframework.krun.KRunOptions;
import org.kframework.krun.KRunOptions.ConfigurationCreationOptions;
import org.kframework.main.GlobalOptions;
import org.kframework.utils.Poset;
import org.kframework.utils.options.SMTOptions;

import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;

public abstract class Context {

    public abstract KompileOptions kompileOptions();
    public abstract KRunOptions krunOptions();
    public abstract GlobalOptions globalOptions();
    public abstract SMTOptions smtOptions();
    public abstract ConfigurationCreationOptions ccOptions();
    public abstract JavaExecutionOptions javaExecutionOptions();

    public abstract Set<Production> productions();
    public abstract Multimap<String, Production> sorts();
    public abstract Map<String, Production> listSorts();
    public abstract Map<String, Production> tokenSorts();
    public abstract Multimap<String, Production> klabels();
    public abstract Map<String, Production> listKLabels();
    public abstract Map<String, ASTNode> locations();
    public abstract Map<String, Production> canonicalBracketForSort();

    public abstract Map<String, String> freshFunctionNames();
    public abstract Map<String, DataStructureSort> dataStructureSorts();

    public abstract String startSymbolPgm();

    public abstract ConfigurationStructureMap configurationStructureMap();
    public abstract Map<String, String> configVarSorts();
    public abstract Map<String, CellDataStructure> cellDataStructures();
    public abstract int maxConfigurationLevel();
    public abstract List<String> komputationCellNames();

    public abstract File kompiled();
    public abstract File dotk();

    public abstract Poset subsorts();
    public abstract Poset assocLeft();
    public abstract Poset assocRight();
    public abstract Poset priorities();

    public DataStructureSort dataStructureListSortOf(String sortName) {
        DataStructureSort sort = dataStructureSorts().get(sortName);
        if (sort == null) return null;
        if (!sort.type().equals(KSorts.LIST)) return null;
        return sort;
    }

    public boolean isSubsorted(String bigSort, String smallSort) {
        return subsorts().isInRelation(bigSort, smallSort);
    }

    public boolean isSubsortedEq(String bigSort, String smallSort) {
        if (bigSort.equals(smallSort))
            return true;
        return subsorts().isInRelation(bigSort, smallSort);
    }

    public boolean hasCommonSubsort(String... sorts) {
        Set<String> maximalLowerBounds = subsorts().getMaximalLowerBounds(Sets.newHashSet(sorts));

        if (maximalLowerBounds.isEmpty()) {
            return false;
        } else if (maximalLowerBounds.size() == 1) {
            String sort = maximalLowerBounds.iterator().next();
            /* checks if the only common subsort is undefined */
            if (sort.equals(CompleteSortLatice.BOTTOM_SORT_NAME)
                    || (listSorts().containsKey(sort)
                    && listSorts().get(sort).getListDecl().getSort().equals(CompleteSortLatice.BOTTOM_SORT_NAME))) {
                return false;
            }
        }

        return true;
    }

    /**
     * Finds the LUB (Least Upper Bound) of a given set of sorts.
     *
     * @param sorts
     *            the given set of sorts
     * @return the sort which is the LUB of the given set of sorts on success;
     *         otherwise {@code null}
     */
    public String getLUBSort(Set<String> sorts) {
        return subsorts().getLUB(sorts);
    }

    /**
     * Finds the LUB (Least Upper Bound) of a given set of sorts.
     *
     * @param sorts
     *            the given set of sorts
     * @return the sort which is the LUB of the given set of sorts on success;
     *         otherwise {@code null}
     */
    public String getLUBSort(String... sorts) {
        return subsorts().getLUB(Sets.newHashSet(sorts));
    }

    /**
     * Finds the GLB (Greatest Lower Bound) of a given set of sorts.
     *
     * @param sorts
     *            the given set of sorts
     * @return the sort which is the GLB of the given set of sorts on success;
     *         otherwise {@code null}
     */
    public String getGLBSort(Set<String> sorts) {
        return subsorts().getGLB(sorts);
    }

    /**
     * Finds the GLB (Greatest Lower Bound) of a given set of sorts.
     *
     * @param sorts
     *            the given set of sorts
     * @return the sort which is the GLB of the given set of sorts on success;
     *         otherwise {@code null}
     */
    public String getGLBSort(String... sorts) {
        return subsorts().getGLB(Sets.newHashSet(sorts));
    }

    public boolean isLeftAssoc(String labelOuter, String labelInner) {
        return assocLeft().isInRelation(labelOuter, labelInner);
    }

    public boolean isRightAssoc(String labelOuter, String labelInner) {
        return assocRight().isInRelation(labelOuter, labelInner);
    }

    public boolean isPriorityWrong(String labelOuter, String labelInner) {
        return priorities().isInRelation(labelOuter, labelInner);
    }
}
