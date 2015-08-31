// Copyright (c) 2015 K Team. All Rights Reserved.
package org.kframework.krun.modes;

import com.google.inject.Inject;
import org.kframework.Rewriter;
import org.kframework.RewriterResult;
import org.kframework.kompile.CompiledDefinition;
import org.kframework.kore.K;
import org.kframework.krun.KRunOptions;
import org.kframework.utils.errorsystem.KExceptionManager;
import org.kframework.utils.file.FileUtil;

import java.util.Optional;

/**
 * Execution Mode for Conventional KRun
 */
public class KRunExecutionMode implements ExecutionMode<RewriterResult> {

    private final KRunOptions kRunOptions;
    private final KExceptionManager kem;
    private final FileUtil files;

    @Inject
    public KRunExecutionMode(KRunOptions kRunOptions, KExceptionManager kem, FileUtil files) {
        this.kRunOptions = kRunOptions;
        this.kem = kem;
        this.files = files;
    }


    @Override
    public RewriterResult execute(K k, Rewriter rewriter, CompiledDefinition compiledDefinition) {
        return rewriter.execute(k, Optional.ofNullable(kRunOptions.depth));
    }
}




