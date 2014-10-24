// Copyright (c) 2012-2014 K Team. All Rights Reserved.
package org.kframework.main;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

import org.kframework.kast.KastFrontEnd;
import org.kframework.kompile.KompileFrontEnd;
import org.kframework.krun.KRunFrontEnd;
import org.kframework.ktest.KTestFrontEnd;
import org.kframework.utils.errorsystem.KExceptionManager;
import org.kframework.utils.errorsystem.KExceptionManager.KEMException;
import org.kframework.utils.file.FileSystemModule;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.ProvisionException;
import com.google.inject.spi.Message;
import com.martiansoftware.nailgun.NGContext;

public class Main {

    /**
     * @param args
     *            - the running arguments for the K3 tool. First argument must be one of the following: kompile|kast|krun.
     * @throws IOException when loadDefinition fails
     */
    public static void main(String[] args) {
        if (args.length >= 1) {

            String[] args2 = Arrays.copyOfRange(args, 1, args.length);
            Injector injector = getInjector(args[0], args2);
            int result = runApplication(injector);
            System.exit(result);
        }
        invalidJarArguments();
    }

    public static void nailMain(NGContext context) {
        if (context.getArgs().length >= 1) {

            String[] args2 = Arrays.copyOfRange(context.getArgs(), 1, context.getArgs().length);
            Injector injector = getInjector(new File(context.getWorkingDirectory()), (Map)context.getEnv(), context.getArgs()[0], args2);
            int result = runApplication(injector);
            context.exit(result);
        }
        invalidJarArguments();
    }

    public static int runApplication(Injector injector) {
        KExceptionManager kem = injector.getInstance(KExceptionManager.class);

        kem.installForUncaughtExceptions();
        try {
            boolean succeeded = injector.getInstance(FrontEnd.class).main();
            return succeeded ? 0 : 1;
        } catch (ProvisionException e) {
            for (Message m : e.getErrorMessages()) {
                if (!(m.getCause() instanceof KEMException)) {
                    throw e;
                } else {
                    ((KEMException) m.getCause()).register(kem);
                }
            }
            kem.print();
            return 1;
        }
    }

    public static Injector getInjector(File workingDir, Map<String, String> env, String tool, String[] args2) {
        ServiceLoader<KModule> kLoader = ServiceLoader.load(KModule.class);
        List<KModule> kModules = new ArrayList<>();
        for (KModule m : kLoader) {
            kModules.add(m);
        }

        List<Module> modules = new ArrayList<>();

            switch (tool) {
                case "-kompile":
                    modules.addAll(KompileFrontEnd.getModules(args2));
                    for (KModule kModule : kModules) {
                        List<Module> ms = kModule.getKompileModules();
                        if (ms != null) {
                            modules.addAll(ms);
                        }
                    }
                    break;
                case "-ktest":
                    modules.addAll(KTestFrontEnd.getModules(args2));
                    for (KModule kModule : kModules) {
                        List<Module> ms = kModule.getKTestModules();
                        if (ms != null) {
                            modules.addAll(ms);
                        }
                    }
                    break;
                case "-kast":
                    modules.addAll(KastFrontEnd.getModules(args2));
                    for (KModule kModule : kModules) {
                        List<Module> ms = kModule.getKastModules();
                        if (ms != null) {
                            modules.addAll(ms);
                        }
                    }
                    break;
                case "-krun":
                    List<Module> definitionSpecificModules = new ArrayList<>();
                    definitionSpecificModules.addAll(KRunFrontEnd.getDefinitionSpecificModules(args2));
                    for (KModule kModule : kModules) {
                        List<Module> ms = kModule.getDefinitionSpecificKRunModules();
                        if (ms != null) {
                            definitionSpecificModules.addAll(ms);
                        }
                    }

                    modules.addAll(KRunFrontEnd.getModules(args2, definitionSpecificModules));
                    for (KModule kModule : kModules) {
                        List<Module> ms = kModule.getKRunModules(definitionSpecificModules);
                        if (ms != null) {
                            modules.addAll(ms);
                        }
                    }
                    break;
                case "-kpp":
                    modules = KppFrontEnd.getModules(args2);
                    break;
                default:
                    invalidJarArguments();
                    throw new AssertionError("unreachable");
        }
        if (modules.size() == 0) {
            //boot error, we should have printed it already
            System.exit(1);
        }
        modules.add(new FileSystemModule(workingDir, env));
        Injector injector = Guice.createInjector(modules);
        return injector;
    }

    private static void invalidJarArguments() {
        System.err.println("The first argument of K3 not recognized. Try -kompile, -kast, -krun, -ktest, -kserver, or -kpp.");
        System.exit(1);
    }

    public static Injector getInjector(String tool, String[] args2) {
        return getInjector(new File("."), System.getenv(), tool, args2);
    }
}
