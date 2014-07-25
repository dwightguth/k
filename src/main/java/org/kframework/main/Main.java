// Copyright (c) 2012-2014 K Team. All Rights Reserved.
package org.kframework.main;

import java.io.IOException;
import java.lang.Thread.UncaughtExceptionHandler;
import java.util.Arrays;

import org.fusesource.jansi.AnsiConsole;
import org.kframework.krun.K;
import org.kframework.utils.Stopwatch;
import org.kframework.utils.errorsystem.KExceptionManager.KEMException;
import org.kframework.utils.general.GlobalSettings;

import com.martiansoftware.nailgun.NGContext;

public class Main {

    /**
     * @param args
     *            - the running arguments for the K3 tool. First argument must be one of the following: kompile|kast|krun.
     * @throws IOException when loadDefinition fails
     */

    public static void nailMain(NGContext context) {
        System.setProperty("user.dir", context.getWorkingDirectory());
        main(context.getArgs());
    }

    public static void main(String[] args) {
        Stopwatch.instance();
        AnsiConsole.systemInstall();

        Thread.currentThread().setUncaughtExceptionHandler(new UncaughtExceptionHandler() {

            @Override
            public void uncaughtException(Thread t, Throwable e) {
                AnsiConsole.systemUninstall();
                e.printStackTrace();
            }
        });

        boolean succeeded = true;
        if (args.length >= 1) {
            String[] args2 = Arrays.copyOfRange(args, 1, args.length);
            try {
                switch (args[0]) {
                    case "-kompile":
                        K.setTool(K.Tool.KOMPILE);
                        org.kframework.kompile.KompileFrontEnd.main(args2);
                        break;
                    case "-kagreg":
                        K.setTool(K.Tool.OTHER);
                        org.kframework.kagreg.KagregFrontEnd.kagreg(args2);
                        break;
                    case "-kcheck":
                        K.setTool(K.Tool.OTHER);
                        succeeded = org.kframework.kcheck.KCheckFrontEnd.kcheck(args2);
                        break;
                    case "-ktest":
                        K.setTool(K.Tool.KTEST);
                        succeeded = org.kframework.ktest.KTest.main(args2);
                        break;
                    case "-kast":
                        K.setTool(K.Tool.KAST);
                        succeeded = org.kframework.kast.KastFrontEnd.kast(args2);
                        break;
                    case "-krun":
                        K.setTool(K.Tool.KRUN);
                        succeeded = org.kframework.krun.KRunFrontEnd.execute_Krun(args2);
                        break;
                    case "-kpp":
                        K.setTool(K.Tool.OTHER);
                        Kpp.codeClean(args2);
                        break;
                    default:
                        invalidJarArguments();
                        break;
                }
            } catch (KEMException e) {
                // terminated with errors, so we need to return nonzero error code.
                GlobalSettings.kem.print();
                AnsiConsole.systemUninstall();
                System.exit(1);
            }

            GlobalSettings.kem.print();
            AnsiConsole.systemUninstall();
            System.exit(succeeded ? 0 : 1);
        }
        invalidJarArguments();
    }

    private static void invalidJarArguments() {
        System.err.println("The first argument of K3 not recognized. Try -kompile, -kast, -krun or -kpp.");
        AnsiConsole.systemUninstall();
        System.exit(1);
    }
}
