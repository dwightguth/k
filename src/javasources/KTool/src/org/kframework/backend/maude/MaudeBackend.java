// Copyright (c) 2012-2014 K Team. All Rights Reserved.
package org.kframework.backend.maude;

import org.kframework.backend.BasicBackend;
import org.kframework.compile.sharing.FreshVariableNormalizer;
import org.kframework.compile.sharing.SortRulesNormalizer;
import org.kframework.kil.Definition;
import org.kframework.kil.loader.Context;
import org.kframework.utils.Stopwatch;
import org.kframework.utils.StringBuilderUtil;
import org.kframework.utils.file.FileUtil;

public class MaudeBackend extends BasicBackend {

    public MaudeBackend(Stopwatch sw, Context context) {
        super(sw, context);
    }

    @Override
    public void run(Definition definition) {
        definition = (Definition) new FreshVariableNormalizer(context).visitNode(definition);
        definition = (Definition) new SortRulesNormalizer(context).visitNode(definition);
        MaudeFilter maudeFilter = new MaudeFilter(context);
        maudeFilter.visitNode(definition);

        final String mainModule = definition.getMainModule();
        StringBuilder maudified = maudeFilter.getResult();
        StringBuilderUtil.replaceFirst(maudified, mainModule, mainModule + "-BASE");

        FileUtil.save(context.kompiled().getAbsolutePath() + "/base.maude", maudified);
        sw.printIntermediate("Generating Maude file");
    }

    @Override
    public String getDefaultStep() {
        return "LastStep";
    }
}
