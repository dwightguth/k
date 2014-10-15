// Copyright (c) 2014 K Team. All Rights Reserved.
package org.kframework.utils.inject;

import java.io.File;
import java.io.FilenameFilter;

import org.kframework.kil.loader.Context;
import org.kframework.kompile.KompileOptions;
import org.kframework.main.GlobalOptions;
import org.kframework.utils.BinaryLoader;
import org.kframework.utils.Stopwatch;
import org.kframework.utils.errorsystem.KExceptionManager;
import org.kframework.utils.file.FileUtil;
import org.kframework.utils.options.DefinitionLoadingOptions;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;

public class DefinitionLoadingModule extends AbstractModule {

    @Override
    protected void configure() {
    }

    @Provides @Singleton
    Context context(
            BinaryLoader loader,
            DefinitionLoadingOptions options,
            GlobalOptions global,
            Stopwatch sw,
            KExceptionManager kem,
            FileUtil files) {
        Context context = loader.loadOrDie(Context.class, files.resolveKompiled("context.bin"));
        context.files = files;

        sw.printIntermediate("Loading serialized context");

        sw.printIntermediate("Initializing definition paths");
        return context;
    }

    @Provides
    KompileOptions kompileOptions(Context context, FileUtil files) {
        context.kompileOptions.setFiles(files);
        return context.kompileOptions;
    }

    @Provides @KompiledDir
    File definition(@DefinitionDir File defDir, KExceptionManager kem) {
        File directory = null;
        File[] dirs = defDir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File current, String name) {
                return new File(current, name).isDirectory();
            }
        });

        for (int i = 0; i < dirs.length; i++) {
            if (dirs[i].getAbsolutePath().endsWith("-kompiled")) {
                if (directory != null) {
                    kem.registerCriticalError("Multiple compiled definitions found in the "
                            + "current working directory: " + directory.getAbsolutePath() + " and " +
                            dirs[i].getAbsolutePath());
                } else {
                    directory = dirs[i];
                }
            }
        }

        if (directory == null) {
            kem.registerCriticalError("Could not find a compiled definition. " +
                    "Use --directory to specify one.");
        }
        if (!directory.isDirectory()) {
            kem.registerCriticalError("Does not exist or not a directory: " + directory.getAbsolutePath());
        }
        return directory;
    }

    @Provides @DefinitionDir
    File directory(DefinitionLoadingOptions options, @WorkingDir File workingDir, KExceptionManager kem) {
        File directory;
        if (options.directory == null) {
            if (System.getenv("KRUN_COMPILED_DEF") != null) {
                File f = new File(System.getenv("KRUN_COMPILED_DEF"));
                if (f.isAbsolute()) {
                    directory = f;
                } else {
                    directory = new File(workingDir, System.getenv("KRUN_COMPILED_DEF"));
                }
            } else {
                directory = workingDir;
            }
        } else {
            File f = new File(options.directory);
            if (f.isAbsolute()) {
                directory = f;
            } else {
                directory = new File(workingDir, options.directory);
            }
        }
        if (!directory.isDirectory()) {
            kem.registerCriticalError("Does not exist or not a directory: " + directory.getAbsolutePath());
        }
        return directory;
    }
}
