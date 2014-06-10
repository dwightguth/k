// Copyright (c) 2012-2014 K Team. All Rights Reserved.
package org.kframework.kast;

import java.io.*;

import org.kframework.backend.maude.MaudeFilter;
import org.kframework.backend.unparser.IndentationOptions;
import org.kframework.backend.unparser.KastFilter;
import org.kframework.compile.FlattenModules;
import org.kframework.compile.transformers.AddTopCellConfig;
import org.kframework.kil.ASTNode;
import org.kframework.kil.loader.Context;
import org.kframework.kil.visitors.exceptions.ParseFailedException;
import org.kframework.kompile.KompileOptions;
import org.kframework.kompile.KompileOptions.Backend;
import org.kframework.krun.K;
import org.kframework.parser.ProgramLoader;
import org.kframework.utils.BinaryLoader;
import org.kframework.utils.Stopwatch;
import org.kframework.utils.StringUtil;
import org.kframework.utils.errorsystem.KException;
import org.kframework.utils.errorsystem.KException.ExceptionType;
import org.kframework.utils.errorsystem.KException.KExceptionGroup;
import org.kframework.utils.file.FileUtil;
import org.kframework.utils.file.KPaths;
import org.kframework.utils.general.GlobalSettings;
import org.kframework.utils.options.SortedParameterDescriptions;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;

public class KastFrontEnd {

    /**
     * 
     * @param args
     * @return true if the application terminated normally; false otherwise
     */
    public static boolean kast(String[] args) {
        KastOptions options = new KastOptions();
        options.global.initialize();
        try {
            JCommander jc = new JCommander(options, args);
            jc.setProgramName("kast");
            jc.setParameterDescriptionComparator(new SortedParameterDescriptions(KastOptions.Experimental.class));
            
            if (options.global.help) {
                StringBuilder sb = new StringBuilder();
                jc.usage(sb);
                System.out.print(StringUtil.finesseJCommanderUsage(sb.toString(), jc)[0]);
                return true;
            }
            
            if (options.helpExperimental) {
                StringBuilder sb = new StringBuilder();
                jc.usage(sb);
                System.out.print(StringUtil.finesseJCommanderUsage(sb.toString(), jc)[1]);
                return true;    
            }
            
            if (options.global.version) {
                String msg = FileUtil.getFileContent(KPaths.getKBase(false) + KPaths.VERSION_FILE);
                System.out.print(msg);
                return true;
            }

            String stringToParse = options.stringToParse();
            String source = options.source();

        
            org.kframework.kil.Definition javaDef = null;
            File compiledFile = options.directory();
            Context context;
            KompileOptions kompileOptions = BinaryLoader.load(KompileOptions.class, new File(compiledFile, "kompile-options.bin").getAbsolutePath());
            
            File defXml;
            if (kompileOptions.backend == Backend.MAUDE) {
                defXml = new File(compiledFile.getAbsolutePath() + "/defx-maude.bin");
            } else if (kompileOptions.backend == Backend.JAVA) {
                defXml = new File(compiledFile.getAbsolutePath() + "/defx-java.bin");
            } else {
                throw new AssertionError("currently only two execution backends are supported: MAUDE and JAVA");
            }
            if (!defXml.exists()) {
                GlobalSettings.kem.register(new KException(ExceptionType.ERROR, KExceptionGroup.CRITICAL, 
                        "Could not find the compiled definition.", null, defXml.getAbsolutePath()));
            }
            
            //merge kast options into kompile options object
            kompileOptions.global = options.global;
            context = new Context(kompileOptions);
            context.kompiled = compiledFile;
            
            javaDef = (org.kframework.kil.Definition) BinaryLoader.load(defXml.toString());
            javaDef = new FlattenModules(context).compile(javaDef, null);
            javaDef = (org.kframework.kil.Definition) new AddTopCellConfig(
                    context).visitNode(javaDef);
            javaDef.preprocess(context);

            String sort = options.sort(context);

            try {
                ASTNode out = ProgramLoader.processPgm(stringToParse, source, javaDef, sort, context, options.parser);
                StringBuilder kast;
                if (options.experimental.pretty) {
                    IndentationOptions indentationOptions = new IndentationOptions(options.experimental.maxWidth(), 
                            options.experimental.auxTabSize, options.experimental.tabSize);
                    KastFilter kastFilter = new KastFilter(indentationOptions, options.experimental.nextLine, context);
                    kastFilter.visitNode(out);
                    kast = kastFilter.getResult();
                } else {
                    MaudeFilter maudeFilter = new MaudeFilter(context);
                    maudeFilter.visitNode(out);
                    kast = maudeFilter.getResult();
                    kast.append(K.lineSeparator);
                }
    
                try {
                    Writer outWriter = new OutputStreamWriter(System.out);
                    try {
                        FileUtil.toWriter(kast, outWriter);
                    } finally {
                        outWriter.flush();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
    
                Stopwatch.instance().printIntermediate("Maudify Program");
                Stopwatch.instance().printTotal("Total");
                return true;
            } catch (ParseFailedException e) {
                e.report();
                return false;
            }
        } catch (ParameterException ex) {
            GlobalSettings.kem.register(new KException(ExceptionType.ERROR, KExceptionGroup.CRITICAL, ex.getMessage()));
            return false;
        }
    }
}

// vim: noexpandtab
