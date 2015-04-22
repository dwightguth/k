// Copyright (c) 2015 K Team. All Rights Reserved.
package org.kframework.kompile;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import org.apache.commons.io.FileUtils;
import org.kframework.Collections;
import org.kframework.Pickling;
import org.kframework.attributes.Source;
import org.kframework.compile.ConfigurationInfoFromModule;
import org.kframework.compile.LabelInfo;
import org.kframework.compile.LabelInfoFromModule;
import org.kframework.compile.StrictToHeatingCooling;
import org.kframework.definition.Bubble;
import org.kframework.definition.Definition;
import org.kframework.definition.DefinitionTransformer;
import org.kframework.definition.Module;
import org.kframework.definition.Sentence;
import org.kframework.kore.K;
import org.kframework.kore.KApply;
import org.kframework.kore.compile.BooleanUtils;
import org.kframework.kore.compile.ConcretizeCells;
import org.kframework.kore.compile.GenerateSentencesFromConfigDecl;
import org.kframework.kore.compile.ResolveSemanticCasts;
import org.kframework.kore.compile.SortInfo;
import org.kframework.parser.Term;
import org.kframework.parser.TreeNodesToKORE;
import org.kframework.parser.concrete2kore.ParseCache;
import org.kframework.parser.concrete2kore.ParseCache.ParsedSentence;
import org.kframework.parser.concrete2kore.ParseInModule;
import org.kframework.parser.concrete2kore.ParserUtils;
import org.kframework.parser.concrete2kore.generator.RuleGrammarGenerator;
import org.kframework.utils.BinaryLoader;
import org.kframework.utils.StringUtil;
import org.kframework.utils.errorsystem.KExceptionManager;
import org.kframework.utils.errorsystem.ParseFailedException;
import org.kframework.utils.file.FileUtil;
import org.kframework.utils.file.JarInfo;
import scala.Tuple2;
import scala.collection.immutable.Set;
import scala.util.Either;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.kframework.Collections.*;
import static org.kframework.definition.Constructors.*;

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
    private final KExceptionManager kem;
    private final ParserUtils parser;
    private final boolean cacheParses;
    private final BinaryLoader loader;

    public Kompile(FileUtil files, KExceptionManager kem, boolean cacheParses) {
        this.files = files;
        this.kem = kem;
        this.parser = new ParserUtils(files);
        this.cacheParses = cacheParses;
        this.loader = new BinaryLoader(kem);
    }

    @Inject
    public Kompile(FileUtil files, KExceptionManager kem) {
        this(files, kem, true);
    }

    /**
     * Executes the Kompile tool. This tool accesses a
     * @param definitionFile
     * @param mainModuleName
     * @param mainProgramsModule
     * @param programStartSymbol
     * @return
     */
    public CompiledDefinition run(File definitionFile, String mainModuleName, String mainProgramsModule, String programStartSymbol) {
        Definition parsedDef = parseDefinition(definitionFile, mainModuleName, mainProgramsModule, true);

        Module afterHeatingCooling = StrictToHeatingCooling.apply(parsedDef.mainModule());

        Module afterResolvingCasts = new ResolveSemanticCasts().resolve(afterHeatingCooling);

        ConfigurationInfoFromModule configInfo = new ConfigurationInfoFromModule(afterResolvingCasts);
        LabelInfo labelInfo = new LabelInfoFromModule(afterResolvingCasts);
        SortInfo sortInfo = SortInfo.fromModule(afterResolvingCasts);

        Module concretized = new ConcretizeCells(configInfo, labelInfo, sortInfo).concretize(afterResolvingCasts);

        Module kseqModule = parsedDef.getModule("KSEQ").get();

        Module withKSeq = Module("EXECUTION",
                Set(concretized, kseqModule),
                Collections.<Sentence>Set(), Att());

        final BiFunction<String, Source, K> pp = getProgramParser(parsedDef.getModule(mainProgramsModule).get(), programStartSymbol);

        System.out.println(concretized);

        return new CompiledDefinition(withKSeq, parsedDef, pp);
    }

    public BiFunction<String, Source, K> getProgramParser(Module moduleForPrograms, String programStartSymbol) {
        ParseInModule parseInModule = new ParseInModule(gen.getProgramsGrammar(moduleForPrograms));

        return (s, source) -> {
            return TreeNodesToKORE.down(TreeNodesToKORE.apply(parseInModule.parseString(s, programStartSymbol, source)._1().right().get()));
        };
    }

    public Definition parseDefinition(File definitionFile, String mainModuleName, String mainProgramsModule, boolean dropQuote) {
        Definition definition = parser.loadDefinition(
                mainModuleName,
                mainProgramsModule, REQUIRE_KAST_K + "require " + StringUtil.enquoteCString(definitionFile.getPath()),
                Source.apply(definitionFile.getPath()),
                definitionFile.getParentFile(),
                Lists.newArrayList(BUILTIN_DIRECTORY),
                dropQuote);

        boolean hasConfigDecl = stream(definition.mainModule().sentences())
                .filter(s -> s instanceof Bubble)
                .map(b -> (Bubble) b)
                .filter(b -> b.sentenceType().equals("config"))
                .findFirst().isPresent();

        Definition definitionWithConfigBubble;
        if (!hasConfigDecl) {
            definitionWithConfigBubble = DefinitionTransformer.from(mod -> {
                if (mod == definition.mainModule()) {
                    return Module(mod.name(), (Set<Module>) mod.imports().$plus(definition.getModule("DEFAULT-CONFIGURATION").get()), mod.localSentences(), mod.att());
                }
                return mod;
            }).apply(definition);
        } else {
            definitionWithConfigBubble = definition;
        }

        errors = java.util.Collections.synchronizedSet(Sets.newHashSet());
        caches = new HashMap<>();
        File cacheFile = files.resolveKompiled("cache.bin");

        if (cacheParses && cacheFile.exists()) {
            try {
                caches = Pickling.unpickleParser(FileUtils.readFileToByteArray(cacheFile));
            } catch (FileNotFoundException e) {
            } catch (IOException e) {
                kem.registerInternalHiddenWarning("Invalidating serialized cache due to corruption.", e);
            }
        }

        gen = new RuleGrammarGenerator(definitionWithConfigBubble);
        Definition defWithConfig = DefinitionTransformer.from(this::resolveConfig).apply(definitionWithConfigBubble);

        gen = new RuleGrammarGenerator(defWithConfig);
        Definition parsedDef = DefinitionTransformer.from(this::resolveBubbles).apply(defWithConfig);

        try {
            FileUtils.writeByteArrayToFile(cacheFile, Pickling.pickleParser(caches));
        } catch (IOException e) {
            throw KExceptionManager.criticalError("Failed to write to " + files.resolveKompiled("cache.bin"), e);
        }

        loader.saveOrDie(cacheFile, caches);

        if (!errors.isEmpty()) {
            kem.addAllKException(errors.stream().map(e -> e.getKException()).collect(Collectors.toList()));
            throw KExceptionManager.compilerError("Had " + errors.size() + " parsing errors.");
        }
        return parsedDef;
    }

    Map<String, ParseCache> caches;
    java.util.Set<ParseFailedException> errors;
    RuleGrammarGenerator gen;

    private Module resolveConfig(Module module) {
        if (stream(module.localSentences())
                .filter(s -> s instanceof Bubble)
                .map(b -> (Bubble) b)
                .filter(b -> b.sentenceType().equals("config")).count() == 0)
            return module;
        Module configParserModule = gen.getConfigGrammar(module);

        ParseCache cache = loadCache(configParserModule);
        ParseInModule parser = new ParseInModule(cache.getModule());

        Set<Sentence> configDeclProductions = stream(module.localSentences())
                .parallel()
                .filter(s -> s instanceof Bubble)
                .map(b -> (Bubble) b)
                .filter(b -> b.sentenceType().equals("config"))
                .flatMap(b -> performParse(parser, cache.getCache(), b))
                .map(contents -> {
                    KApply configContents = (KApply) contents;
                    List<K> items = configContents.klist().items();
                    switch (configContents.klabel().name()) {
                    case "#ruleNoConditions":
                        return Configuration(items.get(0), BooleanUtils.TRUE, configContents.att());
                    case "#ruleEnsures":
                        return Configuration(items.get(0), items.get(1), configContents.att());
                    default:
                        throw new AssertionError("Wrong KLabel for rule content");
                    }
                })
                .flatMap(
                        configDecl -> stream(GenerateSentencesFromConfigDecl.gen(configDecl.body(), configDecl.ensures(), configDecl.att(), configParserModule)))
                .collect(Collections.toSet());

        return Module(module.name(), module.imports(), (Set<Sentence>) module.localSentences().$bar(configDeclProductions), module.att());
    }

    private Module resolveBubbles(Module module) {
        if (stream(module.localSentences())
                .filter(s -> s instanceof Bubble)
                .map(b -> (Bubble)b)
                .filter(b -> !b.sentenceType().equals("config")).count() == 0)
            return module;
        Module ruleParserModule = gen.getRuleGrammar(module);

        ParseCache cache = loadCache(ruleParserModule);
        ParseInModule parser = new ParseInModule(cache.getModule());

        Set<Sentence> ruleSet = stream(module.localSentences())
                .parallel()
                .filter(s -> s instanceof Bubble)
                .map(b -> (Bubble) b)
                .filter(b -> !b.sentenceType().equals("config"))
                .flatMap(b -> performParse(parser, cache.getCache(), b))
                .map(contents -> {
                    KApply ruleContents = (KApply) contents;
                    List<org.kframework.kore.K> items = ruleContents.klist().items();
                    switch (ruleContents.klabel().name()) {
                    case "#ruleNoConditions":
                        return Rule(items.get(0), BooleanUtils.TRUE, BooleanUtils.TRUE, ruleContents.att());
                    case "#ruleRequires":
                        return Rule(items.get(0), items.get(1), BooleanUtils.TRUE, ruleContents.att());
                    case "#ruleEnsures":
                        return Rule(items.get(0), BooleanUtils.TRUE, items.get(1), ruleContents.att());
                    case "#ruleRequiresEnsures":
                        return Rule(items.get(0), items.get(1), items.get(2), ruleContents.att());
                    default:
                        throw new AssertionError("Wrong KLabel for rule content");
                    }
                })
                .collect(Collections.toSet());

        return Module(module.name(), module.imports(),
                (Set<Sentence>) module.localSentences().$bar(ruleSet), module.att());
    }

    private ParseCache loadCache(Module parser) {
        ParseCache cachedParser = caches.get(parser.name());
        if (cachedParser == null || !equalsSyntax(cachedParser.getModule(), parser)) {
            cachedParser = new ParseCache(parser, java.util.Collections.synchronizedMap(new HashMap<>()));
            caches.put(parser.name(), cachedParser);
        }
        return cachedParser;
    }

    private boolean equalsSyntax(Module _this, Module that) {
        if (!_this.productions().equals(that.productions())) return false;
        if (!_this.priorities().equals(that.priorities())) return false;
        if (!_this.leftAssoc().equals(that.leftAssoc())) return false;
        if (!_this.rightAssoc().equals(that.rightAssoc())) return false;
        return _this.sortDeclarations().equals(that.sortDeclarations());
    }

    private Stream<? extends K> performParse(ParseInModule parser, Map<String, ParsedSentence> cache, Bubble b) {
        int startLine = b.att().<Integer>get("contentStartLine").get();
        int startColumn = b.att().<Integer>get("contentStartColumn").get();
        String source = b.att().<String>get("Source").get();
        Tuple2<Either<java.util.Set<ParseFailedException>, Term>, java.util.Set<ParseFailedException>> result;
        if (cache.containsKey(b.contents())) {
            ParsedSentence parse = cache.get(b.contents());
            kem.addAllKException(parse.getWarnings().stream().map(e -> e.getKException()).collect(Collectors.toList()));
            return Stream.of(parse.getParse());
        } else {
            result = parser.parseString(b.contents(), startSymbol, Source.apply(source), startLine, startColumn);
            kem.addAllKException(result._2().stream().map(e -> e.getKException()).collect(Collectors.toList()));
            if (result._1().isRight()) {
                K k = TreeNodesToKORE.down(TreeNodesToKORE.apply(result._1().right().get()));
                cache.put(b.contents(), new ParsedSentence(k, new HashSet<>(result._2())));
                return Stream.of(k);
            } else {
                errors.addAll(result._1().left().get());
                return Stream.empty();
            }
        }
    }
}
