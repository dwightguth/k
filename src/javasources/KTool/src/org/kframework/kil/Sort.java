// Copyright (c) 2014 K Team. All Rights Reserved.
package org.kframework.kil;

import java.io.Serializable;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.kframework.kil.NonTerminal.SortDecl;
import org.kframework.kil.loader.Context;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

public class Sort implements Serializable {

    public static final Sort K = Sort.of("K");
    public static final Sort KITEM = Sort.of("KItem");
    public static final Sort KLABEL = Sort.of("KLabel");
    public static final Sort KLIST = Sort.of("KList");
    public static final Sort KRESULT = Sort.of("KResult");

    public static final Sort CELL_LABEL = Sort.of("CellLabel");

    public static final Sort BAG = Sort.of("Bag");
    public static final Sort BAG_ITEM = Sort.of("BagItem");
    public static final Sort LIST = Sort.of("List");
    public static final Sort LIST_ITEM = Sort.of("ListItem");
    public static final Sort MAP = Sort.of("Map");
    public static final Sort MAP_ITEM = Sort.of("MapItem");
    public static final Sort SET = Sort.of("Set");
    public static final Sort SET_ITEM = Sort.of("SetItem");

    public static final Sort ID = Sort.of("Id");
    public static final Sort INT = Sort.of("Int");
    public static final Sort BOOL = Sort.of("Bool");
    public static final Sort STRING = Sort.of("String");

    public static final Sort BUILTIN_ID = Sort.of("#Id");
    public static final Sort BUILTIN_RAT = Sort.of("#Rat");
    public static final Sort BUILTIN_BOOL = Sort.of("#Bool");
    public static final Sort BUILTIN_INT = Sort.of("#Int");
    public static final Sort BUILTIN_FLOAT = Sort.of("#Float");
    public static final Sort BUILTIN_STRING = Sort.of("#String");
    public static final Sort BUILTIN_BOT = Sort.of("#Bot");

    /* IO */
    public static final Sort STREAM = Sort.of("Stream");

    /* tokens */
    public static final Sort BUILTIN_INT32 = Sort.of("#Int32");

    /* LTL builtin sorts */
    public static final Sort BUILTIN_LTL_FORMULA = Sort.of("#LtlFormula");
    public static final Sort BUILTIN_PROP = Sort.of("#Prop");
    public static final Sort BUILTIN_MODEL_CHECKER_STATE = Sort.of("#ModelCheckerState");
    public static final Sort BUILTIN_MODEL_CHECK_RESULT = Sort.of("#ModelCheckResult");

    public static class SortId implements Serializable {
        private final String name;
        private final int arity;

        public SortId(String name, int arity) {
            this.name = name;
            this.arity = arity;
        }

        public String getName() {
            return name;
        }

        public int getArity() {
            return arity;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + arity;
            result = prime * result + ((name == null) ? 0 : name.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            SortId other = (SortId) obj;
            if (arity != other.arity)
                return false;
            if (name == null) {
                if (other.name != null)
                    return false;
            } else if (!name.equals(other.name))
                return false;
            return true;
        }

        @Override
        public String toString() {
            return Sort.of(this).toString();
        }

        public boolean isCellSort() {
            return getName().endsWith(CELL_SORT_NAME) || getName().endsWith(CELL_FRAGMENT_NAME);
        }

        public boolean isComputationSort() {
            return equals(K) || equals(KITEM) || !isKSort();
        }

        private static Set<SortId> K_SORTS = ImmutableSet.of(K.id, BAG.id, BAG_ITEM.id, KITEM.id,
                KLIST.id, CELL_LABEL.id, KLABEL.id);

        private static Set<SortId> BASE_SORTS = ImmutableSet.of(K.id, KRESULT.id, KITEM.id,
                KLIST.id, BAG.id, BAG_ITEM.id, KLABEL.id, CELL_LABEL.id);

        public boolean isKSort() {
            return K_SORTS.contains(this);
        }

        public boolean isBaseSort() {
            return BASE_SORTS.contains(this);
        }
        public static Set<SortId> getBaseSorts() {
            return new HashSet<SortId>(BASE_SORTS);
        }



        public boolean isBuiltinSort() {
            /* TODO: replace with a proper table of builtins */
            return equals(Sort.BUILTIN_BOOL.id)
                   || equals(Sort.BUILTIN_INT.id)
                   || equals(Sort.BUILTIN_STRING.id)
                   || equals(Sort.BUILTIN_FLOAT.id)
                   /* LTL builtin sorts */
//                   || sort.equals(Sort.SHARP_LTL_FORMULA)
                   || equals(Sort.BUILTIN_PROP.id)
                   || equals(Sort.BUILTIN_MODEL_CHECKER_STATE.id)
                   || equals(Sort.BUILTIN_MODEL_CHECK_RESULT.id);
        }
    }

    private final SortId id;

    private final ImmutableList<Sort> parameters;

    public static Sort of(String name, Sort... parameters) {
        return new Sort(name, parameters);
    }

    public static Sort of(String name, List<Sort> parameters) {
        return new Sort(name, parameters);
    }

    /**
     * Gets the most unconstrained instance of this SortId. For sorts with no
     * dependencies, this is the single sort with this id. For sorts with dependencies,
     * an unconstrained dependency is created for each type parameter.
     * @param id
     * @return
     */
    public static Sort of(SortDecl decl) {
        Sort[] params = new Sort[decl.getId().arity];
        for (int i = 0; i < params.length; i++) {
            params[i] = decl.getParameters().get(i).getBound();
        }
        return new Sort(decl.getId().name, params);
    }

    private Sort(String name, Sort[] parameters) {
        this.id = new SortId(name, parameters.length);
        this.parameters = ImmutableList.copyOf(parameters);
    }

    private Sort(String name, List<Sort> dependencies) {
        this.id = new SortId(name, dependencies.size());
        this.parameters = ImmutableList.copyOf(dependencies);
    }

    public SortId getId() {
        return id;
    }

    public String getName() {
        return id.name;
    }

    public ImmutableList<Sort> getParameters() {
        return parameters;
    }

    public SortDecl getDecl() {
        assert id.arity == 0;
        return new SortDecl(id);
    }

    public org.kframework.backend.java.kil.Sort toBackendJava(Context context) {
        org.kframework.backend.java.kil.Sort[] parameters =
                new org.kframework.backend.java.kil.Sort[this.parameters.size()];
        for (int i = 0; i < parameters.length; i++) {
            parameters[i] = this.parameters.get(i).toBackendJava(context);
        }
        return org.kframework.backend.java.kil.Sort.of(id.name, parameters);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getName());
        if (parameters.size() > 0) {
            sb.append("{");
            String conn = "";
            for (Sort tp : parameters) {
                sb.append(conn).append(tp);
                conn = ",";
            }
            sb.append("}");
        }
        return sb.toString();
    }


    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result
                + ((parameters == null) ? 0 : parameters.hashCode());
        result = prime * result + ((id == null) ? 0 : id.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        Sort other = (Sort) obj;
        if (parameters == null) {
            if (other.parameters != null)
                return false;
        } else if (!parameters.equals(other.parameters))
            return false;
        if (id == null) {
            if (other.id != null)
                return false;
        } else if (!id.equals(other.id))
            return false;
        return true;
    }

    public boolean isKSort() {
        return id.isKSort();
    }

    public boolean isBaseSort() {
        return id.isBaseSort();
    }

    public boolean isComputationSort() {
        return id.isComputationSort();
    }

    public boolean isBuiltinSort() {
        return id.isBuiltinSort();
    }

    public boolean isCellSort() {
        return id.isCellSort();
    }

    /**
     * TODO(traiansf)
     * @param sort
     * @return
     */
    public Sort getKSort() {
        return SortId.K_SORTS.contains(this.id) ? this : K;
    }

    public boolean isDataSort() {
        return equals(Sort.BUILTIN_BOOL)
                || equals(Sort.BUILTIN_INT)
                || equals(Sort.BUILTIN_STRING);
    }

    public static final String CELL_SORT_NAME = "CellSort";
    public static final String CELL_FRAGMENT_NAME = "CellFragment";
    public static final String LIST_OF_BOTTOM_PREFIX = "#ListOf";

    public boolean isCellFragment() {
        return id.getName().endsWith(CELL_FRAGMENT_NAME);
    }

    public Sort getUserListSort(String separator) {
        return Sort.of(LIST_OF_BOTTOM_PREFIX + id.getName() + "{\"" + separator + "\"}");
    }

    /**
     * TODO(traiansf)
     * @return
     */
    public Sort mainSort() {
        if (equals(BAG) || equals(BAG_ITEM)) {
            return BAG;
        } else if (equals(KITEM)) {
            return K;
        } else {
            return this;
        }
    }

    public boolean isDefaultable() {
        return equals(K) || equals(BAG);
    }

}
