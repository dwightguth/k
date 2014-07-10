// Copyright (c) 2013-2014 K Team. All Rights Reserved.
package org.kframework.backend.symbolic;

import org.kframework.kil.ASTNode;
import org.kframework.kil.Attribute;
import org.kframework.kil.Attributes;
import org.kframework.kil.Rule;
import org.kframework.kil.loader.Constants;
import org.kframework.kil.loader.Context;
import org.kframework.kil.visitors.CopyOnWriteTransformer;
import org.kframework.utils.file.KPaths;

import java.io.File;
import java.util.List;
import java.util.Set;

import com.google.common.collect.ImmutableSet;


/**
 * Tag all the rules which are not part of K "dist/include" files with
 * 'symbolic' attribute. All the rules tagged with symbolic will suffer the
 * symbolic execution transformation steps.
 * 
 * @author andreiarusoaie
 */
public class TagUserRules extends CopyOnWriteTransformer {

    public final Set<String> notSymbolicTags;

    public TagUserRules(Context context) {
        super("Tag rules which are not builtin with 'symbolic' tag", context);
        notSymbolicTags = ImmutableSet.of(
                Constants.MACRO,
                Constants.FUNCTION,
                Constants.STRUCTURAL,
                Constants.ANYWHERE,
                SymbolicBackend.NOTSYMBOLIC);
        if (!kompileOptions.experimental.nonSymbolicRules.isEmpty()) {
            notSymbolicTags.addAll(kompileOptions.experimental.nonSymbolicRules);
        }
    }

    @Override
    public ASTNode visit(Rule node, Void _)  {

        for (String nst : notSymbolicTags)
            if (node.containsAttribute(nst)) {
                return super.visit(node, _);
            }

        if ((!node.getFilename().startsWith(
                KPaths.getKBase(false) + File.separator + "include")
                && !node.getFilename().startsWith(
                        org.kframework.kil.loader.Constants.GENERATED_FILENAME))
                ) {

            // this handles the case when the user wants to
            // specify exactly what rules should be transformed
            // symAllowed is true when the rule is tagged in this purpose
            boolean symAllowed = false;
            for (String st : kompileOptions.experimental.symbolicRules) {
                if (node.containsAttribute(st)) {
                    symAllowed = true;
                }
            }
            // the first condition might not be needed, but we keep it
            // to ensure that, by default, if no rules (identified by tags)
            // are specified, then all rules are transformed by symbolic steps.
            if (!kompileOptions.experimental.symbolicRules.isEmpty() && !symAllowed) {
                return super.visit(node, _);
            }

            List<Attribute> attrs = node.getAttributes().getContents();
            attrs.add(new Attribute(SymbolicBackend.SYMBOLIC, ""));

            Attributes atts = node.getAttributes().shallowCopy();
            atts.setContents(attrs);

            node = node.shallowCopy();
            node.setAttributes(atts);
            return node;
        }

        return super.visit(node, _);
    }
}
