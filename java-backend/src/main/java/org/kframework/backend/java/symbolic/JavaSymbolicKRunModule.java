// Copyright (c) 2014-2015 K Team. All Rights Reserved.
package org.kframework.backend.java.symbolic;

import com.beust.jcommander.JCommander;
import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Names;
import org.kframework.Rewriter;
import org.kframework.backend.java.kil.Definition;
import org.kframework.backend.java.kil.GlobalContext;
import org.kframework.kil.loader.Context;
import org.kframework.krun.KRunOptions;
import org.kframework.krun.KRunOptions.ConfigurationCreationOptions;
import org.kframework.main.AnnotatedByDefinitionModule;
import org.kframework.utils.BinaryLoader;
import org.kframework.utils.Stopwatch;
import org.kframework.utils.errorsystem.KExceptionManager;
import org.kframework.utils.file.FileUtil;
import org.kframework.utils.inject.Annotations;
import org.kframework.utils.inject.DefinitionScoped;
import org.kframework.utils.inject.Options;
import org.kframework.utils.inject.RequestScoped;
import org.kframework.utils.inject.Spec;

import java.util.List;
import java.util.function.Function;

public class JavaSymbolicKRunModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(Stage.class).toInstance(Stage.REWRITING);

        bind(JavaExecutionOptions.class).in(RequestScoped.class);
        bind(Boolean.class).annotatedWith(FreshRules.class).toInstance(false);

        Multibinder<Object> optionsBinder = Multibinder.newSetBinder(binder(), Object.class, Options.class);
        optionsBinder.addBinding().to(JavaExecutionOptions.class);
        Multibinder<Class<?>> experimentalOptionsBinder = Multibinder.newSetBinder(binder(), new TypeLiteral<Class<?>>() {}, Options.class);
        experimentalOptionsBinder.addBinding().toInstance(JavaExecutionOptions.class);

    }

    public static class CommonModule extends AbstractModule {

        @Override
        protected void configure() {
            install(new JavaSymbolicCommonModule());

            bind(SymbolicRewriter.class);
            bind(GlobalContext.class);
            bind(InitializeRewriter.class);

            MapBinder<String, Integer> checkPointBinder = MapBinder.newMapBinder(
                    binder(), String.class, Integer.class, Names.named("checkpointIntervalMap"));
            checkPointBinder.addBinding("java").toInstance(new Integer(500));

            MapBinder<String, Function<org.kframework.definition.Module, Rewriter>> rewriterBinder = MapBinder.newMapBinder(
                    binder(), TypeLiteral.get(String.class), new TypeLiteral<Function<org.kframework.definition.Module, Rewriter>>() {
                    });
            rewriterBinder.addBinding("java").to(InitializeRewriter.class);

        }

        @Provides @DefinitionScoped
        Definition javaDefinition(
                BinaryLoader loader,
                Context context,
                FileUtil files,
                KExceptionManager kem,
                Stopwatch sw) {
            Definition def = loader.loadOrDie(Definition.class,
                    files.resolveKompiled(JavaSymbolicBackend.DEFINITION_FILENAME));
            def.setContext(context);
            def.setKem(kem);
            sw.printIntermediate("Deserialize internal definition representation");
            return def;
        }
    }

    public static class SimulationModule extends AnnotatedByDefinitionModule {

        private final List<Module> definitionSpecificModules;

        public SimulationModule(List<Module> definitionSpecificModules) {
            this.definitionSpecificModules = definitionSpecificModules;
        }

        @Override
        protected void configure() {
            exposeBindings(definitionSpecificModules, Spec.class, Annotations::spec);
        }

        @Provides
        ConfigurationCreationOptions ccOptions(KRunOptions options) {
            ConfigurationCreationOptions simulationCCOptions = new ConfigurationCreationOptions();
            new JCommander(simulationCCOptions,
                    options.experimental.simulation.toArray(
                            new String[options.experimental.simulation.size()]));
            return simulationCCOptions;
        }
    }
}
