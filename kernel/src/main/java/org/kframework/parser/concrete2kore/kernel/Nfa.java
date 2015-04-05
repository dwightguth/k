package org.kframework.parser.concrete2kore.kernel;

import com.google.common.collect.ArrayTable;
import com.google.common.collect.BiMap;
import com.google.common.collect.ContiguousSet;
import com.google.common.collect.DiscreteDomain;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Multiset;
import com.google.common.collect.Range;
import com.google.common.collect.Sets;
import com.google.common.collect.Table;
import org.apache.commons.lang3.tuple.Pair;
import org.kframework.utils.algorithms.AutoVivifyingBiMap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
* A Nondeterministic Finite Automaton (∪-Nfa) with the ability to store an additional value with each State
* The domain of the transition function is S x TAlphabet, where S is the set of states.The type of the value associated with a state.
*/
public class Nfa <TAlphabet, TAssignment>  
{
    private static class RefSupport<T> {
        T value;

        public T getValue() {
            return value;
        }

        public void setValue(T value) {
            this.value = value;
        }
    }

    private final Set<State<TAssignment>> _acceptStates = new HashSet<>();
    private final Set<State<TAssignment>> _startStates = new HashSet<>();
    private final Set<State<TAssignment>> _states = new HashSet<>();
    private final Multitable<State<TAssignment>, TAlphabet, State<TAssignment>> _transitionFunction = new Multitable<>();

    public Set<State<TAssignment>> getStates() {
        return _states;
    }

    public Multitable<State<TAssignment>, TAlphabet, State<TAssignment>> getTransitionFunction() {
        return _transitionFunction;
    }

    public Set<State<TAssignment>> getStartStates() {
        return _startStates;
    }

    public Set<State<TAssignment>> getAcceptStates() {
        return _acceptStates;
    }

    public Set<Transition<TAssignment>> getTransitions() {
        return  _transitionFunction.cellSet().stream().flatMap(c -> c.getValue().stream().map(s -> new Transition<>(c.getRowKey(), c.getColumnKey(), s))).collect(Collectors.toSet());
    }

    public Set<State<TAssignment>> transitionFunctionExtended(Iterable<State<TAssignment>> fromStates, TAlphabet input) {
        if (fromStates == null)
        {
            throw new NullPointerException("fromStates");
        }
         
        Set<State<TAssignment>> result = new HashSet<>();
        for (State state : fromStates)
        {
            result.addAll(_transitionFunction.getOrDefault(state, input));
        }
        return result;
    }


    /**
    * Creates a new Nfa that has only one transition for each input symbol for each state - i.e. it is deterministic
    * 
    *  @return The new DFA
    */
    public Nfa<TAlphabet,Set<State<TAssignment>>> determinize()  {
        Map<Set<State<TAssignment>>, State<Set<State<TAssignment>>>> stateSetToDeterminizedState = new HashMap<>();
        Nfa<TAlphabet, Set<State<TAssignment>>> result = new Nfa<>();
        Multiset<State<Set<State<TAssignment>>>> resultAcceptStates = HashMultiset.create();
        Function<Set<State<TAssignment>>, State<Set<State<TAssignment>>>> adder = (stateSet) -> determinizeAdder(stateSetToDeterminizedState, result, resultAcceptStates, stateSet);
        Set<State<TAssignment>> startStateSet = new HashSet<>(getStartStates());
        result.getStartStates().add(adder.apply(startStateSet));
        result.getStates().addAll(stateSetToDeterminizedState.values());
        for (State<Set<State<TAssignment>>> acceptState : resultAcceptStates)
        {
            result.getAcceptStates().add(acceptState);
        }
        return result;
    }

    private State<Set<State<TAssignment>>> determinizeAdder(
            Map<Set<State<TAssignment>>,
            State<Set<State<TAssignment>>>> stateSetToDeterminizedState,
            Nfa<TAlphabet, Set<State<TAssignment>>> result,
            Multiset<State<Set<State<TAssignment>>>> resultAcceptStates,
            Set<State<TAssignment>> stateSet) {
        if (stateSetToDeterminizedState.containsKey(stateSet)) {
            return stateSetToDeterminizedState.get(stateSet);
        }
        State<Set<State<TAssignment>>> newState = new State<>(stateSet);
        boolean isAcceptState = stateSet.stream().anyMatch((x) -> getAcceptStates().contains(x));
        if (isAcceptState)
        {
            resultAcceptStates.add(newState);
        }
        Set<TAlphabet> transitions = stateSet.stream().map(x -> getTransitionFunction().row(x))
                .flatMap(y -> y.keySet().stream()).collect(Collectors.toSet());
        transitions.stream().forEach(transition -> {
            Set<State<TAssignment>> nextStateSet = transitionFunctionExtended(stateSet, transition);
            if (nextStateSet.size() > 0) {
                State<Set<State<TAssignment>>> nextState = determinizeAdder(stateSetToDeterminizedState, result, resultAcceptStates, new HashSet<>(nextStateSet));
                result.getTransitionFunction().putElement(newState, transition, nextState);
            }
        });
        stateSetToDeterminizedState.put(stateSet, newState);
        return newState;
    }

    /**
    * Creates a new Nfa that recognizes the reversed language
    * 
    *  @return The new Nfa
    */
    public Nfa<TAlphabet,TAssignment> dual() {
        Nfa<TAlphabet, TAssignment> result = new Nfa<>();
        result._startStates.addAll(_acceptStates);
        result._states.addAll(_states);
        for ( Table.Cell<State<TAssignment>, TAlphabet, Set<State<TAssignment>>> keyValuePair : _transitionFunction.cellSet())
        {
            for (State state : keyValuePair.getValue()) {
                result._transitionFunction.putElement(state, keyValuePair.getColumnKey(), keyValuePair.getRowKey());
            }
        }
        result._acceptStates.addAll(_startStates);
        return result;
    }

    public Nfa()  {
    }

    /**
    * Create a deep copy of the specified NFA (the state objects are shallow copied)
    *
    */
    public Nfa(Nfa<TAlphabet,TAssignment> other)  {
        getStates().addAll(other.getStates());
        getStartStates().addAll(other.getStartStates());
        getAcceptStates().addAll(other.getAcceptStates());
        getTransitionFunction().putAll(other.getTransitionFunction());
    }

    /**
    * Creates a state map (SM) as described in [1]
    * 
    */
    private ArrayTable<Set<State<TAssignment>>, Set<State<TAssignment>>, Set<State<TAssignment>>> makeStateMap(RefSupport<Nfa<TAlphabet,Set<State<TAssignment>>>> determinized)  {
        determinized.setValue(determinize());
        Nfa<TAlphabet,Set<State<TAssignment>>> determinizedDual = dual().determinize();
        List<State<Set<State<TAssignment>>>> orderedRows = Lists.newArrayList(determinized.getValue().getStates());
        State<Set<State<TAssignment>>> first = determinized.getValue().getStartStates().iterator().next();
        orderedRows.remove(first);
        orderedRows.add(0, first);
        List<State<Set<State<TAssignment>>>> orderedColumns = Lists.newArrayList(determinizedDual.getStates());
        first =  determinizedDual.getStartStates().iterator().next();
        orderedColumns.remove(first);
        orderedColumns.add(0, first);
        ArrayTable<Set<State<TAssignment>>, Set<State<TAssignment>>, Set<State<TAssignment>>> result = ArrayTable.create(
                orderedRows.stream().map(State::getValue).collect(Collectors.toList()),
                orderedColumns.stream().map(State::getValue).collect(Collectors.toList()));
        for (State<Set<State<TAssignment>>> rowState : orderedRows) {
            Set<State<TAssignment>> rowStateSet = new HashSet<>(rowState.getValue());
            for (State<Set<State<TAssignment>>> columnState : orderedColumns) {
                Set<State<TAssignment>> columnStateSet = new HashSet<>(columnState.getValue());
                Set<State<TAssignment>> intersect = new HashSet<>(rowStateSet);
                intersect.retainAll(columnStateSet);
                result.put(rowStateSet, columnStateSet, intersect);
            }
        }
        return result;
    }

    private Nfa<TAlphabet,Integer> generateEquivalenceClassReducedDfa(Nfa<TAlphabet,Set<State<TAssignment>>> subsetConstructionDfa, Map<Set<State<TAssignment>>, Integer> equivalenceClassLookup)  {
        Nfa<TAlphabet, Integer> result = new Nfa<>();
        AutoVivifyingBiMap<Integer, State<Integer>> resultStates = new AutoVivifyingBiMap<>(State::new);
        result.getStartStates().add(resultStates.get(equivalenceClassLookup.get(subsetConstructionDfa.getStartStates().iterator().next().getValue())));
        for (State<Set<State<TAssignment>>> acceptState : subsetConstructionDfa.getAcceptStates())
        {
            result.getAcceptStates().add(resultStates.get(equivalenceClassLookup.get(acceptState.getValue())));
        }
        for ( Table.Cell<State<Set<State<TAssignment>>>, TAlphabet, Set<State<Set<State<TAssignment>>>>> keyValuePair : subsetConstructionDfa.getTransitionFunction().cellSet())
        {
            State<Integer> fromState = resultStates.get(equivalenceClassLookup.get(keyValuePair.getRowKey().getValue()));
            TAlphabet inputSymbol = keyValuePair.getColumnKey();
            for (State<Set<State<TAssignment>>> state : keyValuePair.getValue())
            {
                State<Integer> toState = resultStates.get(equivalenceClassLookup.get(state.getValue()));
                result.getTransitionFunction().putElement(fromState, inputSymbol, toState);
            }
        }
        result.getStates().addAll(resultStates.values());
        return result;
    }

    private <T> boolean getRAMValue(ArrayTable<T, T, Set<State<TAssignment>>> stateMap, int row, int col) {
        return stateMap.at(row, col).size() > 0;
    }

    private ArrayTable<Set<Integer>, Set<Integer>, Set<State<TAssignment>>> reduceStateMap(ArrayTable<Set<State<TAssignment>>, Set<State<TAssignment>>, Set<State<TAssignment>>> stateMap,
                                           Nfa<TAlphabet,Set<State<TAssignment>>> subsetConstructionDfa,
                                           RefSupport<Nfa<TAlphabet,Integer>> minimizedSubsetConstructionDfa)  {
        //construct an elementary automata matrix (EAM) [1]
        //determine which rows can be merged
        List<Set<Integer>> rowsToMerge = computeAxisToMerge(stateMap.rowKeySet(), stateMap.columnKeySet(), (on, off) -> getRAMValue(stateMap, on, off));
        //determine which columns can be merged
        List<Set<Integer>> columnsToMerge = computeAxisToMerge(stateMap.columnKeySet(), stateMap.rowKeySet(), (on, off) -> getRAMValue(stateMap, off, on));

        ArrayTable<Set<Integer>, Set<Integer>, Set<State<TAssignment>>> result = ArrayTable.create(rowsToMerge, columnsToMerge);
        Map<Set<State<TAssignment>>, Integer> stateSetToEquivalenceClassRowIndex = new HashMap<>();
        for (int equivalenceClassRowIndex = 0;equivalenceClassRowIndex < rowsToMerge.size();equivalenceClassRowIndex++)
        {
            for (int row : rowsToMerge.get(equivalenceClassRowIndex))
            {
                stateSetToEquivalenceClassRowIndex.put(stateMap.rowKeyList().get(row), equivalenceClassRowIndex);
            }
        }
        minimizedSubsetConstructionDfa.setValue(generateEquivalenceClassReducedDfa(subsetConstructionDfa, stateSetToEquivalenceClassRowIndex));
        for (int equivalenceClassRowIndex = 0;equivalenceClassRowIndex < rowsToMerge.size();equivalenceClassRowIndex++)
        {
            for (int equivalenceClassColumnIndex = 0;equivalenceClassColumnIndex < columnsToMerge.size();equivalenceClassColumnIndex++)
            {
                Set<State<TAssignment>> statesUnion = new HashSet<>();
                result.set(equivalenceClassRowIndex, equivalenceClassColumnIndex, statesUnion);
                for (int rowIndex : rowsToMerge.get(equivalenceClassRowIndex))
                {
                    for (int columnIndex : columnsToMerge.get(equivalenceClassColumnIndex))
                    {
                        statesUnion.addAll(stateMap.at(rowIndex, columnIndex));
                    }
                }
            }
        }
        return result;
    }

    private List<Set<Integer>> computeAxisToMerge(Set<Set<State<TAssignment>>> onAxis,
                                                  Set<Set<State<TAssignment>>> offAxis,
                                                  BiFunction<Integer, Integer, Boolean> readTable) {
        List<Set<Integer>> rowsToMerge = new ArrayList<>();
        {
            List<Integer> unmergedRows = new ArrayList<>(ContiguousSet.create(Range.closedOpen(0, onAxis.size()), DiscreteDomain.integers()));
            while (unmergedRows.size() > 0)
            {
                rowsToMerge.add(Sets.newHashSet(unmergedRows.get(0)));
                for (int rowIndex = 1;rowIndex < unmergedRows.size();rowIndex++)
                {
                    int columnIndex;
                    for (columnIndex = 0;columnIndex < offAxis.size();columnIndex++)
                    {
                        if (readTable.apply(unmergedRows.get(0), columnIndex) != readTable.apply(unmergedRows.get(rowIndex), columnIndex))
                        {
                            break;
                        }

                    }
                    if (columnIndex != offAxis.size())
                    {
                        continue;
                    }

                    rowsToMerge.get(rowsToMerge.size() - 1).add(unmergedRows.get(rowIndex));
                    unmergedRows.remove(rowIndex);
                    rowIndex--;
                }
                unmergedRows.remove(0);
            }
        } return rowsToMerge;
    }
    
    private static class DistinctRecursiveAlgorithmProcessor<T> {
        private final Set<T> _alreadyQueuedItems = Collections.synchronizedSet(new HashSet<>());
        private final Queue<T> _items = new LinkedBlockingDeque<>();
        
        public boolean add(T item) {
            if (!_alreadyQueuedItems.add(item)) {
                return false;
            }
            _items.offer(item);
            return true;
        }
        
        public void run(Consumer<T> algorithm) {
            while(_items.size() > 0) {
                int i = _items.size();
                Collection<Integer> integerList = ContiguousSet.create(Range.closedOpen(0, i), DiscreteDomain.integers());
                integerList.stream().parallel().forEach(j -> {
                    T item = _items.poll();
                    algorithm.accept(item);
                });
            }
        }
    }

    private <T> Grid[] computePrimeGrids(ArrayTable<T, T, Set<State<TAssignment>>> reducedAutomataMatrix)  {
        DistinctRecursiveAlgorithmProcessor<Grid> gridsToProcess = new DistinctRecursiveAlgorithmProcessor<>();
        int rowCount = reducedAutomataMatrix.rowKeySet().size();
        int columnCount = reducedAutomataMatrix.columnKeySet().size();
        for (int rowIndex = 0;rowIndex < rowCount;rowIndex++)
        {
            for (int columnIndex = 0;columnIndex < columnCount;columnIndex++)
            {
                //make initial grids which contain only one element
                if (getRAMValue(reducedAutomataMatrix, rowIndex, columnIndex))
                {
                    Grid grid = new Grid(Collections.singleton(rowIndex), Collections.singleton(columnIndex));
                    gridsToProcess.add(grid);
                }
                 
            }
        }
        //then, grow them incrementally, adding them back into the queue
        //or saving them if they cannot be grown
        Set<Grid> results = Collections.synchronizedSet(new HashSet<>());
        gridsToProcess.run((grid) -> {
            boolean isPrime = isPrimeOnAxis(gridsToProcess, grid.rows, grid.columns, (on, off) -> getRAMValue(reducedAutomataMatrix, on, off), Grid::new);
            isPrime &= isPrimeOnAxis(gridsToProcess, grid.columns, grid.rows, (on, off) -> getRAMValue(reducedAutomataMatrix, off, on), (on, off) -> new Grid(off, on));
            //if it's prime, then save it to the results
            if (isPrime)
            {
                results.add(grid);
            }
             
        });
        return results.stream().toArray(Grid[]::new);
    }

    private boolean isPrimeOnAxis(
            DistinctRecursiveAlgorithmProcessor<Grid> gridsToProcess,
            Set<Integer> onAxis, Set<Integer> offAxis,
            BiFunction<Integer, Integer, Boolean> readTable,
            BiFunction<Set<Integer>, Set<Integer>, Grid> makeGrid) {
        boolean isPrime = true;
        {
            //try expanding to other rows
            int comparisonRow = onAxis.iterator().next();
            Set<Integer> keys = Sets.newHashSet(ContiguousSet.create(Range.closedOpen(0, onAxis.size()), DiscreteDomain.integers()));
            keys.removeAll(onAxis);
            for (int testRow : keys)
            {
                boolean canExpand = offAxis.stream().allMatch((columnIndex) -> readTable.apply(testRow, columnIndex) == readTable.apply(comparisonRow, columnIndex));
                if (!canExpand)
                {
                    continue;
                }

                Grid newGrid = makeGrid.apply(Sets.union(onAxis, Collections.singleton(testRow)), offAxis);
                gridsToProcess.add(newGrid);
                isPrime = false;
            }
        }
        return isPrime;
    }

    private static class CoverIterator implements Iterator<Set<Grid>> {

        private final Grid[] primeGrids;
        private final int firstGridIndex;
        private final Map<Grid, Set<Integer>> gridToFlattenedIndicesSet;
        private final Set<Integer> flattenedIndicesWithTrue;
        private final int gridCount;
        private int gridIndex;
        private CoverIterator currentSubIter;

        public CoverIterator(Grid[] primeGrids,
                             int firstGridIndex,
                             Map<Grid, Set<Integer>> gridToFlattenedIndicesSet,
                             Set<Integer> flattenedIndicesWithTrue,
                             int gridCount) {
            this.primeGrids = primeGrids;
            this.firstGridIndex = firstGridIndex;
            this.gridToFlattenedIndicesSet = gridToFlattenedIndicesSet;
            this.flattenedIndicesWithTrue = flattenedIndicesWithTrue;
            this.gridCount = gridCount;
            this.gridIndex = firstGridIndex;
        }

        private Set<Grid> cachedNext = null;

        @Override
        public boolean hasNext() {
            if (cachedNext == null) {
                cachedNext = tryNext();
            }
            return cachedNext != null;
        }

        @Override
        public Set<Grid> next() {
            Set<Grid> next = cachedNext;
            cachedNext = null;
            return next;
        }

        public Set<Grid> tryNext() {
            if (gridCount > primeGrids.length - firstGridIndex || gridIndex >= primeGrids.length) return null;
            Grid primeGrid = primeGrids[gridIndex];
            Set<Integer> remainingIndices = Sets.newHashSet(flattenedIndicesWithTrue);
            remainingIndices.removeAll(gridToFlattenedIndicesSet.get(primeGrid));
            if (gridCount == 1) {
                gridIndex++;
                if (remainingIndices.size() == 0) {
                    return Collections.singleton(primeGrid);
                }
                return next();
            } else {
                if (currentSubIter == null) {
                    currentSubIter = new CoverIterator(primeGrids, gridIndex + 1, gridToFlattenedIndicesSet, remainingIndices, gridCount - 1);
                }
                if (currentSubIter.hasNext()) {
                    return currentSubIter.next();
                } else {
                    currentSubIter = null;
                    gridIndex++;
                    return next();
                }
            }
        }
    }

    private class CoverIterator2<T> implements Iterator<Set<Grid>>, Iterable<Set<Grid>> {

        private final Grid[] primeGrids;
        private final int rowCount;
        private final int columnCount;
        private final Set<Integer> flattenedIndicesWithTrue = new HashSet<>();
        private final Map<Grid, Set<Integer>> gridToIndicesSet;

        public CoverIterator2(ArrayTable<T, T, Set<State<TAssignment>>> ram, Grid[] primeGrids) {
            this.primeGrids = primeGrids;

            rowCount = ram.rowKeySet().size();
            columnCount = ram.columnKeySet().size();

            for (int row = 0;row < rowCount;row++)
            {
                for (int column = 0;column < columnCount;column++)
                {
                    if (getRAMValue(ram, row, column))
                    {
                        flattenedIndicesWithTrue.add(column * rowCount + row);
                    }
                }
            }

            gridToIndicesSet = Stream.of(primeGrids).collect(Collectors.toMap(grid -> grid, grid -> {
                Set<Integer> indices = new HashSet<>();
                for (int row : grid.rows) {
                    indices.addAll(grid.columns.stream().map(col -> col * rowCount + row).collect(Collectors.toList()));
                }
                return indices;
            }));
        }

        private Set<Grid> cachedNext = null;

        @Override
        public boolean hasNext() {
            if (cachedNext == null) {
                cachedNext = tryNext();
            }
            return cachedNext != null;
        }

        @Override
        public Set<Grid> next() {
            Set<Grid> next = cachedNext;
            cachedNext = null;
            return next;
        }

        private int gridCount = 1;
        private Iterator<Set<Grid>> subIter;

        public Set<Grid> tryNext() {
            if (flattenedIndicesWithTrue.size() == 0 || gridCount > primeGrids.length) return null;
            if (subIter == null) {
                subIter = new CoverIterator(primeGrids, 0, gridToIndicesSet, flattenedIndicesWithTrue, gridCount);
            }
            if (subIter.hasNext()) {
                return subIter.next();
            } else {
                subIter = null;
                gridCount++;
                return next();
            }
        }

        @Override
        public Iterator<Set<Grid>> iterator() {
            return this;
        }
    }

    private static Set<Grid> subsetAssignment(Set<Grid> cover, int idx)  {
        return cover.stream().filter(grid -> grid.rows.contains(idx)).collect(Collectors.toSet());
    }

    private Nfa<TAlphabet,Integer> fromIntersectionRule(Nfa<TAlphabet,Integer> reducedDfa, Set<Grid> cover, RefSupport<BiMap<Integer, Grid>> orderedGrids)  {
        List<State<Integer>> orderedReducedDfaStates = reducedDfa._states.stream().sorted((a, b) -> a.getValue().compareTo(b.getValue())).collect(Collectors.toList());
        class Holder {
            int i = 0;
        }
        final Holder h = new Holder();
        BiMap<Integer, Grid> orderedGridsTemp = HashBiMap.create(cover.stream().collect(Collectors.toMap(x -> h.i++, x -> x)));
        Nfa<TAlphabet, Integer> result = new Nfa<>();
        AutoVivifyingBiMap<Integer, State<Integer>> intToResultState = new AutoVivifyingBiMap<>(State::new);
        for (int resultStateIndex = 0;resultStateIndex < orderedGridsTemp.size();resultStateIndex++)
        {
            Grid grid = orderedGridsTemp.get(resultStateIndex);
            State<Integer> resultState = intToResultState.get(resultStateIndex);
            Set<State<Integer>> rows = grid.rows.stream().map(orderedReducedDfaStates::get).collect(Collectors.toSet());
            Set<TAlphabet> symbols = rows.stream()
                    .map(row -> reducedDfa._transitionFunction.row(row).keySet()).reduce(Collections.emptySet(), Sets::intersection);
            for (TAlphabet symbol: symbols)
            {

                result._transitionFunction.get(resultState, symbol).addAll(rows.stream()
                        .map(row -> subsetAssignment(cover, reducedDfa._transitionFunction.get(row, symbol).iterator().next().getValue()))
                        .reduce(Sets::intersection).get().stream().map(orderedGridsTemp.inverse()::get).map(intToResultState::get).collect(Collectors.toSet()));
            }
            if (grid.columns.contains(0))
            {
                result._acceptStates.add(resultState);
            }
             
            if (grid.rows.contains(0))
            {
                result._startStates.add(resultState);
            }
             
        }
        result._states.addAll(intToResultState.values());
        orderedGrids.setValue(orderedGridsTemp);
        return result;
    }

    private <T> boolean gridSetSpansRow(BiMap<Integer, Grid> orderedGrids, Iterable<Integer> gridIndices, ArrayTable<T, T, Set<State<TAssignment>>> ram, int rowIndex)  {
        Set<Integer> neededColumns = ContiguousSet.create(Range.closedOpen(0, ram.columnKeySet().size()), DiscreteDomain.integers()).stream()
                .filter(columnIndex -> getRAMValue(ram, rowIndex, columnIndex)).collect(Collectors.toSet());
        for (int gridIndex : gridIndices)
        {
            Grid grid = orderedGrids.get(gridIndex);
            if (grid.rows.contains(rowIndex))
            {
                neededColumns.removeAll(grid.columns);
                if (neededColumns.size() == 0)
                {
                    break;
                }
                 
            }
             
        }
        return neededColumns.size() == 0;
    }

    private <T> boolean subsetAssignmentIsLegitimate(Nfa<TAlphabet,Integer> intersectionRuleNfa, Nfa<TAlphabet,Integer> minimizedDfa,
                                                     ArrayTable<T, T, Set<State<TAssignment>>> ram, BiMap<Integer, Grid> orderedGrids)  {
        Nfa<TAlphabet,Set<State<Integer>>> intersectionRuleDfa = intersectionRuleNfa.determinize();
        List<State<Set<State<Integer>>>> intersectionRuleDfaOrderedStates = new ArrayList<>(intersectionRuleDfa.getStates());
        State<Set<State<Integer>>> first = intersectionRuleDfa.getStartStates().iterator().next();
        intersectionRuleDfaOrderedStates.remove(first);
        intersectionRuleDfaOrderedStates.add(0, first);
        /*minimized*/
        DistinctRecursiveAlgorithmProcessor<Pair<State<Integer>, State<Set<State<Integer>>>>> processor = new DistinctRecursiveAlgorithmProcessor<>();
        /*intersection rule*/
        State<Integer> first2 = minimizedDfa.getStartStates().iterator().next();
        processor.add(Pair.of(first2, first));
        class Holder {
            boolean isLegitimate = true;
        }
        Holder h = new Holder();
        processor.run((pair) -> {
            if (h.isLegitimate) {
                State<Integer> minimizedDfaState = pair.getKey();
                State<Set<State<Integer>>> intersectionRuleDfaState = pair.getValue();
                Iterable<TAlphabet> inputSymbols = minimizedDfa.getTransitionFunction().row(minimizedDfaState).keySet();
                for (TAlphabet inputSymbol : inputSymbols) {
                    if (intersectionRuleDfa.getTransitionFunction().getOrDefault(intersectionRuleDfaState, inputSymbol).size() == 0) {
                        h.isLegitimate = false;
                        continue;
                    }

                    State<Set<State<Integer>>> nextIntersectionRuleDfaState = intersectionRuleDfa.getTransitionFunction().get(intersectionRuleDfa, inputSymbol).iterator().next();
                    State<Integer> nextMinimizedDfaState = minimizedDfa.getTransitionFunction().get(minimizedDfaState, inputSymbol).iterator().next();
                    if (!intersectionRuleDfa.getAcceptStates().contains(nextIntersectionRuleDfaState) && minimizedDfa.getAcceptStates().contains(nextMinimizedDfaState)) {
                        h.isLegitimate = false;
                    } else if (!gridSetSpansRow(orderedGrids, nextIntersectionRuleDfaState.getValue().stream().map(State::getValue)::iterator, ram, nextMinimizedDfaState.getValue())) {
                        h.isLegitimate = false;
                    } else {
                        processor.add(Pair.of(nextMinimizedDfaState, nextIntersectionRuleDfaState));
                    }
                }
            }

        });
        return h.isLegitimate;
    }

    public <TAssignment2> Nfa<TAlphabet,TAssignment2> reassign(Function<State, TAssignment2> func)  {
        Nfa<TAlphabet, TAssignment2> result = new Nfa<>();
        Map<State<TAssignment>, State<TAssignment2>> stateMapper = new HashMap<>();
        for (State<TAssignment> state : _states) {
            stateMapper.put(state, new State<>(func.apply(state)));
        }
        for (State<TAssignment> state : _transitionFunction.rowKeySet())
        {
            for (TAlphabet inputSymbol : _transitionFunction.row(state).keySet())
            {
                Set<State<TAssignment>> sourcePartialEvaluation1 = _transitionFunction.get(state, inputSymbol);
                Set<State<TAssignment2>> targetPartialEvaluation1 = result._transitionFunction.get(stateMapper.get(state), inputSymbol);
                if (targetPartialEvaluation1 == null) {
                    targetPartialEvaluation1 = new HashSet<>();
                    result._transitionFunction.put(stateMapper.get(state), inputSymbol, targetPartialEvaluation1);
                }
                targetPartialEvaluation1.addAll(sourcePartialEvaluation1.stream().map(stateMapper::get).collect(Collectors.toList()));
            }
        }
        result._startStates.addAll(_startStates.stream().map(stateMapper::get).collect(Collectors.toSet()));
        result._acceptStates.addAll(_acceptStates.stream().map(stateMapper::get).collect(Collectors.toSet()));
        result._states.addAll(stateMapper.values());
        return result;
    }

    /**
    * If there are any nodes that cannot be reached or cannot reach an accept state then remove them
    * iff a transition can be removed without changing the behavior, remove it
    */
    public void removeRedundancies()  {
        boolean redo = false;
        do {
            for (State<TAssignment> state : getStates()) {
                if (getStartStates().stream().map(x -> new RoutesIterator(x, state, null).hasNext()).allMatch(x -> !x)
                        && getAcceptStates().stream().map(x -> new RoutesIterator(state, x, null).hasNext()).allMatch(x -> !x)) {
                    getStates().remove(state);
                    getAcceptStates().remove(state);
                    getStartStates().remove(state);
                    _transitionFunction.rowKeySet().remove(state);
                    for (Transition<TAssignment> transition : getTransitions().stream().filter((x) -> x.ToState == state).collect(Collectors.toSet())) {
                        _transitionFunction.get(transition.FromState, transition.Symbol).remove(state);
                        if (_transitionFunction.get(transition.FromState, transition.Symbol).size() == 0) {
                            _transitionFunction.row(transition.FromState).remove(transition.Symbol);
                        }

                    }
                    redo = true;
                    break;
                }

            }
        } while (redo);
        trimTransitions();
        trimStartAccepts();
    }

    private void trimTransitions()  {
        Nfa<TAlphabet, TAssignment> copy = new Nfa<>(this);
        Nfa<TAlphabet, Integer> thatMinDfa = copy.minimizedDfa();
        for (Transition<TAssignment> transition : getTransitions())
        {
            getTransitionFunction().get(transition.FromState, transition.Symbol).remove(transition.ToState);
            if (!proprocessedIsEquivalent(thatMinDfa))
            {
                getTransitionFunction().get(transition.FromState, transition.Symbol).add(transition.ToState);
            }
        }
    }

    //System.Diagnostics.Debug.WriteLine("Trimmed transition");
    private void trimStartAccepts()  {
        Set<State<TAssignment>> startAccepts = getStartStates().stream().filter((startState) -> getAcceptStates().contains(startState)).collect(Collectors.toSet());
        Set<State<TAssignment>> withFromTransitions = startAccepts.stream()
                .filter((state) -> getTransitionFunction().row(state).entrySet().stream()
                        .anyMatch((symbolListPair) -> symbolListPair.getValue().size() > 0))
                .collect(Collectors.toSet());
        Set<State<TAssignment>> withoutFromTransitions = Sets.difference(startAccepts, withFromTransitions);
        if (withFromTransitions.size() > 0)
        {
            for ( State<TAssignment> withoutFromTransition : withoutFromTransitions)
            {
                getStartStates().remove(withoutFromTransition);
            }
        }
         
    }

    /**
    * Minimize this Nfa using the Kameda-Weiner algorithm [1]
    * 
    *  @return A minimal-state Nfa accepting the same language
    */
    public Nfa<TAlphabet,Integer> minimized()  {
        Nfa<TAlphabet,Set<State<TAssignment>>> determinized;
        RefSupport<Nfa<TAlphabet,Set<State<TAssignment>>>> ref = new RefSupport<>();
        ArrayTable<Set<State<TAssignment>>, Set<State<TAssignment>>, Set<State<TAssignment>>> sm = makeStateMap(ref);
        determinized = ref.getValue();
        Nfa<TAlphabet,Integer> minimizedSubsetConstructionDfa;
        RefSupport<Nfa<TAlphabet,Integer>> ref2 = new RefSupport<>();
        ArrayTable<Set<Integer>, Set<Integer>, Set<State<TAssignment>>> ram = reduceStateMap(sm, determinized, ref2);
        minimizedSubsetConstructionDfa = ref2.getValue();
        Grid[] primeGrids = computePrimeGrids(ram);
        Iterable<Set<Grid>> covers = new CoverIterator2<>(ram, primeGrids);
        for (Set<Grid> cover : covers)
        {
            if (cover.size() >= _states.size() || cover.size() >= determinized.getStates().size())
            {
                break;
            }
             
            BiMap<Integer, Grid> orderedGrids;
            RefSupport<BiMap<Integer, Grid>> ref3 = new RefSupport<>();
            Nfa<TAlphabet,Integer> minNFA = fromIntersectionRule(minimizedSubsetConstructionDfa, cover, ref3);
            orderedGrids = ref3.getValue();
            boolean isLegitimate = subsetAssignmentIsLegitimate(minNFA, minimizedSubsetConstructionDfa, ram, orderedGrids);
            if (isLegitimate)
            {
                minNFA.removeRedundancies();
                return minNFA;
            }
             
        }
        int stateCount = 0;
        if (determinized.getStates().size() <= _states.size())
        {
            AtomicInteger i = new AtomicInteger(stateCount);
            return determinized.reassign((x) -> i.incrementAndGet());
        }
        else
        {
            AtomicInteger i = new AtomicInteger(stateCount);
            return reassign((x) -> i.incrementAndGet());
        } 
    }

    //did not find a smaller Nfa. Return this;
    public static <TAlphabet, TAssignment> Nfa<TAlphabet,TAssignment> union(Iterable<Nfa<TAlphabet,TAssignment>> nfas)  {
        Nfa<TAlphabet, TAssignment> result = new Nfa<>();
        for ( Nfa<TAlphabet, TAssignment> nfa : nfas)
        {
            //don't need to clone the states because they are immutable
            result._startStates.addAll(nfa._startStates);
            result._acceptStates.addAll(nfa._acceptStates);
            for (Table.Cell<State<TAssignment>, TAlphabet, Set<State<TAssignment>>> cell: nfa._transitionFunction.cellSet())
            {
                result._transitionFunction.get(cell.getRowKey(), cell.getColumnKey()).addAll(cell.getValue());
            }
            result._states.addAll(nfa._states);
        }
        return result;
    }

    public Nfa<TAlphabet,Integer> minimizedDfa()  {
        Nfa<TAlphabet,Set<State<TAssignment>>> determinized;
        RefSupport<Nfa<TAlphabet,Set<State<TAssignment>>>> ref = new RefSupport<>();
        ArrayTable<Set<State<TAssignment>>, Set<State<TAssignment>>, Set<State<TAssignment>>> sm = makeStateMap(ref);
        determinized = ref.getValue();
        Nfa<TAlphabet,Integer> minimizedSubsetConstructionDfa;
        RefSupport<Nfa<TAlphabet,Integer>> ref2 = new RefSupport<>();
        reduceStateMap(sm,determinized,ref2);
        minimizedSubsetConstructionDfa = ref2.getValue();
        return minimizedSubsetConstructionDfa;
    }

    public boolean proprocessedIsEquivalent(Nfa<TAlphabet,Integer> thatMinDfa)  {
        Nfa<TAlphabet,Integer> thisMinDfa = minimizedDfa();
        Map<State<Integer>, State<Integer>> stateMap = new HashMap<>();
        DistinctRecursiveAlgorithmProcessor<Pair<State<Integer>, State<Integer>>> processor = new DistinctRecursiveAlgorithmProcessor<>();
        processor.add(Pair.of(thisMinDfa._startStates.iterator().next(), thatMinDfa._startStates.iterator().next()));
        class Holder {
            boolean equivalent = true;
        }
        Holder h = new Holder();
        processor.run((pair) -> {
            //only one start state since it's a min dfa
            if (!h.equivalent) {
                return;
            }

            for (TAlphabet inputSymbol : Sets.union(thisMinDfa._transitionFunction.row(pair.getKey()).keySet(), thatMinDfa._transitionFunction.row(pair.getValue()).keySet())) {
                State<Integer> thisMinDfaNextState = thisMinDfa._transitionFunction.get(pair.getKey(), inputSymbol).stream().findFirst().orElse(null);
                //deterministic, so only one state
                State<Integer> thatMinDfaNextState = thatMinDfa._transitionFunction.get(pair.getValue(), inputSymbol).stream().findFirst().orElse(null);
                if (thatMinDfaNextState == null || thisMinDfaNextState == null) {
                    h.equivalent = false;
                } else {
                    State<Integer> mappedThisMinDfaNextState;
                    if (stateMap.containsKey(thisMinDfaNextState)) {
                        mappedThisMinDfaNextState = stateMap.get(thisMinDfaNextState);
                    } else {
                        processor.add(Pair.of(thisMinDfaNextState, thatMinDfaNextState));
                        mappedThisMinDfaNextState = thatMinDfaNextState;
                        stateMap.put(thisMinDfaNextState, thatMinDfaNextState);
                    }
                    if (thatMinDfaNextState != mappedThisMinDfaNextState) {
                        h.equivalent = false;
                    }

                    if (thatMinDfa.getAcceptStates().contains(thatMinDfaNextState) != thisMinDfa.getAcceptStates().contains(thisMinDfaNextState)) {
                        h.equivalent = false;
                    }

                }
            }
        });
        return h.equivalent;
    }

    public Nfa<TAlphabet,Integer> intersect(List<Nfa<TAlphabet,TAssignment>> nfas)  {
        List<Nfa<TAlphabet,Integer>> minDets = nfas.stream().map(Nfa::minimizedDfa).collect(Collectors.toList());
        Nfa<TAlphabet,Integer> singleTransitionNFA = Nfa.union(minDets);
        int stateCount = 0;
        AtomicInteger i = new AtomicInteger(stateCount);
        Map<Set<State<Integer>>, State<Integer>> resultStates = new HashMap<>();
       DistinctRecursiveAlgorithmProcessor<Set<State<Integer>>> processor = new DistinctRecursiveAlgorithmProcessor<>();
        Set<State<Integer>> startStateSet = new HashSet<>(minDets.stream().map((x) -> x._startStates.iterator().next()).collect(Collectors.toSet()));
        Nfa<TAlphabet, Integer> result = new Nfa<>();
        Set<State<Integer>> acceptStates = Collections.synchronizedSet(new HashSet<>());
        processor.add(startStateSet);
        processor.run((stateSet) -> {
            State<Integer> fromState;
            synchronized (resultStates) {
                fromState = resultStates.get(stateSet);
                if (fromState == null) {
                    fromState = new State<>(i.incrementAndGet());
                    resultStates.put(stateSet, fromState);
                }
            }
            if (singleTransitionNFA._acceptStates.containsAll(stateSet)) {
                acceptStates.add(fromState);
            }

            Set<TAlphabet> fromSymbols = stateSet.stream().map(state -> singleTransitionNFA._transitionFunction.row(state).keySet()).reduce(Sets::intersection).get();
            for (TAlphabet fromSymbol : fromSymbols) {
                Set<State<Integer>> nextStateSet = stateSet.stream().map(state -> singleTransitionNFA._transitionFunction.get(state, fromSymbol).iterator().next()).collect(Collectors.toSet());
                State<Integer> toState;
                synchronized (resultStates) {
                    toState = resultStates.get(nextStateSet);
                    if (toState == null) {
                        toState = new State<>(i.incrementAndGet());
                        resultStates.put(nextStateSet, toState);
                    }
                }
                processor.add(nextStateSet);
                result._transitionFunction.get(fromState, fromSymbol).add(toState);
            }
        });
        result._states.addAll(resultStates.values());
        result._startStates.add(resultStates.get(startStateSet));
        result._acceptStates.addAll(acceptStates);
        return result;
    }

    public boolean contains(Nfa<TAlphabet,TAssignment> that)  {
        return intersect(Arrays.asList(this, that)).isEquivalent(that);
    }

    public <TAssignment2> boolean isEquivalent(Nfa<TAlphabet, TAssignment2> that) {
        Nfa<TAlphabet, Integer> thatMinDfa = that.minimizedDfa();
        return proprocessedIsEquivalent(thatMinDfa);
    }

    private class RoutesIterator implements Iterator<Iterable<State<TAssignment>>> {

        private final State<TAssignment> toState;
        private final Set<State<TAssignment>> ignoredStates;
        private final Iterator<State<TAssignment>> stateIterator;
        private State<TAssignment> currState = null;
        private boolean atFirst = true;
        private Iterator<Iterable<State<TAssignment>>> subIter = null;

        public RoutesIterator(State<TAssignment> fromState, State<TAssignment> toState, Set<State<TAssignment>> ignoredStates) {
            this.toState = toState;
            this.ignoredStates = ignoredStates == null ? Collections.emptySet() : ignoredStates;
            stateIterator = _transitionFunction.row(fromState).entrySet().stream()
                    .flatMap(e -> e.getValue().stream()).distinct().filter((s) -> !this.ignoredStates.contains(s)).iterator();
        }

        @Override
        public boolean hasNext() {
            return stateIterator.hasNext() || currState != null;
        }

        @Override
        public Iterable<State<TAssignment>> next() {
            if (currState == null) {
                currState = stateIterator.next();
            }
            if  (atFirst) {
                if (currState == toState) {
                    ignoredStates.add(currState);
                    atFirst = false;
                    return Collections.singleton(toState);
                } else {
                    atFirst = false;
                    return next();
                }
            } else {
                if (subIter == null) {
                    subIter = new RoutesIterator(currState, toState, ignoredStates);
                    return Collections.singleton(currState);
                }
                if (subIter.hasNext()) {
                    return subIter.next();
                } else {
                    subIter = null;
                    ignoredStates.remove(currState);
                    atFirst = true;
                    return next();
                }
            }
        }
    }

    private static class Grid   
    {
        public final Set<Integer> columns;
        public final Set<Integer> rows;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Grid grid = (Grid) o;

            return columns.equals(grid.columns) && rows.equals(grid.rows);

        }

        @Override
        public int hashCode() {
            int result = columns.hashCode();
            result = 31 * result + rows.hashCode();
            return result;
        }

        public Grid(Iterable<Integer> rows, Iterable<Integer> columns)  {
            this.rows = ImmutableSet.copyOf(rows);
            this.columns = ImmutableSet.copyOf(columns);
        }

    }

    /**
    * A State of an Nfa
    */
    public static class State<TAssignment>
    {
        private final TAssignment _value;
        public State(TAssignment value) {
            _value = value;
        }

        public TAssignment getValue()  {
            return _value;
        }

        @Override
        public String toString() {
            return "State{" +
                    "_value=" + _value +
                    '}';
        }
    }

    public class Transition<T>
    {
        public State<T> FromState;
        public TAlphabet Symbol;
        public State<T> ToState;

        public Transition(State<T> from, TAlphabet symbol, State<T> to) {
            this.FromState = from;
            this.Symbol = symbol;
            this.ToState = to;
        }
    }

}


/**
* A Nondeterministic Finite Automaton (∪-Nfa)
* The domain of the transition function is S x TAlphabet, where S is the set of states.
* Creates a new Nfa that has only one transition for each input symbol for each state - i.e. it is deterministic
* 
*  @return The new DFA
* Creates a new Nfa that recognizes the reversed language
* 
*  @return The new Nfa
* Creates a state map (SM) as described in [1]
* 
*  @return
*/
//construct an elementary automata matrix (EAM) [1]
//determine which rows can be merged
//determine which columns can be merged
//make initial grids which contain only one element
//then, grow them incrementally, adding them back into the queue
//or saving them if they cannot be grown
//try expanding to other rows
//try expanding to other columns
//if it's prime, then save it to the results
//can't reach gridCount == 0 before the recursion runs out of grids
/*minimized*/
/*intersection rule*/
/**
* Minimize this Nfa using the Kameda-Weiner algorithm [1]
* 
*  @return A minimal-state Nfa accepting the same language
*/
//don't need to clone the states because they are immutable
//only one start state since it's a min dfa
//deterministic, so only one state
//it will always be either 0 or 1
//get a listing of all transitions that need to be considered from any state
//since each determinized machine can only have one active state
//and we only do transitions that have arrows from every active state
//if no active states are not accept states
//then all source machines are in an accept state
//only permit transitions with arrows from every active state
//generate new configurations, and add them to the processor
//result.States was previously empty
//the one and only
//result.AcceptStates was previously empty
//subtract one NFA from another
//possible because NFAs are closed under negation and intersection
//public bool Contains(Nfa<TAlphabet> that)
//{
//    return Intersect(new[] { this, that }).IsEquivalent(that);
//}
/**
* Inserts an Nfa 'require' at the 'at' state
* Any transitions leaving 'at' are removed and stored in outgoingTransitions
* Any start states of 'require' become synonymous with at
* Any accept states of 'require' have outgoingTransitions added to them
* 
*  @param at The state to insert the Nfa at
*  @param require The Nfa to insert
*/
//Store copies of all the transitions leaving 'at'
//Add all of 'require's states to storage, except start and accept states
//Simultaneously, create a map from 'require's states to storage's states
//which for the most part is identity, but start states map to 'at'
//Also, make a list of the mapped accept states, which we'll use later
//now that the map is complete, copy the transitions from 'require' to storage
//using the stateMap to make necessary alterations
//lastly, hook up the mappedAcceptStates using the saved outgoingTransitions
//one more thing, 'at' can't be an accept state anymore
//unless one or more of 'require's start states is an accept state
/**
* A State of an Nfa
* 
* An immutable set of States that can be quickly tested for inequality
*/
/*
 * References:
 * [1] Kameda, T. ; IEEE ; Weiner, Peter
 *      "On the State Minimization of Nondeterministic Finite Automata"
 *      Computers, IEEE Transactions on  (Volume:C-19 ,  Issue: 7 )
 */