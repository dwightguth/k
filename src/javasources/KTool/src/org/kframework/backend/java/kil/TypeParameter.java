package org.kframework.backend.java.kil;

import org.kframework.backend.java.symbolic.BackendJavaKILtoKILTransformer;
import org.kframework.backend.java.kil.Term;
import org.kframework.kil.loader.Context;

public class TypeParameter {

    private final String name;
    private final Term requires;

    public TypeParameter(String name, Term requires) {
        this.name = name;
        this.requires = requires;
    }

    public String name() {
        return name;
    }

    public Term requires() {
        return requires;
    }

    public org.kframework.kil.TypeParameter toFrontEnd(Context context) {
        BackendJavaKILtoKILTransformer transformer = new BackendJavaKILtoKILTransformer(context);
        return new org.kframework.kil.TypeParameter(name, (org.kframework.kil.Term)requires.accept(transformer));
    }
}
