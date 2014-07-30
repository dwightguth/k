// Copyright (c) 2014 K Team. All Rights Reserved.
package org.kframework.kil;

import java.io.Serializable;

import org.kframework.kil.loader.Context;
import org.kframework.backend.java.symbolic.KILtoBackendJavaKILTransformer;

public class TypeParameter implements Serializable {

    private final String name;
    private final Term requires;

    // Create an unconstrained type parameter
    public TypeParameter() {
        this("_");
    }

    public TypeParameter(String name) {
        this(name, null);
    }
    public TypeParameter(String name, Term requires) {
        this.name = name;
        this.requires = requires;
    }

    public String getName() {
        return name;
    }

    public Term getRequires() {
        return requires;
    }

    public Sort getBound() {
        assert false;
        return null;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        result = prime * result
                + ((requires == null) ? 0 : requires.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        TypeParameter other = (TypeParameter) obj;
        if (name == null) {
            if (other.name != null)
                return false;
        } else if (!name.equals(other.name))
            return false;
        if (requires == null) {
            if (other.requires != null)
                return false;
        } else if (!requires.equals(other.requires))
            return false;
        return true;
    }

    @Override
    public String toString() {
        if (requires == null) {
            return name;
        }
        return name + " requires " + requires;
    }

    public org.kframework.backend.java.kil.TypeParameter toBackendJava(Context context) {
        KILtoBackendJavaKILTransformer transformer = new KILtoBackendJavaKILTransformer(context);
        return new org.kframework.backend.java.kil.TypeParameter(name, (org.kframework.backend.java.kil.Term)transformer.visitNode(requires));
    }
}
