package org.kframework.kore.compile;

import org.junit.Test;
import org.kframework.definition.Context;
import org.kframework.definition.Rule;
import org.kframework.definition.Sentence;

import static org.junit.Assert.assertEquals;
import static org.kframework.definition.Constructors.*;
import static org.kframework.kore.KORE.KApply;
import static org.kframework.kore.KORE.KLabel;
import static org.kframework.kore.KORE.KVariable;

/**
 * Created by dwightguth on 4/17/15.
 */
public class ResolveSemanticCastsTest {

    @Test
    public void testRule() {
        Rule rule = Rule(KApply(KLabel("foo"), KApply(KLabel("#SemanticCastToFoo"), KVariable("bar"))), BooleanUtils.TRUE, BooleanUtils.TRUE);
        Sentence actual = new ResolveSemanticCasts().resolve(rule);
        Rule expected = Rule(KApply(KLabel("foo"), KVariable("bar")), BooleanUtils.and(BooleanUtils.TRUE, KApply(KLabel("isFoo"), KVariable("bar"))), BooleanUtils.TRUE);
        assertEquals(expected, actual);
    }

    @Test
    public void testContext() {
        Context ctx = Context(KApply(KLabel("foo"),
                KApply(KLabel("#SemanticCastToFoo"), KVariable("bar")),
                KApply(KLabel("#SemanticCastToFoo"), KVariable("bar")),
                KApply(KLabel("#SemanticCastToBar"), KApply(KLabel("baz")))), BooleanUtils.TRUE);
        Sentence actual = new ResolveSemanticCasts().resolve(ctx);
        Context expected = Context(KApply(KLabel("foo"), KVariable("bar"), KVariable("bar"), KApply(KLabel("baz"))),
                BooleanUtils.and(BooleanUtils.and(BooleanUtils.TRUE, KApply(KLabel("isBar"), KApply(KLabel("baz")))), KApply(KLabel("isFoo"), KVariable("bar"))));
        assertEquals(expected, actual);
    }
}
