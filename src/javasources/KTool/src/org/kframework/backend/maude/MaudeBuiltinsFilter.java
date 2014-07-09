// Copyright (c) 2012-2014 K Team. All Rights Reserved.
package org.kframework.backend.maude;

import org.kframework.backend.BackendFilter;
import org.kframework.compile.utils.MetaK;
import org.kframework.kil.Attribute;
import org.kframework.kil.Configuration;
import org.kframework.kil.Definition;
import org.kframework.kil.Production;
import org.kframework.kil.Rule;
import org.kframework.kil.Sort;
import org.kframework.kil.Variable;
import org.kframework.kil.loader.Context;
import org.kframework.utils.StringUtil;

import java.util.HashSet;
import java.util.Properties;
import java.util.Set;


/**
 * Visitor generating the maude equations hooking the builtins from the hooked productions.
 */
public class MaudeBuiltinsFilter extends BackendFilter {
    private String left, right;
    private boolean first;
    private final Properties maudeHooksMap;
    private final Properties specialMaudeHooks;
    
    private final Set<String> modules = new HashSet<>();
    private final Set<String> specialHooksUsed = new HashSet<>();

    public MaudeBuiltinsFilter(Properties maudeHooksMap, Properties specialMaudeHooks, Context context) {
        super(context);
        this.maudeHooksMap = maudeHooksMap;
        this.specialMaudeHooks = specialMaudeHooks;
    }
    
    @Override
    public Void visit(Definition node, Void _) {
        super.visit(node, _);
        for (Object o : specialMaudeHooks.keySet()) {
            String prop = (String) o;
            if (!specialHooksUsed.contains(prop)) {
                assert false : "detected an unused maude hook. Typo?";
            }
        }
        return null;
    }

    @Override
    public Void visit(Configuration node, Void _) {
        return null;
    }

    @Override
    public Void visit(org.kframework.kil.Context node, Void _) {
        return null;
    }

    @Override
    public Void visit(Rule node, Void _) {
        return null;
    }

    @Override
    public Void visit(Production node, Void _) {
        if (!node.containsAttribute(Attribute.HOOK_KEY)) {
            return null;
        }
        final String hook = node.getAttribute(Attribute.HOOK_KEY);
        if (!maudeHooksMap.containsKey(hook)) {
            return null;
        }
        
        String module = getHookModule(maudeHooksMap.getProperty(hook));
        if (!modules.contains(module)) {
            result.append("including ");
            result.append(module);
            result.append(" .\n");
            modules.add(module);
        }
        
        if (specialMaudeHooks.containsKey(hook)) {
            result.append(specialMaudeHooks.getProperty(hook));
            result.append("\n");
            specialHooksUsed.add(hook);
            return null;
        }

        result.append(" eq ");
        left = StringUtil.escapeMaude(node.getKLabel());
        left += "(";
        right = getHookLabel((String)maudeHooksMap.get(hook));
        if (node.getArity() > 0) {
            right += "(";
            first = true;
            super.visit(node, _);
            right += ")";
        } else {
            left += ".KList";
        }
        left += ")";
        result.append(left);
        result.append(" = _`(_`)(");
        if (context.getDataStructureSorts().containsKey(node.getSort())) {
            result.append(context.dataStructureSortOf(node.getSort()).type() + "2KLabel_(");
        } else {
            result.append("#_(");
        }
        result.append(right);
        result.append("), .KList)");
        result.append(" .\n");
        return null;
    }


    @Override
    public Void visit(Sort node, Void _) {
        if (!first) {
            left += ",, ";
            right += ", ";
        } else {
            first = false;
        }

        Variable var;
        if (MetaK.isBuiltinSort("#" + node.getName())) {
            var = Variable.getFreshVar("#" + node.getName());
        } else {
            var = Variable.getFreshVar(node.getName());
        }

        MaudeFilter filter = new MaudeFilter(context);
        filter.visit(var, null);
        left += filter.getResult();

        if (context.getDataStructureSorts().containsKey(node.getName())) {
            var.setSort(context.dataStructureSortOf(node.getName()).type());
        } else if (MetaK.isBuiltinSort(var.getSort())) {
            var.setSort(MetaK.getMaudeBackendSortName(var.getSort()));
        }
        right += var.toString();
        return null;
    }

    private String getHookLabel(String hook) {
        return hook.split(":")[1];
    }
    
    private String getHookModule(String hook) {
        return hook.split(":")[0];
    }

}
