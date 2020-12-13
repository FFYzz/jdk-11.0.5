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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * Provides a framework for implementing blocking locks and related
 * synchronizers (semaphores, events, etc) that rely on
 * first-in-first-out (FIFO) wait queues.  This class is designed to
 * be a useful basis for most kinds of synchronizers that rely on a
 * single atomic {@code int} value to represent state. Subclasses
 * must define the protected methods that change this state, and which
 * define what that state means in terms of this object being acquired
 * or released.  Given these, the other methods in this class carry
 * out all queuing and blocking mechanics. Subclasses can maintain
 * other state fields, but only the atomically updated {@code int}
 * value manipulated using methods {@link #getState}, {@link
 * #setState} and {@link #compareAndSetState} is tracked with respect
 * to synchronization.
 *
 * <p>Subclasses should be defined as non-public internal helper
 * classes that are used to implement the synchronization properties
 * of their enclosing class.  Class
 * {@code AbstractQueuedSynchronizer} does not implement any
 * synchronization interface.  Instead it defines methods such as
 * {@link #acquireInterruptibly} that can be invoked as
 * appropriate by concrete locks and related synchronizers to
 * implement their public methods.
 *
 * <p>This class supports either or both a default <em>exclusive</em>
 * mode and a <em>shared</em> mode. When acquired in exclusive mode,
 * attempted acquires by other threads cannot succeed. Shared mode
 * acquires by multiple threads may (but need not) succeed. This class
 * does not &quot;understand&quot; these differences except in the
 * mechanical sense that when a shared mode acquire succeeds, the next
 * waiting thread (if one exists) must also determine whether it can
 * acquire as well. Threads waiting in the different modes share the
 * same FIFO queue. Usually, implementation subclasses support only
 * one of these modes, but both can come into play for example in a
 * {@link ReadWriteLock}. Subclasses that support only exclusive or
 * only shared modes need not define the methods supporting the unused mode.
 *
 * <p>This class defines a nested {@link ConditionObject} class that
 * can be used as a {@link Condition} implementation by subclasses
 * supporting exclusive mode for which method {@link
 * #isHeldExclusively} reports whether synchronization is exclusively
 * held with respect to the current thread, method {@link #release}
 * invoked with the current {@link #getState} value fully releases
 * this object, and {@link #acquire}, given this saved state value,
 * eventually restores this object to its previous acquired state.  No
 * {@code AbstractQueuedSynchronizer} method otherwise creates such a
 * condition, so if this constraint cannot be met, do not use it.  The
 * behavior of {@link ConditionObject} depends of course on the
 * semantics of its synchronizer implementation.
 *
 * <p>This class provides inspection, instrumentation, and monitoring
 * methods for the internal queue, as well as similar methods for
 * condition objects. These can be exported as desired into classes
 * using an {@code AbstractQueuedSynchronizer} for their
 * synchronization mechanics.
 *
 * <p>Serialization of this class stores only the underlying atomic
 * integer maintaining state, so deserialized objects have empty
 * thread queues. Typical subclasses requiring serializability will
 * define a {@code readObject} method that restores this to a known
 * initial state upon deserialization.
 *
 * <h3>Usage</h3>
 *
 * <p>To use this class as the basis of a synchronizer, redefine the
 * following methods, as applicable, by inspecting and/or modifying
 * the synchronization state using {@link #getState}, {@link
 * #setState} and/or {@link #compareAndSetState}:
 *
 * <ul>
 * <li>{@link #tryAcquire}
 * <li>{@link #tryRelease}
 * <li>{@link #tryAcquireShared}
 * <li>{@link #tryReleaseShared}
 * <li>{@link #isHeldExclusively}
 * </ul>
 *
 * Each of these methods by default throws {@link
 * UnsupportedOperationException}.  Implementations of these methods
 * must be internally thread-safe, and should in general be short and
 * not block. Defining these methods is the <em>only</em> supported
 * means of using this class. All other methods are declared
 * {@code final} because they cannot be independently varied.
 *
 * <p>You may also find the inherited methods from {@link
 * AbstractOwnableSynchronizer} useful to keep track of the thread
 * owning an exclusive synchronizer.  You are encouraged to use them
 * -- this enables monitoring and diagnostic tools to assist users in
 * determining which threads hold locks.
 *
 * <p>Even though this class is based on an internal FIFO queue, it
 * does not automatically enforce FIFO acquisition policies.  The core
 * of exclusive synchronization takes the form:
 *
 * <pre>
 * Acquire:
 *     while (!tryAcquire(arg)) {
 *        <em>enqueue thread if it is not already queued</em>;
 *        <em>possibly block current thread</em>;
 *     }
 *
 * Release:
 *     if (tryRelease(arg))
 *        <em>unblock the first queued thread</em>;
 * </pre>
 *
 * (Shared mode is similar but may involve cascading signals.)
 *
 * <p id="barging">Because checks in acquire are invoked before
 * enqueuing, a newly acquiring thread may <em>barge</em> ahead of
 * others that are blocked and queued.  However, you can, if desired,
 * define {@code tryAcquire} and/or {@code tryAcquireShared} to
 * disable barging by internally invoking one or more of the inspection
 * methods, thereby providing a <em>fair</em> FIFO acquisition order.
 * In particular, most fair synchronizers can define {@code tryAcquire}
 * to return {@code false} if {@link #hasQueuedPredecessors} (a method
 * specifically designed to be used by fair synchronizers) returns
 * {@code true}.  Other variations are possible.
 *
 * <p>Throughput and scalability are generally highest for the
 * default barging (also known as <em>greedy</em>,
 * <em>renouncement</em>, and <em>convoy-avoidance</em>) strategy.
 * While this is not guaranteed to be fair or starvation-free, earlier
 * queued threads are allowed to recontend before later queued
 * threads, and each recontention has an unbiased chance to succeed
 * against incoming threads.  Also, while acquires do not
 * &quot;spin&quot; in the usual sense, they may perform multiple
 * invocations of {@code tryAcquire} interspersed with other
 * computations before blocking.  This gives most of the benefits of
 * spins when exclusive synchronization is only briefly held, without
 * most of the liabilities when it isn't. If so desired, you can
 * augment this by preceding calls to acquire methods with
 * "fast-path" checks, possibly prechecking {@link #hasContended}
 * and/or {@link #hasQueuedThreads} to only do so if the synchronizer
 * is likely not to be contended.
 *
 * <p>This class provides an efficient and scalable basis for
 * synchronization in part by specializing its range of use to
 * synchronizers that can rely on {@code int} state, acquire, and
 * release parameters, and an internal FIFO wait queue. When this does
 * not suffice, you can build synchronizers from a lower level using
 * {@link java.util.concurrent.atomic atomic} classes, your own custom
 * {@link java.util.Queue} classes, and {@link LockSupport} blocking
 * support.
 *
 * <h3>Usage Examples</h3>
 *
 * <p>Here is a non-reentrant mutual exclusion lock class that uses
 * the value zero to represent the unlocked state, and one to
 * represent the locked state. While a non-reentrant lock
 * does not strictly require recording of the current owner
 * thread, this class does so anyway to make usage easier to monitor.
 * It also supports conditions and exposes some instrumentation methods:
 *
 * <pre> {@code
 * class Mutex implements Lock, java.io.Serializable {
 *
 *   // Our internal helper class
 *   private static class Sync extends AbstractQueuedSynchronizer {
 *     // Acquires the lock if state is zero
 *     public boolean tryAcquire(int acquires) {
 *       assert acquires == 1; // Otherwise unused
 *       if (compareAndSetState(0, 1)) {
 *         setExclusiveOwnerThread(Thread.currentThread());
 *         return true;
 *       }
 *       return false;
 *     }
 *
 *     // Releases the lock by setting state to zero
 *     protected boolean tryRelease(int releases) {
 *       assert releases == 1; // Otherwise unused
 *       if (!isHeldExclusively())
 *         throw new IllegalMonitorStateException();
 *       setExclusiveOwnerThread(null);
 *       setState(0);
 *       return true;
 *     }
 *
 *     // Reports whether in locked state
 *     public boolean isLocked() {
 *       return getState() != 0;
 *     }
 *
 *     public boolean isHeldExclusively() {
 *       // a data race, but safe due to out-of-thin-air guarantees
 *       return getExclusiveOwnerThread() == Thread.currentThread();
 *     }
 *
 *     // Provides a Condition
 *     public Condition newCondition() {
 *       return new ConditionObject();
 *     }
 *
 *     // Deserializes properly
 *     private void readObject(ObjectInputStream s)
 *         throws IOException, ClassNotFoundException {
 *       s.defaultReadObject();
 *       setState(0); // reset to unlocked state
 *     }
 *   }
 *
 *   // The sync object does all the hard work. We just forward to it.
 *   private final Sync sync = new Sync();
 *
 *   public void lock()              { sync.acquire(1); }
 *   public boolean tryLock()        { return sync.tryAcquire(1); }
 *   public void unlock()            { sync.release(1); }
 *   public Condition newCondition() { return sync.newCondition(); }
 *   public boolean isLocked()       { return sync.isLocked(); }
 *   public boolean isHeldByCurrentThread() {
 *     return sync.isHeldExclusively();
 *   }
 *   public boolean hasQueuedThreads() {
 *     return sync.hasQueuedThreads();
 *   }
 *   public void lockInterruptibly() throws InterruptedException {
 *     sync.acquireInterruptibly(1);
 *   }
 *   public boolean tryLock(long timeout, TimeUnit unit)
 *       throws InterruptedException {
 *     return sync.tryAcquireNanos(1, unit.toNanos(timeout));
 *   }
 * }}</pre>
 *
 * <p>Here is a latch class that is like a
 * {@link java.util.concurrent.CountDownLatch CountDownLatch}
 * except that it only requires a single {@code signal} to
 * fire. Because a latch is non-exclusive, it uses the {@code shared}
 * acquire and release methods.
 *
 * <pre> {@code
 * class BooleanLatch {
 *
 *   private static class Sync extends AbstractQueuedSynchronizer {
 *     boolean isSignalled() { return getState() != 0; }
 *
 *     protected int tryAcquireShared(int ignore) {
 *       return isSignalled() ? 1 : -1;
 *     }
 *
 *     protected boolean tryReleaseShared(int ignore) {
 *       setState(1);
 *       return true;
 *     }
 *   }
 *
 *   private final Sync sync = new Sync();
 *   public boolean isSignalled() { return sync.isSignalled(); }
 *   public void signal()         { sync.releaseShared(1); }
 *   public void await() throws InterruptedException {
 *     sync.acquireSharedInterruptibly(1);
 *   }
 * }}</pre>
 *
 * @since 1.5
 * @author Doug Lea
 */

/**
 * 1. 第一个 Node 存储真实的节点吗？
 *  不是，任何情况下都是一个 dummy node
 * 2. 为什么要从 tail 往前遍历？
 *  与 node 入队的方式有关，看 {@link AbstractQueuedSynchronizer#addWaiter(Node)}
 */
public abstract class AbstractQueuedSynchronizer
    extends AbstractOwnableSynchronizer
    implements java.io.Serializable {

    private static final long serialVersionUID = 7373984972572414691L;

    /**
     * Creates a new {@code AbstractQueuedSynchronizer} instance
     * with initial synchronization state of zero.
     */
    protected AbstractQueuedSynchronizer() { }

    /**
     * Wait queue node class.
     *
     * <p>The wait queue is a variant of a "CLH" (Craig, Landin, and
     * Hagersten) lock queue. CLH locks are normally used for
     * spinlocks.  We instead use them for blocking synchronizers, but
     * use the same basic tactic of holding some of the control
     * information about a thread in the predecessor of its node.  A
     * "status" field in each node keeps track of whether a thread
     * should block.  A node is signalled when its predecessor
     * releases.  Each node of the queue otherwise serves as a
     * specific-notification-style monitor holding a single waiting
     * thread. The status field does NOT control whether threads are
     * granted locks etc though.  A thread may try to acquire if it is
     * first in the queue. But being first does not guarantee success;
     * it only gives the right to contend.  So the currently released
     * contender thread may need to rewait.
     *
     * <p>To enqueue into a CLH lock, you atomically splice it in as new
     * tail. To dequeue, you just set the head field.
     * <pre>
     *      +------+  prev +-----+       +-----+
     * head |      | <---- |     | <---- |     |  tail
     *      +------+       +-----+       +-----+
     * </pre>
     *
     * <p>Insertion into a CLH queue requires only a single atomic
     * operation on "tail", so there is a simple atomic point of
     * demarcation from unqueued to queued. Similarly, dequeuing
     * involves only updating the "head". However, it takes a bit
     * more work for nodes to determine who their successors are,
     * in part to deal with possible cancellation due to timeouts
     * and interrupts.
     *
     * <p>The "prev" links (not used in original CLH locks), are mainly
     * needed to handle cancellation. If a node is cancelled, its
     * successor is (normally) relinked to a non-cancelled
     * predecessor. For explanation of similar mechanics in the case
     * of spin locks, see the papers by Scott and Scherer at
     * http://www.cs.rochester.edu/u/scott/synchronization/
     *
     * <p>We also use "next" links to implement blocking mechanics.
     * The thread id for each node is kept in its own node, so a
     * predecessor signals the next node to wake up by traversing
     * next link to determine which thread it is.  Determination of
     * successor must avoid races with newly queued nodes to set
     * the "next" fields of their predecessors.  This is solved
     * when necessary by checking backwards from the atomically
     * updated "tail" when a node's successor appears to be null.
     * (Or, said differently, the next-links are an optimization
     * so that we don't usually need a backward scan.)
     *
     * <p>Cancellation introduces some conservatism to the basic
     * algorithms.  Since we must poll for cancellation of other
     * nodes, we can miss noticing whether a cancelled node is
     * ahead or behind us. This is dealt with by always unparking
     * successors upon cancellation, allowing them to stabilize on
     * a new predecessor, unless we can identify an uncancelled
     * predecessor who will carry this responsibility.
     *
     * <p>CLH queues need a dummy header node to get started. But
     * we don't create them on construction, because it would be wasted
     * effort if there is never contention. Instead, the node
     * is constructed and head and tail pointers are set upon first
     * contention.
     *
     * <p>Threads waiting on Conditions use the same nodes, but
     * use an additional link. Conditions only need to link nodes
     * in simple (non-concurrent) linked queues because they are
     * only accessed when exclusively held.  Upon await, a node is
     * inserted into a condition queue.  Upon signal, the node is
     * transferred to the main queue.  A special value of status
     * field is used to mark which queue a node is on.
     *
     * <p>Thanks go to Dave Dice, Mark Moir, Victor Luchangco, Bill
     * Scherer and Michael Scott, along with members of JSR-166
     * expert group, for helpful ideas, discussions, and critiques
     * on the design of this class.
     */
    static final class Node {
        /** Marker to indicate a node is waiting in shared mode */
        static final Node SHARED = new Node();
        /** Marker to indicate a node is waiting in exclusive mode */
        static final Node EXCLUSIVE = null;

        /**
         * 当前线程取消获取
         */
        /** waitStatus value to indicate thread has cancelled. */
        static final int CANCELLED =  1;
        /**
         * 这个状态的影响是影响到下一个节点
         * 后继节点需要被唤醒，并且后续节点为已经被挂起或者马上要被挂起状态
         * SIGNAL 状态一般为后继节点给当前节点设置的
         */
        /** waitStatus value to indicate successor's thread needs unparking. */
        static final int SIGNAL    = -1;
        /**
         * 在 condition queue 中等待
         * 仅在等待队列中使用
         */
        /** waitStatus value to indicate thread is waiting on condition. */
        static final int CONDITION = -2;
        /**
         * 在释放共享锁的情况下要能够往后面传播
         * 仅在共享模式下使用
         *
         * waitStatus value to indicate the next acquireShared should
         * unconditionally propagate.
         */
        static final int PROPAGATE = -3;

        /**
         * 节点当前的状态，可能是上面所列的状态之一。
         *
         * Status field, taking on only the values:
         *   SIGNAL:     The successor of this node is (or will soon be)
         *               blocked (via park), so the current node must
         *               unpark its successor when it releases or
         *               cancels. To avoid races, acquire methods must
         *               first indicate they need a signal,
         *               then retry the atomic acquire, and then,
         *               on failure, block.
         *   CANCELLED:  This node is cancelled due to timeout or interrupt.
         *               Nodes never leave this state. In particular,
         *               a thread with cancelled node never again blocks.
         *   CONDITION:  This node is currently on a condition queue.
         *               It will not be used as a sync queue node
         *               until transferred, at which time the status
         *               will be set to 0. (Use of this value here has
         *               nothing to do with the other uses of the
         *               field, but simplifies mechanics.)
         *   PROPAGATE:  A releaseShared should be propagated to other
         *               nodes. This is set (for head node only) in
         *               doReleaseShared to ensure propagation
         *               continues, even if other operations have
         *               since intervened.
         *   0:          None of the above
         *
         * The values are arranged numerically to simplify use.
         * Non-negative values mean that a node doesn't need to
         * signal. So, most code doesn't need to check for particular
         * values, just for sign.
         *
         * The field is initialized to 0 for normal sync nodes, and
         * CONDITION for condition nodes.  It is modified using CAS
         * (or when possible, unconditional volatile writes).
         */
        volatile int waitStatus;

        /**
         * 指向前驱节点
         * 入队时即赋值
         * 出队即置为 null
         * 前驱节点 cancel 的时候，会往前指向找到一个非 cancel 的节点
         * 非 cancel 节点必存在，因为 head 节点肯定不是 cancel 的
         *
         * Link to predecessor node that current node/thread relies on
         * for checking waitStatus. Assigned during enqueuing, and nulled
         * out (for sake of GC) only upon dequeuing.  Also, upon
         * cancellation of a predecessor, we short-circuit while
         * finding a non-cancelled one, which will always exist
         * because the head node is never cancelled: A node becomes
         * head only as a result of successful acquire. A
         * cancelled thread never succeeds in acquiring, and a thread only
         * cancels itself, not any other node.
         */
        volatile Node prev;

        /**
         * 指向后继节点
         * 一个节点入队的时候，其前驱节点并不是马上将 next 指针指向入队节点
         * 因此 next 指针指向 null 的时候并不一定代表该节点是队尾节点
         * 可以通过从 tail 往前遍历的方式
         * cancellaton 节点的 next 指针指向自己
         *
         * Link to the successor node that the current node/thread
         * unparks upon release. Assigned during enqueuing, adjusted
         * when bypassing cancelled predecessors, and nulled out (for
         * sake of GC) when dequeued.  The enq operation does not
         * assign next field of a predecessor until after attachment,
         * so seeing a null next field does not necessarily mean that
         * node is at end of queue. However, if a next field appears
         * to be null, we can scan prev's from the tail to
         * double-check.  The next field of cancelled nodes is set to
         * point to the node itself instead of null, to make life
         * easier for isOnSyncQueue.
         */
        volatile Node next;

        /**
         * node 绑定的 Thread
         *
         * The thread that enqueued this node.  Initialized on
         * construction and nulled out after use.
         */
        volatile Thread thread;

        /**
         * 在 condition queue 中指向下一个等待的 node
         * 或者
         * 是在 shared mode 中等待的 node 的下一个 node
         *
         * Link to next node waiting on condition, or the special
         * value SHARED.  Because condition queues are accessed only
         * when holding in exclusive mode, we just need a simple
         * linked queue to hold nodes while they are waiting on
         * conditions. They are then transferred to the queue to
         * re-acquire. And because conditions can only be exclusive,
         * we save a field by using special value to indicate shared
         * mode.
         */
        Node nextWaiter;

        /**
         * Returns true if node is waiting in shared mode.
         */
        final boolean isShared() {
            return nextWaiter == SHARED;
        }

        /**
         * Returns previous node, or throws NullPointerException if null.
         * Use when predecessor cannot be null.  The null check could
         * be elided, but is present to help the VM.
         *
         * @return the predecessor of this node
         */
        final Node predecessor() {
            Node p = prev;
            if (p == null)
                throw new NullPointerException();
            else
                return p;
        }

        /** Establishes initial head or SHARED marker. */
        Node() {}

        /** Constructor used by addWaiter. */
        Node(Node nextWaiter) {
            this.nextWaiter = nextWaiter;
            THREAD.set(this, Thread.currentThread());
        }

        /** Constructor used by addConditionWaiter. */
        Node(int waitStatus) {
            // 设置状态
            WAITSTATUS.set(this, waitStatus);
            // 设置绑定线程
            THREAD.set(this, Thread.currentThread());
        }

        /** CASes waitStatus field. */
        final boolean compareAndSetWaitStatus(int expect, int update) {
            return WAITSTATUS.compareAndSet(this, expect, update);
        }

        /** CASes next field. */
        final boolean compareAndSetNext(Node expect, Node update) {
            return NEXT.compareAndSet(this, expect, update);
        }

        final void setPrevRelaxed(Node p) {
            PREV.set(this, p);
        }

        // VarHandle mechanics
        private static final VarHandle NEXT;
        private static final VarHandle PREV;
        private static final VarHandle THREAD;
        private static final VarHandle WAITSTATUS;
        static {
            try {
                MethodHandles.Lookup l = MethodHandles.lookup();
                NEXT = l.findVarHandle(Node.class, "next", Node.class);
                PREV = l.findVarHandle(Node.class, "prev", Node.class);
                THREAD = l.findVarHandle(Node.class, "thread", Thread.class);
                WAITSTATUS = l.findVarHandle(Node.class, "waitStatus", int.class);
            } catch (ReflectiveOperationException e) {
                throw new ExceptionInInitializerError(e);
            }
        }
    }

    /**
     * 永远都是是一个 dummy head(可以看成是当前持有锁的线程对应的 node，但是 thread 指向 null)
     * head 节点保证不为 CANCELLED 状态
     * 在首次出现冲突的时候才会初始化
     * 只能通过 setHead 方法修改
     * head 节点的 thread 永远为 null，某种情况下可以将其认为是当前持有锁的线程
     *
     * Head of the wait queue, lazily initialized.  Except for
     * initialization, it is modified only via method setHead.  Note:
     * If head exists, its waitStatus is guaranteed not to be
     * CANCELLED.
     */
    private transient volatile Node head;

    /**
     * 在首次出现冲突的时候才会初始化
     * 只能通过 enq 方法中修改
     *
     * Tail of the wait queue, lazily initialized.  Modified only via
     * method enq to add new wait node.
     */
    private transient volatile Node tail;

    /**
     * 当前的锁状态
     *
     * The synchronization state.
     */
    private volatile int state;

    /**
     * Returns the current value of synchronization state.
     * This operation has memory semantics of a {@code volatile} read.
     * @return current state value
     */
    protected final int getState() {
        return state;
    }

    /**
     * volatile 写
     *
     * Sets the value of synchronization state.
     * This operation has memory semantics of a {@code volatile} write.
     * @param newState the new state value
     */
    protected final void setState(int newState) {
        state = newState;
    }

    /**
     * cas 原子更新
     * volatile 读写
     *
     * Atomically sets synchronization state to the given updated
     * value if the current state value equals the expected value.
     * This operation has memory semantics of a {@code volatile} read
     * and write.
     *
     * @param expect the expected value
     * @param update the new value
     * @return {@code true} if successful. False return indicates that the actual
     *         value was not equal to the expected value.
     */
    protected final boolean compareAndSetState(int expect, int update) {
        return STATE.compareAndSet(this, expect, update);
    }

    // Queuing utilities

    /**
     * 自旋的最长时间为 1000 nanoseconds
     *
     * The number of nanoseconds for which it is faster to spin
     * rather than to use timed park. A rough estimate suffices
     * to improve responsiveness with very short timeouts.
     */
    static final long SPIN_FOR_TIMEOUT_THRESHOLD = 1000L;

    /**
     * 节点入队
     *
     * Inserts node into queue, initializing if necessary. See picture above.
     * @param node the node to insert
     * @return node's predecessor 返回前驱节点
     */
    private Node enq(Node node) {
        for (;;) {
            Node oldTail = tail;
            if (oldTail != null) {
                // 原子更新 pre 指针
                node.setPrevRelaxed(oldTail);
                // 唯一更新 tail 的地方
                if (compareAndSetTail(oldTail, node)) {
                    oldTail.next = node;
                    // 返回原来的 tail
                    return oldTail;
                }
            } else {
                initializeSyncQueue();
            }
        }
    }

    /**
     * 添加一个节点并入队
     * 将一个节点入队尾分为三步：
     * 1. 向前节点的 pre 指向 oldTail
     * 2. tail 指向当前节点(cas 操作，并发情况下可能失败)
     * 3. oldTail 的 next 指向当前节点
     * 这三步不是原子操作。因此，当 2 完成的时候，oldTail 的 next 还是指向 null，
     * 此时如果有从 head 正向遍历过来的节点，那么就会丢失后面新增加的节点信息。所以要从 tail 往前遍历。
     *
     * Creates and enqueues node for current thread and given mode.
     *
     * @param mode Node.EXCLUSIVE for exclusive, Node.SHARED for shared
     * @return the new node
     */
    private Node addWaiter(Node mode) {
        Node node = new Node(mode);

        // 死循环入队
        for (;;) {
            Node oldTail = tail;
            if (oldTail != null) {
                node.setPrevRelaxed(oldTail);
                // 可能在入队的同时有其他线程已经入队，cas 失败
                // 则会再次尝试
                if (compareAndSetTail(oldTail, node)) {
                    oldTail.next = node;
                    return node;
                }
            } else {
                initializeSyncQueue();
            }
        }
    }

    /**
     * 设置新的 head node，只有获取到锁之后才会调用到该方法
     * 因此是线程安全的
     *
     * Sets head of queue to be node, thus dequeuing. Called only by
     * acquire methods.  Also nulls out unused fields for sake of GC
     * and to suppress unnecessary signals and traversals.
     *
     * @param node the node
     */
    private void setHead(Node node) {
        head = node;
        node.thread = null;
        node.prev = null;
    }

    /**
     * 唤醒 node 节点的后继节点
     *
     * Wakes up node's successor, if one exists.
     *
     * @param node the node
     */
    private void unparkSuccessor(Node node) {
        /*
         * 当前节点的 status
         * If status is negative (i.e., possibly needing signal) try
         * to clear in anticipation of signalling.  It is OK if this
         * fails or if status is changed by waiting thread.
         */
        int ws = node.waitStatus;
        // 不是 canceled
        // 一般其状态为 SIGNAL 或者 PROPAGATE
        // 因为后续要进行唤醒动作，这里可以将其状态改为 0 了
        if (ws < 0)
            // 将其状态设置为 0
            node.compareAndSetWaitStatus(ws, 0);

        /*
         * 唤醒后继节点，如果后继节点是 canceled，那么从 tail 往前遍历，找到没有 cancel 的节点
         *
         * Thread to unpark is held in successor, which is normally
         * just the next node.  But if cancelled or apparently null,
         * traverse backwards from tail to find the actual
         * non-cancelled successor.
         */
        Node s = node.next;
        // 不存在后继节点或者后继节点的状态为 canceled
        if (s == null || s.waitStatus > 0) {
            s = null;
            // 从 tail 往前遍历，找到最后一个正常的 node
            for (Node p = tail; p != node && p != null; p = p.prev)
                if (p.waitStatus <= 0)
                    s = p;
        }
        if (s != null)
            // 如果找到了，则 unpark
            LockSupport.unpark(s.thread);
    }

    /**
     * shared 模式下，释放锁
     * 唤醒后继节点，并且进行传播
     *
     * Release action for shared mode -- signals successor and ensures
     * propagation. (Note: For exclusive mode, release just amounts
     * to calling unparkSuccessor of head if it needs signal.)
     */
    private void doReleaseShared() {
        /*
         * Ensure that a release propagates, even if there are other
         * in-progress acquires/releases.  This proceeds in the usual
         * way of trying to unparkSuccessor of head if it needs
         * signal. But if it does not, status is set to PROPAGATE to
         * ensure that upon release, propagation continues.
         * Additionally, we must loop in case a new node is added
         * while we are doing this. Also, unlike other uses of
         * unparkSuccessor, we need to know if CAS to reset status
         * fails, if so rechecking.
         */
        for (;;) {
            Node h = head;
            // 说明当前队列中至少有两个节点
            if (h != null && h != tail) {
                // 当前 head 节点的状态
                int ws = h.waitStatus;
                // 如果条件成立，说明头结点的状态已经被后续的节点设置成为了 SIGNAL
                if (ws == Node.SIGNAL) {
                    // 将 SIGNAL 状态改成 0
                    if (!h.compareAndSetWaitStatus(Node.SIGNAL, 0))
                        // 更改失败，则暂时跳过继续尝试
                        continue;            // loop to recheck cases
                    // 唤醒后继节点
                    unparkSuccessor(h);
                }
                // ws == 0 能走到这里说明头结点还是刚刚成为头结点，waitStatus 还没有被后续的节点修改
                // 也就是头结点的后续节点还未执行完 shouldParkAfterFailedAcquire 方法
                // h.compareAndSetWaitStatus(0, Node.PROPAGATE) 说明头结点的后续节点执行完了 shouldParkAfterFailedAcquire 方法，并且将其 status 设为 SIGNAL 了。
                else if (ws == 0 &&
                         !h.compareAndSetWaitStatus(0, Node.PROPAGATE))
                    continue;                // loop on failed CAS
            }
            // head 没有变的时候跳出循环
            if (h == head)                   // loop if head changed
                break;
        }
    }

    /**
     * Sets head of queue, and checks if successor may be waiting
     * in shared mode, if so propagating if either propagate > 0 or
     * PROPAGATE status was set.
     *
     * @param node the node
     * @param propagate the return value from a tryAcquireShared
     */
    private void setHeadAndPropagate(Node node, int propagate) {
        Node h = head; // Record old head for check below
        // 设置新的 head
        setHead(node);
        /*
         * Try to signal next queued node if:
         *   Propagation was indicated by caller,
         *     or was recorded (as h.waitStatus either before
         *     or after setHead) by a previous operation
         *     (note: this uses sign-check of waitStatus because
         *      PROPAGATE status may transition to SIGNAL.)
         * and
         *   The next node is waiting in shared mode,
         *     or we don't know, because it appears null
         *
         * The conservatism in both of these checks may cause
         * unnecessary wake-ups, but only when there are multiple
         * racing acquires/releases, so most need signals now or soon
         * anyway.
         */
        if (propagate > 0 || h == null || h.waitStatus < 0 ||
            (h = head) == null || h.waitStatus < 0) {
            // 后继节点
            Node s = node.next;
            if (s == null || s.isShared())
                doReleaseShared();
        }
    }

    // Utilities for various versions of acquire

    /**
     * 取消正在尝试获取锁的线程
     *
     * Cancels an ongoing attempt to acquire.
     *
     * @param node the node
     */
    private void cancelAcquire(Node node) {
        // Ignore if node doesn't exist
        if (node == null)
            return;

        node.thread = null;

        // Skip cancelled predecessors
        // 往前找到未 canceled 的节点
        Node pred = node.prev;
        while (pred.waitStatus > 0)
            node.prev = pred = pred.prev;

        // predNext is the apparent node to unsplice. CASes below will
        // fail if not, in which case, we lost race vs another cancel
        // or signal, so no further action is necessary, although with
        // a possibility that a cancelled node may transiently remain
        // reachable.
        // predNext 节点是需要断开的节点
        // cas 需要用到
        Node predNext = pred.next;

        // Can use unconditional write instead of CAS here.
        // After this atomic step, other Nodes can skip past us.
        // Before, we are free of interference from other threads.
        node.waitStatus = Node.CANCELLED;

        // If we are the tail, remove ourselves.
        // 如果当前要删除的 node 是 tail，那么直接将 tail 指向刚刚找到的未被删除的 node
        if (node == tail && compareAndSetTail(node, pred)) {
            // 将 pred 的 next 指向 null
            pred.compareAndSetNext(predNext, null);
            // 当前要删除的 node 不是 tail
        } else {
            // If successor needs signal, try to set pred's next-link
            // so it will get one. Otherwise wake it up to propagate.
            // 保存前驱节点状态
            int ws;
            if (pred != head &&
                ((ws = pred.waitStatus) == Node.SIGNAL ||
                 (ws <= 0 && pred.compareAndSetWaitStatus(ws, Node.SIGNAL))) &&
                pred.thread != null) {
                // 获取当前删除节点的后继节点
                Node next = node.next;
                // node 的 next 存在且未被 cancel
                if (next != null && next.waitStatus <= 0)
                    // pred 的 next 指向 next
                    pred.compareAndSetNext(predNext, next);
            } else {
                // pred 的状态为 PROPAGATE
                // 唤醒 node 节点的后继节点
                unparkSuccessor(node);
            }
            // 指向自己
            node.next = node; // help GC
        }
    }

    /**
     * 返回获取失败后是否需要 park
     *
     * Checks and updates status for a node that failed to acquire.
     * Returns true if thread should block. This is the main signal
     * control in all acquire loops.  Requires that pred == node.prev.
     *
     * @param pred node's predecessor holding status
     * @param node the node
     * @return {@code true} if thread should block
     */
    private static boolean shouldParkAfterFailedAcquire(Node pred, Node node) {
        // 前驱节点的状态
        int ws = pred.waitStatus;
        // 如果前驱节点的状态已经为 SIGNAL
        // 可以安心的 park 了
        // 当前去节点释放锁之后，会唤醒当前节点
        if (ws == Node.SIGNAL)
            /*
             * This node has already set status asking a release
             * to signal it, so it can safely park.
             */
            return true;
            // 前驱节点 canceled
        if (ws > 0) {
            /*
             * 往前找非 canceled 的节点
             * Predecessor was cancelled. Skip over predecessors and
             * indicate retry.
             */
            do {
                node.prev = pred = pred.prev;
            } while (pred.waitStatus > 0);
            // 找到的节点的 next 指向当前节点
            pred.next = node;
        } else {
            /*
             * 前驱节点状态 为 0 或者 PROPAGATE，说明当前节点马上要被唤醒了，不用 park
             * 尝试将前驱节点的状态设为 SIGNAL
             * waitStatus must be 0 or PROPAGATE.  Indicate that we
             * need a signal, but don't park yet.  Caller will need to
             * retry to make sure it cannot acquire before parking.
             */
            pred.compareAndSetWaitStatus(ws, Node.SIGNAL);
        }
        // 不用 park
        return false;
    }

    /**
     * 一般是补偿中断
     *
     * Convenience method to interrupt current thread.
     */
    static void selfInterrupt() {
        // 中断当前线程
        Thread.currentThread().interrupt();
    }

    /**
     * Convenience method to park and then check if interrupted.
     *
     * @return {@code true} if interrupted
     */
    private final boolean parkAndCheckInterrupt() {
        // park 在这里
        LockSupport.park(this);
        // 返回是否被中断
        return Thread.interrupted();
    }

    /*
     * Various flavors of acquire, varying in exclusive/shared and
     * control modes.  Each is mostly the same, but annoyingly
     * different.  Only a little bit of factoring is possible due to
     * interactions of exception mechanics (including ensuring that we
     * cancel if tryAcquire throws exception) and other control, at
     * least not without hurting performance too much.
     */

    /**
     * 能调用到该方法说明 node 已经进队了
     * 独占不可中断模式下获取锁
     *
     * Acquires in exclusive uninterruptible mode for thread already in
     * queue. Used by condition wait methods as well as acquire.
     *
     * @param node the node
     * @param arg the acquire argument
     * @return {@code true} if interrupted while waiting 返回中断标记
     */
    final boolean acquireQueued(final Node node, int arg) {
        // 中断标记
        boolean interrupted = false;
        try {
            // 自旋
            for (;;) {
                // 获取当前节点的前驱节点
                final Node p = node.predecessor();
                // 如果前驱节点就是 head 节点，则再次调用 tryAcquire 尝试获取锁
                if (p == head && tryAcquire(arg)) {
                    // 获取成功
                    setHead(node);
                    p.next = null; // help GC
                    // 返回抢锁过程中是否被中断，如果是的话在外面处理该中断
                    return interrupted;
                }
                // 获取失败，判断是否需要 park
                if (shouldParkAfterFailedAcquire(p, node))
                    // 进入 park
                    // 如果是被中断，则 interrupted 为 true，不响应中断，继续获取锁。
                    // 如果是被 unpark，那么则再次进入抢夺锁的循环
                    interrupted |= parkAndCheckInterrupt();
            }
        } catch (Throwable t) {
            // 这里 catch 的不是中断异常
            // 可能是子类在重写 tryAcquire 的时候定义抛出的异常
            cancelAcquire(node);
            if (interrupted)
                selfInterrupt();
            throw t;
        }
    }

    /**
     * 可中断的获取
     *
     * Acquires in exclusive interruptible mode.
     * @param arg the acquire argument
     */
    private void doAcquireInterruptibly(int arg)
        throws InterruptedException {
        // 封装成 Node 并入队
        final Node node = addWaiter(Node.EXCLUSIVE);
        try {
            for (;;) {
                // 获取前驱节点
                final Node p = node.predecessor();
                // 如果前驱节点是 head 节点
                // 尝试一次获取锁
                if (p == head && tryAcquire(arg)) {
                    setHead(node);
                    p.next = null; // help GC
                    return;
                }
                // 前驱节点不是 head 节点
                // 检查是否需要 park
                if (shouldParkAfterFailedAcquire(p, node) &&
                    parkAndCheckInterrupt())
                    // 如果是 park 过程中被中断，则抛出中断异常
                    throw new InterruptedException();
            }
        } catch (Throwable t) {
            // 捕获到上面抛出的中断异常进行处理
            // 直接取消获取
            cancelAcquire(node);
            throw t;
        }
    }

    /**
     * 独占模式下超时获取
     *
     * Acquires in exclusive timed mode.
     *
     * @param arg the acquire argument
     * @param nanosTimeout max wait time
     * @return {@code true} if acquired
     */
    private boolean doAcquireNanos(int arg, long nanosTimeout)
            throws InterruptedException {
        // 如果超时时间 <= 0 直接返回 false
        if (nanosTimeout <= 0L)
            return false;
        // 计算 ddl
        final long deadline = System.nanoTime() + nanosTimeout;
        // 入队
        final Node node = addWaiter(Node.EXCLUSIVE);
        try {
            for (;;) {
                // 获得前驱节点
                final Node p = node.predecessor();
                // 如果前驱节点是 head 节点
                // 尝试获取
                if (p == head && tryAcquire(arg)) {
                    // 获取到了
                    setHead(node);
                    p.next = null; // help GC
                    return true;
                }
                // 计算剩余的时间
                nanosTimeout = deadline - System.nanoTime();
                // 如果时间到了
                if (nanosTimeout <= 0L) {
                    // 取消获取
                    cancelAcquire(node);
                    return false;
                }
                // 判断是否需要进入 park
                // 如果需要进入 park 且 剩余的时间大于自旋时间
                if (shouldParkAfterFailedAcquire(p, node) &&
                        // 如果剩余时间小于 SPIN_FOR_TIMEOUT_THRESHOLD
                        // 那么久一直自旋，不进入 park
                    nanosTimeout > SPIN_FOR_TIMEOUT_THRESHOLD)
                    // park nanosTimeout 时间
                    LockSupport.parkNanos(this, nanosTimeout);
                // 如果被中断了，抛出中断异常
                if (Thread.interrupted())
                    throw new InterruptedException();
            }
        } catch (Throwable t) {
            // 抛出中断异常后取消获取
            cancelAcquire(node);
            throw t;
        }
    }

    /**
     *
     * 共享模式下获取锁
     *
     * Acquires in shared uninterruptible mode.
     * @param arg the acquire argument
     */
    private void doAcquireShared(int arg) {
        // 进队
        final Node node = addWaiter(Node.SHARED);
        boolean interrupted = false;
        try {
            // 自旋
            for (;;) {
                // 找到前驱节点
                final Node p = node.predecessor();
                // 前驱是 head，则自旋获取锁
                if (p == head) {
                    int r = tryAcquireShared(arg);
                    // 获取成功
                    if (r >= 0) {
                        // 设置 head 并往后传播
                        setHeadAndPropagate(node, r);
                        p.next = null; // help GC
                        return;
                    }
                }
                // 前驱不是 head
                // 检查是否需要 park
                if (shouldParkAfterFailedAcquire(p, node))
                    // 仅仅记录是否中断
                    interrupted |= parkAndCheckInterrupt();
            }
        } catch (Throwable t) {
            cancelAcquire(node);
            throw t;
        } finally {
            // 如果被中断，会抛到外面
            if (interrupted)
                selfInterrupt();
        }
    }

    /**
     * 共享模式下获取锁，支持中断
     *
     * Acquires in shared interruptible mode.
     * @param arg the acquire argument
     */
    private void doAcquireSharedInterruptibly(int arg)
        throws InterruptedException {
        // 入队
        final Node node = addWaiter(Node.SHARED);
        try {
            // 自旋
            for (;;) {
                // 前驱节点
                final Node p = node.predecessor();
                // 如果是 head 节点
                if (p == head) {
                    // 尝试获取锁
                    int r = tryAcquireShared(arg);
                    if (r >= 0) {
                        setHeadAndPropagate(node, r);
                        p.next = null; // help GC
                        return;
                    }
                }
                if (shouldParkAfterFailedAcquire(p, node) &&
                    parkAndCheckInterrupt())
                    // 如果 park 中被中断
                    // 抛出中断异常
                    throw new InterruptedException();
            }
        } catch (Throwable t) {
            // catch 上面抛出的中断异常
            // 取消获取
            cancelAcquire(node);
            throw t;
        }
    }

    /**
     * 共享模式下，带超时时间的获取锁
     *
     * Acquires in shared timed mode.
     *
     * @param arg the acquire argument
     * @param nanosTimeout max wait time
     * @return {@code true} if acquired
     */
    private boolean doAcquireSharedNanos(int arg, long nanosTimeout)
            throws InterruptedException {
        // 如果时间 <= 0，直接返回获取失败
        if (nanosTimeout <= 0L)
            return false;
        // 计算 ddl
        final long deadline = System.nanoTime() + nanosTimeout;
        // 入队
        final Node node = addWaiter(Node.SHARED);
        try {
            // 自旋
            for (;;) {
                final Node p = node.predecessor();
                if (p == head) {
                    int r = tryAcquireShared(arg);
                    if (r >= 0) {
                        setHeadAndPropagate(node, r);
                        p.next = null; // help GC
                        return true;
                    }
                }
                // 剩余时间
                nanosTimeout = deadline - System.nanoTime();
                // 无剩余时间，直接返回 false
                if (nanosTimeout <= 0L) {
                    cancelAcquire(node);
                    return false;
                }
                // 剩余时间 大于 SPIN_FOR_TIMEOUT_THRESHOLD 且需要 park，则进入 park
                if (shouldParkAfterFailedAcquire(p, node) &&
                    nanosTimeout > SPIN_FOR_TIMEOUT_THRESHOLD)
                    // park nanosTimeout 时间
                    LockSupport.parkNanos(this, nanosTimeout);
                // 如果被中断唤醒
                if (Thread.interrupted())
                    // 抛出中断异常
                    throw new InterruptedException();
            }
        } catch (Throwable t) {
            // 处理中断异常，取消获取
            cancelAcquire(node);
            throw t;
        }
    }

    // Main exported methods

    /**
     * 需要子类重写该方法
     *
     * Attempts to acquire in exclusive mode. This method should query
     * if the state of the object permits it to be acquired in the
     * exclusive mode, and if so to acquire it.
     *
     * <p>This method is always invoked by the thread performing
     * acquire.  If this method reports failure, the acquire method
     * may queue the thread, if it is not already queued, until it is
     * signalled by a release from some other thread. This can be used
     * to implement method {@link Lock#tryLock()}.
     *
     * <p>The default
     * implementation throws {@link UnsupportedOperationException}.
     *
     * @param arg the acquire argument. This value is always the one
     *        passed to an acquire method, or is the value saved on entry
     *        to a condition wait.  The value is otherwise uninterpreted
     *        and can represent anything you like.
     * @return {@code true} if successful. Upon success, this object has
     *         been acquired.
     * @throws IllegalMonitorStateException if acquiring would place this
     *         synchronizer in an illegal state. This exception must be
     *         thrown in a consistent fashion for synchronization to work
     *         correctly.
     * @throws UnsupportedOperationException if exclusive mode is not supported
     */
    protected boolean tryAcquire(int arg) {
        throw new UnsupportedOperationException();
    }

    /**
     * 需要子类重写该方法
     *
     * Attempts to set the state to reflect a release in exclusive
     * mode.
     *
     * <p>This method is always invoked by the thread performing release.
     *
     * <p>The default implementation throws
     * {@link UnsupportedOperationException}.
     *
     * @param arg the release argument. This value is always the one
     *        passed to a release method, or the current state value upon
     *        entry to a condition wait.  The value is otherwise
     *        uninterpreted and can represent anything you like.
     * @return {@code true} if this object is now in a fully released
     *         state, so that any waiting threads may attempt to acquire;
     *         and {@code false} otherwise.
     * @throws IllegalMonitorStateException if releasing would place this
     *         synchronizer in an illegal state. This exception must be
     *         thrown in a consistent fashion for synchronization to work
     *         correctly.
     * @throws UnsupportedOperationException if exclusive mode is not supported
     */
    protected boolean tryRelease(int arg) {
        throw new UnsupportedOperationException();
    }

    /**
     * 需要子类重写该方法
     * 共享模式下获取锁
     * 返回一个整型值
     * &lt; 0：当前线程获取锁失败
     * = 0：当前线程获取锁成功，后续节点也没有成功获取锁
     * &gt; 0：当前线程获取锁成功，后续节点在共享模式下的获取也可能成功
     *
     * Attempts to acquire in shared mode. This method should query if
     * the state of the object permits it to be acquired in the shared
     * mode, and if so to acquire it.
     *
     * <p>This method is always invoked by the thread performing
     * acquire.  If this method reports failure, the acquire method
     * may queue the thread, if it is not already queued, until it is
     * signalled by a release from some other thread.
     *
     * <p>The default implementation throws {@link
     * UnsupportedOperationException}.
     *
     * @param arg the acquire argument. This value is always the one
     *        passed to an acquire method, or is the value saved on entry
     *        to a condition wait.  The value is otherwise uninterpreted
     *        and can represent anything you like.
     * @return a negative value on failure; zero if acquisition in shared
     *         mode succeeded but no subsequent shared-mode acquire can
     *         succeed; and a positive value if acquisition in shared
     *         mode succeeded and subsequent shared-mode acquires might
     *         also succeed, in which case a subsequent waiting thread
     *         must check availability. (Support for three different
     *         return values enables this method to be used in contexts
     *         where acquires only sometimes act exclusively.)  Upon
     *         success, this object has been acquired.
     * @throws IllegalMonitorStateException if acquiring would place this
     *         synchronizer in an illegal state. This exception must be
     *         thrown in a consistent fashion for synchronization to work
     *         correctly.
     * @throws UnsupportedOperationException if shared mode is not supported
     */
    protected int tryAcquireShared(int arg) {
        throw new UnsupportedOperationException();
    }

    /**
     * 需要子类重写该方法
     * 共享模式下释放锁
     *
     * Attempts to set the state to reflect a release in shared mode.
     *
     * <p>This method is always invoked by the thread performing release.
     *
     * <p>The default implementation throws
     * {@link UnsupportedOperationException}.
     *
     * @param arg the release argument. This value is always the one
     *        passed to a release method, or the current state value upon
     *        entry to a condition wait.  The value is otherwise
     *        uninterpreted and can represent anything you like.
     * @return {@code true} if this release of shared mode may permit a
     *         waiting acquire (shared or exclusive) to succeed; and
     *         {@code false} otherwise
     * @throws IllegalMonitorStateException if releasing would place this
     *         synchronizer in an illegal state. This exception must be
     *         thrown in a consistent fashion for synchronization to work
     *         correctly.
     * @throws UnsupportedOperationException if shared mode is not supported
     */
    protected boolean tryReleaseShared(int arg) {
        throw new UnsupportedOperationException();
    }

    /**
     * 需要子类重写该方法
     * 返回是否被当前线程独占
     *
     * Returns {@code true} if synchronization is held exclusively with
     * respect to the current (calling) thread.  This method is invoked
     * upon each call to a {@link ConditionObject} method.
     *
     * <p>The default implementation throws {@link
     * UnsupportedOperationException}. This method is invoked
     * internally only within {@link ConditionObject} methods, so need
     * not be defined if conditions are not used.
     *
     * @return {@code true} if synchronization is held exclusively;
     *         {@code false} otherwise
     * @throws UnsupportedOperationException if conditions are not supported
     */
    protected boolean isHeldExclusively() {
        throw new UnsupportedOperationException();
    }

    /**
     * 获取锁的逻辑
     *
     * Acquires in exclusive mode, ignoring interrupts.  Implemented
     * by invoking at least once {@link #tryAcquire},
     * returning on success.  Otherwise the thread is queued, possibly
     * repeatedly blocking and unblocking, invoking {@link
     * #tryAcquire} until success.  This method can be used
     * to implement method {@link Lock#lock}.
     *
     * @param arg the acquire argument.  This value is conveyed to
     *        {@link #tryAcquire} but is otherwise uninterpreted and
     *        can represent anything you like.
     */
    public final void acquire(int arg) {
        // 先通过 tryAcquire 获取，tryAcquire 由子类实现
        // 如果 tryAcquire 没有获取到锁
        if (!tryAcquire(arg) && acquireQueued(addWaiter(Node.EXCLUSIVE), arg)) {
            // 被中断了才会进入这个逻辑
            selfInterrupt();
        }
    }

    /**
     * 独占模式下获取锁，响应中断
     *
     * Acquires in exclusive mode, aborting if interrupted.
     * Implemented by first checking interrupt status, then invoking
     * at least once {@link #tryAcquire}, returning on
     * success.  Otherwise the thread is queued, possibly repeatedly
     * blocking and unblocking, invoking {@link #tryAcquire}
     * until success or the thread is interrupted.  This method can be
     * used to implement method {@link Lock#lockInterruptibly}.
     *
     * @param arg the acquire argument.  This value is conveyed to
     *        {@link #tryAcquire} but is otherwise uninterpreted and
     *        can represent anything you like.
     * @throws InterruptedException if the current thread is interrupted
     */
    public final void acquireInterruptibly(int arg)
            throws InterruptedException {
        // 先检查中断
        if (Thread.interrupted())
            // 如果被中断了，则直接抛出异常
            throw new InterruptedException();
        // 尝试获取锁
        if (!tryAcquire(arg))
            // 获取失败的情况
            doAcquireInterruptibly(arg);
    }

    /**
     * 独占模式下，带超时时间的获取锁
     * 首先马上尝试一次获取锁，支持抢占
     * 如果第一次获取未获取到，则进入队列
     * 在指定时间内获取到，则返回 true，否则返回 false
     *
     * Attempts to acquire in exclusive mode, aborting if interrupted,
     * and failing if the given timeout elapses.  Implemented by first
     * checking interrupt status, then invoking at least once {@link
     * #tryAcquire}, returning on success.  Otherwise, the thread is
     * queued, possibly repeatedly blocking and unblocking, invoking
     * {@link #tryAcquire} until success or the thread is interrupted
     * or the timeout elapses.  This method can be used to implement
     * method {@link Lock#tryLock(long, TimeUnit)}.
     *
     * @param arg the acquire argument.  This value is conveyed to
     *        {@link #tryAcquire} but is otherwise uninterpreted and
     *        can represent anything you like.
     * @param nanosTimeout the maximum number of nanoseconds to wait
     * @return {@code true} if acquired; {@code false} if timed out
     * @throws InterruptedException if the current thread is interrupted
     */
    public final boolean tryAcquireNanos(int arg, long nanosTimeout)
            throws InterruptedException {
        // 先检查中断
        if (Thread.interrupted())
            // 如果发生中断，则抛出异常
            throw new InterruptedException();
        // 先尝试一次 acquire
        // 如果 tryAcquire 失败，则调用 doAcquireNanos 超时获取
        return tryAcquire(arg) ||
            doAcquireNanos(arg, nanosTimeout);
    }

    /**
     * 独占模式下释放锁
     *
     * Releases in exclusive mode.  Implemented by unblocking one or
     * more threads if {@link #tryRelease} returns true.
     * This method can be used to implement method {@link Lock#unlock}.
     *
     * @param arg the release argument.  This value is conveyed to
     *        {@link #tryRelease} but is otherwise uninterpreted and
     *        can represent anything you like.
     * @return the value returned from {@link #tryRelease} 返回锁是否完全释放
     */
    public final boolean release(int arg) {
        // 返回锁是否完全释放
        if (tryRelease(arg)) {
            Node h = head;
            // h != null 说明已经发生过冲突
            // h.waitStatus  != 0 说明 只能为 SIGNAL 或者 PROPAGATE
            // 只有新建节点的 waitStatus 为 0
            // 需要唤醒后继节点
            if (h != null && h.waitStatus != 0)
                unparkSuccessor(h);
            return true;
        }
        // 锁并没有完全释放
        return false;
    }

    /**
     * 一般由子类的 tryAcquireShared 方法调用
     *
     * Acquires in shared mode, ignoring interrupts.  Implemented by
     * first invoking at least once {@link #tryAcquireShared},
     * returning on success.  Otherwise the thread is queued, possibly
     * repeatedly blocking and unblocking, invoking {@link
     * #tryAcquireShared} until success.
     *
     * @param arg the acquire argument.  This value is conveyed to
     *        {@link #tryAcquireShared} but is otherwise uninterpreted
     *        and can represent anything you like.
     */
    public final void acquireShared(int arg) {
        // 首先调用 tryAcquireShared 尝试一次获取
        // < 0 获取失败
        if (tryAcquireShared(arg) < 0)
            doAcquireShared(arg);
    }

    /**
     * 共享模式下获取锁，支持中断
     *
     * Acquires in shared mode, aborting if interrupted.  Implemented
     * by first checking interrupt status, then invoking at least once
     * {@link #tryAcquireShared}, returning on success.  Otherwise the
     * thread is queued, possibly repeatedly blocking and unblocking,
     * invoking {@link #tryAcquireShared} until success or the thread
     * is interrupted.
     * @param arg the acquire argument.
     * This value is conveyed to {@link #tryAcquireShared} but is
     * otherwise uninterpreted and can represent anything
     * you like.
     * @throws InterruptedException if the current thread is interrupted
     */
    public final void acquireSharedInterruptibly(int arg)
            throws InterruptedException {
        if (Thread.interrupted())
            throw new InterruptedException();
        if (tryAcquireShared(arg) < 0)
            doAcquireSharedInterruptibly(arg);
    }

    /**
     * 共享模式下带超时时间的获取锁
     *
     * Attempts to acquire in shared mode, aborting if interrupted, and
     * failing if the given timeout elapses.  Implemented by first
     * checking interrupt status, then invoking at least once {@link
     * #tryAcquireShared}, returning on success.  Otherwise, the
     * thread is queued, possibly repeatedly blocking and unblocking,
     * invoking {@link #tryAcquireShared} until success or the thread
     * is interrupted or the timeout elapses.
     *
     * @param arg the acquire argument.  This value is conveyed to
     *        {@link #tryAcquireShared} but is otherwise uninterpreted
     *        and can represent anything you like.
     * @param nanosTimeout the maximum number of nanoseconds to wait
     * @return {@code true} if acquired; {@code false} if timed out
     * @throws InterruptedException if the current thread is interrupted
     */
    public final boolean tryAcquireSharedNanos(int arg, long nanosTimeout)
            throws InterruptedException {
        if (Thread.interrupted())
            throw new InterruptedException();
        return tryAcquireShared(arg) >= 0 ||
            doAcquireSharedNanos(arg, nanosTimeout);
    }

    /**
     * 共享模式下释放锁
     *
     * Releases in shared mode.  Implemented by unblocking one or more
     * threads if {@link #tryReleaseShared} returns true.
     *
     * @param arg the release argument.  This value is conveyed to
     *        {@link #tryReleaseShared} but is otherwise uninterpreted
     *        and can represent anything you like.
     * @return the value returned from {@link #tryReleaseShared}
     */
    public final boolean releaseShared(int arg) {
        if (tryReleaseShared(arg)) {
            doReleaseShared();
            return true;
        }
        return false;
    }

    // Queue inspection methods

    /**
     * 是否有线程在队列中等待
     *
     * Queries whether any threads are waiting to acquire. Note that
     * because cancellations due to interrupts and timeouts may occur
     * at any time, a {@code true} return does not guarantee that any
     * other thread will ever acquire.
     *
     * @return {@code true} if there may be other threads waiting to acquire
     */
    public final boolean hasQueuedThreads() {
        for (Node p = tail, h = head; p != h && p != null; p = p.prev)
            if (p.waitStatus <= 0)
                return true;
        return false;
    }

    /**
     * 返回是否产生过竞争
     *
     * Queries whether any threads have ever contended to acquire this
     * synchronizer; that is, if an acquire method has ever blocked.
     *
     * <p>In this implementation, this operation returns in
     * constant time.
     *
     * @return {@code true} if there has ever been contention
     */
    public final boolean hasContended() {
        return head != null;
    }

    /**
     * 获取队列中的第一个线程
     *
     * Returns the first (longest-waiting) thread in the queue, or
     * {@code null} if no threads are currently queued.
     *
     * <p>In this implementation, this operation normally returns in
     * constant time, but may iterate upon contention if other threads are
     * concurrently modifying the queue.
     *
     * @return the first (longest-waiting) thread in the queue, or
     *         {@code null} if no threads are currently queued
     */
    public final Thread getFirstQueuedThread() {
        // handle only fast path, else relay
        return (head == tail) ? null : fullGetFirstQueuedThread();
    }

    /**
     * Version of getFirstQueuedThread called when fastpath fails.
     */
    private Thread fullGetFirstQueuedThread() {
        /*
         * The first node is normally head.next. Try to get its
         * thread field, ensuring consistent reads: If thread
         * field is nulled out or s.prev is no longer head, then
         * some other thread(s) concurrently performed setHead in
         * between some of our reads. We try this twice before
         * resorting to traversal.
         */
        Node h, s;
        Thread st;
        if (((h = head) != null && (s = h.next) != null &&
             s.prev == head && (st = s.thread) != null) ||
                // 执行两次
            ((h = head) != null && (s = h.next) != null &&
             s.prev == head && (st = s.thread) != null))
            return st;

        /*
         * Head's next field might not have been set yet, or may have
         * been unset after setHead. So we must check to see if tail
         * is actually first node. If not, we continue on, safely
         * traversing from tail back to head to find first,
         * guaranteeing termination.
         */

        // 如果上面并发修改了
        // 从 tail 往前找
        Thread firstThread = null;
        for (Node p = tail; p != null && p != head; p = p.prev) {
            Thread t = p.thread;
            if (t != null)
                firstThread = t;
        }
        return firstThread;
    }

    /**
     * Returns true if the given thread is currently queued.
     *
     * <p>This implementation traverses the queue to determine
     * presence of the given thread.
     *
     * @param thread the thread
     * @return {@code true} if the given thread is on the queue
     * @throws NullPointerException if the thread is null
     */
    public final boolean isQueued(Thread thread) {
        if (thread == null)
            throw new NullPointerException();
        for (Node p = tail; p != null; p = p.prev)
            if (p.thread == thread)
                return true;
        return false;
    }

    /**
     * Returns {@code true} if the apparent first queued thread, if one
     * exists, is waiting in exclusive mode.  If this method returns
     * {@code true}, and the current thread is attempting to acquire in
     * shared mode (that is, this method is invoked from {@link
     * #tryAcquireShared}) then it is guaranteed that the current thread
     * is not the first queued thread.  Used only as a heuristic in
     * ReentrantReadWriteLock.
     */
    final boolean apparentlyFirstQueuedIsExclusive() {
        Node h, s;
        return (h = head) != null &&
            (s = h.next)  != null &&
            !s.isShared()         &&
            s.thread != null;
    }

    /**
     * 返回队列中是否已经有在等待锁的线程
     * canceled 的节点不算
     *
     * Queries whether any threads have been waiting to acquire longer
     * than the current thread.
     *
     * <p>An invocation of this method is equivalent to (but may be
     * more efficient than):
     * <pre> {@code
     * getFirstQueuedThread() != Thread.currentThread()
     *   && hasQueuedThreads()}</pre>
     *
     * <p>Note that because cancellations due to interrupts and
     * timeouts may occur at any time, a {@code true} return does not
     * guarantee that some other thread will acquire before the current
     * thread.  Likewise, it is possible for another thread to win a
     * race to enqueue after this method has returned {@code false},
     * due to the queue being empty.
     *
     * <p>This method is designed to be used by a fair synchronizer to
     * avoid <a href="AbstractQueuedSynchronizer.html#barging">barging</a>.
     * Such a synchronizer's {@link #tryAcquire} method should return
     * {@code false}, and its {@link #tryAcquireShared} method should
     * return a negative value, if this method returns {@code true}
     * (unless this is a reentrant acquire).  For example, the {@code
     * tryAcquire} method for a fair, reentrant, exclusive mode
     * synchronizer might look like this:
     *
     * <pre> {@code
     * protected boolean tryAcquire(int arg) {
     *   if (isHeldExclusively()) {
     *     // A reentrant acquire; increment hold count
     *     return true;
     *   } else if (hasQueuedPredecessors()) {
     *     return false;
     *   } else {
     *     // try to acquire normally
     *   }
     * }}</pre>
     *
     * @return {@code true} if there is a queued thread preceding the
     *         current thread, and {@code false} if the current thread
     *         is at the head of the queue or the queue is empty
     * @since 1.7
     */
    public final boolean hasQueuedPredecessors() {
        Node h, s;
        if ((h = head) != null) {
            if ((s = h.next) == null || s.waitStatus > 0) {
                s = null; // traverse in case of concurrent cancellation
                for (Node p = tail; p != h && p != null; p = p.prev) {
                    if (p.waitStatus <= 0)
                        s = p;
                }
            }
            if (s != null && s.thread != Thread.currentThread())
                return true;
        }
        return false;
    }

    // Instrumentation and monitoring methods

    /**
     * Returns an estimate of the number of threads waiting to
     * acquire.  The value is only an estimate because the number of
     * threads may change dynamically while this method traverses
     * internal data structures.  This method is designed for use in
     * monitoring system state, not for synchronization control.
     *
     * @return the estimated number of threads waiting to acquire
     */
    public final int getQueueLength() {
        int n = 0;
        for (Node p = tail; p != null; p = p.prev) {
            if (p.thread != null)
                ++n;
        }
        return n;
    }

    /**
     * Returns a collection containing threads that may be waiting to
     * acquire.  Because the actual set of threads may change
     * dynamically while constructing this result, the returned
     * collection is only a best-effort estimate.  The elements of the
     * returned collection are in no particular order.  This method is
     * designed to facilitate construction of subclasses that provide
     * more extensive monitoring facilities.
     *
     * @return the collection of threads
     */
    public final Collection<Thread> getQueuedThreads() {
        ArrayList<Thread> list = new ArrayList<>();
        for (Node p = tail; p != null; p = p.prev) {
            Thread t = p.thread;
            if (t != null)
                list.add(t);
        }
        return list;
    }

    /**
     * Returns a collection containing threads that may be waiting to
     * acquire in exclusive mode. This has the same properties
     * as {@link #getQueuedThreads} except that it only returns
     * those threads waiting due to an exclusive acquire.
     *
     * @return the collection of threads
     */
    public final Collection<Thread> getExclusiveQueuedThreads() {
        ArrayList<Thread> list = new ArrayList<>();
        for (Node p = tail; p != null; p = p.prev) {
            if (!p.isShared()) {
                Thread t = p.thread;
                if (t != null)
                    list.add(t);
            }
        }
        return list;
    }

    /**
     * Returns a collection containing threads that may be waiting to
     * acquire in shared mode. This has the same properties
     * as {@link #getQueuedThreads} except that it only returns
     * those threads waiting due to a shared acquire.
     *
     * @return the collection of threads
     */
    public final Collection<Thread> getSharedQueuedThreads() {
        ArrayList<Thread> list = new ArrayList<>();
        for (Node p = tail; p != null; p = p.prev) {
            if (p.isShared()) {
                Thread t = p.thread;
                if (t != null)
                    list.add(t);
            }
        }
        return list;
    }

    /**
     * Returns a string identifying this synchronizer, as well as its state.
     * The state, in brackets, includes the String {@code "State ="}
     * followed by the current value of {@link #getState}, and either
     * {@code "nonempty"} or {@code "empty"} depending on whether the
     * queue is empty.
     *
     * @return a string identifying this synchronizer, as well as its state
     */
    public String toString() {
        return super.toString()
            + "[State = " + getState() + ", "
            + (hasQueuedThreads() ? "non" : "") + "empty queue]";
    }


    // Internal support methods for Conditions

    /**
     * 返回 node 是否一开始在 condition queue，后来 transfer 到 sync queue
     *
     * Returns true if a node, always one that was initially placed on
     * a condition queue, is now waiting to reacquire on sync queue.
     * @param node the node
     * @return true if is reacquiring
     */
    final boolean isOnSyncQueue(Node node) {
        // 还在 condition queue 中，直接返回 false
        // pre 为 null 说明肯定不在 sync queue 中，因为 pre 指针是最早进行更新的
        if (node.waitStatus == Node.CONDITION || node.prev == null)
            return false;
        // next 指针不为 null，那么一定仅 syn queue 了。
        // 因为在 进队操作中， 更新 next 指针是最后的步骤
        if (node.next != null) // If has successor, it must be on queue
            return true;
        /*
         * 中间情况
         * node.prev can be non-null, but not yet on queue because
         * the CAS to place it on queue can fail. So we have to
         * traverse from tail to make sure it actually made it.  It
         * will always be near the tail in calls to this method, and
         * unless the CAS failed (which is unlikely), it will be
         * there, so we hardly ever traverse much.
         */
        return findNodeFromTail(node);
    }

    /**
     * 从 tail 往前找，返回是否能够找到 node
     *
     * Returns true if node is on sync queue by searching backwards from tail.
     * Called only when needed by isOnSyncQueue.
     * @return true if present
     */
    private boolean findNodeFromTail(Node node) {
        // We check for node first, since it's likely to be at or near tail.
        // tail is known to be non-null, so we could re-order to "save"
        // one null check, but we leave it this way to help the VM.
        for (Node p = tail;;) {
            if (p == node)
                return true;
            if (p == null)
                return false;
            p = p.prev;
        }
    }

    /**
     * 将 condition queue 中的节点转换成 sync queue 中的节点
     *
     * Transfers a node from a condition queue onto sync queue.
     * Returns true if successful.
     * @param node the node
     * @return true if successfully transferred (else the node was
     * cancelled before signal)
     */
    final boolean transferForSignal(Node node) {
        /*
         * If cannot change waitStatus, the node has been cancelled.
         * 将要 transfer 的节点的 status 转为 0
         * 还有一种情况就是已经 canceled 了 状态为 cancel，直接不管
         */
        if (!node.compareAndSetWaitStatus(Node.CONDITION, 0))
            return false;

        /*
         * 入 sync queue
         * Splice onto queue and try to set waitStatus of predecessor to
         * indicate that thread is (probably) waiting. If cancelled or
         * attempt to set waitStatus fails, wake up to resync (in which
         * case the waitStatus can be transiently and harmlessly wrong).
         */
        // 返回前驱结点
        Node p = enq(node);
        // 前驱节点的状态
        int ws = p.waitStatus;
        // 前驱节点被取消
        // 或者不能成功将前驱节点的 status 设置成 SIGNAL
        // 当前节点还不能 park，要唤醒，继续尝试获取锁
        if (ws > 0 || !p.compareAndSetWaitStatus(ws, Node.SIGNAL))
            // 继续活跃
            LockSupport.unpark(node.thread);
        return true;
    }

    /**
     * 在 signal 之前如果被 cancel 则返回 true，这里的 cancel 可能是中断
     *
     * Transfers node, if necessary, to sync queue after a cancelled wait.
     * Returns true if thread was cancelled before being signalled.
     *
     * @param node the node
     * @return true if cancelled before the node was signalled
     */
    final boolean transferAfterCancelledWait(Node node) {
        // 如果 cas 成功，说明没有被 signal
        // 如果 status 不为 CONDITION，说明被 signal 了，但是在 signal 之后，马上被中断了，才会进入到该方法中
        if (node.compareAndSetWaitStatus(Node.CONDITION, 0)) {
            enq(node);
            return true;
        }
        // 当前线程可能正在进入 sync queue 的路上，或者已经进入到 sync queue 了
        /*
         * 自旋判断另一个线程是否已经将 node 入 sync queue
         * If we lost out to a signal(), then we can't proceed
         * until it finishes its enq().  Cancelling during an
         * incomplete transfer is both rare and transient, so just
         * spin.
         */
        while (!isOnSyncQueue(node))
            // 暂时放弃 cpu，等待重新调度
            Thread.yield();
        // 最终返回 false
        return false;
    }

    /**
     * 释放锁
     *
     * Invokes release with current state value; returns saved state.
     * Cancels node and throws exception on failure.
     * @param node the condition node for this wait
     * @return previous sync state 返回之前的锁状态
     */
    final int fullyRelease(Node node) {
        try {
            // 当前的锁状态
            int savedState = getState();
            // 释放动作
            if (release(savedState))
                return savedState;
            throw new IllegalMonitorStateException();
        } catch (Throwable t) {
            node.waitStatus = Node.CANCELLED;
            throw t;
        }
    }

    // Instrumentation methods for conditions

    /**
     * Queries whether the given ConditionObject
     * uses this synchronizer as its lock.
     *
     * @param condition the condition
     * @return {@code true} if owned
     * @throws NullPointerException if the condition is null
     */
    public final boolean owns(ConditionObject condition) {
        return condition.isOwnedBy(this);
    }

    /**
     * Queries whether any threads are waiting on the given condition
     * associated with this synchronizer. Note that because timeouts
     * and interrupts may occur at any time, a {@code true} return
     * does not guarantee that a future {@code signal} will awaken
     * any threads.  This method is designed primarily for use in
     * monitoring of the system state.
     *
     * @param condition the condition
     * @return {@code true} if there are any waiting threads
     * @throws IllegalMonitorStateException if exclusive synchronization
     *         is not held
     * @throws IllegalArgumentException if the given condition is
     *         not associated with this synchronizer
     * @throws NullPointerException if the condition is null
     */
    public final boolean hasWaiters(ConditionObject condition) {
        if (!owns(condition))
            throw new IllegalArgumentException("Not owner");
        return condition.hasWaiters();
    }

    /**
     * Returns an estimate of the number of threads waiting on the
     * given condition associated with this synchronizer. Note that
     * because timeouts and interrupts may occur at any time, the
     * estimate serves only as an upper bound on the actual number of
     * waiters.  This method is designed for use in monitoring system
     * state, not for synchronization control.
     *
     * @param condition the condition
     * @return the estimated number of waiting threads
     * @throws IllegalMonitorStateException if exclusive synchronization
     *         is not held
     * @throws IllegalArgumentException if the given condition is
     *         not associated with this synchronizer
     * @throws NullPointerException if the condition is null
     */
    public final int getWaitQueueLength(ConditionObject condition) {
        if (!owns(condition))
            throw new IllegalArgumentException("Not owner");
        return condition.getWaitQueueLength();
    }

    /**
     * Returns a collection containing those threads that may be
     * waiting on the given condition associated with this
     * synchronizer.  Because the actual set of threads may change
     * dynamically while constructing this result, the returned
     * collection is only a best-effort estimate. The elements of the
     * returned collection are in no particular order.
     *
     * @param condition the condition
     * @return the collection of threads
     * @throws IllegalMonitorStateException if exclusive synchronization
     *         is not held
     * @throws IllegalArgumentException if the given condition is
     *         not associated with this synchronizer
     * @throws NullPointerException if the condition is null
     */
    public final Collection<Thread> getWaitingThreads(ConditionObject condition) {
        if (!owns(condition))
            throw new IllegalArgumentException("Not owner");
        return condition.getWaitingThreads();
    }

    /**
     * 实现了 Condition 接口
     * condition queue 中没有 dummy node
     *
     * Condition implementation for a {@link AbstractQueuedSynchronizer}
     * serving as the basis of a {@link Lock} implementation.
     *
     * <p>Method documentation for this class describes mechanics,
     * not behavioral specifications from the point of view of Lock
     * and Condition users. Exported versions of this class will in
     * general need to be accompanied by documentation describing
     * condition semantics that rely on those of the associated
     * {@code AbstractQueuedSynchronizer}.
     *
     * <p>This class is Serializable, but all fields are transient,
     * so deserialized conditions have no waiters.
     */
    public class ConditionObject implements Condition, java.io.Serializable {
        private static final long serialVersionUID = 1173984872572414699L;
        /** First node of condition queue. */
        // condition queue 中的第一个节点
        private transient Node firstWaiter;
        /** Last node of condition queue. */
        // condition queue 中的最后一个节点
        private transient Node lastWaiter;

        /**
         * Creates a new {@code ConditionObject} instance.
         */
        public ConditionObject() { }

        // Internal methods

        /**
         * 只有获得了锁的线程才能进入到 condition queue
         * 因此不存在竞争
         * 线程入 condition queue
         *
         * Adds a new waiter to wait queue.
         * @return its new wait node
         */
        private Node addConditionWaiter() {
            // 当前线程必须得持有锁
            if (!isHeldExclusively())
                throw new IllegalMonitorStateException();
            Node t = lastWaiter;
            // If lastWaiter is cancelled, clean out.
            // 检查一下 lastWaiter 是否是 canceled 的节点
            if (t != null && t.waitStatus != Node.CONDITION) {
                // 清楚所有的被 canceled 的节点
                unlinkCancelledWaiters();
                // 指向新的 lastWaiter
                t = lastWaiter;
            }
            // 创建一个 Condition Node
            Node node = new Node(Node.CONDITION);

            if (t == null)
                firstWaiter = node;
            else
                t.nextWaiter = node;
            lastWaiter = node;
            return node;
        }

        /**
         * 唤醒并转换一个非 cancelled 节点
         *
         * Removes and transfers nodes until hit non-cancelled one or
         * null. Split out from signal in part to encourage compilers
         * to inline the case of no waiters.
         * @param first (non-null) the first node on condition queue
         */
        private void doSignal(Node first) {
            do {
                // firstWaiter 指向原来头结点的下一个节点
                if ( (firstWaiter = first.nextWaiter) == null)
                    // condition queue 为空了，更新一下 lastWaiter
                    lastWaiter = null;
                // 将出队的节点的 next 置为 null
                first.nextWaiter = null;
            } while (!transferForSignal(first) &&
                    // 重新获取头结点
                     (first = firstWaiter) != null);
        }

        /**
         * 移除 condition queue 中的所有元素
         * 并转移到 sync queue 中
         *
         * Removes and transfers all nodes.
         * @param first (non-null) the first node on condition queue
         */
        private void doSignalAll(Node first) {
            // 全部转移完之后 lastWaiter 与 firstWaiter 肯定指向 null
            // 这里提前更新这两个值
            lastWaiter = firstWaiter = null;
            // 调用 do-while 是因为前面已经确保了 first 不为 null
            do {
                Node next = first.nextWaiter;
                // nextWaiter 置为 null
                first.nextWaiter = null;
                // 进行 transfer
                transferForSignal(first);
                first = next;
            } while (first != null);
        }

        /**
         * 从 condition queue 中移除 cancel 的节点
         *
         * Unlinks cancelled waiter nodes from condition queue.
         * Called only while holding lock. This is called when
         * cancellation occurred during condition wait, and upon
         * insertion of a new waiter when lastWaiter is seen to have
         * been cancelled. This method is needed to avoid garbage
         * retention in the absence of signals. So even though it may
         * require a full traversal, it comes into play only when
         * timeouts or cancellations occur in the absence of
         * signals. It traverses all nodes rather than stopping at a
         * particular target to unlink all pointers to garbage nodes
         * without requiring many re-traversals during cancellation
         * storms.
         */
        private void unlinkCancelledWaiters() {
            // t 保存当前遍历的节点
            Node t = firstWaiter;
            // 保留的 node
            Node trail = null;
            while (t != null) {
                Node next = t.nextWaiter;
                if (t.waitStatus != Node.CONDITION) {
                    t.nextWaiter = null;
                    if (trail == null)
                        firstWaiter = next;
                    else
                        trail.nextWaiter = next;
                    // 单独需要处理 lastWaiter
                    if (next == null)
                        lastWaiter = trail;
                }
                // 往后遍历
                else
                    trail = t;
                t = next;
            }
        }

        // public methods

        /**
         * 唤醒在 condition queue 中等待时间最长的没有被 cancelled 的节点
         *
         * Moves the longest-waiting thread, if one exists, from the
         * wait queue for this condition to the wait queue for the
         * owning lock.
         *
         * @throws IllegalMonitorStateException if {@link #isHeldExclusively}
         *         returns {@code false}
         */
        public final void signal() {
            if (!isHeldExclusively())
                throw new IllegalMonitorStateException();
            // 第一个节点
            Node first = firstWaiter;
            if (first != null)
                doSignal(first);
        }

        /**
         * 唤醒所有的 node
         * 把 condition queue 中的 node 转移到 sync queue 中
         *
         * Moves all threads from the wait queue for this condition to
         * the wait queue for the owning lock.
         *
         * @throws IllegalMonitorStateException if {@link #isHeldExclusively}
         *         returns {@code false}
         */
        public final void signalAll() {
            // 调用 sugnalAll 的线程必须持有锁
            if (!isHeldExclusively())
                throw new IllegalMonitorStateException();
            Node first = firstWaiter;
            if (first != null)
                // 执行 signalAll 的逻辑
                doSignalAll(first);
        }

        /**
         * 不可中断的 await 方法
         *
         * Implements uninterruptible condition wait.
         * <ol>
         * <li>Save lock state returned by {@link #getState}.
         * <li>Invoke {@link #release} with saved state as argument,
         *     throwing IllegalMonitorStateException if it fails.
         * <li>Block until signalled.
         * <li>Reacquire by invoking specialized version of
         *     {@link #acquire} with saved state as argument.
         * </ol>
         */
        public final void awaitUninterruptibly() {
            // 当前线程读 condition queue
            Node node = addConditionWaiter();
            // 释放当前全部持有的锁
            int savedState = fullyRelease(node);
            // 中断标识
            boolean interrupted = false;
            // 跳出循环的唯一条件是通过 signal 然后进入到 sync queue 中
            while (!isOnSyncQueue(node)) {
                LockSupport.park(this);
                // 如果被中断从 park 中唤醒
                // 啥也不做，只是记录以下被中断了
                if (Thread.interrupted())
                    interrupted = true;
            }
            // 在 sync queue 中尝试获取锁
            if (acquireQueued(node, savedState) || interrupted)
                // 如果发生过中断，则再次自我中断
                selfInterrupt();
        }

        /*
         * For interruptible waits, we need to track whether to throw
         * InterruptedException, if interrupted while blocked on
         * condition, versus reinterrupt current thread, if
         * interrupted while blocked waiting to re-acquire.
         */

        /** Mode meaning to reinterrupt on exit from wait */
        /**
         * 退出 await 方法时需要再自我中断一下，中断发生在 signal 之后
         */
        private static final int REINTERRUPT =  1;
        /** Mode meaning to throw InterruptedException on exit from wait */
        /**
         * 退出 await 方法时需要抛出 IE，表示中断发生在 signal 之前，或者说就没有发生过 signal
         * 是有 中断唤醒的，因此还需要抛出 IE
         */
        private static final int THROW_IE    = -1;

        /**
         * Checks for interrupt, returning THROW_IE if interrupted
         * before signalled, REINTERRUPT if after signalled, or
         * 0 if not interrupted.
         */
        private int checkInterruptWhileWaiting(Node node) {
            // 检查中断标记
            return Thread.interrupted() ?
                    // 如果被中断了，则
                (transferAfterCancelledWait(node) ? THROW_IE : REINTERRUPT) :
                0;
        }

        /**
         * 根据中断状态指定相应的操作
         *
         * Throws InterruptedException, reinterrupts current thread, or
         * does nothing, depending on mode.
         */
        private void reportInterruptAfterWait(int interruptMode)
            throws InterruptedException {
            // THROW_IE 则抛出中断异常
            if (interruptMode == THROW_IE)
                throw new InterruptedException();
            // REINTERRUPT 则自我中断
            else if (interruptMode == REINTERRUPT)
                selfInterrupt();
        }

        /**
         * Implements interruptible condition wait.
         * <ol>
         * <li>If current thread is interrupted, throw InterruptedException.
         * <li>Save lock state returned by {@link #getState}.
         * <li>Invoke {@link #release} with saved state as argument,
         *     throwing IllegalMonitorStateException if it fails.
         * <li>Block until signalled or interrupted.
         * <li>Reacquire by invoking specialized version of
         *     {@link #acquire} with saved state as argument.
         * <li>If interrupted while blocked in step 4, throw InterruptedException.
         * </ol>
         */
        public final void await() throws InterruptedException {
            // 检查中断
            // 如果被中断则直接抛出中断异常
            if (Thread.interrupted())
                throw new InterruptedException();
            // 进 condition queue
            Node node = addConditionWaiter();
            // 进入队列之后可以调用 fullyRelease 释放锁了
            // savedState 之前的锁状态 state 的值
            int savedState = fullyRelease(node);
            // = 0 时表示没有发生中断
            int interruptMode = 0;
            //需要确保 node 不在 sync queue 中才能进行 park
            // 正常通过 signal 会根据这个条件跳出循环
            while (!isOnSyncQueue(node)) {
                // park 进行阻塞
                LockSupport.park(this);
                // 两行代码之间可能发生中断与 signal
                // 或者两者仅发生一个
                // checkInterruptWhileWaiting 返回 0 -> 表示没有发生中断
                // 如果被中断了，直接跳出 while
                // 如果被 unpark，返回 0 ，则继续循环
                // 如果仅仅被 signal 了，并没有中断，那么不会 break，跳出的判断由外面的 isOnSyncQueue 来决定
                // 如果是真正的 signal，那么 node 一定会进入到 sync queue 中
                // 如果是 虚假的 signal，那么不会进入到 sync queue 中没继续 park
                if ((interruptMode = checkInterruptWhileWaiting(node)) != 0)
                    break;
            }
            // 在 sync queue 中获取锁，获取不到还是会被中断
            // acquireQueued 返回是否发生中断
            if (acquireQueued(node, savedState) && interruptMode != THROW_IE)
                interruptMode = REINTERRUPT;
            // 执行到这里说明在 sync queue 中获取到了锁
            // node.nextWaiter 为 null 的情况，前面是通过 signal 唤醒的
            if (node.nextWaiter != null) // clean up if cancelled
                // 将节点从 condition queue 中删除
                unlinkCancelledWaiters();
            // 汇报中断状态
            // 这里总结一下：
            // 1. 如果是通过中断从park 中唤醒，则 await 方法执行完之后，还是会抛出中断异常
            // 2. 如果是 signal 之后出现中断，那么仅仅是在外面再次自我中断一下
            if (interruptMode != 0)
                reportInterruptAfterWait(interruptMode);
        }

        /**
         * 带超时时间的 await
         * 响应中断，也就是中断能够将 node 从 condition queue 中移出
         * 如果返回值 &gt; 0 说明是在指定时间内从 condition queue 到了 sync queue，话句话说，是在指定时间内由 signal/interrupt 唤醒的
         * 如果返回值 == Long.MIN_VALUE，说明是超时之后获取到的锁
         *
         * Implements timed condition wait.
         * <ol>
         * <li>If current thread is interrupted, throw InterruptedException.
         * <li>Save lock state returned by {@link #getState}.
         * <li>Invoke {@link #release} with saved state as argument,
         *     throwing IllegalMonitorStateException if it fails.
         * <li>Block until signalled, interrupted, or timed out.
         * <li>Reacquire by invoking specialized version of
         *     {@link #acquire} with saved state as argument.
         * <li>If interrupted while blocked in step 4, throw InterruptedException.
         * </ol>
         */
        public final long awaitNanos(long nanosTimeout)
                throws InterruptedException {
            // 先检查是否中断，如果发生了中断，则直接抛出中断异常
            if (Thread.interrupted())
                throw new InterruptedException();
            // We don't check for nanosTimeout <= 0L here, to allow
            // awaitNanos(0) as a way to "yield the lock".
            // 计算 ddl
            final long deadline = System.nanoTime() + nanosTimeout;
            // 传入的超时时间
            long initialNanos = nanosTimeout;
            // 进 condition queue
            Node node = addConditionWaiter();
            // 释放锁
            int savedState = fullyRelease(node);
            //
            int interruptMode = 0;
            while (!isOnSyncQueue(node)) {
                // 没有剩余时间了
                if (nanosTimeout <= 0L) {
                    // 直接从 condition queue 移到 sync queue 中
                    transferAfterCancelledWait(node);
                    break;
                }
                // 如果剩余的时间大于 SPIN_FOR_TIMEOUT_THRESHOLD
                // 那么就 park 了
                if (nanosTimeout > SPIN_FOR_TIMEOUT_THRESHOLD)
                    LockSupport.parkNanos(this, nanosTimeout);
                // 走到这里说明剩余的时间小于 SPIN_FOR_TIMEOUT_THRESHOLD，不 park，自旋
                // 检查中断，如果中断了，则直接跳出
                if ((interruptMode = checkInterruptWhileWaiting(node)) != 0)
                    break;
                nanosTimeout = deadline - System.nanoTime();
            }
            // 在 sync queue 中尝试获取锁
            if (acquireQueued(node, savedState) && interruptMode != THROW_IE)
                interruptMode = REINTERRUPT;
            if (node.nextWaiter != null)
                unlinkCancelledWaiters();
            if (interruptMode != 0)
                reportInterruptAfterWait(interruptMode);
            // 剩余的时间
            long remaining = deadline - System.nanoTime(); // avoid overflow
            return (remaining <= initialNanos) ? remaining : Long.MIN_VALUE;
        }

        /**
         * 与 awaitNanos 类似
         * 返回 true 表示在时间内 signal/interrupt 了
         *
         * Implements absolute timed condition wait.
         * <ol>
         * <li>If current thread is interrupted, throw InterruptedException.
         * <li>Save lock state returned by {@link #getState}.
         * <li>Invoke {@link #release} with saved state as argument,
         *     throwing IllegalMonitorStateException if it fails.
         * <li>Block until signalled, interrupted, or timed out.
         * <li>Reacquire by invoking specialized version of
         *     {@link #acquire} with saved state as argument.
         * <li>If interrupted while blocked in step 4, throw InterruptedException.
         * <li>If timed out while blocked in step 4, return false, else true.
         * </ol>
         */
        public final boolean awaitUntil(Date deadline)
                throws InterruptedException {
            // 计算 ddl
            long abstime = deadline.getTime();
            // 响应中断
            if (Thread.interrupted())
                throw new InterruptedException();
            // 入队
            Node node = addConditionWaiter();
            // 完全释放锁
            int savedState = fullyRelease(node);
            boolean timedout = false;
            int interruptMode = 0;
            while (!isOnSyncQueue(node)) {
                // 超时了
                if (System.currentTimeMillis() >= abstime) {
                    timedout = transferAfterCancelledWait(node);
                    break;
                }
                LockSupport.parkUntil(this, abstime);
                if ((interruptMode = checkInterruptWhileWaiting(node)) != 0)
                    break;
            }
            if (acquireQueued(node, savedState) && interruptMode != THROW_IE)
                interruptMode = REINTERRUPT;
            if (node.nextWaiter != null)
                unlinkCancelledWaiters();
            if (interruptMode != 0)
                reportInterruptAfterWait(interruptMode);
            return !timedout;
        }

        /**
         * 与 {@link AbstractQueuedSynchronizer#awaitNanos(long)} 类似
         * await 这个时间指的是从 condition queue 出来的时间，而不是在 sync queue 中获取到锁的时间
         * 返回 true 表示由 指定时间内的 signal/interrupt 从 condition queue 中移出
         *
         * Implements timed condition wait.
         * <ol>
         * <li>If current thread is interrupted, throw InterruptedException.
         * <li>Save lock state returned by {@link #getState}.
         * <li>Invoke {@link #release} with saved state as argument,
         *     throwing IllegalMonitorStateException if it fails.
         * <li>Block until signalled, interrupted, or timed out.
         * <li>Reacquire by invoking specialized version of
         *     {@link #acquire} with saved state as argument.
         * <li>If interrupted while blocked in step 4, throw InterruptedException.
         * <li>If timed out while blocked in step 4, return false, else true.
         * </ol>
         */
        public final boolean await(long time, TimeUnit unit)
                throws InterruptedException {
            // 转成 nanosTime
            long nanosTimeout = unit.toNanos(time);
            // 检查中断，如果有中断直接抛中断异常
            if (Thread.interrupted())
                throw new InterruptedException();
            // We don't check for nanosTimeout <= 0L here, to allow
            // await(0, unit) as a way to "yield the lock".
            // ddl
            final long deadline = System.nanoTime() + nanosTimeout;
            // 入 condition queue
            Node node = addConditionWaiter();
            // 释放锁
            int savedState = fullyRelease(node);
            boolean timedout = false;
            int interruptMode = 0;
            while (!isOnSyncQueue(node)) {
                if (nanosTimeout <= 0L) {
                    timedout = transferAfterCancelledWait(node);
                    break;
                }
                if (nanosTimeout > SPIN_FOR_TIMEOUT_THRESHOLD)
                    LockSupport.parkNanos(this, nanosTimeout);
                if ((interruptMode = checkInterruptWhileWaiting(node)) != 0)
                    break;
                nanosTimeout = deadline - System.nanoTime();
            }
            if (acquireQueued(node, savedState) && interruptMode != THROW_IE)
                interruptMode = REINTERRUPT;
            if (node.nextWaiter != null)
                unlinkCancelledWaiters();
            if (interruptMode != 0)
                reportInterruptAfterWait(interruptMode);
            // 返回是超时获取的还是在指定时间内获取的
            return !timedout;
        }

        //  support for instrumentation

        /**
         * Returns true if this condition was created by the given
         * synchronization object.
         *
         * @return {@code true} if owned
         */
        final boolean isOwnedBy(AbstractQueuedSynchronizer sync) {
            return sync == AbstractQueuedSynchronizer.this;
        }

        /**
         * Queries whether any threads are waiting on this condition.
         * Implements {@link AbstractQueuedSynchronizer#hasWaiters(ConditionObject)}.
         *
         * @return {@code true} if there are any waiting threads
         * @throws IllegalMonitorStateException if {@link #isHeldExclusively}
         *         returns {@code false}
         */
        protected final boolean hasWaiters() {
            if (!isHeldExclusively())
                throw new IllegalMonitorStateException();
            for (Node w = firstWaiter; w != null; w = w.nextWaiter) {
                if (w.waitStatus == Node.CONDITION)
                    return true;
            }
            return false;
        }

        /**
         * Returns an estimate of the number of threads waiting on
         * this condition.
         * Implements {@link AbstractQueuedSynchronizer#getWaitQueueLength(ConditionObject)}.
         *
         * @return the estimated number of waiting threads
         * @throws IllegalMonitorStateException if {@link #isHeldExclusively}
         *         returns {@code false}
         */
        protected final int getWaitQueueLength() {
            if (!isHeldExclusively())
                throw new IllegalMonitorStateException();
            int n = 0;
            for (Node w = firstWaiter; w != null; w = w.nextWaiter) {
                if (w.waitStatus == Node.CONDITION)
                    ++n;
            }
            return n;
        }

        /**
         * Returns a collection containing those threads that may be
         * waiting on this Condition.
         * Implements {@link AbstractQueuedSynchronizer#getWaitingThreads(ConditionObject)}.
         *
         * @return the collection of threads
         * @throws IllegalMonitorStateException if {@link #isHeldExclusively}
         *         returns {@code false}
         */
        protected final Collection<Thread> getWaitingThreads() {
            if (!isHeldExclusively())
                throw new IllegalMonitorStateException();
            ArrayList<Thread> list = new ArrayList<>();
            for (Node w = firstWaiter; w != null; w = w.nextWaiter) {
                if (w.waitStatus == Node.CONDITION) {
                    Thread t = w.thread;
                    if (t != null)
                        list.add(t);
                }
            }
            return list;
        }
    }

    // VarHandle mechanics
    private static final VarHandle STATE;
    private static final VarHandle HEAD;
    private static final VarHandle TAIL;

    static {
        try {
            MethodHandles.Lookup l = MethodHandles.lookup();
            STATE = l.findVarHandle(AbstractQueuedSynchronizer.class, "state", int.class);
            HEAD = l.findVarHandle(AbstractQueuedSynchronizer.class, "head", Node.class);
            TAIL = l.findVarHandle(AbstractQueuedSynchronizer.class, "tail", Node.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }

        // Reduce the risk of rare disastrous classloading in first call to
        // LockSupport.park: https://bugs.openjdk.java.net/browse/JDK-8074773
        Class<?> ensureLoaded = LockSupport.class;
    }

    /**
     * 初始化队列
     *
     * Initializes head and tail fields on first contention.
     */
    private final void initializeSyncQueue() {
        Node h;
        if (HEAD.compareAndSet(this, null, (h = new Node())))
            tail = h;
    }

    /**
     * CASes tail field.
     */
    private final boolean compareAndSetTail(Node expect, Node update) {
        return TAIL.compareAndSet(this, expect, update);
    }
}
