package org.kframework.kore.compile;

import org.kframework.TopologicalSort;
import org.kframework.attributes.Att;
import org.kframework.builtin.BooleanUtils;
import org.kframework.builtin.Sorts;
import org.kframework.definition.Context;
import org.kframework.definition.Module;
import org.kframework.definition.Rule;
import org.kframework.definition.Sentence;
import org.kframework.kil.Attribute;
import org.kframework.kore.Assoc;
import org.kframework.kore.K;
import org.kframework.kore.KApply;
import org.kframework.kore.KLabel;
import org.kframework.kore.KRewrite;
import org.kframework.kore.KVariable;
import org.kframework.utils.errorsystem.KEMException;
import scala.Tuple2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static org.kframework.Collections.*;
import static org.kframework.definition.Constructors.*;
import static org.kframework.kore.KORE.*;

public class ConvertDataStructureToLookup {


    private Set<KApply> state = new HashSet<>();
    private Set<KVariable> vars = new HashSet<>();

    void reset() {
        state.clear();
        vars.clear();
    }

    private final Module m;

    public ConvertDataStructureToLookup(Module m) {
        this.m = m;
    }

    private Rule convert(Rule rule) {
        reset();
        gatherVars(rule.body(), vars);
        gatherVars(rule.requires(), vars);
        gatherVars(rule.ensures(), vars);
        K body = transform(rule.body());
        return Rule(
                body,
                addSideCondition(rule.requires()),
                rule.ensures(),
                rule.att());
    }

    private Context convert(Context context) {
        reset();
        gatherVars(context.body(), vars);
        gatherVars(context.requires(), vars);
        K body = transform(context.body());
        return new Context(
                body,
                addSideCondition(context.requires()),
                context.att());
    }

    void gatherVars(K term, Set<KVariable> vars) {
        new VisitKORE() {
            @Override
            public Void apply(KVariable v) {
                vars.add(v);
                return super.apply(v);
            }
        }.apply(term);
    }

    K addSideCondition(K requires) {
        Optional<KApply> sideCondition = getSortedLookups().reduce(BooleanUtils::and);
        if (!sideCondition.isPresent()) {
            return requires;
        } else if (requires.equals(BooleanUtils.TRUE) && sideCondition.isPresent()) {
            return sideCondition.get();
        } else {
            return BooleanUtils.and(requires, sideCondition.get());
        }
    }

    private Stream<KApply> getSortedLookups() {
        List<Tuple2<KApply, KApply>> edges = new ArrayList<>();
        for (KApply k1 : state) {
            Set<KVariable> rhsVars = new HashSet<>();
            if (k1.klabel().name().equals("_in_")) {
                continue;
            }
            gatherVars(k1.klist().items().get(1), rhsVars);
            for (KApply k2 : state) {
                Set<KVariable> lhsVars = new HashSet<>();
                if (k2.klabel().name().equals("_in_")) {
                    continue;
                }
                gatherVars(k2.klist().items().get(0), lhsVars);
                for (KVariable var : rhsVars) {
                    if (lhsVars.contains(var)) {
                        edges.add(Tuple2.apply(k2, k1));
                        break;
                    }
                }
            }
        };
        List<KApply> topologicalSorted = mutable(TopologicalSort.tsort(immutable(edges)).toList());
        return state.stream().sorted((k1, k2) -> (topologicalSorted.indexOf(k1) - topologicalSorted.indexOf(k2)));
    }

    private int counter = 0;
    KVariable newDotVariable() {
        KVariable newLabel;
        do {
            newLabel = KVariable("_" + (counter++));
        } while (vars.contains(newLabel));
        vars.add(newLabel);
        return newLabel;
    }

    private

    K transform(K term) {
        return new TransformKORE() {
            @Override
            public K apply(KApply k) {
                if (m.collectionFor().contains(k.klabel())) {
                    KLabel collectionLabel = m.collectionFor().apply(k.klabel());
                    Att att = m.attributesFor().apply(collectionLabel);
                    //assumed assoc
                    KApply left = (KApply) RewriteToTop.toLeft(k);
                    List<K> components = Assoc.flatten(collectionLabel, Collections.singletonList(left), m);
                    if (att.contains(Attribute.COMMUTATIVE_KEY)) {
                        if (att.contains(Attribute.IDEMPOTENT_KEY)) {
                            // Set
                            if (rhsOf == null) {
                                //left hand side
                                KVariable frame = null;
                                Set<K> elements = new LinkedHashSet<>();
                                for (K component : components) {
                                    if (component instanceof KVariable) {
                                        if (frame != null) {
                                            throw KEMException.internalError("Unsupported associative matching on Set. Found variables " + component + " and " + frame, k);
                                        }
                                        frame = (KVariable) component;
                                    } else if (component instanceof KApply) {
                                        KApply kapp = (KApply) component;
                                        if (kapp.klabel().equals(KLabel(m.attributesFor().apply(k.klabel()).<String>get("element").get()))) {
                                            K stack = lhsOf;
                                            lhsOf = kapp;
                                            elements.add(super.apply(kapp));
                                            lhsOf = stack;
                                        } else {
                                            throw KEMException.internalError("Unexpected term in set, not a set element.", kapp);
                                        }
                                    }
                                }
                                KVariable set = newDotVariable();
                                for (K element : elements) {
                                    Set<KVariable> vars = new HashSet<>();
                                    gatherVars(element, vars);
                                    if (vars.isEmpty()) {
                                        state.add(KApply(KLabel("_in_"), element, set));
                                    } else {
                                        //set choice
                                        state.add(KApply(KLabel("#choice"), element, set));
                                    }
                                }
                                if (frame != null) {
                                    K removeElements = elements.stream().reduce(KApply(KLabel(".Set")), (k1, k2) -> KApply(KLabel("_Set_"), k1, k2));
                                    state.add(KApply(KLabel("#match"), frame, KApply(KLabel("_-Set_"), set, removeElements)));
                                }
                                if (lhsOf == null) {
                                    return KRewrite(set, RewriteToTop.toRight(k));
                                } else {
                                    return set;
                                }
                            } else {
                                return super.apply(k);
                            }
                        } else {
                            //TODO(dwightguth): differentiate Map and Bag
                            if (rhsOf == null) {
                                //left hand side
                                KVariable frame = null;
                                Map<K, K> elements = new LinkedHashMap<>();
                                for (K component : components) {
                                    if (component instanceof KVariable) {
                                        if (frame != null) {
                                            throw KEMException.internalError("Unsupported associative matching on Map. Found variables " + component + " and " + frame, k);
                                        }
                                        frame = (KVariable) component;
                                    } else if (component instanceof KApply) {
                                        KApply kapp = (KApply) component;
                                        if (kapp.klabel().equals(KLabel(m.attributesFor().apply(k.klabel()).<String>get("element").get()))) {
                                            if (kapp.klist().size() != 2) {
                                                throw KEMException.internalError("Unexpected arity of map element: " + kapp.klist().size(), kapp);
                                            }
                                            K stack = lhsOf;
                                            lhsOf = kapp;
                                            elements.put(super.apply(kapp.klist().items().get(0)), super.apply(kapp.klist().items().get(1)));
                                            lhsOf = stack;
                                        } else {
                                            throw KEMException.internalError("Unexpected term in map, not a map element.", kapp);
                                        }
                                    }
                                }
                                KVariable map = newDotVariable();
                                if (frame != null) {
                                    state.add(KApply(KLabel("#match"), frame, elements.keySet().stream().reduce(map, (a1, a2) -> KApply(KLabel("_[_<-undef]"), a1, a2))));
                                }
                                for (Map.Entry<K, K> element : elements.entrySet()) {
                                    state.add(KApply(KLabel("#match"), element.getValue(), KApply(KLabel("Map:lookup"), map, element.getKey())));
                                }
                                if (lhsOf == null) {
                                    return KRewrite(map, RewriteToTop.toRight(k));
                                } else {
                                    return map;
                                }
                            } else {
                                return super.apply(k);
                            }
                        }
                    } else {
                        // List
                        if (rhsOf == null) {
                            //left hand side
                            KVariable frame = null;
                            List<K> elementsLeft = new ArrayList<K>();
                            List<K> elementsRight = new ArrayList<K>();
                            boolean isRight = false;
                            for (K component : components) {
                                if (component instanceof KVariable) {
                                    if (frame != null) {
                                        throw KEMException.internalError("Unsupported associative matching on List. Found variables " + component + " and " + frame, k);
                                    }
                                    frame = (KVariable) component;
                                    isRight = true;
                                } else if (component instanceof KApply) {
                                    KApply kapp = (KApply) component;
                                    if (kapp.klabel().equals(KLabel(m.attributesFor().apply(k.klabel()).<String>get("element").get()))) {
                                        if (kapp.klist().size() != 1) {
                                            throw KEMException.internalError("Unexpected arity of list element: " + kapp.klist().size(), kapp);
                                        }
                                        K stack = lhsOf;
                                        lhsOf = kapp;
                                        (isRight ? elementsRight : elementsLeft).add(super.apply(kapp.klist().items().get(0)));
                                        lhsOf = stack;
                                    } else {
                                        throw KEMException.internalError("Unexpected term in list, not a list element.", kapp);
                                    }
                                }
                            }
                            KVariable list = newDotVariable();
                            if (frame != null) {
                                state.add(KApply(KLabel("#match"), frame, KApply(KLabel("List:range"), list,
                                        KToken(Integer.toString(elementsLeft.size()), Sorts.Int()),
                                        KToken(Integer.toString(elementsRight.size()), Sorts.Int()))));
                            }
                            for (int i = 0; i < elementsLeft.size(); i++) {
                                K element = elementsLeft.get(i);
                                state.add(KApply(KLabel("#match"), element, KApply(KLabel("List:get"), list, KToken(Integer.toString(i), Sorts.Int()))));
                            }
                            for (int i = 0; i < elementsRight.size(); i++) {
                                K element = elementsRight.get(i);
                                state.add(KApply(KLabel("#match"), element, KApply(KLabel("List:get"), list, KToken(Integer.toString(-i - 1), Sorts.Int()))));
                            }
                            if (lhsOf == null) {
                                return KRewrite(list, RewriteToTop.toRight(k));
                            } else {
                                return list;
                            }
                        } else {
                            return super.apply(k);
                        }
                    }
                } else {
                    return super.apply(k);
                }
            }

            private K lhsOf;
            private K rhsOf;

            @Override
            public K apply(KRewrite k) {
                lhsOf = k;
                K l = apply(k.left());
                lhsOf = null;
                rhsOf = k;
                K r = apply(k.right());
                rhsOf = null;
                if (l != k.left() || r != k.right()) {
                    return KRewrite(l, r, k.att());
                } else {
                    return k;
                }
            }
        }.apply(term);
    }


    public synchronized Sentence convert(Sentence s) {
        if (s instanceof Rule) {
            return convert((Rule) s);
        } else if (s instanceof Context) {
            return convert((Context) s);
        } else {
            return s;
        }
    }
}
