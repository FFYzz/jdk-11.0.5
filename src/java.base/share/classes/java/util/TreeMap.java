/*
 * Copyright (c) 1997, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package java.util;

import java.io.Serializable;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * A Red-Black tree based {@link NavigableMap} implementation.
 * The map is sorted according to the {@linkplain Comparable natural
 * ordering} of its keys, or by a {@link Comparator} provided at map
 * creation time, depending on which constructor is used.
 * <p>
 * TreeMap 是基于红黑树的实现。
 * map 的排序规则是根据 key(Comparable) 的自然排序规则
 * 或者根据过早方法中给定的比较规则(Comparator)进行排序。
 *
 * <p>This implementation provides guaranteed log(n) time cost for the
 * {@code containsKey}, {@code get}, {@code put} and {@code remove}
 * operations.  Algorithms are adaptations of those in Cormen, Leiserson, and
 * Rivest's <em>Introduction to Algorithms</em>.
 * <p>
 * containsKey,get,put,remove 操作都是 Log(n)的复杂度。算法实现改编自CLR的算法导论
 *
 * <p>Note that the ordering maintained by a tree map, like any sorted map, and
 * whether or not an explicit comparator is provided, must be <em>consistent
 * with {@code equals}</em> if this sorted map is to correctly implement the
 * {@code Map} interface.  (See {@code Comparable} or {@code Comparator} for a
 * precise definition of <em>consistent with equals</em>.)  This is so because
 * the {@code Map} interface is defined in terms of the {@code equals}
 * operation, but a sorted map performs all key comparisons using its {@code
 * compareTo} (or {@code compare}) method, so two keys that are deemed equal by
 * this method are, from the standpoint of the sorted map, equal.  The behavior
 * of a sorted map <em>is</em> well-defined even if its ordering is
 * inconsistent with {@code equals}; it just fails to obey the general contract
 * of the {@code Map} interface.
 * <p>
 * 使用的比较器要包含 等于 的情况。
 *
 * <p><strong>Note that this implementation is not synchronized.</strong>
 * If multiple threads access a map concurrently, and at least one of the
 * threads modifies the map structurally, it <em>must</em> be synchronized
 * externally.  (A structural modification is any operation that adds or
 * deletes one or more mappings; merely changing the value associated
 * with an existing key is not a structural modification.)  This is
 * typically accomplished by synchronizing on some object that naturally
 * encapsulates the map.
 * If no such object exists, the map should be "wrapped" using the
 * {@link Collections#synchronizedSortedMap Collections.synchronizedSortedMap}
 * method.  This is best done at creation time, to prevent accidental
 * unsynchronized access to the map: <pre>
 *   SortedMap m = Collections.synchronizedSortedMap(new TreeMap(...));</pre>
 * <p>
 * 非同步的。
 * 多线程并发访问，比如外部手动加锁保证线程安全。
 * 也可以使用包装类 Collections#synchronizedSortedMap
 *
 * <p>The iterators returned by the {@code iterator} method of the collections
 * returned by all of this class's "collection view methods" are
 * <em>fail-fast</em>: if the map is structurally modified at any time after
 * the iterator is created, in any way except through the iterator's own
 * {@code remove} method, the iterator will throw a {@link
 * ConcurrentModificationException}.  Thus, in the face of concurrent
 * modification, the iterator fails quickly and cleanly, rather than risking
 * arbitrary, non-deterministic behavior at an undetermined time in the future.
 * <p>
 * 迭代器支持 fail-fast
 * 会抛出 ConcurrentModificationException 异常
 *
 * <p>Note that the fail-fast behavior of an iterator cannot be guaranteed
 * as it is, generally speaking, impossible to make any hard guarantees in the
 * presence of unsynchronized concurrent modification.  Fail-fast iterators
 * throw {@code ConcurrentModificationException} on a best-effort basis.
 * Therefore, it would be wrong to write a program that depended on this
 * exception for its correctness:   <em>the fail-fast behavior of iterators
 * should be used only to detect bugs.</em>
 * <p>
 * fail-fast 不能作为安全机制，只能用来检测 bug
 *
 * <p>All {@code Map.Entry} pairs returned by methods in this class
 * and its views represent snapshots of mappings at the time they were
 * produced. They do <strong>not</strong> support the {@code Entry.setValue}
 * method. (Note however that it is possible to change mappings in the
 * associated map using {@code put}.)
 *
 * <p>This class is a member of the
 * <a href="{@docRoot}/java.base/java/util/package-summary.html#CollectionsFramework">
 * Java Collections Framework</a>.
 *
 * @param <K> the type of keys maintained by this map
 * @param <V> the type of mapped values
 * @author Josh Bloch and Doug Lea
 * @see Map
 * @see HashMap
 * @see Hashtable
 * @see Comparable
 * @see Comparator
 * @see Collection
 * @since 1.2
 */

/**
 * 以红黑树的形式存储，支持按 key 的规则排序
 * 红黑树的5大性质：
 * 1. 所有节点要么为红色，要么为黑色
 * 2. 根节点为黑色
 * 3. 叶子节点为黑色(这里的叶子节点指 nil 节点)
 * 4. 任意节点到子孙节点之间的路径上的包含相同的黑色节点的个数
 * 5. 红色节点的孩子节点必须是黑色节点
 * <p>
 * 为什么要给树节点染上颜色？
 * 1. 颜色的意义是为了构造树的平衡。
 * 2. 使用颜色可以较方便的满足平衡的条件，以及树的调整。
 */
public class TreeMap<K, V>
        extends AbstractMap<K, V>
        implements NavigableMap<K, V>, Cloneable, java.io.Serializable {
    /**
     * The comparator used to maintain order in this tree map, or
     * null if it uses the natural ordering of its keys.
     * <p>
     * 用于维护排序规则，若不指定，则按照 key 的自然顺序排序
     *
     * @serial
     */
    private final Comparator<? super K> comparator;

    /**
     * 根节点
     */
    private transient Entry<K, V> root;

    /**
     * entry 的数量
     * The number of entries in the tree
     */
    private transient int size = 0;

    /**
     * 修改次数，用于 fail-fast 机制
     * The number of structural modifications to the tree.
     */
    private transient int modCount = 0;

    /**
     * 构造函数
     * 使用自然排序，插入的 key 必须是实现 Comparable 接口
     * key 之间是可以相互比较的
     * 如果 key 没有实现 Comparable 接口，则会抛出 ClassCastException 异常
     * 或者可以指定 Comparator 接口，自定义排序规则，那么 key 不显示 Comparable 接口也是可以的
     * <p>
     * Constructs a new, empty tree map, using the natural ordering of its
     * keys.  All keys inserted into the map must implement the {@link
     * Comparable} interface.  Furthermore, all such keys must be
     * <em>mutually comparable</em>: {@code k1.compareTo(k2)} must not throw
     * a {@code ClassCastException} for any keys {@code k1} and
     * {@code k2} in the map.  If the user attempts to put a key into the
     * map that violates this constraint (for example, the user attempts to
     * put a string key into a map whose keys are integers), the
     * {@code put(Object key, Object value)} call will throw a
     * {@code ClassCastException}.
     */
    public TreeMap() {
        comparator = null;
    }

    /**
     * 自定义的排序规则，实现 Comparator 接口
     * <p>
     * Constructs a new, empty tree map, ordered according to the given
     * comparator.  All keys inserted into the map must be <em>mutually
     * comparable</em> by the given comparator: {@code comparator.compare(k1,
     * k2)} must not throw a {@code ClassCastException} for any keys
     * {@code k1} and {@code k2} in the map.  If the user attempts to put
     * a key into the map that violates this constraint, the {@code put(Object
     * key, Object value)} call will throw a
     * {@code ClassCastException}.
     *
     * @param comparator the comparator that will be used to order this map.
     *                   If {@code null}, the {@linkplain Comparable natural
     *                   ordering} of the keys will be used.
     */
    public TreeMap(Comparator<? super K> comparator) {
        this.comparator = comparator;
    }

    /**
     * 传入的 Map 的 key 必须实现了 Comparable 接口。
     * 并且所有的 key 之间是可以比较的。
     * 完成该操作需要 n*logn 时间
     * <p>
     * Constructs a new tree map containing the same mappings as the given
     * map, ordered according to the <em>natural ordering</em> of its keys.
     * All keys inserted into the new map must implement the {@link
     * Comparable} interface.  Furthermore, all such keys must be
     * <em>mutually comparable</em>: {@code k1.compareTo(k2)} must not throw
     * a {@code ClassCastException} for any keys {@code k1} and
     * {@code k2} in the map.  This method runs in n*log(n) time.
     *
     * @param m the map whose mappings are to be placed in this map
     * @throws ClassCastException   if the keys in m are not {@link Comparable},
     *                              or are not mutually comparable
     * @throws NullPointerException if the specified map is null
     */
    public TreeMap(Map<? extends K, ? extends V> m) {
        comparator = null;
        putAll(m);
    }

    /**
     * 线性时间就能构造完成一个新的 map
     * <p>
     * Constructs a new tree map containing the same mappings and
     * using the same ordering as the specified sorted map.  This
     * method runs in linear time.
     *
     * @param m the sorted map whose mappings are to be placed in this map,
     *          and whose comparator is to be used to sort this map
     * @throws NullPointerException if the specified map is null
     */
    public TreeMap(SortedMap<K, ? extends V> m) {
        comparator = m.comparator();
        try {
            buildFromSorted(m.size(), m.entrySet().iterator(), null, null);
        } catch (java.io.IOException | ClassNotFoundException cannotHappen) {
        }
    }


    // Query Operations

    /**
     * 返回 entry 的数量
     * <p>
     * Returns the number of key-value mappings in this map.
     *
     * @return the number of key-value mappings in this map
     */
    public int size() {
        return size;
    }

    /**
     * 返回是否包含某个 key
     * 查找树
     * <p>
     * Returns {@code true} if this map contains a mapping for the specified
     * key.
     *
     * @param key key whose presence in this map is to be tested
     * @return {@code true} if this map contains a mapping for the
     * specified key
     * @throws ClassCastException   if the specified key cannot be compared
     *                              with the keys currently in the map
     * @throws NullPointerException if the specified key is null
     *                              and this map uses natural ordering, or its comparator
     *                              does not permit null keys
     */
    public boolean containsKey(Object key) {
        return getEntry(key) != null;
    }

    /**
     * 是否包含某个 value
     * 线性时间，因为需要遍历
     * <p>
     * Returns {@code true} if this map maps one or more keys to the
     * specified value.  More formally, returns {@code true} if and only if
     * this map contains at least one mapping to a value {@code v} such
     * that {@code (value==null ? v==null : value.equals(v))}.  This
     * operation will probably require time linear in the map size for
     * most implementations.
     *
     * @param value value whose presence in this map is to be tested
     * @return {@code true} if a mapping to {@code value} exists;
     * {@code false} otherwise
     * @since 1.2
     */
    public boolean containsValue(Object value) {
        for (Entry<K, V> e = getFirstEntry(); e != null; e = successor(e))
            if (valEquals(value, e.value))
                return true;
        return false;
    }

    /**
     * 根据 key 获取 value
     * Returns the value to which the specified key is mapped,
     * or {@code null} if this map contains no mapping for the key.
     *
     * <p>More formally, if this map contains a mapping from a key
     * {@code k} to a value {@code v} such that {@code key} compares
     * equal to {@code k} according to the map's ordering, then this
     * method returns {@code v}; otherwise it returns {@code null}.
     * (There can be at most one such mapping.)
     *
     * <p>A return value of {@code null} does not <em>necessarily</em>
     * indicate that the map contains no mapping for the key; it's also
     * possible that the map explicitly maps the key to {@code null}.
     * The {@link #containsKey containsKey} operation may be used to
     * distinguish these two cases.
     *
     * @throws ClassCastException   if the specified key cannot be compared
     *                              with the keys currently in the map
     * @throws NullPointerException if the specified key is null
     *                              and this map uses natural ordering, or its comparator
     *                              does not permit null keys
     */
    public V get(Object key) {
        Entry<K, V> p = getEntry(key);
        return (p == null ? null : p.value);
    }

    /**
     * 返回当前 TreeMap 实例的 comparator 实例
     */
    public Comparator<? super K> comparator() {
        return comparator;
    }

    /**
     * 得到 TreeMap 中的第一个元素
     * 根据排序规则最小/最大的那个 key
     *
     * @throws NoSuchElementException {@inheritDoc}
     */
    public K firstKey() {
        return key(getFirstEntry());
    }

    /**
     * 得到 TreeMap 中的最后一个元素
     * 根据排序规则最小/最大的那个 key
     *
     * @throws NoSuchElementException {@inheritDoc}
     */
    public K lastKey() {
        return key(getLastEntry());
    }

    /**
     * 使用
     * Copies all of the mappings from the specified map to this map.
     * These mappings replace any mappings that this map had for any
     * of the keys currently in the specified map.
     *
     * @param map mappings to be stored in this map
     * @throws ClassCastException   if the class of a key or value in
     *                              the specified map prevents it from being stored in this map
     * @throws NullPointerException if the specified map is null or
     *                              the specified map contains a null key and this map does not
     *                              permit null keys
     */
    public void putAll(Map<? extends K, ? extends V> map) {
        // 传入 map 的 size
        int mapSize = map.size();
        // 当前 TreeMap 为空 且 传入的 map 不为空 且 传入 map 是 SortedMap
        if (size==0 && mapSize!=0 && map instanceof SortedMap) {
            Comparator<?> c = ((SortedMap<?,?>)map).comparator();
            // 如果比较两个 map 的比较器是否一致
            if (c == comparator || (c != null && c.equals(comparator))) {
                ++modCount;
                try {
                    // 使用这种方式条件挺苛刻的.....
                    buildFromSorted(mapSize, map.entrySet().iterator(),
                            null, null);
                } catch (java.io.IOException | ClassNotFoundException cannotHappen) {
                }
                return;
            }
        }
        super.putAll(map);
    }

    /**
     * 根据 key 获取 entry
     * <p>
     * Returns this map's entry for the given key, or {@code null} if the map
     * does not contain an entry for the key.
     *
     * @return this map's entry for the given key, or {@code null} if the map
     * does not contain an entry for the key
     * @throws ClassCastException   if the specified key cannot be compared
     *                              with the keys currently in the map
     * @throws NullPointerException if the specified key is null
     *                              and this map uses natural ordering, or its comparator
     *                              does not permit null keys
     */
    final Entry<K, V> getEntry(Object key) {
        // Offload comparator-based version for sake of performance
        // 如果自定义了比较规则
        if (comparator != null)
            return getEntryUsingComparator(key);
        if (key == null)
            throw new NullPointerException();
        // 自然比较规则
        @SuppressWarnings("unchecked")
        Comparable<? super K> k = (Comparable<? super K>) key;
        // 根节点
        Entry<K, V> p = root;
        // 查找树
        while (p != null) {
            int cmp = k.compareTo(p.key);
            if (cmp < 0)
                p = p.left;
            else if (cmp > 0)
                p = p.right;
            else
                return p;
        }
        // 找不到则返回 null
        return null;
    }

    /**
     * 根据自定义的规则比较去寻找
     * <p>
     * Version of getEntry using comparator. Split off from getEntry
     * for performance. (This is not worth doing for most methods,
     * that are less dependent on comparator performance, but is
     * worthwhile here.)
     */
    final Entry<K, V> getEntryUsingComparator(Object key) {
        @SuppressWarnings("unchecked")
        K k = (K) key;
        // 获取当前 TreeMap 实例的 comparator 规则
        Comparator<? super K> cpr = comparator;
        if (cpr != null) {
            // 获取根节点
            Entry<K, V> p = root;
            // 查找树
            while (p != null) {
                int cmp = cpr.compare(k, p.key);
                if (cmp < 0)
                    p = p.left;
                else if (cmp > 0)
                    p = p.right;
                else
                    return p;
            }
        }
        // 查找不到返回 空
        return null;
    }

    /**
     * Gets the entry corresponding to the specified key; if no such entry
     * exists, returns the entry for the least key greater than the specified
     * key; if no such entry exists (i.e., the greatest key in the Tree is less
     * than the specified key), returns {@code null}.
     */
    final Entry<K, V> getCeilingEntry(K key) {
        Entry<K, V> p = root;
        while (p != null) {
            int cmp = compare(key, p.key);
            if (cmp < 0) {
                if (p.left != null)
                    p = p.left;
                else
                    return p;
            } else if (cmp > 0) {
                if (p.right != null) {
                    p = p.right;
                } else {
                    Entry<K, V> parent = p.parent;
                    Entry<K, V> ch = p;
                    while (parent != null && ch == parent.right) {
                        ch = parent;
                        parent = parent.parent;
                    }
                    return parent;
                }
            } else
                return p;
        }
        return null;
    }

    /**
     * Gets the entry corresponding to the specified key; if no such entry
     * exists, returns the entry for the greatest key less than the specified
     * key; if no such entry exists, returns {@code null}.
     */
    final Entry<K, V> getFloorEntry(K key) {
        Entry<K, V> p = root;
        while (p != null) {
            int cmp = compare(key, p.key);
            if (cmp > 0) {
                if (p.right != null)
                    p = p.right;
                else
                    return p;
            } else if (cmp < 0) {
                if (p.left != null) {
                    p = p.left;
                } else {
                    Entry<K, V> parent = p.parent;
                    Entry<K, V> ch = p;
                    while (parent != null && ch == parent.left) {
                        ch = parent;
                        parent = parent.parent;
                    }
                    return parent;
                }
            } else
                return p;

        }
        return null;
    }

    /**
     * Gets the entry for the least key greater than the specified
     * key; if no such entry exists, returns the entry for the least
     * key greater than the specified key; if no such entry exists
     * returns {@code null}.
     */
    final Entry<K, V> getHigherEntry(K key) {
        Entry<K, V> p = root;
        while (p != null) {
            int cmp = compare(key, p.key);
            if (cmp < 0) {
                if (p.left != null)
                    p = p.left;
                else
                    return p;
            } else {
                if (p.right != null) {
                    p = p.right;
                } else {
                    Entry<K, V> parent = p.parent;
                    Entry<K, V> ch = p;
                    while (parent != null && ch == parent.right) {
                        ch = parent;
                        parent = parent.parent;
                    }
                    return parent;
                }
            }
        }
        return null;
    }

    /**
     * Returns the entry for the greatest key less than the specified key; if
     * no such entry exists (i.e., the least key in the Tree is greater than
     * the specified key), returns {@code null}.
     */
    final Entry<K, V> getLowerEntry(K key) {
        Entry<K, V> p = root;
        while (p != null) {
            int cmp = compare(key, p.key);
            if (cmp > 0) {
                if (p.right != null)
                    p = p.right;
                else
                    return p;
            } else {
                if (p.left != null) {
                    p = p.left;
                } else {
                    Entry<K, V> parent = p.parent;
                    Entry<K, V> ch = p;
                    while (parent != null && ch == parent.left) {
                        ch = parent;
                        parent = parent.parent;
                    }
                    return parent;
                }
            }
        }
        return null;
    }

    /**
     * Associates the specified value with the specified key in this map.
     * If the map previously contained a mapping for the key, the old
     * value is replaced.
     *
     * @param key   key with which the specified value is to be associated
     * @param value value to be associated with the specified key
     * @return the previous value associated with {@code key}, or
     * {@code null} if there was no mapping for {@code key}.
     * (A {@code null} return can also indicate that the map
     * previously associated {@code null} with {@code key}.)
     * @throws ClassCastException   if the specified key cannot be compared
     *                              with the keys currently in the map
     * @throws NullPointerException if the specified key is null
     *                              and this map uses natural ordering, or its comparator
     *                              does not permit null keys
     */
    public V put(K key, V value) {
        // 获取根节点
        Entry<K, V> t = root;
        // 若根节点为空
        if (t == null) {
            // 类型检查，key 是否实现了 comparable 接口 或者自定义了 comparator 比较器
            // 不满足上述情况抛异常，不允许插入
            compare(key, key); // type (and possibly null) check
            // 直接作为 根节点
            root = new Entry<>(key, value, null);
            // 技数
            size = 1;
            modCount++;
            return null;
        }
        // 根节点不为空
        // 保存比较结果
        int cmp;
        // 暂存父节点
        Entry<K, V> parent;
        // split comparator and comparable paths
        Comparator<? super K> cpr = comparator;
        // check 是否自定义了比较器
        // 如果自定义了比较器
        if (cpr != null) {
            // 使用 do-while 因为前面已经确定 root 不为空
            do {
                // 从根节点出发
                parent = t;
                // 比较大小，确定左右子树
                cmp = cpr.compare(key, t.key);
                // 比 parent 中的 key 小
                // 往左子树找
                if (cmp < 0)
                    t = t.left;
                    // 比 parent 中的 key 大
                    // 往右子树找
                else if (cmp > 0)
                    t = t.right;
                    // key 相等
                else
                    // 替换 value
                    return t.setValue(value);
            } while (t != null);
            // 使用自然排序规则
        } else {
            // key 不能为 null ，会报 NPE
            // HashMap 中的 key 可以为 null
            // 比较规则与上述类似，只是使用了自然排序
            if (key == null)
                throw new NullPointerException();
            @SuppressWarnings("unchecked")
            Comparable<? super K> k = (Comparable<? super K>) key;
            do {
                parent = t;
                cmp = k.compareTo(t.key);
                if (cmp < 0)
                    t = t.left;
                else if (cmp > 0)
                    t = t.right;
                else
                    return t.setValue(value);
            } while (t != null);
        }
        // 上述的 if-else 最终能够找到一个 parent 的 entry
        // 以及 一个 cmp 的结果，用于确定加入左子树还是右子树
        Entry<K, V> e = new Entry<>(key, value, parent);
        // 成为 parent 的左子树
        if (cmp < 0)
            parent.left = e;
            // 成为 parent 的右子树
        else
            parent.right = e;
        // 调整树
        fixAfterInsertion(e);
        // 计数
        size++;
        modCount++;
        return null;
    }

    /**
     * 删除 key 对应的 entry，并返回 value
     * <p>
     * Removes the mapping for this key from this TreeMap if present.
     *
     * @param key key for which mapping should be removed
     * @return the previous value associated with {@code key}, or
     * {@code null} if there was no mapping for {@code key}.
     * (A {@code null} return can also indicate that the map
     * previously associated {@code null} with {@code key}.)
     * @throws ClassCastException   if the specified key cannot be compared
     *                              with the keys currently in the map
     * @throws NullPointerException if the specified key is null
     *                              and this map uses natural ordering, or its comparator
     *                              does not permit null keys
     */
    public V remove(Object key) {
        // 根据 key 找到需要删除的 entry
        Entry<K, V> p = getEntry(key);
        if (p == null)
            return null;

        V oldValue = p.value;
        // 删除 entry
        deleteEntry(p);
        // 返回删除 entry 对应的 value
        return oldValue;
    }

    /**
     * 清除 map 中的所有元素
     * 只需将 root 指向 null
     * <p>
     * Removes all of the mappings from this map.
     * The map will be empty after this call returns.
     */
    public void clear() {
        modCount++;
        size = 0;
        root = null;
    }

    /**
     * Returns a shallow copy of this {@code TreeMap} instance. (The keys and
     * values themselves are not cloned.)
     *
     * @return a shallow copy of this map
     */
    public Object clone() {
        TreeMap<?, ?> clone;
        try {
            clone = (TreeMap<?, ?>) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new InternalError(e);
        }

        // Put clone into "virgin" state (except for comparator)
        clone.root = null;
        clone.size = 0;
        clone.modCount = 0;
        clone.entrySet = null;
        clone.navigableKeySet = null;
        clone.descendingMap = null;

        // Initialize clone with our mappings
        try {
            clone.buildFromSorted(size, entrySet().iterator(), null, null);
        } catch (java.io.IOException | ClassNotFoundException cannotHappen) {
        }

        return clone;
    }

    // NavigableMap API methods

    /**
     * @since 1.6
     */
    public Map.Entry<K, V> firstEntry() {
        return exportEntry(getFirstEntry());
    }

    /**
     * @since 1.6
     */
    public Map.Entry<K, V> lastEntry() {
        return exportEntry(getLastEntry());
    }

    /**
     * @since 1.6
     */
    public Map.Entry<K, V> pollFirstEntry() {
        Entry<K, V> p = getFirstEntry();
        Map.Entry<K, V> result = exportEntry(p);
        if (p != null)
            deleteEntry(p);
        return result;
    }

    /**
     * @since 1.6
     */
    public Map.Entry<K, V> pollLastEntry() {
        Entry<K, V> p = getLastEntry();
        Map.Entry<K, V> result = exportEntry(p);
        if (p != null)
            deleteEntry(p);
        return result;
    }

    /**
     * @throws ClassCastException   {@inheritDoc}
     * @throws NullPointerException if the specified key is null
     *                              and this map uses natural ordering, or its comparator
     *                              does not permit null keys
     * @since 1.6
     */
    public Map.Entry<K, V> lowerEntry(K key) {
        return exportEntry(getLowerEntry(key));
    }

    /**
     * @throws ClassCastException   {@inheritDoc}
     * @throws NullPointerException if the specified key is null
     *                              and this map uses natural ordering, or its comparator
     *                              does not permit null keys
     * @since 1.6
     */
    public K lowerKey(K key) {
        return keyOrNull(getLowerEntry(key));
    }

    /**
     * @throws ClassCastException   {@inheritDoc}
     * @throws NullPointerException if the specified key is null
     *                              and this map uses natural ordering, or its comparator
     *                              does not permit null keys
     * @since 1.6
     */
    public Map.Entry<K, V> floorEntry(K key) {
        return exportEntry(getFloorEntry(key));
    }

    /**
     * @throws ClassCastException   {@inheritDoc}
     * @throws NullPointerException if the specified key is null
     *                              and this map uses natural ordering, or its comparator
     *                              does not permit null keys
     * @since 1.6
     */
    public K floorKey(K key) {
        return keyOrNull(getFloorEntry(key));
    }

    /**
     * @throws ClassCastException   {@inheritDoc}
     * @throws NullPointerException if the specified key is null
     *                              and this map uses natural ordering, or its comparator
     *                              does not permit null keys
     * @since 1.6
     */
    public Map.Entry<K, V> ceilingEntry(K key) {
        return exportEntry(getCeilingEntry(key));
    }

    /**
     * @throws ClassCastException   {@inheritDoc}
     * @throws NullPointerException if the specified key is null
     *                              and this map uses natural ordering, or its comparator
     *                              does not permit null keys
     * @since 1.6
     */
    public K ceilingKey(K key) {
        return keyOrNull(getCeilingEntry(key));
    }

    /**
     * @throws ClassCastException   {@inheritDoc}
     * @throws NullPointerException if the specified key is null
     *                              and this map uses natural ordering, or its comparator
     *                              does not permit null keys
     * @since 1.6
     */
    public Map.Entry<K, V> higherEntry(K key) {
        return exportEntry(getHigherEntry(key));
    }

    /**
     * @throws ClassCastException   {@inheritDoc}
     * @throws NullPointerException if the specified key is null
     *                              and this map uses natural ordering, or its comparator
     *                              does not permit null keys
     * @since 1.6
     */
    public K higherKey(K key) {
        return keyOrNull(getHigherEntry(key));
    }

    // Views

    /**
     * Fields initialized to contain an instance of the entry set view
     * the first time this view is requested.  Views are stateless, so
     * there's no reason to create more than one.
     */
    private transient EntrySet entrySet;
    private transient KeySet<K> navigableKeySet;
    private transient NavigableMap<K, V> descendingMap;

    /**
     * Returns a {@link Set} view of the keys contained in this map.
     *
     * <p>The set's iterator returns the keys in ascending order.
     * The set's spliterator is
     * <em><a href="Spliterator.html#binding">late-binding</a></em>,
     * <em>fail-fast</em>, and additionally reports {@link Spliterator#SORTED}
     * and {@link Spliterator#ORDERED} with an encounter order that is ascending
     * key order.  The spliterator's comparator (see
     * {@link java.util.Spliterator#getComparator()}) is {@code null} if
     * the tree map's comparator (see {@link #comparator()}) is {@code null}.
     * Otherwise, the spliterator's comparator is the same as or imposes the
     * same total ordering as the tree map's comparator.
     *
     * <p>The set is backed by the map, so changes to the map are
     * reflected in the set, and vice-versa.  If the map is modified
     * while an iteration over the set is in progress (except through
     * the iterator's own {@code remove} operation), the results of
     * the iteration are undefined.  The set supports element removal,
     * which removes the corresponding mapping from the map, via the
     * {@code Iterator.remove}, {@code Set.remove},
     * {@code removeAll}, {@code retainAll}, and {@code clear}
     * operations.  It does not support the {@code add} or {@code addAll}
     * operations.
     */
    public Set<K> keySet() {
        return navigableKeySet();
    }

    /**
     * @since 1.6
     */
    public NavigableSet<K> navigableKeySet() {
        KeySet<K> nks = navigableKeySet;
        return (nks != null) ? nks : (navigableKeySet = new KeySet<>(this));
    }

    /**
     * @since 1.6
     */
    public NavigableSet<K> descendingKeySet() {
        return descendingMap().navigableKeySet();
    }

    /**
     * 返回 map 中所有的 value
     * <p>
     * Returns a {@link Collection} view of the values contained in this map.
     *
     * <p>The collection's iterator returns the values in ascending order
     * of the corresponding keys. The collection's spliterator is
     * <em><a href="Spliterator.html#binding">late-binding</a></em>,
     * <em>fail-fast</em>, and additionally reports {@link Spliterator#ORDERED}
     * with an encounter order that is ascending order of the corresponding
     * keys.
     *
     * <p>The collection is backed by the map, so changes to the map are
     * reflected in the collection, and vice-versa.  If the map is
     * modified while an iteration over the collection is in progress
     * (except through the iterator's own {@code remove} operation),
     * the results of the iteration are undefined.  The collection
     * supports element removal, which removes the corresponding
     * mapping from the map, via the {@code Iterator.remove},
     * {@code Collection.remove}, {@code removeAll},
     * {@code retainAll} and {@code clear} operations.  It does not
     * support the {@code add} or {@code addAll} operations.
     */
    public Collection<V> values() {
        Collection<V> vs = values;
        if (vs == null) {
            vs = new Values();
            values = vs;
        }
        return vs;
    }

    /**
     * 返回 map 中所有的 entry
     * <p>
     * Returns a {@link Set} view of the mappings contained in this map.
     *
     * <p>The set's iterator returns the entries in ascending key order. The
     * set's spliterator is
     * <em><a href="Spliterator.html#binding">late-binding</a></em>,
     * <em>fail-fast</em>, and additionally reports {@link Spliterator#SORTED} and
     * {@link Spliterator#ORDERED} with an encounter order that is ascending key
     * order.
     *
     * <p>The set is backed by the map, so changes to the map are
     * reflected in the set, and vice-versa.  If the map is modified
     * while an iteration over the set is in progress (except through
     * the iterator's own {@code remove} operation, or through the
     * {@code setValue} operation on a map entry returned by the
     * iterator) the results of the iteration are undefined.  The set
     * supports element removal, which removes the corresponding
     * mapping from the map, via the {@code Iterator.remove},
     * {@code Set.remove}, {@code removeAll}, {@code retainAll} and
     * {@code clear} operations.  It does not support the
     * {@code add} or {@code addAll} operations.
     */
    public Set<Map.Entry<K, V>> entrySet() {
        EntrySet es = entrySet;
        return (es != null) ? es : (entrySet = new EntrySet());
    }

    /**
     * 返回 key 逆序的 map
     *
     * @since 1.6
     */
    public NavigableMap<K, V> descendingMap() {
        NavigableMap<K, V> km = descendingMap;
        return (km != null) ? km :
                (descendingMap = new DescendingSubMap<>(this,
                        true, null, true,
                        true, null, true));
    }

    /**
     * @throws ClassCastException       {@inheritDoc}
     * @throws NullPointerException     if {@code fromKey} or {@code toKey} is
     *                                  null and this map uses natural ordering, or its comparator
     *                                  does not permit null keys
     * @throws IllegalArgumentException {@inheritDoc}
     * @since 1.6
     */
    public NavigableMap<K, V> subMap(K fromKey, boolean fromInclusive,
                                     K toKey, boolean toInclusive) {
        return new AscendingSubMap<>(this,
                false, fromKey, fromInclusive,
                false, toKey, toInclusive);
    }

    /**
     * @throws ClassCastException       {@inheritDoc}
     * @throws NullPointerException     if {@code toKey} is null
     *                                  and this map uses natural ordering, or its comparator
     *                                  does not permit null keys
     * @throws IllegalArgumentException {@inheritDoc}
     * @since 1.6
     */
    public NavigableMap<K, V> headMap(K toKey, boolean inclusive) {
        return new AscendingSubMap<>(this,
                true, null, true,
                false, toKey, inclusive);
    }

    /**
     * @throws ClassCastException       {@inheritDoc}
     * @throws NullPointerException     if {@code fromKey} is null
     *                                  and this map uses natural ordering, or its comparator
     *                                  does not permit null keys
     * @throws IllegalArgumentException {@inheritDoc}
     * @since 1.6
     */
    public NavigableMap<K, V> tailMap(K fromKey, boolean inclusive) {
        return new AscendingSubMap<>(this,
                false, fromKey, inclusive,
                true, null, true);
    }

    /**
     * @throws ClassCastException       {@inheritDoc}
     * @throws NullPointerException     if {@code fromKey} or {@code toKey} is
     *                                  null and this map uses natural ordering, or its comparator
     *                                  does not permit null keys
     * @throws IllegalArgumentException {@inheritDoc}
     */
    public SortedMap<K, V> subMap(K fromKey, K toKey) {
        return subMap(fromKey, true, toKey, false);
    }

    /**
     * @throws ClassCastException       {@inheritDoc}
     * @throws NullPointerException     if {@code toKey} is null
     *                                  and this map uses natural ordering, or its comparator
     *                                  does not permit null keys
     * @throws IllegalArgumentException {@inheritDoc}
     */
    public SortedMap<K, V> headMap(K toKey) {
        return headMap(toKey, false);
    }

    /**
     * @throws ClassCastException       {@inheritDoc}
     * @throws NullPointerException     if {@code fromKey} is null
     *                                  and this map uses natural ordering, or its comparator
     *                                  does not permit null keys
     * @throws IllegalArgumentException {@inheritDoc}
     */
    public SortedMap<K, V> tailMap(K fromKey) {
        return tailMap(fromKey, true);
    }

    @Override
    public boolean replace(K key, V oldValue, V newValue) {
        Entry<K, V> p = getEntry(key);
        if (p != null && Objects.equals(oldValue, p.value)) {
            p.value = newValue;
            return true;
        }
        return false;
    }

    @Override
    public V replace(K key, V value) {
        Entry<K, V> p = getEntry(key);
        if (p != null) {
            V oldValue = p.value;
            p.value = value;
            return oldValue;
        }
        return null;
    }

    @Override
    public void forEach(BiConsumer<? super K, ? super V> action) {
        Objects.requireNonNull(action);
        // 保存遍历前的修改次数
        int expectedModCount = modCount;
        // 执行遍历，先获取第一个元素的位置，再循环遍历后继节点
        for (Entry<K, V> e = getFirstEntry(); e != null; e = successor(e)) {
            // 执行动作
            action.accept(e.key, e.value);
            // 如果遍历过程中发生修改操作，则抛出异常
            if (expectedModCount != modCount) {
                throw new ConcurrentModificationException();
            }
        }
    }

    @Override
    public void replaceAll(BiFunction<? super K, ? super V, ? extends V> function) {
        Objects.requireNonNull(function);
        int expectedModCount = modCount;

        for (Entry<K, V> e = getFirstEntry(); e != null; e = successor(e)) {
            e.value = function.apply(e.key, e.value);

            if (expectedModCount != modCount) {
                throw new ConcurrentModificationException();
            }
        }
    }

    // View class support

    class Values extends AbstractCollection<V> {
        public Iterator<V> iterator() {
            return new ValueIterator(getFirstEntry());
        }

        public int size() {
            return TreeMap.this.size();
        }

        public boolean contains(Object o) {
            return TreeMap.this.containsValue(o);
        }

        public boolean remove(Object o) {
            for (Entry<K, V> e = getFirstEntry(); e != null; e = successor(e)) {
                if (valEquals(e.getValue(), o)) {
                    deleteEntry(e);
                    return true;
                }
            }
            return false;
        }

        public void clear() {
            TreeMap.this.clear();
        }

        public Spliterator<V> spliterator() {
            return new ValueSpliterator<>(TreeMap.this, null, null, 0, -1, 0);
        }
    }

    class EntrySet extends AbstractSet<Map.Entry<K, V>> {
        public Iterator<Map.Entry<K, V>> iterator() {
            return new EntryIterator(getFirstEntry());
        }

        public boolean contains(Object o) {
            if (!(o instanceof Map.Entry))
                return false;
            Map.Entry<?, ?> entry = (Map.Entry<?, ?>) o;
            Object value = entry.getValue();
            Entry<K, V> p = getEntry(entry.getKey());
            return p != null && valEquals(p.getValue(), value);
        }

        public boolean remove(Object o) {
            if (!(o instanceof Map.Entry))
                return false;
            Map.Entry<?, ?> entry = (Map.Entry<?, ?>) o;
            Object value = entry.getValue();
            Entry<K, V> p = getEntry(entry.getKey());
            if (p != null && valEquals(p.getValue(), value)) {
                deleteEntry(p);
                return true;
            }
            return false;
        }

        public int size() {
            return TreeMap.this.size();
        }

        public void clear() {
            TreeMap.this.clear();
        }

        public Spliterator<Map.Entry<K, V>> spliterator() {
            return new EntrySpliterator<>(TreeMap.this, null, null, 0, -1, 0);
        }
    }

    /*
     * Unlike Values and EntrySet, the KeySet class is static,
     * delegating to a NavigableMap to allow use by SubMaps, which
     * outweighs the ugliness of needing type-tests for the following
     * Iterator methods that are defined appropriately in main versus
     * submap classes.
     */

    Iterator<K> keyIterator() {
        return new KeyIterator(getFirstEntry());
    }

    Iterator<K> descendingKeyIterator() {
        return new DescendingKeyIterator(getLastEntry());
    }

    static final class KeySet<E> extends AbstractSet<E> implements NavigableSet<E> {
        private final NavigableMap<E, ?> m;

        KeySet(NavigableMap<E, ?> map) {
            m = map;
        }

        public Iterator<E> iterator() {
            if (m instanceof TreeMap)
                return ((TreeMap<E, ?>) m).keyIterator();
            else
                return ((TreeMap.NavigableSubMap<E, ?>) m).keyIterator();
        }

        public Iterator<E> descendingIterator() {
            if (m instanceof TreeMap)
                return ((TreeMap<E, ?>) m).descendingKeyIterator();
            else
                return ((TreeMap.NavigableSubMap<E, ?>) m).descendingKeyIterator();
        }

        public int size() {
            return m.size();
        }

        public boolean isEmpty() {
            return m.isEmpty();
        }

        public boolean contains(Object o) {
            return m.containsKey(o);
        }

        public void clear() {
            m.clear();
        }

        public E lower(E e) {
            return m.lowerKey(e);
        }

        public E floor(E e) {
            return m.floorKey(e);
        }

        public E ceiling(E e) {
            return m.ceilingKey(e);
        }

        public E higher(E e) {
            return m.higherKey(e);
        }

        public E first() {
            return m.firstKey();
        }

        public E last() {
            return m.lastKey();
        }

        public Comparator<? super E> comparator() {
            return m.comparator();
        }

        public E pollFirst() {
            Map.Entry<E, ?> e = m.pollFirstEntry();
            return (e == null) ? null : e.getKey();
        }

        public E pollLast() {
            Map.Entry<E, ?> e = m.pollLastEntry();
            return (e == null) ? null : e.getKey();
        }

        public boolean remove(Object o) {
            int oldSize = size();
            m.remove(o);
            return size() != oldSize;
        }

        public NavigableSet<E> subSet(E fromElement, boolean fromInclusive,
                                      E toElement, boolean toInclusive) {
            return new KeySet<>(m.subMap(fromElement, fromInclusive,
                    toElement, toInclusive));
        }

        public NavigableSet<E> headSet(E toElement, boolean inclusive) {
            return new KeySet<>(m.headMap(toElement, inclusive));
        }

        public NavigableSet<E> tailSet(E fromElement, boolean inclusive) {
            return new KeySet<>(m.tailMap(fromElement, inclusive));
        }

        public SortedSet<E> subSet(E fromElement, E toElement) {
            return subSet(fromElement, true, toElement, false);
        }

        public SortedSet<E> headSet(E toElement) {
            return headSet(toElement, false);
        }

        public SortedSet<E> tailSet(E fromElement) {
            return tailSet(fromElement, true);
        }

        public NavigableSet<E> descendingSet() {
            return new KeySet<>(m.descendingMap());
        }

        public Spliterator<E> spliterator() {
            return keySpliteratorFor(m);
        }
    }

    /**
     * Base class for TreeMap Iterators
     */
    abstract class PrivateEntryIterator<T> implements Iterator<T> {
        Entry<K, V> next;
        Entry<K, V> lastReturned;
        int expectedModCount;

        PrivateEntryIterator(Entry<K, V> first) {
            expectedModCount = modCount;
            lastReturned = null;
            next = first;
        }

        public final boolean hasNext() {
            return next != null;
        }

        final Entry<K, V> nextEntry() {
            Entry<K, V> e = next;
            if (e == null)
                throw new NoSuchElementException();
            if (modCount != expectedModCount)
                throw new ConcurrentModificationException();
            next = successor(e);
            lastReturned = e;
            return e;
        }

        final Entry<K, V> prevEntry() {
            Entry<K, V> e = next;
            if (e == null)
                throw new NoSuchElementException();
            if (modCount != expectedModCount)
                throw new ConcurrentModificationException();
            next = predecessor(e);
            lastReturned = e;
            return e;
        }

        public void remove() {
            if (lastReturned == null)
                throw new IllegalStateException();
            if (modCount != expectedModCount)
                throw new ConcurrentModificationException();
            // deleted entries are replaced by their successors
            if (lastReturned.left != null && lastReturned.right != null)
                next = lastReturned;
            deleteEntry(lastReturned);
            expectedModCount = modCount;
            lastReturned = null;
        }
    }

    final class EntryIterator extends PrivateEntryIterator<Map.Entry<K, V>> {
        EntryIterator(Entry<K, V> first) {
            super(first);
        }

        public Map.Entry<K, V> next() {
            return nextEntry();
        }
    }

    final class ValueIterator extends PrivateEntryIterator<V> {
        ValueIterator(Entry<K, V> first) {
            super(first);
        }

        public V next() {
            return nextEntry().value;
        }
    }

    final class KeyIterator extends PrivateEntryIterator<K> {
        KeyIterator(Entry<K, V> first) {
            super(first);
        }

        public K next() {
            return nextEntry().key;
        }
    }

    final class DescendingKeyIterator extends PrivateEntryIterator<K> {
        DescendingKeyIterator(Entry<K, V> first) {
            super(first);
        }

        public K next() {
            return prevEntry().key;
        }

        public void remove() {
            if (lastReturned == null)
                throw new IllegalStateException();
            if (modCount != expectedModCount)
                throw new ConcurrentModificationException();
            deleteEntry(lastReturned);
            lastReturned = null;
            expectedModCount = modCount;
        }
    }

    // Little utilities

    /**
     * 比较两个 key 的大小关系
     * <p>
     * Compares two keys using the correct comparison method for this TreeMap.
     */
    @SuppressWarnings("unchecked")
    final int compare(Object k1, Object k2) {
        return comparator == null ? ((Comparable<? super K>) k1).compareTo((K) k2)
                : comparator.compare((K) k1, (K) k2);
    }

    /**
     * 比较 value 是否相等
     * <p>
     * Test two values for equality.  Differs from o1.equals(o2) only in
     * that it copes with {@code null} o1 properly.
     */
    static final boolean valEquals(Object o1, Object o2) {
        return (o1 == null ? o2 == null : o1.equals(o2));
    }

    /**
     * 返回一个不可修改的 Entry
     * final 修饰
     * <p>
     * Return SimpleImmutableEntry for entry, or null if null
     */
    static <K, V> Map.Entry<K, V> exportEntry(TreeMap.Entry<K, V> e) {
        return (e == null) ? null :
                new AbstractMap.SimpleImmutableEntry<>(e);
    }

    /**
     * 根据 entry 返回该 entry 中的 key
     * 如果 key 为 null，返回 null
     * <p>
     * Return key for entry, or null if null
     */
    static <K, V> K keyOrNull(TreeMap.Entry<K, V> e) {
        return (e == null) ? null : e.key;
    }

    /**
     * 根据 entry 返回该 entry 中的 key
     * 如果 key 为 null，抛出 NoSuchElementException 异常
     * <p>
     * Returns the key corresponding to the specified Entry.
     *
     * @throws NoSuchElementException if the Entry is null
     */
    static <K> K key(Entry<K, ?> e) {
        if (e == null)
            throw new NoSuchElementException();
        return e.key;
    }


    // SubMaps

    /**
     * Dummy value serving as unmatchable fence key for unbounded
     * SubMapIterators
     */
    private static final Object UNBOUNDED = new Object();

    /**
     * @serial include
     */
    abstract static class NavigableSubMap<K,V> extends AbstractMap<K,V>
        implements NavigableMap<K,V>, java.io.Serializable {
        private static final long serialVersionUID = -2102997345730753016L;
        /**
         * The backing map.
         */
        final TreeMap<K, V> m;

        /**
         * Endpoints are represented as triples (fromStart, lo,
         * loInclusive) and (toEnd, hi, hiInclusive). If fromStart is
         * true, then the low (absolute) bound is the start of the
         * backing map, and the other values are ignored. Otherwise,
         * if loInclusive is true, lo is the inclusive bound, else lo
         * is the exclusive bound. Similarly for the upper bound.
         */
        final K lo, hi;
        final boolean fromStart, toEnd;
        final boolean loInclusive, hiInclusive;

        NavigableSubMap(TreeMap<K, V> m,
                        boolean fromStart, K lo, boolean loInclusive,
                        boolean toEnd, K hi, boolean hiInclusive) {
            if (!fromStart && !toEnd) {
                if (m.compare(lo, hi) > 0)
                    throw new IllegalArgumentException("fromKey > toKey");
            } else {
                if (!fromStart) // type check
                    m.compare(lo, lo);
                if (!toEnd)
                    m.compare(hi, hi);
            }

            this.m = m;
            this.fromStart = fromStart;
            this.lo = lo;
            this.loInclusive = loInclusive;
            this.toEnd = toEnd;
            this.hi = hi;
            this.hiInclusive = hiInclusive;
        }

        // internal utilities

        final boolean tooLow(Object key) {
            if (!fromStart) {
                int c = m.compare(key, lo);
                if (c < 0 || (c == 0 && !loInclusive))
                    return true;
            }
            return false;
        }

        final boolean tooHigh(Object key) {
            if (!toEnd) {
                int c = m.compare(key, hi);
                if (c > 0 || (c == 0 && !hiInclusive))
                    return true;
            }
            return false;
        }

        final boolean inRange(Object key) {
            return !tooLow(key) && !tooHigh(key);
        }

        final boolean inClosedRange(Object key) {
            return (fromStart || m.compare(key, lo) >= 0)
                    && (toEnd || m.compare(hi, key) >= 0);
        }

        final boolean inRange(Object key, boolean inclusive) {
            return inclusive ? inRange(key) : inClosedRange(key);
        }

        /*
         * Absolute versions of relation operations.
         * Subclasses map to these using like-named "sub"
         * versions that invert senses for descending maps
         */

        final TreeMap.Entry<K, V> absLowest() {
            TreeMap.Entry<K, V> e =
                    (fromStart ? m.getFirstEntry() :
                            (loInclusive ? m.getCeilingEntry(lo) :
                                    m.getHigherEntry(lo)));
            return (e == null || tooHigh(e.key)) ? null : e;
        }

        final TreeMap.Entry<K, V> absHighest() {
            TreeMap.Entry<K, V> e =
                    (toEnd ? m.getLastEntry() :
                            (hiInclusive ? m.getFloorEntry(hi) :
                                    m.getLowerEntry(hi)));
            return (e == null || tooLow(e.key)) ? null : e;
        }

        final TreeMap.Entry<K, V> absCeiling(K key) {
            if (tooLow(key))
                return absLowest();
            TreeMap.Entry<K, V> e = m.getCeilingEntry(key);
            return (e == null || tooHigh(e.key)) ? null : e;
        }

        final TreeMap.Entry<K, V> absHigher(K key) {
            if (tooLow(key))
                return absLowest();
            TreeMap.Entry<K, V> e = m.getHigherEntry(key);
            return (e == null || tooHigh(e.key)) ? null : e;
        }

        final TreeMap.Entry<K, V> absFloor(K key) {
            if (tooHigh(key))
                return absHighest();
            TreeMap.Entry<K, V> e = m.getFloorEntry(key);
            return (e == null || tooLow(e.key)) ? null : e;
        }

        final TreeMap.Entry<K, V> absLower(K key) {
            if (tooHigh(key))
                return absHighest();
            TreeMap.Entry<K, V> e = m.getLowerEntry(key);
            return (e == null || tooLow(e.key)) ? null : e;
        }

        /**
         * Returns the absolute high fence for ascending traversal
         */
        final TreeMap.Entry<K, V> absHighFence() {
            return (toEnd ? null : (hiInclusive ?
                    m.getHigherEntry(hi) :
                    m.getCeilingEntry(hi)));
        }

        /**
         * Return the absolute low fence for descending traversal
         */
        final TreeMap.Entry<K, V> absLowFence() {
            return (fromStart ? null : (loInclusive ?
                    m.getLowerEntry(lo) :
                    m.getFloorEntry(lo)));
        }

        // Abstract methods defined in ascending vs descending classes
        // These relay to the appropriate absolute versions

        abstract TreeMap.Entry<K, V> subLowest();

        abstract TreeMap.Entry<K, V> subHighest();

        abstract TreeMap.Entry<K, V> subCeiling(K key);

        abstract TreeMap.Entry<K, V> subHigher(K key);

        abstract TreeMap.Entry<K, V> subFloor(K key);

        abstract TreeMap.Entry<K, V> subLower(K key);

        /**
         * Returns ascending iterator from the perspective of this submap
         */
        abstract Iterator<K> keyIterator();

        abstract Spliterator<K> keySpliterator();

        /**
         * Returns descending iterator from the perspective of this submap
         */
        abstract Iterator<K> descendingKeyIterator();

        // public methods

        public boolean isEmpty() {
            return (fromStart && toEnd) ? m.isEmpty() : entrySet().isEmpty();
        }

        public int size() {
            return (fromStart && toEnd) ? m.size() : entrySet().size();
        }

        public final boolean containsKey(Object key) {
            return inRange(key) && m.containsKey(key);
        }

        public final V put(K key, V value) {
            if (!inRange(key))
                throw new IllegalArgumentException("key out of range");
            return m.put(key, value);
        }

        public final V get(Object key) {
            return !inRange(key) ? null : m.get(key);
        }

        public final V remove(Object key) {
            return !inRange(key) ? null : m.remove(key);
        }

        public final Map.Entry<K, V> ceilingEntry(K key) {
            return exportEntry(subCeiling(key));
        }

        public final K ceilingKey(K key) {
            return keyOrNull(subCeiling(key));
        }

        public final Map.Entry<K, V> higherEntry(K key) {
            return exportEntry(subHigher(key));
        }

        public final K higherKey(K key) {
            return keyOrNull(subHigher(key));
        }

        public final Map.Entry<K, V> floorEntry(K key) {
            return exportEntry(subFloor(key));
        }

        public final K floorKey(K key) {
            return keyOrNull(subFloor(key));
        }

        public final Map.Entry<K, V> lowerEntry(K key) {
            return exportEntry(subLower(key));
        }

        public final K lowerKey(K key) {
            return keyOrNull(subLower(key));
        }

        public final K firstKey() {
            return key(subLowest());
        }

        public final K lastKey() {
            return key(subHighest());
        }

        public final Map.Entry<K, V> firstEntry() {
            return exportEntry(subLowest());
        }

        public final Map.Entry<K, V> lastEntry() {
            return exportEntry(subHighest());
        }

        public final Map.Entry<K, V> pollFirstEntry() {
            TreeMap.Entry<K, V> e = subLowest();
            Map.Entry<K, V> result = exportEntry(e);
            if (e != null)
                m.deleteEntry(e);
            return result;
        }

        public final Map.Entry<K, V> pollLastEntry() {
            TreeMap.Entry<K, V> e = subHighest();
            Map.Entry<K, V> result = exportEntry(e);
            if (e != null)
                m.deleteEntry(e);
            return result;
        }

        // Views
        transient NavigableMap<K, V> descendingMapView;
        transient EntrySetView entrySetView;
        transient KeySet<K> navigableKeySetView;

        public final NavigableSet<K> navigableKeySet() {
            KeySet<K> nksv = navigableKeySetView;
            return (nksv != null) ? nksv :
                    (navigableKeySetView = new TreeMap.KeySet<>(this));
        }

        public final Set<K> keySet() {
            return navigableKeySet();
        }

        public NavigableSet<K> descendingKeySet() {
            return descendingMap().navigableKeySet();
        }

        public final SortedMap<K, V> subMap(K fromKey, K toKey) {
            return subMap(fromKey, true, toKey, false);
        }

        public final SortedMap<K, V> headMap(K toKey) {
            return headMap(toKey, false);
        }

        public final SortedMap<K, V> tailMap(K fromKey) {
            return tailMap(fromKey, true);
        }

        // View classes

        abstract class EntrySetView extends AbstractSet<Map.Entry<K, V>> {
            private transient int size = -1, sizeModCount;

            public int size() {
                if (fromStart && toEnd)
                    return m.size();
                if (size == -1 || sizeModCount != m.modCount) {
                    sizeModCount = m.modCount;
                    size = 0;
                    Iterator<?> i = iterator();
                    while (i.hasNext()) {
                        size++;
                        i.next();
                    }
                }
                return size;
            }

            public boolean isEmpty() {
                TreeMap.Entry<K, V> n = absLowest();
                return n == null || tooHigh(n.key);
            }

            public boolean contains(Object o) {
                if (!(o instanceof Map.Entry))
                    return false;
                Map.Entry<?, ?> entry = (Map.Entry<?, ?>) o;
                Object key = entry.getKey();
                if (!inRange(key))
                    return false;
                TreeMap.Entry<?, ?> node = m.getEntry(key);
                return node != null &&
                        valEquals(node.getValue(), entry.getValue());
            }

            public boolean remove(Object o) {
                if (!(o instanceof Map.Entry))
                    return false;
                Map.Entry<?, ?> entry = (Map.Entry<?, ?>) o;
                Object key = entry.getKey();
                if (!inRange(key))
                    return false;
                TreeMap.Entry<K, V> node = m.getEntry(key);
                if (node != null && valEquals(node.getValue(),
                        entry.getValue())) {
                    m.deleteEntry(node);
                    return true;
                }
                return false;
            }
        }

        /**
         * Iterators for SubMaps
         */
        abstract class SubMapIterator<T> implements Iterator<T> {
            TreeMap.Entry<K, V> lastReturned;
            TreeMap.Entry<K, V> next;
            final Object fenceKey;
            int expectedModCount;

            SubMapIterator(TreeMap.Entry<K, V> first,
                           TreeMap.Entry<K, V> fence) {
                expectedModCount = m.modCount;
                lastReturned = null;
                next = first;
                fenceKey = fence == null ? UNBOUNDED : fence.key;
            }

            public final boolean hasNext() {
                return next != null && next.key != fenceKey;
            }

            final TreeMap.Entry<K, V> nextEntry() {
                TreeMap.Entry<K, V> e = next;
                if (e == null || e.key == fenceKey)
                    throw new NoSuchElementException();
                if (m.modCount != expectedModCount)
                    throw new ConcurrentModificationException();
                next = successor(e);
                lastReturned = e;
                return e;
            }

            final TreeMap.Entry<K, V> prevEntry() {
                TreeMap.Entry<K, V> e = next;
                if (e == null || e.key == fenceKey)
                    throw new NoSuchElementException();
                if (m.modCount != expectedModCount)
                    throw new ConcurrentModificationException();
                next = predecessor(e);
                lastReturned = e;
                return e;
            }

            final void removeAscending() {
                if (lastReturned == null)
                    throw new IllegalStateException();
                if (m.modCount != expectedModCount)
                    throw new ConcurrentModificationException();
                // deleted entries are replaced by their successors
                if (lastReturned.left != null && lastReturned.right != null)
                    next = lastReturned;
                m.deleteEntry(lastReturned);
                lastReturned = null;
                expectedModCount = m.modCount;
            }

            final void removeDescending() {
                if (lastReturned == null)
                    throw new IllegalStateException();
                if (m.modCount != expectedModCount)
                    throw new ConcurrentModificationException();
                m.deleteEntry(lastReturned);
                lastReturned = null;
                expectedModCount = m.modCount;
            }

        }

        final class SubMapEntryIterator extends SubMapIterator<Map.Entry<K, V>> {
            SubMapEntryIterator(TreeMap.Entry<K, V> first,
                                TreeMap.Entry<K, V> fence) {
                super(first, fence);
            }

            public Map.Entry<K, V> next() {
                return nextEntry();
            }

            public void remove() {
                removeAscending();
            }
        }

        final class DescendingSubMapEntryIterator extends SubMapIterator<Map.Entry<K, V>> {
            DescendingSubMapEntryIterator(TreeMap.Entry<K, V> last,
                                          TreeMap.Entry<K, V> fence) {
                super(last, fence);
            }

            public Map.Entry<K, V> next() {
                return prevEntry();
            }

            public void remove() {
                removeDescending();
            }
        }

        // Implement minimal Spliterator as KeySpliterator backup
        final class SubMapKeyIterator extends SubMapIterator<K>
                implements Spliterator<K> {
            SubMapKeyIterator(TreeMap.Entry<K, V> first,
                              TreeMap.Entry<K, V> fence) {
                super(first, fence);
            }

            public K next() {
                return nextEntry().key;
            }

            public void remove() {
                removeAscending();
            }

            public Spliterator<K> trySplit() {
                return null;
            }

            public void forEachRemaining(Consumer<? super K> action) {
                while (hasNext())
                    action.accept(next());
            }

            public boolean tryAdvance(Consumer<? super K> action) {
                if (hasNext()) {
                    action.accept(next());
                    return true;
                }
                return false;
            }

            public long estimateSize() {
                return Long.MAX_VALUE;
            }

            public int characteristics() {
                return Spliterator.DISTINCT | Spliterator.ORDERED |
                        Spliterator.SORTED;
            }

            public final Comparator<? super K> getComparator() {
                return NavigableSubMap.this.comparator();
            }
        }

        final class DescendingSubMapKeyIterator extends SubMapIterator<K>
                implements Spliterator<K> {
            DescendingSubMapKeyIterator(TreeMap.Entry<K, V> last,
                                        TreeMap.Entry<K, V> fence) {
                super(last, fence);
            }

            public K next() {
                return prevEntry().key;
            }

            public void remove() {
                removeDescending();
            }

            public Spliterator<K> trySplit() {
                return null;
            }

            public void forEachRemaining(Consumer<? super K> action) {
                while (hasNext())
                    action.accept(next());
            }

            public boolean tryAdvance(Consumer<? super K> action) {
                if (hasNext()) {
                    action.accept(next());
                    return true;
                }
                return false;
            }

            public long estimateSize() {
                return Long.MAX_VALUE;
            }

            public int characteristics() {
                return Spliterator.DISTINCT | Spliterator.ORDERED;
            }
        }
    }

    /**
     * @serial include
     */
    static final class AscendingSubMap<K,V> extends NavigableSubMap<K,V> {
        private static final long serialVersionUID = 912986545866124060L;

        AscendingSubMap(TreeMap<K, V> m,
                        boolean fromStart, K lo, boolean loInclusive,
                        boolean toEnd, K hi, boolean hiInclusive) {
            super(m, fromStart, lo, loInclusive, toEnd, hi, hiInclusive);
        }

        public Comparator<? super K> comparator() {
            return m.comparator();
        }

        public NavigableMap<K, V> subMap(K fromKey, boolean fromInclusive,
                                         K toKey, boolean toInclusive) {
            if (!inRange(fromKey, fromInclusive))
                throw new IllegalArgumentException("fromKey out of range");
            if (!inRange(toKey, toInclusive))
                throw new IllegalArgumentException("toKey out of range");
            return new AscendingSubMap<>(m,
                    false, fromKey, fromInclusive,
                    false, toKey, toInclusive);
        }

        public NavigableMap<K, V> headMap(K toKey, boolean inclusive) {
            if (!inRange(toKey, inclusive))
                throw new IllegalArgumentException("toKey out of range");
            return new AscendingSubMap<>(m,
                    fromStart, lo, loInclusive,
                    false, toKey, inclusive);
        }

        public NavigableMap<K, V> tailMap(K fromKey, boolean inclusive) {
            if (!inRange(fromKey, inclusive))
                throw new IllegalArgumentException("fromKey out of range");
            return new AscendingSubMap<>(m,
                    false, fromKey, inclusive,
                    toEnd, hi, hiInclusive);
        }

        public NavigableMap<K, V> descendingMap() {
            NavigableMap<K, V> mv = descendingMapView;
            return (mv != null) ? mv :
                    (descendingMapView =
                            new DescendingSubMap<>(m,
                                    fromStart, lo, loInclusive,
                                    toEnd, hi, hiInclusive));
        }

        Iterator<K> keyIterator() {
            return new SubMapKeyIterator(absLowest(), absHighFence());
        }

        Spliterator<K> keySpliterator() {
            return new SubMapKeyIterator(absLowest(), absHighFence());
        }

        Iterator<K> descendingKeyIterator() {
            return new DescendingSubMapKeyIterator(absHighest(), absLowFence());
        }

        final class AscendingEntrySetView extends EntrySetView {
            public Iterator<Map.Entry<K, V>> iterator() {
                return new SubMapEntryIterator(absLowest(), absHighFence());
            }
        }

        public Set<Map.Entry<K, V>> entrySet() {
            EntrySetView es = entrySetView;
            return (es != null) ? es : (entrySetView = new AscendingEntrySetView());
        }

        TreeMap.Entry<K, V> subLowest() {
            return absLowest();
        }

        TreeMap.Entry<K, V> subHighest() {
            return absHighest();
        }

        TreeMap.Entry<K, V> subCeiling(K key) {
            return absCeiling(key);
        }

        TreeMap.Entry<K, V> subHigher(K key) {
            return absHigher(key);
        }

        TreeMap.Entry<K, V> subFloor(K key) {
            return absFloor(key);
        }

        TreeMap.Entry<K, V> subLower(K key) {
            return absLower(key);
        }
    }

    /**
     * @serial include
     */
    static final class DescendingSubMap<K,V>  extends NavigableSubMap<K,V> {
        private static final long serialVersionUID = 912986545866120460L;

        DescendingSubMap(TreeMap<K, V> m,
                         boolean fromStart, K lo, boolean loInclusive,
                         boolean toEnd, K hi, boolean hiInclusive) {
            super(m, fromStart, lo, loInclusive, toEnd, hi, hiInclusive);
        }

        private final Comparator<? super K> reverseComparator =
                Collections.reverseOrder(m.comparator);

        public Comparator<? super K> comparator() {
            return reverseComparator;
        }

        public NavigableMap<K, V> subMap(K fromKey, boolean fromInclusive,
                                         K toKey, boolean toInclusive) {
            if (!inRange(fromKey, fromInclusive))
                throw new IllegalArgumentException("fromKey out of range");
            if (!inRange(toKey, toInclusive))
                throw new IllegalArgumentException("toKey out of range");
            return new DescendingSubMap<>(m,
                    false, toKey, toInclusive,
                    false, fromKey, fromInclusive);
        }

        public NavigableMap<K, V> headMap(K toKey, boolean inclusive) {
            if (!inRange(toKey, inclusive))
                throw new IllegalArgumentException("toKey out of range");
            return new DescendingSubMap<>(m,
                    false, toKey, inclusive,
                    toEnd, hi, hiInclusive);
        }

        public NavigableMap<K, V> tailMap(K fromKey, boolean inclusive) {
            if (!inRange(fromKey, inclusive))
                throw new IllegalArgumentException("fromKey out of range");
            return new DescendingSubMap<>(m,
                    fromStart, lo, loInclusive,
                    false, fromKey, inclusive);
        }

        public NavigableMap<K, V> descendingMap() {
            NavigableMap<K, V> mv = descendingMapView;
            return (mv != null) ? mv :
                    (descendingMapView =
                            new AscendingSubMap<>(m,
                                    fromStart, lo, loInclusive,
                                    toEnd, hi, hiInclusive));
        }

        Iterator<K> keyIterator() {
            return new DescendingSubMapKeyIterator(absHighest(), absLowFence());
        }

        Spliterator<K> keySpliterator() {
            return new DescendingSubMapKeyIterator(absHighest(), absLowFence());
        }

        Iterator<K> descendingKeyIterator() {
            return new SubMapKeyIterator(absLowest(), absHighFence());
        }

        final class DescendingEntrySetView extends EntrySetView {
            public Iterator<Map.Entry<K, V>> iterator() {
                return new DescendingSubMapEntryIterator(absHighest(), absLowFence());
            }
        }

        public Set<Map.Entry<K, V>> entrySet() {
            EntrySetView es = entrySetView;
            return (es != null) ? es : (entrySetView = new DescendingEntrySetView());
        }

        TreeMap.Entry<K, V> subLowest() {
            return absHighest();
        }

        TreeMap.Entry<K, V> subHighest() {
            return absLowest();
        }

        TreeMap.Entry<K, V> subCeiling(K key) {
            return absFloor(key);
        }

        TreeMap.Entry<K, V> subHigher(K key) {
            return absLower(key);
        }

        TreeMap.Entry<K, V> subFloor(K key) {
            return absCeiling(key);
        }

        TreeMap.Entry<K, V> subLower(K key) {
            return absHigher(key);
        }
    }

    /**
     * This class exists solely for the sake of serialization
     * compatibility with previous releases of TreeMap that did not
     * support NavigableMap.  It translates an old-version SubMap into
     * a new-version AscendingSubMap. This class is never otherwise
     * used.
     *
     * @serial include
     */
    private class SubMap extends AbstractMap<K,V>
        implements SortedMap<K,V>, java.io.Serializable {
        private static final long serialVersionUID = -6520786458950516097L;
        private boolean fromStart = false, toEnd = false;
        private K fromKey, toKey;
        private Object readResolve() {
            return new AscendingSubMap<>(TreeMap.this,
                    fromStart, fromKey, true,
                    toEnd, toKey, false);
        }

        public Set<Map.Entry<K, V>> entrySet() {
            throw new InternalError();
        }

        public K lastKey() {
            throw new InternalError();
        }

        public K firstKey() {
            throw new InternalError();
        }

        public SortedMap<K, V> subMap(K fromKey, K toKey) {
            throw new InternalError();
        }

        public SortedMap<K, V> headMap(K toKey) {
            throw new InternalError();
        }

        public SortedMap<K, V> tailMap(K fromKey) {
            throw new InternalError();
        }

        public Comparator<? super K> comparator() {
            throw new InternalError();
        }
    }


    // Red-black mechanics

    private static final boolean RED = false;
    private static final boolean BLACK = true;

    /**
     * 红黑树中的节点
     * <p>
     * Node in the Tree.  Doubles as a means to pass key-value pairs back to
     * user (see Map.Entry).
     */

    static final class Entry<K, V> implements Map.Entry<K, V> {
        /**
         * key
         */
        K key;
        /**
         * value
         */
        V value;
        /**
         * 左孩子
         */
        Entry<K, V> left;
        /**
         * 右孩子
         */
        Entry<K, V> right;
        /**
         * 父节点
         */
        Entry<K, V> parent;
        /**
         * 颜色
         */
        boolean color = BLACK;

        /**
         * 默认加进去的节点为黑色
         * <p>
         * Make a new cell with given key, value, and parent, and with
         * {@code null} child links, and BLACK color.
         */
        Entry(K key, V value, Entry<K, V> parent) {
            this.key = key;
            this.value = value;
            this.parent = parent;
        }

        /**
         * Returns the key.
         *
         * @return the key
         */
        public K getKey() {
            return key;
        }

        /**
         * Returns the value associated with the key.
         *
         * @return the value associated with the key
         */
        public V getValue() {
            return value;
        }

        /**
         * Replaces the value currently associated with the key with the given
         * value.
         *
         * @return the value associated with the key before this method was
         * called
         */
        public V setValue(V value) {
            V oldValue = this.value;
            this.value = value;
            return oldValue;
        }

        /**
         * Entry 比较
         *
         * @param o
         * @return
         */
        public boolean equals(Object o) {
            if (!(o instanceof Map.Entry))
                return false;
            Map.Entry<?, ?> e = (Map.Entry<?, ?>) o;
            // 比较 key 的值以及 value 的值
            return valEquals(key, e.getKey()) && valEquals(value, e.getValue());
        }

        public int hashCode() {
            int keyHash = (key == null ? 0 : key.hashCode());
            int valueHash = (value == null ? 0 : value.hashCode());
            return keyHash ^ valueHash;
        }

        public String toString() {
            return key + "=" + value;
        }
    }

    /**
     * 返回第一个 Entry
     * 返回红黑树的最左节点
     * 按照 key 的排序顺序
     * <p>
     * Returns the first Entry in the TreeMap (according to the TreeMap's
     * key-sort function).  Returns null if the TreeMap is empty.
     */
    final Entry<K, V> getFirstEntry() {
        // 根节点
        Entry<K, V> p = root;
        // 根节点非空
        if (p != null)
            // 寻找最左节点
            while (p.left != null)
                p = p.left;
        return p;
    }

    /**
     * 遍历树，从根节点出发，找到最右孩子节点
     * <p>
     * Returns the last Entry in the TreeMap (according to the TreeMap's
     * key-sort function).  Returns null if the TreeMap is empty.
     */
    final Entry<K, V> getLastEntry() {
        Entry<K, V> p = root;
        if (p != null)
            while (p.right != null)
                p = p.right;
        return p;
    }

    /**
     * 返回指定节点的后继节点
     * 右子树中的最小节点
     * 按照 key 的排序规则
     * 中序遍历的思想
     * <p>
     * Returns the successor of the specified Entry, or null if no such.
     */
    static <K, V> TreeMap.Entry<K, V> successor(Entry<K, V> t) {
        // 如果当前节点为 null，直接返回
        if (t == null)
            return null;
            // 判断是否有右孩子节点
        else if (t.right != null) {
            // 指向右孩子节点
            Entry<K, V> p = t.right;
            // 在右孩子基础上找到其最左孩子节点
            while (p.left != null)
                p = p.left;
            return p;
            // 没有右孩子
            // 若当前节点是父节点的左孩子，则直接返回当前节点
            // 若当前节点是父节点的右孩子，则需要一直往上回退，找到一个左孩子的父节点，返回那个父节点。
        } else {
            // 记录 parent 节点
            Entry<K, V> p = t.parent;
            // 保存当前节点
            Entry<K, V> ch = t;
            // 父节点非空 且 当前节点是父节点的右孩子
            // 说明父节点已经访问过了，继续往上找。
            while (p != null && ch == p.right) {
                ch = p;
                p = p.parent;
            }
            // 返回 父节点
            return p;
        }
    }

    /**
     * Returns the predecessor of the specified Entry, or null if no such.
     */
    static <K, V> Entry<K, V> predecessor(Entry<K, V> t) {
        if (t == null)
            return null;
        else if (t.left != null) {
            Entry<K, V> p = t.left;
            while (p.right != null)
                p = p.right;
            return p;
        } else {
            Entry<K, V> p = t.parent;
            Entry<K, V> ch = t;
            while (p != null && ch == p.left) {
                ch = p;
                p = p.parent;
            }
            return p;
        }
    }

    /**
     * Balancing operations.
     * <p>
     * Implementations of rebalancings during insertion and deletion are
     * slightly different than the CLR version.  Rather than using dummy
     * nilnodes, we use a set of accessors that deal properly with null.  They
     * are used to avoid messiness surrounding nullness checks in the main
     * algorithms.
     */

    private static <K, V> boolean colorOf(Entry<K, V> p) {
        return (p == null ? BLACK : p.color);
    }

    /**
     * 获取节点 p 的父节点
     *
     * @param p
     * @param <K>
     * @param <V>
     * @return
     */
    private static <K, V> Entry<K, V> parentOf(Entry<K, V> p) {
        return (p == null ? null : p.parent);
    }

    /**
     * 设置当前节点的颜色
     *
     * @param p
     * @param c
     * @param <K>
     * @param <V>
     */
    private static <K, V> void setColor(Entry<K, V> p, boolean c) {
        if (p != null)
            p.color = c;
    }

    /**
     * 获取当前节点的左孩子节点
     *
     * @param p
     * @param <K>
     * @param <V>
     * @return
     */
    private static <K, V> Entry<K, V> leftOf(Entry<K, V> p) {
        return (p == null) ? null : p.left;
    }

    /**
     * 获取当前节点的右孩子节点
     *
     * @param p
     * @param <K>
     * @param <V>
     * @return
     */
    private static <K, V> Entry<K, V> rightOf(Entry<K, V> p) {
        return (p == null) ? null : p.right;
    }

    /**
     * 左旋/右旋
     * 1. 保存当前节点的子节点
     * 2. 移动子节点的子节点
     * 3. 更新子节点的父节点为当前节点的父节点
     * 3. 判断当前节点是否为根节点 或者 判断当前节点是父节点的左孩子节点/右孩子节点
     * 4. 更新子节点的左/右孩子 节点为当前节点
     * 5. 更新当前节点的父节点为子节点
     */

    /**
     * 基于节点 p 左旋
     * <p>
     * From CLR
     *
     * @param p
     */
    private void rotateLeft(Entry<K, V> p) {
        // 当前节点 p 非空
        if (p != null) {
            // 保存当前节点 p 的右孩子节点
            Entry<K, V> r = p.right;
            // 将当前节点 p 的右孩子指针指向其原来右孩子的左孩子节点
            p.right = r.left;
            // 如果 p 的新的右孩子不为空
            if (r.left != null)
                // 更新右孩子的 parent 为 p
                r.left.parent = p;
            // 更新原来 p 的右孩子的父节点为 p 原来的父节点
            r.parent = p.parent;
            // 如果 p 原来的父节点为 null
            if (p.parent == null)
                // 说明原来的 p 是 root
                // 把原来 p 的右孩子节点置为根节点
                root = r;
                // 如果 p 之前是左孩子节点
            else if (p.parent.left == p)
                // 将 p 原来 parent 节点的左孩子 指针指向 r
                p.parent.left = r;
                // 如果 p 之前是右孩子节点
            else
                p.parent.right = r;
            // r 的左孩子指针指向 p
            r.left = p;
            // 更新 p 的父节点为 r
            p.parent = r;
        }
    }

    /**
     * 基于节点 p 右旋
     * From CLR
     *
     * @param p
     */
    private void rotateRight(Entry<K, V> p) {
        // 若果当前节点不为空
        if (p != null) {
            // 记录当前节点的左孩子节点
            Entry<K, V> l = p.left;
            // 当前 p 节点的左孩子指针指向 原来左孩子的右孩子节点
            p.left = l.right;
            // 如果节点 p 左孩子节点的右孩子节点不为空，则更新它的父指针
            if (l.right != null) l.right.parent = p;
            // 更新左孩子节点的新的 parent 指针
            l.parent = p.parent;
            // 若为空，则为根节点
            if (p.parent == null)
                root = l;
                // 判断原来的 p 节点是左孩子节点还是右孩子节点
            else if (p.parent.right == p)
                p.parent.right = l;
            else p.parent.left = l;
            l.right = p;
            p.parent = l;
        }
    }

    /**
     * ！！！推荐给出一个插入的列表画一下！！！
     * <p>
     * 调整树
     * 调整分为以下三种情况：
     * 1. 若为根节点，则直接把颜色改成黑色即可。
     * 2. 若插入节点的父节点为黑色，不需要平衡，直接插入即可。
     * 3. 情况比较复杂，如果插入元素的父节点为红色，需要平衡，因为这样破坏了黑色节点的子节点必须为黑色节点这条原则。
     * <p>
     * 情况A：当前父节点是祖父节点的左节点
     * ------------------------------------------------------------------------------
     * |                                |   （1）将父节点设为黑色；（2）将叔叔节点设为黑色；
     * |  A1)父节点为红色, 叔叔节点也为红色 |   （3）将祖父节点设为红色；
     * |                                |   （4）将祖父节点设为新的当前节点，进入下一次循环判断；
     * |--------------------------------|---------------------------------------
     * |                                |
     * |  A2)父节点为红色, 但叔叔节点为黑色 |   （1）将父节点作为新的当前节点；
     * |  且当前节点是其父节点的右节点      |   （2）以新当节点为支点进行左旋，进入情况 3)
     * |                                |
     * |--------------------------------|--------------------------------------
     * |                                |   （1）将父节点设为黑色；
     * |  A3)父节点为红色, 但叔叔节点为黑色 |   （2）将祖父节点设为红色；
     * |  且当前节点是其父节点的左节点      |   （3）以祖父节点为支点进行右旋，进入下一次循环判断；
     * |                                |
     * |-----------------------------------------------------------------------
     * <p>
     * 情况B：当前父节点是祖父节点的右节点
     * ------------------------------------------------------------------------------
     * |                                |   （1）将父节点设为黑色；（2）将叔叔节点设为黑色；
     * |  B1)父节点为红色, 叔叔节点也为红色 |   （3）将祖父节点设为红色；
     * |                                |   （4）将祖父节点设为新的当前节点，进入下一次循环判断；
     * |--------------------------------|---------------------------------------
     * |                                |
     * |  B2)父节点为红色, 但叔叔节点为黑色 |   （1）将父节点作为新的当前节点；
     * |  且当前节点是其父节点的左节点      |   （2）以新当节点为支点进行右旋，进入情况 3)
     * |                                |
     * |--------------------------------|--------------------------------------
     * |                                |   （1）将父节点设为黑色；
     * |  B3)父节点为红色, 但叔叔节点为黑色 |   （2）将祖父节点设为红色；
     * |  且当前节点是其父节点的右节点      |   （3）以祖父节点为支点进行左旋，进入下一次循环判断；
     * |                                |
     * |-----------------------------------------------------------------------
     *
     * <p>
     * From CLR
     */
    private void fixAfterInsertion(Entry<K, V> x) {
        // 默认插入的节点为红色
        x.color = RED;
        // 当前节点不为空 && 当前节点不是根节点 && 当前节点的父节点是 红色节点
        while (x != null && x != root && x.parent.color == RED) {
            // 如果父节点是祖父节点的左节点
            if (parentOf(x) == leftOf(parentOf(parentOf(x)))) {
                // 当前节点的叔叔节点
                Entry<K, V> y = rightOf(parentOf(parentOf(x)));
                // 如果叔叔节点是红色的
                // 对应上表的情况 A1)
                if (colorOf(y) == RED) {
                    // 父节点设为黑色
                    setColor(parentOf(x), BLACK);
                    // 叔叔节点设为黑色
                    setColor(y, BLACK);
                    // 祖父节点设为 红色
                    setColor(parentOf(parentOf(x)), RED);
                    // 将当前节点设置为祖父节点
                    x = parentOf(parentOf(x));
                    // 如果叔叔节点是黑色的
                    // 对应上面的情况 A2)
                } else {
                    // 如果当前节点是父节点的右节点
                    if (x == rightOf(parentOf(x))) {
                        // 将当前节点设为父节点
                        x = parentOf(x);
                        // 以新的当前节点进行左旋
                        rotateLeft(x);
                    }
                    // 情况 A3 如果当前节点为其父节点的左节点（如果是情况A2）则左旋之后新当前节点正好为其父节点的左节点了）
                    // 将父节点设为黑色
                    setColor(parentOf(x), BLACK);
                    // 将祖父节点设为红色
                    setColor(parentOf(parentOf(x)), RED);
                    // 以祖父节点进行右旋
                    rotateRight(parentOf(parentOf(x)));
                }
                // 父节点是祖父节点右节点
            } else {
                // 当前节点的叔叔节点
                Entry<K, V> y = leftOf(parentOf(parentOf(x)));
                // 如果叔叔节点是红色的
                // 对应情况 B1
                if (colorOf(y) == RED) {
                    // 设置父节点为黑色
                    setColor(parentOf(x), BLACK);
                    // 设置叔叔节点为黑色
                    setColor(y, BLACK);
                    // 设置祖父节点为红色
                    setColor(parentOf(parentOf(x)), RED);
                    // 将当前节点设为祖父节点
                    x = parentOf(parentOf(x));
                    // 如果叔叔节点是黑色的
                } else {
                    // 对应情况 B2
                    // 当前节点是父节点的左节点
                    if (x == leftOf(parentOf(x))) {
                        // 将当前节点指向父节点
                        x = parentOf(x);
                        // 以新的当前节点进行右旋
                        rotateRight(x);
                    }
                    // 对应情况 B3
                    // 即当前节点是父节点的右节点，或者经过B2之后，旋转之后当前节点也是父节点的右节点
                    //设置父节点的颜色为黑色
                    setColor(parentOf(x), BLACK);
                    // 设置祖父节点的颜色为红色
                    setColor(parentOf(parentOf(x)), RED);
                    // 以祖父节点为基准，进行左旋
                    rotateLeft(parentOf(parentOf(x)));
                }
            }
        }
        // root 节点的颜色为黑色
        root.color = BLACK;
    }

    /**
     * 删除节点 p 之后平衡树
     * 1. 删除的节点如果有两个孩子节点，则从其右孩子中取出最小的元素放到被删除的位置，然后把删除位置移到右孩子最小元素的位置（指针指向那个节点），然后进行 2.
     * 2. 如果删除的位置只有一个子节点，可能是经过 1. 之后转化的，也可能是本来就只有一个子节点。
     * 则把子节点作为替换元素，放到当前删除的位置，也就是取代当前节点（其实还没取代，只是要替换的节点的父指针也指向了要删除节点的父节点而已）
     * 3. 如果要删除的节点没有子节点（可能是一开始就没有，或者转化之后没有），则直接删除该位置上的节点。
     * 4. 如果删除的节点是黑色的，则需要平衡树
     * 5. 如果有替换元素，则以替换元素作为当前节点进入平衡 树的操作
     * 6. 如果没有替代元素，则以删除的位置的元素作为当前节点进入再平衡，平衡之后再删除这个节点。
     *
     * <p>
     * Delete node p, and then rebalance the tree.
     */
    private void deleteEntry(Entry<K, V> p) {
        // 计数
        modCount++;
        size--;

        // If strictly internal, copy successor's element to p and then make p
        // point to successor.
        // 如果删除节点的左右节点都不为空
        // 对应上述 1.
        if (p.left != null && p.right != null) {
            // 保存 p 的后继节点，即右子树中的最小节点
            Entry<K, V> s = successor(p);
            // 把 key 和 value 赋值 给 p
            p.key = s.key;
            p.value = s.value;
            // 把 p 的后继节点赋值给 p
            // p 指针指向 原来 p 节点的最小右节点
            p = s;
        } // p has 2 children
        // 若当前节点本就没有两个子节点 或者 经过上面 if 之后当前节点最多只有一个右孩子节点了。
        // 也即当前节点最多只有一个节点
        // Start fixup at replacement node, if it exists.
        // 如果当前节点有子节点，就用 replacement 保存当前节点的子节点（可能是左节点也有可能是右节点）
        Entry<K, V> replacement = (p.left != null ? p.left : p.right);

        // 若存在子节点
        // 对应上述 2.
        if (replacement != null) {
            // Link replacement to parent
            // 把替换节点直接放到当前节点的位置上（相当于删除了 p 节点，并把替换节点移动过来了）
            replacement.parent = p.parent;
            // 如果 p.parent 节点是空
            if (p.parent == null)
                // 则 replacement 即为 root 节点
                root = replacement;
            else if (p == p.parent.left)
                // replacement 成为左节点
                p.parent.left = replacement;
            else
                // replacement 成为右节点
                p.parent.right = replacement;

            // Null out links so they are OK to use by fixAfterDeletion.
            // 将引用 p 的指针属性都置为空
            // 即断开与树的关系
            p.left = p.right = p.parent = null;

            // Fix replacement
            // 如果 p 是黑色节点，p 是已经删除的节点
            if (p.color == BLACK)
                // 需要进行平衡
                // 对应上述 5.
                fixAfterDeletion(replacement);
            // 不存在子节点，判断当前节点是否为 root 节点
            // 对应上述 3.
        } else if (p.parent == null) { // return if we are the only node.
            // 如果是 root 节点，说明该 Tree 只有一个根节点，只要删除 root 节点。
            root = null;
            // 没有子节点，也不是根节点
            // 对应上述 3.
        } else { //  No children. Use self as phantom replacement and unlink.
            // 如果当前节点是黑色节点
            // 对应上述 6.
            if (p.color == BLACK)
                // 需要平衡
                fixAfterDeletion(p);
            // 平衡之后删除当前节点
            if (p.parent != null) {
                // 当前节点是父节点的左节点，则将当前节点的父节点的左节点置为空
                if (p == p.parent.left)
                    p.parent.left = null;
                    // 当前节点是父节点的右节点，则将当前节点的父节点的右节点置为空
                else if (p == p.parent.right)
                    p.parent.right = null;
                // 将当前节点断开
                p.parent = null;
            }
        }
    }

    /**
     * 删除元素之后的再平衡
     * 因为删除的节点是黑色的，删除之后树就变得不再平衡了
     * <p>
     * 情况A：当前节点是父节点的左节点
     * ------------------------------------------------------------------------------
     * |                                |   （1）将兄弟节点设为黑色；（2）将父节点设为红色；
     * |  A1)当前节点是黑 + 黑节点         |   （3）以父节点为支点进行左旋；
     * |    兄弟节点是红节点              |   （4）重新设置x的兄弟节点，进入下一步；
     * |--------------------------------|---------------------------------------
     * |                                |
     * |  A2)x是黑+黑节点，x的兄弟是黑节点  |   （1）将兄弟节点设置为红色；
     * |  且兄弟节点的两个子节点都是黑色    |   （2）将x的父节点作为新的当前节点，进入下一次循环；
     * |                                |
     * |--------------------------------|--------------------------------------
     * |                                |   （1）将兄弟节点的左子节点设为黑色；
     * |  A3)x是黑+黑节点，x的兄弟是黑节点  |   （2）将兄弟节点设为红色；
     * |   且兄弟节点的右子节点为黑色       |   （3）以兄弟节点为支点进行右旋；
     * |    左子节点为红色                |   （4）重新设置x的兄弟节点，进入下一步；
     * |--------------------------------|---------------------------------------
     * |                                |   （1）将兄弟节点的颜色设为父节点的颜色；
     * |  A4)x是黑+黑节点，x的兄弟是黑节点，|   （2）将父节点设为黑色；
     * |   且兄弟节点的右子节点为红色，     |   （3）将兄弟节点的右子节点设为黑色；
     * |   左子节点任意颜色               |   （4）以父节点为支点进行左旋；
     * |                                |   （5）将root作为新的当前节点（退出循环）；
     * |-----------------------------------------------------------------------
     * |
     * <p>
     * 情况B：当前节点是父节点的右节点
     * ------------------------------------------------------------------------------
     * |                                |   （1）将兄弟节点设为黑色；（2）将父节点设为红色；
     * |  B1)当前节点是黑 + 黑节点         |   （3）以父节点为支点进行右旋；
     * |    兄弟节点是红节点              |   （4）重新设置x的兄弟节点，进入下一步；
     * |--------------------------------|---------------------------------------
     * |                                |
     * |  B2)x是黑+黑节点，x的兄弟是黑节点  |   （1）将兄弟节点设置为红色；
     * |  且兄弟节点的两个子节点都是黑色    |   （2）将x的父节点作为新的当前节点，进入下一次循环；
     * |                                |
     * |--------------------------------|--------------------------------------
     * |                                |   （1）将兄弟节点的右子节点设为黑色；
     * |  B3)x是黑+黑节点，x的兄弟是黑节点  |   （2）将兄弟节点设为红色；
     * |   且兄弟节点的左子节点为黑色       |   （3）以兄弟节点为支点进行左旋；
     * |    右子节点为红色                |   （4）重新设置x的兄弟节点，进入下一步；
     * |--------------------------------|---------------------------------------
     * |                                |   （1）将兄弟节点的颜色设为父节点的颜色；
     * |  B4)x是黑+黑节点，x的兄弟是黑节点，|   （2）将父节点设为黑色；
     * |   且兄弟节点的左子节点为红色，     |   （3）将兄弟节点的左子节点设为黑色；
     * |   右子节点任意颜色               |   （4）以父节点为支点进行右旋；
     * |                                |   （5）将root作为新的当前节点（退出循环）；
     * |-----------------------------------------------------------------------
     * |
     * <p>
     * From CLR
     */
    private void fixAfterDeletion(Entry<K, V> x) {
        // 如果当前节点不是根节点且当前节点是黑色
        while (x != root && colorOf(x) == BLACK) {
            // 如果当前节点是父节点的左节点
            if (x == leftOf(parentOf(x))) {
                // 获取当前节点的兄弟节点
                Entry<K, V> sib = rightOf(parentOf(x));
                // 如果当前节点的兄弟节点是 红色
                if (colorOf(sib) == RED) {
                    // 将兄弟节点设置为黑色
                    setColor(sib, BLACK);
                    // 将父节点设置为红色
                    setColor(parentOf(x), RED);
                    // 左旋
                    rotateLeft(parentOf(x));
                    // 更新兄弟节点指针，指向原来父节点的右孩子的左孩子节点
                    // 该兄弟节点可能为 null
                    sib = rightOf(parentOf(x));
                }
                // 当前兄弟节点的左节点颜色为黑 且 右孩子节点颜色为黑
                if (colorOf(leftOf(sib)) == BLACK &&
                        colorOf(rightOf(sib)) == BLACK) {
                    // 设置兄弟节点的颜色为红
                    setColor(sib, RED);
                    // x 指向其父节点
                    x = parentOf(x);
                    // 兄弟节点的子节点颜色不全为黑（不存在孩子，或者存在颜色为红）
                } else {
                    // 如果右孩子节点颜色为黑，意味着左孩子节点不存在或者左孩子节点颜色为红
                    if (colorOf(rightOf(sib)) == BLACK) {
                        // 将左孩子节点颜色描黑
                        setColor(leftOf(sib), BLACK);
                        // 兄弟节点颜色描红
                        setColor(sib, RED);
                        // 右旋
                        rotateRight(sib);
                        // 兄弟孩子节点指针指向 x 父节点的右孩子节点
                        sib = rightOf(parentOf(x));
                    }
                    // 设置兄弟节点颜色为 x 父节点的颜色
                    setColor(sib, colorOf(parentOf(x)));
                    // 设置 x 父节点的颜色为黑
                    setColor(parentOf(x), BLACK);
                    // 设置 兄弟节点右孩子的颜色为黑
                    setColor(rightOf(sib), BLACK);
                    // 左旋
                    rotateLeft(parentOf(x));
                    // 将root作为新的当前节点（退出循环）
                    x = root;
                }
                // 如果当前节点是父节点的右节点
            } else { // symmetric
                Entry<K, V> sib = leftOf(parentOf(x));

                if (colorOf(sib) == RED) {
                    setColor(sib, BLACK);
                    setColor(parentOf(x), RED);
                    rotateRight(parentOf(x));
                    sib = leftOf(parentOf(x));
                }

                if (colorOf(rightOf(sib)) == BLACK &&
                        colorOf(leftOf(sib)) == BLACK) {
                    setColor(sib, RED);
                    x = parentOf(x);
                } else {
                    if (colorOf(leftOf(sib)) == BLACK) {
                        setColor(rightOf(sib), BLACK);
                        setColor(sib, RED);
                        rotateLeft(sib);
                        sib = leftOf(parentOf(x));
                    }
                    setColor(sib, colorOf(parentOf(x)));
                    setColor(parentOf(x), BLACK);
                    setColor(leftOf(sib), BLACK);
                    rotateRight(parentOf(x));
                    x = root;
                }
            }
        }
        // 当前节点标黑
        setColor(x, BLACK);
    }

    private static final long serialVersionUID = 919286545866124006L;

    /**
     * Save the state of the {@code TreeMap} instance to a stream (i.e.,
     * serialize it).
     *
     * @serialData The <em>size</em> of the TreeMap (the number of key-value
     * mappings) is emitted (int), followed by the key (Object)
     * and value (Object) for each key-value mapping represented
     * by the TreeMap. The key-value mappings are emitted in
     * key-order (as determined by the TreeMap's Comparator,
     * or by the keys' natural ordering if the TreeMap has no
     * Comparator).
     */
    private void writeObject(java.io.ObjectOutputStream s)
            throws java.io.IOException {
        // Write out the Comparator and any hidden stuff
        s.defaultWriteObject();

        // Write out size (number of Mappings)
        s.writeInt(size);

        // Write out keys and values (alternating)
        for (Map.Entry<K, V> e : entrySet()) {
            s.writeObject(e.getKey());
            s.writeObject(e.getValue());
        }
    }

    /**
     * Reconstitute the {@code TreeMap} instance from a stream (i.e.,
     * deserialize it).
     */
    private void readObject(final java.io.ObjectInputStream s)
            throws java.io.IOException, ClassNotFoundException {
        // Read in the Comparator and any hidden stuff
        s.defaultReadObject();

        // Read in size
        int size = s.readInt();

        buildFromSorted(size, null, s, null);
    }

    /**
     * Intended to be called only from TreeSet.readObject
     */
    void readTreeSet(int size, java.io.ObjectInputStream s, V defaultVal)
            throws java.io.IOException, ClassNotFoundException {
        buildFromSorted(size, null, s, defaultVal);
    }

    /**
     * Intended to be called only from TreeSet.addAll
     */
    void addAllForTreeSet(SortedSet<? extends K> set, V defaultVal) {
        try {
            buildFromSorted(set.size(), set.iterator(), null, defaultVal);
        } catch (java.io.IOException | ClassNotFoundException cannotHappen) {
        }
    }


    /**
     * Linear time tree building algorithm from sorted data.  Can accept keys
     * and/or values from iterator or stream. This leads to too many
     * parameters, but seems better than alternatives.  The four formats
     * that this method accepts are:
     * <p>
     * 1) An iterator of Map.Entries.  (it != null, defaultVal == null).
     * 2) An iterator of keys.         (it != null, defaultVal != null).
     * 3) A stream of alternating serialized keys and values.
     * (it == null, defaultVal == null).
     * 4) A stream of serialized keys. (it == null, defaultVal != null).
     * <p>
     * It is assumed that the comparator of the TreeMap is already set prior
     * to calling this method.
     *
     * @param size       the number of keys (or key-value pairs) to be read from
     *                   the iterator or stream
     * @param it         If non-null, new entries are created from entries
     *                   or keys read from this iterator.
     * @param str        If non-null, new entries are created from keys and
     *                   possibly values read from this stream in serialized form.
     *                   Exactly one of it and str should be non-null.
     * @param defaultVal if non-null, this default value is used for
     *                   each value in the map.  If null, each value is read from
     *                   iterator or stream, as described above.
     * @throws java.io.IOException    propagated from stream reads. This cannot
     *                                occur if str is null.
     * @throws ClassNotFoundException propagated from readObject.
     *                                This cannot occur if str is null.
     */
    private void buildFromSorted(int size, Iterator<?> it,
                                 java.io.ObjectInputStream str,
                                 V defaultVal)
            throws java.io.IOException, ClassNotFoundException {
        this.size = size;
        root = buildFromSorted(0, 0, size - 1, computeRedLevel(size),
                it, str, defaultVal);
    }

    /**
     * Recursive "helper method" that does the real work of the
     * previous method.  Identically named parameters have
     * identical definitions.  Additional parameters are documented below.
     * It is assumed that the comparator and size fields of the TreeMap are
     * already set prior to calling this method.  (It ignores both fields.)
     *
     * @param level    the current level of tree. Initial call should be 0.
     * @param lo       the first element index of this subtree. Initial should be 0.
     * @param hi       the last element index of this subtree.  Initial should be
     *                 size-1.
     * @param redLevel the level at which nodes should be red.
     *                 Must be equal to computeRedLevel for tree of this size.
     */
    @SuppressWarnings("unchecked")
    private final Entry<K, V> buildFromSorted(int level, int lo, int hi,
                                              int redLevel,
                                              Iterator<?> it,
                                              java.io.ObjectInputStream str,
                                              V defaultVal)
            throws java.io.IOException, ClassNotFoundException {
        /*
         * Strategy: The root is the middlemost element. To get to it, we
         * have to first recursively construct the entire left subtree,
         * so as to grab all of its elements. We can then proceed with right
         * subtree.
         *
         * The lo and hi arguments are the minimum and maximum
         * indices to pull out of the iterator or stream for current subtree.
         * They are not actually indexed, we just proceed sequentially,
         * ensuring that items are extracted in corresponding order.
         */

        if (hi < lo) return null;

        int mid = (lo + hi) >>> 1;

        Entry<K, V> left = null;
        if (lo < mid)
            left = buildFromSorted(level + 1, lo, mid - 1, redLevel,
                    it, str, defaultVal);

        // extract key and/or value from iterator or stream
        K key;
        V value;
        if (it != null) {
            if (defaultVal == null) {
                Map.Entry<?, ?> entry = (Map.Entry<?, ?>) it.next();
                key = (K) entry.getKey();
                value = (V) entry.getValue();
            } else {
                key = (K) it.next();
                value = defaultVal;
            }
        } else { // use stream
            key = (K) str.readObject();
            value = (defaultVal != null ? defaultVal : (V) str.readObject());
        }

        Entry<K, V> middle = new Entry<>(key, value, null);

        // color nodes in non-full bottommost level red
        if (level == redLevel)
            middle.color = RED;

        if (left != null) {
            middle.left = left;
            left.parent = middle;
        }

        if (mid < hi) {
            Entry<K, V> right = buildFromSorted(level + 1, mid + 1, hi, redLevel,
                    it, str, defaultVal);
            middle.right = right;
            right.parent = middle;
        }

        return middle;
    }

    /**
     * Finds the level down to which to assign all nodes BLACK.  This is the
     * last `full' level of the complete binary tree produced by buildTree.
     * The remaining nodes are colored RED. (This makes a `nice' set of
     * color assignments wrt future insertions.) This level number is
     * computed by finding the number of splits needed to reach the zeroeth
     * node.
     *
     * @param size the (non-negative) number of keys in the tree to be built
     */
    private static int computeRedLevel(int size) {
        return 31 - Integer.numberOfLeadingZeros(size + 1);
    }

    /**
     * Currently, we support Spliterator-based versions only for the
     * full map, in either plain of descending form, otherwise relying
     * on defaults because size estimation for submaps would dominate
     * costs. The type tests needed to check these for key views are
     * not very nice but avoid disrupting existing class
     * structures. Callers must use plain default spliterators if this
     * returns null.
     */
    static <K> Spliterator<K> keySpliteratorFor(NavigableMap<K, ?> m) {
        if (m instanceof TreeMap) {
            @SuppressWarnings("unchecked") TreeMap<K, Object> t =
                    (TreeMap<K, Object>) m;
            return t.keySpliterator();
        }
        if (m instanceof DescendingSubMap) {
            @SuppressWarnings("unchecked") DescendingSubMap<K, ?> dm =
                    (DescendingSubMap<K, ?>) m;
            TreeMap<K, ?> tm = dm.m;
            if (dm == tm.descendingMap) {
                @SuppressWarnings("unchecked") TreeMap<K, Object> t =
                        (TreeMap<K, Object>) tm;
                return t.descendingKeySpliterator();
            }
        }
        @SuppressWarnings("unchecked") NavigableSubMap<K, ?> sm =
                (NavigableSubMap<K, ?>) m;
        return sm.keySpliterator();
    }

    final Spliterator<K> keySpliterator() {
        return new KeySpliterator<>(this, null, null, 0, -1, 0);
    }

    final Spliterator<K> descendingKeySpliterator() {
        return new DescendingKeySpliterator<>(this, null, null, 0, -2, 0);
    }

    /**
     * Base class for spliterators.  Iteration starts at a given
     * origin and continues up to but not including a given fence (or
     * null for end).  At top-level, for ascending cases, the first
     * split uses the root as left-fence/right-origin. From there,
     * right-hand splits replace the current fence with its left
     * child, also serving as origin for the split-off spliterator.
     * Left-hands are symmetric. Descending versions place the origin
     * at the end and invert ascending split rules.  This base class
     * is non-committal about directionality, or whether the top-level
     * spliterator covers the whole tree. This means that the actual
     * split mechanics are located in subclasses. Some of the subclass
     * trySplit methods are identical (except for return types), but
     * not nicely factorable.
     * <p>
     * Currently, subclass versions exist only for the full map
     * (including descending keys via its descendingMap).  Others are
     * possible but currently not worthwhile because submaps require
     * O(n) computations to determine size, which substantially limits
     * potential speed-ups of using custom Spliterators versus default
     * mechanics.
     * <p>
     * To boostrap initialization, external constructors use
     * negative size estimates: -1 for ascend, -2 for descend.
     */
    static class TreeMapSpliterator<K, V> {
        final TreeMap<K, V> tree;
        TreeMap.Entry<K, V> current; // traverser; initially first node in range
        TreeMap.Entry<K, V> fence;   // one past last, or null
        int side;                   // 0: top, -1: is a left split, +1: right
        int est;                    // size estimate (exact only for top-level)
        int expectedModCount;       // for CME checks

        TreeMapSpliterator(TreeMap<K, V> tree,
                           TreeMap.Entry<K, V> origin, TreeMap.Entry<K, V> fence,
                           int side, int est, int expectedModCount) {
            this.tree = tree;
            this.current = origin;
            this.fence = fence;
            this.side = side;
            this.est = est;
            this.expectedModCount = expectedModCount;
        }

        final int getEstimate() { // force initialization
            int s;
            TreeMap<K, V> t;
            if ((s = est) < 0) {
                if ((t = tree) != null) {
                    current = (s == -1) ? t.getFirstEntry() : t.getLastEntry();
                    s = est = t.size;
                    expectedModCount = t.modCount;
                } else
                    s = est = 0;
            }
            return s;
        }

        public final long estimateSize() {
            return (long) getEstimate();
        }
    }

    static final class KeySpliterator<K, V>
            extends TreeMapSpliterator<K, V>
            implements Spliterator<K> {
        KeySpliterator(TreeMap<K, V> tree,
                       TreeMap.Entry<K, V> origin, TreeMap.Entry<K, V> fence,
                       int side, int est, int expectedModCount) {
            super(tree, origin, fence, side, est, expectedModCount);
        }

        public KeySpliterator<K, V> trySplit() {
            if (est < 0)
                getEstimate(); // force initialization
            int d = side;
            TreeMap.Entry<K, V> e = current, f = fence,
                    s = ((e == null || e == f) ? null :      // empty
                            (d == 0) ? tree.root : // was top
                                    (d > 0) ? e.right :   // was right
                                            (d < 0 && f != null) ? f.left :    // was left
                                                    null);
            if (s != null && s != e && s != f &&
                    tree.compare(e.key, s.key) < 0) {        // e not already past s
                side = 1;
                return new KeySpliterator<>
                        (tree, e, current = s, -1, est >>>= 1, expectedModCount);
            }
            return null;
        }

        public void forEachRemaining(Consumer<? super K> action) {
            if (action == null)
                throw new NullPointerException();
            if (est < 0)
                getEstimate(); // force initialization
            TreeMap.Entry<K, V> f = fence, e, p, pl;
            if ((e = current) != null && e != f) {
                current = f; // exhaust
                do {
                    action.accept(e.key);
                    if ((p = e.right) != null) {
                        while ((pl = p.left) != null)
                            p = pl;
                    } else {
                        while ((p = e.parent) != null && e == p.right)
                            e = p;
                    }
                } while ((e = p) != null && e != f);
                if (tree.modCount != expectedModCount)
                    throw new ConcurrentModificationException();
            }
        }

        public boolean tryAdvance(Consumer<? super K> action) {
            TreeMap.Entry<K, V> e;
            if (action == null)
                throw new NullPointerException();
            if (est < 0)
                getEstimate(); // force initialization
            if ((e = current) == null || e == fence)
                return false;
            current = successor(e);
            action.accept(e.key);
            if (tree.modCount != expectedModCount)
                throw new ConcurrentModificationException();
            return true;
        }

        public int characteristics() {
            return (side == 0 ? Spliterator.SIZED : 0) |
                    Spliterator.DISTINCT | Spliterator.SORTED | Spliterator.ORDERED;
        }

        public final Comparator<? super K> getComparator() {
            return tree.comparator;
        }

    }

    static final class DescendingKeySpliterator<K, V>
            extends TreeMapSpliterator<K, V>
            implements Spliterator<K> {
        DescendingKeySpliterator(TreeMap<K, V> tree,
                                 TreeMap.Entry<K, V> origin, TreeMap.Entry<K, V> fence,
                                 int side, int est, int expectedModCount) {
            super(tree, origin, fence, side, est, expectedModCount);
        }

        public DescendingKeySpliterator<K, V> trySplit() {
            if (est < 0)
                getEstimate(); // force initialization
            int d = side;
            TreeMap.Entry<K, V> e = current, f = fence,
                    s = ((e == null || e == f) ? null :      // empty
                            (d == 0) ? tree.root : // was top
                                    (d < 0) ? e.left :    // was left
                                            (d > 0 && f != null) ? f.right :   // was right
                                                    null);
            if (s != null && s != e && s != f &&
                    tree.compare(e.key, s.key) > 0) {       // e not already past s
                side = 1;
                return new DescendingKeySpliterator<>
                        (tree, e, current = s, -1, est >>>= 1, expectedModCount);
            }
            return null;
        }

        public void forEachRemaining(Consumer<? super K> action) {
            if (action == null)
                throw new NullPointerException();
            if (est < 0)
                getEstimate(); // force initialization
            TreeMap.Entry<K, V> f = fence, e, p, pr;
            if ((e = current) != null && e != f) {
                current = f; // exhaust
                do {
                    action.accept(e.key);
                    if ((p = e.left) != null) {
                        while ((pr = p.right) != null)
                            p = pr;
                    } else {
                        while ((p = e.parent) != null && e == p.left)
                            e = p;
                    }
                } while ((e = p) != null && e != f);
                if (tree.modCount != expectedModCount)
                    throw new ConcurrentModificationException();
            }
        }

        public boolean tryAdvance(Consumer<? super K> action) {
            TreeMap.Entry<K, V> e;
            if (action == null)
                throw new NullPointerException();
            if (est < 0)
                getEstimate(); // force initialization
            if ((e = current) == null || e == fence)
                return false;
            current = predecessor(e);
            action.accept(e.key);
            if (tree.modCount != expectedModCount)
                throw new ConcurrentModificationException();
            return true;
        }

        public int characteristics() {
            return (side == 0 ? Spliterator.SIZED : 0) |
                    Spliterator.DISTINCT | Spliterator.ORDERED;
        }
    }

    static final class ValueSpliterator<K, V>
            extends TreeMapSpliterator<K, V>
            implements Spliterator<V> {
        ValueSpliterator(TreeMap<K, V> tree,
                         TreeMap.Entry<K, V> origin, TreeMap.Entry<K, V> fence,
                         int side, int est, int expectedModCount) {
            super(tree, origin, fence, side, est, expectedModCount);
        }

        public ValueSpliterator<K, V> trySplit() {
            if (est < 0)
                getEstimate(); // force initialization
            int d = side;
            TreeMap.Entry<K, V> e = current, f = fence,
                    s = ((e == null || e == f) ? null :      // empty
                            (d == 0) ? tree.root : // was top
                                    (d > 0) ? e.right :   // was right
                                            (d < 0 && f != null) ? f.left :    // was left
                                                    null);
            if (s != null && s != e && s != f &&
                    tree.compare(e.key, s.key) < 0) {        // e not already past s
                side = 1;
                return new ValueSpliterator<>
                        (tree, e, current = s, -1, est >>>= 1, expectedModCount);
            }
            return null;
        }

        public void forEachRemaining(Consumer<? super V> action) {
            if (action == null)
                throw new NullPointerException();
            if (est < 0)
                getEstimate(); // force initialization
            TreeMap.Entry<K, V> f = fence, e, p, pl;
            if ((e = current) != null && e != f) {
                current = f; // exhaust
                do {
                    action.accept(e.value);
                    if ((p = e.right) != null) {
                        while ((pl = p.left) != null)
                            p = pl;
                    } else {
                        while ((p = e.parent) != null && e == p.right)
                            e = p;
                    }
                } while ((e = p) != null && e != f);
                if (tree.modCount != expectedModCount)
                    throw new ConcurrentModificationException();
            }
        }

        public boolean tryAdvance(Consumer<? super V> action) {
            TreeMap.Entry<K, V> e;
            if (action == null)
                throw new NullPointerException();
            if (est < 0)
                getEstimate(); // force initialization
            if ((e = current) == null || e == fence)
                return false;
            current = successor(e);
            action.accept(e.value);
            if (tree.modCount != expectedModCount)
                throw new ConcurrentModificationException();
            return true;
        }

        public int characteristics() {
            return (side == 0 ? Spliterator.SIZED : 0) | Spliterator.ORDERED;
        }
    }

    static final class EntrySpliterator<K, V>
            extends TreeMapSpliterator<K, V>
            implements Spliterator<Map.Entry<K, V>> {
        EntrySpliterator(TreeMap<K, V> tree,
                         TreeMap.Entry<K, V> origin, TreeMap.Entry<K, V> fence,
                         int side, int est, int expectedModCount) {
            super(tree, origin, fence, side, est, expectedModCount);
        }

        public EntrySpliterator<K, V> trySplit() {
            if (est < 0)
                getEstimate(); // force initialization
            int d = side;
            TreeMap.Entry<K, V> e = current, f = fence,
                    s = ((e == null || e == f) ? null :      // empty
                            (d == 0) ? tree.root : // was top
                                    (d > 0) ? e.right :   // was right
                                            (d < 0 && f != null) ? f.left :    // was left
                                                    null);
            if (s != null && s != e && s != f &&
                    tree.compare(e.key, s.key) < 0) {        // e not already past s
                side = 1;
                return new EntrySpliterator<>
                        (tree, e, current = s, -1, est >>>= 1, expectedModCount);
            }
            return null;
        }

        public void forEachRemaining(Consumer<? super Map.Entry<K, V>> action) {
            if (action == null)
                throw new NullPointerException();
            if (est < 0)
                getEstimate(); // force initialization
            TreeMap.Entry<K, V> f = fence, e, p, pl;
            if ((e = current) != null && e != f) {
                current = f; // exhaust
                do {
                    action.accept(e);
                    if ((p = e.right) != null) {
                        while ((pl = p.left) != null)
                            p = pl;
                    } else {
                        while ((p = e.parent) != null && e == p.right)
                            e = p;
                    }
                } while ((e = p) != null && e != f);
                if (tree.modCount != expectedModCount)
                    throw new ConcurrentModificationException();
            }
        }

        public boolean tryAdvance(Consumer<? super Map.Entry<K, V>> action) {
            TreeMap.Entry<K, V> e;
            if (action == null)
                throw new NullPointerException();
            if (est < 0)
                getEstimate(); // force initialization
            if ((e = current) == null || e == fence)
                return false;
            current = successor(e);
            action.accept(e);
            if (tree.modCount != expectedModCount)
                throw new ConcurrentModificationException();
            return true;
        }

        public int characteristics() {
            return (side == 0 ? Spliterator.SIZED : 0) |
                    Spliterator.DISTINCT | Spliterator.SORTED | Spliterator.ORDERED;
        }

        @Override
        public Comparator<Map.Entry<K, V>> getComparator() {
            // Adapt or create a key-based comparator
            if (tree.comparator != null) {
                return Map.Entry.comparingByKey(tree.comparator);
            } else {
                return (Comparator<Map.Entry<K, V>> & Serializable) (e1, e2) -> {
                    @SuppressWarnings("unchecked")
                    Comparable<? super K> k1 = (Comparable<? super K>) e1.getKey();
                    return k1.compareTo(e2.getKey());
                };
            }
        }
    }
}
