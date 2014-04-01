package org.kframework.kil.loader;

import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Formatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import org.kframework.compile.utils.ConfigurationStructureMap;
import org.kframework.compile.utils.MetaK;
import org.kframework.kil.ASTNode;
import org.kframework.kil.Cell;
import org.kframework.kil.CellDataStructure;
import org.kframework.kil.DataStructureSort;
import org.kframework.kil.KApp;
import org.kframework.kil.KInjectedLabel;
import org.kframework.kil.KSorts;
import org.kframework.kil.Production;
import org.kframework.kil.Sort;
import org.kframework.kil.Term;
import org.kframework.kil.UserList;
import org.kframework.kompile.KompileOptions;
import org.kframework.main.GlobalOptions;
import org.kframework.parser.ExperimentalParserOptions;
import org.kframework.utils.Poset;
import org.kframework.utils.errorsystem.KException;
import org.kframework.utils.errorsystem.KException.ExceptionType;
import org.kframework.utils.errorsystem.KException.KExceptionGroup;
import org.kframework.utils.general.GlobalSettings;

import com.google.common.collect.ImmutableSet;


public class Context implements Serializable {

    public static final Set<String> generatedTags = ImmutableSet.of(
            "cons",
            "kgeneratedlabel",
            "prefixlabel");

    public static final Set<String> parsingTags = ImmutableSet.of(
        "left",
        "right",
        "non-assoc");

    public static final Set<String> specialTerminals = ImmutableSet.of(
        "(",
        ")",
        ",",
        "[",
        "]",
        "{",
        "}");
    
    /**
     * Represents the bijection map between conses and productions.
     */
    public transient BiMap<String, Production> conses = HashBiMap.create();
    /**
     * Represents a map from all Klabels in string representation plus two
     * strings, "cons" and "prefixlabel", to sets of corresponding productions.
     * 
     * TODO(YilongL): it doesn't contain getKLabel_ in key set?! instead the
     * production "getKLabel" K is in the values of both "cons" and "prefix".
     * why?
     */
    public transient Map<String, Set<Production>> productions = new HashMap<String, Set<Production>>();
    /**
     * Represents a map from all labels (KLabels and prefix-labels) to sets of
     * corresponding conses in string representation.
     */
    public transient Map<String, Set<String>> labels = new HashMap<String, Set<String>>();
    public transient Map<String, Cell> cells = new HashMap<String, Cell>();
    public transient Map<String, String> cellKinds = new HashMap<String, String>();
    public transient Map<String, String> cellSorts = new HashMap<String, String>();
    public transient Map<String, Production> listConses = new HashMap<String, Production>();
    public transient Map<String, Set<String>> listLabels = new HashMap<String, Set<String>>();
    public transient Map<String, ASTNode> locations = new HashMap<String, ASTNode>();
    public transient Map<String, Set<Production>> associativity = new HashMap<String, Set<Production>>();
    public Map<String, Production> canonicalBracketForSort = new HashMap<>();
    private Poset subsorts = new Poset();
    public java.util.Set<String> definedSorts = Sort.getBaseSorts();
    private Poset priorities = new Poset();
    private transient Poset modules = new Poset();
    private transient Poset fileRequirements = new Poset();
    public String startSymbolPgm = "K";
    public transient Map<String, String> configVarSorts = new HashMap<String, String>();
    public transient File dotk = null;
    public transient File kompiled = null;
    public boolean initialized = false;
    protected transient java.util.List<String> komputationCells = null;
    public Map<String, CellDataStructure> cellDataStructures = new HashMap<>();
    public Set<String> variableTokenSorts = new HashSet<>();

    public int numModules, numSentences, numProductions, numCells;

    /**
     * The two structures below are populated by the InitializeConfigurationStructure step of the compilation.
     * configurationStructureMap represents a map from cell names to structures containing cell data.
     * maxConfigurationLevel represent the maximum level of cell nesting in the configuration.
     */
    private ConfigurationStructureMap configurationStructureMap = new ConfigurationStructureMap();
    private int maxConfigurationLevel = -1;

    /**
     * {@link Map} of sort names into {@link DataStructureSort} instances.
     */
    private Map<String, DataStructureSort> dataStructureSorts;

    /**
     * {@link Set} of the names of the sorts with lexical productions.
     */
    private Set<String> tokenSorts;

    // TODO(dwightguth): remove these fields and replace with injected dependencies
    public transient GlobalOptions globalOptions;
    public KompileOptions kompileOptions;
    public transient ExperimentalParserOptions experimentalParserOptions;
    
    public Context() {
        initSubsorts();
    }
    
    public Context(GlobalOptions globalOptions) {
        this();
        this.merge(globalOptions, null);
    }
    
    public Context(GlobalOptions globalOptions, ExperimentalParserOptions experimentalParserOptions) {
        this();
        this.merge(globalOptions, experimentalParserOptions);
    }
    
    public Context(KompileOptions kompileOptions) {
        this();
        this.merge(kompileOptions.global, kompileOptions.experimental.parser);
        this.kompileOptions = kompileOptions;
    }
    
    public void merge(GlobalOptions globalOptions, ExperimentalParserOptions experimentalParserOptions) {
        this.globalOptions = globalOptions;
        this.experimentalParserOptions = experimentalParserOptions;
    }
    
    private void initSubsorts() {
        subsorts.addRelation(KSorts.KLIST, "K");
        subsorts.addRelation(KSorts.KLIST, "KResult");
        subsorts.addRelation("K", "KResult");
        subsorts.addRelation("K", KSorts.KITEM);
        subsorts.addRelation("Map", "MapItem");
        subsorts.addRelation("Set", "SetItem");
        subsorts.addRelation("List", "ListItem");
        subsorts.addRelation("Bag", "BagItem");        
    }
    
    public void printStatistics() {
        Formatter f = new Formatter(System.out);
        f.format("%n");
        f.format("%-60s = %5d%n", "Number of Modules", numModules);
        f.format("%-60s = %5d%n", "Number of Sentences", numSentences);
        f.format("%-60s = %5d%n", "Number of Productions", numProductions);
        f.format("%-60s = %5d%n", "Number of Cells", numCells);
    }
    
    public java.util.List<String> getKomputationCells() {
        return kompileOptions.experimental.kCells;
    }

    public ConfigurationStructureMap getConfigurationStructureMap() {
        return configurationStructureMap;
    }

    public int getMaxConfigurationLevel() {
        return maxConfigurationLevel;
    }

    public void setMaxConfigurationLevel(int maxConfigurationLevel) {
        this.maxConfigurationLevel = maxConfigurationLevel;
    }

    public void putLabel(Production p, String cons) {
//        String label;
//        if (!MetaK.isComputationSort(p.getSort()))
//            label = p.getLabel();
//        else
//            label = p.getKLabel();
//        Set<String> s = labels.get(label);
//        if (s == null) {
//            labels.put(label, s = new HashSet<String>());
//        }
//        s.add(cons);
        putLabel(p.getLabel(), cons);
        putLabel(p.getKLabel(), cons);
    }
    
    private void putLabel(String label, String cons) {
        Set<String> s = labels.get(label);
        if (s == null) {
            labels.put(label, s = new HashSet<String>());
        }
        s.add(cons);
    }

    public void putListLabel(Production p) {
        String separator = ((UserList) p.getItems().get(0)).getSeparator();
        String label = MetaK.getListUnitLabel(separator);
        Set<String> s = listLabels.get(label);
        if (s == null)
            listLabels.put(label, s = new HashSet<String>());
        s.add(p.getSort());
    }

    public void putAssoc(String cons, Collection<Production> prods) {
        if (associativity.get(cons) == null) {
            associativity.put(cons, new HashSet<Production>(prods));
        } else {
            associativity.get(cons).addAll(prods);
        }
    }

    public void addCellDecl(Cell c) {
        cells.put(c.getLabel(), c);

        String kind = subsorts.getMaxim(c.getContents().getSort());
        if (kind.equals(KSorts.KLIST)) {
            kind = "K";
        }
        cellKinds.put(c.getLabel(), kind);

        String sort = c.getCellAttributes().get(Cell.SORT_ATTRIBUTE);
        if (sort == null) {
            sort = c.getContents().getSort();
        }
        cellSorts.put(c.getLabel(), sort);
    }

    public boolean isListSort(String sort) {
        return listConses.containsKey(sort);
    }
    
    /**
     * Returns a unmodifiable view of all sorts.
     */
    public Set<String> getAllSorts() {
        return Collections.unmodifiableSet(subsorts.getElements());
    }

    /**
     * Takes a List sort and returns the sort of the elements of that List sort. e.g, for List{Exp, ","}, returns Exp.
     * 
     * returns null if not a List sort
     * 
     * we suppress cast warnings because we know that the sort must be UserList
     */
    @SuppressWarnings("cast")
    public String getListElementSort(String sort) {
        if (!isListSort(sort))
            return null;
        return ((UserList) listConses.get(sort).getItems().get(0)).getSort();
    }

    /**
     * find the LUB of a list of sorts
     */
    public String getLUBSort(Set<String> sorts) {
        return subsorts.getLUB(sorts);
    }

    /**
     * find the GLB of a list of sorts
     */
    public String getGLBSort(Set<String> sorts) {
        return subsorts.getGLB(sorts);
    }
    
    /**
     * Finds the most general common subsorts of a given set of sorts
     * 
     * @param sorts
     *            the given set of sorts
     * @return an immutable set of the most general common subsorts
     */
    public Set<String> getCommonSubsorts(Set<String> sorts) {
        return subsorts.getMaximalLowerBounds(sorts);
    }

    public void addPriority(String bigPriority, String smallPriority) {
        // add the new priority
        priorities.addRelation(bigPriority, smallPriority);
    }

    public void finalizePriority() {
        priorities.transitiveClosure();
    }

    /**
     * Check to see if the two klabels are in the wrong order according to the priority filter.
     * 
     * @param klabelParent
     * @param klabelChild
     * @return
     */
    public boolean isPriorityWrong(String klabelParent, String klabelChild) {
        return priorities.isInRelation(klabelParent, klabelChild);
    }

    public void addFileRequirement(String required, String local) {
        // add the new subsorting
        if (required.equals(local))
            return;

        fileRequirements.addRelation(required, local);
    }

    public void finalizeRequirements() {
        fileRequirements.transitiveClosure();
    }

    public void addModuleImport(String mainModule, String importedModule) {
        // add the new subsorting
        if (mainModule.equals(importedModule))
            return;

        modules.addRelation(mainModule, importedModule);
    }

    public void finalizeModules() {
        modules.transitiveClosure();
    }

    public boolean isModuleIncluded(String localModule, String importedModule) {
        return modules.isInRelation(localModule, importedModule);
    }

    public boolean isModuleIncludedEq(String localModule, String importedModule) {
        if (localModule.equals(importedModule))
            return true;
        return modules.isInRelation(localModule, importedModule);
    }

    public boolean isRequiredEq(String required, String local) {
        try {
            required = new File(required).getCanonicalPath();
            local = new File(local).getCanonicalPath();
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (required.equals(local))
            return true;
        return fileRequirements.isInRelation(required, local);
    }

    public void addSubsort(String bigSort, String smallSort) {
        // add the new subsorting
        subsorts.addRelation(bigSort, smallSort);
    }

    public void finalizeSubsorts() {
        List<String> circuit = subsorts.checkForCycles();
        if (circuit != null) {
            String msg = "Circularity detected in subsorts: ";
            for (String sort : circuit)
                msg += sort + " < ";
            msg += circuit.get(0);
            GlobalSettings.kem.register(new KException(ExceptionType.ERROR, KExceptionGroup.CRITICAL, msg, "Definition files", "File system."));
        }
        subsorts.transitiveClosure();
        // detect if lists are subsorted (Vals Ids < Exps)
        for (Map.Entry<String, Production> ls1 : listConses.entrySet()) {
            for (Map.Entry<String, Production> ls2 : listConses.entrySet()) {
                String sort1 = ((UserList) ls1.getValue().getItems().get(0)).getSort();
                String sort2 = ((UserList) ls2.getValue().getItems().get(0)).getSort();
                if (isSubsorted(sort1, sort2)) {
                    subsorts.addRelation(ls1.getValue().getSort(), ls2.getValue().getSort());
                }
            }
        }
        subsorts.transitiveClosure();
    }

    /**
     * Check to see if smallSort is subsorted to bigSort (strict)
     * 
     * @param bigSort
     * @param smallSort
     * @return
     */
    public boolean isSubsorted(String bigSort, String smallSort) {
        return subsorts.isInRelation(bigSort, smallSort);
    }

    /**
     * Check to see if smallSort is subsorted or equal to bigSort
     * 
     * @param bigSort
     * @param smallSort
     * @return
     */
    public boolean isSubsortedEq(String bigSort, String smallSort) {
        if (bigSort.equals(smallSort))
            return true;
        return subsorts.isInRelation(bigSort, smallSort);
    }

    public boolean isTagGenerated(String key) {
        return generatedTags.contains(key);
    }

    public boolean isSpecialTerminal(String terminal) {
        return specialTerminals.contains(terminal);
    }

    public boolean isParsingTag(String key) {
        return parsingTags.contains(key);
    }

    public static final int HASH_PRIME = 37;

    /**
     * Returns a {@link List} of productions associated with the specified KLabel
     * 
     * @param label
     *            string representation of the KLabel
     * @return list of productions associated with the label
     */
    @SuppressWarnings("unchecked")
    public List<Production> productionsOf(String label) {
        Set<String> conses = labels.get(label);
        if (conses == null) {
            return (List<Production>) Collections.EMPTY_LIST;
        }

        ArrayList<Production> productions = new ArrayList<Production>();
        for (String cons : conses) {
            assert this.conses.containsKey(cons);

            productions.add(this.conses.get(cons));
        }

        return productions;
    }

    public Term kWrapper(Term t) {
        if (isSubsortedEq("K", t.getSort()))
            return t;
        return KApp.of(new KInjectedLabel(t));
    }

    private static final String fragment = "-fragment";

    private String getCellSort2(String sort) {
        sort = sort.substring(0, 1).toLowerCase() + sort.substring(1);
        if (sort.endsWith(MetaK.cellSort)) {
            return sort.substring(0, sort.length() - MetaK.cellSort.length());
        } else {
            return sort.substring(0, sort.length() - MetaK.cellFragment.length()) + "-fragment";
        }
    }

    public String getCellSort(String sort) {
        sort = getCellSort2(sort);
        String cellName = sort;
        if (sort.endsWith(fragment)) {
            cellName = sort.substring(0, sort.length() - fragment.length());
        }
        if (cells.containsKey(cellName)) {
            return sort;
        }
        return sort.substring(0, 1).toUpperCase() + sort.substring(1);
    }

    public Map<String, DataStructureSort> getDataStructureSorts() {
        return Collections.unmodifiableMap(dataStructureSorts);
    }

    public void setDataStructureSorts(Map<String, DataStructureSort> dataStructureSorts) {
        assert !initialized;

        this.dataStructureSorts = new HashMap<String, DataStructureSort>(dataStructureSorts);
    }

    public DataStructureSort dataStructureSortOf(String sortName) {
        assert initialized : "Context is not initialized yet";

        return dataStructureSorts.get(sortName);
    }

    public DataStructureSort dataStructureListSortOf(String sortName) {
        assert initialized : "Context is not initialized yet";
        DataStructureSort sort = dataStructureSorts.get(sortName);
        if (sort == null) return null;
        if (!sort.type().equals(KSorts.LIST)) return null;
        return sort;
    }

    public Set<String> getTokenSorts() {
        return Collections.unmodifiableSet(tokenSorts);
    }

    public void setTokenSorts(Set<String> tokenSorts) {
        assert !initialized;

        this.tokenSorts = new HashSet<String>(tokenSorts);
    }

    private Production[] serializationViewOfProductions;
    private Cell[] serializationViewOfCells;

    private void writeObject(ObjectOutputStream out) throws IOException {
        serializationViewOfProductions = new Production[conses.values().size()];
        conses.values().toArray(serializationViewOfProductions);
        serializationViewOfCells = new Cell[cells.values().size()];
        cells.values().toArray(serializationViewOfCells);
        out.defaultWriteObject();
    }
     
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        conses = HashBiMap.create();
        labels = new HashMap<>();
        listConses = new HashMap<>();
        listLabels = new HashMap<>();
        cells = new HashMap<>();
        cellKinds = new HashMap<>();
        cellSorts = new HashMap<>();
        for (Production p : serializationViewOfProductions) {
            conses.put(p.getCons(), p);
            putLabel(p, p.getCons());

            if (p.isListDecl()) {
                listConses.put(p.getSort(), p);
                putListLabel(p);
            }
        }
        for (Cell c : serializationViewOfCells) {
            addCellDecl(c);
        }
    }

}
