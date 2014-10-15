// Copyright (c) 2014 K Team. All Rights Reserved.
package org.kframework.utils.inject;

import java.io.File;

import org.kframework.main.Tool;
import org.kframework.utils.BinaryLoader;
import org.kframework.utils.Stopwatch;
import org.kframework.utils.file.FileUtil;
import org.kframework.utils.general.GlobalSettings;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;

public class CommonModule extends AbstractModule {

    @Override
    protected void configure() {
        requestStaticInjection(Stopwatch.class);
        requestStaticInjection(GlobalSettings.class);
        requestStaticInjection(BinaryLoader.class);
        requestStaticInjection(Tool.class);

        bind(File.class).annotatedWith(WorkingDir.class).toInstance(new File(System.getProperty("user.dir")));

        //TODO(dwightguth): when we upgrade to Guice 4.0, add
        //binder().requireAtInjectOnConstructors()
    }

    @Provides @TempDir
    File tempDir(@WorkingDir File workingDir, Tool tool) {
        return new File(workingDir, FileUtil.generateUniqueFolderName("." + tool.name().toLowerCase()));
    }

}
