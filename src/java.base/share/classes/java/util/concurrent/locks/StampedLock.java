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

package java.util.concurrent.locks;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.concurrent.TimeUnit;
import jdk.internal.vm.annotation.ReservedStackAccess;

/**
 * A capability-based lock with three modes for controlling read/write
 * access.  The state of a StampedLock consists of a version and mode.
 * Lock acquisition methods return a stamp that represents and
 * controls access with respect to a lock state; "try" versions of
 * these methods may instead return the special value zero to
 * represent failure to acquire access. Lock release and conversion
 * methods require stamps as arguments, and fail if they do not match
 * the state of the lock. The three modes are:
 *
 * <ul>
 *
 *  <li><b>Writing.</b> Method {@link #writeLock} possibly blocks
 *   waiting for exclusive access, returning a stamp that can be used
 *   in method {@link #unlockWrite} to release the lock. Untimed and
 *   timed versions of {@code tryWriteLock} are also provided. When
 *   the lock is held in write mode, no read locks may be obtained,
 *   and all optimistic read validations will fail.
 *
 *  <li><b>Reading.</b> Method {@link #readLock} possibly blocks
 *   waiting for non-exclusive access, returning a stamp that can be
 *   used in method {@link #unlockRead} to release the lock. Untimed
 *   and timed versions of {@code tryReadLock} are also provided.
 *
 *  <li><b>Optimistic Reading.</b> Method {@link #tryOptimisticRead}
 *   returns a non-zero stamp only if the lock is not currently held
 *   in write mode. Method {@link #validate} returns true if the lock
 *   has not been acquired in write mode since obtaining a given
 *   stamp.  This mode can be thought of as an extremely weak version
 *   of a read-lock, that can be broken by a writer at any time.  The
 *   use of optimistic mode for short read-only code segments often
 *   reduces contention and improves throughput.  However, its use is
 *   inherently fragile.  Optimistic read sections should only read
 *   fields and hold them in local variables for later use after
 *   validation. Fields read while in optimistic mode may be wildly
 *   inconsistent, so usage applies only when you are familiar enough
 *   with data representations to check consistency and/or repeatedly
 *   invoke method {@code validate()}.  For example, such steps are
 *   typically required when first reading an object or array
 *   reference, and then accessing one of its fields, elements or
 *   methods.
 *
 * </ul>
 *
 * <p>This class also supports methods that conditionally provide
 * conversions across the three modes. For example, method {@link
 * #tryConvertToWriteLock} attempts to "upgrade" a mode, returning
 * a valid write stamp if (1) already in writing mode (2) in reading
 * mode and there are no other readers or (3) in optimistic mode and
 * the lock is available. The forms of these methods are designed to
 * help reduce some of the code bloat that otherwise occurs in
 * retry-based designs.
 *
 * <p>StampedLocks are designed for use as internal utilities in the
 * development of thread-safe components. Their use relies on
 * knowledge of the internal properties of the data, objects, and
 * methods they are protecting.  They are not reentrant, so locked
 * bodies should not call other unknown methods that may try to
 * re-acquire locks (although you may pass a stamp to other methods
 * that can use or convert it).  The use of read lock modes relies on
 * the associated code sections being side-effect-free.  Unvalidated
 * optimistic read sections cannot call methods that are not known to
 * tolerate potential inconsistencies.  Stamps use finite
 * representations, and are not cryptographically secure (i.e., a
 * valid stamp may be guessable). Stamp values may recycle after (no
 * sooner than) one year of continuous operation. A stamp held without
 * use or validation for longer than this period may fail to validate
 * correctly.  StampedLocks are serializable, but always deserialize
 * into initial unlocked state, so they are not useful for remote
 * locking.
 *
 * <p>Like {@link java.util.concurrent.Semaphore Semaphore}, but unlike most
 * {@link Lock} implementations, StampedLocks have no notion of ownership.
 * Locks acquired in one thread can be released or converted in another.
 *
 * <p>The scheduling policy of StampedLock does not consistently
 * prefer readers over writers or vice versa.  All "try" methods are
 * best-effort and do not necessarily conform to any scheduling or
 * fairness policy. A zero return from any "try" method for acquiring
 * or converting locks does not carry any information about the state
 * of the lock; a subsequent invocation may succeed.
 *
 * <p>Because it supports coordinated usage across multiple lock
 * modes, this class does not directly implement the {@link Lock} or
 * {@link ReadWriteLock} interfaces. However, a StampedLock may be
 * viewed {@link #asReadLock()}, {@link #asWriteLock()}, or {@link
 * #asReadWriteLock()} in applications requiring only the associated
 * set of functionality.
 *
 * <p><b>Sample Usage.</b> The following illustrates some usage idioms
 * in a class that maintains simple two-dimensional points. The sample
 * code illustrates some try/catch conventions even though they are
 * not strictly needed here because no exceptions can occur in their
 * bodies.
 *
 * <pre> {@code
 * class Point {
 *   private double x, y;
 *   private final StampedLock sl = new StampedLock();
 *
 *   // an exclusively locked method
 *   void move(double deltaX, double deltaY) {
 *     long stamp = sl.writeLock();
 *     try {
 *       x += deltaX;
 *       y += deltaY;
 *     } finally {
 *       sl.unlockWrite(stamp);
 *     }
 *   }
 *
 *   // a read-only method
 *   // upgrade from optimistic read to read lock
 *   double distanceFromOrigin() {
 *     long stamp = sl.tryOptimisticRead();
 *     try {
 *       retryHoldingLock: for (;; stamp = sl.readLock()) {
 *         if (stamp == 0L)
 *           continue retryHoldingLock;
 *         // possibly racy reads
 *         double currentX = x;
 *         double currentY = y;
 *         if (!sl.validate(stamp))
 *           continue retryHoldingLock;
 *         return Math.hypot(currentX, currentY);
 *       }
 *     } finally {
 *       if (StampedLock.isReadLockStamp(stamp))
 *         sl.unlockRead(stamp);
 *     }
 *   }
 *
 *   // upgrade from optimistic read to write lock
 *   void moveIfAtOrigin(double newX, double newY) {
 *     long stamp = sl.tryOptimisticRead();
 *     try {
 *       retryHoldingLock: for (;; stamp = sl.writeLock()) {
 *         if (stamp == 0L)
 *           continue retryHoldingLock;
 *         // possibly racy reads
 *         double currentX = x;
 *         double currentY = y;
 *         if (!sl.validate(stamp))
 *           continue retryHoldingLock;
 *         if (currentX != 0.0 || currentY != 0.0)
 *           break;
 *         stamp = sl.tryConvertToWriteLock(stamp);
 *         if (stamp == 0L)
 *           continue retryHoldingLock;
 *         // exclusive access
 *         x = newX;
 *         y = newY;
 *         return;
 *       }
 *     } finally {
 *       if (StampedLock.isWriteLockStamp(stamp))
 *         sl.unlockWrite(stamp);
 *     }
 *   }
 *
 *   // Upgrade read lock to write lock
 *   void moveIfAtOrigin(double newX, double newY) {
 *     long stamp = sl.readLock();
 *     try {
 *       while (x == 0.0 && y == 0.0) {
 *         long ws = sl.tryConvertToWriteLock(stamp);
 *         if (ws != 0L) {
 *           stamp = ws;
 *           x = newX;
 *           y = newY;
 *           break;
 *         }
 *         else {
 *           sl.unlockRead(stamp);
 *           stamp = sl.writeLock();
 *         }
 *       }
 *     } finally {
 *       sl.unlock(stamp);
 *     }
 *   }
 * }}</pre>
 *
 * @since 1.8
 * @author Doug Lea
 */

/**
 * 可重入吗？
 * 不可重入
 * 低 7 为表示读锁的个数 + overFlow 的值
 * 高 24 位表示持有写锁的 stamp
 * 第 8 为表示是否持有写锁
 */
public class StampedLock implements java.io.Serializable {
    /*
     * Algorithmic notes:
     *
     * The design employs elements of Sequence locks
     * (as used in linux kernels; see Lameter's
     * http://www.lameter.com/gelato2005.pdf
     * and elsewhere; see
     * Boehm's http://www.hpl.hp.com/techreports/2012/HPL-2012-68.html)
     * and Ordered RW locks (see Shirako et al
     * http://dl.acm.org/citation.cfm?id=2312015)
     *
     *
     *
     * Conceptually, the primary state of the lock includes a sequence
     * number that is odd when write-locked and even otherwise.
     * However, this is offset by a reader count that is non-zero when
     * read-locked.  The read count is ignored when validating
     * "optimistic" seqlock-reader-style stamps.  Because we must use
     * a small finite number of bits (currently 7) for readers, a
     * supplementary reader overflow word is used when the number of
     * readers exceeds the count field. We do this by treating the max
     * reader count value (RBITS) as a spinlock protecting overflow
     * updates.
     *
     * Waiters use a modified form of CLH lock used in
     * AbstractQueuedSynchronizer (see its internal documentation for
     * a fuller account), where each node is tagged (field mode) as
     * either a reader or writer. Sets of waiting readers are grouped
     * (linked) under a common node (field cowait) so act as a single
     * node with respect to most CLH mechanics.  By virtue of the
     * queue structure, wait nodes need not actually carry sequence
     * numbers; we know each is greater than its predecessor.  This
     * simplifies the scheduling policy to a mainly-FIFO scheme that
     * incorporates elements of Phase-Fair locks (see Brandenburg &
     * Anderson, especially http://www.cs.unc.edu/~bbb/diss/).  In
     * particular, we use the phase-fair anti-barging rule: If an
     * incoming reader arrives while read lock is held but there is a
     * queued writer, this incoming reader is queued.  (This rule is
     * responsible for some of the complexity of method acquireRead,
     * but without it, the lock becomes highly unfair.) Method release
     * does not (and sometimes cannot) itself wake up cowaiters. This
     * is done by the primary thread, but helped by any other threads
     * with nothing better to do in methods acquireRead and
     * acquireWrite.
     *
     * These rules apply to threads actually queued. All tryLock forms
     * opportunistically try to acquire locks regardless of preference
     * rules, and so may "barge" their way in.  Randomized spinning is
     * used in the acquire methods to reduce (increasingly expensive)
     * context switching while also avoiding sustained memory
     * thrashing among many threads.  We limit spins to the head of
     * queue. If, upon wakening, a thread fails to obtain lock, and is
     * still (or becomes) the first waiting thread (which indicates
     * that some other thread barged and obtained lock), it escalates
     * spins (up to MAX_HEAD_SPINS) to reduce the likelihood of
     * continually losing to barging threads.
     *
     * Nearly all of these mechanics are carried out in methods
     * acquireWrite and acquireRead, that, as typical of such code,
     * sprawl out because actions and retries rely on consistent sets
     * of locally cached reads.
     *
     * As noted in Boehm's paper (above), sequence validation (mainly
     * method validate()) requires stricter ordering rules than apply
     * to normal volatile reads (of "state").  To force orderings of
     * reads before a validation and the validation itself in those
     * cases where this is not already forced, we use acquireFence.
     * Unlike in that paper, we allow writers to use plain writes.
     * One would not expect reorderings of such writes with the lock
     * acquisition CAS because there is a "control dependency", but it
     * is theoretically possible, so we additionally add a
     * storeStoreFence after lock acquisition CAS.
     *
     * ----------------------------------------------------------------
     * Here's an informal proof that plain reads by _successful_
     * readers see plain writes from preceding but not following
     * writers (following Boehm and the C++ standard [atomics.fences]):
     *
     * Because of the total synchronization order of accesses to
     * volatile long state containing the sequence number, writers and
     * _successful_ readers can be globally sequenced.
     *
     * int x, y;
     *
     * Writer 1:
     * inc sequence (odd - "locked")
     * storeStoreFence();
     * x = 1; y = 2;
     * inc sequence (even - "unlocked")
     *
     * Successful Reader:
     * read sequence (even)
     * // must see writes from Writer 1 but not Writer 2
     * r1 = x; r2 = y;
     * acquireFence();
     * read sequence (even - validated unchanged)
     * // use r1 and r2
     *
     * Writer 2:
     * inc sequence (odd - "locked")
     * storeStoreFence();
     * x = 3; y = 4;
     * inc sequence (even - "unlocked")
     *
     * Visibility of writer 1's stores is normal - reader's initial
     * read of state synchronizes with writer 1's final write to state.
     * Lack of visibility of writer 2's plain writes is less obvious.
     * If reader's read of x or y saw writer 2's write, then (assuming
     * semantics of C++ fences) the storeStoreFence would "synchronize"
     * with reader's acquireFence and reader's validation read must see
     * writer 2's initial write to state and so validation must fail.
     * But making this "proof" formal and rigorous is an open problem!
     * ----------------------------------------------------------------
     *
     * The memory layout keeps lock state and queue pointers together
     * (normally on the same cache line). This usually works well for
     * read-mostly loads. In most other cases, the natural tendency of
     * adaptive-spin CLH locks to reduce memory contention lessens
     * motivation to further spread out contended locations, but might
     * be subject to future improvements.
     */

    private static final long serialVersionUID = -6001602636862214147L;

    /** Number of processors, for spin control */
    /**
     * CPU 处理器数，一般返回的是逻辑核数，而不是物理核数
     * 超线程 逻辑核数 = 物理核数 * 2
     */
    private static final int NCPU = Runtime.getRuntime().availableProcessors();

    /** Maximum number of retries before enqueuing on acquisition; at least 1 */
    /**
     * 自旋的次数 32 次 or 1 次
     */
    private static final int SPINS = (NCPU > 1) ? 1 << 6 : 1;

    /** Maximum number of tries before blocking at head on acquisition */
    /**
     * 在 head spin 的次数
     */
    private static final int HEAD_SPINS = (NCPU > 1) ? 1 << 10 : 1;

    /** Maximum number of retries before re-blocking */
    /**
     * (多次)再次进入阻塞之前最大的自旋次数
     */
    private static final int MAX_HEAD_SPINS = (NCPU > 1) ? 1 << 16 : 1;

    /** The period for yielding when waiting for overflow spinlock */
    private static final int OVERFLOW_YIELD_RATE = 7; // must be power 2 - 1

    /** The number of bits to use for reader count before overflowing */
    /**
     * 低七位在溢出之前表示读锁的个数
     */
    private static final int LG_READERS = 7;

    // Values for lock state and stamp operations
    /**
     * 读锁的增加单位
     */
    private static final long RUNIT = 1L;
    /**
     * 写锁的标记值，用于判断当前是否持有写锁
     */
    private static final long WBIT  = 1L << LG_READERS;
    /**
     * 读锁的掩码
     */
    private static final long RBITS = WBIT - 1L;
    /**
     * 不溢出的情况下读锁能持有的个数
     */
    private static final long RFULL = RBITS - 1L;
    /**
     * 用于检测是否持有锁 读锁或者写锁
     */
    private static final long ABITS = RBITS | WBIT;
    /**
     * 读锁掩码的反码
     */
    private static final long SBITS = ~RBITS; // note overlap with ABITS

    /*
     * 3 stamp modes can be distinguished by examining (m = stamp & ABITS):
     * write mode: m == WBIT
     * optimistic read mode: m == 0L (even when read lock is held)
     * read mode: m > 0L && m <= RFULL (the stamp is a copy of state, but the
     * read hold count in the stamp is unused other than to determine mode)
     *
     * This differs slightly from the encoding of state:
     * (state & ABITS) == 0L indicates the lock is currently unlocked.
     * (state & ABITS) == RBITS is a special transient value
     * indicating spin-locked to manipulate reader bits overflow.
     */

    /** Initial value for lock state; avoids failure value zero. */
    /**
     * 锁的初始状态，而不是以 0 表示
     */
    private static final long ORIGIN = WBIT << 1;

    // Special value from cancelled acquire methods so caller can throw IE
    private static final long INTERRUPTED = 1L;

    // Values for node status; order matters
    // 节点的状态 等待状态
    private static final int WAITING   = -1;
    // 节点的状态 取消状态
    private static final int CANCELLED =  1;

    // Modes for nodes (int not boolean to allow arithmetic)
    /**
     * 读模式
     */
    private static final int RMODE = 0;
    /**
     * 写模式
     */
    private static final int WMODE = 1;

    /**
     * 等待队列的定义
     * Waiting Node
     */
    /** Wait nodes */
    static final class WNode {
        volatile WNode prev;
        volatile WNode next;
        volatile WNode cowait;    // list of linked readers
        volatile Thread thread;   // non-null while possibly parked
        volatile int status;      // 0, WAITING, or CANCELLED
        final int mode;           // RMODE or WMODE
        WNode(int m, WNode p) { mode = m; prev = p; }
    }

    /** Head of CLH queue */
    private transient volatile WNode whead;
    /** Tail (last) of CLH queue */
    /**
     * CLH 队列的队尾节点
     */
    private transient volatile WNode wtail;

    // views
    /**
     * 锁视图对象，暴露给外部使用
     */
    transient ReadLockView readLockView;
    transient WriteLockView writeLockView;
    transient ReadWriteLockView readWriteLockView;

    /** Lock sequence/state */
    /**
     * 表示锁状态
     */
    private transient volatile long state;
    /** extra reader count when state read count saturated */
    /**
     * 低七位溢出之后保存溢出的读锁个数
     */
    private transient int readerOverflow;

    /**
     * Creates a new lock, initially in unlocked state.
     */
    public StampedLock() {
        // 初始化锁的状态
        state = ORIGIN;
    }

    /**
     * cas 更新 state
     *
     * @param expectedValue
     * @param newValue
     * @return 更新是否成功
     */
    private boolean casState(long expectedValue, long newValue) {
        return STATE.compareAndSet(this, expectedValue, newValue);
    }

    /**
     * 尝试获取写锁
     * @param s
     * @return 返回锁状态值，0 表示获取失败
     */
    private long tryWriteLock(long s) {
        // assert (s & ABITS) == 0L;
        long next;
        // 通过 cas 更新 state
        if (casState(s, next = s | WBIT)) {
            VarHandle.storeStoreFence();
            return next;
        }
        return 0L;
    }

    /**
     * 获取写锁，获取不到则阻塞
     *
     * Exclusively acquires the lock, blocking if necessary
     * until available.
     *
     * @return a write stamp that can be used to unlock or convert mode
     */
    @ReservedStackAccess
    public long writeLock() {
        long next;
        return ((next = tryWriteLock()) != 0L) ? next : acquireWrite(false, 0L);
    }

    /**
     * 获取写锁
     *
     * Exclusively acquires the lock if it is immediately available.
     *
     * @return a write stamp that can be used to unlock or convert mode,
     * or zero if the lock is not available
     */
    @ReservedStackAccess
    public long tryWriteLock() {
        long s;
        // 先检查是否为持有锁
        // 若未持有锁，则调用 tryWriteLock 尝试获取锁
        return (((s = state) & ABITS) == 0L) ? tryWriteLock(s) : 0L;
    }

    /**
     * 带超时时间的获取写锁
     *
     * Exclusively acquires the lock if it is available within the
     * given time and the current thread has not been interrupted.
     * Behavior under timeout and interruption matches that specified
     * for method {@link Lock#tryLock(long,TimeUnit)}.
     *
     * @param time the maximum time to wait for the lock
     * @param unit the time unit of the {@code time} argument
     * @return a write stamp that can be used to unlock or convert mode,
     * or zero if the lock is not available
     * @throws InterruptedException if the current thread is interrupted
     * before acquiring the lock
     */
    public long tryWriteLock(long time, TimeUnit unit)
        throws InterruptedException {
        // 转成 nanos 时间
        long nanos = unit.toNanos(time);
        // 中断检查
        if (!Thread.interrupted()) {
            long next, deadline;
            // 先尝试一次获取
            if ((next = tryWriteLock()) != 0L)
                // 获取成功直接返回 stamp
                return next;
            // 如果 时间 <= 0
            if (nanos <= 0L)
                // 直接返回，不获取
                return 0L;
            // 计算剩余时间
            if ((deadline = System.nanoTime() + nanos) == 0L)
                deadline = 1L;
            // 调用 acquireWrite 方法
            if ((next = acquireWrite(true, deadline)) != INTERRUPTED)
                // 如果成功获取到，则返回 next
                return next;
        }
        throw new InterruptedException();
    }

    /**
     * 写锁的获取，支持响应中断
     *
     * Exclusively acquires the lock, blocking if necessary
     * until available or the current thread is interrupted.
     * Behavior under interruption matches that specified
     * for method {@link Lock#lockInterruptibly()}.
     *
     * @return a write stamp that can be used to unlock or convert mode
     * @throws InterruptedException if the current thread is interrupted
     * before acquiring the lock
     */
    @ReservedStackAccess
    public long writeLockInterruptibly() throws InterruptedException {
        long next;
        // 先检查中断
        if (!Thread.interrupted() &&
                // 直接调用 acquireWrite 获取
            (next = acquireWrite(true, 0L)) != INTERRUPTED)
            return next;
        throw new InterruptedException();
    }

    /**
     * 读锁的获取
     *
     * Non-exclusively acquires the lock, blocking if necessary
     * until available.
     *
     * @return a read stamp that can be used to unlock or convert mode
     */
    @ReservedStackAccess
    public long readLock() {
        long s, next;
        // bypass acquireRead on common uncontended case
        // whead == wtail 队列中没有等待的节点
        // ((s = state) & ABITS) < RFULL 未持有写锁且读锁个数未溢出
        return (whead == wtail
                && ((s = state) & ABITS) < RFULL
                // cas 尝试获取读锁
                && casState(s, next = s + RUNIT))
                // 成功直接返回 next
            ? next
                // 失败后的处理
            : acquireRead(false, 0L);
    }

    /**
     * Non-exclusively acquires the lock if it is immediately available.
     *
     * @return a read stamp that can be used to unlock or convert mode,
     * or zero if the lock is not available
     */
    @ReservedStackAccess
    public long tryReadLock() {
        long s, m, next;
        // 未持有写锁
        while ((m = (s = state) & ABITS) != WBIT) {
            // 如果没有满
            if (m < RFULL) {
                // cas 更新
                if (casState(s, next = s + RUNIT))
                    return next;
            }
            // 如果满了，则调用 overflow 方法获取读锁
            else if ((next = tryIncReaderOverflow(s)) != 0L)
                return next;
        }
        return 0L;
    }

    /**
     * 带超时时间的获取读锁，响应中断
     *
     * Non-exclusively acquires the lock if it is available within the
     * given time and the current thread has not been interrupted.
     * Behavior under timeout and interruption matches that specified
     * for method {@link Lock#tryLock(long,TimeUnit)}.
     *
     * @param time the maximum time to wait for the lock
     * @param unit the time unit of the {@code time} argument
     * @return a read stamp that can be used to unlock or convert mode,
     * or zero if the lock is not available
     * @throws InterruptedException if the current thread is interrupted
     * before acquiring the lock
     */
    @ReservedStackAccess
    public long tryReadLock(long time, TimeUnit unit)
        throws InterruptedException {
        long s, m, next, deadline;
        // 转成 nanos 时间
        long nanos = unit.toNanos(time);
        // 检查中断
        if (!Thread.interrupted()) {
            // 未持有写锁
            if ((m = (s = state) & ABITS) != WBIT) {
                // 未满
                if (m < RFULL) {
                    if (casState(s, next = s + RUNIT))
                        return next;
                }
                // 调用 overflow 获取读锁
                else if ((next = tryIncReaderOverflow(s)) != 0L)
                    return next;
            }
            // 上述获取失败，
            // 1. 可能是因为目前持有写锁
            // 2. cas 获取失败
            // 3. tryIncReaderOverflow 也获取失败

            // 超时后返回 0
            if (nanos <= 0L)
                return 0L;
            // 计算 deadline
            if ((deadline = System.nanoTime() + nanos) == 0L)
                deadline = 1L;
            // 进队列等待
            if ((next = acquireRead(true, deadline)) != INTERRUPTED)
                return next;
        }
        throw new InterruptedException();
    }

    /**
     * 读锁的获取，支持响应中断
     *
     * Non-exclusively acquires the lock, blocking if necessary
     * until available or the current thread is interrupted.
     * Behavior under interruption matches that specified
     * for method {@link Lock#lockInterruptibly()}.
     *
     * @return a read stamp that can be used to unlock or convert mode
     * @throws InterruptedException if the current thread is interrupted
     * before acquiring the lock
     */
    @ReservedStackAccess
    public long readLockInterruptibly() throws InterruptedException {
        long s, next;
        // 未被中断
        if (!Thread.interrupted()
            // bypass acquireRead on common uncontended case
                // 队列中没有等待的节点
            && ((whead == wtail
                // 读锁未满
                 && ((s = state) & ABITS) < RFULL
                // cas 更新 state 成功
                 && casState(s, next = s + RUNIT))
                ||
                // 或者 acquire 获取读锁成功
                (next = acquireRead(true, 0L)) != INTERRUPTED))
            // 返回 stamp
            return next;
        throw new InterruptedException();
    }

    /**
     * 尝试乐观读
     *
     * Returns a stamp that can later be validated, or zero
     * if exclusively locked.
     *
     * @return a valid optimistic read stamp, or zero if exclusively locked
     */
    public long tryOptimisticRead() {
        long s;
        // 先检查是否持有写锁
        // 如果持有写锁直接返回 0，说明获取失败
        // 否则返回一个 stamp = s & SBITS，一般情况下为 1 00000000
        return (((s = state) & WBIT) == 0L) ? (s & SBITS) : 0L;
    }

    /**
     * 验证 stamp 值是否有效
     * 返回自从该 stamp 后没有获取过写锁
     *
     * Returns true if the lock has not been exclusively acquired
     * since issuance of the given stamp. Always returns false if the
     * stamp is zero. Always returns true if the stamp represents a
     * currently held lock. Invoking this method with a value not
     * obtained from {@link #tryOptimisticRead} or a locking method
     * for this lock has no defined effect or result.
     *
     * @param stamp a stamp
     * @return {@code true} if the lock has not been exclusively acquired
     * since issuance of the given stamp; else false
     */
    public boolean validate(long stamp) {
        VarHandle.acquireFence();
        return (stamp & SBITS) == (state & SBITS);
    }

    /**
     * 返回 stamp
     * 释放写锁的时候 + WBIT
     * 获取写锁的时候 + WBIT
     *
     * Returns an unlocked state, incrementing the version and
     * avoiding special failure value 0L.
     *
     * @param s a write-locked state (or stamp)
     */
    private static long unlockWriteState(long s) {
        // state + 一个 WBIT
        return ((s += WBIT) == 0L) ? ORIGIN : s;
    }

    private long unlockWriteInternal(long s) {
        long next; WNode h;
        // 更新 state
        STATE.setVolatile(this, next = unlockWriteState(s));
        // 队头不为 null 且状态不为 0
        if ((h = whead) != null && h.status != 0)
            // 唤醒后继节点
            release(h);
        return next;
    }

    /**
     * stamp 匹配，则释放写锁
     *
     * If the lock state matches the given stamp, releases the
     * exclusive lock.
     *
     * @param stamp a stamp returned by a write-lock operation
     * @throws IllegalMonitorStateException if the stamp does
     * not match the current state of this lock
     */
    @ReservedStackAccess
    public void unlockWrite(long stamp) {
        // 不匹配 或者 未持有写锁
        if (state != stamp || (stamp & WBIT) == 0L)
            throw new IllegalMonitorStateException();
        unlockWriteInternal(stamp);
    }

    /**
     * 释放读锁
     *
     * If the lock state matches the given stamp, releases the
     * non-exclusive lock.
     *
     * @param stamp a stamp returned by a read-lock operation
     * @throws IllegalMonitorStateException if the stamp does
     * not match the current state of this lock
     */
    @ReservedStackAccess
    public void unlockRead(long stamp) {
        long s, m; WNode h;
        // 两者相等，也就是 stamp 验证通过
        while (((s = state) & SBITS) == (stamp & SBITS)
                // 读计数 > 0
               && (stamp & RBITS) > 0L
                // state > 0
               && ((m = s & RBITS) > 0L)) {
            // 读锁个数没有溢出
            if (m < RFULL) {
                // cas 更新 state - 1
                if (casState(s, s - RUNIT)) {
                    // 若读锁完全释放了 并且 头结点不为 null(有节点在等待) 并且头结点的状态不为 0
                    if (m == RUNIT && (h = whead) != null && h.status != 0)
                        // 唤醒后继节点
                        release(h);
                    return;
                }
            }
            // 溢出了
            else if (tryDecReaderOverflow(s) != 0L)
                return;
        }
        throw new IllegalMonitorStateException();
    }

    /**
     * 释放锁，根据 stamp
     *
     * If the lock state matches the given stamp, releases the
     * corresponding mode of the lock.
     *
     * @param stamp a stamp returned by a lock operation
     * @throws IllegalMonitorStateException if the stamp does
     * not match the current state of this lock
     */
    @ReservedStackAccess
    public void unlock(long stamp) {
        // 如果是写锁
        if ((stamp & WBIT) != 0L)
            // 释放写锁
            unlockWrite(stamp);
        else
            // 释放读锁
            unlockRead(stamp);
    }

    /**
     * 根据当前 stamp 的表示状态进行对应的操作，目标是将当前状态转为 write 锁状态下的 stamp
     *
     * If the lock state matches the given stamp, atomically performs one of
     * the following actions. If the stamp represents holding a write
     * lock, returns it.  Or, if a read lock, if the write lock is
     * available, releases the read lock and returns a write stamp.
     * Or, if an optimistic read, returns a write stamp only if
     * immediately available. This method returns zero in all other
     * cases.
     *
     * @param stamp a stamp
     * @return a valid write stamp, or zero on failure
     */
    public long tryConvertToWriteLock(long stamp) {
        // a 根据 stamp 检查是否持有锁
        long a = stamp & ABITS, m, s, next;
        // stamp 的状态与 state 的状态相同
        // 均为持有写锁或者读锁或者乐观读锁
        while (((s = state) & SBITS) == (stamp & SBITS)) {
            // state 表示的锁状态为 无锁 或者 乐观读锁
            if ((m = s & ABITS) == 0L) {
                // 传进来的 stamp 不是无锁或者乐观读锁状态
                if (a != 0L)
                    // 直接跳出，升级失败
                    break;
                // 获取写锁
                if ((next = tryWriteLock(s)) != 0L)
                    // 获取成功则返回 stamp
                    return next;
            }
            // 当前 state 表示持有写锁
            else if (m == WBIT) {
                // stamp 表示不持有写锁
                if (a != m)
                    // 直接退出，返回 0
                    break;
                // 两者都表示持有写锁，直接返回 stamp
                return stamp;
            }
            // state 表示持有一个读锁 且 传入的 stamp 也表示持有锁
            else if (m == RUNIT && a != 0L) {
                // cas 更新 state，释放读锁，获取写锁
                if (casState(s, next = s - RUNIT + WBIT)) {
                    VarHandle.storeStoreFence();
                    // 返回获取写锁后的 stamp
                    return next;
                }
            }
            // 其他条件一律返回 0
            else
                break;
        }
        return 0L;
    }

    /**
     * 转化成读锁
     *
     * If the lock state matches the given stamp, atomically performs one of
     * the following actions. If the stamp represents holding a write
     * lock, releases it and obtains a read lock.  Or, if a read lock,
     * returns it. Or, if an optimistic read, acquires a read lock and
     * returns a read stamp only if immediately available. This method
     * returns zero in all other cases.
     *
     * @param stamp a stamp
     * @return a valid read stamp, or zero on failure
     */
    public long tryConvertToReadLock(long stamp) {
        long a, s, next; WNode h;
        // state 与 stamp 的状态相同
        while (((s = state) & SBITS) == (stamp & SBITS)) {
            // 如果 stamp 表示持有写锁
            if ((a = stamp & ABITS) >= WBIT) {
                // write stamp
                // state 状态与 stamp 状态不同
                if (s != stamp)
                    // 直接跳出，转换失败
                    break;
                // 释放写锁并且获取读锁
                STATE.setVolatile(this, next = unlockWriteState(s) + RUNIT);
                // 如果有节点在等待，则唤醒在等待的所有节点
                if ((h = whead) != null && h.status != 0)
                    release(h);
                // 返回 stamp
                return next;
            }
            // stamp 表示无锁或者持有乐观读锁
            else if (a == 0L) {
                // optimistic read stamp
                // state 表示持有读锁或者无锁或者乐观读，且没有溢出
                if ((s & ABITS) < RFULL) {
                    // 更新 state 新增读锁
                    if (casState(s, next = s + RUNIT))
                        // 返回新的 stamp
                        return next;
                }
                // 溢出了，则调用 tryIncReaderOverflow 获取读锁
                else if ((next = tryIncReaderOverflow(s)) != 0L)
                    return next;
            }
            // 剩余的情况就是 stamp 表示持有读锁
            else {
                // already a read stamp
                // state 表示未持有锁，则跳出
                if ((s & ABITS) == 0L)
                    break;
                // 否则直接返回 stamp
                return stamp;
            }
        }
        return 0L;
    }

    /**
     * 转为乐观读
     *
     * If the lock state matches the given stamp then, atomically, if the stamp
     * represents holding a lock, releases it and returns an
     * observation stamp.  Or, if an optimistic read, returns it if
     * validated. This method returns zero in all other cases, and so
     * may be useful as a form of "tryUnlock".
     *
     * @param stamp a stamp
     * @return a valid optimistic read stamp, or zero on failure
     */
    public long tryConvertToOptimisticRead(long stamp) {
        long a, m, s, next; WNode h;
        VarHandle.acquireFence();
        // stamp 的状态与 state 的状态相同
        while (((s = state) & SBITS) == (stamp & SBITS)) {
            // stamp 表示持有写锁
            if ((a = stamp & ABITS) >= WBIT) {
                // write stamp
                // state 与 stamp 不相同
                if (s != stamp)
                    // 直接跳出
                    break;
                // 否则释放写锁
                return unlockWriteInternal(s);
            }
            // stamp 表示未持有锁或者持有乐观读锁
            else if (a == 0L)
                // already an optimistic read stamp
                // 直接返回该 stamp
                return stamp;
            // 剩余的情况就是 stamp 表示持有读锁
            // 如果 state 表示未持有读锁或者持有乐观读锁
            else if ((m = s & ABITS) == 0L) // invalid read stamp
                // 直接跳出
                break;
            // state 表示持有读锁
            else if (m < RFULL) {
                // 释放一个读锁
                if (casState(s, next = s - RUNIT)) {
                    // 如果完全释放了，并且队列中有在等待的节点
                    if (m == RUNIT && (h = whead) != null && h.status != 0)
                        // 唤醒后继节点
                        release(h);
                    // 一般情况下返回 1 00000000
                    return next & SBITS;
                }
            }
            // 读锁溢出
            else if ((next = tryDecReaderOverflow(s)) != 0L)
                // 一般情况下返回 1 00000000
                return next & SBITS;
        }
        return 0L;
    }

    /**
     * 无条件释放写锁
     *
     * Releases the write lock if it is held, without requiring a
     * stamp value. This method may be useful for recovery after
     * errors.
     *
     * @return {@code true} if the lock was held, else false
     */
    @ReservedStackAccess
    public boolean tryUnlockWrite() {
        long s;
        // 如果持有写锁
        if (((s = state) & WBIT) != 0L) {
            unlockWriteInternal(s);
            return true;
        }
        return false;
    }

    /**
     * 尝试释放读锁
     *
     * Releases one hold of the read lock if it is held, without
     * requiring a stamp value. This method may be useful for recovery
     * after errors.
     *
     * @return {@code true} if the read lock was held, else false
     */
    @ReservedStackAccess
    public boolean tryUnlockRead() {
        long s, m; WNode h;
        // 如果存在锁且不为写锁
        while ((m = (s = state) & ABITS) != 0L && m < WBIT) {
            // 是否溢出
            if (m < RFULL) {
                if (casState(s, s - RUNIT)) {
                    // 读锁完全释放了且队列不为 null
                    if (m == RUNIT && (h = whead) != null && h.status != 0)
                        // 通知后继节点
                        release(h);
                    return true;
                }
            }
            // 溢出处理
            else if (tryDecReaderOverflow(s) != 0L)
                return true;
        }
        return false;
    }

    // status monitoring methods

    /**
     * 最大的返回个数为 int 个
     *
     * Returns combined state-held and overflow read count for given
     * state s.
     */
    private int getReadLockCount(long s) {
        long readers;
        // 检查是否越界
        if ((readers = s & RBITS) >= RFULL)
            // 越界了则需要加上越界之后的技术
            readers = RFULL + readerOverflow;
        return (int) readers;
    }

    /**
     * 返回是否持有写锁
     *
     * Returns {@code true} if the lock is currently held exclusively.
     *
     * @return {@code true} if the lock is currently held exclusively
     */
    public boolean isWriteLocked() {
        return (state & WBIT) != 0L;
    }

    /**
     * 返回是否持有读锁
     *
     * Returns {@code true} if the lock is currently held non-exclusively.
     *
     * @return {@code true} if the lock is currently held non-exclusively
     */
    public boolean isReadLocked() {
        return (state & RBITS) != 0L;
    }

    /**
     * 返回传入的 stamp 是否表示当前持有写锁
     *
     * Tells whether a stamp represents holding a lock exclusively.
     * This method may be useful in conjunction with
     * {@link #tryConvertToWriteLock}, for example: <pre> {@code
     * long stamp = sl.tryOptimisticRead();
     * try {
     *   ...
     *   stamp = sl.tryConvertToWriteLock(stamp);
     *   ...
     * } finally {
     *   if (StampedLock.isWriteLockStamp(stamp))
     *     sl.unlockWrite(stamp);
     * }}</pre>
     *
     * @param stamp a stamp returned by a previous StampedLock operation
     * @return {@code true} if the stamp was returned by a successful
     *   write-lock operation
     * @since 10
     */
    public static boolean isWriteLockStamp(long stamp) {
        return (stamp & ABITS) == WBIT;
    }

    /**
     * 返回传入的 stamp 是否表示当前持有读锁
     *
     * Tells whether a stamp represents holding a lock non-exclusively.
     * This method may be useful in conjunction with
     * {@link #tryConvertToReadLock}, for example: <pre> {@code
     * long stamp = sl.tryOptimisticRead();
     * try {
     *   ...
     *   stamp = sl.tryConvertToReadLock(stamp);
     *   ...
     * } finally {
     *   if (StampedLock.isReadLockStamp(stamp))
     *     sl.unlockRead(stamp);
     * }}</pre>
     *
     * @param stamp a stamp returned by a previous StampedLock operation
     * @return {@code true} if the stamp was returned by a successful
     *   read-lock operation
     * @since 10
     */
    public static boolean isReadLockStamp(long stamp) {
        return (stamp & RBITS) != 0L;
    }

    /**
     * 返回传入的 stamp 是否表示持有锁
     *
     * Tells whether a stamp represents holding a lock.
     * This method may be useful in conjunction with
     * {@link #tryConvertToReadLock} and {@link #tryConvertToWriteLock},
     * for example: <pre> {@code
     * long stamp = sl.tryOptimisticRead();
     * try {
     *   ...
     *   stamp = sl.tryConvertToReadLock(stamp);
     *   ...
     *   stamp = sl.tryConvertToWriteLock(stamp);
     *   ...
     * } finally {
     *   if (StampedLock.isLockStamp(stamp))
     *     sl.unlock(stamp);
     * }}</pre>
     *
     * @param stamp a stamp returned by a previous StampedLock operation
     * @return {@code true} if the stamp was returned by a successful
     *   read-lock or write-lock operation
     * @since 10
     */
    public static boolean isLockStamp(long stamp) {
        return (stamp & ABITS) != 0L;
    }

    /**
     * 返回传入的 stamp 是否表示处于乐观读的 stamp
     * 也就是当前的 stamp 为 1 00000000
     *
     * Tells whether a stamp represents a successful optimistic read.
     *
     * @param stamp a stamp returned by a previous StampedLock operation
     * @return {@code true} if the stamp was returned by a successful
     *   optimistic read operation, that is, a non-zero return from
     *   {@link #tryOptimisticRead()} or
     *   {@link #tryConvertToOptimisticRead(long)}
     * @since 10
     */
    public static boolean isOptimisticReadStamp(long stamp) {
        return (stamp & ABITS) == 0L && stamp != 0L;
    }

    /**
     * 返回读锁的持有个数
     *
     * Queries the number of read locks held for this lock. This
     * method is designed for use in monitoring system state, not for
     * synchronization control.
     * @return the number of read locks held
     */
    public int getReadLockCount() {
        return getReadLockCount(state);
    }

    /**
     * Returns a string identifying this lock, as well as its lock
     * state.  The state, in brackets, includes the String {@code
     * "Unlocked"} or the String {@code "Write-locked"} or the String
     * {@code "Read-locks:"} followed by the current number of
     * read-locks held.
     *
     * @return a string identifying this lock, as well as its lock state
     */
    public String toString() {
        long s = state;
        return super.toString() +
            ((s & ABITS) == 0L ? "[Unlocked]" :
             (s & WBIT) != 0L ? "[Write-locked]" :
             "[Read-locks:" + getReadLockCount(s) + "]");
    }

    // views

    /**
     * 返回当前对象持有的 readLockView，如果未持有，则创建一个
     *
     * Returns a plain {@link Lock} view of this StampedLock in which
     * the {@link Lock#lock} method is mapped to {@link #readLock},
     * and similarly for other methods. The returned Lock does not
     * support a {@link Condition}; method {@link Lock#newCondition()}
     * throws {@code UnsupportedOperationException}.
     *
     * @return the lock
     */
    public Lock asReadLock() {
        ReadLockView v;
        if ((v = readLockView) != null) return v;
        return readLockView = new ReadLockView();
    }

    /**
     * 返回当前对象持有的写锁，如果未持有，则创建
     *
     * Returns a plain {@link Lock} view of this StampedLock in which
     * the {@link Lock#lock} method is mapped to {@link #writeLock},
     * and similarly for other methods. The returned Lock does not
     * support a {@link Condition}; method {@link Lock#newCondition()}
     * throws {@code UnsupportedOperationException}.
     *
     * @return the lock
     */
    public Lock asWriteLock() {
        WriteLockView v;
        if ((v = writeLockView) != null) return v;
        return writeLockView = new WriteLockView();
    }

    /**
     * 返回当前对象持有的读写锁，如果未持有，则创建
     *
     * Returns a {@link ReadWriteLock} view of this StampedLock in
     * which the {@link ReadWriteLock#readLock()} method is mapped to
     * {@link #asReadLock()}, and {@link ReadWriteLock#writeLock()} to
     * {@link #asWriteLock()}.
     *
     * @return the lock
     */
    public ReadWriteLock asReadWriteLock() {
        ReadWriteLockView v;
        if ((v = readWriteLockView) != null) return v;
        return readWriteLockView = new ReadWriteLockView();
    }

    // view classes
    // 暴露给外部的 API

    /**
     * 读锁视图
     */
    final class ReadLockView implements Lock {
        /**
         * 调用内部的 readLock
         */
        public void lock() { readLock(); }

        /**
         * 调用内部的 readLockInterruptibly
         *
         * @throws InterruptedException
         */
        public void lockInterruptibly() throws InterruptedException {
            readLockInterruptibly();
        }

        /**
         * 调用内部的 tryReadLock
         * 一次性尝试获取，获取失败则直接返回
         *
         * @return 是否获取成功
         */
        public boolean tryLock() { return tryReadLock() != 0L; }

        /**
         * 带超时时间的一次性获取读锁
         * TODO
         * 如果在时间内未能成功获取到锁，则阻塞，超时还未获取到则取消获取
         *
         * @param time 如果小于等于 0，则仅尝试一次
         * @param unit
         * @return 是否获取成功
         * @throws InterruptedException
         */
        public boolean tryLock(long time, TimeUnit unit)
            throws InterruptedException {
            return tryReadLock(time, unit) != 0L;
        }

        /**
         * 无条件释放读锁
         */
        public void unlock() { unstampedUnlockRead(); }

        /**
         * 不支持返回条件等待队列
         *
         * @return 不支持
         */
        public Condition newCondition() {
            throw new UnsupportedOperationException();
        }
    }

    /**
     * 写锁视图，暴露给外部的 API
     */
    final class WriteLockView implements Lock {
        /**
         * 获取写锁，获取不到则阻塞
         */
        public void lock() { writeLock(); }

        /**
         * 获取写锁，获取不到则阻塞，能够响应中断
         *
         * @throws InterruptedException
         */
        public void lockInterruptibly() throws InterruptedException {
            writeLockInterruptibly();
        }

        /**
         * 一次性获取写锁
         *
         * @return 是否获取成功
         */
        public boolean tryLock() { return tryWriteLock() != 0L; }

        /**
         * 一次性获取写锁，带超时时间，超时时间内阻塞，超时后取消获取
         *
         * @param time
         * @param unit
         * @return 返回是否获取成功
         * @throws InterruptedException
         */
        public boolean tryLock(long time, TimeUnit unit)
            throws InterruptedException {
            return tryWriteLock(time, unit) != 0L;
        }

        /**
         * 无条件释放写锁
         */
        public void unlock() { unstampedUnlockWrite(); }

        /**
         * 不支持返回条件等待队列
         *
         * @return 不支持
         */
        public Condition newCondition() {
            throw new UnsupportedOperationException();
        }
    }

    /**
     * 读写锁视图
     * 暴露到外部的 API
     */
    final class ReadWriteLockView implements ReadWriteLock {
        /**
         * @return 返回读锁
         */
        public Lock readLock() { return asReadLock(); }

        /**
         * @return 返回写锁
         */
        public Lock writeLock() { return asWriteLock(); }
    }

    // Unlock methods without stamp argument checks for view classes.
    // Needed because view-class lock methods throw away stamps.

    /**
     * 无条件释放写锁，不考虑 stamp
     */
    final void unstampedUnlockWrite() {
        long s;
        if (((s = state) & WBIT) == 0L)
            throw new IllegalMonitorStateException();
        unlockWriteInternal(s);
    }

    /**
     * 不用 stamp 的释放读锁
     * 可以理解为无条件释放读锁
     */
    final void unstampedUnlockRead() {
        long s, m; WNode h;
        // 返回读锁持有的个数
        while ((m = (s = state) & RBITS) > 0L) {
            // 未满则更新 state
            if (m < RFULL) {
                // 释放一个
                if (casState(s, s - RUNIT)) {
                    // 如果是完全释放了 并且 队列中有节点在等待 且 等待节点的状态不为 0
                    if (m == RUNIT && (h = whead) != null && h.status != 0)
                        release(h);
                    return;
                }
            }
            // state 中记录的读锁个数满了
            // 调用 tryDecReaderOverflow 进行释放
            else if (tryDecReaderOverflow(s) != 0L)
                return;
        }
        throw new IllegalMonitorStateException();
    }

    private void readObject(java.io.ObjectInputStream s)
        throws java.io.IOException, ClassNotFoundException {
        s.defaultReadObject();
        STATE.setVolatile(this, ORIGIN); // reset to unlocked state
    }

    // internals

    /**
     * 溢出之后读锁的获取
     *
     * Tries to increment readerOverflow by first setting state
     * access bits value to RBITS, indicating hold of spinlock,
     * then updating, then releasing.
     *
     * @param s a reader overflow stamp: (s & ABITS) >= RFULL
     * @return new stamp on success, else zero
     */
    private long tryIncReaderOverflow(long s) {
        // assert (s & ABITS) >= RFULL;
        // 最低位为 0
        if ((s & ABITS) == RFULL) {
            // 最低位也尝试改为 1
            if (casState(s, s | RBITS)) {
                // readerOverflow 也更新为 +1
                ++readerOverflow;
                STATE.setVolatile(this, s);
                return s;
            }
        }
        // 下面根据规则调用 yeild 或者 onSpinWait
        else if ((LockSupport.nextSecondarySeed() & OVERFLOW_YIELD_RATE) == 0)
            Thread.yield();
        else
            Thread.onSpinWait();
        return 0L;
    }

    /**
     * 在溢出的保存变量中读计数 -1
     *
     * Tries to decrement readerOverflow.
     *
     * @param s a reader overflow stamp: (s & ABITS) >= RFULL
     * @return new stamp on success, else zero
     */
    private long tryDecReaderOverflow(long s) {
        // assert (s & ABITS) >= RFULL;
        // 确实是满的
        if ((s & ABITS) == RFULL) {
            if (casState(s, s | RBITS)) {
                int r; long next;
                // 还有溢出
                if ((r = readerOverflow) > 0) {
                    // 溢出 - 1
                    readerOverflow = r - 1;
                    next = s;
                }
                // 溢出的计数已经全部释放了
                else
                    // 更新 state 了
                    next = s - RUNIT;
                STATE.setVolatile(this, next);
                return next;
            }
        }
        else if ((LockSupport.nextSecondarySeed() & OVERFLOW_YIELD_RATE) == 0)
            Thread.yield();
        else
            Thread.onSpinWait();
        return 0L;
    }

    /**
     * 唤醒 h 节点的后继节点
     *
     * Wakes up the successor of h (normally whead). This is normally
     * just h.next, but may require traversal from wtail if next
     * pointers are lagging. This may fail to wake up an acquiring
     * thread when one or more have been cancelled, but the cancel
     * methods themselves provide extra safeguards to ensure liveness.
     */
    private void release(WNode h) {
        if (h != null) {
            WNode q; Thread w;
            // 将 h 的 status 从 WAITING 改为 0
            WSTATUS.compareAndSet(h, WAITING, 0);
            // q 为 h 的后继节点
            if ((q = h.next) == null || q.status == CANCELLED) {
                // 目标是往后找到一个有效的节点
                // 从 tail 往前找
                for (WNode t = wtail; t != null && t != h; t = t.prev)
                    if (t.status <= 0)
                        q = t;
            }
            if (q != null && (w = q.thread) != null)
                // 唤醒
                LockSupport.unpark(w);
        }
    }

    /**
     * 获取写锁
     *
     * See above for explanation.
     *
     * @param interruptible true if should check interrupts and if so
     * return INTERRUPTED 是否能够响应中断
     * @param deadline if nonzero, the System.nanoTime value to timeout
     * at (and return zero)
     * @return next state, or INTERRUPTED
     */
    private long acquireWrite(boolean interruptible, long deadline) {
        WNode node = null, p;
        // 自旋
        // 1. 先尝试获取锁
        // 2. 获取不到则入队
        for (int spins = -1;;) { // spin while enqueuing
            long m, s, ns;
            // 未持有锁
            if ((m = (s = state) & ABITS) == 0L) {
                // 尝试获取写锁
                if ((ns = tryWriteLock(s)) != 0L)
                    // 获取到了返回 stamp
                    return ns;
            }
            // 检查自旋次数
            else if (spins < 0)
                //  更新自旋的次数
                spins = (m == WBIT && wtail == whead) ? SPINS : 0;
            else if (spins > 0) {
                --spins;
                // 自旋一会儿
                Thread.onSpinWait();
            }
            // 自旋也自旋完了
            // 队列是否已经初始化
            else if ((p = wtail) == null) { // initialize queue
                // 创建一个写模式下等待的节点
                // dummy head
                WNode hd = new WNode(WMODE, null);
                // cas 更新 whead 引用
                if (WHEAD.weakCompareAndSet(this, null, hd))
                    // 尾引用也指向它
                    wtail = hd;
            }
            // 节点未初始化
            else if (node == null)
                // 创建一个 WMODE 节点
                node = new WNode(WMODE, p);
            // 分两步走
            // 初始化前驱结点，当前节点的前驱节点指向尾节点
            else if (node.prev != p)
                // 指向前驱节点
                node.prev = p;
            // cas 更新 wtail 引用，node 节点入队
            else if (WTAIL.weakCompareAndSet(this, p, node)) {
                p.next = node;
                break;
            }
        }

        // 中断标识
        boolean wasInterrupted = false;
        // 自旋
        for (int spins = -1;;) {
            WNode h, np, pp; int ps;
            // 头尾相等
            // 说明快可以获取到锁了
            if ((h = whead) == p) {
                // 更新自旋次数
                if (spins < 0)
                    spins = HEAD_SPINS;
                else if (spins < MAX_HEAD_SPINS)
                    spins <<= 1;
                // 在 head 上自旋
                for (int k = spins; k > 0; --k) { // spin at head
                    long s, ns;
                    // 如果锁被完全释放了
                    if (((s = state) & ABITS) == 0L) {
                        // 尝试获取
                        if ((ns = tryWriteLock(s)) != 0L) {
                            // 获取成功
                            whead = node;
                            node.prev = null;
                            // 如果有被中断
                            if (wasInterrupted)
                                // 再次自我中断
                                Thread.currentThread().interrupt();
                            // 返回 stamp
                            return ns;
                        }
                    }
                    else
                        // 自旋
                        Thread.onSpinWait();
                }
            }
            // whead 不为空
            else if (h != null) { // help release stale waiters
                WNode c; Thread w;
                // whead group 上的 cowait 全部唤醒
                while ((c = h.cowait) != null) {
                    if (WCOWAIT.weakCompareAndSet(h, c, c.cowait) &&
                        (w = c.thread) != null)
                        LockSupport.unpark(w);
                }
            }
            // 头结点未改变
            if (whead == h) {
                // 可能有其他线程先抢占了
                // 如果尾节点有变化
                if ((np = node.prev) != p) {
                    if (np != null)
                        (p = np).next = node;   // stale
                }
                // 尾节点的状态如果为 0
                else if ((ps = p.status) == 0)
                    // 将尾节点的状态改为 WAITING
                    WSTATUS.compareAndSet(p, 0, WAITING);
                // 如果尾节点的状态为 cancelled
                else if (ps == CANCELLED) {
                    // 更新 node 的 pre 为 p 的 pre
                    if ((pp = p.prev) != null) {
                        node.prev = pp;
                        pp.next = node;
                    }
                }
                else {
                    long time; // 0 argument to park means no timeout
                    // 不超时控制
                    if (deadline == 0L)
                        time = 0L;
                    // 计算剩余时间
                    else if ((time = deadline - System.nanoTime()) <= 0L)
                        // 如果过了，则取消获取
                        return cancelWaiter(node, node, false);
                    Thread wt = Thread.currentThread();
                    node.thread = wt;
                    // p 的状态为 WAITING
                    // 头尾节点不是同一个节点或者目前持有锁
                    if (p.status < 0 && (p != h || (state & ABITS) != 0L) &&
                            // head 没变且 node 的前驱节点与没变
                        whead == h && node.prev == p) {
                        if (time == 0L)
                            // 阻塞
                            LockSupport.park(this);
                        else
                            // 超时阻塞
                            LockSupport.parkNanos(this, time);
                    }
                    node.thread = null;
                    if (Thread.interrupted()) {
                        // 是否响应中断
                        if (interruptible)
                            return cancelWaiter(node, node, true);
                        wasInterrupted = true;
                    }
                }
            }
        }
    }

    /**
     * 进入该方法时因为之前有一次 cas 获取锁失败了
     * 读锁最重要的方法
     *
     * See above for explanation.
     *
     * @param interruptible true if should check interrupts and if so
     * return INTERRUPTED 是否支持响应中断
     * @param deadline if nonzero, the System.nanoTime value to timeout
     * at (and return zero) 是否支持超时，<=0 表示不
     * @return next state, or INTERRUPTED
     */
    private long acquireRead(boolean interruptible, long deadline) {
        boolean wasInterrupted = false;
        WNode node = null, p;
        // 外层自旋
        for (int spins = -1;;) {
            WNode h;
            // 如果队列中没有等待的节点，马上要轮到自己了
            // 自旋就完事了，不需要阻塞
            if ((h = whead) == (p = wtail)) {
                // 内层自旋，尝试获取读锁
                for (long m, s, ns;;) {
                    // m = (s = state) & ABITS) 获取锁状态，判断目前读锁是否已满或者是否持有的为写锁
                    if ((m = (s = state) & ABITS) < RFULL ?
                            // cas 尝试获取读锁
                        casState(s, ns = s + RUNIT) :
                            // 持有的不为写锁 且
                            // state 中读锁已经存满了
                            // 则调用 tryIncReaderOverflow 来获取读锁
                        (m < WBIT && (ns = tryIncReaderOverflow(s)) != 0L)) {
                        // 检测中断
                        if (wasInterrupted)
                            // 中断当前线程
                            // 仅仅是将内部的中断通知到外部
                            // 具体如果处理由外部决定
                            Thread.currentThread().interrupt();
                        // 获取成功返回 stamp
                        return ns;
                    }
                    // 如果持有的是写锁
                    else if (m >= WBIT) {
                        // 自旋次数
                        if (spins > 0) {
                            --spins;
                            // 暂时放弃 CPU 调度
                            Thread.onSpinWait();
                        }
                        // 自旋剩余次数 <= 0
                        else {
                            if (spins == 0) {
                                WNode nh = whead, np = wtail;
                                // 如果头尾节点没变，说明持有的写锁还没释放，也去排队吧
                                // 头尾节点不再相等，说明有读节点入队了，没必要再自旋下去了，老老实实去排队吧
                                // 跳出内部自旋
                                if ((nh == h && np == p) || (h = nh) != (p = np))
                                    break;
                            }
                            spins = SPINS;
                        }
                    }
                }
            }
            // 经过上面自旋之后还未成功获取到读锁
            // p == null 说明持有的是写锁，并且队列还未初始化
            if (p == null) { // initialize queue
                // 初始化队列，构造头结点
                // 头结点表示当前持有锁的节点类型是 WMODE
                WNode hd = new WNode(WMODE, null);
                // 更新头尾节点引用
                if (WHEAD.weakCompareAndSet(this, null, hd))
                    wtail = hd;
            }
            // 当前线程的节点如果还未创建，则创建节点，为 RMODE
            else if (node == null)
                // prev 指向 tail
                node = new WNode(RMODE, p);
            // 如果头尾节点相等 或者 尾节点的不为 RMODE 节点
            // 入队，入队分了两步来做
            else if (h == p || p.mode != RMODE) {
                // 入队
                if (node.prev != p)
                    node.prev = p;
                // 更新 tail 指向 node
                else if (WTAIL.weakCompareAndSet(this, p, node)) {
                    // 原来的 tail 的 next 指向 node
                    p.next = node;
                    // 跳出外部自旋
                    break;
                }
            }
            // 尾节点肯定是读模式的节点
            // 将当前节点加入到尾节点的 cowait 队列中，头插法
            else if (!WCOWAIT.compareAndSet(p, node.cowait = p.cowait, node))
                node.cowait = null;
            // 头尾节点不相等，尾节点等待模式为读锁模式，并且当前节点 cas 加入尾节点的 cowait 队列中失败
            // 也就是说，当前持有写锁，且有读线程已经进入到了队列中等待
            else {
                // 自旋
                for (;;) {
                    WNode pp, c; Thread w;
                    // 头结点不为 null
                    // 头结点的 cowait 不为 null
                    // cas 成功将头节点的 cowait 指向节点 cowait 链表中的下一个节点，c 为 头节点的 cowait 节点
                    // 如果 c 的绑定线程不为 null
                    if ((h = whead) != null && (c = h.cowait) != null &&
                        WCOWAIT.compareAndSet(h, c, c.cowait) &&
                        (w = c.thread) != null) // help release
                        // 唤醒节点 c 绑定的线程
                        LockSupport.unpark(w);
                    // 检查中断
                    if (Thread.interrupted()) {
                        // 如果处理中断
                        if (interruptible)
                            // 取消获取读锁
                            return cancelWaiter(node, p, true);
                        // 记录内部是否发生中断
                        wasInterrupted = true;
                    }
                    // 尾节点的前驱节点就是头节点
                    // 头尾相等
                    // 尾节点的前驱节点为 null
                    // 说明快要可以获取到锁了
                    if (h == (pp = p.prev) || h == p || pp == null) {
                        long m, s, ns;
                        // 先尝试一次获取
                        do {
                            // 未持有写锁且读锁未满
                            if ((m = (s = state) & ABITS) < RFULL ?
                                    // cas 获取读锁
                                casState(s, ns = s + RUNIT) :
                                    // 未持有读锁，但是 state 维护的读锁个数满了
                                (m < WBIT &&
                                        // overflow 中获取读锁
                                 (ns = tryIncReaderOverflow(s)) != 0L)) {
                                // 记录中断
                                if (wasInterrupted)
                                    Thread.currentThread().interrupt();
                                return ns;
                            }
                            // 为持有写锁的情况下继续循环
                        } while (m < WBIT);
                    }
                    // 头尾节点情况未改变
                    if (whead == h && p.prev == pp) {
                        long time;
                        if (pp == null || h == p || p.status > 0) {
                            node = null; // throw away
                            break;
                        }
                        // 是否超时获取
                        if (deadline == 0L)
                            time = 0L;
                        // 计算剩余时间
                        else if ((time = deadline - System.nanoTime()) <= 0L) {
                            // 已经超时
                            if (wasInterrupted)
                                Thread.currentThread().interrupt();
                            // 取消获取
                            return cancelWaiter(node, p, false);
                        }
                        // 当前线程
                        Thread wt = Thread.currentThread();
                        node.thread = wt;
                        // 尾节点的前驱节点不为 head 节点，说明等待时间还要挺长。或者 持有写锁
                        // 头尾节点的引用未变化
                        if ((h != pp || (state & ABITS) == WBIT) &&
                            whead == h && p.prev == pp) {
                            // 不带超时时间
                            if (time == 0L)
                                // 直接阻塞
                                LockSupport.park(this);
                            else
                                // 带超时时间
                            // 阻塞剩余的超时时间
                                LockSupport.parkNanos(this, time);
                        }
                        // thread 置为 null
                        node.thread = null;
                    }
                }
            }
        }

        // 只有第一个读线程会走到下面的 for 循环处，并且该线程已经入队，是一个 group 中的首节点
        // 外部自旋
        for (int spins = -1;;) {
            WNode h, np, pp; int ps;
            // 头尾相等，说明快可以获取到锁了
            if ((h = whead) == p) {
                // 第一次需要设置自旋次数
                if (spins < 0)
                    spins = HEAD_SPINS;
                else if (spins < MAX_HEAD_SPINS)
                    // 每次左移 1 位，也就是 * 2
                    spins <<= 1;
                // 内部自旋
                for (int k = spins;;) { // spin at head
                    long m, s, ns;
                    // 尝试获取读锁
                    if ((m = (s = state) & ABITS) < RFULL ?
                        casState(s, ns = s + RUNIT) :
                        (m < WBIT && (ns = tryIncReaderOverflow(s)) != 0L)) {
                        // 获取读锁成功
                        WNode c; Thread w;
                        whead = node;
                        node.prev = null;
                        // 遍历该节点后续的 cowait 节点
                        while ((c = node.cowait) != null) {
                            // 将 node 节点的 cowait 引用往后移
                            if (WCOWAIT.compareAndSet(node, c, c.cowait) &&
                                    // 当前 cowait 的 thread 不为 null
                                (w = c.thread) != null)
                                // 唤醒当前 cowait 节点
                                LockSupport.unpark(w);
                        }
                        // 如果被中断过
                        if (wasInterrupted)
                            // 发起中断
                            Thread.currentThread().interrupt();
                        // 返回版本号
                        return ns;
                    }
                    // 上面获取失败了
                    // 如果当前持有写锁且自旋次数也到了
                    else if (m >= WBIT && --k <= 0)
                        // 跳出
                        break;
                    else
                        // 自旋
                        Thread.onSpinWait();
                }
            }
            // 头结点存在，且不等于尾节点
            else if (h != null) {
                WNode c; Thread w;
                // 头结点跟着的 cowait 节点
                while ((c = h.cowait) != null) {
                    // 指向下一个 cowait 节点
                    if (WCOWAIT.compareAndSet(h, c, c.cowait) &&
                            // cowait 节点绑定的线程不为 null
                        (w = c.thread) != null)
                        // 对该 cowait 节点进行唤醒
                        LockSupport.unpark(w);
                }
            }
            // 头结点未改变
            if (whead == h) {
                // node 刚入队时， node 的 pre 指向 p
                // node 的 pre 不再指向 p
                if ((np = node.prev) != p) {
                    // 更新 node 的前置节点
                    if (np != null)
                        (p = np).next = node;   // stale
                }
                // 前置节点的状态为 0
                else if ((ps = p.status) == 0)
                    WSTATUS.compareAndSet(p, 0, WAITING);
                // 前置节点如果 cancelled 了
                else if (ps == CANCELLED) {
                    // node 的 pre 更新为 pp
                    if ((pp = p.prev) != null) {
                        node.prev = pp;
                        pp.next = node;
                    }
                }
                else {
                    long time;
                    if (deadline == 0L)
                        // 不设置超时时间
                        time = 0L;
                    // 剩余时间
                    else if ((time = deadline - System.nanoTime()) <= 0L)
                        return cancelWaiter(node, node, false);
                    Thread wt = Thread.currentThread();
                    node.thread = wt;
                    if (p.status < 0 &&
                        (p != h || (state & ABITS) == WBIT) &&
                        whead == h && node.prev == p) {
                            if (time == 0L)
                                // 无限 park
                                LockSupport.park(this);
                            else
                                // 带超时时间的 park
                                LockSupport.parkNanos(this, time);
                    }
                    node.thread = null;
                    if (Thread.interrupted()) {
                        if (interruptible)
                            return cancelWaiter(node, node, true);
                        wasInterrupted = true;
                    }
                }
            }
        }
    }

    /**
     * 取消当前节点
     * 唤醒在该节点上的 cowait 节点
     *
     * If node non-null, forces cancel status and unsplices it from
     * queue if possible and wakes up any cowaiters (of the node, or
     * group, as applicable), and in any case helps release current
     * first waiter if lock is free. (Calling with null arguments
     * serves as a conditional form of release, which is not currently
     * needed but may be needed under possible future cancellation
     * policies). This is a variant of cancellation methods in
     * AbstractQueuedSynchronizer (see its detailed explanation in AQS
     * internal documentation).
     *
     * @param node if non-null, the waiter 当前要取消的节点
     * @param group either node or the group node is cowaiting with
     * @param interrupted if already interrupted 是否中断引起的 cancel
     * @return INTERRUPTED if interrupted or Thread.interrupted, else zero
     */
    private long cancelWaiter(WNode node, WNode group, boolean interrupted) {
        if (node != null && group != null) {
            Thread w;
            // 将 node 的状态置为 cancellled
            node.status = CANCELLED;
            // unsplice cancelled nodes from group
            // 移除 group 中所有为 cancelled 的节点
            // 清理该 group 中的 cancelled 节点
            for (WNode p = group, q; (q = p.cowait) != null;) {
                // 如果 q 的状态的 cancelled
                if (q.status == CANCELLED) {
                    // 指向下一个
                    WCOWAIT.compareAndSet(p, q, q.cowait);
                    // 从头开始
                    p = group; // restart
                }
                else
                    // 往后移动
                    p = q;
            }
            // 如果 group 与 node 为统一节点
            // 如果要 cancel 的节点不是该 group 的节点首节点，那么就不走下面这个 if 逻辑
            if (group == node) {
                // 往后遍历，唤醒其后面的所有等待节点
                for (WNode r = group.cowait; r != null; r = r.cowait) {
                    if ((w = r.thread) != null)
                        LockSupport.unpark(w); // wake up uncancelled co-waiters
                }
                // 循环中做的事情就是找到合适的 node 节点的前驱节点和后继节点
                // 在大队列中 node 节点的前驱节点
                // 找到一个合适的前驱节点
                // 合适的定义：
                // 1. 前驱节点不为 cancelled
                // 2. 前驱为 cancelled 的并且前驱节点是头结点
                for (WNode pred = node.prev; pred != null; ) { // unsplice
                    // succ 后继节点
                    // pp 前驱的前驱节点
                    WNode succ, pp;        // find valid successor
                    // 这个 while 循环就是要将 node 的 pre 指向 node 的下一个有效节点
                    // node 节点往后找有效的后继
                    while ((succ = node.next) == null ||
                           succ.status == CANCELLED) {
                        // node 后继中有效的节点
                        WNode q = null;    // find successor the slow way
                        // 从队尾往前找
                        for (WNode t = wtail; t != null && t != node; t = t.prev)
                            // 如果不是 cancelled 的
                            if (t.status != CANCELLED)
                                // 作为候选
                                q = t;     // don't link if succ cancelled

                        // succ == q 说明 succ = q = null
                        if (succ == q ||   // ensure accurate successor
                                // 如果不为 null 则通过 cas 更新 node 的 next 为找到的有效的节点，也即 q
                            WNEXT.compareAndSet(node, succ, succ = q)) {
                            // 如果 node 为 tail 了
                            if (succ == null && node == wtail)
                                // 更新 wtail 引用，指向 node 的 pred
                                WTAIL.compareAndSet(this, node, pred);
                            break;
                        }
                    }
                    // 说明没有进入上面的 while 循环
                    // node 的 next 为有效的节点
                    if (pred.next == node) // unsplice pred link
                        // 直接将 node 的 pred 的 next 指向 node 的后继 succ
                        WNEXT.compareAndSet(pred, node, succ);
                    // 后继如果不为 null，且绑定的线程不为 null
                    if (succ != null && (w = succ.thread) != null) {
                        // wake up succ to observe new pred
                        succ.thread = null;
                        // 唤醒它
                        LockSupport.unpark(w);
                    }
                    // 前驱不为 cancelled 或者 前驱的前驱节点为 null
                    if (pred.status != CANCELLED || (pp = pred.prev) == null)
                        break;
                    // 前驱节点不满足条件，继续往前找
                    // node 的前驱指向 pp
                    node.prev = pp;        // repeat if new pred wrong/cancelled
                    // 更新 pp 的 next 为 succ
                    WNEXT.compareAndSet(pp, pred, succ);
                    // 前驱往前移
                    pred = pp;
                }
            }
        }
        WNode h; // Possibly release first waiter
        // 头结点不为空
        while ((h = whead) != null) {
            long s; WNode q; // similar to release() but check eligibility
            // 如果仅仅只有 head 一个 group 了 或者 head 之后的节点的 group 的首节点 cancelled 了
            if ((q = h.next) == null || q.status == CANCELLED) {
                // 从 wtail 往前找
                for (WNode t = wtail; t != null && t != h; t = t.prev)
                    // <= 0 说明未被 cancelled
                    if (t.status <= 0)
                        q = t;
            }
            // head 未被改变
            if (h == whead) {
                if (q != null && h.status == 0 &&
                    ((s = state) & ABITS) != WBIT && // waiter is eligible
                    (s == 0L || q.mode == RMODE))
                    // 唤醒 h 节点的下一有效节点
                    release(h);
                break;
            }
        }
        // 返回是否中断
        return (interrupted || Thread.interrupted()) ? INTERRUPTED : 0L;
    }

    // VarHandle mechanics
    private static final VarHandle STATE;
    private static final VarHandle WHEAD;
    private static final VarHandle WTAIL;
    private static final VarHandle WNEXT;
    private static final VarHandle WSTATUS;
    private static final VarHandle WCOWAIT;
    static {
        try {
            MethodHandles.Lookup l = MethodHandles.lookup();
            STATE = l.findVarHandle(StampedLock.class, "state", long.class);
            WHEAD = l.findVarHandle(StampedLock.class, "whead", WNode.class);
            WTAIL = l.findVarHandle(StampedLock.class, "wtail", WNode.class);
            WSTATUS = l.findVarHandle(WNode.class, "status", int.class);
            WNEXT = l.findVarHandle(WNode.class, "next", WNode.class);
            WCOWAIT = l.findVarHandle(WNode.class, "cowait", WNode.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}
