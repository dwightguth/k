// Copyright (c) 2014-2015 K Team. All Rights Reserved.
package org.kframework.backend.java.symbolic;

import org.kframework.utils.inject.RequestScoped;
import com.beust.jcommander.Parameter;

@RequestScoped
public final class JavaExecutionOptions {

    @Parameter(names="--deterministic-functions", description="Throw assertion failure during "
        + "execution in the java backend if function definitions are not deterministic.")
    public boolean deterministicFunctions = false;

    @Parameter(names="--pattern-matching", description="Use pattern-matching rather than "
        + "unification to drive rewriting in the Java backend.")
    public boolean patternMatching = false;

        @Parameter(names="--audit-file", description="Enforce that the rule applied at the step specified by "
            + "--apply-step is a rule at the specified file and line, or fail with an error explaining why "
            + "the rule did not apply.")
    public String auditingFile;

    @Parameter(names="--audit-line", description="Enforce that the rule applied at the step specified by "
            + "--apply-step is a rule at the specified file and line, or fail with an error explaining why "
            + "the rule did not apply.")
    public Integer auditingLine;

    @Parameter(names="--audit-step", description="Enforce that the rule applied at the specified step is a rule "
            + "tagged with the value of --apply-tag, or fail with an error explaining why the rule did not apply.")
    public Integer auditingStep;
}

