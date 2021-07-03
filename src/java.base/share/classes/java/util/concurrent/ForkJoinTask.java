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

package java.util.concurrent;

import java.io.Serializable;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.util.Collection;
import java.util.List;
import java.util.RandomAccess;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Abstract base class for tasks that run within a {@link ForkJoinPool}.
 * A {@code ForkJoinTask} is a thread-like entity that is much
 * lighter weight than a normal thread.  Huge numbers of tasks and
 * subtasks may be hosted by a small number of actual threads in a
 * ForkJoinPool, at the price of some usage limitations.
 *
 * <p>A "main" {@code ForkJoinTask} begins execution when it is
 * explicitly submitted to a {@link ForkJoinPool}, or, if not already
 * engaged in a ForkJoin computation, commenced in the {@link
 * ForkJoinPool#commonPool()} via {@link #fork}, {@link #invoke}, or
 * related methods.  Once started, it will usually in turn start other
 * subtasks.  As indicated by the name of this class, many programs
 * using {@code ForkJoinTask} employ only methods {@link #fork} and
 * {@link #join}, or derivatives such as {@link
 * #invokeAll(ForkJoinTask...) invokeAll}.  However, this class also
 * provides a number of other methods that can come into play in
 * advanced usages, as well as extension mechanics that allow support
 * of new forms of fork/join processing.
 *
 * <p>A {@code ForkJoinTask} is a lightweight form of {@link Future}.
 * The efficiency of {@code ForkJoinTask}s stems from a set of
 * restrictions (that are only partially statically enforceable)
 * reflecting their main use as computational tasks calculating pure
 * functions or operating on purely isolated objects.  The primary
 * coordination mechanisms are {@link #fork}, that arranges
 * asynchronous execution, and {@link #join}, that doesn't proceed
 * until the task's result has been computed.  Computations should
 * ideally avoid {@code synchronized} methods or blocks, and should
 * minimize other blocking synchronization apart from joining other
 * tasks or using synchronizers such as Phasers that are advertised to
 * cooperate with fork/join scheduling. Subdividable tasks should also
 * not perform blocking I/O, and should ideally access variables that
 * are completely independent of those accessed by other running
 * tasks. These guidelines are loosely enforced by not permitting
 * checked exceptions such as {@code IOExceptions} to be
 * thrown. However, computations may still encounter unchecked
 * exceptions, that are rethrown to callers attempting to join
 * them. These exceptions may additionally include {@link
 * RejectedExecutionException} stemming from internal resource
 * exhaustion, such as failure to allocate internal task
 * queues. Rethrown exceptions behave in the same way as regular
 * exceptions, but, when possible, contain stack traces (as displayed
 * for example using {@code ex.printStackTrace()}) of both the thread
 * that initiated the computation as well as the thread actually
 * encountering the exception; minimally only the latter.
 *
 * <p>It is possible to define and use ForkJoinTasks that may block,
 * but doing so requires three further considerations: (1) Completion
 * of few if any <em>other</em> tasks should be dependent on a task
 * that blocks on external synchronization or I/O. Event-style async
 * tasks that are never joined (for example, those subclassing {@link
 * CountedCompleter}) often fall into this category.  (2) To minimize
 * resource impact, tasks should be small; ideally performing only the
 * (possibly) blocking action. (3) Unless the {@link
 * ForkJoinPool.ManagedBlocker} API is used, or the number of possibly
 * blocked tasks is known to be less than the pool's {@link
 * ForkJoinPool#getParallelism} level, the pool cannot guarantee that
 * enough threads will be available to ensure progress or good
 * performance.
 *
 * <p>The primary method for awaiting completion and extracting
 * results of a task is {@link #join}, but there are several variants:
 * The {@link Future#get} methods support interruptible and/or timed
 * waits for completion and report results using {@code Future}
 * conventions. Method {@link #invoke} is semantically
 * equivalent to {@code fork(); join()} but always attempts to begin
 * execution in the current thread. The "<em>quiet</em>" forms of
 * these methods do not extract results or report exceptions. These
 * may be useful when a set of tasks are being executed, and you need
 * to delay processing of results or exceptions until all complete.
 * Method {@code invokeAll} (available in multiple versions)
 * performs the most common form of parallel invocation: forking a set
 * of tasks and joining them all.
 *
 * <p>In the most typical usages, a fork-join pair act like a call
 * (fork) and return (join) from a parallel recursive function. As is
 * the case with other forms of recursive calls, returns (joins)
 * should be performed innermost-first. For example, {@code a.fork();
 * b.fork(); b.join(); a.join();} is likely to be substantially more
 * efficient than joining {@code a} before {@code b}.
 *
 * <p>The execution status of tasks may be queried at several levels
 * of detail: {@link #isDone} is true if a task completed in any way
 * (including the case where a task was cancelled without executing);
 * {@link #isCompletedNormally} is true if a task completed without
 * cancellation or encountering an exception; {@link #isCancelled} is
 * true if the task was cancelled (in which case {@link #getException}
 * returns a {@link CancellationException}); and
 * {@link #isCompletedAbnormally} is true if a task was either
 * cancelled or encountered an exception, in which case {@link
 * #getException} will return either the encountered exception or
 * {@link CancellationException}.
 *
 * <p>The ForkJoinTask class is not usually directly subclassed.
 * Instead, you subclass one of the abstract classes that support a
 * particular style of fork/join processing, typically {@link
 * RecursiveAction} for most computations that do not return results,
 * {@link RecursiveTask} for those that do, and {@link
 * CountedCompleter} for those in which completed actions trigger
 * other actions.  Normally, a concrete ForkJoinTask subclass declares
 * fields comprising its parameters, established in a constructor, and
 * then defines a {@code compute} method that somehow uses the control
 * methods supplied by this base class.
 *
 * <p>Method {@link #join} and its variants are appropriate for use
 * only when completion dependencies are acyclic; that is, the
 * parallel computation can be described as a directed acyclic graph
 * (DAG). Otherwise, executions may encounter a form of deadlock as
 * tasks cyclically wait for each other.  However, this framework
 * supports other methods and techniques (for example the use of
 * {@link Phaser}, {@link #helpQuiesce}, and {@link #complete}) that
 * may be of use in constructing custom subclasses for problems that
 * are not statically structured as DAGs. To support such usages, a
 * ForkJoinTask may be atomically <em>tagged</em> with a {@code short}
 * value using {@link #setForkJoinTaskTag} or {@link
 * #compareAndSetForkJoinTaskTag} and checked using {@link
 * #getForkJoinTaskTag}. The ForkJoinTask implementation does not use
 * these {@code protected} methods or tags for any purpose, but they
 * may be of use in the construction of specialized subclasses.  For
 * example, parallel graph traversals can use the supplied methods to
 * avoid revisiting nodes/tasks that have already been processed.
 * (Method names for tagging are bulky in part to encourage definition
 * of methods that reflect their usage patterns.)
 *
 * <p>Most base support methods are {@code final}, to prevent
 * overriding of implementations that are intrinsically tied to the
 * underlying lightweight task scheduling framework.  Developers
 * creating new basic styles of fork/join processing should minimally
 * implement {@code protected} methods {@link #exec}, {@link
 * #setRawResult}, and {@link #getRawResult}, while also introducing
 * an abstract computational method that can be implemented in its
 * subclasses, possibly relying on other {@code protected} methods
 * provided by this class.
 *
 * <p>ForkJoinTasks should perform relatively small amounts of
 * computation. Large tasks should be split into smaller subtasks,
 * usually via recursive decomposition. As a very rough rule of thumb,
 * a task should perform more than 100 and less than 10000 basic
 * computational steps, and should avoid indefinite looping. If tasks
 * are too big, then parallelism cannot improve throughput. If too
 * small, then memory and internal task maintenance overhead may
 * overwhelm processing.
 *
 * <p>This class provides {@code adapt} methods for {@link Runnable}
 * and {@link Callable}, that may be of use when mixing execution of
 * {@code ForkJoinTasks} with other kinds of tasks. When all tasks are
 * of this form, consider using a pool constructed in <em>asyncMode</em>.
 *
 * <p>ForkJoinTasks are {@code Serializable}, which enables them to be
 * used in extensions such as remote execution frameworks. It is
 * sensible to serialize tasks only before or after, but not during,
 * execution. Serialization is not relied on during execution itself.
 *
 * @since 1.7
 * @author Doug Lea
 */
public abstract class ForkJoinTask<V> implements Future<V>, Serializable {

    /*
     * See the internal documentation of class ForkJoinPool for a
     * general implementation overview.  ForkJoinTasks are mainly
     * responsible for maintaining their "status" field amidst relays
     * to methods in ForkJoinWorkerThread and ForkJoinPool.
     *
     * The methods of this class are more-or-less layered into
     * (1) basic status maintenance
     * (2) execution and awaiting completion
     * (3) user-level methods that additionally report results.
     * This is sometimes hard to see because this file orders exported
     * methods in a way that flows well in javadocs.
     */

    /**
     * 表示当前任务的状态
     * 前 16 位 为 tag
     * <p>
     *
     * The status field holds run control status bits packed into a
     * single int to ensure atomicity.  Status is initially zero, and
     * takes on nonnegative values until completed, upon which it
     * holds (sign bit) DONE, possibly with ABNORMAL (cancelled or
     * exceptional) and THROWN (in which case an exception has been
     * stored). Tasks with dependent blocked waiting joiners have the
     * SIGNAL bit set.  Completion of a task with SIGNAL set awakens
     * any waiters via notifyAll. (Waiters also help signal others
     * upon completion.)
     *
     * These control bits occupy only (some of) the upper half (16
     * bits) of status field. The lower bits are used for user-defined
     * tags.
     */
    volatile int status; // accessed directly by pool and workers

    /**
     * 1000 0000 0000 0000 0000 0000 0000 0000
     */
    private static final int DONE     = 1 << 31; // must be negative
    /**
     * 正常取消的状态为 ABNORMAL
     * 抛异常也会记录该状态
     * 0000 0000 0000 0100 0000 0000 0000 0000
     */
    private static final int ABNORMAL = 1 << 18; // set atomically with DONE
    /**
     * 0000 0000 0000 0010 0000 0000 0000 0000
     */
    private static final int THROWN   = 1 << 17; // set atomically with ABNORMAL
    /**
     * 用于判断是否需要 signal
     * 0000 0000 0000 0001 0000 0000 0000 0000
     */
    private static final int SIGNAL   = 1 << 16; // true if joiner waiting
    /**
     * 0000 0000 0000 0000 1111 1111 1111 1111
     * 掩码 表示当前 status 的 tag
     */
    private static final int SMASK    = 0xffff;  // short bits for tags

    /**
     * 返回是否是异常状态
     */
    static boolean isExceptionalStatus(int s) {  // needed by subclasses
        return (s & THROWN) != 0;
    }

    /**
     * 设置任务完成并且唤醒在等待 join 的线程
     * <p>
     * 
     * Sets DONE status and wakes up threads waiting to join this task.
     *
     * @return status on exit
     */
    private int setDone() {
        int s;
        // 将当前的 status 设置 DONE
        // 并且与上 SIGNAL 判断是否需要唤醒
        if (((s = (int)STATUS.getAndBitwiseOr(this, DONE)) & SIGNAL) != 0)
            // 唤醒
            synchronized (this) { notifyAll(); }
        // 返回是否完成
        return s | DONE;
    }

    /**
     * 异常终止
     * <p>
     *
     * Marks cancelled or exceptional completion unless already done.
     *
     * @param completion must be DONE | ABNORMAL, ORed with THROWN if exceptional
     * @return status on exit
     */
    private int abnormalCompletion(int completion) {
        for (int s, ns;;) {
            // 说明已经 Done 了
            if ((s = status) < 0)
                // 直接返回当前状态
                return s;
            // 将 status 改成 s|completion
            else if (STATUS.weakCompareAndSet(this, s, ns = s | completion)) {
                // 检查是否需要发送通知
                if ((s & SIGNAL) != 0)
                    synchronized (this) { notifyAll(); }
                return ns;
            }
        }
    }

    /**
     * 真正的执行任务
     * <p>
     *
     * Primary execution method for stolen tasks. Unless done, calls
     * exec and records status if completed, but doesn't wait for
     * completion otherwise.
     *
     * @return status on exit from this method
     */
    final int doExec() {
        int s; boolean completed;
        if ((s = status) >= 0) {
            try {
                // 有具体实现类执行
                // 返回是否执行成功
                completed = exec();
            } catch (Throwable rex) {
                completed = false;
                s = setExceptionalCompletion(rex);
            }
            if (completed)
                // 设置 status
                // 并根据需要唤醒
                s = setDone();
        }
        return s;
    }

    /**
     * If not done, sets SIGNAL status and performs Object.wait(timeout).
     * This task may or may not be done on exit. Ignores interrupts.
     *
     * @param timeout using Object.wait conventions.
     */
    final void internalWait(long timeout) {
        if ((int)STATUS.getAndBitwiseOr(this, SIGNAL) >= 0) {
            synchronized (this) {
                if (status >= 0)
                    try { wait(timeout); } catch (InterruptedException ie) { }
                else
                    notifyAll();
            }
        }
    }

    /**
     * 不是 ForkJoinWorkerThread 线程会调用到该方法
     * 阻塞线程直到完成 调用 object 的 wait 方法
     * <p>
     *
     * Blocks a non-worker-thread until completion.
     * @return status upon completion
     */
    private int externalAwaitDone() {
        int s = tryExternalHelp();
        // s > 0 说明还未完成 && 将唤醒标记写入
        if (s >= 0 && (s = (int)STATUS.getAndBitwiseOr(this, SIGNAL)) >= 0) {
            // 中断标记
            boolean interrupted = false;
            // 加锁
            synchronized (this) {
                // 自旋
                for (;;) {
                    // 未完成
                    if ((s = status) >= 0) {
                        try {
                            // wait 等待 释放锁
                            wait(0L);
                            // 如果被中断
                        } catch (InterruptedException ie) {
                            // 设置中断标识
                            interrupted = true;
                        }
                    }
                    // 任务已经执行完成
                    else {
                        // 唤醒所有
                        notifyAll();
                        // 结束，跳出循环
                        break;
                    }
                }
            }
            // 根据中断标识
            if (interrupted)
                // 中断当前线程
                Thread.currentThread().interrupt();
        }
        // 返回 status
        return s;
    }

    /**
     * 阻塞直到完成
     * 响应中断，会抛出中断异常
     * <p>
     * 
     * Blocks a non-worker-thread until completion or interruption.
     */
    private int externalInterruptibleAwaitDone() throws InterruptedException {
        int s = tryExternalHelp();
        // 未完成且将 status 成功设置了 SIGNAL 标识
        if (s >= 0 && (s = (int)STATUS.getAndBitwiseOr(this, SIGNAL)) >= 0) {
            synchronized (this) {
                for (;;) {
                    if ((s = status) >= 0)
                        // wait 等待
                        wait(0L);
                    else {
                        // 唤醒所有的等待线程
                        notifyAll();
                        break;
                    }
                }
            }
        }
        else if (Thread.interrupted())
            // 抛出中断异常
            throw new InterruptedException();
        return s;
    }

    /**
     * Tries to help with tasks allowed for external callers.
     *
     * @return current status
     */
    private int tryExternalHelp() {
        int s;
        // status < 0  说明已经 Done 了
        return ((s = status) < 0 ? s:
                // 如果是 CountedCompleter 任务
                (this instanceof CountedCompleter) ?
                ForkJoinPool.common.externalHelpComplete(
                    (CountedCompleter<?>)this, 0) :
                        // 尝试出栈
                ForkJoinPool.common.tryExternalUnpush(this) ?
                doExec() : 0);
    }

    /**
     * Implementation for join, get, quietlyJoin. Directly handles
     * only cases of already-completed, external wait, and
     * unfork+exec.  Others are relayed to ForkJoinPool.awaitJoin.
     *
     * @return status upon completion
     */
    private int doJoin() {
        int s; Thread t; ForkJoinWorkerThread wt; ForkJoinPool.WorkQueue w;
        return (s = status) < 0 ? s :
                // 未完成
            ((t = Thread.currentThread()) instanceof ForkJoinWorkerThread) ?
                    // 当前线程所在的 workQueue
                    ((w = (wt = (ForkJoinWorkerThread)t).workQueue).
                    // 出栈成功 且 任务执行完毕，则直接返回 status
            tryUnpush(this) && (s = doExec()) < 0 ? s :
                    // 等待
            wt.pool.awaitJoin(w, this, 0L)) :
                    // 外部等待
            externalAwaitDone();
    }

    /**
     * invoke 传递过来
     * <p>
     *
     * Implementation for invoke, quietlyInvoke.
     *
     * @return status upon completion
     */
    private int doInvoke() {
        int s; Thread t; ForkJoinWorkerThread wt;
        // 调用 exec 方法执行任务
        // < 0 说明 Done，直接返回 statue
        // >= 0 说明还有其他子任务没有执行完，等待子任务执行完
        return (s = doExec()) < 0 ? s :
                // 没有 Done
            ((t = Thread.currentThread()) instanceof ForkJoinWorkerThread) ?
                    // 调用 ForkJoinPool 的 awaitJoin 方法
            (wt = (ForkJoinWorkerThread)t).pool.
            awaitJoin(wt.workQueue, this, 0L) :
                    // 外部的 await
            externalAwaitDone();
    }

    // Exception table support

    /**
     * 类似于 hashtable 的形式
     * 外层是一个数组，每个 ExceptionNode 内部是一个链表
     * <p>
     *
     * Hash table of exceptions thrown by tasks, to enable reporting
     * by callers. Because exceptions are rare, we don't directly keep
     * them with task objects, but instead use a weak ref table.  Note
     * that cancellation exceptions don't appear in the table, but are
     * instead recorded as status values.
     *
     * The exception table has a fixed capacity.
     */
    private static final ExceptionNode[] exceptionTable
        = new ExceptionNode[32];

    /**
     * 访问 exceptionTable 时需要用锁保护
     * <p>
     * Lock protecting access to exceptionTable.
     */
    private static final ReentrantLock exceptionTableLock
        = new ReentrantLock();

    /**
     * 引用队列
     * <p>
     *
     * Reference queue of stale exceptionally completed tasks.
     */
    private static final ReferenceQueue<ForkJoinTask<?>> exceptionTableRefQueue
        = new ReferenceQueue<>();

    /**
     * 是一个 WeakReference，包装了 ForkJoinTask
     *
     * Key-value nodes for exception table.  The chained hash table
     * uses identity comparisons, full locking, and weak references
     * for keys. The table has a fixed capacity because it only
     * maintains task exceptions long enough for joiners to access
     * them, so should never become very large for sustained
     * periods. However, since we do not know when the last joiner
     * completes, we must use weak references and expunge them. We do
     * so on each operation (hence full locking). Also, some thread in
     * any ForkJoinPool will call helpExpungeStaleExceptions when its
     * pool becomes isQuiescent.
     */
    static final class ExceptionNode extends WeakReference<ForkJoinTask<?>> {
        /**
         * 保存的 Throwable
         */
        final Throwable ex;
        /**
         * 是一个链表的形式
         * 头插法
         */
        ExceptionNode next;
        final long thrower;  // use id not ref to avoid weak cycles
        /**
         * 当前 ExceptionNode 的 hashcode
         */
        final int hashCode;  // store task hashCode before weak ref disappears
        ExceptionNode(ForkJoinTask<?> task, Throwable ex, ExceptionNode next,
                      ReferenceQueue<ForkJoinTask<?>> exceptionTableRefQueue) {
            super(task, exceptionTableRefQueue);
            this.ex = ex;
            this.next = next;
            this.thrower = Thread.currentThread().getId();
            this.hashCode = System.identityHashCode(task);
        }
    }

    /**
     * 记录异常并设置状态
     * <p>
     *
     * Records exception and sets status.
     *
     * @return status on exit
     */
    final int recordExceptionalCompletion(Throwable ex) {
        int s;
        // 未完成
        if ((s = status) >= 0) {
            // 得到 hashcode
            int h = System.identityHashCode(this);
            final ReentrantLock lock = exceptionTableLock;
            // 加锁
            lock.lock();
            try {
                // 处理被 GC 回收的 ExceptionNode
                expungeStaleExceptions();
                ExceptionNode[] t = exceptionTable;
                // 计算位置
                int i = h & (t.length - 1);
                //
                for (ExceptionNode e = t[i]; ; e = e.next) {
                    // 放入链表中
                    if (e == null) {
                        // 头插法
                        t[i] = new ExceptionNode(this, ex, t[i],
                                                 exceptionTableRefQueue);
                        break;
                    }
                    // 如果已经存在，则直接跳出
                    if (e.get() == this) // already present
                        break;
                }
            } finally {
                // 释放锁
                lock.unlock();
            }
            // 更新状态
            s = abnormalCompletion(DONE | ABNORMAL | THROWN);
        }
        return s;
    }

    /**
     * 设置异常完成
     * <p>
     *
     * Records exception and possibly propagates.
     *
     * @return status on exit
     */
    private int setExceptionalCompletion(Throwable ex) {
        int s = recordExceptionalCompletion(ex);
        // 存在 THROWN 标志
        if ((s & THROWN) != 0)
            // 内部传播异常
            internalPropagateException(ex);
        return s;
    }

    /**
     * 异常传播的 hook，由子类实现
     *
     * Hook for exception propagation support for tasks with completers.
     */
    void internalPropagateException(Throwable ex) {
    }

    /**
     * 取消并且忽略任务由于取消抛出的异常
     * <p>
     *
     * Cancels, ignoring any exceptions thrown by cancel. Used during
     * worker and pool shutdown. Cancel is spec'ed not to throw any
     * exceptions, but if it does anyway, we have no recourse during
     * shutdown, so guard against this case.
     */
    static final void cancelIgnoringExceptions(ForkJoinTask<?> t) {
        if (t != null && t.status >= 0) {
            try {
                t.cancel(false);
            } catch (Throwable ignore) {
                // ... 直接不处理
            }
        }
    }

    /**
     * 清除当前线程在 exceptionTable 中的 ExceptionNode 并且清空状态
     * <p>
     * Removes exception node and clears status.
     */
    private void clearExceptionalCompletion() {
        // 计算 hashcode
        int h = System.identityHashCode(this);
        final ReentrantLock lock = exceptionTableLock;
        // 加锁
        lock.lock();
        try {
            ExceptionNode[] t = exceptionTable;
            int i = h & (t.length - 1);
            ExceptionNode e = t[i];
            ExceptionNode pred = null;
            while (e != null) {
                ExceptionNode next = e.next;
                // 找到了
                if (e.get() == this) {
                    if (pred == null)
                        t[i] = next;
                    else
                        pred.next = next;
                    break;
                }
                pred = e;
                e = next;
            }
            // 顺便处理被 GC 的 exceptionNode
            expungeStaleExceptions();
            // status 重置为 0
            status = 0;
        } finally {
            // 释放锁
            lock.unlock();
        }
    }

    /**
     * 返回一个 Throwable 的实例
     * <p>
     *
     * Returns a rethrowable exception for this task, if available.
     * To provide accurate stack traces, if the exception was not
     * thrown by the current thread, we try to create a new exception
     * of the same type as the one thrown, but with the recorded
     * exception as its cause. If there is no such constructor, we
     * instead try to use a no-arg constructor, followed by initCause,
     * to the same effect. If none of these apply, or any fail due to
     * other exceptions, we return the recorded exception, which is
     * still correct, although it may contain a misleading stack
     * trace.
     *
     * @return the exception, or null if none
     */
    private Throwable getThrowableException() {
        // 得到当前 task 的 hashcode
        int h = System.identityHashCode(this);
        ExceptionNode e;
        final ReentrantLock lock = exceptionTableLock;
        lock.lock();
        try {
            // 清理 exceptionTable
            expungeStaleExceptions();
            ExceptionNode[] t = exceptionTable;
            // 当前 task 对应位置上的 exceptionNode
            e = t[h & (t.length - 1)];
            // 寻找当前 task 的 node
            while (e != null && e.get() != this)
                e = e.next;
        } finally {
            lock.unlock();
        }
        Throwable ex;
        // 没有找到 或者 找到之后的 exceptionNode 未包含异常
        if (e == null || (ex = e.ex) == null)
            // 直接返回 null
            return null;
        if (e.thrower != Thread.currentThread().getId()) {
            try {
                // 寻找无参构造函数
                Constructor<?> noArgCtor = null;
                // public ctors only
                for (Constructor<?> c : ex.getClass().getConstructors()) {
                    // 获取异常类型构造方法的参数
                    Class<?>[] ps = c.getParameterTypes();
                    // 无参
                    if (ps.length == 0)
                        noArgCtor = c;
                    // 有参且为 1 个参数
                    else if (ps.length == 1 && ps[0] == Throwable.class)
                        return (Throwable)c.newInstance(ex);
                }
                if (noArgCtor != null) {
                    Throwable wx = (Throwable)noArgCtor.newInstance();
                    wx.initCause(ex);
                    return wx;
                }
            } catch (Exception ignore) {
            }
        }
        return ex;
    }

    /**
     * 处理过期的数据(被 GC 回收的 ExceptionNode)
     * 将被 GC 回收的 ExceptionNode 从 exceptionTable 中移除
     * <p>
     *
     * Polls stale refs and removes them. Call only while holding lock.
     */
    private static void expungeStaleExceptions() {
        // 遍历引用队列
        for (Object x; (x = exceptionTableRefQueue.poll()) != null;) {
            // 如果是 ExceptionNode
            if (x instanceof ExceptionNode) {
                ExceptionNode[] t = exceptionTable;
                // 计算出当前 ExceptionNode 在 exceptionTable 中的位置
                int i = ((ExceptionNode)x).hashCode & (t.length - 1);
                // 保存当前被回收的 ExceptionNode
                ExceptionNode e = t[i];
                ExceptionNode pred = null;
                while (e != null) {
                    // 下一个节点
                    ExceptionNode next = e.next;
                    // 找到了
                    if (e == x) {
                        // 处理链表
                        if (pred == null)
                            t[i] = next;
                        else
                            pred.next = next;
                        break;
                    }
                    // 往链表的后面继续找
                    pred = e;
                    e = next;
                }
            }
        }
    }

    /**
     * 清理过期的异常<p>
     * If lock is available, polls stale refs and removes them.
     * Called from ForkJoinPool when pools become quiescent.
     */
    static final void helpExpungeStaleExceptions() {
        final ReentrantLock lock = exceptionTableLock;
        if (lock.tryLock()) {
            try {
                expungeStaleExceptions();
            } finally {
                lock.unlock();
            }
        }
    }

    /**
     * 避免编译器的警告的抛异常
     * <p>
     *
     * A version of "sneaky throw" to relay exceptions.
     */
    static void rethrow(Throwable ex) {
        ForkJoinTask.<RuntimeException>uncheckedThrow(ex);
    }

    /**
     * The sneaky part of sneaky throw, relying on generics
     * limitations to evade compiler complaints about rethrowing
     * unchecked exceptions.
     */
    @SuppressWarnings("unchecked") static <T extends Throwable>
    void uncheckedThrow(Throwable t) throws T {
        if (t != null)
            // 抛出当前异常
            throw (T)t; // rely on vacuous cast
        else
            throw new Error("Unknown Exception");
    }

    /**
     * 抛出异常
     * <p>
     *
     * Throws exception, if any, associated with the given status.
     */
    private void reportException(int s) {
        // 要么抛出 ExceptionNode 中的异常
        // 要么抛出 CancellationException 异常
        rethrow((s & THROWN) != 0 ? getThrowableException() :
                new CancellationException());
    }

    // public methods

    /**
     * 将任务提交到线程池中进行异步执行
     * <p>
     *
     * Arranges to asynchronously execute this task in the pool the
     * current task is running in, if applicable, or using the {@link
     * ForkJoinPool#commonPool()} if not {@link #inForkJoinPool}.  While
     * it is not necessarily enforced, it is a usage error to fork a
     * task more than once unless it has completed and been
     * reinitialized.  Subsequent modifications to the state of this
     * task or any data it operates on are not necessarily
     * consistently observable by any thread other than the one
     * executing it unless preceded by a call to {@link #join} or
     * related methods, or a call to {@link #isDone} returning {@code
     * true}.
     *
     * @return {@code this}, to simplify usage
     */
    public final ForkJoinTask<V> fork() {
        Thread t;
        // 如果当前执行线程是一个 ForkJoinWorkerThread
        if ((t = Thread.currentThread()) instanceof ForkJoinWorkerThread)
            // 提交到内部的 workQueue 中，通过 push 提交
            ((ForkJoinWorkerThread)t).workQueue.push(this);
        else
            // 不是 ForkJoinWorkerThread 线程，提交到 common 线程池中
            ForkJoinPool.common.externalPush(this);
        return this;
    }

    /**
     * 阻塞当前线程，等待任务执行完毕
     * 与 get 方法类似，区别在于该方法不会抛出 IE
     * <p>
     *
     * Returns the result of the computation when it
     * {@linkplain #isDone is done}.
     * This method differs from {@link #get()} in that abnormal
     * completion results in {@code RuntimeException} or {@code Error},
     * not {@code ExecutionException}, and that interrupts of the
     * calling thread do <em>not</em> cause the method to abruptly
     * return by throwing {@code InterruptedException}.
     *
     * @return the computed result
     */
    public final V join() {
        int s;
        // doJoin 会阻塞
        if (((s = doJoin()) & ABNORMAL) != 0)
            // 非正常结束，报告异常
            reportException(s);
        // 返回执行结果
        return getRawResult();
    }

    /**
     * 开始执行任务
     * <p>
     *
     * Commences performing this task, awaits its completion if
     * necessary, and returns its result, or throws an (unchecked)
     * {@code RuntimeException} or {@code Error} if the underlying
     * computation did so.
     *
     * @return the computed result
     */
    public final V invoke() {
        int s;
        // 检查是否有异常
        if (((s = doInvoke()) & ABNORMAL) != 0)
            // 报告异常
            reportException(s);
        // 返回结果
        return getRawResult();
    }

    /**
     * 调用多个任务，其中一个任务由当前线程执行，其他任务提交至线程池执行
     * <p>
     * Forks the given tasks, returning when {@code isDone} holds for
     * each task or an (unchecked) exception is encountered, in which
     * case the exception is rethrown. If more than one task
     * encounters an exception, then this method throws any one of
     * these exceptions. If any task encounters an exception, the
     * other may be cancelled. However, the execution status of
     * individual tasks is not guaranteed upon exceptional return. The
     * status of each task may be obtained using {@link
     * #getException()} and related methods to check if they have been
     * cancelled, completed normally or exceptionally, or left
     * unprocessed.
     *
     * @param t1 the first task
     * @param t2 the second task
     * @throws NullPointerException if any task is null
     */
    public static void invokeAll(ForkJoinTask<?> t1, ForkJoinTask<?> t2) {
        int s1, s2;
        // t2 任务提交至线程池
        t2.fork();
        // t1 任务由当前线程执行
        if (((s1 = t1.doInvoke()) & ABNORMAL) != 0)
            // 如果 t1 任务执行过程中抛异常，则抛出异常
            t1.reportException(s1);
        if (((s2 = t2.doJoin()) & ABNORMAL) != 0)
            // t2 任务执行过程中出现错误，同样也抛异常
            t2.reportException(s2);
    }

    /**
     * 立即执行多个任务
     * <p>
     * Forks the given tasks, returning when {@code isDone} holds for
     * each task or an (unchecked) exception is encountered, in which
     * case the exception is rethrown. If more than one task
     * encounters an exception, then this method throws any one of
     * these exceptions. If any task encounters an exception, others
     * may be cancelled. However, the execution status of individual
     * tasks is not guaranteed upon exceptional return. The status of
     * each task may be obtained using {@link #getException()} and
     * related methods to check if they have been cancelled, completed
     * normally or exceptionally, or left unprocessed.
     *
     * @param tasks the tasks
     * @throws NullPointerException if any task is null
     */
    public static void invokeAll(ForkJoinTask<?>... tasks) {
        Throwable ex = null;
        int last = tasks.length - 1;
        for (int i = last; i >= 0; --i) {
            ForkJoinTask<?> t = tasks[i];
            if (t == null) {
                if (ex == null)
                    ex = new NullPointerException();
            }
            // 下标不为 0 的任务均提交到线程池执行
            else if (i != 0)
                // 提交到线程池
                t.fork();
            // 下标为 0 的任务由当前线程执行
            else if ((t.doInvoke() & ABNORMAL) != 0 && ex == null)
                // 出现异常则抛异常
                ex = t.getException();
        }
        // join 等待提交到线程池的任务执行完成
        for (int i = 1; i <= last; ++i) {
            ForkJoinTask<?> t = tasks[i];
            if (t != null) {
                // 如果有一个任务出现异常了
                if (ex != null)
                    // 后续任务全都取消
                    t.cancel(false);
                // 检查是否存在异常
                else if ((t.doJoin() & ABNORMAL) != 0)
                    // 出现异常则将异常保存到 ex 中
                    ex = t.getException();
            }
        }
        if (ex != null)
            // 如果有出现异常，同样抛出异常
            rethrow(ex);
    }

    /**
     * 立即执行 Collection 中的任务
     * <p>
     * Forks all tasks in the specified collection, returning when
     * {@code isDone} holds for each task or an (unchecked) exception
     * is encountered, in which case the exception is rethrown. If
     * more than one task encounters an exception, then this method
     * throws any one of these exceptions. If any task encounters an
     * exception, others may be cancelled. However, the execution
     * status of individual tasks is not guaranteed upon exceptional
     * return. The status of each task may be obtained using {@link
     * #getException()} and related methods to check if they have been
     * cancelled, completed normally or exceptionally, or left
     * unprocessed.
     *
     * @param tasks the collection of tasks
     * @param <T> the type of the values returned from the tasks
     * @return the tasks argument, to simplify usage
     * @throws NullPointerException if tasks or any element are null
     */
    public static <T extends ForkJoinTask<?>> Collection<T> invokeAll(Collection<T> tasks) {
        // 如果 Collection 是不支持随机访问的或者不是 list 类型的，则转为数组，调用上面的方案
        if (!(tasks instanceof RandomAccess) || !(tasks instanceof List<?>)) {
            invokeAll(tasks.toArray(new ForkJoinTask<?>[0]));
            return tasks;
        }
        // 转为 List 类型
        @SuppressWarnings("unchecked")
        List<? extends ForkJoinTask<?>> ts =
            (List<? extends ForkJoinTask<?>>) tasks;
        Throwable ex = null;
        //
        int last = ts.size() - 1;
        for (int i = last; i >= 0; --i) {
            ForkJoinTask<?> t = ts.get(i);
            // 不能为 null
            if (t == null) {
                if (ex == null)
                    ex = new NullPointerException();
            }
            // 下标不为 0 则提交到线程池执行
            else if (i != 0)
                t.fork();
            // 小表为 0 则由当前线程执行
            else if ((t.doInvoke() & ABNORMAL) != 0 && ex == null)
                ex = t.getException();
        }
        for (int i = 1; i <= last; ++i) {
            ForkJoinTask<?> t = ts.get(i);
            if (t != null) {
                if (ex != null)
                    t.cancel(false);
                else if ((t.doJoin() & ABNORMAL) != 0)
                    ex = t.getException();
            }
        }
        if (ex != null)
            rethrow(ex);
        return tasks;
    }

    /**
     * 取消任务
     * <p>
     *
     * 如果任务已经完成或者其他一些原因，任务不能取消。
     * 在任务启动前调用该方法，那么任务将被取消。
     * 该方法调用成功之后，isCancelled isDone cancel 都返回 true
     * 调用 join 以及相关的方法会抛出 CancellationException 异常
     * 除非又重新调用了 reinitialize 方法。
     * <p>
     * Attempts to cancel execution of this task. This attempt will
     * fail if the task has already completed or could not be
     * cancelled for some other reason. If successful, and this task
     * has not started when {@code cancel} is called, execution of
     * this task is suppressed. After this method returns
     * successfully, unless there is an intervening call to {@link
     * #reinitialize}, subsequent calls to {@link #isCancelled},
     * {@link #isDone}, and {@code cancel} will return {@code true}
     * and calls to {@link #join} and related methods will result in
     * {@code CancellationException}.
     *
     * <p>This method may be overridden in subclasses, but if so, must
     * still ensure that these properties hold. In particular, the
     * {@code cancel} method itself must not throw exceptions.
     *
     * <p>
     * 该方法设计初衷为被其他任务调用。
     * <p>This method is designed to be invoked by <em>other</em>
     * tasks. To terminate the current task, you can just return or
     * throw an unchecked exception from its computation method, or
     * invoke {@link #completeExceptionally(Throwable)}.
     *
     * @param mayInterruptIfRunning this value has no effect in the
     * default implementation because interrupts are not used to
     * control cancellation. 该参数没有作用
     *
     * @return {@code true} if this task is now cancelled
     */
    public boolean cancel(boolean mayInterruptIfRunning) {
        int s = abnormalCompletion(DONE | ABNORMAL);
        // 如果为抛出异常，则返回 true
        // 如果抛出异常，则返回 false
        // 返回 false 说明 state 中包含 THROWN 值
        return (s & (ABNORMAL | THROWN)) == ABNORMAL;
    }

    /**
     * 返回是否已经完成任务
     */
    public final boolean isDone() {
        return status < 0;
    }

    /**
     * 返回是否已经正常取消
     */
    public final boolean isCancelled() {
        return (status & (ABNORMAL | THROWN)) == ABNORMAL;
    }

    /**
     * 返回是否异常完成
     * <p>
     * 
     * Returns {@code true} if this task threw an exception or was cancelled.
     *
     * @return {@code true} if this task threw an exception or was cancelled
     */
    public final boolean isCompletedAbnormally() {
        return (status & ABNORMAL) != 0;
    }

    /**
     * 返回是否正常完成
     * <p>
     *
     * Returns {@code true} if this task completed without throwing an
     * exception and was not cancelled.
     *
     * @return {@code true} if this task completed without throwing an
     * exception and was not cancelled
     */
    public final boolean isCompletedNormally() {
        // 检查是否异常取消
        return (status & (DONE | ABNORMAL)) == DONE;
    }

    /**
     * 返回异常
     * <p>
     * Returns the exception thrown by the base computation, or a
     * {@code CancellationException} if cancelled, or {@code null} if
     * none or if the method has not yet completed.
     *
     * @return the exception, or {@code null} if none
     */
    public final Throwable getException() {
        int s = status;
        // 根据状态来判断是否发生异常
        // 检查是否取消
        return ((s & ABNORMAL) == 0 ? null :
                // 检查是否存在异常
                (s & THROWN)   == 0 ? new CancellationException() :
                getThrowableException());
    }

    /**
     * Completes this task abnormally, and if not already aborted or
     * cancelled, causes it to throw the given exception upon
     * {@code join} and related operations. This method may be used
     * to induce exceptions in asynchronous tasks, or to force
     * completion of tasks that would not otherwise complete.  Its use
     * in other situations is discouraged.  This method is
     * overridable, but overridden versions must invoke {@code super}
     * implementation to maintain guarantees.
     *
     * @param ex the exception to throw. If this exception is not a
     * {@code RuntimeException} or {@code Error}, the actual exception
     * thrown will be a {@code RuntimeException} with cause {@code ex}.
     */
    public void completeExceptionally(Throwable ex) {
        setExceptionalCompletion((ex instanceof RuntimeException) ||
                                 (ex instanceof Error) ? ex :
                                 new RuntimeException(ex));
    }

    /**
     * 任务执行完成时调用的
     * <p>
     * Completes this task, and if not already aborted or cancelled,
     * returning the given value as the result of subsequent
     * invocations of {@code join} and related operations. This method
     * may be used to provide results for asynchronous tasks, or to
     * provide alternative handling for tasks that would not otherwise
     * complete normally. Its use in other situations is
     * discouraged. This method is overridable, but overridden
     * versions must invoke {@code super} implementation to maintain
     * guarantees.
     *
     * @param value the result value for this task
     */
    public void complete(V value) {
        try {
            // 保存结果
            setRawResult(value);
        } catch (Throwable rex) {
            // 若抛出异常，则保存关联的异常
            setExceptionalCompletion(rex);
            return;
        }
        // 设置完成状态
        setDone();
    }

    /**
     * Completes this task normally without setting a value. The most
     * recent value established by {@link #setRawResult} (or {@code
     * null} by default) will be returned as the result of subsequent
     * invocations of {@code join} and related operations.
     *
     * @since 1.8
     */
    public final void quietlyComplete() {
        // 仅设置状态
        setDone();
    }

    /**
     * 获取执行的结果
     * <p>
     *
     * Waits if necessary for the computation to complete, and then
     * retrieves its result.
     *
     * @return the computed result
     * @throws CancellationException if the computation was cancelled
     * @throws ExecutionException if the computation threw an
     * exception
     * @throws InterruptedException if the current thread is not a
     * member of a ForkJoinPool and was interrupted while waiting
     */
    public final V get() throws InterruptedException, ExecutionException {
        // 如果当前线程是 ForkJoinWorkerThread
        int s = (Thread.currentThread() instanceof ForkJoinWorkerThread) ?
                // 非 ForkJoinWorkerThread 线程则调用 externalInterruptibleAwaitDone
            doJoin() : externalInterruptibleAwaitDone();
        // 检查 THROWN 标识
        if ((s & THROWN) != 0)
            throw new ExecutionException(getThrowableException());
        // 检查是否被 cancel
        else if ((s & ABNORMAL) != 0)
            throw new CancellationException();
        else
            // 返回结果
            return getRawResult();
    }

    /**
     * 带超时等待的获取结果
     * <p>
     * 
     * Waits if necessary for at most the given time for the computation
     * to complete, and then retrieves its result, if available.
     *
     * @param timeout the maximum time to wait
     * @param unit the time unit of the timeout argument
     * @return the computed result
     * @throws CancellationException if the computation was cancelled
     * @throws ExecutionException if the computation threw an
     * exception
     * @throws InterruptedException if the current thread is not a
     * member of a ForkJoinPool and was interrupted while waiting
     * @throws TimeoutException if the wait timed out
     */
    public final V get(long timeout, TimeUnit unit)
        throws InterruptedException, ExecutionException, TimeoutException {
        int s;
        // 转成 nanos 时间
        long nanos = unit.toNanos(timeout);
        // 先检查中断
        if (Thread.interrupted())
            // 如果发生中断，直接抛出中断异常
            throw new InterruptedException();
        // 未完成 且 超时时间 > 0
        if ((s = status) >= 0 && nanos > 0L) {
            // 计算 ddl
            long d = System.nanoTime() + nanos;
            long deadline = (d == 0L) ? 1L : d; // avoid 0
            // 当前线程
            Thread t = Thread.currentThread();
            // 如果是 ForkJoinWorkerThread 线程
            if (t instanceof ForkJoinWorkerThread) {
                ForkJoinWorkerThread wt = (ForkJoinWorkerThread)t;
                // 调用 awaitJoin
                s = wt.pool.awaitJoin(wt.workQueue, this, deadline);
            }
            // 不是 ForkJoinWorkerThread 线程
            // 如果当前任务是 CountedCompleter 任务
            else if ((s = ((this instanceof CountedCompleter) ?
                           ForkJoinPool.common.externalHelpComplete(
                               (CountedCompleter<?>)this, 0) :
                    // 不是 CountedCompleter 任务
                    // 如果成功出栈，说明窃取到了
                           ForkJoinPool.common.tryExternalUnpush(this) ?
                                   // >= 0 说明未完成
                           doExec() : 0)) >= 0) {
                // 进入阻塞等待
                long ns, ms; // measure in nanosecs, but wait in millisecs
                // 未完成且还有剩余时间
                while ((s = status) >= 0 &&
                       (ns = deadline - System.nanoTime()) > 0L) {
                    // 还未超时
                    if ((ms = TimeUnit.NANOSECONDS.toMillis(ns)) > 0L &&
                            // 成功将 status 加上 SIGNAL 标志
                        (s = (int)STATUS.getAndBitwiseOr(this, SIGNAL)) >= 0) {
                        synchronized (this) {
                            if (status >= 0)
                                // 进入等待
                                wait(ms); // OK to throw InterruptedException
                            else
                                notifyAll();
                        }
                    }
                }
            }
        }
        // 走到这里说明剩余时间已经归 0 了
        // 如果还未完成
        if (s >= 0)
            // 抛出超时异常
            throw new TimeoutException();
        // 如果 s 已经完成，且 THROWN 被置为 1
        else if ((s & THROWN) != 0)
            // 抛出执行异常
            throw new ExecutionException(getThrowableException());
        // 如果 s 已经完成，且 ABNORMAL 被置为 1
        else if ((s & ABNORMAL) != 0)
            // 抛出CancellationException
            throw new CancellationException();
        else
            // 正常返回结果 s == Done
            return getRawResult();
    }

    /**
     * 仅仅等待任务执行完毕，不抛异常，也不返回执行结果
     * <p>
     * Joins this task, without returning its result or throwing its
     * exception. This method may be useful when processing
     * collections of tasks when some have been cancelled or otherwise
     * known to have aborted.
     */
    public final void quietlyJoin() {
        doJoin();
    }

    /**
     * 直接调用 invoke，不抛异常，也不返回结果
     * <p>
     * Commences performing this task and awaits its completion if
     * necessary, without returning its result or throwing its
     * exception.
     */
    public final void quietlyInvoke() {
        doInvoke();
    }

    /**
     * 帮助静默等待
     * <p>
     * Possibly executes tasks until the pool hosting the current task
     * {@linkplain ForkJoinPool#isQuiescent is quiescent}.  This
     * method may be of use in designs in which many tasks are forked,
     * but none are explicitly joined, instead executing them until
     * all are processed.
     */
    public static void helpQuiesce() {
        Thread t;
        // 如果是 ForkJoinWorkerThread 线程
        if ((t = Thread.currentThread()) instanceof ForkJoinWorkerThread) {
            ForkJoinWorkerThread wt = (ForkJoinWorkerThread)t;
            wt.pool.helpQuiescePool(wt.workQueue);
        }
        else
            // 外部线程池 common 执行静默
            ForkJoinPool.quiesceCommonPool();
    }

    /**
     * 将任务恢复至初始状态
     * <p>
     * Resets the internal bookkeeping state of this task, allowing a
     * subsequent {@code fork}. This method allows repeated reuse of
     * this task, but only if reuse occurs when this task has either
     * never been forked, or has been forked, then completed and all
     * outstanding joins of this task have also completed. Effects
     * under any other usage conditions are not guaranteed.
     * This method may be useful when executing
     * pre-constructed trees of subtasks in loops.
     *
     * <p>Upon completion of this method, {@code isDone()} reports
     * {@code false}, and {@code getException()} reports {@code
     * null}. However, the value returned by {@code getRawResult} is
     * unaffected. To clear this value, you can invoke {@code
     * setRawResult(null)}.
     */
    public void reinitialize() {
        // 未出现异常
        if ((status & THROWN) != 0)
            clearExceptionalCompletion();
        else
            status = 0;
    }

    /**
     * 返回当前线程在使用的线程池
     * <p>
     *
     * Returns the pool hosting the current thread, or {@code null}
     * if the current thread is executing outside of any ForkJoinPool.
     *
     * <p>This method returns {@code null} if and only if {@link
     * #inForkJoinPool} returns {@code false}.
     *
     * @return the pool, or {@code null} if none
     */
    public static ForkJoinPool getPool() {
        Thread t = Thread.currentThread();
        return (t instanceof ForkJoinWorkerThread) ?
            ((ForkJoinWorkerThread) t).pool : null;
    }

    /**
     * 返回当前线程是否是 ForkJoinWorkerThread 线程
     * <p>
     * 
     * Returns {@code true} if the current thread is a {@link
     * ForkJoinWorkerThread} executing as a ForkJoinPool computation.
     *
     * @return {@code true} if the current thread is a {@link
     * ForkJoinWorkerThread} executing as a ForkJoinPool computation,
     * or {@code false} otherwise
     */
    public static boolean inForkJoinPool() {
        return Thread.currentThread() instanceof ForkJoinWorkerThread;
    }

    /**
     * 尝试将任务从任务队列中 pop 出来，然后由当前线程执行
     * 只有当该任务处于栈顶且未被开始执行的情况下才会 unfork 成功
     * <p>
     * Tries to unschedule this task for execution. This method will
     * typically (but is not guaranteed to) succeed if this task is
     * the most recently forked task by the current thread, and has
     * not commenced executing in another thread.  This method may be
     * useful when arranging alternative local processing of tasks
     * that could have been, but were not, stolen.
     *
     * @return {@code true} if unforked
     */
    public boolean tryUnfork() {
        Thread t;
        // 判断是否为 ForkJoinWorkerThread
        return (((t = Thread.currentThread()) instanceof ForkJoinWorkerThread) ?
                // 如果是 ForkJoinWorkerThread，则调用 unpush 从栈中 pop 出来
                ((ForkJoinWorkerThread)t).workQueue.tryUnpush(this) :
                // 不是 ForkJoinWorkerThread，则尝试外部 common 线程池的 pop
                ForkJoinPool.common.tryExternalUnpush(this));
    }

    /**
     * 返回
     *
     * Returns an estimate of the number of tasks that have been
     * forked by the current worker thread but not yet executed. This
     * value may be useful for heuristic decisions about whether to
     * fork other tasks.
     *
     * @return the number of tasks
     */
    public static int getQueuedTaskCount() {
        Thread t; ForkJoinPool.WorkQueue q;
        // 如果当前线程是一个 ForkJoinWorkerThread 线程
        if ((t = Thread.currentThread()) instanceof ForkJoinWorkerThread)
            // 获取其 workQueue
            q = ((ForkJoinWorkerThread)t).workQueue;
        else
            // 不是 ForkJoinWorkerThread 线程，普通线程
        // TODO 普通线程在 ForkJoinPool 中是如何存储的?
            q = ForkJoinPool.commonSubmitterQueue();
        // 返回 queue 的长度
        return (q == null) ? 0 : q.queueSize();
    }

    /**
     * 返回剩余的任务数，是一个估计值
     * <p>
     *
     * Returns an estimate of how many more locally queued tasks are
     * held by the current worker thread than there are other worker
     * threads that might steal them, or zero if this thread is not
     * operating in a ForkJoinPool. This value may be useful for
     * heuristic decisions about whether to fork other tasks. In many
     * usages of ForkJoinTasks, at steady state, each worker should
     * aim to maintain a small constant surplus (for example, 3) of
     * tasks, and to process computations locally if this threshold is
     * exceeded.
     *
     * @return the surplus number of tasks, which may be negative
     */
    public static int getSurplusQueuedTaskCount() {
        return ForkJoinPool.getSurplusQueuedTaskCount();
    }

    // Extension methods

    /**
     * Returns the result that would be returned by {@link #join}, even
     * if this task completed abnormally, or {@code null} if this task
     * is not known to have been completed.  This method is designed
     * to aid debugging, as well as to support extensions. Its use in
     * any other context is discouraged.
     *
     * @return the result, or {@code null} if not completed
     */
    public abstract V getRawResult();

    /**
     * Forces the given value to be returned as a result.  This method
     * is designed to support extensions, and should not in general be
     * called otherwise.
     *
     * @param value the value
     */
    protected abstract void setRawResult(V value);

    /**
     * 一般是调用 callable/runnable 的 run 方法
     * <p>
     *
     * Immediately performs the base action of this task and returns
     * true if, upon return from this method, this task is guaranteed
     * to have completed normally. This method may return false
     * otherwise, to indicate that this task is not necessarily
     * complete (or is not known to be complete), for example in
     * asynchronous actions that require explicit invocations of
     * completion methods. This method may also throw an (unchecked)
     * exception to indicate abnormal exit. This method is designed to
     * support extensions, and should not in general be called
     * otherwise.
     *
     * @return {@code true} if this task is known to have completed normally
     */
    protected abstract boolean exec();

    /**
     * 返回一个由当前线程入队的任务
     * <p>
     * Returns, but does not unschedule or execute, a task queued by
     * the current thread but not yet executed, if one is immediately
     * available. There is no guarantee that this task will actually
     * be polled or executed next. Conversely, this method may return
     * null even if a task exists but cannot be accessed without
     * contention with other threads.  This method is designed
     * primarily to support extensions, and is unlikely to be useful
     * otherwise.
     *
     * @return the next task, or {@code null} if none are available
     */
    protected static ForkJoinTask<?> peekNextLocalTask() {
        Thread t; ForkJoinPool.WorkQueue q;
        if ((t = Thread.currentThread()) instanceof ForkJoinWorkerThread)
            q = ((ForkJoinWorkerThread)t).workQueue;
        else
            q = ForkJoinPool.commonSubmitterQueue();
        return (q == null) ? null : q.peek();
    }

    /**
     * 返回一个由当前线程提交到对应 workQueue 中的任务，该任务从队列中 poll 出来
     * <p>
     * Unschedules and returns, without executing, the next task
     * queued by the current thread but not yet executed, if the
     * current thread is operating in a ForkJoinPool.  This method is
     * designed primarily to support extensions, and is unlikely to be
     * useful otherwise.
     *
     * @return the next task, or {@code null} if none are available
     */
    protected static ForkJoinTask<?> pollNextLocalTask() {
        Thread t;
        // 只有 ForkJoinWorkerThread 能执行这个操作
        return ((t = Thread.currentThread()) instanceof ForkJoinWorkerThread) ?
            ((ForkJoinWorkerThread)t).workQueue.nextLocalTask() :
            null;
    }

    /**
     * poll 一个任务
     * <p>
     * If the current thread is operating in a ForkJoinPool,
     * unschedules and returns, without executing, the next task
     * queued by the current thread but not yet executed, if one is
     * available, or if not available, a task that was forked by some
     * other thread, if available. Availability may be transient, so a
     * {@code null} result does not necessarily imply quiescence of
     * the pool this task is operating in.  This method is designed
     * primarily to support extensions, and is unlikely to be useful
     * otherwise.
     *
     * @return a task, or {@code null} if none are available
     */
    protected static ForkJoinTask<?> pollTask() {
        Thread t; ForkJoinWorkerThread wt;
        // 只有 ForkJoinWorkerThread 能进行该操作，否则返回 null
        return ((t = Thread.currentThread()) instanceof ForkJoinWorkerThread) ?
            (wt = (ForkJoinWorkerThread)t).pool.nextTaskFor(wt.workQueue) :
            null;
    }

    /**
     * 类似 pollTask()
     * <p>
     * If the current thread is operating in a ForkJoinPool,
     * unschedules and returns, without executing, a task externally
     * submitted to the pool, if one is available. Availability may be
     * transient, so a {@code null} result does not necessarily imply
     * quiescence of the pool.  This method is designed primarily to
     * support extensions, and is unlikely to be useful otherwise.
     *
     * @return a task, or {@code null} if none are available
     * @since 9
     */
    protected static ForkJoinTask<?> pollSubmission() {
        Thread t;
        return ((t = Thread.currentThread()) instanceof ForkJoinWorkerThread) ?
            ((ForkJoinWorkerThread)t).pool.pollSubmission() : null;
    }

    // tag operations

    /**
     * 返回当前任务的 tag
     * <p>
     *
     * Returns the tag for this task.
     *
     * @return the tag for this task
     * @since 1.8
     */
    public final short getForkJoinTaskTag() {
        return (short)status;
    }

    /**
     * Atomically sets the tag value for this task and returns the old value.
     *
     * @param newValue the new tag value
     * @return the previous value of the tag
     * @since 1.8
     */
    public final short setForkJoinTaskTag(short newValue) {
        for (int s;;) {
            if (STATUS.weakCompareAndSet(this, s = status,
                                         (s & ~SMASK) | (newValue & SMASK)))
                return (short)s;
        }
    }

    /**
     * cas 更新 tag
     * <p>
     *
     * Atomically conditionally sets the tag value for this task.
     * Among other applications, tags can be used as visit markers
     * in tasks operating on graphs, as in methods that check: {@code
     * if (task.compareAndSetForkJoinTaskTag((short)0, (short)1))}
     * before processing, otherwise exiting because the node has
     * already been visited.
     *
     * @param expect the expected tag value
     * @param update the new tag value
     * @return {@code true} if successful; i.e., the current value was
     * equal to {@code expect} and was changed to {@code update}.
     * @since 1.8
     */
    public final boolean compareAndSetForkJoinTaskTag(short expect, short update) {
        for (int s;;) {
            if ((short)(s = status) != expect)
                return false;
            if (STATUS.weakCompareAndSet(this, s,
                                         (s & ~SMASK) | (update & SMASK)))
                return true;
        }
    }

    /**
     * runnable 的适配器
     * 带返回结果
     * <p>
     *
     * Adapter for Runnables. This implements RunnableFuture
     * to be compliant with AbstractExecutorService constraints
     * when used in ForkJoinPool.
     */
    static final class AdaptedRunnable<T> extends ForkJoinTask<T>
        implements RunnableFuture<T> {
        // 传入的 Runnable
        final Runnable runnable;
        // 用于保存结果
        T result;
        AdaptedRunnable(Runnable runnable, T result) {
            if (runnable == null) throw new NullPointerException();
            this.runnable = runnable;
            this.result = result; // OK to set this even before completion
        }
        public final T getRawResult() { return result; }
        public final void setRawResult(T v) { result = v; }
        public final boolean exec() { runnable.run(); return true; }
        public final void run() { invoke(); }
        public String toString() {
            return super.toString() + "[Wrapped task = " + runnable + "]";
        }
        private static final long serialVersionUID = 5232453952276885070L;
    }

    /**
     * runnable 的适配器
     * 不带返回结果
     * <p>
     *
     * Adapter for Runnables without results.
     */
    static final class AdaptedRunnableAction extends ForkJoinTask<Void>
        implements RunnableFuture<Void> {
        final Runnable runnable;
        AdaptedRunnableAction(Runnable runnable) {
            if (runnable == null) throw new NullPointerException();
            this.runnable = runnable;
        }
        public final Void getRawResult() { return null; }
        public final void setRawResult(Void v) { }
        public final boolean exec() { runnable.run(); return true; }
        public final void run() { invoke(); }
        public String toString() {
            return super.toString() + "[Wrapped task = " + runnable + "]";
        }
        private static final long serialVersionUID = 5232453952276885070L;
    }

    /**
     * Adapter for Runnables in which failure forces worker exception.
     */
    static final class RunnableExecuteAction extends ForkJoinTask<Void> {
        final Runnable runnable;
        RunnableExecuteAction(Runnable runnable) {
            if (runnable == null) throw new NullPointerException();
            this.runnable = runnable;
        }
        public final Void getRawResult() { return null; }
        public final void setRawResult(Void v) { }
        public final boolean exec() { runnable.run(); return true; }
        void internalPropagateException(Throwable ex) {
            rethrow(ex); // rethrow outside exec() catches.
        }
        private static final long serialVersionUID = 5232453952276885070L;
    }

    /**
     * callable 的适配器
     * <p>
     *
     * Adapter for Callables.
     */
    static final class AdaptedCallable<T> extends ForkJoinTask<T>
        implements RunnableFuture<T> {
        // 需要适配的 callable
        final Callable<? extends T> callable;
        // 保存结果
        T result;
        AdaptedCallable(Callable<? extends T> callable) {
            if (callable == null) throw new NullPointerException();
            this.callable = callable;
        }
        public final T getRawResult() { return result; }
        public final void setRawResult(T v) { result = v; }

        /**
         * 执行任务
         */
        public final boolean exec() {
            try {
                // 其实就是调用了 run 方法
                // 并把结果赋值给 result
                result = callable.call();
                return true;
            } catch (RuntimeException rex) {
                throw rex;
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }

        /**
         * run 方法调用了外部的 invoke 方法
         */
        public final void run() { invoke(); }
        public String toString() {
            return super.toString() + "[Wrapped task = " + callable + "]";
        }
        private static final long serialVersionUID = 2838392045355241008L;
    }

    /**
     * 创建 ForkJoinTask，封装 Runnable，不带返回结果
     * <p>
     *
     * Returns a new {@code ForkJoinTask} that performs the {@code run}
     * method of the given {@code Runnable} as its action, and returns
     * a null result upon {@link #join}.
     *
     * @param runnable the runnable action
     * @return the task
     */
    public static ForkJoinTask<?> adapt(Runnable runnable) {
        return new AdaptedRunnableAction(runnable);
    }

    /**
     * 创建 ForkJoinTask，封装 Runnable，带返回结果
     * <p>
     *
     * Returns a new {@code ForkJoinTask} that performs the {@code run}
     * method of the given {@code Runnable} as its action, and returns
     * the given result upon {@link #join}.
     *
     * @param runnable the runnable action
     * @param result the result upon completion
     * @param <T> the type of the result
     * @return the task
     */
    public static <T> ForkJoinTask<T> adapt(Runnable runnable, T result) {
        return new AdaptedRunnable<T>(runnable, result);
    }

    /**
     * 创建 ForkJoinTask
     * <p>
     *
     * Returns a new {@code ForkJoinTask} that performs the {@code call}
     * method of the given {@code Callable} as its action, and returns
     * its result upon {@link #join}, translating any checked exceptions
     * encountered into {@code RuntimeException}.
     *
     * @param callable the callable action
     * @param <T> the type of the callable's result
     * @return the task
     */
    public static <T> ForkJoinTask<T> adapt(Callable<? extends T> callable) {
        return new AdaptedCallable<T>(callable);
    }

    // Serialization support

    private static final long serialVersionUID = -7721805057305804111L;

    /**
     * Saves this task to a stream (that is, serializes it).
     *
     * @param s the stream
     * @throws java.io.IOException if an I/O error occurs
     * @serialData the current run status and the exception thrown
     * during execution, or {@code null} if none
     */
    private void writeObject(java.io.ObjectOutputStream s)
        throws java.io.IOException {
        s.defaultWriteObject();
        s.writeObject(getException());
    }

    /**
     * Reconstitutes this task from a stream (that is, deserializes it).
     * @param s the stream
     * @throws ClassNotFoundException if the class of a serialized object
     *         could not be found
     * @throws java.io.IOException if an I/O error occurs
     */
    private void readObject(java.io.ObjectInputStream s)
        throws java.io.IOException, ClassNotFoundException {
        s.defaultReadObject();
        Object ex = s.readObject();
        if (ex != null)
            setExceptionalCompletion((Throwable)ex);
    }

    // VarHandle mechanics
    private static final VarHandle STATUS;
    static {
        try {
            MethodHandles.Lookup l = MethodHandles.lookup();
            STATUS = l.findVarHandle(ForkJoinTask.class, "status", int.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

}
