// Copyright (c) 2014-2015 K Team. All Rights Reserved.
package org.kframework.krun.tools;

import java.util.Map;
import java.util.Set;

import org.kframework.compile.transformers.DataStructure2Cell;
import org.kframework.compile.utils.CompilerStepDone;
import org.kframework.compile.utils.ConfigurationSubstitutionVisitor;
import org.kframework.compile.utils.SpecificationCompilerSteps;
import org.kframework.compile.utils.Substitution;
import org.kframework.kil.Attributes;
import org.kframework.kil.Definition;
import org.kframework.kil.Module;
import org.kframework.kil.Sources;
import org.kframework.kil.Term;
import org.kframework.kil.loader.Context;
import org.kframework.krun.KRunOptions;
import org.kframework.krun.api.KRunProofResult;
import org.kframework.krun.api.KRunResult;
import org.kframework.parser.TermLoader;
import org.kframework.transformation.Transformation;
import org.kframework.utils.Stopwatch;
import org.kframework.utils.errorsystem.KExceptionManager;
import org.kframework.utils.file.FileUtil;
import org.kframework.utils.inject.Main;

import com.google.inject.Inject;
import com.google.inject.Provider;

public interface Prover {

    /**
     * Prove a set of reachability rules using Matching Logic.
     * @param module A {@link org.kframework.kil.Module} containing a set of reachability rules to be proven.
     * @exception UnsupportedOperationException The backend implementing this interface does not
     * support proofs
     * @return An object containing metadata about whether the proof succeeded, and a counterexample
     * if it failed.
    */
    public abstract KRunProofResult<Set<Term>> prove(Module module);

    public static class Tool implements Transformation<Void, KRunResult> {

        private final KRunOptions options;
        private final Context context;
        private final Stopwatch sw;
        private final Provider<Term> initialConfiguration;
        private final Prover prover;
        private final Executor executor;
        private final FileUtil files;
        private final TermLoader termLoader;
        private final KExceptionManager kem;

        @Inject
        protected Tool(
                KRunOptions options,
                @Main Context context,
                Stopwatch sw,
                @Main Provider<Term> initialConfiguration,
                @Main Prover prover,
                @Main Executor executor,
                @Main FileUtil files,
                TermLoader termLoader,
                KExceptionManager kem) {
            this.options = options;
            this.context = context;
            this.sw = sw;
            this.initialConfiguration = initialConfiguration;
            this.prover = prover;
            this.executor = executor;
            this.files = files;
            this.termLoader = termLoader;
            this.kem = kem;
        }

        @Override
        public KRunProofResult<Set<Term>> run(Void v, Attributes a) {
            a.add(Context.class, context);
            String proofFile = options.experimental.prove;
            String content = files.loadFromWorkingDirectory(proofFile);
            Definition parsed = termLoader.parseString(content,
                    Sources.fromFile(files.resolveWorkingDirectory(proofFile)), context);
            Module mod = parsed.getSingletonModule();
            mod = preprocess(mod, initialConfiguration.get());
            sw.printIntermediate("Preprocess specification rules");
            KRunProofResult<Set<Term>> result = prover.prove(mod);
            sw.printIntermediate("Proof total");
            return result;
        }

        @Override
        public String getName() {
            return "--prove";
        }

        private Module preprocess(Module module, Term cfg) {
            Map<Term, Term> substitution = null;
            if (cfg != null) {
                cfg = executor.run(cfg, null, false).getFinalState().getRawResult();
                cfg = (Term) (new DataStructure2Cell(context)).visitNode(cfg);
                ConfigurationSubstitutionVisitor configurationSubstitutionVisitor =
                        new ConfigurationSubstitutionVisitor(context);
                configurationSubstitutionVisitor.visitNode(cfg);
                substitution = configurationSubstitutionVisitor.getSubstitution();
//                System.out.println(substitution);
                Module mod = module;
                mod = (Module) new Substitution(substitution,context).visitNode(module);
//                    System.out.println(mod.toString());
                module = mod;
            }
            try {
                module = new SpecificationCompilerSteps(context, kem).compile(module, null);
            } catch (CompilerStepDone e) {
                assert false: "dead code";
            }
            return module;
        }
    }
}
