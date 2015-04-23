// Copyright (c) 2015 K Team. All Rights Reserved.
package org.kframework.backend.java.symbolic;

import com.google.inject.Inject;
import com.google.inject.Provider;
import org.kframework.Rewriter;
import org.kframework.backend.java.compile.KOREtoBackendKIL;
import org.kframework.backend.java.indexing.IndexingTable;
import org.kframework.backend.java.kil.ConstrainedTerm;
import org.kframework.backend.java.kil.Definition;
import org.kframework.backend.java.kil.GlobalContext;
import org.kframework.backend.java.kil.KLabelConstant;
import org.kframework.backend.java.kil.Term;
import org.kframework.backend.java.kil.TermContext;
import org.kframework.backend.java.util.JavaKRunState;
import org.kframework.definition.Module;
import org.kframework.kompile.KompileOptions;
import org.kframework.kore.K;
import org.kframework.krun.api.KRunState;
import org.kframework.krun.api.io.FileSystem;
import org.kframework.main.GlobalOptions;
import org.kframework.utils.errorsystem.KExceptionManager;
import org.kframework.utils.file.FileUtil;
import org.kframework.utils.inject.Builtins;
import org.kframework.utils.inject.DefinitionScoped;
import org.kframework.utils.options.SMTOptions;
import scala.collection.JavaConversions;

import java.lang.invoke.MethodHandle;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Created by dwightguth on 5/6/15.
 */
@DefinitionScoped
public class InitializeRewriter implements Function<Module, Rewriter> {

    private final FileSystem fs;
    private final JavaExecutionOptions javaOptions;
    private final GlobalOptions globalOptions;
    private final KExceptionManager kem;
    private final SMTOptions smtOptions;
    private final Map<String, Provider<MethodHandle>> hookProvider;
    private final KompileOptions kompileOptions;
    private final FileUtil files;

    @Inject
    public InitializeRewriter(
            FileSystem fs,
            JavaExecutionOptions javaOptions,
            GlobalOptions globalOptions,
            KExceptionManager kem,
            SMTOptions smtOptions,
            @Builtins Map<String, Provider<MethodHandle>> hookProvider,
            KompileOptions kompileOptions,
            FileUtil files) {
        this.fs = fs;
        this.javaOptions = javaOptions;
        this.globalOptions = globalOptions;
        this.kem = kem;
        this.smtOptions = smtOptions;
        this.hookProvider = hookProvider;
        this.kompileOptions = kompileOptions;
        this.files = files;
    }

    private final Map<Module, Rewriter> cache = new LinkedHashMap<Module, Rewriter>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Module, Rewriter> eldest) {
            return this.size() > 20;
        }
    };

    @Override
    public synchronized Rewriter apply(Module module) {
        if (cache.containsKey(module)) {
            return cache.get(module);
        }
        Definition definition = new Definition(module, kem);
        GlobalContext initializingContext = new GlobalContext(fs, javaOptions, globalOptions, kem, smtOptions, hookProvider, files, Stage.INITIALIZING);
        GlobalContext rewritingContext = new GlobalContext(fs, javaOptions, globalOptions, kem, smtOptions, hookProvider, files, Stage.REWRITING);

        TermContext termContext = TermContext.of(initializingContext);
        termContext.global().setDefinition(definition);

        JavaConversions.setAsJavaSet(module.attributesFor().keySet()).stream()
                .map(l -> KLabelConstant.of(l.name(), definition))
                .forEach(definition::addKLabel);
        definition.addKoreRules(module, termContext);

        Definition evalutedDef = KILtoBackendJavaKILTransformer.expandAndEvaluateDefinition(termContext.global(), kem);

        evalutedDef.setIndex(new IndexingTable(() -> evalutedDef, new IndexingTable.Data()));
        rewritingContext.setDefinition(evalutedDef);

        SymbolicRewriter rewriter = new SymbolicRewriter(evalutedDef, kompileOptions, javaOptions, new KRunState.Counter());
        cache.put(module, new SymbolicRewriterGlue(rewriter, initializingContext, rewritingContext));
        return cache.get(module);
    }

    public static class SymbolicRewriterGlue implements Rewriter {

        private final SymbolicRewriter rewriter;
        private final GlobalContext initializingContext;
        private final GlobalContext rewritingContext;

        public SymbolicRewriterGlue(SymbolicRewriter rewriter, GlobalContext initializingContext, GlobalContext rewritingContext) {
            this.rewriter = rewriter;
            this.initializingContext = initializingContext;
            this.rewritingContext = rewritingContext;
        }

        @Override
        public K execute(K k) {
            KOREtoBackendKIL converter = new KOREtoBackendKIL(TermContext.of(rewritingContext));
            Term backendKil = converter.convert(k);
            JavaKRunState result = (JavaKRunState) rewriter.rewrite(new ConstrainedTerm(backendKil, TermContext.of(rewritingContext, backendKil, BigInteger.ZERO)), rewritingContext.getDefinition().context(), -1, false);
            return result.getJavaKilTerm();
        }
    }
}
