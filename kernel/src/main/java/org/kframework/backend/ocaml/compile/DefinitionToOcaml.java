package org.kframework.backend.ocaml.compile;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.SetMultimap;
import org.kframework.builtin.BooleanUtils;
import org.kframework.builtin.Sorts;
import org.kframework.definition.Module;
import org.kframework.definition.ModuleTransformer;
import org.kframework.definition.Production;
import org.kframework.definition.Rule;
import org.kframework.kil.Attribute;
import org.kframework.kompile.CompiledDefinition;
import org.kframework.kore.InjectedKLabel;
import org.kframework.kore.K;
import org.kframework.kore.KApply;
import org.kframework.kore.KLabel;
import org.kframework.kore.KRewrite;
import org.kframework.kore.KSequence;
import org.kframework.kore.KToken;
import org.kframework.kore.KVariable;
import org.kframework.kore.Sort;
import org.kframework.kore.ToKast;
import org.kframework.kore.compile.ConvertDataStructureToLookup;
import org.kframework.kore.compile.GenerateSortPredicates;
import org.kframework.kore.compile.LiftToKSequence;
import org.kframework.kore.compile.RewriteToTop;
import org.kframework.kore.compile.VisitKORE;
import org.kframework.main.GlobalOptions;
import org.kframework.utils.BinaryLoader;
import org.kframework.utils.StringUtil;
import org.kframework.utils.algorithms.SCCTarjan;
import org.kframework.utils.errorsystem.KEMException;
import org.kframework.utils.errorsystem.KExceptionManager;
import org.kframework.utils.file.FileUtil;
import scala.Function1;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.kframework.Collections.*;
import static org.kframework.kore.KORE.*;
import static scala.compat.java8.JFunction.*;

public class DefinitionToOcaml {

    public static void main(String[] args) {
        KExceptionManager kem = new KExceptionManager(new GlobalOptions());
        CompiledDefinition def = new BinaryLoader(kem).loadOrDie(CompiledDefinition.class, new File(args[0]));

        DefinitionToOcaml convert = new DefinitionToOcaml();
        String ocaml = convert.convert(def);
        FileUtil.testFileUtil().saveToWorkingDirectory("def.ml", ocaml);
    }

    public static final String kType = "t = kitem list\n" +
            " and kitem = KApply of klabel * t list\n" +
            "           | KToken of sort * string\n" +
            "           | InjectedKLabel of klabel\n" +
            "           | Map of t m\n" +
            "           | List of t list\n" +
            "           | Set of s\n" +
            "           | Int of int\n" +
            "           | String of string\n" +
            "           | Bool of bool\n" +
            "           | Bottom\n";

    public static final String prelude = "module type S =\n" +
            "sig\n" +
            "  type 'a m\n" +
            "  type s\n" +
            "  type " + kType +
            "  val compare : t -> t -> int\n" +
            "end \n" +
            "\n" +
            "\n" +
            "module rec K : (S with type 'a m = 'a Map.Make(K).t and type s = Set.Make(K).t)  = \n" +
            "struct\n" +
            "  module KMap = Map.Make(K)\n" +
            "  module KSet = Set.Make(K)\n" +
            "  type 'a m = 'a KMap.t\n" +
            "  and s = KSet.t\n" +
            "  and " + kType +
            "  let rec compare c1 c2 = match (c1, c2) with\n" +
            "    | [], [] -> 0\n" +
            "    | (hd1 :: tl1), (hd2 :: tl2) -> let v = compare_kitem hd1 hd2 in if v = 0 then compare tl1 tl2 else v\n" +
            "    | (hd1 :: tl1), _ -> -1\n" +
            "    | _ -> 1\n" +
            "  and compare_kitem c1 c2 = match (c1, c2) with\n" +
            "    | (KApply(kl1, k1)), (KApply(kl2, k2)) -> let v = compare_klabel kl1 kl2 in if v = 0 then compare_klist k1 k2 else v\n" +
            "    | (KToken(s1, st1)), (KToken(s2, st2)) -> let v = compare_sort s1 s2 in if v = 0 then Pervasives.compare st1 st2 else v\n" +
            "    | (InjectedKLabel kl1), (InjectedKLabel kl2) -> compare_klabel kl1 kl2\n" +
            "    | (Map m1), (Map m2) -> (KMap.compare) compare m1 m2\n" +
            "    | (List l1), (List l2) -> compare_klist l1 l2\n" +
            "    | (Set s1), (Set s2) -> (KSet.compare) s1 s2\n" +
            "    | (Int i1), (Int i2) -> i2 - i1\n" +
            "    | (String s1), (String s2) -> Pervasives.compare s1 s2\n" +
            "    | (Bool b1), (Bool b2) -> if b1 = b2 then 0 else if b1 then -1 else 1\n" +
            "    | Bottom, Bottom -> 0\n" +
            "    | KApply(_, _), _ -> -1\n" +
            "    | _, KApply(_, _) -> 1\n" +
            "    | KToken(_, _), _ -> -1\n" +
            "    | _, KToken(_, _) -> 1\n" +
            "    | InjectedKLabel(_), _ -> -1\n" +
            "    | _, InjectedKLabel(_) -> 1\n" +
            "    | Map(_), _ -> -1\n" +
            "    | _, Map(_) -> 1\n" +
            "    | List(_), _ -> -1\n" +
            "    | _, List(_) -> 1\n" +
            "    | Set(_), _ -> -1\n" +
            "    | _, Set(_) -> 1\n" +
            "    | Int(_), _ -> -1\n" +
            "    | _, Int(_) -> 1\n" +
            "    | String(_), _ -> -1\n" +
            "    | _, String(_) -> 1\n" +
            "    | Bool(_), _ -> -1\n" +
            "    | _, Bool(_) -> 1\n" +
            "  and compare_klist c1 c2 = match (c1, c2) with\n" +
            "    | [], [] -> 0\n" +
            "    | (hd1 :: tl1), (hd2 :: tl2) -> let v = compare hd1 hd2 in if v = 0 then compare_klist tl1 tl2 else v\n" +
            "    | (hd1 :: tl1), _ -> -1\n" +
            "    | _ -> 1\n" +
            "  and compare_klabel kl1 kl2 = (order_klabel kl2) - (order_klabel kl1)\n" +
            "  and compare_sort s1 s2 = (order_sort s2) - (order_sort s1)\n" +
            "end\n" +
            "\n" +
            "  module KMap = Map.Make(K)\n" +
            "  module KSet = Set.Make(K)\n" +
            "\n" +
            "open K\n" +
            "type k = K.t" +
            "\n" +
            "exception Stuck of k\n" +
            "module GuardElt = struct\n" +
            "  type t = Guard of int\n" +
            "  let compare c1 c2 = match c1 with Guard(i1) -> match c2 with Guard(i2) -> i2 - i1\n" +
            "end\n" +
            "module Guard = Set.Make(GuardElt)\n";

    public static final String TRUE = "(Bool true)";

    public static final String midlude = "let eq k1 k2 = k1 = k2\n" +
            "let isTrue(c: k) : bool = match c with\n" +
            "| ([" + TRUE + "]) -> true\n" +
            "| _ -> false\n" +
            "let rec print_klist(c: k list) : unit = match c with\n" +
            "| [] -> print_string(\".KList\")\n" +
            "| e::l -> print_k(e); print_string(\", \"); print_klist(l)\n" +
            "and print_k(c: k) : unit = match c with\n" +
            "| [] -> print_string(\".K\")\n" +
            "| e::l -> print_kitem(e); print_string(\" ~> \"); print_k(l)\n" +
            "and print_kitem(c: kitem) : unit = match c with\n" +
            "| KApply(klabel, klist) -> print_string(print_klabel(klabel)); print_string \"(\"; print_klist(klist); print_string \")\"\n" +
            "| KToken(sort, s) -> print_string \"#token(\"; print_string(print_sort_string(sort)); print_string (\", \\\"\" ^ s ^ \"\\\")\")\n" +
            "| InjectedKLabel(klabel) -> print_string \"#klabel(\"; print_string(print_klabel(klabel)); print_string(\")\")\n" +
            "| Bool(b) -> print_kitem(KToken(LblBool, string_of_bool(b)))\n" +
            "| Int(i) -> print_kitem(KToken(LblInt, string_of_int(i)))\n" +
            "| Map(m) -> if m = KMap.empty then print_string(\"`.Map`(.KList)\") else KMap.iter (fun k v -> print_string(\"`_|->_`(\"); print_k(k); print_string(\", \"); print_k(v); print_string(\")\")) m\n";

    public static final String postlude = "let run c =\n" +
            "  try let rec go c = go (step c)\n" +
            "      in go c\n" +
            "  with Stuck c' -> c'\n";

    public static final ImmutableMap<String, String> hooks;
    public static final ImmutableMap<String, Function<String, String>> sortHooks;
    public static final ImmutableMap<String, String> predicateRules;

    static {
        ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();
        builder.put("#INT:_%Int_", "[Int a] :: [Int b] :: [] -> [Int (a mod b)]");
        builder.put("#INT:_+Int_", "[Int a] :: [Int b] :: [] -> [Int (a + b)]");
        builder.put("#INT:_<=Int_", "[Int a] :: [Int b] :: [] -> [Bool (a <= b)]");
        builder.put("#INT:_=/=Int_", "[Int a] :: [Int b] :: [] -> [Bool (a = b)]");
        builder.put("Map:_|->_", "k1 :: k2 :: [] -> [Map (KMap.add k1 k2 KMap.empty)]");
        builder.put("Map:.Map", "[] -> [Map KMap.empty]");
        builder.put("Map:__", "([Map k1]) :: ([Map k2]) :: [] -> [Map (KMap.merge (fun k a b -> match a, b with None, None -> None | None, Some v | Some v, None -> Some v) k1 k2)]");
        builder.put("Map:lookup", "[Map k1] :: k2 :: [] -> (try KMap.find k2 k1 with Not_found -> [Bottom])");
        builder.put("Map:remove", "[Map k1] :: k2 :: [] -> [Map (KMap.remove k2 k1)]");
        builder.put("Map:keys", "[Map k1] :: [] -> [Set (KMap.fold (fun key -> KSet.add) k1 KSet.empty)]");
        builder.put("Set:in", "k1 :: [Set k2] :: [] -> [Bool (KSet.mem k1 k2)]");
        builder.put("MetaK:#sort", "[KToken (sort, s)] :: [] -> [String (print_sort(sort))] " +
                "| [Int _] :: [] -> [String \"Int\"] " +
                "| [String _] :: [] -> [String \"String\"] " +
                "| [Bool _] :: [] -> [String \"Bool\"] " +
                "| [Map _] :: [] -> [String \"Map\"] " +
                "| [List _] :: [] -> [String \"List\"] " +
                "| [Set _] :: [] -> [String \"Set\"] " +
                "| _ -> [String \"\"]");
        builder.put("#K-EQUAL:_==K_", "k1 :: k2 :: [] -> [Bool (eq k1 k2)]");
        builder.put("#BOOL:_andBool_", "[Bool b1] :: [Bool b2] :: [] -> [Bool (b1 && b2)]");
        builder.put("#BOOL:notBool_", "[Bool b1] :: [] -> [Bool (not b1)]");
        hooks = builder.build();
    }

    static {
        ImmutableMap.Builder<String, Function<String, String>> builder = ImmutableMap.builder();
        builder.put("#BOOL", s -> "(Bool " + s + ")");
        builder.put("#INT", s -> "(Int (" + s + "))");
        builder.put("#STRING", s -> "(String " + StringUtil.enquoteCString(StringUtil.unquoteKString(s)) + ")");
        sortHooks = builder.build();
    }

    static {
        ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();
        builder.put("isK", "k1 :: [] -> [Bool true]");
        builder.put("isKItem", "[k1] :: [] -> [Bool true]");
        builder.put("isInt", "[Int _] :: [] -> [Bool true]");
        builder.put("isString", "[String _] :: [] -> [Bool true]");
        builder.put("isBool", "[Bool _] :: [] -> [Bool true]");
        builder.put("isMap", "[Map _] :: [] -> [Bool true]");
        builder.put("isSet", "[Set _] :: [] -> [Bool true]");
        builder.put("isList", "[List _] :: [] -> [Bool true]");
        predicateRules = builder.build();
    }


    private Module mainModule;

    public String convert(CompiledDefinition def) {
        ModuleTransformer convertLookups = ModuleTransformer.fromSentenceTransformer(new ConvertDataStructureToLookup(def.executionModule())::convert, "convert data structures to lookups");
        Function1<Module, Module> generatePredicates = func(new GenerateSortPredicates(def.kompiledDefinition)::gen);
        ModuleTransformer liftToKSequence = ModuleTransformer.fromSentenceTransformer(new LiftToKSequence()::convert, "lift K into KSequence");
        Function1<Module, Module> pipeline = convertLookups
                .andThen(generatePredicates)
                .andThen(liftToKSequence);
        mainModule = pipeline.apply(def.executionModule());
        return convert();
    }

    Set<KLabel> functions;

    public String convert(K k) {
        StringBuilder sb = new StringBuilder();
        sb.append("open Def\n");
        sb.append("let _ = try print_k(run(");
        convert(sb, true, HashMultimap.create(), false).apply(new LiftToKSequence().convert(k));
        sb.append(")) with Stuck c' -> print_k c'");
        return sb.toString();
    }

    private String convert() {
        StringBuilder sb = new StringBuilder();
        sb.append("type sort = ");
        for (Sort s : iterable(mainModule.definedSorts())) {
            sb.append("|");
            encodeStringToIdentifier(sb, s.name());
            sb.append("\n");
        }
        sb.append("let order_sort(s: sort) = match s with \n");
        int i = 0;
        for (Sort s : iterable(mainModule.definedSorts())) {
            sb.append("|");
            encodeStringToIdentifier(sb, s.name());
            sb.append(" -> ").append(i++).append("\n");
        }
        sb.append("type klabel = ");
        for (KLabel label : iterable(mainModule.definedKLabels())) {
            sb.append("|");
            encodeStringToIdentifier(sb, label.name());
            sb.append("\n");
        }
        i = 0;
        sb.append("let order_klabel(l: klabel) = match l with \n");
        for (KLabel label : iterable(mainModule.definedKLabels())) {
            sb.append("|");
            encodeStringToIdentifier(sb, label.name());
            sb.append(" -> ").append(i++).append("\n");
        }
        sb.append(prelude);
        sb.append("let print_sort_string(c: sort) : string = match c with \n");
        for (Sort s : iterable(mainModule.definedSorts())) {
            sb.append("|");
            encodeStringToIdentifier(sb, s.name());
            sb.append(" -> ");
            sb.append(StringUtil.enquoteCString(StringUtil.enquoteKString(s.name())));
            sb.append("\n");
        }
        sb.append("let print_sort(c: sort) : string = match c with \n");
        for (Sort s : iterable(mainModule.definedSorts())) {
            sb.append("|");
            encodeStringToIdentifier(sb, s.name());
            sb.append(" -> ");
            sb.append(StringUtil.enquoteCString(s.name()));
            sb.append("\n");
        }
        sb.append("let print_klabel(c: klabel) : string = match c with \n");
        for (KLabel label : iterable(mainModule.definedKLabels())) {
            sb.append("|");
            encodeStringToIdentifier(sb, label.name());
            sb.append(" -> ");
            sb.append(StringUtil.enquoteCString(ToKast.apply(label)));
            sb.append("\n");
        }
        sb.append(midlude);
        SetMultimap<KLabel, Rule> functionRules = HashMultimap.create();
        for (Rule r : iterable(mainModule.rules())) {
            K left = RewriteToTop.toLeft(r.body());
            if (left instanceof KSequence) {
                KSequence kseq = (KSequence) left;
                if (kseq.items().size() == 1 && kseq.items().get(0) instanceof KApply) {
                    KApply kapp = (KApply) kseq.items().get(0);
                    if (mainModule.attributesFor().apply(kapp.klabel()).contains(Attribute.FUNCTION_KEY)) {
                        functionRules.put(kapp.klabel(), r);
                    }
                }
            }
        }
        functions = new HashSet<>(functionRules.keySet());
        for (Production p : iterable(mainModule.productions())) {
            if (p.att().contains(Attribute.FUNCTION_KEY)) {
                functions.add(p.klabel().get());
            }
        }

        List<List<KLabel>> functionOrder = sortFunctions(functionRules);

        for (List<KLabel> component : functionOrder) {
            String conn = "let rec ";
            for (KLabel functionLabel : component) {
                sb.append(conn);
                String functionName = encodeStringToFunction(sb, functionLabel.name());
                sb.append(" (l: k list) (guards: Guard.t) : k = match l with \n");
                String hook = mainModule.attributesFor().apply(functionLabel).<String>getOptional(Attribute.HOOK_KEY).orElse("");
                if (hooks.containsKey(hook)) {
                    sb.append("| ");
                    sb.append(hooks.get(hook));
                    sb.append("\n");
                }
                if (predicateRules.containsKey(functionLabel.name())) {
                    sb.append("| ");
                    sb.append(predicateRules.get(functionLabel.name()));
                    sb.append("\n");
                }

                i = 0;
                for (Rule r : functionRules.get(functionLabel).stream().sorted(this::sortFunctionRules).collect(Collectors.toList())) {
                    convert(r, sb, true, i++, functionName);
                }
                sb.append("| _ -> raise (Stuck [KApply (");
                encodeStringToIdentifier(sb, functionLabel.name());
                sb.append(", l)])\n");
                conn = "and ";
            }
        }

        boolean hasLookups = false;
        Map<Boolean, List<Rule>> sortedRules = stream(mainModule.rules()).collect(Collectors.groupingBy(this::hasLookups));
        sb.append("let rec lookups_step (c: k) (guards: Guard.t) : k = match c with \n");
        i = 0;
        for (Rule r : sortedRules.get(true)) {
            if (!functionRules.values().contains(r)) {
                convert(r, sb, false, i++, "lookups_step");
            }
        }
        sb.append("| _ -> raise (Stuck c)\n");
        sb.append("let step (c: k) : k = match c with \n");
        for (Rule r : sortedRules.get(false)) {
            if (!functionRules.values().contains(r)) {
                convert(r, sb, false, i++, "step");
            }
        }
        sb.append("| _ -> lookups_step c Guard.empty\n");
        sb.append(postlude);
        return sb.toString();
    }

    private boolean hasLookups(Rule r) {
        class Holder { boolean b; }
        Holder h = new Holder();
        new VisitKORE() {
            @Override
            public Void apply(KApply k) {
                h.b |= k.klabel().name().equals("#match");
                return super.apply(k);
            }
        }.apply(r.requires());
        return h.b;
    }

    private int sortRules(Rule a1, Rule a2) {
        return Boolean.compare(hasLookups(a1), hasLookups(a2));
    }

    private int sortFunctionRules(Rule a1, Rule a2) {
        return Boolean.compare(a1.att().contains("owise"), a2.att().contains("owise"));
    }

    private List<List<KLabel>> sortFunctions(SetMultimap<KLabel, Rule> functionRules) {
        BiMap<KLabel, Integer> mapping = HashBiMap.create();
        int counter = 0;
        for (KLabel lbl : functions) {
            mapping.put(lbl, counter++);
        }
        List<Integer>[] predecessors = new List[functions.size()];
        for (int i = 0; i < predecessors.length; i++) {
            predecessors[i] = new ArrayList<>();
        }

        class GetPredecessors extends VisitKORE {
            private final KLabel current;

            public GetPredecessors(KLabel current) {
                this.current = current;
            }

            @Override
            public Void apply(KApply k) {
                if (functions.contains(k.klabel())) {
                    predecessors[mapping.get(current)].add(mapping.get(k.klabel()));
                }
                return super.apply(k);
            }
        }

        for (Map.Entry<KLabel, Rule> entry : functionRules.entries()) {
            GetPredecessors visitor = new GetPredecessors(entry.getKey());
            visitor.apply(entry.getValue().body());
            visitor.apply(entry.getValue().requires());
        }

        List<List<Integer>> components = new SCCTarjan().scc(predecessors);

        return components.stream().map(l -> l.stream()
                .map(i -> mapping.inverse().get(i)).collect(Collectors.toList()))
                .collect(Collectors.toList());
    }

    private static void encodeStringToIdentifier(StringBuilder sb, String name) {
        sb.append("Lbl");
        encodeStringToAlphanumeric(sb, name);
    }

    private static String encodeStringToFunction(StringBuilder sb, String name) {
        StringBuilder sb2 = new StringBuilder();
        sb2.append("eval");
        encodeStringToAlphanumeric(sb2, name);
        sb.append(sb2);
        return sb2.toString();
    }

    private static long counter = 0;

    private static String encodeStringToVariable(String name) {
        StringBuilder sb2 = new StringBuilder();
        sb2.append("var");
        encodeStringToAlphanumeric(sb2, name);
        sb2.append("_");
        sb2.append(counter++);
        return sb2.toString();
    }
    public static final Pattern identChar = Pattern.compile("[A-Za-z0-9_]");

    private static void encodeStringToAlphanumeric(StringBuilder sb, String name) {
        boolean inIdent = true;
        for (int i = 0; i < name.length(); i++) {
            if (identChar.matcher(name).region(i, name.length()).lookingAt()) {
                if (!inIdent) {
                    inIdent = true;
                    sb.append("'");
                }
                sb.append(name.charAt(i));
            } else {
                if (inIdent) {
                    inIdent = false;
                    sb.append("'");
                }
                sb.append(String.format("%04x", (int) name.charAt(i)));
            }
        }
    }



    private void convert(Rule r, StringBuilder sb, boolean function, int ruleNum, String functionName) {
        sb.append("(* rule ");
        sb.append(ToKast.apply(r.body()));
        sb.append(" requires ");
        sb.append(ToKast.apply(r.requires()));
        sb.append(" ensures ");
        sb.append(ToKast.apply(r.ensures()));
        sb.append(" ");
        sb.append(r.att().toString());
        sb.append("*)\n");
        sb.append("| ");
        K left = RewriteToTop.toLeft(r.body());
        K right = RewriteToTop.toRight(r.body());
        K requires = r.requires();
        SetMultimap<KVariable, String> vars = HashMultimap.create();
        Visitor visitor = convert(sb, false, vars, false);
        if (function) {
            KApply kapp = (KApply)((KSequence)left).items().get(0);
            visitor.apply(kapp.klist().items(),true);
        } else {
            visitor.apply(left);
        }
        String result = convert(vars);
        Holder numLookups = new Holder();
        if (!requires.equals(KSequence(BooleanUtils.TRUE)) || !result.equals("true")) {
            sb.append(convertLookups(requires, vars, numLookups, ruleNum));
            sb.append(" when ");
            convert(sb, true, vars, true).apply(requires);
            sb.append(" && (");
            sb.append(result);
            sb.append(")");
        }
        sb.append(" -> ");
        convert(sb, true, vars, false).apply(right);
        for (int i = 0; i < numLookups.i; i++) {
            sb.append("| _ -> (").append(functionName).append(" c (Guard.add (GuardElt.Guard ").append(ruleNum).append(") guards)))");
        }
        sb.append("\n");
    }

    private static class Holder { int i; }

    private String convertLookups(K requires, SetMultimap<KVariable, String> vars, Holder h, int ruleNum) {
        StringBuilder sb = new StringBuilder();
        h.i = 0;
        new VisitKORE() {
            @Override
            public Void apply(KApply k) {
                if (k.klabel().name().equals("#match")) {
                    if (k.klist().items().size() != 2) {
                        throw KEMException.internalError("Unexpected arity of lookup: " + k.klist().size(), k);
                    }
                    convertLookup(sb, k.klist().items().get(0), k.klist().items().get(1), vars, ruleNum);
                    h.i++;
                }
                return super.apply(k);
            }
        }.apply(requires);
        return sb.toString();
    }

    private void convertLookup(StringBuilder sb, K lhs, K rhs, SetMultimap<KVariable, String> vars, int ruleNum) {
        sb.append(" when not (Guard.mem (GuardElt.Guard ").append(ruleNum).append(") guards)");
        sb.append(" -> (match ");
        convert(sb, true, vars, false).apply(rhs);
        sb.append(" with \n");
        convert(sb, false, vars, false).apply(lhs);
    }

    private static String convert(SetMultimap<KVariable, String> vars) {
        StringBuilder sb = new StringBuilder();
        for (Collection<String> nonLinearVars : vars.asMap().values()) {
            if (nonLinearVars.size() < 2) {
                continue;
            }
            Iterator<String> iter = nonLinearVars.iterator();
            String last = iter.next();
            while (iter.hasNext()) {
                //handle nonlinear variables in pattern
                String next = iter.next();
                sb.append("(eq ");
                sb.append(last);
                sb.append(" ");
                sb.append(next);
                sb.append(")");
                last = next;
                sb.append(" && ");
            }
        }
        sb.append("true");
        return sb.toString();
    }

    private static void applyVarRhs(KVariable v, StringBuilder sb, SetMultimap<KVariable, String> vars) {
        sb.append(vars.get(v).iterator().next());
    }

    private void applyVarLhs(KVariable k, StringBuilder sb, SetMultimap<KVariable, String> vars) {
        String varName = encodeStringToVariable(k.name());
        vars.put(k, varName);
        Sort s = Sort(k.att().<String>getOptional(Attribute.SORT_KEY).orElse(""));
        if (mainModule.sortAttributesFor().contains(s)) {
            String hook = mainModule.sortAttributesFor().apply(s).<String>getOptional("hook").orElse("");
            if (sortHooks.containsKey(hook)) {
                sb.append("(");
                sb.append(s.name()).append(" _");
                sb.append(" as ").append(varName).append(")");
                return;
            }
        }
        sb.append(varName);
    }

    private Visitor convert(StringBuilder sb, boolean rhs, SetMultimap<KVariable, String> vars, boolean useNativeBooleanExp) {
        return new Visitor(sb, rhs, vars, useNativeBooleanExp);
    }

    private class Visitor extends VisitKORE {
        private final StringBuilder sb;
        private final boolean rhs;
        private final SetMultimap<KVariable, String> vars;
        private final boolean useNativeBooleanExp;

        public Visitor(StringBuilder sb, boolean rhs, SetMultimap<KVariable, String> vars, boolean useNativeBooleanExp) {
            this.sb = sb;
            this.rhs = rhs;
            this.vars = vars;
            this.useNativeBooleanExp = useNativeBooleanExp;
            this.inBooleanExp = useNativeBooleanExp;
        }

        private boolean inBooleanExp;

        @Override
        public Void apply(KApply k) {
            if (k.klabel().name().equals("#match")) {
                apply(BooleanUtils.TRUE);
            } else if (k.klabel().name().equals("#KToken")) {
                //magic down-ness
                sb.append("KToken (");
                Sort sort = Sort(((KToken) ((KSequence) k.klist().items().get(0)).items().get(0)).s());
                apply(sort);
                sb.append(", ");
                apply(((KSequence) k.klist().items().get(1)).items().get(0));
                sb.append(")");
            } else if (functions.contains(k.klabel())) {
                applyFunction(k);
            } else {
                sb.append("KApply (");
                apply(k.klabel());
                sb.append(", ");
                apply(k.klist().items(), true);
                sb.append(")");
            }
            return null;
        }

        public void applyFunction(KApply k) {
            boolean stack = inBooleanExp;
            String hook = mainModule.attributesFor().apply(k.klabel()).<String>getOptional(Attribute.HOOK_KEY).orElse("");
            // use native &&, ||, not where possible
            if (useNativeBooleanExp && hook.equals("#BOOL:_andBool_") || hook.equals("#BOOL:_andThenBool_")) {
                assert k.klist().items().size() == 2;
                if (!stack) {
                    sb.append("[Bool ");
                }
                inBooleanExp = true;
                sb.append("(");
                apply(k.klist().items().get(0));
                sb.append(") && (");
                apply(k.klist().items().get(1));
                sb.append(")");
                if (!stack) {
                    sb.append("]");
                }
            } else if (useNativeBooleanExp && hook.equals("#BOOL:_orBool_") || hook.equals("#BOOL:_orElseBool_")) {
                assert k.klist().items().size() == 2;
                if (!stack) {
                    sb.append("[Bool ");
                }
                inBooleanExp = true;
                sb.append("(");
                apply(k.klist().items().get(0));
                sb.append(") || (");
                apply(k.klist().items().get(1));
                sb.append(")");
                if (!stack) {
                    sb.append("]");
                }
            } else if (useNativeBooleanExp && hook.equals("#BOOL:notBool_")) {
                assert k.klist().items().size() == 1;
                if (!stack) {
                    sb.append("[Bool ");
                }
                inBooleanExp = true;
                sb.append("(not ");
                apply(k.klist().items().get(0));
                sb.append(")");
                if (!stack) {
                    sb.append("]");
                }
            } else {
                if (mainModule.attributesFor().apply(k.klabel()).contains(Attribute.PREDICATE_KEY)) {
                    Sort s = Sort(mainModule.attributesFor().apply(k.klabel()).<String>get(Attribute.PREDICATE_KEY).get());
                    if (mainModule.sortAttributesFor().contains(s)) {
                        String hook2 = mainModule.sortAttributesFor().apply(s).<String>getOptional("hook").orElse("");
                        if (sortHooks.containsKey(hook2)) {
                            if (k.klist().items().size() == 1) {
                                KSequence item = (KSequence) k.klist().items().get(0);
                                if (item.items().size() == 1 &&
                                        vars.containsKey(item.items().get(0))) {
                                    Optional<String> varSort = item.items().get(0).att().<String>getOptional(Attribute.SORT_KEY);
                                    if (varSort.isPresent() && varSort.get().equals(s.name())) {
                                        // this has been subsumed by a structural check on the builtin data type
                                        apply(BooleanUtils.TRUE);
                                        return;
                                    }
                                }
                            }
                        }
                    }
                    if (s.equals(Sorts.KItem()) && k.klist().items().size() == 1) {
                        if (k.klist().items().get(0) instanceof KSequence) {
                            KSequence item = (KSequence) k.klist().items().get(0);
                            if (item.items().size() == 1) {
                                apply(BooleanUtils.TRUE);
                                return;
                            }
                        }
                    }
                }
                if (stack) {
                    sb.append("(isTrue ");
                }
                inBooleanExp = false;
                sb.append("(");
                encodeStringToFunction(sb, k.klabel().name());
                sb.append("(");
                apply(k.klist().items(), true);
                sb.append(") Guard.empty)");
                if (stack) {
                    sb.append(")");
                }
            }
            inBooleanExp = stack;
        }

        @Override
        public Void apply(KRewrite k) {
            throw new AssertionError("unexpected rewrite");
        }

        @Override
        public Void apply(KToken k) {
            if (useNativeBooleanExp && inBooleanExp && k.sort().equals(Sorts.Bool())) {
                sb.append(k.s());
                return null;
            }
            if (mainModule.sortAttributesFor().contains(k.sort())) {
                String hook = mainModule.sortAttributesFor().apply(k.sort()).<String>getOptional("hook").orElse("");
                if (sortHooks.containsKey(hook)) {
                    sb.append(sortHooks.get(hook).apply(k.s()));
                    return null;
                }
            }
            sb.append("KToken (");
            apply(k.sort());
            sb.append(", ");
            sb.append(StringUtil.enquoteCString(k.s()));
            sb.append(")");
            return null;
        }

        @Override
        public Void apply(KVariable k) {
            if (rhs) {
                applyVarRhs(k, sb, vars);
            } else {
                applyVarLhs(k, sb, vars);
            }
            return null;
        }

        @Override
        public Void apply(KSequence k) {
            if (useNativeBooleanExp && k.items().size() == 1 && inBooleanExp) {
                apply(k.items().get(0));
                return null;
            }
            sb.append("(");
            if (!rhs) {
                for (int i = 0; i < k.items().size() - 1; i++) {
                    if (isList(k.items().get(i), false)) {
                        throw KEMException.criticalError("Cannot compile KSequence with K variable not at tail.", k.items().get(i));
                    }
                }
            }
            apply(k.items(), false);
            sb.append(")");
            return null;
        }

        public String getSortOfVar(K k) {
            return k.att().<String>getOptional(Attribute.SORT_KEY).orElse("K");
        }

        @Override
        public Void apply(InjectedKLabel k) {
            sb.append("InjectedKLabel (");
            apply(k.klabel());
            sb.append(")");
            return null;
        }

        private void apply(List<K> items, boolean klist) {
            for (int i = 0; i < items.size(); i++) {
                K item = items.get(i);
                apply(item);
                if (i != items.size() - 1) {
                    if (isList(item, klist)) {
                        sb.append(" @ ");
                    } else {
                        sb.append(" :: ");
                    }
                } else {
                    if (!isList(item, klist)) {
                        sb.append(" :: []");
                    }
                }
            }
            if (items.size() == 0)
                sb.append("[]");
        }

        private boolean isList(K item, boolean klist) {
            return !klist && ((item instanceof KVariable && getSortOfVar(item).equals("K")) || item instanceof KSequence
                    || (item instanceof KApply && functions.contains(((KApply) item).klabel())));
        }

        private void apply(Sort sort) {
            encodeStringToIdentifier(sb, sort.name());
        }

        public void apply(KLabel klabel) {
            if (klabel instanceof KVariable) {
                apply((KVariable) klabel);
            } else {
                encodeStringToIdentifier(sb, klabel.name());
            }
        }
    }
}
