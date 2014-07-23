package org.kframework.compile;

import java.io.File;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.kframework.backend.java.symbolic.JavaExecutionOptions;
import org.kframework.compile.utils.ConfigurationStructureMap;
import org.kframework.kil.ASTNode;
import org.kframework.kil.CellDataStructure;
import org.kframework.kil.DataStructureSort;
import org.kframework.kil.Production;
import org.kframework.kil.loader.Context;
import org.kframework.kompile.KompileOptions;
import org.kframework.krun.KRunOptions;
import org.kframework.krun.KRunOptions.ConfigurationCreationOptions;
import org.kframework.main.GlobalOptions;
import org.kframework.utils.Poset;
import org.kframework.utils.options.SMTOptions;

import com.google.common.collect.Multimap;

public class CompiledDefinition extends Context implements Serializable {

    private final KompileOptions kompileOptions;

    public CompiledDefinition(Context context) {
        this.kompileOptions = context.kompileOptions();
    }

    @Override
    public KompileOptions kompileOptions() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public KRunOptions krunOptions() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public GlobalOptions globalOptions() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public SMTOptions smtOptions() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public ConfigurationCreationOptions ccOptions() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public JavaExecutionOptions javaExecutionOptions() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Set<Production> productions() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Multimap<String, Production> sorts() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Map<String, Production> listSorts() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Map<String, Production> tokenSorts() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Multimap<String, Production> klabels() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Map<String, Production> listKLabels() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Map<String, ASTNode> locations() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Map<String, Production> canonicalBracketForSort() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Map<String, String> freshFunctionNames() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Map<String, DataStructureSort> dataStructureSorts() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String startSymbolPgm() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public ConfigurationStructureMap configurationStructureMap() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Map<String, String> configVarSorts() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Map<String, CellDataStructure> cellDataStructures() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public int maxConfigurationLevel() {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public List<String> komputationCellNames() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public File kompiled() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public File dotk() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Poset subsorts() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Poset assocLeft() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Poset assocRight() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Poset priorities() {
        // TODO Auto-generated method stub
        return null;
    }
}
