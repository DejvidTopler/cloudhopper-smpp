package com.cloudhopper.smpp.async;

import com.google.common.collect.Iterables;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * User: ksimatov
 * Date: 18.7.2014
 */
public class ConcurrentTable<R,C,V> {

	private static final int PROC_NUM = Runtime.getRuntime().availableProcessors();
    private final ConcurrentMap<R,ConcurrentMap<C,V>> table;
    private final int expectedColumns;
    private final float loadFactor;

    public ConcurrentTable(int expectedRows, int expectedColumns) {
        this(expectedRows, expectedColumns, 0.75f);
    }

    public ConcurrentTable(int expectedRows, int expectedColumns, float loadFactor) {
        table = new ConcurrentHashMap<>(expectedRows, loadFactor, PROC_NUM);
        this.expectedColumns = expectedColumns;
        this.loadFactor = loadFactor;
    }

    public ConcurrentMap<C, V> row(R rowVal) {
        return table.computeIfAbsent(rowVal, rw -> new ConcurrentHashMap<>(expectedColumns, loadFactor, PROC_NUM));
    }

    /**
     * Puts value for given row and column value.
     *
     * @param rowVal
     * @param colVal
     * @param value
     * @return previous value or null if not found.
     */
    public V putIfAbsent(R rowVal, C colVal, V value) {
        return row(rowVal).putIfAbsent(colVal, value);
    }

    /**
     * Gets value for given row and column value.
     *
     * @param rowVal
     * @param colVal
     * @return null if no value was found
     */
    public V get(R rowVal, C colVal) {
        ConcurrentMap<C,V> res = table.get(rowVal);
        if( res == null ) {
            return null;
        }
        return res.get(colVal);
    }
    
    public V get(R rowVal, C colVal, Function<? super C, ? extends V> func){
        checkNotNull(func);
        return row(rowVal).computeIfAbsent(colVal, func);
    }

    /**
     * Removes entry for given row and column.
     *
     * @param rowVal
     * @param colVal
     * @return null if there was no entry
     */
    public V remove(R rowVal, C colVal) {
        ConcurrentMap<C,V> row = table.get(rowVal);
        if( row == null ) {
            return null;
        }
        return row.remove(colVal);
    }

    public ConcurrentMap<C, V> removeRow(R rowKey){
        return table.remove(rowKey);
    }

    public ConcurrentMap<R,ConcurrentMap<C,V>> rowMap() {
        return table;
    }

    public int size() {
        int res = 0;
        for (ConcurrentMap<C, V> rVal : table.values()) {
            res += rVal.size();
        }
        return res;
    }

    public boolean isEmpty() {
        for (ConcurrentMap<C, V> rVal : table.values()) {
            if( !rVal.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    public class Triplet<R, C, V> {
        private final R row;
        private final C column;
        private final V value;

        public Triplet(R row, C column, V value) {
            this.row = row;
            this.column = column;
            this.value = value;
        }

        public R getRow() {
            return row;
        }

        public C getColumn() {
            return column;
        }

        public V getValue() {
            return value;
        }
    }

    public interface TripletConsumer<R, C, V> {
        void accept(R rowKey, C columnKey, V value);
    }

    public interface TripletSelectiveConsumer<R, C, V> {
        boolean accept(R rowKey, C columnKey, V value);
    }


    public void forEach(TripletConsumer<R, C, V> consumer) {
        Objects.requireNonNull(consumer);
        for (Map.Entry<R, ConcurrentMap<C, V>> rowEntry : table.entrySet()) {
            R rowKey = rowEntry.getKey();
            ConcurrentMap<C, V> row = rowEntry.getValue();
            for (Map.Entry<C, V> columnEntry : row.entrySet()) {
                C columnKey = columnEntry.getKey();
                V value = columnEntry.getValue();
                consumer.accept(rowKey, columnKey, value);
            }
        }
    }

    public void forEachSelective(TripletSelectiveConsumer<R, C, V> consumer) {
        Objects.requireNonNull(consumer);
        for (Map.Entry<R, ConcurrentMap<C, V>> rowEntry : table.entrySet()) {
            R rowKey = rowEntry.getKey();
            ConcurrentMap<C, V> row = rowEntry.getValue();
            for (Map.Entry<C, V> columnEntry : row.entrySet()) {
                C columnKey = columnEntry.getKey();
                V value = columnEntry.getValue();
                if(!consumer.accept(rowKey, columnKey, value))
                    return;
            }
        }
    }

    public Iterable<V> values() {
        Collection<Iterable<V>> iterables = new ArrayList<>(table.size());
        for (ConcurrentMap<C, V> rowMap : table.values()) {
            iterables.add(rowMap.values());
        }
        return Iterables.concat(iterables);
    }

    public void clear(){
    	table.clear();
    }
    
    @Override
    public String toString() {
        return table.toString();
    }
}
