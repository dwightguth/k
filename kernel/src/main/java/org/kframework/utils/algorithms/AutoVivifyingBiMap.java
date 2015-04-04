// Copyright (c) 2014-2015 K Team. All Rights Reserved.
package org.kframework.utils.algorithms;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

import java.util.Set;
import java.util.function.Function;

/** A BiMap in which values are automatically added to the map
 * if one doesn't already exist. In other words, it is a BiMap
 * that autovivifies.
 * @param <K>    The type of the key
 * @param <V>    The type of the value
 */
public class AutoVivifyingBiMap<K, V> {

    private  Function<K, V> create;

    public AutoVivifyingBiMap(Function<K, V> create) {
        this.create = create;
    }

    private BiMap<K, V> map = HashBiMap.create();

    /** Retrieves the V associated with a given K.
     * Creates a new one if one doesn't exist yet.
     *
     * NOTE: We would prefer for this to conform to the java.util.Map interface
     * but that interface requires get() to take an Object as instance and
     * then check that the object is an instance of the right class.
     * Unfortunately we can't do that because K, the class to check,
     * is generic and Java doesn't allow 'instanceof' on generic parameters.
     * Sigh ... Java fails us yet another time.
     *
     * @param key    The key to lookup
     * @return The value that key maps to
     */
    public V get(K key) {
        V value = map.get(key);
        if (value == null) {
            value = create.apply(key);
            map.put(key, value);
        }
        return value;
    }

    /**
     * Returns the set of keys that have already been autovivified.
     * @return The set of keys that have already been autovivified
     */
    public Set<K> keySet() { return map.keySet(); }

    public Set<V> values() { return map.values(); }
}
