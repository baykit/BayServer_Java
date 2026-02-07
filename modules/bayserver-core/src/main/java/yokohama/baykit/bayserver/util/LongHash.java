package yokohama.baykit.bayserver.util;

import java.util.*;

/**
 * Final optimized primitive long-key HashMap.
 * - Tombstones for O(1) removals.
 * - Power-of-two masking for fast indexing.
 * - Full long range support (including 0 and Long.MIN_VALUE).
 */
public class LongHash<V> {

    private long[] keys;
    private V[] values;
    private int capacity;
    private int mask;
    private int size;
    private int usedSlots;
    private int threshold;

    private static final long EMPTY_KEY = 0L;
    private static final long REMOVED_KEY = Long.MIN_VALUE;
    private static final float LOAD_FACTOR = 0.75f;

    // Special cases for reserved keys
    private boolean hasZeroKey = false;
    private V zeroValue = null;
    private boolean hasMinKey = false;
    private V minValue = null;

    @SuppressWarnings("unchecked")
    public LongHash(int initialCapacity) {
        int cap = 1;
        while (cap < initialCapacity) cap <<= 1;

        this.capacity = Math.max(4, cap);
        this.mask = this.capacity - 1;
        this.keys = new long[this.capacity];
        this.values = (V[]) new Object[this.capacity];
        this.threshold = (int) (this.capacity * LOAD_FACTOR);
    }

    public int size() {
        int s = size;
        if (hasZeroKey) s++;
        if (hasMinKey) s++;
        return s;
    }

    public boolean isEmpty() {
        return size() == 0;
    }

    public V get(long k) {
        if (k == 0L) return hasZeroKey ? zeroValue : null;
        if (k == REMOVED_KEY) return hasMinKey ? minValue : null;

        int idx = hash(k) & mask;
        long curr;
        while ((curr = keys[idx]) != EMPTY_KEY) {
            if (curr == k) return values[idx];
            idx = (idx + 1) & mask;
        }
        return null;
    }

    public V put(long k, V value) {
        if (k == 0L) {
            V old = zeroValue;
            if (!hasZeroKey) hasZeroKey = true;
            zeroValue = value;
            return old;
        }
        if (k == REMOVED_KEY) {
            V old = minValue;
            if (!hasMinKey) hasMinKey = true;
            minValue = value;
            return old;
        }

        if (usedSlots >= threshold) rehash();

        int idx = hash(k) & mask;
        int firstRemoved = -1;

        while (keys[idx] != EMPTY_KEY) {
            if (keys[idx] == k) {
                V old = values[idx];
                values[idx] = value;
                return old;
            }
            if (keys[idx] == REMOVED_KEY && firstRemoved == -1) {
                firstRemoved = idx;
            }
            idx = (idx + 1) & mask;
        }

        if (firstRemoved != -1) {
            idx = firstRemoved;
        } else {
            usedSlots++;
        }

        keys[idx] = k;
        values[idx] = value;
        size++;
        return null;
    }

    public V remove(long k) {
        if (k == 0L) {
            if (!hasZeroKey) return null;
            V old = zeroValue;
            hasZeroKey = false;
            zeroValue = null;
            return old;
        }
        if (k == REMOVED_KEY) {
            if (!hasMinKey) return null;
            V old = minValue;
            hasMinKey = false;
            minValue = null;
            return old;
        }

        int idx = hash(k) & mask;
        long curr;
        while ((curr = keys[idx]) != EMPTY_KEY) {
            if (curr == k) {
                V old = values[idx];
                keys[idx] = REMOVED_KEY;
                values[idx] = null;
                size--;
                return old;
            }
            idx = (idx + 1) & mask;
        }
        return null;
    }

    public void clear() {
        Arrays.fill(keys, EMPTY_KEY);
        Arrays.fill(values, null);
        hasZeroKey = false;
        zeroValue = null;
        hasMinKey = false;
        minValue = null;
        size = 0;
        usedSlots = 0;
    }

    @SuppressWarnings("unchecked")
    private void rehash() {
        long[] oldKeys = keys;
        V[] oldValues = values;
        int oldCap = capacity;

        // ONLY expand if the actual active size is very high (e.g., 50% of capacity)
        // If we just have many tombstones, keep the same capacity to clear them.
        int newCap = (size > capacity * 0.5) ? oldCap * 2 : oldCap;

        this.capacity = newCap;
        this.mask = this.capacity - 1;
        this.keys = new long[capacity];
        this.values = (V[]) new Object[capacity];
        this.threshold = (int) (capacity * LOAD_FACTOR);

        this.size = 0;
        this.usedSlots = 0;

        for (int i = 0; i < oldCap; i++) {
            if (oldKeys[i] != EMPTY_KEY && oldKeys[i] != REMOVED_KEY) {
                // This put will not trigger another rehash because usedSlots is reset
                put(oldKeys[i], oldValues[i]);
            }
        }
    }


    private int hash(long k) {
        k ^= k >>> 33;
        k *= 0xff51afd7ed558ccdL;
        k ^= k >>> 33;
        k *= 0xc4ceb9fe1a85ec53L;
        k ^= k >>> 33;
        return (int) k & 0x7fffffff;
    }

    public long[] keys() { return keys; }

    public Object[] values() {
        ArrayList<V> vals = new ArrayList<>();
        for(int i = 0; i < keys.length; i++) {
            if(keys[i] != EMPTY_KEY && keys[i] != REMOVED_KEY)
                vals.add(values[i]);
        }
        return vals.toArray();
    }

    public boolean hasZeroKey() { return hasZeroKey; }
    public V zeroValue() { return zeroValue; }
    public boolean hasMinKey() { return hasMinKey; }
    public V minValue() { return minValue; }
}
