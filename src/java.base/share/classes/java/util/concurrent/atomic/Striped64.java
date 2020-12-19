/*
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

/*
 * This file is available under and governed by the GNU General Public
 * License version 2 only, as published by the Free Software Foundation.
 * However, the following notice accompanied the original version of this
 * file:
 *
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

package java.util.concurrent.atomic;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.DoubleBinaryOperator;
import java.util.function.LongBinaryOperator;

/**
 * A package-local class holding common representation and mechanics
 * for classes supporting dynamic striping on 64bit values. The class
 * extends Number so that concrete subclasses must publicly do so.
 */
@SuppressWarnings("serial")
abstract class Striped64 extends Number {
    /*
     * This class maintains a lazily-initialized table of atomically
     * updated variables, plus an extra "base" field. The table size
     * is a power of two. Indexing uses masked per-thread hash codes.
     * Nearly all declarations in this class are package-private,
     * accessed directly by subclasses.
     *
     * Table entries are of class Cell; a variant of AtomicLong padded
     * (via @Contended) to reduce cache contention. Padding is
     * overkill for most Atomics because they are usually irregularly
     * scattered in memory and thus don't interfere much with each
     * other. But Atomic objects residing in arrays will tend to be
     * placed adjacent to each other, and so will most often share
     * cache lines (with a huge negative performance impact) without
     * this precaution.
     *
     * In part because Cells are relatively large, we avoid creating
     * them until they are needed.  When there is no contention, all
     * updates are made to the base field.  Upon first contention (a
     * failed CAS on base update), the table is initialized to size 2.
     * The table size is doubled upon further contention until
     * reaching the nearest power of two greater than or equal to the
     * number of CPUS. Table slots remain empty (null) until they are
     * needed.
     *
     * A single spinlock ("cellsBusy") is used for initializing and
     * resizing the table, as well as populating slots with new Cells.
     * There is no need for a blocking lock; when the lock is not
     * available, threads try other slots (or the base).  During these
     * retries, there is increased contention and reduced locality,
     * which is still better than alternatives.
     *
     * The Thread probe fields maintained via ThreadLocalRandom serve
     * as per-thread hash codes. We let them remain uninitialized as
     * zero (if they come in this way) until they contend at slot
     * 0. They are then initialized to values that typically do not
     * often conflict with others.  Contention and/or table collisions
     * are indicated by failed CASes when performing an update
     * operation. Upon a collision, if the table size is less than
     * the capacity, it is doubled in size unless some other thread
     * holds the lock. If a hashed slot is empty, and lock is
     * available, a new Cell is created. Otherwise, if the slot
     * exists, a CAS is tried.  Retries proceed by "double hashing",
     * using a secondary hash (Marsaglia XorShift) to try to find a
     * free slot.
     *
     * The table size is capped because, when there are more threads
     * than CPUs, supposing that each thread were bound to a CPU,
     * there would exist a perfect hash function mapping threads to
     * slots that eliminates collisions. When we reach capacity, we
     * search for this mapping by randomly varying the hash codes of
     * colliding threads.  Because search is random, and collisions
     * only become known via CAS failures, convergence can be slow,
     * and because threads are typically not bound to CPUS forever,
     * may not occur at all. However, despite these limitations,
     * observed contention rates are typically low in these cases.
     *
     * It is possible for a Cell to become unused when threads that
     * once hashed to it terminate, as well as in the case where
     * doubling the table causes no thread to hash to it under
     * expanded mask.  We do not try to detect or remove such cells,
     * under the assumption that for long-running instances, observed
     * contention levels will recur, so the cells will eventually be
     * needed again; and for short-lived ones, it does not matter.
     */

    /**
     * Padded variant of AtomicLong supporting only raw accesses plus CAS.
     *
     * JVM intrinsics note: It would be possible to use a release-only
     * form of CAS here, if it were provided.
     */
    @jdk.internal.vm.annotation.Contended
    static final class Cell {
        // 内部维护的值
        volatile long value;
        Cell(long x) { value = x; }
        final boolean cas(long cmp, long val) {
            return VALUE.compareAndSet(this, cmp, val);
        }
        final void reset() {
            VALUE.setVolatile(this, 0L);
        }
        final void reset(long identity) {
            VALUE.setVolatile(this, identity);
        }
        final long getAndSet(long val) {
            return (long)VALUE.getAndSet(this, val);
        }

        // VarHandle mechanics
        // value 的 varHanlde
        private static final VarHandle VALUE;
        static {
            try {
                MethodHandles.Lookup l = MethodHandles.lookup();
                VALUE = l.findVarHandle(Cell.class, "value", long.class);
            } catch (ReflectiveOperationException e) {
                throw new ExceptionInInitializerError(e);
            }
        }
    }

    /** Number of CPUS, to place bound on table size */
    /**
     * CPU 数，虚拟核
     */
    static final int NCPU = Runtime.getRuntime().availableProcessors();

    /**
     * cell 的个数
     * Table of cells. When non-null, size is a power of 2.
     */
    transient volatile Cell[] cells;

    /**
     * 没有发生竞争的情况下使用 base 来保存计数
     *
     * Base value, used mainly when there is no contention, but also as
     * a fallback during table initialization races. Updated via CAS.
     */
    transient volatile long base;

    /**
     * 创建 cell 或者扩容 cell 的时候使用的自旋锁
     *
     * Spinlock (locked via CAS) used when resizing and/or creating Cells.
     */
    transient volatile int cellsBusy;

    /**
     * 包内可用
     *
     * Package-private default constructor.
     */
    Striped64() {
    }

    /**
     * cas 更新 base
     *
     * CASes the base field.
     */
    final boolean casBase(long cmp, long val) {
        return BASE.compareAndSet(this, cmp, val);
    }

    /**
     * 设置 base，返回 base 原来的值
     */
    final long getAndSetBase(long val) {
        return (long)BASE.getAndSet(this, val);
    }

    /**
     * cas 更新 cellsBusy
     *
     * CASes the cellsBusy field from 0 to 1 to acquire lock.
     */
    final boolean casCellsBusy() {
        return CELLSBUSY.compareAndSet(this, 0, 1);
    }

    /**
     * 返回当前线程的 probe 值
     *
     * Returns the probe value for the current thread.
     * Duplicated from ThreadLocalRandom because of packaging restrictions.
     */
    static final int getProbe() {
        return (int) THREAD_PROBE.get(Thread.currentThread());
    }

    /**
     * Pseudo-randomly advances and records the given probe value for the
     * given thread.
     * Duplicated from ThreadLocalRandom because of packaging restrictions.
     */
    static final int advanceProbe(int probe) {
        probe ^= probe << 13;   // xorshift
        probe ^= probe >>> 17;
        probe ^= probe << 5;
        // 设置当前线程的 probe
        THREAD_PROBE.set(Thread.currentThread(), probe);
        return probe;
    }

    /**
     * Handles cases of updates involving initialization, resizing,
     * creating new Cells, and/or contention. See above for
     * explanation. This method suffers the usual non-modularity
     * problems of optimistic retry code, relying on rechecked sets of
     * reads.
     *
     * @param x the value
     * @param fn the update function, or null for add (this convention
     * avoids the need for an extra field or function in LongAdder).
     * @param wasUncontended false if CAS failed before call
     */
    final void longAccumulate(long x, LongBinaryOperator fn,
                              boolean wasUncontended) {
        // 存储线程的 probe 值
        int h;
        // 如果getProbe() 方法返回 0 ，说明随机数未初始化
        if ((h = getProbe()) == 0) {
            // 初始化 ThreadLocalRandom
            // 并出初始化 probe 值
            ThreadLocalRandom.current(); // force initialization
            // 获取 probe 值
            h = getProbe();
            // 如果都未初始化，表示肯定还不存在竞争
            wasUncontended = true;
        }
        // 是否发生碰撞的标志
        boolean collide = false;                // True if last slot nonempty
        // 自旋
        done: for (;;) {
            Cell[] cs; Cell c; int n; long v;
            // cells 已经初始化
            if ((cs = cells) != null && (n = cs.length) > 0) {
                // 当前位置的 cell 为 null
                if ((c = cs[(n - 1) & h]) == null) {
                    // 未进行 cells 扩容动作
                    if (cellsBusy == 0) {       // Try to attach new Cell
                        // 创建一个新的 cell
                        Cell r = new Cell(x);   // Optimistically create
                        // 获取锁
                        if (cellsBusy == 0 && casCellsBusy()) {
                            try {               // Recheck under lock
                                Cell[] rs; int m, j;
                                if ((rs = cells) != null &&
                                    (m = rs.length) > 0 &&
                                    rs[j = (m - 1) & h] == null) {
                                    // 放到 rs[j] 位置上
                                    rs[j] = r;
                                    // 初始化结束
                                    break done;
                                }
                            } finally {
                                // 释放锁
                                cellsBusy = 0;
                            }
                            continue;           // Slot is now non-empty
                        }
                    }
                    collide = false;
                }
                // 当前线程所在的 Cell 不为空，且更新失败了
                // 这里简单地设为 true ，相当于简单地自旋一次
                // 通过下面的语句修改线程的 probe 再重新尝试
                else if (!wasUncontended)       // CAS already known to fail
                    wasUncontended = true;      // Continue after rehash
                // 通过 CAS 更新当前线程所在 Cell 的值，如果成功了就返回
                else if (c.cas(v = c.value,
                               (fn == null) ? v + x : fn.applyAsLong(v, x)))
                    // 跳出，结束
                    break;
                // cells 的个数 > CPU 虚拟核的个数
                // 或者 cells 扩容了
                else if (n >= NCPU || cells != cs)
                    collide = false;            // At max size or stale
                else if (!collide)
                    // 走到这里说明发生冲突了
                    collide = true;
                // 获取锁
                else if (cellsBusy == 0 && casCellsBusy()) {
                    try {
                        // cells 没改变
                        if (cells == cs)        // Expand table unless stale
                            // 扩容
                            cells = Arrays.copyOf(cs, n << 1);
                    } finally {
                        // 释放锁
                        cellsBusy = 0;
                    }
                    // 设置冲突为 false
                    collide = false;
                    // 跳过该次循环，继续尝试写入
                    continue;                   // Retry with expanded table
                }
                // 重新生成 probe
                h = advanceProbe(h);
            }
            // cellsBusy == 0 此时没有对 cell 的操作
            // cells == cs cells 没有改变
            // casCellsBusy() 获取到了 cellsBusy 锁
            else if (cellsBusy == 0 && cells == cs && casCellsBusy()) {
                try {                           // Initialize table
                    // 创建 cells
                    if (cells == cs) {
                        Cell[] rs = new Cell[2];
                        rs[h & 1] = new Cell(x);
                        cells = rs;
                        break done;
                    }
                } finally {
                    // 将 cellBusy 更改回 0
                    cellsBusy = 0;
                }
            }
            // Fall back on using base
            // 在 base 上更新
            else if (casBase(v = base,
                             (fn == null) ? v + x : fn.applyAsLong(v, x)))
                break done;
        }
    }

    /**
     * 执行 DoubleBinaryOperator 函数
     */
    private static long apply(DoubleBinaryOperator fn, long v, double x) {
        double d = Double.longBitsToDouble(v);
        d = (fn == null) ? d + x : fn.applyAsDouble(d, x);
        return Double.doubleToRawLongBits(d);
    }

    /**
     * Same as longAccumulate, but injecting long/double conversions
     * in too many places to sensibly merge with long version, given
     * the low-overhead requirements of this class. So must instead be
     * maintained by copy/paste/adapt.
     */
    final void doubleAccumulate(double x, DoubleBinaryOperator fn,
                                boolean wasUncontended) {
        // 保存当前线程的 probe
        int h;
        // 如果 == 0 说明没有初始化
        if ((h = getProbe()) == 0) {
            // 初始化 ThreadLocalRandom
            // 并出初始化 probe 值
            ThreadLocalRandom.current(); // force initialization
            // 获取当前线程的 probe 值
            h = getProbe();
            // 未发生竞争
            wasUncontended = true;
        }
        // 是否发生碰撞标志
        boolean collide = false;                // True if last slot nonempty
        done: for (;;) {
            Cell[] cs; Cell c; int n; long v;
            // 已经初始化 cells
            if ((cs = cells) != null && (n = cs.length) > 0) {
                // 如果当前线程计算出来的 cells 中的位置没有初始化
                if ((c = cs[(n - 1) & h]) == null) {
                    // 当前锁未被占有
                    if (cellsBusy == 0) {       // Try to attach new Cell
                        // 创建当前的 cell， 将 double 值转为 longBits
                        Cell r = new Cell(Double.doubleToRawLongBits(x));
                        // 获取锁
                        if (cellsBusy == 0 && casCellsBusy()) {
                            try {               // Recheck under lock
                                // 放到计算出来的位置下
                                Cell[] rs; int m, j;
                                if ((rs = cells) != null &&
                                    (m = rs.length) > 0 &&
                                    rs[j = (m - 1) & h] == null) {
                                    rs[j] = r;
                                    break done;
                                }
                            } finally {
                                // 释放锁
                                cellsBusy = 0;
                            }
                            continue;           // Slot is now non-empty
                        }
                    }
                    // 当前线程其他持有着锁
                    collide = false;
                }
                else if (!wasUncontended)       // CAS already known to fail
                    wasUncontended = true;      // Continue after rehash
                else if (c.cas(v = c.value, apply(fn, v, x)))
                    break;
                else if (n >= NCPU || cells != cs)
                    collide = false;            // At max size or stale
                else if (!collide)
                    collide = true;
                else if (cellsBusy == 0 && casCellsBusy()) {
                    try {
                        if (cells == cs)        // Expand table unless stale
                            cells = Arrays.copyOf(cs, n << 1);
                    } finally {
                        cellsBusy = 0;
                    }
                    collide = false;
                    continue;                   // Retry with expanded table
                }
                h = advanceProbe(h);
            }
            else if (cellsBusy == 0 && cells == cs && casCellsBusy()) {
                try {                           // Initialize table
                    if (cells == cs) {
                        Cell[] rs = new Cell[2];
                        rs[h & 1] = new Cell(Double.doubleToRawLongBits(x));
                        cells = rs;
                        break done;
                    }
                } finally {
                    cellsBusy = 0;
                }
            }
            // Fall back on using base
            else if (casBase(v = base, apply(fn, v, x)))
                break done;
        }
    }

    // VarHandle mechanics
    private static final VarHandle BASE;
    private static final VarHandle CELLSBUSY;
    private static final VarHandle THREAD_PROBE;
    static {
        try {
            MethodHandles.Lookup l = MethodHandles.lookup();
            BASE = l.findVarHandle(Striped64.class,
                    "base", long.class);
            CELLSBUSY = l.findVarHandle(Striped64.class,
                    "cellsBusy", int.class);
            l = java.security.AccessController.doPrivileged(
                    new java.security.PrivilegedAction<>() {
                        public MethodHandles.Lookup run() {
                            try {
                                return MethodHandles.privateLookupIn(Thread.class, MethodHandles.lookup());
                            } catch (ReflectiveOperationException e) {
                                throw new ExceptionInInitializerError(e);
                            }
                        }});
            THREAD_PROBE = l.findVarHandle(Thread.class,
                    "threadLocalRandomProbe", int.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

}
