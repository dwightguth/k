// Copyright (c) 2015 K Team. All Rights Reserved.
package org.kframework.kore;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.apache.commons.io.FileUtils;
import org.kframework.Collections;
import org.kframework.attributes.Att;
import org.kframework.builtin.Sorts;
import org.kframework.compile.ConfigurationInfo;
import org.kframework.compile.ConfigurationInfoFromModule;
import org.kframework.compile.LabelInfoFromModule;
import org.kframework.compile.StrictToHeatingCooling;
import org.kframework.definition.Bubble;
import org.kframework.definition.Configuration;
import org.kframework.definition.Definition;
import org.kframework.definition.Module;
import org.kframework.parser.TreeNodesToKORE;
import org.kframework.parser.concrete2kore.ParseInModule;
import org.kframework.parser.concrete2kore.ParserUtils;
import org.kframework.parser.concrete2kore.generator.RuleGrammarGenerator;
import org.kframework.tiny.*;
import scala.Tuple2;
import scala.collection.Seq;
import scala.collection.immutable.Set;

import static org.kframework.Collections.*;
import static org.kframework.kore.KORE.*;
import static org.kframework.definition.Constructors.*;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * The new compilation pipeline. Everything is just wired together and will need clean-up once we deside on design.
 * Tracked by #1442.
 */

public class Kompile {

    public static final File BUILTIN_DIRECTORY = JarInfo.getKIncludeDir().resolve("builtin").toFile();
    private static final String REQUIRE_KAST_K = "requires \"kast.k\"\n";
    private static final String mainModule = "K";
    private static final String startSymbol = "RuleContent";

    public Kompile() throws IOException, URISyntaxException {
        gen = makeRuleGrammarGenerator();
    }

    private static RuleGrammarGenerator makeRuleGrammarGenerator() throws URISyntaxException, IOException {
        String definitionText;
        File definitionFile = new File(BUILTIN_DIRECTORY.toString() + "/kast.k");
        try {
            definitionText = FileUtils.readFileToString(definitionFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        //Definition baseK = ParserUtils.parseMainModuleOuterSyntax(definitionText, mainModule);
        java.util.Set<Module> modules =
                ParserUtils.loadModules(definitionText,
                        Source.apply(definitionFile.getAbsolutePath()),
                        definitionFile.getParentFile(),
                        Lists.newArrayList(BUILTIN_DIRECTORY));

        return new RuleGrammarGenerator(modules);
    }

    public static org.kframework.tiny.Rewriter getRewriter(Module module) throws IOException, URISyntaxException {
        return new org.kframework.tiny.Rewriter(module, KIndex$.MODULE$);
    }

    // todo: rename and refactor this
    public Tuple2<Module, BiFunction<String, Source, K>> getStuff(File definitionFile, String mainModuleName, String mainProgramsModule) throws IOException, URISyntaxException {
        String definitionString = FileUtils.readFileToString(definitionFile);

//        Module mainModuleWithBubble = ParserUtils.parseMainModuleOuterSyntax(definitionString, "TEST");

        Definition definition = ParserUtils.loadDefinition(
                mainModuleName,
                mainProgramsModule, definitionString,
                Sources.fromFile(definitionFile),
                definitionFile.getParentFile(),
                Lists.newArrayList(BUILTIN_DIRECTORY));

        Module mainModuleWithBubble = stream(definition.modules()).filter(m -> m.name().equals(mainModuleName)).findFirst().get();

        Kompile kompile = new Kompile();

        Module mainModule = ModuleTransformer.from(kompile::resolveBubbles).apply(mainModuleWithBubble);

        Module afterHeatingCooling = StrictToHeatingCooling.apply(mainModule);

        ConfigurationInfoFromModule configInfo = new ConfigurationInfoFromModule(afterHeatingCooling);
        LabelInfo labelInfo = new LabelInfoFromModule(afterHeatingCooling);
        SortInfo sortInfo = SortInfo.fromModule(afterHeatingCooling);

        Module concretized = new ConcretizeCells(configInfo, labelInfo, sortInfo).concretize(afterHeatingCooling);

        Module kseqModule = ParserUtils.loadModules("requires \"kast.k\"",
                Sources.fromFile(BUILTIN_DIRECTORY.toPath().resolve("kast.k").toFile()),
                definitionFile.getParentFile(),
                Lists.newArrayList(BUILTIN_DIRECTORY)).stream().filter(m -> m.name().equals("KSEQ")).findFirst().get();

        Module withKSeq = Module("EXECUTION",
                Set(concretized, kseqModule),
                Collections.<Sentence>Set(), Att());

        Module moduleForPrograms = definition.getModule(mainProgramsModule).get();
        ParseInModule parseInModule = RuleGrammarGenerator.getProgramsGrammar(moduleForPrograms);

        final Function<String, K> pp = s -> {
            return TreeNodesToKORE.down(TreeNodesToKORE.apply(parseInModule.parseString(s, "K")._1().right().get()));
        };

        System.out.println(concretized);

        return Tuple2.apply(withKSeq, pp);
    }

    RuleGrammarGenerator gen;

    private Module resolveConfig(Module mainModuleWithBubble) {
        Set<Bubble> configDecls = stream(mainModuleWithBubble.sentences())
                .filter(s -> s instanceof Bubble)
                .map(b -> (Bubble) b)
                .filter(b -> b.sentenceType().equals("config"))
                .collect(Collections.toSet());
        if (configDecls.size() > 1) {
            throw KExceptionManager.compilerError("Found more than one configuration in definition: " + configDecls);
        }
        if (configDecls.size() == 0) {
            configDecls = Set(Bubble("config", "<k> $PGM:K </k>", Att()));
        }

        java.util.Set<ParseFailedException> errors = Sets.newHashSet();

        Optional<Configuration> configDeclOpt = stream(configDecls)
                .parallel()
                .map(b -> {
                    int startLine = b.att().<Integer>get("contentStartLine").get();
                    int startColumn = b.att().<Integer>get("contentStartColumn").get();
                    String source = b.att().<String>get("Source").get();
                    return configParser.parseString(b.contents(), startSymbol, Source.apply(source), startLine, startColumn);
                })
                .flatMap(result -> {
                    System.out.println("warning = " + result._2());
                    if (result._1().isRight())
                        return Stream.of(result._1().right().get());
                    else {
                        errors.addAll(result._1().left().get());
                        return Stream.empty();
                    }
                })
                .map(TreeNodesToKORE::apply)
                .map(TreeNodesToKORE::down)
                .map(contents -> {
                    KApply ruleContents = (KApply) contents;
                    List<org.kframework.kore.K> items = ruleContents.klist().items();
                    switch (ruleContents.klabel().name()) {
                    case "#ruleNoConditions":
                        return Configuration(items.get(0), Or.apply(), Att.apply());
                    case "#ruleEnsures":
                        return Configuration(items.get(0), items.get(1), Att.apply());
                    default:
                        throw new AssertionError("Wrong KLabel for rule content");
                    }
                })
                .findFirst();

        if (!errors.isEmpty()) {
            throw new AssertionError("Had " + errors.size() + " parsing errors: " + errors);
        }

        Configuration configDecl = configDeclOpt.get();

        Set<Sentence> configDeclProductions = GenerateSentencesFromConfigDecl.gen(configDecl.body(), configDecl.ensures(), configDecl.att(), configParser.module())._1();
        Module mainModuleBubblesWithConfig = Module(mainModuleName, Set(),
                (Set<Sentence>) mainModuleWithBubble.sentences().$bar(configDeclProductions), Att());
    }

    private Module resolveBubbles(Module mainModuleWithBubble) {

        ParseInModule ruleParser = gen.getRuleGrammar(mainModuleWithBubble);

        Set<Sentence> ruleSet = stream(mainModuleBubblesWithConfig.sentences())
                .parallel()
                .filter(s -> s instanceof Bubble)
                .map(b -> (Bubble) b)
                .filter(b -> !b.sentenceType().equals("config"))
                .map(b -> {
                    int startLine = b.att().<Integer>get("contentStartLine").get();
                    int startColumn = b.att().<Integer>get("contentStartColumn").get();
                    String source = b.att().<String>get("Source").get();
                    return ruleParser.parseString(b.contents(), startSymbol, Source.apply(source), startLine, startColumn);
                })
                .flatMap(result -> {
                    if (result._1().isRight()) {
                        System.out.println("warning = " + result._2());
                        return Stream.of(result._1().right().get());
                    } else {
                        errors.addAll(result._1().left().get());
                        return Stream.empty();
                    }
                })
                .map(TreeNodesToKORE::apply)
                .map(TreeNodesToKORE::down)
                .map(contents -> {
                    KApply ruleContents = (KApply) contents;
                    List<org.kframework.kore.K> items = ruleContents.klist().items();
                    switch (ruleContents.klabel().name()) {
                        case "#ruleNoConditions":
                            return Rule(items.get(0), And.apply(), Or.apply());
                        case "#ruleRequires":
                            return Rule(items.get(0), items.get(1), Or.apply());
                        case "#ruleEnsures":
                            return Rule(items.get(0), And.apply(), items.get(1));
                        case "#ruleRequiresEnsures":
                            return Rule(items.get(0), items.get(1), items.get(2));
                        default:
                            throw new AssertionError("Wrong KLabel for rule content");
                    }
                })
                .collect(Collections.toSet());

        // todo: Cosmin: fix as this effectively flattens the module
        return Module(mainModuleWithBubble.name(), mainModuleWithBubble.imports(),
                (Set<Sentence>) mainModuleWithBubble.localSentences().$bar(ruleSet), mainModuleWithBubble.att());
    }
}
