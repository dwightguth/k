// Copyright (c) 2014-2015 K Team. All Rights Reserved.
package org.kframework.parser.concrete2kore.kernel;

import com.beust.jcommander.internal.Lists;
import org.kframework.attributes.Location;
import org.kframework.attributes.Source;
import org.kframework.definition.Production;
import org.kframework.parser.Alphabet;
import org.kframework.parser.Constant;
import org.kframework.parser.KList;
import org.kframework.parser.SymbolReference;
import org.kframework.parser.Term;
import org.kframework.parser.TermCons;
import org.kframework.parser.Terminal;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * An action that transforms an AST into another AST
 */
public abstract class Rule implements Serializable {
    /**
     * Metadata used to inform a rule about the current parse.
     */
    static class MetaData {
        public static class Location {
            public final int position;
            public final int line;
            public final int column;

            public Location(int position, int line, int column) {
                this.position = position;
                this.line = line;
                this.column = column;
            }
        }
        public final Source source;
        public final Location start;
        public final Location end;
        public final CharSequence input;
        public MetaData(Source source, Location start, Location end, CharSequence input) {
            assert start != null && end != null && source != null;
            this.source = source;
            this.start = start;
            this.end = end;
            this.input = input;
        }
    }

    /**
     * A rule who's action does not depend on the context in which the parse occurs.
     */
    public static abstract class ContextFreeRule extends Rule {
        public abstract Set<KList> apply(Set<KList> set, MetaData metaData);
    }

    /**
     * Helper class for rules that treat each KList passed to apply() independently from each other
     */
    public static abstract class KListRule extends ContextFreeRule {
        public Set<KList> apply(Set<KList> set, MetaData metaData) {
            Set<KList> result = new HashSet<>();
            for (KList klist : set) {
                KList newKList = this.apply(klist, metaData);
                if (newKList != null) {
                    result.add(newKList);
                }
            }
            return result;
        }
        protected abstract KList apply(KList set, MetaData metaData);
    }

    /**
     * Wraps the current KList with the given KLabel
     */
    public static class WrapLabelRule extends ContextFreeRule {

        private final Map<List<Alphabet>, List<Production>> productionMap;
        private final Map<Production, Pattern> rejectPatterns;

        public WrapLabelRule(Map<List<Alphabet>, List<Production>> productionMap, Map<Production, Pattern> rejectPatterns) {
            this.productionMap = productionMap;
            this.rejectPatterns = rejectPatterns;
        }

        @Override
        public Set<KList> apply(Set<KList> set, MetaData metaData) {
            Set<KList> parses = new HashSet<>();
            Location loc = new Location(metaData.start.line, metaData.start.column, metaData.end.line, metaData.end.column);
          Source source = metaData.source;
          for (KList parse  : set) {
                List<Term> children = parse.items().stream().map(i -> (SymbolReference)i).filter(i -> !i.symbol().isLayout()).collect(Collectors.toList());
                List<Alphabet> word = children.stream().map(i -> (SymbolReference) i).map(i -> i.symbol()).collect(Collectors.toList());
                for (Production p : productionMap.get(word)) {
                    if (p.att().contains("token")) {
                        String value = metaData.input.subSequence(metaData.start.position, metaData.end.position).toString();
                        Pattern rejectPattern = rejectPatterns.get(p);
                        if (rejectPattern != null && rejectPattern.matcher(value).matches()) {
                        } else {
                            parses.add(KList.apply(Constant.apply(value, p, new org.kframework.parser.NonTerminal(p.sort().name()), loc, source)));
                        }
                    }  else {
                        parses.add(KList.apply(TermCons.apply(children.stream().filter(i -> !(i instanceof Terminal)).collect(Collectors.toList()), p, new org.kframework.parser.NonTerminal(p.sort().name()), loc, source)));
                    }
                }
            }
            return parses;
        }
//        private final Production label;
//        public final Pattern rejectPattern;
//        public WrapLabelRule(Production label, Pattern rejectPattern) {
//            assert label != null;
//            this.label = label;
//            this.rejectPattern = rejectPattern;
//        }
//        public WrapLabelRule(Production label) {
//            assert label != null;
//            this.label = label;
//            rejectPattern = null;
//        }
//        protected KList apply(KList klist, MetaData metaData) {
//            Term term;
//            Location loc = new Location(metaData.start.line, metaData.start.column, metaData.end.line, metaData.end.column);
//            Source source = metaData.source;
//            if (label.att().contains("token")) {
//                String value = metaData.input.subSequence(metaData.start.position, metaData.end.position).toString();
//                if (rejectPattern != null && rejectPattern.matcher(value).matches()) {
//                    return null;
//                }
//                term = Constant.apply(value, label, loc, source);
//            } else {
//                term = TermCons.apply(klist.items(), label, loc, source);
//            }
//            return new KList(Lists.newArrayList(term));
//        }
    }

    /**
     * Helper class for rules that consider only the last few elements of a KList
     */
    public static abstract class SuffixRule extends KListRule {
        /** Returns true iff a KList should be rejected if it doesn't have enough elements */
        protected abstract boolean rejectSmallKLists();
        /** Returns the number of elements at the end of a KList to consider */
        protected abstract int getSuffixLength();
        /** Transforms the last getSuffixLength() elements of a KList.
         * Returns 'null' if the parse should be rejected.
         * Returns 'suffix' if the original parse should be used.
         */
        protected abstract List<Term> applySuffix(List<Term> suffix, MetaData metaData);

        protected KList apply(KList klist, MetaData metaData) {
            List<Term> terms = klist.items();
            int i = terms.size() - this.getSuffixLength();
            if (i < 0) {
                return this.rejectSmallKLists() ? null : klist;
            } else {
                List<Term> suffix = new ArrayList<>();
                for (; i < terms.size(); i++) {
                    suffix.add(terms.get(i));
                }
                List<Term> result = this.applySuffix(suffix, metaData);
                if (result == null) { return null; }
                else if (result == suffix) { return klist; }
                else {
                    KList prefix = KList.apply(klist);
                    for (int j = terms.size() - 1;
                         j >= terms.size() - this.getSuffixLength(); j--) {
                        prefix.items().remove(j);
                    }
                    for (Term term : result) {
                        prefix.add(term);
                    }
                    return prefix;
                }
            }
        }
    }

    /**
     * Delete the last few elements added to the KList.
     * Usually used to remove whitespace and tokens
     */
    public static class DeleteRule extends SuffixRule {
        private final int length;
        private final boolean reject;
        public DeleteRule(int length, boolean reject) {
            this.length = length; this.reject = reject;
        }

        protected boolean rejectSmallKLists() { return reject; }
        protected int getSuffixLength() { return length; }
        protected List<Term> applySuffix(List<Term> terms, MetaData metaData) {
            return new ArrayList<>();
        }
    }

    /**
     * Appends a term to the KList in a parse.
     * This is useful if you are putting labels down before parsing children.
     */
    public static class InsertRule extends SuffixRule {
        private final Term term;
        public InsertRule(Term term) { assert term != null; this.term = term; }
        protected boolean rejectSmallKLists() { return false; }
        protected int getSuffixLength() { return 0; }
        public List<Term> applySuffix(List<Term> set, MetaData metaData) {
            return Lists.newArrayList(term);
        }
    }

    /**
     * Annotates the last term from the KList with location information.
     */
    public static class AddLocationRule extends SuffixRule {
        protected boolean rejectSmallKLists() { return false; }
        protected int getSuffixLength() { return 1; }
        public List<Term> applySuffix(List<Term> terms, MetaData metaData) {
            Term newTerm = terms.get(0).shallowCopy(
                    new Location(metaData.start.line, metaData.start.column,
                                 metaData.end.line, metaData.end.column),
                    metaData.source);
            return Lists.newArrayList(newTerm);
        }
    }

    /*  // for adding a non-constant to a label that was added before the fact
        class AdoptionRule extends ContextFreeRule {
            private boolean reject;
            public Set<KList> apply(Set<KList> set) {
                Set<KList> result = new HashSet<>();
                for (KList klist : set) {
                    List<Term> contents = klist.getContents();
                    if (contents.size() >= 2) {
                        KList newKList = new KList(klist);
                        Term oldFinal = newKList.getContents().remove(contents);
                        Term oldPreFinal = newKList.getContents().remove(...);
                        if (oldPreFinal instanceof KApp) {
                            assert ((KApp) oldPreFinal).getChild() instanceof KList : "unimplemented"; // TODO
                            Term newFinal = new KApp(((KApp) oldPreFinal).getLabel(),
                                KList.append((KList) ((KApp) oldPreFinal).getChild(), oldFinal));
                            newKList.add(newFinal);
                            result.add(newKList);
                        } else if (!reject) { result.add(klist); }
                    } else if (!reject) { return.add(klist); }
                }
            }
        }
        */

    /**
     * TODO: implement this
     */
    public static abstract class ContextSensitiveRule extends Rule {
        abstract Set<KList> apply(KList context, Set<KList> set, MetaData metaData);
    }

    /*
    public static class CheckLabelContextRule extends ContextSensitiveRule {
        private boolean positive;
    }
    */
}
