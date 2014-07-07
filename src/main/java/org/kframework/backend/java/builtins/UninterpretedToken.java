// Copyright (c) 2013-2014 K Team. All Rights Reserved.
package org.kframework.backend.java.builtins;

import org.kframework.backend.java.kil.Term;
import org.kframework.backend.java.kil.Token;
import org.kframework.backend.java.symbolic.Matcher;
import org.kframework.backend.java.symbolic.Unifier;
import org.kframework.backend.java.symbolic.Transformer;
import org.kframework.backend.java.symbolic.Visitor;
import org.kframework.kil.ASTNode;

import java.util.HashMap;
import java.util.Map;


/**
 * Represents an uninterpreted token which consists of the {@code String}
 * representation of a sort and a value.
 * 
 * @author AndreiS
 */
public final class UninterpretedToken extends Token {

    /* Token cache */
    private static final Map<String, Map <String, UninterpretedToken>> cache = new HashMap<String, Map <String, UninterpretedToken>>();

    private final String sort;
    private final String value;

    private UninterpretedToken(String sort, String value) {
        this.sort = sort;
        this.value = value;
    }

    /**
     * Returns a {@code UninterpretedToken} representation of a token of given sort and value. The
     * UninterpretedToken instances are cached to ensure uniqueness (subsequent invocations of
     * this method with the same sort and value return the same {@code UninterpretedToken} object).
     */
    public static UninterpretedToken of(String sort, String value) {
        Map<String, UninterpretedToken> sortCache = cache.get(sort);
        if (sortCache == null) {
            sortCache = new HashMap<String, UninterpretedToken>();
            cache.put(sort, sortCache);
        }

        UninterpretedToken cachedGenericToken = sortCache.get(value);
        if (cachedGenericToken == null) {
            cachedGenericToken = new UninterpretedToken(sort, value);
            sortCache.put(value, cachedGenericToken);
        }

        return cachedGenericToken;
    }

    /**
     * Returns a {@code String} representation of the sort of this UninterpretedToken.
     */
    @Override
    public String sort() {
        return sort;
    }

    /**
     * Returns a {@code String} representation of the value of this UninterpretedToken.
     */
    @Override
    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object object) {
        /* UninterpretedToken instances are cached */
        return this == object;
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
    public ASTNode accept(Transformer transformer) {
        return transformer.transform(this);
    }

    @Override
    public void accept(Visitor visitor) {
        visitor.visit(this);
    }

    /**
     * Returns the cached instance rather than the de-serialized instance if there is a cached
     * instance.
     */
    private Object readResolve() {
        Map<String, UninterpretedToken> sortCache = cache.get(sort);
        if (sortCache == null) {
            sortCache = new HashMap<String, UninterpretedToken>();
            cache.put(sort, sortCache);
        }

        UninterpretedToken cachedGenericToken = sortCache.get(value);
        if (cachedGenericToken == null) {
            cachedGenericToken = this;
            sortCache.put(value, cachedGenericToken);
        }

        return cachedGenericToken;
    }

}
