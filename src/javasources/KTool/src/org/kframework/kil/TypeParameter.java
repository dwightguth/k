// Copyright (c) 2014 K Team. All Rights Reserved.
package org.kframework.kil;

import org.kframework.kil.Term;

public interface TypeParameter {

    public static class DependentTypeParameter implements TypeParameter {

        private final Term parameter;

        public DependentTypeParameter(Term parameter) {
            this.parameter = parameter;
        }

        public Term getParameter() {
            return parameter;
        }
    }

    public static class TypeVariable implements TypeParameter {

        private final Variable parameter;

        public TypeVariable(Variable parameter) {
            this.parameter = parameter;
        }

        public Variable getParameter() {
            return parameter;
        }
    }
}
