// Copyright (c) 2015 K Team. All Rights Reserved.
package org.kframework.parser.concrete2kore.generator;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.kframework.Collections;
import org.kframework.attributes.Att;
import org.kframework.definition.Definition;
import org.kframework.definition.Module;
import org.kframework.definition.Production;
import org.kframework.definition.ProductionItem;
import org.kframework.definition.RegexTerminal;
import org.kframework.definition.Sentence;
import org.kframework.definition.Terminal;
import org.kframework.kore.Sort;
import org.kframework.parser.concrete2kore.ParseInModule;
import org.kframework.utils.StringUtil;
import scala.collection.immutable.List;
import scala.collection.immutable.Seq;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import static org.kframework.Collections.*;
import static org.kframework.definition.Constructors.*;
import static org.kframework.kore.KORE.Sort;

/**
 * Generator for rule and ground parsers.
 * Takes as input a reference to a definition containing all the base syntax of K
 * and uses it to generate a grammar by connecting all users sorts in a lattice with
 * the top sort KItem#Top and the bottom sort KItem#Bottom.
 * <p/>
 * The instances of the non-terminal KItem is renamed in KItem#Top if found in the right
 * hand side of a production, and into KItem#Bottom if found in the left hand side.
 */
public class RuleGrammarGenerator {

    private final Map<String, Module> baseK;
    private static final Sort KBott = Sort("KBott");
    private static final Sort KTop = Sort("K");
    private static final Sort KLabel = Sort("KLabel");
    private static final Sort KList = Sort("KList");
    private static final Sort KItem = Sort("KItem");
    private static final Set<Sort> kSorts = new HashSet<>();

    static {
        kSorts.add(KBott);
        kSorts.add(KTop);
        kSorts.add(KLabel);
        kSorts.add(KList);
        kSorts.add(KItem);
        kSorts.add(Sort("RuleContent"));
        kSorts.add(Sort("KVariable"));
    }
    /// modules that have a meaning:
    public static final String RULE_CELLS = "RULE-CELLS";
    public static final String CONFIG_CELLS = "CONFIG-CELLS";
    public static final String K = "K";
    public static final String CAST_AUTO_GEN = "CAST-AUTO-GEN";

    public RuleGrammarGenerator(Set<Module> baseK) {
        this.baseK = new HashMap<>();
        renameKItem2Bottom(baseK).stream().forEach(m -> this.baseK.put(m.name(), m));
    }

    private Set<Module> renameKItem2Bottom(Set<Module> def) {
        // TODO: do renaming of KItem and K in the LHS to KBott
        return def;
    }

    public ParseInModule getRuleGrammar(Module mod) {
        Module newM = new Module(mod.name() + "-" + RULE_CELLS, Set(mod, baseK.getModule(K).get(), baseK.getModule(RULE_CELLS).get()), Set(), null);
        return getCombinedGrammar(newM);
    }

    public ParseInModule getConfigGrammar(Module mod) {
        Module newM = new Module(mod.name() + "-" + CONFIG_CELLS, Set(mod, baseK.getModule(K).get(), baseK.getModule(CONFIG_CELLS).get()), Set(), null);
        return getCombinedGrammar(newM);
    }

    /**
     * Create the rule parser for the given module.
     * It creates a module which includes the given module and the base K module given to the
     * constructor. The new module contains syntax declaration for Casts and the diamond
     * which connects the user concrete syntax with K syntax.
     *
     * @param mod module for which to create the parser.
     * @return parser which applies disambiguation filters by default.
     */
    public ParseInModule getCombinedGrammar(Module mod) {
        Set<Sentence> prods = new HashSet<>();

        if (mod.importedModules().contains(baseK.getModule(CAST_AUTO_GEN).get())) { // create the diamond
            for (Sort srt : iterable(mod.definedSorts())) {
                if (!kSorts.contains(srt) && !srt.name().startsWith("#")) {
                    // Sort ::= KBott
                    prods.add(Production(srt, Seq(NonTerminal(KBott)), Att()));
                    // K ::= Sort
                    prods.add(Production(KTop, Seq(NonTerminal(srt)), Att()));
                    // K ::= K "::Sort" | K ":Sort" | K "<:Sort" | K ":>Sort"
                    prods.addAll(makeCasts(KBott, KTop, srt));
                }
            }
            prods.addAll(makeCasts(KLabel, KLabel, KLabel));
            prods.addAll(makeCasts(KList, KList, KList));
            prods.addAll(makeCasts(KBott, KTop, KItem));
            prods.addAll(makeCasts(KBott, KTop, KTop));
        }
        if (mod.importedModules().contains(baseK.getModule(RULE_CELLS).get())) { // prepare cell productions for rule parsing
            scala.collection.immutable.Set<Sentence> prods2 = stream(mod.sentences()).map(s -> {
                if (s instanceof Production && (s.att().contains("cell") || s.att().contains("maincell"))) {
                    Production p = (Production) s;
                    // assuming that productions tagged with 'cell' start and end with terminals, and only have non-terminals in the middle
                    assert p.items().head() instanceof Terminal || p.items().head() instanceof RegexTerminal;
                    assert p.items().last() instanceof Terminal || p.items().last() instanceof RegexTerminal;
                    Seq<ProductionItem> pi = Seq(p.items().head(), NonTerminal(Sort("#OptionalDots")), NonTerminal(Sort("K")), NonTerminal(Sort("#OptionalDots")), p.items().last());
                    return Production(p.klabel().get().name(), Sort("Cell"), pi, p.att());
                }
                return s;
            }).collect(Collections.toSet());
            prods.addAll(mutable(prods2));
        } else
            prods.addAll(mutable(mod.sentences()));

        Set<String> terminals = new HashSet<>(); // collect all terminals so we can do automatic follow restriction for prefix terminals
        prods.stream().filter(sent -> sent instanceof Production).forEach(p -> stream(((Production) p).items()).forEach(i -> {
            if (i instanceof Terminal) terminals.add(((Terminal) i).value());
        }));
        // Most of the problems with ensuring greedy match of tokens comes from __ productions combined
        // with variables. Find the variable declarations inside the definition and look for prefix terminals
        String varid = "(?<![A-Za-z0-9_\\$!\\?])(\\$|!|\\?)?([A-Z][A-Za-z0-9']*|_)";
        Optional<Sentence> varIdProd = prods.stream().filter(sent -> {
                    if (sent instanceof Production) {
                        Production p = (Production) sent;
                        if (p.sort().name().equals("KVariable")
                                && p.items().size() == 1
                                && p.items().head() instanceof RegexTerminal
                                && p.att().contains("token"))
                            return true;
                    }
                    return false;
                }
        ).findFirst();
        if (varIdProd.isPresent())
            varid = ((RegexTerminal)((Production) varIdProd.get()).items().head()).regex();
        Pattern pattern = Pattern.compile(varid);

        prods = mutable(prods.stream().map(s -> {
            if (s instanceof Production) {
                Production p = (Production) s;
                if (p.sort().name().startsWith("#")) return p; // don't do anything for such productions since they are advanced features
                // rewrite productions to contain follow restrictions for prefix terminals
                // example _==_ and _==K_ can produce ambiguities. Rewrite the first into _(==(?![K])_
                // this also takes care of casting and productions that have ":"
                List<ProductionItem> items = stream(p.items()).map(pi -> {
                    if (pi instanceof Terminal) {
                        Terminal t = (Terminal) pi;
                        Set<String> follow = new HashSet<>();
                        for (String biggerString : terminals) {
                            if (!t.value().equals(biggerString) && biggerString.startsWith(t.value())) {
                                String ending = biggerString.substring(t.value().length());
                                if (pattern.matcher(ending).matches() || terminals.contains(ending))
                                    follow.add(ending.substring(0, 1));
                            }
                        }
                        // add follow restrictions for the characters that might produce ambiguities
                        if (!follow.isEmpty()) {
                            StringBuilder sb = new StringBuilder();
                            follow.stream().forEach(ch -> sb.append(StringUtils.isAlphanumeric(ch) ? ch : "\\" + ch));
                            return RegexTerminal(Pattern.quote(t.value()) + "(?![" + sb.toString() + "])");
                        }
                    }
                    return pi;
                }).collect(Collections.toList());
                if (p.klabel().isDefined())
                    p = Production(p.klabel().get().name(), p.sort(), Seq(items), p.att());
                else
                    p = Production(p.sort(), Seq(items), p.att());
                return p;
            }
            return s;
        }).collect(Collections.toSet()));

        Module newM = new Module(mod.name() + "-PARSER", Set(), immutable(prods), null);
        return new ParseInModule(newM);
    }

    private Set<Sentence> makeCasts(Sort outerSort, Sort innerSort, Sort castSort) {
        Set<Sentence> prods = new HashSet<>();
        Att attrs1 = Att().add("sort", castSort.name());
        prods.add(Production("#SyntacticCast", castSort, Seq(NonTerminal(castSort), Terminal("::" + castSort.name())), attrs1));
        prods.add(Production("#SemanticCast",  castSort, Seq(NonTerminal(castSort), Terminal(":"  + castSort.name())), attrs1));
        prods.add(Production("#InnerCast",     outerSort, Seq(NonTerminal(castSort), Terminal("<:" + castSort.name())), attrs1));
        prods.add(Production("#OuterCast",     castSort, Seq(NonTerminal(innerSort), Terminal(":>" + castSort.name())), attrs1));
        return prods;
    }

    public static ParseInModule getProgramsGrammar(Module mod) {
        Set<Sentence> prods = new HashSet<>();

        // if no start symbol has been defined in the configuration, then use K
        for (Sort srt : iterable(mod.definedSorts())) {
            if (!kSorts.contains(srt) && !mod.listSorts().contains(srt)) {
                // K ::= Sort
                prods.add(Production(KTop, Seq(NonTerminal(srt)), Att()));
            }
        }

        Module newM = new Module(mod.name() + "-FOR-PROGRAMS", Set(mod), immutable(prods), null);
        return new ParseInModule(newM);
    }
}
