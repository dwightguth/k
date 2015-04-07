package org.kframework.kompile;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import org.apache.commons.io.FileUtils;
import org.kframework.Collections;
import org.kframework.attributes.Att;
import org.kframework.builtin.Sorts;
import org.kframework.compile.ConfigurationInfo;
import org.kframework.compile.ConfigurationInfoFromModule;
import org.kframework.compile.StrictToHeatingCooling;
import org.kframework.definition.Bubble;
import org.kframework.definition.Configuration;
import org.kframework.definition.Definition;
import org.kframework.definition.Module;
import org.kframework.parser.TreeNodesToKORE;
import org.kframework.parser.concrete2kore.ParseInModule;
import org.kframework.parser.concrete2kore.ParserUtils;
import org.kframework.parser.concrete2kore.generator.RuleGrammarGenerator;
import scala.Tuple2;
import scala.Tuple3;
import scala.collection.Seq;
import scala.collection.immutable.Set;
import scala.util.Either;

import static org.kframework.Collections.*;
import static org.kframework.kore.KORE.*;
import static org.kframework.definition.Constructors.*;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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

    private final FileUtil files;
    private final ParserUtils parser;

    public RuleGrammarGenerator makeRuleGrammarGenerator() {
        String definitionText;
        File definitionFile = new File(BUILTIN_DIRECTORY.toString() + "/kast.k");
        definitionText = files.loadFromWorkingDirectory(definitionFile.getPath());

        //Definition baseK = ParserUtils.parseMainModuleOuterSyntax(definitionText, mainModule);
        java.util.Set<Module> modules =
                parser.loadModules(definitionText,
                        Source.apply(definitionFile.getAbsolutePath()),
                        definitionFile.getParentFile(),
                        Lists.newArrayList(BUILTIN_DIRECTORY));

        return new RuleGrammarGenerator(modules);
    }

    public Tuple3<Module, Definition, BiFunction<String, Source, K>> run(File definitionFile, String mainModuleName, String mainProgramsModule, String programStartSymbol) {
        String definitionString = files.loadFromWorkingDirectory(definitionFile.getPath());

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

    @Inject
    public Kompile(FileUtil files) {
        this.files = files;
        this.parser = new ParserUtils(files);
        BUILTIN_DIRECTORY = files.resolveKBase("include/builtin");
    }

    private Module resolveConfig(Module mainModuleWithBubble) {
        boolean hasConfigDecl = stream(mainModuleWithBubble.sentences())
                .filter(s -> s instanceof Bubble)
                .map(b -> (Bubble) b)
                .filter(b -> b.sentenceType().equals("config"))
                .findFirst().isPresent();

        if (!hasConfigDecl) {
            mainModuleWithBubble = Module(mainModuleName, (Set<Module>)mainModuleWithBubble.imports().$plus(definition.getModule("DEFAULT-CONFIGURATION").get()), mainModuleWithBubble.localSentences(), mainModuleWithBubble.att());
        }

        RuleGrammarGenerator gen = makeRuleGrammarGenerator();
        ParseInModule configParser = gen.getConfigGrammar(mainModuleWithBubble);

        Map<Bubble, Module> configDecls = new HashMap<>();
        Optional<Bubble> configBubbleMainModule = stream(mainModuleWithBubble.localSentences())
                .filter(s -> s instanceof Bubble)
                .map(b -> (Bubble) b)
                .filter(b -> b.sentenceType().equals("config"))
                .findFirst();
        if (configBubbleMainModule.isPresent()) {
            configDecls.put(configBubbleMainModule.get(), mainModuleWithBubble);
        }
        for (Module mod : iterable(mainModuleWithBubble.importedModules())) {
            Optional<Bubble> configBubble = stream(mod.localSentences())
                    .filter(s -> s instanceof Bubble)
                    .map(b -> (Bubble) b)
                    .filter(b -> b.sentenceType().equals("config"))
                    .findFirst();
            if (configBubble.isPresent()) {
                configDecls.put(configBubble.get(), mod);
            }
        }
        if (configDecls.size() > 1) {
            throw KExceptionManager.compilerError("Found more than one configuration in definition: " + configDecls);
        }
        if (configDecls.size() == 0) {
            throw KExceptionManager.compilerError("Unexpected lack of default configuration and no configuration present: bad prelude?");
        }

        Map.Entry<Bubble, Module> configDeclBubble = configDecls.entrySet().iterator().next();

        java.util.Set<ParseFailedException> errors = Sets.newHashSet();

        K _true = KToken(Sort("Bool"), "true");

        Optional<Configuration> configDeclOpt = configDecls.keySet().stream()
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
                        return Configuration(items.get(0), _true, Att.apply());
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
        Module configurationModule = configDeclBubble.getValue();
        Module configurationModuleWithSentences = Module(configurationModule.name(), configurationModule.imports(), (Set<Sentence>) configurationModule.localSentences().$bar(configDeclProductions), configurationModule.att());

        Set<Module> newModules = modules.stream().map(mod -> new ModuleTransformation(mod2 -> {
            if (mod2.name().equals(configurationModule.name()))
                return configurationModuleWithSentences;
            return mod2;
        }).apply(mod)).collect(Collections.toSet());

        Definition defWithConfiguration = Definition(newModules);

        gen = new RuleGrammarGenerator(defWithConfiguration);
        Module mainModuleBubblesWithConfig = stream(defWithConfiguration.modules()).filter(m -> m.name().equals(mainModuleName)).findFirst().get();
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
                            return Rule(items.get(0), _true, _true);
                        case "#ruleRequires":
                            return Rule(items.get(0), items.get(1), _true);
                        case "#ruleEnsures":
                            return Rule(items.get(0), _true, items.get(1));
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
