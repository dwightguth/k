package org.kframework.backend.java.symbolic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.kframework.backend.java.kil.GlobalContext;
import org.kframework.backend.java.kil.Rule;
import org.kframework.backend.java.kil.Term;
import org.kframework.backend.java.kil.TermContext;
import org.kframework.backend.java.kil.Variable;
import org.kframework.backend.java.util.JavaKRunState;
import org.kframework.compile.utils.RuleCompilerSteps;
import org.kframework.kil.loader.Context;
import org.kframework.krun.SubstitutionFilter;
import org.kframework.krun.api.KRunGraph;
import org.kframework.krun.api.KRunState;
import org.kframework.krun.api.RewriteRelation;
import org.kframework.krun.api.SearchResult;
import org.kframework.krun.api.SearchResults;
import org.kframework.krun.api.SearchType;
import org.kframework.krun.tools.Executor;

public abstract class AbstractRewriter implements Executor {

    protected abstract KRunState rewrite(
            Term subject,
            int bound,
            boolean computeGraph,
            TermContext context);
    protected abstract List<Map<Variable,Term>> search(
            Term initialTerm,
            Rule pattern,
            int bound,
            int depth,
            SearchType searchType,
            TermContext termContext);
    protected abstract KRunGraph getExecutionGraph();

    private final Context context;
    private final KRunState.Counter counter;
    private final KILtoBackendJavaKILTransformer kilTransformer;
    private final GlobalContext globalContext;

    public AbstractRewriter(
            KILtoBackendJavaKILTransformer kilTransformer,
            Context context,
            KRunState.Counter counter,
            GlobalContext globalContext) {
        this.kilTransformer = kilTransformer;
        this.context = context;
        this.counter = counter;
        this.globalContext = globalContext;
    }

    @Override
    public RewriteRelation run(org.kframework.kil.Term cfg, Integer steps, boolean computeGraph) {
        if (steps == null) {
            steps = -1;
        }
        Term term = kilTransformer.transformAndEval(cfg);
        TermContext termContext = TermContext.of(globalContext);
        termContext.setTopTerm(term);
        KRunState finalState = rewrite(term, steps, computeGraph, termContext);
        return new RewriteRelation(finalState, getExecutionGraph());
    }

    @Override
    public SearchResults search(Integer bound, Integer depth,
            SearchType searchType, org.kframework.kil.Rule pattern, org.kframework.kil.Term cfg,
            RuleCompilerSteps compilationInfo) {
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
        Rule patternRule = kilTransformer.transformAndEval(pattern);

        List<SearchResult> searchResults = new ArrayList<SearchResult>();
        List<Map<Variable,Term>> hits;
        Term initialTerm = kilTransformer.transformAndEval(cfg);
        TermContext termContext = TermContext.of(globalContext);
        termContext.setTopTerm(initialTerm);
        hits = search(initialTerm, patternRule, bound, depth, searchType, termContext);

        for (Map<Variable,Term> map : hits) {
            // Construct substitution map from the search results
            Map<String, org.kframework.kil.Term> substitutionMap =
                    new HashMap<String, org.kframework.kil.Term>();
            for (Variable var : map.keySet()) {
                org.kframework.kil.Term kilTerm =
                        (org.kframework.kil.Term) map.get(var).accept(
                                new BackendJavaKILtoKILTransformer(context));
                substitutionMap.put(var.name(), kilTerm);
            }

            // Apply the substitution to the pattern
            org.kframework.kil.Term rawResult =
                    (org.kframework.kil.Term) new SubstitutionFilter(substitutionMap, context)
                        .visitNode(pattern.getBody());

            searchResults.add(new SearchResult(
                    new JavaKRunState(rawResult, counter),
                    substitutionMap,
                    compilationInfo));
        }

        SearchResults retval = new SearchResults(
                searchResults,
                null);

        return retval;
    }
}
