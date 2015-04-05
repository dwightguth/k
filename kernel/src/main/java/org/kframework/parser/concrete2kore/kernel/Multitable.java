package org.kframework.parser.concrete2kore.kernel;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Created by dwightguth on 4/4/15.
 */
public class Multitable<TRow, TCol, TValue> implements Table<TRow, TCol, Set<TValue>> {
    private final Table<TRow, TCol, Set<TValue>> internal = HashBasedTable.create();

    @Override
    public boolean contains(Object rowKey, Object columnKey) {
        return internal.contains(rowKey, columnKey);
    }

    @Override
    public boolean containsRow(Object rowKey) {
        return internal.containsRow(rowKey);
    }

    @Override
    public boolean containsColumn(Object columnKey) {
        return internal.containsColumn(columnKey);
    }

    @Override
    public boolean containsValue(Object value) {
        return internal.containsValue(value);
    }

    @Override
    public Set<TValue> get(Object rowKey, Object columnKey) {
        return internal.get(rowKey, columnKey);
    }

    public Set<TValue> getOrDefault(TRow rowKey, TCol columnKey) {
        Set<TValue> res = internal.get(rowKey, columnKey);
        if (res == null) {
            res = new HashSet<>();
            put(rowKey, columnKey, res);
        }
        return res;
    }

    @Override
    public boolean isEmpty() { return internal.isEmpty(); }

    @Override
    public int size() {
        return internal.size();
    }

    @Override
    public void clear() { internal.clear(); }

    @Override
    public Set<TValue> put(TRow rowKey, TCol columnKey, Set<TValue> value) { return internal.put(rowKey, columnKey, value); }

    public Set<TValue> putElement(TRow rowKey, TCol  columnKey, TValue value) {
        Set<TValue> result = internal.get(rowKey, columnKey);
        Set<TValue> set = result;
        if (set == null) {
            set = new HashSet<>();
            internal.put(rowKey, columnKey, set);
        }
        set.add(value);
        return result;
    }

    @Override
    public void putAll(Table<? extends TRow, ? extends TCol, ? extends Set<TValue>> table) { internal.putAll(table); }

    @Override
    public Set<TValue> remove(Object rowKey, Object columnKey) { return internal.remove(rowKey, columnKey); }

    @Override
    public Map<TCol, Set<TValue>> row(TRow rowKey) { return internal.row(rowKey); }

    @Override
    public Map<TRow, Set<TValue>> column(TCol columnKey) {
        return internal.column(columnKey);
    }

    @Override
    public Set<Cell<TRow, TCol, Set<TValue>>> cellSet() {
        return internal.cellSet();
    }

    @Override
    public Set<TRow> rowKeySet() {
        return internal.rowKeySet();
    }

    @Override
    public Set<TCol> columnKeySet() {
        return internal.columnKeySet();
    }

    @Override
    public Collection<Set<TValue>> values() {
        return internal.values();
    }

    @Override
    public Map<TRow, Map<TCol, Set<TValue>>> rowMap() {
        return internal.rowMap();
    }

    @Override
    public Map<TCol, Map<TRow, Set<TValue>>> columnMap() {
        return internal.columnMap();
    }
}
