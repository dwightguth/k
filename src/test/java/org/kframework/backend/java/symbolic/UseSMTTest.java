package org.kframework.backend.java.symbolic;

import static org.junit.Assert.*;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.kframework.backend.java.builtins.BoolToken;
import org.kframework.backend.java.kil.BuiltinMap;
import org.kframework.backend.java.kil.TermContext;
import org.kframework.backend.java.kil.Variable;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class UseSMTTest {

    @Mock
    TermContext tc;
    
    @Test
    public void testGetModel() {
        BuiltinMap.Builder builder = new BuiltinMap.Builder();
        assertEquals(builder.build(), UseSMT.checkSat(BoolToken.TRUE, tc));
    }
}
