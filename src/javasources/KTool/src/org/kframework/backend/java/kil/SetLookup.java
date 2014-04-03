package org.kframework.backend.java.kil;

import org.kframework.backend.java.builtins.BoolToken;
import org.kframework.backend.java.symbolic.Matcher;
import org.kframework.backend.java.symbolic.Transformer;
import org.kframework.backend.java.symbolic.Unifier;
import org.kframework.backend.java.symbolic.Visitor;
import org.kframework.backend.java.util.Utils;
import org.kframework.kil.ASTNode;

/**
 *
 * @author TraianSF
 */
public class SetLookup extends Term implements DataStructureLookup {

    private final Term base;
    private final Term key;

    public SetLookup(Term base, Term key) {
        super(Kind.KITEM);
        this.base = base;
        this.key = key;
    }

    public Term evaluateLookup() {
        if (!(base instanceof BuiltinSet)) {
            return this;
        }

        if (((BuiltinSet) base).contains(key)) {
            return BoolToken.TRUE;
        }
        return  this;
    }

    public Term key() {
        return key;
    }

    public Term base() {
        return base;
    }

    @Override
    public boolean isExactSort() {
        return true;
    }

    @Override
    public boolean isSymbolic() {
        return true;
//        assert false : "isSymbolic is not supported by SetLookup (yet)";
//        throw new UnsupportedOperationException();
    }

    @Override
    public String sort() {
        return BoolToken.SORT_NAME;
    }

    @Override
    public int hashCode() {
        if (hashCode == 0) {
            hashCode = 1;
            hashCode = hashCode * Utils.HASH_PRIME + key.hashCode();
            hashCode = hashCode * Utils.HASH_PRIME + base.hashCode();
        }
        return hashCode;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }

        if (!(object instanceof SetLookup)) {
            return false;
        }

        SetLookup mapLookup = (SetLookup) object;
        return key.equals(mapLookup.key) && base.equals(mapLookup.base);
    }

    @Override
    public String toString() {
        return base.toString() + "[" + key + "]";
    }

    @Override
    public void accept(Unifier unifier, Term patten) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void accept(Matcher matcher, Term pattern) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ASTNode accept(Transformer transformer) {
        return transformer.transform(this);
    }

    @Override
    public void accept(Visitor visitor) {
        visitor.visit(this);
    }

}
