// Copyright (c) 2012-2014 K Team. All Rights Reserved.
package org.kframework.kil;

import java.util.Collections;
import java.util.List;

import org.kframework.kil.Sort.SortId;
import org.kframework.kil.visitors.Visitor;

/** A nonterminal in a {@link Production}. Also abused in some places as a sort identifier */
public class NonTerminal extends ProductionItem {

    private SortDecl sort;
    private String capture;

    public NonTerminal(SortDecl sort) {
        super();
        this.sort = sort;
    }

    public NonTerminal(SortDecl sort, String capture) {
        this(sort);
        this.capture = capture;
    }

    public NonTerminal(NonTerminal nonTerminal) {
        super(nonTerminal);
        this.sort = nonTerminal.sort;
        this.capture = nonTerminal.capture;
    }

    public String getCapture() {
        return capture;
    }

    public String getName() {
        return getSort().id.getName();
    }

    public void setSort(SortId sort, List<TypeParameter> parameters) {
        this.sort = new SortDecl(sort, parameters);
    }

    public static class SortDecl {
        private SortId id;
        private List<TypeParameter> parameters;

        public SortDecl(SortId id) {
            this(id, Collections.<TypeParameter>emptyList());
        }

        public SortDecl(SortId id, List<TypeParameter> parameters) {
            this.id = id;
            this.parameters = parameters;
        }

        public SortId getId() {
            return id;
        }

        public String getName() {
            return id.getName();
        }

        public List<TypeParameter> getParameters() {
            return parameters;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((id == null) ? 0 : id.hashCode());
            result = prime * result
                    + ((parameters == null) ? 0 : parameters.hashCode());
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
            SortDecl other = (SortDecl) obj;
            if (id == null) {
                if (other.id != null)
                    return false;
            } else if (!id.equals(other.id))
                return false;
            if (parameters == null) {
                if (other.parameters != null)
                    return false;
            } else if (!parameters.equals(other.parameters))
                return false;
            return true;
        }

        public boolean isComputationSort() {
            return id.isComputationSort();
        }

        public boolean isKSort() {
            return id.isKSort();
        }

        public boolean isBuiltinSort() {
            return id.isBuiltinSort();
        }

        public boolean isBaseSort() {
            return id.isBaseSort();
        }

        public boolean isCellSort() {
            return id.isCellSort();
        }
    }

    public SortDecl getSort() {
        return sort.id.isCellSort() ? Sort.BAG.getDecl() : sort;
    }

    public SortDecl getRealSort() {
        return sort;
    }

    @Override
    public String toString() {
        return sort.getName();
    }

    @Override
    protected <P, R, E extends Throwable> R accept(Visitor<P, R, E> visitor, P p) throws E {
        return visitor.complete(this, visitor.visit(this, p));
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null)
            return false;
        if (obj == this)
            return true;
        if (!(obj instanceof NonTerminal))
            return false;

        NonTerminal nt = (NonTerminal) obj;

        if (!sort.equals(nt.sort))
            return false;
        return true;
    }

    @Override
    public int hashCode() {
        return sort.hashCode();
    }

    @Override
    public NonTerminal shallowCopy() {
        return new NonTerminal(this);
    }
}
