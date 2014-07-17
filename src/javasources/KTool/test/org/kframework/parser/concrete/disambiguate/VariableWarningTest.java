// Copyright (c) 2014 K Team. All Rights Reserved.
package org.kframework.parser.concrete.disambiguate;



import static org.mockito.Mockito.*;

import org.junit.Before;
import org.junit.Test;
import org.kframework.kil.KSequence;
import org.kframework.kil.KSorts;
import org.kframework.kil.Rule;
import org.kframework.kil.Variable;
import org.kframework.kil.loader.Context;
import org.kframework.kil.visitors.exceptions.ParseFailedException;
import org.kframework.main.GlobalOptions;
import org.kframework.utils.BaseTestCase;
import org.kframework.utils.errorsystem.KException;
import org.kframework.utils.errorsystem.KException.ExceptionType;
import org.kframework.utils.general.GlobalSettings;
import org.mockito.ArgumentMatcher;

public class VariableWarningTest extends BaseTestCase {

    @Before
    public void setUp() {
        context = new Context();
        context.globalOptions = new GlobalOptions();
        GlobalSettings.kem = kem;

    }

    @Test
    public void testWarnings() throws ParseFailedException {
        Rule r = new Rule();
        Variable v1 = new Variable("A", KSorts.K);
        v1.setUserTyped(false);
        Variable v2 = new Variable("A", KSorts.K);
        v2.setUserTyped(false);
        Variable v3 = new Variable("B", KSorts.K);
        v3.setUserTyped(true);
        Variable v4 = new Variable("B", KSorts.K);
        v4.setUserTyped(false);
        KSequence ks = new KSequence();
        ks.getContents().add(v1);
        ks.getContents().add(v2);
        ks.getContents().add(v3);
        ks.getContents().add(v4);
        r.setBody(ks);

        new VariableTypeInferenceFilter(context).visitNode(r);
        verify(kem).register(argThat(new ArgumentMatcher<KException>() {
            @Override
            public boolean matches(Object argument) {
                return ((KException)argument).getType() == ExceptionType.HIDDENWARNING;
            }
        }));
    }
}
