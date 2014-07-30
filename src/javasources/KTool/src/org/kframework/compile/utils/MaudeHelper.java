// Copyright (c) 2012-2014 K Team. All Rights Reserved.
package org.kframework.compile.utils;

import org.kframework.kil.NonTerminal;
import org.kframework.kil.Sort;
import org.kframework.kil.Sort.SortId;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MaudeHelper {

    public static List<String> separators = new ArrayList<String>();
    public static Set<NonTerminal> declaredSorts = new HashSet<>();
    public static Set<String> kLabels = new HashSet<String>();

    public static Set<SortId> basicSorts = new HashSet<>();
    static {
        basicSorts.add(Sort.K.getId());
        basicSorts.add(Sort.KITEM.getId());
        basicSorts.add(Sort.KLABEL.getId());
        basicSorts.add(Sort.KLIST.getId());
        basicSorts.add(Sort.KRESULT.getId());

        basicSorts.add(Sort.CELL_LABEL.getId());

        basicSorts.add(Sort.BUILTIN_BOOL.getId());
        basicSorts.add(Sort.BUILTIN_INT.getId());
        basicSorts.add(Sort.BUILTIN_STRING.getId());
        basicSorts.add(Sort.BUILTIN_FLOAT.getId());

        basicSorts.add(Sort.BAG.getId());
        basicSorts.add(Sort.BAG_ITEM.getId());
        basicSorts.add(Sort.LIST.getId());
        basicSorts.add(Sort.LIST_ITEM.getId());
        basicSorts.add(Sort.MAP.getId());
        basicSorts.add(Sort.MAP_ITEM.getId());
        basicSorts.add(Sort.SET.getId());
        basicSorts.add(Sort.SET_ITEM.getId());

        basicSorts.add(Sort.BUILTIN_ID.getId());
        basicSorts.add(Sort.BUILTIN_RAT.getId());
        basicSorts.add(Sort.BUILTIN_MODEL_CHECKER_STATE.getId());
        basicSorts.add(Sort.BUILTIN_MODEL_CHECK_RESULT.getId());
        basicSorts.add(Sort.BUILTIN_LTL_FORMULA.getId());
        basicSorts.add(Sort.BUILTIN_PROP.getId());
    }

    public static Set<Sort> constantSorts = new HashSet<>();
    static {
        constantSorts.add(Sort.BUILTIN_BOOL);
        constantSorts.add(Sort.BUILTIN_INT);
        constantSorts.add(Sort.BUILTIN_STRING);
        constantSorts.add(Sort.BUILTIN_FLOAT);

        constantSorts.add(Sort.KLABEL);

        constantSorts.add(Sort.CELL_LABEL);

        /* andreis: not sure if this two are needed */
        constantSorts.add(Sort.BUILTIN_ID);
        constantSorts.add(Sort.BUILTIN_RAT);
    }
}
