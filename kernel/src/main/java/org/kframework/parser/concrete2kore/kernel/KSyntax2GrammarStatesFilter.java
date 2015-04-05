// Copyright (c) 2014-2015 K Team. All Rights Reserved.
package org.kframework.parser.concrete2kore.kernel;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Table;
import edu.uci.ics.jung.algorithms.shortestpath.DijkstraShortestPath;
import edu.uci.ics.jung.graph.DirectedGraph;
import edu.uci.ics.jung.graph.DirectedSparseGraph;
import org.kframework.definition.Module;
import org.kframework.definition.Production;
import org.kframework.definition.ProductionItem;
import org.kframework.definition.RegexTerminal;
import org.kframework.definition.Terminal;
import org.kframework.kil.loader.Constants;
import org.kframework.kore.Sort;
import org.kframework.parser.Alphabet;
import org.kframework.parser.concrete2kore.kernel.Grammar.NextableState;
import org.kframework.parser.concrete2kore.kernel.Grammar.NonTerminal;
import org.kframework.parser.concrete2kore.kernel.Grammar.RuleState;
import org.kframework.parser.concrete2kore.kernel.Nfa.State;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.kframework.Collections.iterable;
import static org.kframework.Collections.stream;

/**
 * A simple visitor that goes through every accessible production and creates the NFA states for the
 * parser. First step is to create a NonTerminal object for every declared syntactic sort. These
 * will be referenced each time a NonTerminalState is created.
 */
public class KSyntax2GrammarStatesFilter {

    public static Grammar getGrammar(Module module) {
        Grammar grammar = new Grammar();
        Set<String> rejects = new HashSet<>();
        // create a NonTerminal for every declared sort
        for (Sort sort : iterable(module.definedSorts())) {
            grammar.add(new NonTerminal(sort.name()));
        }

        stream(module.productions()).forEach(p -> collectRejects(p, rejects));
        String rejectPattern = mkString(rejects, p -> "(" + p + ")", "|");
        stream(module.productions()).collect(Collectors.groupingBy(Production::sort)).entrySet().parallelStream().forEach(e -> processProductions(e.getKey(), e.getValue(), grammar, rejectPattern));

        grammar.addWhiteSpace();
        grammar.compile();
        return grammar;
    }

    static public <E> String mkString(Iterable<E> list, Function<E, String> stringify, String delimiter) {
        int i = 0;
        StringBuilder s = new StringBuilder();
        for (E e : list) {
            if (i != 0) {
                s.append(delimiter);
            }
            s.append(stringify.apply(e));
            i++;
        }
        return s.toString();
    }

    private static void collectRejects(Production prd, Set<String> rejects) {
        for (ProductionItem prdItem : iterable(prd.items())) {
            String pattern = "";
            if (prdItem instanceof Terminal) {
                if (!((Terminal) prdItem).value().equals("")) {
                    pattern = Pattern.quote(((Terminal) prdItem).value());
                    rejects.add(pattern);
                }
            } else if (prdItem instanceof RegexTerminal) {
                pattern = ((RegexTerminal) prdItem).regex();
                if (!pattern.equals("") && !prd.att().contains("token"))
                    rejects.add(pattern);
            }
        }
    }

    public static void processProductions(Sort sort, List<Production> prods, Grammar grammar, String rejectPattern) {
        Nfa<Alphabet, ProductionItem> unminimizedNfa = new Nfa<>();
        State<ProductionItem> startState = new State<>(null);
        unminimizedNfa.getStartStates().add(startState);
        unminimizedNfa.getStates().add(startState);
//        State<ProductionItem> exitState = new State<>(null);
//        unminimizedNfa.getStates().add(exitState);
//        State<ProductionItem> whitespace2 = new State<>(lnull);
//        unminimizedNfa.getStates().add(whitespace2);
//        unminimizedNfa.getTransitionFunction().putElement(startState, new org.kframework.parser.Terminal(Grammar.patternString, true), whitespace2);
        for (Production prd : prods) {
            if (prd.att().contains("notInPrograms") || prd.att().contains("reject"))
                continue;

            State<ProductionItem> previous = startState;

            // all types of production follow pretty much the same pattern
            // previous = entryState
            // loop: add a new State to the 'previous' state; update 'previous' state

            // just a normal production with Terminals and Sort alternations
            // this will create a labeled KApp with the same arity as the
            // production
            int i = 1;
            for (ProductionItem prdItem : iterable(prd.items())) {
                State<ProductionItem> nextState = new State<>(prdItem);
                i++;
                if (prdItem instanceof Terminal) {
                    Terminal terminal = (Terminal) prdItem;
                    String p = Pattern.quote(terminal.value());
                    unminimizedNfa.getStates().add(nextState);
                    unminimizedNfa.getTransitionFunction().putElement(previous, new org.kframework.parser.Terminal(p, false), nextState);
                    previous = nextState;
//                    State<ProductionItem> whitespace = new State<>(null);
//                    unminimizedNfa.getStates().add(whitespace);
//                    unminimizedNfa.getTransitionFunction().putElement(pstate, new org.kframework.parser.Terminal(Grammar.patternString, true), whitespace);
//                    previous = whitespace;
                } else if (prdItem instanceof org.kframework.definition.NonTerminal) {
                    org.kframework.definition.NonTerminal srt = (org.kframework.definition.NonTerminal) prdItem;
                    String name = srt.sort().name();
                    unminimizedNfa.getStates().add(nextState);
                    unminimizedNfa.getTransitionFunction().putElement(previous, new org.kframework.parser.NonTerminal(name), nextState);
                    previous = nextState;
                } else if (prdItem instanceof RegexTerminal) {
                    RegexTerminal lx = (RegexTerminal) prdItem;
                    unminimizedNfa.getStates().add(nextState);
                    unminimizedNfa.getTransitionFunction().putElement(previous, new org.kframework.parser.Terminal(lx.regex(), false), nextState);
                    previous = nextState;
//                    State<ProductionItem> whitespace = new State<>(null);
//                    unminimizedNfa.getStates().add(whitespace);
//                    unminimizedNfa.getTransitionFunction().putElement(pstate, new org.kframework.parser.Terminal(Grammar.patternString, true), whitespace);
//                    previous = whitespace;
                } else {
                    assert false : "Didn't expect this ProductionItem type: "
                            + prdItem.getClass().getName();
                }
            }
            unminimizedNfa.getAcceptStates().add(previous);
        }
        //unminimizedNfa.getAcceptStates().add(exitState);
        Nfa<Alphabet, Integer> minNfa = unminimizedNfa.minimized();
        addNfaToGrammar(sort, prods, grammar, minNfa, rejectPattern);
    }

    private static void addNfaToGrammar(Sort sort, List<Production> prods, Grammar grammar, Nfa<Alphabet, Integer> minNfa, String rejectPattern) {
        NonTerminal nt = grammar.get(sort.name());
        assert nt != null : "expected to find nonterminal in grammar";
        Table<State<Integer>, Alphabet, List<NextableState>> grammarStates = HashBasedTable.create();
        Map<List<Alphabet>, List<Production>> productionMap = getProductionMap(prods);
        for (Table.Cell<State<Integer>, Alphabet, Set<State<Integer>>> cell : minNfa.getTransitionFunction().cellSet()) {
            List<NextableState> states;
            if (cell.getColumnKey() instanceof org.kframework.parser.NonTerminal) {
                String name = ((org.kframework.parser.NonTerminal) cell.getColumnKey()).sort();
                states = Collections.singletonList(new Grammar.NonTerminalState(sort.name() + " := " + name, nt, grammar.get(name), false));
            } else if (cell.getColumnKey() instanceof org.kframework.parser.Terminal) {
                String p = ((org.kframework.parser.Terminal) cell.getColumnKey()).regex();
                Grammar.RegExState state = new Grammar.RegExState(sort.name() + ": " + p, nt, Pattern.compile(p));
                RuleState delState = new RuleState("DelTerminalRS", nt, new Rule.DeleteRule(1, true));
                state.next.add(delState);
                states = Arrays.asList(state, delState);
            } else {
                throw new AssertionError("unexpected alphabet type");
            }
            grammarStates.put(cell.getRowKey(), cell.getColumnKey(), states);
        }
        Map<Production, NextableState> exitStateFor = new HashMap<>();
        SetMultimap<State<Integer>, Production> exitStateMapping = computeMapOfExitStates(minNfa, prods, rejectPattern);
        Iterable<Production> prds = minNfa.getAcceptStates().stream().flatMap(state -> exitStateMapping.get(state).stream())::iterator;
        for (Production prd : prds) {
            RuleState addLabel = new RuleState("AddLabelRS", nt, new Rule.WrapLabelRule(prd, getRejectPattern(prd, rejectPattern)));
            exitStateFor.put(prd, addLabel);
        }
        for (Nfa<Alphabet, Integer>.Transition<Integer> transition : minNfa.getTransitions()) {
            List<NextableState> states = grammarStates.get(transition.FromState, transition.Symbol);
            states.get(states.size() - 1).next.addAll(grammarStates.row(transition.ToState).values().stream().map(e -> e.get(0)).collect(Collectors.toSet()));
            if (minNfa.getAcceptStates().contains(transition.ToState)) {
                Set<Production> productionsToConstruct = exitStateMapping.get(transition.ToState);
                for (Production prd : productionsToConstruct) {
                    NextableState previous = states.get(states.size() - 1);
    //                    Pattern pattern = null;

                    previous.next.add(exitStateFor.get(prd));
                }
            }
        }
        for (State<Integer> state : minNfa.getStartStates()) {
            nt.entryState.next.addAll(grammarStates.row(state).values().stream().map(s -> s.get(0)).collect(Collectors.toSet()));
        }
    }

    private static SetMultimap<State<Integer>, Production> computeMapOfExitStates(Nfa<Alphabet, Integer> minNfa, List<Production> prods, String rejectPattern) {
        assert minNfa.getStartStates().size() == 1 : "unexpected multiple start states in NFA";
        Map<List<Alphabet>, List<Production>> productionsByString = getProductionMap(prods);
        class Edge {
            Alphabet label;

            public Edge(Alphabet label) {
                this.label = label;
            }

            @Override
            public String toString() {
                return "Edge{" +
                        "label=" + label +
                        '}';
            }
        }
        DirectedGraph<State<Integer>, Edge> nfa = new DirectedSparseGraph<>();
        for (State<Integer> state : minNfa.getStates()) {
            nfa.addVertex(state);
        }
        for (Nfa<Alphabet, Integer>.Transition<Integer> transition : minNfa.getTransitions()) {
            nfa.addEdge(new Edge(transition.Symbol), transition.FromState, transition.ToState);
        }
        DijkstraShortestPath<State<Integer>, Edge> shortestPath = new DijkstraShortestPath<>(nfa);
        SetMultimap<State<Integer>, Production> result = HashMultimap.create();
        for (State<Integer> state : minNfa.getAcceptStates()) {
            List<Alphabet> path = shortestPath.getPath(minNfa.getStartStates().iterator().next(), state).stream().map(e -> e.label)
                    .filter(a -> !(a instanceof org.kframework.parser.Terminal) || !((org.kframework.parser.Terminal) a).isLayout()).collect(Collectors.toList());
            result.putAll(state, productionsByString.get(path));
        }
        return result;
    }

    private static Map<List<Alphabet>, List<Production>> getProductionMap(List<Production> prods) {
        return prods.stream().collect(Collectors.groupingBy(p -> stream(p.items()).map(item -> {
                    if (item instanceof org.kframework.definition.NonTerminal) {
                        return new org.kframework.parser.NonTerminal(((org.kframework.definition.NonTerminal) item).sort().name());
                    } else if (item instanceof Terminal) {
                        return new org.kframework.parser.Terminal(Pattern.quote(((Terminal) item).value()), false);
                    } else if (item instanceof RegexTerminal) {
                        return new org.kframework.parser.Terminal(((RegexTerminal) item).regex(), false);
                    } else {
                        throw new AssertionError("unexpected production item type");
                    }
                }).collect(Collectors.toList())));
    }

    private static Pattern getRejectPattern(Production prd, String rejectPattern) {
        String pattern = "";
        if (prd.att().contains("token")) {
            //TODO: calculate reject list
            if (prd.att().contains(Constants.AUTOREJECT) && prd.att().contains(Constants.REJECT2))
                pattern = "(" + prd.att().get(Constants.REJECT2).get().toString() + ")|(" + rejectPattern + ")";
            else if (prd.att().contains(Constants.AUTOREJECT))
                pattern = rejectPattern;
            else if (prd.att().contains(Constants.REJECT2))
                pattern = prd.att().get(Constants.REJECT2).get().toString();
        }
        return Pattern.compile(pattern);
    }
}