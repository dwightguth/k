// Copyright (c) 2014 K Team. All Rights Reserved.
package org.kframework.backend.java.kil;

import java.io.ObjectStreamException;
import java.io.Serializable;
import java.util.Collection;
import java.util.Set;

import org.apache.commons.collections4.trie.PatriciaTrie;
import org.kframework.kil.Sort.SortId;
import org.kframework.kil.loader.Context;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

/**
 * Sort of a {@link Term}.
 *
 * @author YilongL
 *
 */
public final class Sort implements MaximalSharing, Serializable {

    private static final PatriciaTrie<Sort> cache = new PatriciaTrie<>();

    /*
     * The following sorts will always have fixed ordinals as they are
     * created and cached during class initialization.
     */
    public static final Sort KITEM          =   Sort.of("KItem");
    public static final Sort KSEQUENCE      =   Sort.of("K");
    public static final Sort KLIST          =   Sort.of("KList");
    public static final Sort KLABEL         =   Sort.of("KLabel");
    public static final Sort KRESULT        =   Sort.of("KResult");

    public static final Sort BAG            =   Sort.of("Bag");
    public static final Sort BAG_ITEM       =   Sort.of("BagItem");
    public static final Sort LIST           =   Sort.of("List");
    public static final Sort MAP            =   Sort.of("Map");
    public static final Sort SET            =   Sort.of("Set");

    public static final Sort INT            =   Sort.of("Int");
    public static final Sort BOOL           =   Sort.of("Bool");
    public static final Sort FLOAT          =   Sort.of("Float");
    public static final Sort CHAR           =   Sort.of("Char");
    public static final Sort STRING         =   Sort.of("String");
    public static final Sort BIT_VECTOR     =   Sort.of("MInt");
    public static final Sort META_VARIABLE  =   Sort.of("MetaVariable");

    public static final Sort BOTTOM         =   Sort.of("Bottom");
    public static final Sort SHARP_BOT      =   Sort.of("#Bot");
    public static final Sort MGU            =   Sort.of("Mgu");

    /**
     * {@code String} representation of this {@code Sort}.
     */
    private final SortId id;

    /**
     * Each sort is tagged with an unique ordinal, which is determined by the
     * order in which the sort is cached.
     * <p>
     * Once the ordinal of a sort is determined, later serialization and
     * de-serialization should have no effect on it.
     */
    private final int ordinal;

    private final ImmutableList<Sort> parameters;

    /**
     * Gets the most unconstrained instance of this SortId. For sorts with no
     * dependencies, this is the single sort with this id. For sorts with dependencies,
     * an unconstrained dependency is created for each type parameter.
     * @param id
     * @return
     */
    public static Sort ofs(SortDecl id) {
        Sort[] params = new Sort[id.getArity()];
        for (int i = 0; i < params.length; i++) {
            params[i] = Sort.SHARP_BOT;
        }
        return Sort.of(id.getName(), params);
    }

    /**
     * Gets the corresponding {@code Sort} from its {@code String}
     * representation.
     * <p>
     * This method shall <b>NOT</b> be used to initialize static {@code Sort}
     * data outside of this class because it will assign a wrong ordinal to that
     * {@code Sort}.
     *
     * @param name
     *            the name of the sort
     * @return the sort
     */
    public static Sort of(String name, Sort... parameters) {
        Sort sort = cache.get(name);
        if (sort == null) {
            sort = new Sort(name, cache.size(), parameters);
            cache.put(name, sort);
        }
        return sort;
    }

    public static Set<Sort> of(Collection<org.kframework.kil.Sort> sorts, Context context) {
        ImmutableSet.Builder<Sort> builder = ImmutableSet.builder();
        for (org.kframework.kil.Sort name : sorts) {
            builder.add(name.toBackendJava(context));
        }
        return builder.build();
    }

    private Sort(String name, int ordinal, Sort... parameters) {
        this.id = new SortId(name, parameters.length);
        this.ordinal = ordinal;
        this.parameters = ImmutableList.copyOf(parameters);
    }

    public String name() {
        return id.getName();
    }

    public int ordinal() {
        return ordinal;
    }

    public Sort getUserListSort(String separator) {
        return Sort.of(org.kframework.kil.Sort.LIST_OF_BOTTOM_PREFIX + id.getName()
                + "{\"" + separator + "\"}");
    }

    public org.kframework.kil.Sort toFrontEnd(Context context) {
        org.kframework.kil.Sort[] parameters =
                new org.kframework.kil.Sort[this.parameters.size()];
        for (int i = 0; i < parameters.length; i++) {
            parameters[i] = this.parameters.get(i).toFrontEnd(context);
        }
        return org.kframework.kil.Sort.of(id.getName(), parameters);
    }

    @Override
    public int hashCode() {
        return ordinal;
    }

    @Override
    public boolean equals(Object object) {
        return this == object;
    }

    @Override
    public String toString() {
        return id.getName();
    }

    /**
     * Returns the cached instance rather than the de-serialized instance if
     * there is a cached instance.
     */
    Object readResolve() throws ObjectStreamException {
        Sort sort = cache.get(id.getName());
        if (sort == null) {
            /* do not use Sort#of to cache this sort; we need to
             * preserve the original ordinal */
            sort = this;
            cache.put(id.getName(), sort);
        } else {
            assert this.ordinal == sort.ordinal : "ordinal of sort " + id.getName()
                    + " changes after deserialization.";
        }
        return sort;
    }

}
