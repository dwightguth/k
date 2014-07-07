// Copyright (c) 2012-2014 K Team. All Rights Reserved.
package org.kframework.compile.utils;

import org.kframework.kil.Cell;

import java.io.Serializable;

/**
 * For each Cell, this class contains a reference to the cell itself, its id, a
 * link to its parent (if there exists), a {@line ConfigurationStructureMap}
 * mapping the name of its direct children to ConfigurationStructures, the
 * multiplicity of the cell, and the nesting level it can be found at.
 * 
 * @author Traian
 */
public class ConfigurationStructure implements Serializable {
    public Cell cell;
    public String id;
    public ConfigurationStructure parent = null;
    public ConfigurationStructureMap sons = new ConfigurationStructureMap();
    public Cell.Multiplicity multiplicity;
    public int level = 0;

    /**
     * Returns {@code true} if this cell has multiplicity='*' or multiplicity='+'
     */
    public boolean isStarOrPlus() {
        return multiplicity == Cell.Multiplicity.ANY || multiplicity == Cell.Multiplicity.SOME;
    }
}
