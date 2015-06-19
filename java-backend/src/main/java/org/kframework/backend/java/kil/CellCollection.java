// Copyright (c) 2013-2015 K Team. All Rights Reserved.
package org.kframework.backend.java.kil;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultiset;
import com.google.common.collect.Multiset;
import org.kframework.backend.java.symbolic.Transformer;
import org.kframework.backend.java.symbolic.Visitor;
import org.kframework.backend.java.util.Utils;
import org.kframework.kil.ASTNode;
import org.kframework.kil.DataStructureSort;
import org.kframework.kil.DataStructureSort.Label;
import org.kframework.utils.errorsystem.KEMException;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;


/**
 * Represents a collection of cells. The ordering of the internal cells
 * is fixed and agrees with the ordering of the cells used to construct this
 * cell collection.
 *
 * @author AndreiS
 *
 */
public class CellCollection extends Collection {

    public static class Cell implements Serializable {
        private final CellLabel cellLabel;
        private Term content;

        public Cell(CellLabel cellLabel, Term content) {
            this.cellLabel = cellLabel;
            this.content = content;
        }

        public CellLabel cellLabel() {
            return cellLabel;
        }

        public Term content() {
            return content;
        }

        public void setContent(Term content) {
            this.content = content;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }

            if (!(object instanceof Cell)) {
                return false;
            }

            Cell cell = (Cell) object;
            return cellLabel.equals(cell.cellLabel) && content.equals(cell.content);
        }

        @Override
        public int hashCode() {
            int hashCode = 1;
            hashCode = hashCode * Utils.HASH_PRIME + cellLabel.hashCode();
            hashCode = hashCode * Utils.HASH_PRIME + content.hashCode();
            return hashCode;
        }

        @Override
        public String toString() {
            return "<" + cellLabel() + ">" + content + "</" + cellLabel() + ">";
        }
    }

    public static final CellCollection EMPTY = new CellCollection(
            ImmutableMap.of(),
            ImmutableMultiset.of(),
            false);

    /**
     * Choose {@code ListMultimap} over {@code SetMultimap} because we need to
     * be able to store identical cells.
     */
    private final ImmutableMap<CellLabel, ImmutableMultiset<Cell>> cells;

    private final Multiset<Variable> collectionVariables;

    /**
     * Specifies if the explicit content of this cell collection contains
     * multiplicity cell.
     */
    // TODO(AndreiS): handle multiplicity='+'
    private final boolean hasMultiplicityCell;

    public static CellCollection singleton(CellLabel cellLabel, Term content, Definition definition) {
        return (CellCollection) builder(definition).put(cellLabel, content).build();
    }

    /**
     * Static helper method which creates canonicalized cell collection
     * according to the given contents.
     */
    public static Term of(Map<CellLabel, Multiset<Cell>> cells, Variable frame, Definition definition) {
        Builder builder = builder(definition);
        builder.putAll(cells);
        if (frame != null) {
            builder.concatenate(frame);
        }
        return builder.build();
    }

    private CellCollection(
            ImmutableMap<CellLabel, ImmutableMultiset<Cell>> cells,
            Multiset<Variable> collectionVariables,
            Definition definition) {
        this(cells, collectionVariables, numOfMultiplicityCellLabels(cells, definition) > 0);
    }

    private CellCollection(
            ImmutableMap<CellLabel, ImmutableMultiset<Cell>> cells,
            Multiset<Variable> collectionVariables,
            boolean hasMultiplicityCell) {
        super(computeFrame(collectionVariables), Kind.CELL_COLLECTION, null);
        this.cells = cells;
        this.collectionVariables = collectionVariables;
        this.hasMultiplicityCell = hasMultiplicityCell;
    }

    private static Variable computeFrame(Multiset<Variable> collectionVariables) {
        return collectionVariables.size() == 1 ? collectionVariables.iterator().next() : null;
    }

    private static int numOfMultiplicityCellLabels(Map<CellLabel, ImmutableMultiset<Cell>> cells, Definition definition) {
        int count = 0;
        for (CellLabel cellLabel : cells.keySet()) {
            if (definition.getConfigurationStructureMap().containsKey(cellLabel.name())) {
                if (definition.getConfigurationStructureMap().get(cellLabel.name()).isStarOrPlus()) {
                    count++;
                } else {
                    if (cells.get(cellLabel).size() != 1) {
                        throw KEMException.criticalError("Cell label " + cellLabel + " does not have "
                                + "multiplicity='*', but multiple cells found: " + cells.get(cellLabel)
                                + "\nExamine the last rule applied to determine the source of the error.");
                    }
                }
            }
        }

        assert count <= 1 :
            "Multiple types of starred cells in one cell collection not supported at present";
        return count;
    }

    public ImmutableMap<CellLabel, ImmutableMultiset<Cell>> cells() {
        return cells;
    }

    public java.util.Collection<Cell> values() {
        return cells.values().stream().flatMap(java.util.Collection::stream).collect(Collectors.toList());
    }

    public Multiset<Term> baseTerms() {
        return (Multiset<Term>) (Object) collectionVariables();
    }

    public Multiset<Variable> collectionVariables() {
        return collectionVariables;
    }

    public boolean containsLabel(CellLabel label) {
        return cells.containsKey(label);
    }

    public java.util.Collection<Cell> get(CellLabel label) {
        return cells.getOrDefault(label, ImmutableMultiset.of());
    }

    /**
     * Checks if the explicitly specified part of this cell collection contains
     * one or more types of cells whose multiplicity attributes are {@code "*"}
     * 's.
     */
    public boolean hasMultiplicityCell() {
        return hasMultiplicityCell;
    }

    public Set<CellLabel> labelSet() {
        return cells.keySet();
    }

    /**
     * Builds a new {@code CellCollection} by removing all the given cell
     * labels.
     */
    public Term removeAll(Set<CellLabel> removeLabels, Definition definition) {
        Builder builder = builder(definition);
        cells.keySet().stream()
                .filter(label -> !removeLabels.contains(label))
                .forEach(label -> builder.addAll(get(label)));
        builder.concatenate(collectionVariables);
        return builder.build();
    }

    @Override
    public boolean isEmpty() {
        return cells.isEmpty() && isConcreteCollection();
    }

    @Override
    public int concreteSize() {
        return cells.size();
    }

    @Override
    public final boolean isConcreteCollection() {
        return collectionVariables.isEmpty();
    }

    @Override
    public boolean isExactSort() {
        return true;
    }

    @Override
    public Sort sort() {
        return kind.asSort();
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }

        if (!(object instanceof CellCollection)) {
            return false;
        }

        CellCollection collection = (CellCollection) object;
        return collectionVariables.equals(collection.collectionVariables)
                && cells.equals(collection.cells);
    }

    @Override
    protected int computeHash() {
        int hashCode = 1;
        hashCode = hashCode * Utils.HASH_PRIME + collectionVariables.hashCode();
        hashCode = hashCode * Utils.HASH_PRIME + cells.hashCode();
        return hashCode;
    }

    @Override
    protected boolean computeMutability() {
        return true;
    }

    @Override
    public String toString() {
        if (isEmpty()) {
            return DataStructureSort.LABELS.get(org.kframework.kil.Sort.BAG).get(Label.UNIT);
        } else {
            StringBuilder stringBuilder = new StringBuilder();
            for (Cell cell : values()) {
                stringBuilder
                        .append("<").append(cell.cellLabel()).append(">")
                        .append(cell.content())
                        .append("</").append(cell.cellLabel()).append(">");
            }
            Iterator<Term> iter = baseTerms().iterator();
            while (iter.hasNext()) {
                stringBuilder.append(iter.next());
                if (iter.hasNext()) {
                    stringBuilder.append(" ");
                }
            }
            return stringBuilder.toString();
        }
    }

    @Override
    public void accept(Visitor visitor) {
        visitor.visit(this);
    }

    @Override
    public ASTNode accept(Transformer transformer) {
        return transformer.transform(this);
    }

    public static Builder builder(Definition definition) {
        return new Builder(definition);
    }

    public static class Builder {
        private final Map<CellLabel, ImmutableMultiset.Builder<Cell>> cellsBuilder;
        private final ImmutableMultiset.Builder<Variable> collectionVariablesBuilder;
        private final Definition definition;

        private Builder(Definition definition) {
            this.definition = definition;
            cellsBuilder = new HashMap<>();
            collectionVariablesBuilder = ImmutableMultiset.builder();
        }

        public Builder add(Cell cell) {
            put(cell.cellLabel(), cell.content);
            return this;
        }

        public Builder addAll(Iterable<Cell> cells) {
            for (Cell cell : cells) {
                add(cell);
            }
            return this;
        }

        public Builder put(CellLabel cellLabel, Term content) {
            ImmutableMultiset.Builder<Cell> multiset = cellsBuilder.get(cellLabel);
            if (multiset == null) {
                multiset = ImmutableMultiset.builder();
                cellsBuilder.put(cellLabel, multiset);
            }
            multiset.add(new Cell(cellLabel, content));
            return this;
        }

        public Builder putAll(Map<CellLabel, ? extends Multiset<Cell>> cellMap) {
            for (Map.Entry<CellLabel, ? extends Multiset<Cell>> entry : cellMap.entrySet()) {
                for (Cell cell : entry.getValue()) {
                    put(entry.getKey(), cell.content);
                }
            }
            return this;
        }

        public Builder concatenate(Term term) {
            if (term instanceof CellCollection) {
                CellCollection cellCollection = (CellCollection) term;
                putAll(cellCollection.cells);
                collectionVariablesBuilder.addAll(cellCollection.collectionVariables);
            } else if (term instanceof Variable && term.kind == Kind.CELL_COLLECTION) {
                collectionVariablesBuilder.add((Variable) term);
            } else {
                assert false : "unexpected concatenated term " + term;
            }
            return this;
        }

        public Builder concatenate(Term... terms) {
            for (Term term : terms) {
                concatenate(term);
            }
            return this;
        }

        public Builder concatenate(Iterable<? extends Term> terms) {
            for (Term term : terms) {
                concatenate(term);
            }
            return this;
        }

        public Term build() {
            ImmutableMap.Builder<CellLabel, ImmutableMultiset<Cell>> intermediateCellsBuilder = ImmutableMap.builder();
            for (Map.Entry<CellLabel, ImmutableMultiset.Builder<Cell>> entry : cellsBuilder.entrySet()) {
                intermediateCellsBuilder.put(entry.getKey(), entry.getValue().build());
            }
            ImmutableMap<CellLabel, ImmutableMultiset<Cell>> cells = intermediateCellsBuilder.build();
            ImmutableMultiset<Variable> collectionVariables = collectionVariablesBuilder.build();
            if (cells.isEmpty()) {
                switch (collectionVariables.size()) {
                    case 0:  return EMPTY;
                    case 1:  return collectionVariables.iterator().next();
                    default: return new CellCollection(cells, collectionVariables, definition);
                }
            } else {
                return new CellCollection(cells, collectionVariables, definition);
            }
        }
    }

}
