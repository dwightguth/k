// Copyright (c) 2013-2014 K Team. All Rights Reserved.
package org.kframework.backend.java.symbolic;

import org.kframework.backend.java.kil.Definition;
import org.kframework.backend.java.kil.Term;
import org.kframework.backend.java.kil.ConstrainedTerm;
import org.kframework.backend.java.kil.Rule;
import org.kframework.backend.java.kil.TermContext;
import org.kframework.backend.java.kil.Variable;
import org.kframework.backend.java.util.TestCaseGenerationSettings;
import org.kframework.backend.java.util.TestCaseGenerationUtil;
import org.kframework.backend.unparser.UnparserFilter;
import org.kframework.compile.transformers.DataStructureToLookupUpdate;
import org.kframework.compile.utils.CompileToBuiltins;
import org.kframework.compile.utils.CompilerStepDone;
import org.kframework.compile.utils.ConfigurationSubstitutionVisitor;
import org.kframework.compile.utils.MetaK;
import org.kframework.compile.utils.RuleCompilerSteps;
import org.kframework.compile.utils.Substitution;
import org.kframework.kil.Module;
import org.kframework.kil.loader.Context;
import org.kframework.krun.K;
import org.kframework.krun.KRunExecutionException;
import org.kframework.krun.SubstitutionFilter;
import org.kframework.krun.api.KRun;
import org.kframework.krun.api.KRunDebugger;
import org.kframework.krun.api.KRunProofResult;
import org.kframework.krun.api.KRunResult;
import org.kframework.krun.api.KRunState;
import org.kframework.krun.api.SearchResult;
import org.kframework.krun.api.SearchResults;
import org.kframework.krun.api.SearchType;
import org.kframework.krun.api.TestGenResult;
import org.kframework.krun.api.TestGenResults;
import org.kframework.krun.api.Transition;
import org.kframework.krun.api.UnsupportedBackendOptionException;
import org.kframework.krun.api.io.FileSystem;
import org.kframework.krun.ioserver.filesystem.portable.PortableFileSystem;
import org.kframework.utils.BinaryLoader;
import org.kframework.utils.general.IndexingStatistics;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.uci.ics.jung.graph.DirectedGraph;

/**
 *
 *
 * @author AndreiS
 */
public class JavaSymbolicKRun implements KRun {

    private final Definition definition;
    private final Context context;
    private final KILtoBackendJavaKILTransformer transformer;
    //Liyi Li: add a build-in SymbolicRewriter to fix the simulation rules
    private SymbolicRewriter simulationRewriter;

    public JavaSymbolicKRun(Context context) throws KRunExecutionException {
        /* context is unused for directory paths; the actual context is de-serialized */
        /* load the definition from a binary file */
        definition = (Definition) BinaryLoader.load(
            new File(context.kompiled, JavaSymbolicBackend.DEFINITION_FILENAME).toString());

        if (definition == null) {
            throw new KRunExecutionException("cannot load definition");
        }

        /* initialize the builtin function table */
        BuiltinFunction.init(definition);

        this.context = definition.context();
        this.context.kompiled = context.kompiled;
        this.context.globalOptions = context.globalOptions;
        transformer = new KILtoBackendJavaKILTransformer(this.context);
    }
    
    public Definition getDefinition(){
        return this.definition;
    }

    @Override
    public KRunResult<KRunState> run(org.kframework.kil.Term cfg) throws KRunExecutionException {
        if (K.get_indexing_stats){
            IndexingStatistics.totalKrunStopwatch.start();
            KRunResult<KRunState> result = internalRun(cfg, -1);
            IndexingStatistics.totalKrunStopwatch.stop();
            IndexingStatistics.print();
            return result;
        } else{
            return internalRun(cfg, -1);
        }
    }

    private KRunResult<KRunState> internalRun(org.kframework.kil.Term cfg, int bound) throws KRunExecutionException {
        ConstrainedTerm result = javaKILRun(cfg, bound);
        org.kframework.kil.Term kilTerm = (org.kframework.kil.Term) result.term().accept(
                new BackendJavaKILtoKILTransformer(context));
        KRunResult<KRunState> returnResult = new KRunResult<KRunState>(new KRunState(kilTerm, context));
        UnparserFilter unparser = new UnparserFilter(true, K.color, K.parens, context);
        unparser.visitNode(kilTerm);
        returnResult.setRawOutput(unparser.getResult());
        return returnResult;
    }

    private ConstrainedTerm javaKILRun(org.kframework.kil.Term cfg, int bound) {
        if (K.get_indexing_stats){
            IndexingStatistics.preProcessStopWatch.start();
        }

        Term term = Term.of(cfg, definition);
        TermContext termContext = TermContext.of(definition, new PortableFileSystem());
        term = term.evaluate(termContext);

        if (K.pattern_matching) {
            GroundRewriter groundRewriter = new GroundRewriter(definition, termContext);
            ConstrainedTerm rewriteResult = new ConstrainedTerm(groundRewriter.rewrite(term, bound), termContext);
            return rewriteResult;
        } else {
            SymbolicRewriter symbolicRewriter = new SymbolicRewriter(definition);
            SymbolicConstraint constraint = new SymbolicConstraint(termContext);
            ConstrainedTerm constrainedTerm = new ConstrainedTerm(term, constraint, termContext);
            if (K.get_indexing_stats){
                IndexingStatistics.preProcessStopWatch.stop();
            }
            ConstrainedTerm rewriteResult;
            if (K.get_indexing_stats) {
                IndexingStatistics.totalRewriteStopwatch.start();
                rewriteResult = symbolicRewriter.rewrite(constrainedTerm, bound);
                IndexingStatistics.totalRewriteStopwatch.stop();
            } else {
                rewriteResult = symbolicRewriter.rewrite(constrainedTerm, bound);
            }
            return rewriteResult;
        }
    }

    @Override
    public KRunProofResult<Set<org.kframework.kil.Term>> prove(Module module, org.kframework.kil.Term cfg) throws KRunExecutionException {
        TermContext termContext = TermContext.of(definition, new PortableFileSystem());
        Map<org.kframework.kil.Term, org.kframework.kil.Term> substitution = null;
        if (cfg != null) {
            cfg = run(cfg).getResult().getRawResult();
            ConfigurationSubstitutionVisitor configurationSubstitutionVisitor =
                    new ConfigurationSubstitutionVisitor(context);
            configurationSubstitutionVisitor.visitNode(cfg);
            substitution = configurationSubstitutionVisitor.getSubstitution();
//            System.out.println(substitution);
            Module mod = module;
            mod = (Module) new Substitution(substitution,context).visitNode(module);
//                System.out.println(mod.toString());
            module = mod;
        }
        try {
            module = new SpecificationCompilerSteps(context).compile(module, null);
        } catch (CompilerStepDone e) {
            assert false: "dead code";
        }
        List<ConstrainedTerm> proofResults = new ArrayList<ConstrainedTerm>();

        DataStructureToLookupUpdate mapTransformer = new DataStructureToLookupUpdate(context);

        List<Rule> rules = new ArrayList<Rule>();
        for (org.kframework.kil.ModuleItem moduleItem : module.getItems()) {
            if (!(moduleItem instanceof org.kframework.kil.Rule)) {
                continue;
            }

            Rule rule = transformer.transformRule(
                    (org.kframework.kil.Rule) mapTransformer.visitNode(moduleItem),
                    definition);
            Rule freshRule = rule.getFreshRule(termContext);
            rules.add(freshRule);
        }

        SymbolicRewriter symbolicRewriter = new SymbolicRewriter(definition);
        for (org.kframework.kil.ModuleItem moduleItem : module.getItems()) {
            if (!(moduleItem instanceof org.kframework.kil.Rule)) {
                continue;
            }

            org.kframework.kil.Rule kilRule = (org.kframework.kil.Rule) moduleItem;
            org.kframework.kil.Term kilLeftHandSide
                    = ((org.kframework.kil.Rewrite) kilRule.getBody()).getLeft();
            org.kframework.kil.Term kilRightHandSide =
                    ((org.kframework.kil.Rewrite) kilRule.getBody()).getRight();
            org.kframework.kil.Term kilRequires = kilRule.getRequires();
            org.kframework.kil.Term kilEnsures = kilRule.getEnsures();

            org.kframework.kil.Rule kilDummyRule = new org.kframework.kil.Rule(
                    kilRightHandSide,
                    MetaK.kWrap(org.kframework.kil.KSequence.EMPTY, "k"),
                    kilRequires,
                    kilEnsures,
                    context);
            Rule dummyRule = transformer.transformRule(
                    (org.kframework.kil.Rule) mapTransformer.visitNode(kilDummyRule),
                    definition);

            SymbolicConstraint initialConstraint = new SymbolicConstraint(termContext);
            //initialConstraint.addAll(rule.condition());
            initialConstraint.addAll(dummyRule.requires());
            ConstrainedTerm initialTerm = new ConstrainedTerm(
                    transformer.transformTerm(kilLeftHandSide, definition),
                    initialConstraint,
                    termContext);

            SymbolicConstraint targetConstraint = new SymbolicConstraint(termContext);
            targetConstraint.addAll(dummyRule.ensures());
            ConstrainedTerm targetTerm = new ConstrainedTerm(
                    dummyRule.leftHandSide().evaluate(termContext),
                    dummyRule.lookups().getSymbolicConstraint(termContext),
                    targetConstraint,
                    termContext);

            proofResults.addAll(symbolicRewriter.proveRule(initialTerm, targetTerm, rules));
        }

        System.err.println(proofResults.isEmpty());
        System.err.println(proofResults);

        return new KRunProofResult<Set<org.kframework.kil.Term>>(
                proofResults.isEmpty(), Collections.<org.kframework.kil.Term>emptySet());
    }

    @Override
    public KRunResult<SearchResults> search(
            Integer bound,
            Integer depth,
            SearchType searchType,
            org.kframework.kil.Rule pattern,
            org.kframework.kil.Term cfg,
            RuleCompilerSteps compilationInfo) throws KRunExecutionException {

        if (K.get_indexing_stats){
            IndexingStatistics.totalSearchStopwatch.start();
        }

        SymbolicRewriter symbolicRewriter = new SymbolicRewriter(definition);
        FileSystem fs = new PortableFileSystem();

        // Change Map, List, and Set to MyMap, MyList, and MySet.
        CompileToBuiltins builtinTransformer = new CompileToBuiltins(context);
        pattern = (org.kframework.kil.Rule)builtinTransformer.visit(pattern, null);
        cfg = (org.kframework.kil.Term) builtinTransformer.visitNode(cfg);

        TermContext termContext = TermContext.of(definition, fs);
        List<Rule> claims = Collections.emptyList();
        if (bound == null) {
            bound = -1;
        }
        if (depth == null) {
            depth = -1;
        }

        // The pattern needs to be a rewrite in order for the transformer to be
        // able to handle it, so we need to give it a right-hand-side.
        org.kframework.kil.Cell c = new org.kframework.kil.Cell();
        c.setLabel("generatedTop");
        c.setContents(new org.kframework.kil.Bag());
        pattern.setBody(new org.kframework.kil.Rewrite(pattern.getBody(), c, context));
        Rule patternRule = transformer.transformRule(pattern, definition);

        List<SearchResult> searchResults = new ArrayList<SearchResult>();
        List<Map<Variable,Term>> hits;
        if (K.pattern_matching) {
            Term initialTerm = Term.of(cfg, definition);
            Term targetTerm = null;
            GroundRewriter rewriter = new GroundRewriter(definition, termContext);
            hits = rewriter.search(initialTerm, targetTerm, claims,
                    patternRule, bound, depth, searchType);
        } else {
            ConstrainedTerm initialTerm = new ConstrainedTerm(Term.of(cfg, definition), termContext);
            ConstrainedTerm targetTerm = null;
            SymbolicRewriter rewriter = new SymbolicRewriter(definition);
            hits = rewriter.search(initialTerm, targetTerm, claims,
                    patternRule, bound, depth, searchType);
        }

        for (Map<Variable,Term> map : hits) {
            // Construct substitution map from the search results
            Map<String, org.kframework.kil.Term> substitutionMap =
                    new HashMap<String, org.kframework.kil.Term>();
            for (Variable var : map.keySet()) {
                org.kframework.kil.Term kilTerm =
                        (org.kframework.kil.Term) map.get(var).accept(
                                new BackendJavaKILtoKILTransformer(context));
                substitutionMap.put(var.toString(), kilTerm);
            }

            // Apply the substitution to the pattern
            org.kframework.kil.Term rawResult =
                    (org.kframework.kil.Term) new SubstitutionFilter(substitutionMap, context)
                        .visitNode(pattern.getBody());

            searchResults.add(new SearchResult(
                    new KRunState(rawResult, context),
                    substitutionMap,
                    compilationInfo,
                    context));
        }

        // TODO(ericmikida): Make the isDefaultPattern option set in some reasonable way
        KRunResult<SearchResults> searchResultsKRunResult = new KRunResult<>(new SearchResults(
                searchResults,
                null,
                false,
                context));

        if (K.get_indexing_stats){
            IndexingStatistics.totalSearchStopwatch.stop();
            IndexingStatistics.print();
        }
        return searchResultsKRunResult;
    }

    @Override
    public KRunResult<TestGenResults> generate(
            Integer bound,
            Integer depth,
            SearchType searchType,
            org.kframework.kil.Rule pattern,
            org.kframework.kil.Term cfg,
            RuleCompilerSteps compilationInfo) throws KRunExecutionException {
        // for now, test generation uses the "search-all" option
        // we hope to add strategies on top of this in the future
        if (searchType != SearchType.STAR) {
            throw new UnsupportedOperationException("Search type should be SearchType.STAR");
        }
        
        // TODO: get rid of this ugly hack
        Object o = ((org.kframework.kil.Bag) ((org.kframework.kil.Cell) cfg).getContents()).getContents().get(0);
        o = ((org.kframework.kil.Bag) ((org.kframework.kil.Cell) o).getContents()).getContents().get(1);
        Map<org.kframework.kil.Term, org.kframework.kil.Term> stateMap = new HashMap<org.kframework.kil.Term, org.kframework.kil.Term>();
        stateMap.put((org.kframework.kil.Term) org.kframework.kil.GenericToken.kAppOf("Id", "x"), (org.kframework.kil.Term) org.kframework.kil.IntBuiltin.kAppOf("3"));
//        stateMap.put((org.kframework.kil.Term) org.kframework.kil.GenericToken.kAppOf("Id", "y"), (org.kframework.kil.Term) org.kframework.kil.IntBuiltin.kAppOf("2"));
        stateMap.put((org.kframework.kil.Term) org.kframework.kil.GenericToken.kAppOf("Id", "$1"), (org.kframework.kil.Term) org.kframework.kil.IntBuiltin.kAppOf("1"));
        ((org.kframework.kil.Cell) o)
                .setContents(new org.kframework.kil.MapBuiltin(context
                        .dataStructureSortOf("Map"),
                        Collections.<org.kframework.kil.Term>emptyList(), 
                        stateMap));
        
        SymbolicRewriter symbolicRewriter = new SymbolicRewriter(definition);
        final TermContext termContext = TermContext.of(definition, new PortableFileSystem());
        ConstrainedTerm initCfg = new ConstrainedTerm(Term.of(cfg, definition), termContext);

        List<TestGenResult> generatorResults = new ArrayList<TestGenResult>();

        List<String> strRules = new ArrayList<String>();
        for (Rule rule : definition.rules()) strRules.add(rule.toString());
        Collections.sort(strRules);
        for (String s : strRules) {
            System.out.println(s);
        }
        
        if (bound == null) {
            bound = -1;
        }
        if (depth == null) {
            depth = -1;
        }
        
        for (Rule rule : definition.functionRules().values()) {
            System.out.println(rule);
        }

        List<ConstrainedTerm> resultCfgs = symbolicRewriter.generate(initCfg, null, null, bound, depth);

        for (ConstrainedTerm result : resultCfgs) {
            // construct the generated program by applying the substitution
            // obtained from the result configuration to the initial one
            Term pgm = Term.of(cfg, definition);
            pgm = pgm.substituteWithBinders(result.constraint().substitution(), termContext);
            
            /* concretize the pgm; only reasonable when the 2nd phase of the
             * test generation is performed */
            if (TestCaseGenerationSettings.TWO_PHASE_GENERATION) {
                final Map<Variable, Term> subst = new HashMap<Variable, Term>();
                pgm.accept(new BottomUpVisitor() {
                   @Override
                   public void visit(Variable var) {
                       subst.put(var, TestCaseGenerationUtil.getSimplestTermOfSort(var.sort(), termContext));
                   }
                });
                pgm = pgm.substituteWithBinders(subst, termContext);
            }

            /* translate back to generic KIL term */
            org.kframework.kil.Term pgmTerm = (org.kframework.kil.Term) pgm.accept(
                    new BackendJavaKILtoKILTransformer(context));

            org.kframework.kil.Term kilTerm = (org.kframework.kil.Term) result.term().accept(
                    new BackendJavaKILtoKILTransformer(context));

            generatorResults.add(new TestGenResult(
                    new KRunState(kilTerm, context),
                    Collections.singletonMap("B:Bag", kilTerm),
                    compilationInfo,
                    context,
                    pgmTerm,
                    result.constraint()));
        }

        return new KRunResult<TestGenResults>(new TestGenResults(
                generatorResults,
                null,
                true,
                context));
    }

    @Override
    public KRunProofResult<DirectedGraph<KRunState, Transition>> modelCheck(
            org.kframework.kil.Term formula,
            org.kframework.kil.Term cfg) throws KRunExecutionException {
        throw new UnsupportedBackendOptionException("--ltlmc");
    }

    @Override
    public KRunResult<KRunState> step(org.kframework.kil.Term cfg, int steps)
            throws KRunExecutionException {
        return internalRun(cfg, steps);
    }
    
    /*
     * author: Liyi Li
     * to get the symbolic rewriter
     */
    public SymbolicRewriter getSimulationRewriter(){
        
        return this.simulationRewriter;
    }
    
    public void initialSimulationRewriter(){
        
        this.simulationRewriter = new SymbolicRewriter(definition);
    }
    
    /* author: Liyi Li
     * a function return all the next step of a given simulation term
     */
    public ConstrainedTerm simulationSteps(ConstrainedTerm cfg)
            throws KRunExecutionException {
        
        ConstrainedTerm result = this.simulationRewriter.computeSimulationStep(cfg);
  
        return result;
    }
    
    /* author: Liyi Li
     * a function return all the next steps of a given term
     */
    public ArrayList<ConstrainedTerm> steps(ConstrainedTerm cfg)
            throws KRunExecutionException {

        ArrayList<ConstrainedTerm> results = this.simulationRewriter.rewriteAll(cfg);    
        


        return results;
    }
    
 
    @Override
    public KRunDebugger debug(org.kframework.kil.Term cfg) {
        throw new UnsupportedBackendOptionException("--debug");
    }

    @Override
    public KRunDebugger debug(DirectedGraph<KRunState, Transition> graph) {
        throw new UnsupportedBackendOptionException("--debug");
    }

    @Override
    public void setBackendOption(String key, Object value) {
    }

}
