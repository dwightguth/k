package org.kframework.kore.compile;

import com.google.common.collect.Sets;
import org.kframework.definition.Context;
import org.kframework.definition.Module;
import org.kframework.definition.ModuleTransformer;
import org.kframework.definition.Rule;
import org.kframework.definition.Sentence;
import org.kframework.kore.K;
import org.kframework.kore.KApply;

import java.util.Set;

import static org.kframework.kore.KORE.KApply;
import static org.kframework.kore.KORE.KLabel;

/**
 * Created by dwightguth on 4/17/15.
 */
public class ResolveSemanticCasts {

    private Set<KApply> casts = Sets.newHashSet();

    void resetCasts() {
        casts.clear();
    }

    private Rule resolve(Rule rule) {
        resetCasts();
        gatherCasts(rule.body());
        gatherCasts(rule.requires());
        gatherCasts(rule.ensures());
        return new Rule(
                transform(rule.body()),
                addSideCondition(transform(rule.requires())),
                transform(rule.ensures()),
                rule.att());
    }

    private Context resolve(Context context) {
        resetCasts();
        gatherCasts(context.body());
        gatherCasts(context.requires());
        return new Context(
                transform(context.body()),
                addSideCondition(transform(context.requires())),
                context.att());
    }

    K addSideCondition(K requires) {
        return casts.stream().map(kapp -> (K)KApply(KLabel("is" + kapp.klabel().name().substring("#SemanticCastTo".length())), kapp.klist()))
                .reduce(requires, BooleanUtils::and);
    }

    void gatherCasts(K term) {
        new VisitKORE() {
            @Override
            public Void apply(KApply v) {
                if (v.klabel().name().startsWith("#SemanticCastTo"))
                    casts.add(v);
                return super.apply(v);
            }
        }.apply(term);
    }

    K transform(K term) {
        return new TransformKORE() {
            @Override
            public K apply(KApply k) {
                if (casts.contains(k)) {
                    return super.apply(k.klist().items().get(0));
                }
                return super.apply(k);
            }
        }.apply(term);
    }


    public synchronized Sentence resolve(Sentence s) {
        if (s instanceof Rule) {
            return resolve((Rule) s);
        } else if (s instanceof Context) {
            return resolve((Context) s);
        } else {
            return s;
        }
    }


    public Module resolve(Module m) {
        return ModuleTransformer.fromSentenceTransformer(this::resolve).apply(m);
    }
}
