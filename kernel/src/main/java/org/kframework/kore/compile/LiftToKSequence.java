// Copyright (c) 2015 K Team. All Rights Reserved.
package org.kframework.kore.compile;

import org.kframework.builtin.Sorts;
import org.kframework.definition.Context;
import org.kframework.definition.Module;
import org.kframework.definition.Rule;
import org.kframework.definition.Sentence;
import org.kframework.kore.K;
import org.kframework.kore.KApply;
import org.kframework.kore.KSequence;
import org.kframework.kore.KVariable;
import org.kframework.kore.Sort;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.kframework.Collections.*;
import static org.kframework.definition.Constructors.*;
import static org.kframework.kore.KORE.*;

/**
 * Raises all rules into a form with a KSequence in every position that expects a term of sort K.
 */
public class LiftToKSequence {

    private final Module mod;

    public LiftToKSequence(Module mod) {
        this.mod = mod;
    }

    public Sentence lift(Sentence s) {
        if (s instanceof Rule) {
            return lift((Rule) s);
        } else if (s instanceof Context) {
            return lift((Context) s);
        } else {
            return s;
        }
    }

    private Rule lift(Rule rule) {
        return Rule(
                lift(rule.body()),
                lift(rule.requires()),
                lift(rule.ensures()),
                rule.att());
    }

    private Context lift(Context context) {
        return Context(
                lift(context.body()),
                lift(context.requires()),
                context.att());
    }

    public Sentence liftPartial(Sentence s) {
        if (s instanceof Rule) {
            return liftPartial((Rule) s);
        } else if (s instanceof Context) {
            return liftPartial((Context) s);
        } else {
            return s;
        }
    }

    private Rule liftPartial(Rule rule) {
        return Rule(
                liftPartial(rule.body()),
                liftPartial(rule.requires()),
                liftPartial(rule.ensures()),
                rule.att());
    }

    private Context liftPartial(Context context) {
        return Context(
                liftPartial(context.body()),
                liftPartial(context.requires()),
                context.att());
    }

    public K lift(K term) {
        K result = new TransformKORE()  {
            @Override
            public K apply(KApply k) {
                List<K> children = new ArrayList<>();
                for (K child : k.klist().items()) {
                    K res = apply(child);
                    if (res instanceof KSequence) {
                        children.add(res);
                    } else {
                        children.add(KSequence(res));
                    }
                }
                return KApply(k.klabel(), KList(children), k.att());
            }
        }.apply(term);
        if (result instanceof KSequence) {
            return result;
        } else {
            return KSequence(result);
        }
    }

    public K liftPartial(K term) {
        K result = new TransformKORE()  {
            @Override
            public K apply(KApply k) {
                List<K> children = new ArrayList<>();
                class Holder {
                    int i = 0;
                }
                Holder h = new Holder();
                for (K child : k.klist().items()) {
                    K res = apply(child);
                    if (!mod.productionsFor().contains(k.klabel())) {
                        if (k.klabel().name().equals("#match") && h.i == 0 && k.klist().items().get(1) instanceof KApply && needsKSeq(res, Collections.singleton(mod.sortFor().apply(((KApply) k.klist().items().get(1)).klabel())))) {
                            children.add(KSequence(res));
                        } else {
                            children.add(res);
                        }
                    } else {
                        Set<Sort> sortsForPosition = stream(mod.productionsFor().apply(k.klabel())).filter(p -> p.arity() == k.klist().size()).map(p -> p.nonTerminal(h.i).sort()).collect(Collectors.toSet());
                        if (needsKSeq(res, sortsForPosition)) {
                            children.add(KSequence(res));
                        } else {
                            children.add(res);
                        }
                    }
                    h.i++;
                }
                return KApply(k.klabel(), KList(children), k.att());
            }

            private boolean needsKSeq(K res, Set<Sort> sortsForPosition) {
                return !(res instanceof KSequence || !sortsForPosition.contains(Sorts.K())
                        || (res instanceof KApply && !(((KApply) res).klabel() instanceof KVariable)
                        && mod.sortFor().contains(((KApply) res).klabel()) && mod.sortFor().apply(((KApply) res).klabel()).equals(Sorts.K())));
            }
        }.apply(term);
        return result;
    }

    public K lower(K term) {
        return new TransformKORE() {
            @Override
            public K apply(KSequence k) {
                if (k.items().size() == 1) {
                    return super.apply(k.items().get(0));
                }
                return super.apply(k);
            }
        }.apply(term);
    }
}
