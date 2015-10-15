// Copyright (c) 2013-2015 K Team. All Rights Reserved.
package org.kframework.backend.java.kil;

import org.kframework.backend.java.symbolic.ConjunctiveFormula;
import org.kframework.backend.java.symbolic.Transformer;
import org.kframework.backend.java.symbolic.Visitor;
import org.kframework.kil.ASTNode;
import org.kframework.krun.api.io.FileSystem;

import java.io.Serializable;

/**
 * An object containing context specific to a particular configuration.
 */
public class TermContext extends JavaSymbolicObject {

    private final FreshCounter counter;

    private final GlobalContext global;

    // TODO(YilongL): do we want to make it thread-safe?
    private static class FreshCounter implements Serializable {
        private long value;

        private FreshCounter(long value) {
            this.value = value;
        }

        private long incrementAndGet() {
            return ++value;
        }
    }

    /**
     * {@code topTerm} and {@code topConstraint} are not set in the constructor
     * because they must be set before use anyway.
     */
    private Term topTerm;
    private ConjunctiveFormula topConstraint;

    private TermContext(GlobalContext global, FreshCounter counter) {
        this.global = global;
        this.counter = counter;
    }

    /**
     * Returns a new {@link TermContext} with a fresh counter starting from {@code 0}.
     */
    public static TermContext of(GlobalContext global) {
        return new TermContext(global, new FreshCounter(0));
    }

    /**
     * Forks an identical {@link TermContext}.
     */
    public TermContext fork() {
        return new TermContext(global, new FreshCounter(counter.value));
    }

    public long freshConstant() {
        return counter.incrementAndGet();
    }

    public Definition definition() {
        return global.getDefinition();
    }

    public FileSystem fileSystem() {
        return global.fs;
    }

    public GlobalContext global() {
        return global;
    }

    public Term getTopTerm() {
        return topTerm;
    }

    public void setTopTerm(Term topTerm) {
        this.topTerm = topTerm;
    }

    public ConjunctiveFormula getTopConstraint() {
        return topConstraint;
    }

    public void setTopConstraint(ConjunctiveFormula topConstraint) {
        this.topConstraint = topConstraint;
    }

    @Override
    public ASTNode accept(Transformer transformer) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void accept(Visitor visitor) {
        throw new UnsupportedOperationException();
    }
}
