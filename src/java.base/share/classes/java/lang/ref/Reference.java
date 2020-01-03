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

package java.lang.ref;

import jdk.internal.HotSpotIntrinsicCandidate;
import jdk.internal.misc.JavaLangRefAccess;
import jdk.internal.misc.SharedSecrets;
import jdk.internal.ref.Cleaner;
import jdk.internal.vm.annotation.ForceInline;

/**
 * 该类不能直接被继承
 * Abstract base class for reference objects.  This class defines the
 * operations common to all reference objects.  Because reference objects are
 * implemented in close cooperation with the garbage collector, this class may
 * not be subclassed directly.
 *
 * @author Mark Reinhold
 * @since 1.2
 */

public abstract class Reference<T> {

    /**
     * The state of a Reference object is characterized by two attributes.  It
     * may be either "active", "pending", or "inactive".  It may also be
     * either "registered", "enqueued", "dequeued", or "unregistered".
     * <p>
     * Reference 对象的状态可以被两类属性标记。
     * 一类是 active、pending、inactive。
     * 另一类是 registered、enqueued、dequeued、unregistered。
     * <p>
     * Active: Subject to special treatment by the garbage collector.  Some
     * time after the collector detects that the reachability of the
     * referent has changed to the appropriate state, the collector
     * "notifies" the reference, changing the state to either "pending" or
     * "inactive".
     * referent != null; discovered = null, or in GC discovered list.
     * <p>
     * Active 状态：当前 Reference 对象是 Active 状态时，将会被 GC 特殊对待。
     * 当 GC 检测到 referent 的可达性变成适合的状态时，垃圾收集器会"通知"当前引用实例改变其状态为"pending"或者"inactive"。
     * 判断条件为 referent != null; discovered = null; 或者 当前的 Reference 实例在 discovered 列表中。
     * <p>
     * Pending: An element of the pending-Reference list, waiting to be
     * processed by the ReferenceHandler thread.  The pending-Reference
     * list is linked through the discovered fields of references in the
     * list.
     * referent = null; discovered = next element in pending-Reference list.
     * <p>
     * Pending 状态：当前 Reference 对象在 pending-Reference 列表中的时候，处于 Pending 状态，
     * 直到被 ReferenceHandler 线程处理。pending-Reference 列表通过应用实例的 discovered 字段进行关联。
     * [ 换句话说就是 pending-Reference 列表中的元素从 discovered 列表中去取。（后续代码中有这个操作）]
     * 此时的判断条件：referent = null; 且 discovered 变量等于 pending-Reference 列表中的下一个元素。
     * <p>
     * Inactive: Neither Active nor Pending.
     * referent = null.
     * <p>
     * Inactive 状态：非 Active 或者 Pending 状态
     * 此时判断条件为：referent = null，同时 discovered = null
     * <p>
     * Registered: Associated with a queue when created, and not yet added
     * to the queue.
     * queue = the associated queue.
     * <p>
     * Registered 状态：当前引用实例创建的时候关联到了一个队列，但是还没有被加入到队列中
     * 此时的判断条件为：成员变量 queue = 传入到 Reference 类中的 ReferenceQueue
     * <p>
     * Enqueued: Added to the associated queue, and not yet removed.
     * queue = ReferenceQueue.ENQUEUE; next = next entry in list, or this to
     * indicate end of list.
     * <p>
     * Enqueued 状态：当前的实例被加入到了关联的队列（传入的ReferenceQueue），并且还没有被出队。
     * 此时的判断条件为：成员变量 queue = ReferenceQueue.ENQUEUE; 成员变量 next = 引用队列中的下一个元素，或者当前引用已经是最后一个元素了。
     * <p>
     * Dequeued: Added to the associated queue and then removed.
     * queue = ReferenceQueue.NULL; next = this.
     * <p>
     * Dequeued 状态：加入到与其相关的引用队列之后又被移除了。
     * 此时的判断条件为：queue = ReferenceQueue.NULL; next = 当前的实例.
     * <p>
     * Unregistered: Not associated with a queue when created.
     * queue = ReferenceQueue.NULL.
     * <p>
     * Unregistered 实例：当前实例不存在关联的引用队列。
     * 此时的判断条件为 queue = ReferenceQueue.NULL。
     * <p>
     * The collector only needs to examine the referent field and the
     * discovered field to determine whether a (non-FinalReference) Reference
     * object needs special treatment.  If the referent is non-null and not
     * known to be live, then it may need to be discovered for possible later
     * notification.  But if the discovered field is non-null, then it has
     * already been discovered.
     * <p>
     * 收集器只需去检测成员变量 referent 和 成员变量 discovered 就可以确定 Reference
     * 对象是否需要特殊处理。
     * <p>
     * FinalReference (which exists to support finalization) differs from
     * other references, because a FinalReference is not cleared when
     * notified.  The referent being null or not cannot be used to distinguish
     * between the active state and pending or inactive states.  However,
     * FinalReferences do not support enqueue().  Instead, the next field of a
     * FinalReference object is set to "this" when it is added to the
     * pending-Reference list.  The use of "this" as the value of next in the
     * enqueued and dequeued states maintains the non-active state.  An
     * additional check that the next field is null is required to determine
     * that a FinalReference object is active.
     * <p>
     * 初始状态
     * Initial states:
     * [active/registered]
     * 或者
     * [active/unregistered] [1]
     * <p>
     * Transitions:
     * clear
     * [active/registered]     ------->   [inactive/registered]
     * |                                 |
     * |                                 | enqueue [2]
     * | GC              enqueue [2]     |
     * |                -----------------|
     * |                                 |
     * v                                 |
     * [pending/registered]    ---              v
     * |                   | ReferenceHandler
     * | enqueue [2]       |--->   [inactive/enqueued]
     * v                   |             |
     * [pending/enqueued]      ---              |
     * |                                 | poll/remove
     * | poll/remove                     |
     * |                                 |
     * v            ReferenceHandler     v
     * [pending/dequeued]      ------>    [inactive/dequeued]
     * <p>
     * <p>
     * clear/enqueue/GC [3]
     * [active/unregistered]   ------
     * |                      |
     * | GC                   |
     * |                      |--> [inactive/unregistered]
     * v                      |
     * [pending/unregistered]  ------
     * ReferenceHandler
     * <p>
     * 终止状态
     * Terminal states:
     * [inactive/dequeued]
     * [inactive/unregistered]
     * <p>
     * Unreachable states (because enqueue also clears):
     * 不可达状态
     * [active/enqeued]
     * [active/dequeued]
     * <p>
     * [1] Unregistered is not permitted for FinalReferences.
     * <p>
     * [2] These transitions are not possible for FinalReferences, making
     * [pending/enqueued] and [pending/dequeued] unreachable, and
     * [inactive/registered] terminal.
     * <p>
     * [3] The garbage collector may directly transition a Reference
     * from [active/unregistered] to [inactive/unregistered],
     * bypassing the pending-Reference list.
     * <p>
     * 保存的引用指向的对象
     * 会被gc特殊对待，即当没有强引用存在时，当下一次gc的时候会被清除。
     */
    private T referent;         /* Treated specially by GC */

    /**
     * The queue this reference gets enqueued to by GC notification or by
     * calling enqueue().
     * 收到 GC 通知或者调用了 enqueue() 方法都会入队
     * <p>
     * When registered: the queue with which this reference is registered.
     * enqueued: ReferenceQueue.ENQUEUE
     * dequeued: ReferenceQueue.NULL
     * unregistered: ReferenceQueue.NULL
     * <p>
     * 引用队列，Reference 对象关联的队列
     * 对象如果即将被垃圾收集器回收，此队列作为通知的回调队列。
     * 也就是当 Reference 实例持有的 referent 对象要被回收的时候，
     * Reference 实例会被放入到 ReferenceQueue 中。可以从 ReferenceQueue 中取到
     * Reference 实例对象。
     */
    volatile ReferenceQueue<? super T> queue;

    /**
     * The link in a ReferenceQueue's list of Reference objects.
     * <p>
     * When registered: null
     * enqueued: next element in queue (or this if last)
     * dequeued: this (marking FinalReferences as inactive)
     * unregistered: null
     * <p>
     * 下一个Reference实例的引用
     * 形成一个单链表
     */
    @SuppressWarnings("rawtypes")
    volatile Reference next;

    /**
     * Used by the garbage collector to accumulate Reference objects that need
     * to be revisited in order to decide whether they should be notified.
     * Also used as the link in the pending-Reference list.  The discovered
     * field and the next field are distinct to allow the enqueue() method to
     * be applied to a Reference object while it is either in the
     * pending-Reference list or in the garbage collector's discovered set.
     * <p>
     * When active: null or next element in a discovered reference list
     * maintained by the GC (or this if last)
     * pending: next element in the pending-Reference list (null if last)
     * inactive: null
     * <p>
     * 在 GC 时，JVM 底层会维护一个叫 DiscoveredList 的链表，
     * 存放的是 Reference 对象，discovered 字段指向的就是链表中的下一个元素，由 JVM 设置
     * 基于状态的不同，指向不同链表中下一个元素
     * active 状态：为 null 或者 指向 discovered 引用列表中的下一个元素，discovered 引用列表由 GC 维护。
     * pending 状态：pending-Reference list 中的下一个元素
     * inactive 状态：为空
     */
    private transient Reference<T> discovered;


    /* High-priority thread to enqueue pending References
     */

    /**
     * 静态初始化代码
     * 高优先级线程
     */
    private static class ReferenceHandler extends Thread {

        /**
         * 确保对应的类型已经被加载
         *
         * @param clazz
         */
        private static void ensureClassInitialized(Class<?> clazz) {
            try {
                // 类加载
                Class.forName(clazz.getName(), true, clazz.getClassLoader());
            } catch (ClassNotFoundException e) {
                throw (Error) new NoClassDefFoundError(e.getMessage()).initCause(e);
            }
        }

        static {
            // pre-load and initialize Cleaner class so that we don't
            // get into trouble later in the run loop if there's
            // memory shortage while loading/initializing it lazily.
            // 确保 Cleaner 类已经初始化
            ensureClassInitialized(Cleaner.class);
        }

        /**
         * 构造方法
         *
         * @param g
         * @param name
         */
        ReferenceHandler(ThreadGroup g, String name) {
            super(g, null, name, 0, false);
        }

        /**
         * run 函数主体
         */
        public void run() {
            // 死循环执行 processPendingReferences 方法
            while (true) {
                processPendingReferences();
            }
        }
    }

    /**
     * Atomically get and clear (set to null) the VM's pending-Reference list.
     * 原子获取(后)并且清理 VM 中的 pending-Reference 列表中的该元素
     */
    private static native Reference<Object> getAndClearReferencePendingList();

    /**
     * Test whether the VM's pending-Reference list contains any entries.
     * 检验 VM 中的 pending-Reference 列表是否有剩余元素
     */
    private static native boolean hasReferencePendingList();

    /**
     * Wait until the VM's pending-Reference list may be non-null.
     * 等待直到 pending 引用对象链表不为 null
     * 等待直到 pending-Reference 列表中有元素
     */
    private static native void waitForReferencePendingList();

    /**
     * 锁对象
     */
    private static final Object processPendingLock = new Object();
    /**
     * 标记变量
     * 正在处理 pending-Reference 对象的时候，这个变量会更新为 true。
     * 处理完毕或者初始化状态为 false，用于避免重复处理或者重复等待
     */
    private static boolean processPendingActive = false;

    /**
     * 死循环核心方法
     * 将 DiscoveredList 中的对象移动到 pendingList 中
     * 将 pendingList 中的对象加到 ReferenceQueue 中
     */
    private static void processPendingReferences() {
        // Only the singleton reference processing thread calls
        // waitForReferencePendingList() and getAndClearReferencePendingList().
        // These are separate operations to avoid a race with other threads
        // that are calling waitForReferenceProcessing().
        // 等待 pending 引用对象链不为空
        waitForReferencePendingList();
        Reference<Object> pendingList;
        synchronized (processPendingLock) {
            // 取出一个 Reference 对象并从 pending-Reference 列表中移除
            pendingList = getAndClearReferencePendingList();
            // 标记设为 true，表示正在处理
            processPendingActive = true;
        }
        // 循环处理
        while (pendingList != null) {
            Reference<Object> ref = pendingList;
            // 获取 discovered 引用
            // discovered 引用根据当前 Reference 的状态的不同，指向的列表也会不同
            // Active 状态下，指向 discovered 列表
            // pending 状态下，指向 pending-reference 列表
            pendingList = ref.discovered;
            // 取出来之后将 当前 Reference 的成员变量 discovered 属性置为 null
            // 因为要进行状态迁移
            ref.discovered = null;
            // 如果是 Cleaner 类型
            // 执行 clean 方法并且对锁对象 processPendingLock 进行唤醒所有阻塞的线程
            // 堆外对象一般使用 cleaner 进行回收
            if (ref instanceof Cleaner) {
                ((Cleaner) ref).clean();
                // Notify any waiters that progress has been made.
                // This improves latency for nio.Bits waiters, which
                // are the only important ones.
                synchronized (processPendingLock) {
                    processPendingLock.notifyAll();
                }
                // 非 Cleaner 类型并且引用队列不为 ReferenceQueue.NULL 则进行入队操作
            } else {
                ReferenceQueue<? super Object> q = ref.queue;
                if (q != ReferenceQueue.NULL) q.enqueue(ref);
            }
        }
        // Notify any waiters of completion of current round.
        // 当次循环结束之前再次唤醒锁对象processPendingLock上阻塞的所有线程
        synchronized (processPendingLock) {
            // 重置标记
            processPendingActive = false;
            processPendingLock.notifyAll();
        }
    }

    // Wait for progress in reference processing.
    //
    // Returns true after waiting (for notification from the reference
    // processing thread) if either (1) the VM has any pending
    // references, or (2) the reference processing thread is
    // processing references. Otherwise, returns false immediately.

    /**
     * 如果正在处理 pending 链表中的引用对象
     * 或者监测到 VM 中的 pending 链表中还有剩余元素
     * 则基于锁对象 processPendingLock 进行等待
     *
     * @return
     * @throws InterruptedException
     */
    private static boolean waitForReferenceProcessing()
            throws InterruptedException {
        synchronized (processPendingLock) {
            if (processPendingActive || hasReferencePendingList()) {
                // Wait for progress, not necessarily completion.
                processPendingLock.wait();
                return true;
            } else {
                return false;
            }
        }
    }

    static {
        ThreadGroup tg = Thread.currentThread().getThreadGroup();
        for (ThreadGroup tgn = tg;
             tgn != null;
             tg = tgn, tgn = tg.getParent())
            ;
        Thread handler = new ReferenceHandler(tg, "Reference Handler");
        /* If there were a special system-only priority greater than
         * MAX_PRIORITY, it would be used here
         */
        // 高优先级
        handler.setPriority(Thread.MAX_PRIORITY);
        // 守护线程
        handler.setDaemon(true);
        // 开启线程
        handler.start();

        // provide access in SharedSecrets
        SharedSecrets.setJavaLangRefAccess(new JavaLangRefAccess() {
            @Override
            public boolean waitForReferenceProcessing()
                    throws InterruptedException {
                return Reference.waitForReferenceProcessing();
            }

            @Override
            public void runFinalization() {
                Finalizer.runFinalization();
            }
        });
    }

    /* -- Referent accessor and setters -- */

    /**
     * 获取 Reference 实例持有的 referent 对象
     * <p></>Returns this reference object's referent.  If this reference object has
     * been cleared, either by the program or by the garbage collector, then
     * this method returns <code>null</code>.
     *
     * @return   The object to which this reference refers, or
     *           <code>null</code> if this reference object has been cleared
     */
    @HotSpotIntrinsicCandidate
    public T get() {
        return this.referent;
    }

    /**
     * Clears this reference object.  Invoking this method will not cause this
     * object to be enqueued.
     *
     * <p> This method is invoked only by Java code; when the garbage collector
     * clears references it does so directly, without invoking this method.
     * <p>
     * 清除 Reference 实例持有的 referent 对象
     */
    public void clear() {
        this.referent = null;
    }

    /* -- Queue operations -- */

    /**
     * 判断是否处于 enqeued 状态
     * Tells whether or not this reference object has been enqueued, either by
     * the program or by the garbage collector.  If this reference object was
     * not registered with a queue when it was created, then this method will
     * always return <code>false</code>.
     *
     * @return   <code>true</code> if and only if this reference object has
     *           been enqueued
     */
    public boolean isEnqueued() {
        return (this.queue == ReferenceQueue.ENQUEUED);
    }

    /**
     * 进队，同时会把 referent 对象设为 null
     * <p>
     * Clears this reference object and adds it to the queue with which
     * it is registered, if any.
     *
     * <p> This method is invoked only by Java code; when the garbage collector
     * enqueues references it does so directly, without invoking this method.
     *
     * @return   <code>true</code> if this reference object was successfully
     *           enqueued; <code>false</code> if it was already enqueued or if
     *           it was not registered with a queue when it was created
     */
    public boolean enqueue() {
        this.referent = null;
        return this.queue.enqueue(this);
    }

    /**
     * Throws {@link CloneNotSupportedException}. A {@code Reference} cannot be
     * meaningfully cloned. Construct a new {@code Reference} instead.
     *
     * @returns never returns normally
     * @throws  CloneNotSupportedException always
     *
     * @since 11
     */
    @Override
    protected Object clone() throws CloneNotSupportedException {
        throw new CloneNotSupportedException();
    }

    /* -- Constructors -- */

    /**
     * 构造函数
     *
     * @param referent
     */
    Reference(T referent) {
        this(referent, null);
    }

    /**
     * 构造函数 依赖于 referent 和 ReferenceQueue
     * 对于不同类别的 ReferenceQueue，有不同的状态跃迁图。
     * 有两类 ReferenceQueue。一类是 ReferenceQueue.NULL，另一类为非 null。
     *
     * @param referent 引用对象
     * @param queue    引用队列
     */
    Reference(T referent, ReferenceQueue<? super T> queue) {
        this.referent = referent;
        // 若传入的队列为 null ，则使用 ReferenceQueue.NULL 队列。
        this.queue = (queue == null) ? ReferenceQueue.NULL : queue;
    }

    /**
     * Ensures that the object referenced by the given reference remains
     * <a href="package-summary.html#reachability"><em>strongly reachable</em></a>,
     * regardless of any prior actions of the program that might otherwise cause
     * the object to become unreachable; thus, the referenced object is not
     * reclaimable by garbage collection at least until after the invocation of
     * this method.  Invocation of this method does not itself initiate garbage
     * collection or finalization.
     *
     * <p> This method establishes an ordering for
     * <a href="package-summary.html#reachability"><em>strong reachability</em></a>
     * with respect to garbage collection.  It controls relations that are
     * otherwise only implicit in a program -- the reachability conditions
     * triggering garbage collection.  This method is designed for use in
     * uncommon situations of premature finalization where using
     * {@code synchronized} blocks or methods, or using other synchronization
     * facilities are not possible or do not provide the desired control.  This
     * method is applicable only when reclamation may have visible effects,
     * which is possible for objects with finalizers (See
     * <a href="https://docs.oracle.com/javase/specs/jls/se8/html/jls-12.html#jls-12.6">
     * Section 12.6 17 of <cite>The Java&trade; Language Specification</cite></a>)
     * that are implemented in ways that rely on ordering control for correctness.
     *
     * @param ref the reference. If {@code null}, this method has no effect.
     * @apiNote Finalization may occur whenever the virtual machine detects that no
     * reference to an object will ever be stored in the heap: The garbage
     * collector may reclaim an object even if the fields of that object are
     * still in use, so long as the object has otherwise become unreachable.
     * This may have surprising and undesirable effects in cases such as the
     * following example in which the bookkeeping associated with a class is
     * managed through array indices.  Here, method {@code action} uses a
     * {@code reachabilityFence} to ensure that the {@code Resource} object is
     * not reclaimed before bookkeeping on an associated
     * {@code ExternalResource} has been performed; in particular here, to
     * ensure that the array slot holding the {@code ExternalResource} is not
     * nulled out in method {@link Object#finalize}, which may otherwise run
     * concurrently.
     *
     * <pre> {@code
     * class Resource {
     *   private static ExternalResource[] externalResourceArray = ...
     *
     *   int myIndex;
     *   Resource(...) {
     *     myIndex = ...
     *     externalResourceArray[myIndex] = ...;
     *     ...
     *   }
     *   protected void finalize() {
     *     externalResourceArray[myIndex] = null;
     *     ...
     *   }
     *   public void action() {
     *     try {
     *       // ...
     *       int i = myIndex;
     *       Resource.update(externalResourceArray[i]);
     *     } finally {
     *       Reference.reachabilityFence(this);
     *     }
     *   }
     *   private static void update(ExternalResource ext) {
     *     ext.status = ...;
     *   }
     * }}</pre>
     * <p>
     * Here, the invocation of {@code reachabilityFence} is nonintuitively
     * placed <em>after</em> the call to {@code update}, to ensure that the
     * array slot is not nulled out by {@link Object#finalize} before the
     * update, even if the call to {@code action} was the last use of this
     * object.  This might be the case if, for example a usage in a user program
     * had the form {@code new Resource().action();} which retains no other
     * reference to this {@code Resource}.  While probably overkill here,
     * {@code reachabilityFence} is placed in a {@code finally} block to ensure
     * that it is invoked across all paths in the method.  In a method with more
     * complex control paths, you might need further precautions to ensure that
     * {@code reachabilityFence} is encountered along all of them.
     *
     * <p> It is sometimes possible to better encapsulate use of
     * {@code reachabilityFence}.  Continuing the above example, if it were
     * acceptable for the call to method {@code update} to proceed even if the
     * finalizer had already executed (nulling out slot), then you could
     * localize use of {@code reachabilityFence}:
     *
     * <pre> {@code
     * public void action2() {
     *   // ...
     *   Resource.update(getExternalResource());
     * }
     * private ExternalResource getExternalResource() {
     *   ExternalResource ext = externalResourceArray[myIndex];
     *   Reference.reachabilityFence(this);
     *   return ext;
     * }}</pre>
     *
     * <p> Method {@code reachabilityFence} is not required in constructions
     * that themselves ensure reachability.  For example, because objects that
     * are locked cannot, in general, be reclaimed, it would suffice if all
     * accesses of the object, in all methods of class {@code Resource}
     * (including {@code finalize}) were enclosed in {@code synchronized (this)}
     * blocks.  (Further, such blocks must not include infinite loops, or
     * themselves be unreachable, which fall into the corner case exceptions to
     * the "in general" disclaimer.)  However, method {@code reachabilityFence}
     * remains a better option in cases where this approach is not as efficient,
     * desirable, or possible; for example because it would encounter deadlock.
     *
     * @param ref the reference. If {@code null}, this method has no effect.
     * @since 9
     */
    @ForceInline
    public static void reachabilityFence(Object ref) {
        // Does nothing. This method is annotated with @ForceInline to eliminate
        // most of the overhead that using @DontInline would cause with the
        // HotSpot JVM, when this fence is used in a wide variety of situations.
        // HotSpot JVM retains the ref and does not GC it before a call to
        // this method, because the JIT-compilers do not have GC-only safepoints.
    }
}
