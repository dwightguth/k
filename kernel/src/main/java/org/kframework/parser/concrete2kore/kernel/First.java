package org.kframework.parser.concrete2kore.kernel;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.SetMultimap;
import dk.brics.automaton.Automaton;
import dk.brics.automaton.BasicAutomata;
import dk.brics.automaton.RegExp;
import dk.brics.automaton.Transition;
import org.kframework.parser.concrete2kore.kernel.Grammar.State;

import java.util.List;

/**
 * Created by dwightguth on 4/17/15.
 */
public class First {

    private final Nullability nullability;

    private final SetMultimap<State, AutomatonReference> first = HashMultimap.create();

    private static class AutomatonReference {
        public final Automaton value;

        AutomatonReference(Automaton value) {
            this.value = value;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            AutomatonReference that = (AutomatonReference) o;

            return value == that.value;

        }

        @Override
        public int hashCode() {
            return System.identityHashCode(value);
        }
    }

    /**
     * First  of a state is based on the following two implications:
     * A. EntryState state => entryNullable(state)
     * B. entryNullable(state) && childNullable(state) => entryNullable(state.next)
     * Where childNullable(state) is true when it is possible to get from the
     * start of the state to the end of the state without consuming input.
     *
     * The following algorithm is a least fixed-point algorithm for solving those implications.
     * mark(state) is called when we discover an implication implying entryNullable(state). We can
     * discover this implication one of three ways:
     *
     * 1. A state is an entry state (see rule A)
     * 2. The entryNullable(state) in rule B becomes true (in which case we check childNullable).)
     * 3. The childNullable(state) in rule B becomes true (in which case we check entryNullable(state).)
     * (ChildNullable(state) becomes true when an exit state becomes entryNullable.)
     * @param grammar the grammar object.
     * @return A set with all the NonTerminals that can become entryNullable.
     */

    private Automaton nullableLookahead = BasicAutomata.makeAnyString();

    public First(List<State> allStates, Nullability nullability) {
        this.nullability = nullability;

        boolean changed;
        do {
            changed = false;
            for (State state : allStates) {
                if (state instanceof Grammar.RegExState) {
                    changed |= first.put(state, new AutomatonReference(((Grammar.RegExState) state).rawPattern));
                    if (nullability.isNullable(state)) {
                        changed |= first.put(state, new AutomatonReference(nullableLookahead));
                    }
                }
                if (state instanceof Grammar.NonTerminalState) {
                    changed |= first.putAll(state, first.get(((Grammar.NonTerminalState) state).child.entryState));
                }
                if (nullability.isEntryNullable(state)) {
                    changed |= first.putAll(state.nt.entryState, first.get(state));
                }
            }
        } while (changed);
    }

    public String flattenToSingleChar(Automaton a) {
        StringBuilder sb = new StringBuilder();
        for (Transition t : a.getInitialState().getTransitions()) {
            sb.append("\\");
            sb.append(t.getMin());
            sb.append("-\\");
            sb.append(t.getMax());
        }
        return sb.toString();
    }

    public Automaton getFirst(State state) {
        return new RegExp("[" + (first.get(state).stream().map(r -> flattenToSingleChar(r.value)).reduce("", (a, b) -> a + b)) + "]").toAutomaton();
    }

}
