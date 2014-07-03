// Copyright (c) 2013-2014 K Team. All Rights Reserved.
package org.kframework.backend.java.kil;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import org.kframework.backend.java.symbolic.Matcher;
import org.kframework.backend.java.symbolic.Transformer;
import org.kframework.backend.java.symbolic.Unifier;
import org.kframework.backend.java.symbolic.Visitor;
import org.kframework.kil.ASTNode;
import org.kframework.kil.Attribute;
import org.kframework.kil.Production;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;


/**
 * A KLabel constant.
 *
 * @author AndreiS
 */
public class KLabelConstant extends KLabel {

    /* KLabelConstant cache */
    private static final HashMap<String, KLabelConstant> cache = new HashMap<String, KLabelConstant>();

    /* un-escaped label */
    private final String label;
    
    /* unmodifiable view of a list of productions generating this {@code KLabelConstant} */
    private final ImmutableList<Production> productions;

    /*
     * boolean flag set iff a production tagged with "function" or "predicate"
     * generates this {@code KLabelConstant}
     */
    private final boolean isFunction;

    private KLabelConstant(String label, Definition definition) {
        this.label = label;
        productions = definition != null ?
                ImmutableList.<Production>copyOf(definition.context().productionsOf(label)) : 
                ImmutableList.<Production>of();
        
        // TODO(YilongL): urgent; how to detect KLabel clash?

        boolean isFunction = false;
        if (!label.startsWith("is")) {
            Iterator<Production> iterator = productions.iterator();
            if (iterator.hasNext()) {
                Production fstProd = iterator.next();
                isFunction = fstProd.containsAttribute(Attribute.FUNCTION.getKey())
                        || fstProd.containsAttribute(Attribute.PREDICATE.getKey());
            }
            
            while (iterator.hasNext()) {
                Production production = iterator.next();
                /*
                 * YilongL: this assertion is necessary because whether this
                 * KLabel is a function determines if the KItem constructed by
                 * this KLabel can be split during unification
                 */
                assert isFunction == (production
                        .containsAttribute(Attribute.FUNCTION.getKey()) || production
                        .containsAttribute(Attribute.PREDICATE.getKey())) : "Cannot determine if the KLabel "
                        + label
                        + " is a function symbol because there are multiple productions associated with this KLabel: "
                        + productions;
            }
        } else {
            /* a KLabel beginning with "is" represents a sort membership predicate */
            isFunction = true;
        }
        this.isFunction = isFunction;
    }

    /**
     * Returns a {@code KLabelConstant} representation of label. The {@code KLabelConstant}
     * instances are cached to ensure uniqueness (subsequent invocations
     * of this method with the same label return the same {@code KLabelConstant} object).
     *
     * @param label string representation of the KLabel; must not be '`' escaped;
     * @return AST term representation the the KLabel;
     */
    public static KLabelConstant of(String label, Definition definition) {
        assert label != null;

        KLabelConstant kLabelConstant = cache.get(label);
        if (kLabelConstant == null) {
            kLabelConstant = new KLabelConstant(label, definition);
            cache.put(label, kLabelConstant);
        }
        return kLabelConstant;
    }

    /**
     * Returns true iff no production tagged with "function" or "predicate" generates this {@code
     * KLabelConstant}.
     */
    @Override
    public boolean isConstructor() {
        return !isFunction;
    }

    /**
     * Returns true iff a production tagged with "function" or "predicate" generates this {@code
     * KLabelConstant}.
     */
    @Override
    public boolean isFunction() {
        return isFunction;
    }

    public String label() {
        return label;
    }

    /**
     * Returns a list of productions generating this {@code KLabelConstant}.
     */
    public List<Production> productions() {
        return productions;
    }
    
    @Override
    public boolean equals(Object object) {
        /* {@code KLabelConstant} objects are cached to ensure uniqueness */
        return this == object;
    }

    @Override
    public int computeHash() {
        return label.hashCode();
    }

    @Override
    public String toString() {
        return label;
    }

    @Override
    public void accept(Unifier unifier, Term pattern) {
        unifier.unify(this, pattern);
    }

    @Override
    public void accept(Matcher matcher, Term pattern) {
        matcher.match(this, pattern);
    }

    @Override
    public void accept(Visitor visitor) {
        visitor.visit(this);
    }

    @Override
    public ASTNode accept(Transformer transformer) {
        return transformer.transform(this);
    }

    /**
     * Returns the cached instance rather than the de-serialized instance if there is a cached
     * instance.
     */
    private Object readResolve() {
        KLabelConstant kLabelConstant = cache.get(label);
        if (kLabelConstant == null) {
            kLabelConstant = this;
            cache.put(label, kLabelConstant);
        }
        return kLabelConstant;
    }

    public boolean isMetaBinder() {
        return hasAttribute("metabinder");
    }

    public boolean isBinder() {
        return hasAttribute("binder");
    }

    private boolean hasAttribute(String attribute) {
        for (Production production : productions) {
            if (production.containsAttribute(attribute)) {
                return true;
                //assuming is binder if one production says so.
            }
        }
        return false;
    }

    /**
     * Searches for and retieves (if found) a binder map for this label
     * See {@link org.kframework.kil.Production#getBinderMap()}
     *
     * @return the binder map for this label (or {@code null} if no binder map was defined.
     */
    public Multimap<Integer, Integer> getBinderMap() {
        for (Production production : productions) {
            Multimap<Integer, Integer> binderMap = production.getBinderMap();
            if (binderMap != null) {
                return binderMap;
                //assuming is binder if one production says so.
            }
        }
        return  null;
    }
}