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

import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * ThreadPoolExecutor 是一个 ExecutorService，能够从池中取出线程执行提交的任务
 * 通常使用 Executors 中的工厂方法来获取实例。(alibaba guide 不建议使用
 * Executor 中的工厂方法，而是自己配置参数实例化 ThreadPoolExecutor)
 *
 * <p>An {@link ExecutorService} that executes each submitted task using
 * one of possibly several pooled threads, normally configured
 * using {@link Executors} factory methods.
 *
 * <p>Thread pools address two different problems: they usually
 * provide improved performance when executing large numbers of
 * asynchronous tasks, due to reduced per-task invocation overhead,
 * and they provide a means of bounding and managing the resources,
 * including threads, consumed when executing a collection of tasks.
 * Each {@code ThreadPoolExecutor} also maintains some basic
 * statistics, such as the number of completed tasks.
 *
 * <p>ThreadPoolExecutor 提供了很多配置参数以及可扩展的 hook。
 * 建议使用 Executors 中的工厂方法(再次强调，alibaba guide 不建议使用此方式)
 * Executors 中提供了
 * 1. newCachedThreadPool(无界的线程池)
 * 2. newFixedThreadPool(固定大小的线程池)
 * 3. newSingleThreadExecutor(单个的后台线程)
 *
 * <p>To be useful across a wide range of contexts, this class
 * provides many adjustable parameters and extensibility
 * hooks. However, programmers are urged to use the more convenient
 * {@link Executors} factory methods {@link
 * Executors#newCachedThreadPool} (unbounded thread pool, with
 * automatic thread reclamation), {@link Executors#newFixedThreadPool}
 * (fixed size thread pool) and {@link
 * Executors#newSingleThreadExecutor} (single background thread), that
 * preconfigure settings for the most common usage
 * scenarios. Otherwise, use the following guide when manually
 * configuring and tuning this class:
 *
 * <dl>
 *
 * <dt>Core and maximum pool sizes</dt>
 *
 * <dd>
 * 如果当前运行的线程数少于 corePoolSize，有新的 task 进来时，即使有其他空闲的线程，
 * 也会创建新的线程来处理该任务。如果 queue 满了且当前的线程数小于 maximumPoolSize，
 * 则会创建新的线程来执行 task。maximumPoolSize = corePoolSize 相当于创建了一个
 * 固定大小的线程池。将 maximumPoolSize 设置为无界的大小，那么该线程池可以接受任意
 * 数量的并发请求。一般当值为 Integer.MAX_VALUE 的时候就称为 无界。
 * 一般情况下，corePoolSize 与 maximumPoolSize 在构造函数中初始化完成，但是
 * 通过 setCorePoolSize 和 setMaximumPoolSize 也可以动态的修改。
 * </dd>
 * <dd>A {@code ThreadPoolExecutor} will automatically adjust the
 * pool size (see {@link #getPoolSize})
 * according to the bounds set by
 * corePoolSize (see {@link #getCorePoolSize}) and
 * maximumPoolSize (see {@link #getMaximumPoolSize}).
 *
 * When a new task is submitted in method {@link #execute(Runnable)},
 * if fewer than corePoolSize threads are running, a new thread is
 * created to handle the request, even if other worker threads are
 * idle.  Else if fewer than maximumPoolSize threads are running, a
 * new thread will be created to handle the request only if the queue
 * is full.  By setting corePoolSize and maximumPoolSize the same, you
 * create a fixed-size thread pool. By setting maximumPoolSize to an
 * essentially unbounded value such as {@code Integer.MAX_VALUE}, you
 * allow the pool to accommodate an arbitrary number of concurrent
 * tasks. Most typically, core and maximum pool sizes are set only
 * upon construction, but they may also be changed dynamically using
 * {@link #setCorePoolSize} and {@link #setMaximumPoolSize}. </dd>
 *
 * <dt>On-demand construction</dt>
 *
 * <dd>
 * 默认情况下，会创建偶数个 core thread，但是当有任务到达时才会 start。
 * 可以通过 prestartCoreThread 或者 prestartAllCoreThreads 来启动 线程
 * </dd>
 * <dd>By default, even core threads are initially created and
 * started only when new tasks arrive, but this can be overridden
 * dynamically using method {@link #prestartCoreThread} or {@link
 * #prestartAllCoreThreads}.  You probably want to prestart threads if
 * you construct the pool with a non-empty queue. </dd>
 *
 * <dt>Creating new threads</dt>
 *
 * <dd>
 * 新的线程由 ThreadFactory 来创建。默认使用 Executors#defaultThreadFactory。
 * 该 ThreadFactory 创建的线程都在同一个 threadGroup 中并且线程的优先级均为
 * NORM_PRIORITY，并且不是 daemon 线程。
 * </dd>
 * <dd>New threads are created using a {@link ThreadFactory}.  If not
 * otherwise specified, a {@link Executors#defaultThreadFactory} is
 * used, that creates threads to all be in the same {@link
 * ThreadGroup} and with the same {@code NORM_PRIORITY} priority and
 * non-daemon status. By supplying a different ThreadFactory, you can
 * alter the thread's name, thread group, priority, daemon status,
 * etc. If a {@code ThreadFactory} fails to create a thread when asked
 * by returning null from {@code newThread}, the executor will
 * continue, but might not be able to execute any tasks. Threads
 * should possess the "modifyThread" {@code RuntimePermission}. If
 * worker threads or other threads using the pool do not possess this
 * permission, service may be degraded: configuration changes may not
 * take effect in a timely manner, and a shutdown pool may remain in a
 * state in which termination is possible but not completed.</dd>
 *
 * <dt>Keep-alive times</dt>
 *
 * <dd>
 * 当前线程池的线程数量超过 corePoolSize 的时候，并且处于空闲状态，那么线程在存活
 * 超过 keepAliveTime 时间之后会被 terminate。通过 setKeepAliveTime 可以设置
 * 空闲线程存活的时间。
 * 通过 allowCoreThreadTimeOut 可以控制是否允许终止线程之后，存活的线程数小于 corePoolSize
 * </dd>
 * <dd>If the pool currently has more than corePoolSize threads,
 * excess threads will be terminated if they have been idle for more
 * than the keepAliveTime (see {@link #getKeepAliveTime(TimeUnit)}).
 * This provides a means of reducing resource consumption when the
 * pool is not being actively used. If the pool becomes more active
 * later, new threads will be constructed. This parameter can also be
 * changed dynamically using method {@link #setKeepAliveTime(long,
 * TimeUnit)}.  Using a value of {@code Long.MAX_VALUE} {@link
 * TimeUnit#NANOSECONDS} effectively disables idle threads from ever
 * terminating prior to shut down. By default, the keep-alive policy
 * applies only when there are more than corePoolSize threads, but
 * method {@link #allowCoreThreadTimeOut(boolean)} can be used to
 * apply this time-out policy to core threads as well, so long as the
 * keepAliveTime value is non-zero. </dd>
 *
 * <dt>Queuing</dt>
 *
 * <dd>队列用于保存提交的任务或者将任务从队列中出队以执行
 * ，不如当没有足够线程可以执行任务的时候</dd>
 * <dd>Any {@link BlockingQueue} may be used to transfer and hold
 * submitted tasks.  The use of this queue interacts with pool sizing:
 *
 * <ul>
 *
 * <li>当线程数小于 corePoolSize 的时候，会创建线程，而不是将任务入队。
 * <li>If fewer than corePoolSize threads are running, the Executor
 * always prefers adding a new thread
 * rather than queuing.
 *
 * <li>如果有 corePoolSize 或者更多的线程在运行，那么后续的任务都将入队。
 * <li>If corePoolSize or more threads are running, the Executor
 * always prefers queuing a request rather than adding a new
 * thread.
 *
 * <li>如果一个线程不能入队了，也就是说队列满了。如果此时线程数小于 maximumPoolSize，
 * 那么会创建线程。否则则根据 RejectedExecutionHandler 对任务进行处理。
 * <li>If a request cannot be queued, a new thread is created unless
 * this would exceed maximumPoolSize, in which case, the task will be
 * rejected.
 *
 * </ul>
 *
 * 以下是三个入队的常规策略:
 * There are three general strategies for queuing:
 * <ol>
 *
 * <li><em>直接传递</em> 直接将任务提交给线程池执行
 * <li><em> Direct handoffs.</em> A good default choice for a work
 * queue is a {@link SynchronousQueue} that hands off tasks to threads
 * without otherwise holding them. Here, an attempt to queue a task
 * will fail if no threads are immediately available to run it, so a
 * new thread will be constructed. This policy avoids lockups when
 * handling sets of requests that might have internal dependencies.
 * Direct handoffs generally require unbounded maximumPoolSizes to
 * avoid rejection of new submitted tasks. This in turn admits the
 * possibility of unbounded thread growth when commands continue to
 * arrive on average faster than they can be processed.
 *
 * <li>无界队列 LinkedBlockingQueue，这种情况下 maximumPoolSize 没有意义
 * 该队列适用于各个任务都相互独立的情况。
 * <li><em> Unbounded queues.</em> Using an unbounded queue (for
 * example a {@link LinkedBlockingQueue} without a predefined
 * capacity) will cause new tasks to wait in the queue when all
 * corePoolSize threads are busy. Thus, no more than corePoolSize
 * threads will ever be created. (And the value of the maximumPoolSize
 * therefore doesn't have any effect.)  This may be appropriate when
 * each task is completely independent of others, so tasks cannot
 * affect each others execution; for example, in a web page server.
 * While this style of queuing can be useful in smoothing out
 * transient bursts of requests, it admits the possibility of
 * unbounded work queue growth when commands continue to arrive on
 * average faster than they can be processed.
 *
 * <li>有界队列 ArrayBlockingQueue。
 * <li><em>Bounded queues.</em> A bounded queue (for example, an
 * {@link ArrayBlockingQueue}) helps prevent resource exhaustion when
 * used with finite maximumPoolSizes, but can be more difficult to
 * tune and control.  Queue sizes and maximum pool sizes may be traded
 * off for each other: Using large queues and small pools minimizes
 * CPU usage, OS resources, and context-switching overhead, but can
 * lead to artificially low throughput.  If tasks frequently block (for
 * example if they are I/O bound), a system may be able to schedule
 * time for more threads than you otherwise allow. Use of small queues
 * generally requires larger pool sizes, which keeps CPUs busier but
 * may encounter unacceptable scheduling overhead, which also
 * decreases throughput.
 *
 * </ol>
 *
 * </dd>
 *
 * <dt>Rejected tasks</dt>
 *
 * <dd>Executor 关闭之后，新提交的任务会被拒绝。
 * <dd>New tasks submitted in method {@link #execute(Runnable)} will be
 * <em>rejected</em> when the Executor has been shut down, and also when
 * the Executor uses finite bounds for both maximum threads and work queue
 * capacity, and is saturated.  In either case, the {@code execute} method
 * invokes the {@link
 * RejectedExecutionHandler#rejectedExecution(Runnable, ThreadPoolExecutor)}
 * method of its {@link RejectedExecutionHandler}.  Four predefined handler
 * policies are provided:
 *
 * <ol>
 *
 * <li>In the default {@link ThreadPoolExecutor.AbortPolicy}, the handler
 * throws a runtime {@link RejectedExecutionException} upon rejection.
 *
 * <li>In {@link ThreadPoolExecutor.CallerRunsPolicy}, the thread
 * that invokes {@code execute} itself runs the task. This provides a
 * simple feedback control mechanism that will slow down the rate that
 * new tasks are submitted.
 *
 * <li>In {@link ThreadPoolExecutor.DiscardPolicy}, a task that
 * cannot be executed is simply dropped.
 *
 * <li>In {@link ThreadPoolExecutor.DiscardOldestPolicy}, if the
 * executor is not shut down, the task at the head of the work queue
 * is dropped, and then execution is retried (which can fail again,
 * causing this to be repeated.)
 *
 * </ol>
 *
 * It is possible to define and use other kinds of {@link
 * RejectedExecutionHandler} classes. Doing so requires some care
 * especially when policies are designed to work only under particular
 * capacity or queuing policies. </dd>
 *
 * <dt>Hook methods</dt>
 *
 * <dd>提供 hook 方法，可以定义执行前后的一些逻辑
 * <dd>This class provides {@code protected} overridable
 * {@link #beforeExecute(Thread, Runnable)} and
 * {@link #afterExecute(Runnable, Throwable)} methods that are called
 * before and after execution of each task.  These can be used to
 * manipulate the execution environment; for example, reinitializing
 * ThreadLocals, gathering statistics, or adding log entries.
 * Additionally, method {@link #terminated} can be overridden to perform
 * any special processing that needs to be done once the Executor has
 * fully terminated.
 *
 * <p>If hook, callback, or BlockingQueue methods throw exceptions,
 * internal worker threads may in turn fail, abruptly terminate, and
 * possibly be replaced.</dd>
 *
 * <dt>队列维护</dt>
 * <dt>Queue maintenance</dt>
 *
 * <dd>getQueue 建议仅用于监控和调试，不建议用于其他场景，
 * <dd>Method {@link #getQueue()} allows access to the work queue
 * for purposes of monitoring and debugging.  Use of this method for
 * any other purpose is strongly discouraged.  Two supplied methods,
 * {@link #remove(Runnable)} and {@link #purge} are available to
 * assist in storage reclamation when large numbers of queued tasks
 * become cancelled.</dd>
 *
 * <dt>回收</dt>
 * <dt>Reclamation</dt>
 *
 * <dd>A pool that is no longer referenced in a program <em>AND</em>
 * has no remaining threads may be reclaimed (garbage collected)
 * without being explicitly shutdown. You can configure a pool to
 * allow all unused threads to eventually die by setting appropriate
 * keep-alive times, using a lower bound of zero core threads and/or
 * setting {@link #allowCoreThreadTimeOut(boolean)}.  </dd>
 *
 * </dl>
 *
 * <p><b>Extension example</b>. Most extensions of this class
 * override one or more of the protected hook methods. For example,
 * here is a subclass that adds a simple pause/resume feature:
 *
 * <pre> {@code
 * class PausableThreadPoolExecutor extends ThreadPoolExecutor {
 *   private boolean isPaused;
 *   private ReentrantLock pauseLock = new ReentrantLock();
 *   private Condition unpaused = pauseLock.newCondition();
 *
 *   public PausableThreadPoolExecutor(...) { super(...); }
 *
 *   protected void beforeExecute(Thread t, Runnable r) {
 *     super.beforeExecute(t, r);
 *     pauseLock.lock();
 *     try {
 *       while (isPaused) unpaused.await();
 *     } catch (InterruptedException ie) {
 *       t.interrupt();
 *     } finally {
 *       pauseLock.unlock();
 *     }
 *   }
 *
 *   public void pause() {
 *     pauseLock.lock();
 *     try {
 *       isPaused = true;
 *     } finally {
 *       pauseLock.unlock();
 *     }
 *   }
 *
 *   public void resume() {
 *     pauseLock.lock();
 *     try {
 *       isPaused = false;
 *       unpaused.signalAll();
 *     } finally {
 *       pauseLock.unlock();
 *     }
 *   }
 * }}</pre>
 *
 * @since 1.5
 * @author Doug Lea
 */
public class ThreadPoolExecutor extends AbstractExecutorService {
    /**
     * ctl: 用于线程状态的控制，能够代表两种变量值。
     * 1. workerCount: 有效的线程数
     * 2. runState： 线程的状态，运行或者终止等
     * <p>
     * The main pool control state, ctl, is an atomic integer packing
     * two conceptual fields
     *   workerCount, indicating the effective number of threads
     *   runState,    indicating whether running, shutting down etc
     *
     * <p>workerCount: 前 29 位。
     * 如果未来不够，可以扩展成使用 AtomicLong。
     * <p>In order to pack them into one int, we limit workerCount to
     * (2^29)-1 (about 500 million) threads rather than (2^31)-1 (2
     * billion) otherwise representable. If this is ever an issue in
     * the future, the variable can be changed to be an AtomicLong,
     * and the shift/mask constants below adjusted. But until the need
     * arises, this code is a bit faster and simpler using an int.
     * <p>workerCount 代表的是允许 start 但是不允许 stop 的线程数。该值可能与线程池
     * 中实际存活的线程数不同。
     * <p>The workerCount is the number of workers that have been
     * permitted to start and not permitted to stop.  The value may be
     * transiently different from the actual number of live threads,
     * for example when a ThreadFactory fails to create a thread when
     * asked, and when exiting threads are still performing
     * bookkeeping before terminating. The user-visible pool size is
     * reported as the current size of the workers set.
     *
     * 线程池运行的状态:
     * The runState provides the main lifecycle control, taking on values:
     *
     *   RUNNING:  Accept new tasks and process queued tasks
     *             接受新的任务并且会处理队列中的任务
     *   SHUTDOWN: Don't accept new tasks, but process queued tasks
     *             不处理新的任务，但是还是会处理队列中的任务
     *   STOP:     Don't accept new tasks, don't process queued tasks,
     *             and interrupt in-progress tasks
     *             不接受新的任务，不处理队列中的任务，并且还会中断正在进行中的任务
     *   TIDYING:  All tasks have terminated, workerCount is zero,
     *             the thread transitioning to state TIDYING
     *             will run the terminated() hook method
     *             所有的任务都已经被终止，工作线程也为 0 。
     *   TERMINATED: terminated() has completed
     *               terminated 回调方法会被调用
     *
     * 状态转移:
     * The numerical order among these values matters, to allow
     * ordered comparisons. The runState monotonically increases over
     * time, but need not hit each state. The transitions are:
     *
     * RUNNING -> SHUTDOWN
     *    On invocation of shutdown()
     * (RUNNING or SHUTDOWN) -> STOP
     *    On invocation of shutdownNow()
     * SHUTDOWN -> TIDYING
     *    When both queue and pool are empty
     * STOP -> TIDYING
     *    When pool is empty
     * TIDYING -> TERMINATED
     *    When the terminated() hook method has completed
     *
     * Threads waiting in awaitTermination() will return when the
     * state reaches TERMINATED.
     *
     * Detecting the transition from SHUTDOWN to TIDYING is less
     * straightforward than you'd like because the queue may become
     * empty after non-empty and vice versa during SHUTDOWN state, but
     * we can only terminate if, after seeing that it is empty, we see
     * that workerCount is 0 (which sometimes entails a recheck -- see
     * below).
     */
    private final AtomicInteger ctl = new AtomicInteger(ctlOf(RUNNING, 0));
    /**
     * 29
     */
    private static final int COUNT_BITS = Integer.SIZE - 3;
    /**
     * 低 29 位 全 1
     */
    private static final int COUNT_MASK = (1 << COUNT_BITS) - 1;

    // runState is stored in the high-order bits
    // 运行的五种状态
    /**
     * < 0
     */
    private static final int RUNNING    = -1 << COUNT_BITS;
    /**
     * 0
     */
    private static final int SHUTDOWN   =  0 << COUNT_BITS;
    /**
     * 2 ^ 29
     */
    private static final int STOP       =  1 << COUNT_BITS;
    /**
     * 2 ^ 30
     */
    private static final int TIDYING    =  2 << COUNT_BITS;
    /**
     * 2 ^ 29 + 2 ^ 30
     */
    private static final int TERMINATED =  3 << COUNT_BITS;

    // Packing and unpacking ctl

    /**
     * 返回当前的状态
     */
    private static int runStateOf(int c)     { return c & ~COUNT_MASK; }

    /**
     * 返回处于 start 状态的线程数
     */
    private static int workerCountOf(int c)  { return c & COUNT_MASK; }

    /**
     * 拼接成 ctl
     */
    private static int ctlOf(int rs, int wc) { return rs | wc; }

    /*
     * Bit field accessors that don't require unpacking ctl.
     * These depend on the bit layout and on workerCount being never negative.
     */

    /**
     * ctl 与 状态常量进行比较
     */
    private static boolean runStateLessThan(int c, int s) {
        return c < s;
    }

    /**
     * ctl 与 状态常量进行比较
     */
    private static boolean runStateAtLeast(int c, int s) {
        return c >= s;
    }

    /**
     * 返回当前线程池是否处于运行状态
     */
    private static boolean isRunning(int c) {
        return c < SHUTDOWN;
    }

    /**
     * Attempts to CAS-increment the workerCount field of ctl.
     */
    private boolean compareAndIncrementWorkerCount(int expect) {
        return ctl.compareAndSet(expect, expect + 1);
    }

    /**
     * Attempts to CAS-decrement the workerCount field of ctl.
     */
    private boolean compareAndDecrementWorkerCount(int expect) {
        return ctl.compareAndSet(expect, expect - 1);
    }

    /**
     * Decrements the workerCount field of ctl. This is called only on
     * abrupt termination of a thread (see processWorkerExit). Other
     * decrements are performed within getTask.
     */
    private void decrementWorkerCount() {
        ctl.addAndGet(-1);
    }

    /**
     * 阻塞队列，FIFO 策略执行。保存任务并传递任务给线程执行。
     * <p>
     * The queue used for holding tasks and handing off to worker
     * threads.  We do not require that workQueue.poll() returning
     * null necessarily means that workQueue.isEmpty(), so rely
     * solely on isEmpty to see if the queue is empty (which we must
     * do for example when deciding whether to transition from
     * SHUTDOWN to TIDYING).  This accommodates special-purpose
     * queues such as DelayQueues for which poll() is allowed to
     * return null even if it may later return non-null when delays
     * expire.
     */
    private final BlockingQueue<Runnable> workQueue;

    /**
     * 访问阻塞队列，工作线程集合用的锁
     * <p>Lock held on access to workers set and related bookkeeping.
     * While we could use a concurrent set of some sort, it turns out
     * to be generally preferable to use a lock. Among the reasons is
     * that this serializes interruptIdleWorkers, which avoids
     * unnecessary interrupt storms, especially during shutdown.
     * Otherwise exiting threads would concurrently interrupt those
     * that have not yet interrupted. It also simplifies some of the
     * associated statistics bookkeeping of largestPoolSize etc. We
     * also hold mainLock on shutdown and shutdownNow, for the sake of
     * ensuring workers set is stable while separately checking
     * permission to interrupt and actually interrupting.
     */
    private final ReentrantLock mainLock = new ReentrantLock();

    /**
     * 保存所有在线程池中的工作线程
     * <p>Set containing all worker threads in pool. Accessed only when
     * holding mainLock.
     */
    private final HashSet<Worker> workers = new HashSet<>();

    /**
     * awaitTermination 方法会在该条件上等待
     * 等待在线程池终止这个条件上
     * <p>
     * Wait condition to support awaitTermination.
     */
    private final Condition termination = mainLock.newCondition();

    /**
     * 保存线程池存在过的最多的线程的数量
     * <p>
     * Tracks largest attained pool size. Accessed only under
     * mainLock.
     */
    private int largestPoolSize;

    /**
     * 所有线程完成的任务数的计数
     * <p>
     * Counter for completed tasks. Updated only on termination of
     * worker threads. Accessed only under mainLock.
     */
    private long completedTaskCount;

    /*
     * All user control parameters are declared as volatiles so that
     * ongoing actions are based on freshest values, but without need
     * for locking, since no internal invariants depend on them
     * changing synchronously with respect to other actions.
     */

    /**
     * 线程创建工厂
     * <p>Factory for new threads. All threads are created using this
     * factory (via method addWorker).  All callers must be prepared
     * for addWorker to fail, which may reflect a system or user's
     * policy limiting the number of threads.  Even though it is not
     * treated as an error, failure to create threads may result in
     * new tasks being rejected or existing ones remaining stuck in
     * the queue.
     *
     * We go further and preserve pool invariants even in the face of
     * errors such as OutOfMemoryError, that might be thrown while
     * trying to create threads.  Such errors are rather common due to
     * the need to allocate a native stack in Thread.start, and users
     * will want to perform clean pool shutdown to clean up.  There
     * will likely be enough memory available for the cleanup code to
     * complete without encountering yet another OutOfMemoryError.
     */
    private volatile ThreadFactory threadFactory;

    /**
     * Handler called when saturated or shutdown in execute.
     */
    private volatile RejectedExecutionHandler handler;

    /**
     * 超出 corePoolSize 的线程的 idle 时间，超过这个时间则会被 terminate
     * <p>
     * Timeout in nanoseconds for idle threads waiting for work.
     * Threads use this timeout when there are more than corePoolSize
     * present or if allowCoreThreadTimeOut. Otherwise they wait
     * forever for new work.
     */
    private volatile long keepAliveTime;

    /**
     * 是否允许空闲线程被回收至线程数小于 corePoolSize
     * <p>If false (default), core threads stay alive even when idle.
     * If true, core threads use keepAliveTime to time out waiting
     * for work.
     */
    private volatile boolean allowCoreThreadTimeOut;

    /**
     * Core pool size is the minimum number of workers to keep alive
     * (and not allow to time out etc) unless allowCoreThreadTimeOut
     * is set, in which case the minimum is zero.
     *
     * Since the worker count is actually stored in COUNT_BITS bits,
     * the effective limit is {@code corePoolSize & COUNT_MASK}.
     */
    private volatile int corePoolSize;

    /**
     * Maximum pool size.
     *
     * Since the worker count is actually stored in COUNT_BITS bits,
     * the effective limit is {@code maximumPoolSize & COUNT_MASK}.
     */
    private volatile int maximumPoolSize;

    /**
     * 默认的拒绝策略使用的是 AbortPolicy
     * <p>The default rejected execution handler.
     */
    private static final RejectedExecutionHandler defaultHandler =
        new AbortPolicy();

    /**
     * Permission required for callers of shutdown and shutdownNow.
     * We additionally require (see checkShutdownAccess) that callers
     * have permission to actually interrupt threads in the worker set
     * (as governed by Thread.interrupt, which relies on
     * ThreadGroup.checkAccess, which in turn relies on
     * SecurityManager.checkAccess). Shutdowns are attempted only if
     * these checks pass.
     *
     * All actual invocations of Thread.interrupt (see
     * interruptIdleWorkers and interruptWorkers) ignore
     * SecurityExceptions, meaning that the attempted interrupts
     * silently fail. In the case of shutdown, they should not fail
     * unless the SecurityManager has inconsistent policies, sometimes
     * allowing access to a thread and sometimes not. In such cases,
     * failure to actually interrupt threads may disable or delay full
     * termination. Other uses of interruptIdleWorkers are advisory,
     * and failure to actually interrupt will merely delay response to
     * configuration changes so is not handled exceptionally.
     */
    private static final RuntimePermission shutdownPerm =
        new RuntimePermission("modifyThread");

    /**
     * 继承了AQS，也是一个同步器。
     * <p>
     * Class Worker mainly maintains interrupt control state for
     * threads running tasks, along with other minor bookkeeping.
     * This class opportunistically extends AbstractQueuedSynchronizer
     * to simplify acquiring and releasing a lock surrounding each
     * task execution.  This protects against interrupts that are
     * intended to wake up a worker thread waiting for a task from
     * instead interrupting a task being run.  We implement a simple
     * non-reentrant mutual exclusion lock rather than use
     * ReentrantLock because we do not want worker tasks to be able to
     * reacquire the lock when they invoke pool control methods like
     * setCorePoolSize.  Additionally, to suppress interrupts until
     * the thread actually starts running tasks, we initialize lock
     * state to a negative value, and clear it upon start (in
     * runWorker).
     */
    private final class Worker
        extends AbstractQueuedSynchronizer
        implements Runnable
    {
        /**
         * This class will never be serialized, but we provide a
         * serialVersionUID to suppress a javac warning.
         */
        private static final long serialVersionUID = 6138294804551838833L;

        /**
         * 当前任务运行的线程
         * <p>
         * Thread this worker is running in.  Null if factory fails.
         */
        final Thread thread;
        /**
         * 初始的任务/第一个任务<p>
         * Initial task to run.  Possibly null.
         */
        Runnable firstTask;
        /**
         * 当前线程完成过的任务数
         * <p>
         * Per-thread task counter
         */
        volatile long completedTasks;

        // TODO: switch to AbstractQueuedLongSynchronizer and move
        // completedTasks into the lock word.

        /**
         * Creates with given first task and thread from ThreadFactory.
         * @param firstTask the first task (null if none)
         */
        Worker(Runnable firstTask) {
            // 这里初始化时的 state 为 -1
            setState(-1); // inhibit interrupts until runWorker
            this.firstTask = firstTask;
            // 创建一个新线程
            this.thread = getThreadFactory().newThread(this);
        }

        /**
         * 线程 start 之后执行任务
         * <p>
         * Delegates main run loop to outer runWorker.
         */
        public void run() {
            runWorker(this);
        }

        // Lock methods
        //
        // The value 0 represents the unlocked state.
        // The value 1 represents the locked state.

        protected boolean isHeldExclusively() {
            // 返回锁是否被占有
            return getState() != 0;
        }

        /**
         * 尝试获取锁，一次性获取
         */
        protected boolean tryAcquire(int unused) {
            if (compareAndSetState(0, 1)) {
                setExclusiveOwnerThread(Thread.currentThread());
                return true;
            }
            return false;
        }

        /**
         * 尝试释放锁
         */
        protected boolean tryRelease(int unused) {
            setExclusiveOwnerThread(null);
            setState(0);
            return true;
        }

        public void lock()        { acquire(1); }
        public boolean tryLock()  { return tryAcquire(1); }
        public void unlock()      { release(1); }
        public boolean isLocked() { return isHeldExclusively(); }

        /**
         * 线程如果启动了，则将其中断
         */
        void interruptIfStarted() {
            Thread t;
            if (getState() >= 0 && (t = thread) != null && !t.isInterrupted()) {
                try {
                    t.interrupt();
                } catch (SecurityException ignore) {
                }
            }
        }
    }

    /*
     * Methods for setting control state
     */

    /**
     * 将 runState 转为 target。target 为 SHUTDOWN 或 STOP
     * <p>
     * Transitions runState to given target, or leaves it alone if
     * already at least the given target.
     *
     * @param targetState the desired state, either SHUTDOWN or STOP
     *        (but not TIDYING or TERMINATED -- use tryTerminate for that)
     */
    private void advanceRunState(int targetState) {
        // assert targetState == SHUTDOWN || targetState == STOP;
        // 自旋
        for (;;) {
            int c = ctl.get();
            if (runStateAtLeast(c, targetState) ||
                ctl.compareAndSet(c, ctlOf(targetState, workerCountOf(c))))
                break;
        }
    }

    /**
     * 尝试终止线程池或者中断一个线程
     * <p>
     * Transitions to TERMINATED state if either (SHUTDOWN and pool
     * and queue empty) or (STOP and pool empty).  If otherwise
     * eligible to terminate but workerCount is nonzero, interrupts an
     * idle worker to ensure that shutdown signals propagate. This
     * method must be called following any action that might make
     * termination possible -- reducing worker count or removing tasks
     * from the queue during shutdown. The method is non-private to
     * allow access from ScheduledThreadPoolExecutor.
     */
    final void tryTerminate() {
        // 自旋
        for (;;) {
            // 获取 ctl 状态
            int c = ctl.get();
            // 运行状态
            if (isRunning(c) ||
                    // 大于等于 TIDYING 状态
                runStateAtLeast(c, TIDYING) ||
                    // 小于 STOP 状态并且队列非空
                (runStateLessThan(c, STOP) && ! workQueue.isEmpty()))
                // 均直接返回
                return;
            // 工作线程数不为 0
            if (workerCountOf(c) != 0) { // Eligible to terminate
                // 尝试中断一个线程
                interruptIdleWorkers(ONLY_ONE);
                // 中断成功也直接返回
                return;
            }

            final ReentrantLock mainLock = this.mainLock;
            mainLock.lock();
            try {
                // 将 ctl 的状态改成 TIDYING 状态
                // 清空了所有的工作线程
                if (ctl.compareAndSet(c, ctlOf(TIDYING, 0))) {
                    try {
                        // 终止
                        // 回调方法
                        terminated();
                    } finally {
                        // 设置成 TERMINATED 状态
                        ctl.set(ctlOf(TERMINATED, 0));
                        // 唤醒所有在 termination 上等待的线程
                        termination.signalAll();
                    }
                    return;
                }
            } finally {
                mainLock.unlock();
            }
            // else retry on failed CAS
        }
    }

    /*
     * Methods for controlling interrupts to worker threads.
     */

    /**
     * If there is a security manager, makes sure caller has
     * permission to shut down threads in general (see shutdownPerm).
     * If this passes, additionally makes sure the caller is allowed
     * to interrupt each worker thread. This might not be true even if
     * first check passed, if the SecurityManager treats some threads
     * specially.
     */
    private void checkShutdownAccess() {
        // assert mainLock.isHeldByCurrentThread();
        SecurityManager security = System.getSecurityManager();
        if (security != null) {
            security.checkPermission(shutdownPerm);
            for (Worker w : workers)
                security.checkAccess(w.thread);
        }
    }

    /**
     * 中断所有的 workers
     * <p>
     * Interrupts all threads, even if active. Ignores SecurityExceptions
     * (in which case some threads may remain uninterrupted).
     */
    private void interruptWorkers() {
        // assert mainLock.isHeldByCurrentThread();
        for (Worker w : workers)
            w.interruptIfStarted();
    }

    /**
     * 中断在等待任务到来的线程，这样就可以根据配置变化或者其他情况对
     * 线程的存活情况作出调整。
     * <p>
     *
     * Interrupts threads that might be waiting for tasks (as
     * indicated by not being locked) so they can check for
     * termination or configuration changes. Ignores
     * SecurityExceptions (in which case some threads may remain
     * uninterrupted).
     *
     * @param onlyOne If true, interrupt at most one worker. This is
     * called only from tryTerminate when termination is otherwise
     * enabled but there are still other workers.  In this case, at
     * most one waiting worker is interrupted to propagate shutdown
     * signals in case all threads are currently waiting.
     * Interrupting any arbitrary thread ensures that newly arriving
     * workers since shutdown began will also eventually exit.
     * To guarantee eventual termination, it suffices to always
     * interrupt only one idle worker, but shutdown() interrupts all
     * idle workers so that redundant workers exit promptly, not
     * waiting for a straggler task to finish.
     */
    private void interruptIdleWorkers(boolean onlyOne) {
        final ReentrantLock mainLock = this.mainLock;
        // 加锁
        mainLock.lock();
        try {
            for (Worker w : workers) {
                Thread t = w.thread;
                // 没有被中断且获取锁成功
                if (!t.isInterrupted() && w.tryLock()) {
                    try {
                        // 中断
                        t.interrupt();
                    } catch (SecurityException ignore) {
                    } finally {
                        // 释放锁
                        w.unlock();
                    }
                }
                // 如果只能操作一个，那么直接 break
                if (onlyOne)
                    break;
            }
        } finally {
            // 释放锁
            mainLock.unlock();
        }
    }

    /**
     * 中断 idle 线程
     * <p>
     * Common form of interruptIdleWorkers, to avoid having to
     * remember what the boolean argument means.
     */
    private void interruptIdleWorkers() {
        interruptIdleWorkers(false);
    }

    private static final boolean ONLY_ONE = true;

    /*
     * Misc utilities, most of which are also exported to
     * ScheduledThreadPoolExecutor
     */

    /**
     * 调用 RejectedExecutionHandler 执行拒绝策略
     * <p>
     * Invokes the rejected execution handler for the given command.
     * Package-protected for use by ScheduledThreadPoolExecutor.
     */
    final void reject(Runnable command) {
        handler.rejectedExecution(command, this);
    }

    /**
     * shutdown 的 hook
     * <p>
     * Performs any further cleanup following run state transition on
     * invocation of shutdown.  A no-op here, but used by
     * ScheduledThreadPoolExecutor to cancel delayed tasks.
     */
    void onShutdown() {
    }

    /**
     * 清空队列，并返回队列中的任务
     * <p>
     * Drains the task queue into a new list, normally using
     * drainTo. But if the queue is a DelayQueue or any other kind of
     * queue for which poll or drainTo may fail to remove some
     * elements, it deletes them one by one.
     */
    private List<Runnable> drainQueue() {
        BlockingQueue<Runnable> q = workQueue;
        ArrayList<Runnable> taskList = new ArrayList<>();
        q.drainTo(taskList);
        // 处理 DelayQueue 或者其他 drain 失败的情况
        if (!q.isEmpty()) {
            // 遍历移除
            for (Runnable r : q.toArray(new Runnable[0])) {
                if (q.remove(r))
                    taskList.add(r);
            }
        }
        return taskList;
    }

    /*
     * Methods for creating, running and cleaning up after workers
     */

    /**
     * check 是否能够创建一个新的 worker，如果可以，则相应的更新 workerCount。
     * 如果 pool 停止了或者马上终止了，那么该方法会返回 false。threadFactory
     * 创建失败也会返回 false。
     *
     * Checks if a new worker can be added with respect to current
     * pool state and the given bound (either core or maximum). If so,
     * the worker count is adjusted accordingly, and, if possible, a
     * new worker is created and started, running firstTask as its
     * first task. This method returns false if the pool is stopped or
     * eligible to shut down. It also returns false if the thread
     * factory fails to create a thread when asked.  If the thread
     * creation fails, either due to the thread factory returning
     * null, or due to an exception (typically OutOfMemoryError in
     * Thread.start()), we roll back cleanly.
     *
     * @param firstTask the task the new thread should run first (or
     * null if none). Workers are created with an initial first task
     * (in method execute()) to bypass queuing when there are fewer
     * than corePoolSize threads (in which case we always start one),
     * or when the queue is full (in which case we must bypass queue).
     * Initially idle threads are usually created via
     * prestartCoreThread or to replace other dying workers. 第一个要执行的任务
     *
     * @param core if true use corePoolSize as bound, else
     * maximumPoolSize. (A boolean indicator is used here rather than a
     * value to ensure reads of fresh values after checking other pool
     * state). 是否为 core thread
     * @return true if successful
     */
    private boolean addWorker(Runnable firstTask, boolean core) {
        retry:
        // 自旋
        // 获取控制状态
        for (int c = ctl.get();;) {
            // Check if queue empty only if necessary.
            // 如果当前的 ctl 大于 SHUTDOWN
            if (runStateAtLeast(c, SHUTDOWN)
                    // ctl 大于 STOP
                && (runStateAtLeast(c, STOP)
                    || firstTask != null
                    || workQueue.isEmpty()))
                // 直接返回添加失败，其实就是当前线程池已经 shutdown 状态下
                // 并且如果线程池的状态至少为 STOP 状态,则不再添加 worker
                return false;

            // 自旋
            for (;;) {
                // 根据创建的线程类型使用不用的 size 来判断
                if (workerCountOf(c)
                    >= ((core ? corePoolSize : maximumPoolSize) & COUNT_MASK))
                    // 如果超过了，则直接返回 false，创建错误
                    return false;
                // 尝试 cas + 1
                if (compareAndIncrementWorkerCount(c))
                    // 添加成功则直接 break
                    break retry;
                // cas 失败
                c = ctl.get();  // Re-read ctl
                // 再次检查是否已经至少为 SHUTDOWN 状态
                if (runStateAtLeast(c, SHUTDOWN))
                    // 如果已经是，则跳过
                    // 在下一次循环的时候会作出决策
                    continue retry;
                // else CAS failed due to workerCount change; retry inner loop
            }
        }

        // 走到这里说明 ctl 更新成功了
        // worker 是否启动的标志
        boolean workerStarted = false;
        // 是否成功将 worker 添加进去的标志
        boolean workerAdded = false;
        Worker w = null;
        try {
            // 创建一个 worker
            w = new Worker(firstTask);
            // 新创建的线程
            final Thread t = w.thread;
            if (t != null) {
                // 成功创建了线程
                final ReentrantLock mainLock = this.mainLock;
                // 加锁
                mainLock.lock();
                try {
                    // Recheck while holding lock.
                    // Back out on ThreadFactory failure or if
                    // shut down before lock acquired.
                    int c = ctl.get();

                    if (isRunning(c) ||
                            // 当前处于 SHUTDOWN 状态 且 firstTask 为 null
                        (runStateLessThan(c, STOP) && firstTask == null)) {
                        // 如果线程的状态不为 NEW
                        if (t.getState() != Thread.State.NEW)
                            // 抛异常
                            throw new IllegalThreadStateException();
                        // 添加到 workers 中
                        workers.add(w);
                        // 已经添加的标志
                        workerAdded = true;
                        int s = workers.size();
                        if (s > largestPoolSize)
                            // 设置 s
                            largestPoolSize = s;
                    }
                } finally {
                    // 释放锁
                    mainLock.unlock();
                }
                // 如果成功添加
                if (workerAdded) {
                    // 启动该线程
                    // 启动之后就线程就会开始执行任务
                    t.start();
                    // 启动标志也置为 true
                    workerStarted = true;
                }
            }
        } finally {
            // 若没有启动
            if (! workerStarted)
                // 说明启动失败
                addWorkerFailed(w);
        }
        // 返回是否已经成功启动
        return workerStarted;
    }

    /**
     * Rolls back the worker thread creation.
     * - removes worker from workers, if present
     * - decrements worker count
     * - rechecks for termination, in case the existence of this
     *   worker was holding up termination
     */
    private void addWorkerFailed(Worker w) {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            if (w != null)
                // workers 中移除该 worker
                workers.remove(w);
            // 减少 workerCount
            decrementWorkerCount();
            // 尝试终止
            tryTerminate();
        } finally {
            mainLock.unlock();
        }
    }

    /**
     * 处理工作线程退出的工作
     * <p>
     * Performs cleanup and bookkeeping for a dying worker. Called
     * only from worker threads. Unless completedAbruptly is set,
     * assumes that workerCount has already been adjusted to account
     * for exit.  This method removes thread from worker set, and
     * possibly terminates the pool or replaces the worker if either
     * it exited due to user task exception or if fewer than
     * corePoolSize workers are running or queue is non-empty but
     * there are no workers.
     *
     * @param w the worker
     * @param completedAbruptly if the worker died due to user exception
     */
    private void processWorkerExit(Worker w, boolean completedAbruptly) {
        // 如果是异常结束
        if (completedAbruptly) // If abrupt, then workerCount wasn't adjusted
            // worker - 1
            decrementWorkerCount();

        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            // 将完成的任务的计数汇总到 completedTaskCount 中
            completedTaskCount += w.completedTasks;
            // 移除当前 worker
            workers.remove(w);
        } finally {
            mainLock.unlock();
        }

        // 尝试终止线程池
        tryTerminate();

        int c = ctl.get();
        if (runStateLessThan(c, STOP)) {
            if (!completedAbruptly) {
                int min = allowCoreThreadTimeOut ? 0 : corePoolSize;
                if (min == 0 && ! workQueue.isEmpty())
                    min = 1;
                // 不需要补充线程
                if (workerCountOf(c) >= min)
                    return; // replacement not needed
            }
            // 添加线程
            addWorker(null, false);
        }
    }

    /**
     * 从队列中取任务
     * <p>
     * Performs blocking or timed wait for a task, depending on
     * current configuration settings, or returns null if this worker
     * must exit because of any of:
     * 1. worker 数超过 maximumPoolSize，因为通过 setMaximumPoolSize 将其变小
     * 1. There are more than maximumPoolSize workers (due to
     *    a call to setMaximumPoolSize).
     * 2. pool 停止
     * 2. The pool is stopped.
     * 3. pool shutdown 了且 workqueue 为空
     * 3. The pool is shutdown and the queue is empty.
     * 4. This worker timed out waiting for a task, and timed-out
     *    workers are subject to termination (that is,
     *    {@code allowCoreThreadTimeOut || workerCount > corePoolSize})
     *    both before and after the timed wait, and if the queue is
     *    non-empty, this worker is not the last thread in the pool.
     *
     * @return task, or null if the worker must exit, in which case
     *         workerCount is decremented
     */
    private Runnable getTask() {
        boolean timedOut = false; // Did the last poll() time out?

        for (;;) {
            // 获取状态
            int c = ctl.get();

            // Check if queue empty only if necessary.
            // 如果线程池已经 SHUTDOWN 并且 已经 STOP 或者任务阻塞队列为空了
            if (runStateAtLeast(c, SHUTDOWN)
                && (runStateAtLeast(c, STOP) || workQueue.isEmpty())) {
                // 工作线程数 - 1
                decrementWorkerCount();
                return null;
            }

            // 当前的工作线程数
            int wc = workerCountOf(c);

            // Are workers subject to culling?
            boolean timed = allowCoreThreadTimeOut || wc > corePoolSize;

            // 如果当前的线程数大于 maximumPoolSize
            // 或者 设置了超时超时处理且超时了
            if ((wc > maximumPoolSize || (timed && timedOut))
                && (wc > 1 || workQueue.isEmpty())) {
                // 工作线程 - 1
                if (compareAndDecrementWorkerCount(c))
                    return null;
                continue;
            }

            try {
                // 是否超时获取
                Runnable r = timed ?
                        // 超时等待获取
                    workQueue.poll(keepAliveTime, TimeUnit.NANOSECONDS) :
                        // 阻塞获取
                    workQueue.take();
                // 取出来了，直接返回
                if (r != null)
                    return r;
                // 走到这里说明超时了
                timedOut = true;
            } catch (InterruptedException retry) {
                timedOut = false;
            }
        }
    }

    /**
     * Main worker run loop.  Repeatedly gets tasks from queue and
     * executes them, while coping with a number of issues:
     *
     * 1. We may start out with an initial task, in which case we
     * don't need to get the first one. Otherwise, as long as pool is
     * running, we get tasks from getTask. If it returns null then the
     * worker exits due to changed pool state or configuration
     * parameters.  Other exits result from exception throws in
     * external code, in which case completedAbruptly holds, which
     * usually leads processWorkerExit to replace this thread.
     *
     * 2. Before running any task, the lock is acquired to prevent
     * other pool interrupts while the task is executing, and then we
     * ensure that unless pool is stopping, this thread does not have
     * its interrupt set.
     *
     * 3. Each task run is preceded by a call to beforeExecute, which
     * might throw an exception, in which case we cause thread to die
     * (breaking loop with completedAbruptly true) without processing
     * the task.
     *
     * 4. Assuming beforeExecute completes normally, we run the task,
     * gathering any of its thrown exceptions to send to afterExecute.
     * We separately handle RuntimeException, Error (both of which the
     * specs guarantee that we trap) and arbitrary Throwables.
     * Because we cannot rethrow Throwables within Runnable.run, we
     * wrap them within Errors on the way out (to the thread's
     * UncaughtExceptionHandler).  Any thrown exception also
     * conservatively causes thread to die.
     *
     * 5. After task.run completes, we call afterExecute, which may
     * also throw an exception, which will also cause thread to
     * die. According to JLS Sec 14.20, this exception is the one that
     * will be in effect even if task.run throws.
     *
     * The net effect of the exception mechanics is that afterExecute
     * and the thread's UncaughtExceptionHandler have as accurate
     * information as we can provide about any problems encountered by
     * user code.
     *
     * @param w the worker
     */
    final void runWorker(Worker w) {
        // 当前线程
        Thread wt = Thread.currentThread();
        // work 中保存的任务
        Runnable task = w.firstTask;
        // 取出来之后将去置为 null
        w.firstTask = null;
        // 初始化时 state 为 -1，因此先 unlock 一次。使其允许被中断
        w.unlock(); // allow interrupts
        // 开始先将异常完成标志位 true
        boolean completedAbruptly = true;
        try {
            // 获取到任务，可以是当前 worker 中的任务或者从队列中取出来的任务
            while (task != null || (task = getTask()) != null) {
                // 加锁
                w.lock();
                // If pool is stopping, ensure thread is interrupted;
                // if not, ensure thread is not interrupted.  This
                // requires a recheck in second case to deal with
                // shutdownNow race while clearing interrupt
                // 检查线程池状态
                // 当前线程为 STOP 状态
                if ((runStateAtLeast(ctl.get(), STOP) ||
                        // 返回线程是否被中断且重置中断标志
                     (Thread.interrupted() &&
                             // 状态至少为 STOP
                      runStateAtLeast(ctl.get(), STOP))) &&
                        // 当前线程未被中断
                    !wt.isInterrupted())
                    // 中断当前线程
                    wt.interrupt();
                try {
                    // hook
                    beforeExecute(wt, task);
                    try {
                        // 执行任务
                        task.run();
                        // hook
                        afterExecute(task, null);
                    } catch (Throwable ex) {
                        afterExecute(task, ex);
                        throw ex;
                    }
                } finally {
                    task = null;
                    // 当前 worker 执行完的线程计数 + 1
                    w.completedTasks++;
                    w.unlock();
                }
            }
            // 正常完成
            completedAbruptly = false;
        } finally {
            processWorkerExit(w, completedAbruptly);
        }
    }

    // Public constructors and methods

    /**
     * Creates a new {@code ThreadPoolExecutor} with the given initial
     * parameters, the default thread factory and the default rejected
     * execution handler.
     *
     * <p>It may be more convenient to use one of the {@link Executors}
     * factory methods instead of this general purpose constructor.
     *
     * @param corePoolSize the number of threads to keep in the pool, even
     *        if they are idle, unless {@code allowCoreThreadTimeOut} is set
     * @param maximumPoolSize the maximum number of threads to allow in the
     *        pool
     * @param keepAliveTime when the number of threads is greater than
     *        the core, this is the maximum time that excess idle threads
     *        will wait for new tasks before terminating.
     * @param unit the time unit for the {@code keepAliveTime} argument
     * @param workQueue the queue to use for holding tasks before they are
     *        executed.  This queue will hold only the {@code Runnable}
     *        tasks submitted by the {@code execute} method.
     * @throws IllegalArgumentException if one of the following holds:<br>
     *         {@code corePoolSize < 0}<br>
     *         {@code keepAliveTime < 0}<br>
     *         {@code maximumPoolSize <= 0}<br>
     *         {@code maximumPoolSize < corePoolSize}
     * @throws NullPointerException if {@code workQueue} is null
     */
    public ThreadPoolExecutor(int corePoolSize,
                              int maximumPoolSize,
                              long keepAliveTime,
                              TimeUnit unit,
                              BlockingQueue<Runnable> workQueue) {
        this(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue,
             Executors.defaultThreadFactory(), defaultHandler);
    }

    /**
     * Creates a new {@code ThreadPoolExecutor} with the given initial
     * parameters and {@linkplain ThreadPoolExecutor.AbortPolicy
     * default rejected execution handler}.
     *
     * @param corePoolSize the number of threads to keep in the pool, even
     *        if they are idle, unless {@code allowCoreThreadTimeOut} is set
     * @param maximumPoolSize the maximum number of threads to allow in the
     *        pool
     * @param keepAliveTime when the number of threads is greater than
     *        the core, this is the maximum time that excess idle threads
     *        will wait for new tasks before terminating.
     * @param unit the time unit for the {@code keepAliveTime} argument
     * @param workQueue the queue to use for holding tasks before they are
     *        executed.  This queue will hold only the {@code Runnable}
     *        tasks submitted by the {@code execute} method.
     * @param threadFactory the factory to use when the executor
     *        creates a new thread
     * @throws IllegalArgumentException if one of the following holds:<br>
     *         {@code corePoolSize < 0}<br>
     *         {@code keepAliveTime < 0}<br>
     *         {@code maximumPoolSize <= 0}<br>
     *         {@code maximumPoolSize < corePoolSize}
     * @throws NullPointerException if {@code workQueue}
     *         or {@code threadFactory} is null
     */
    public ThreadPoolExecutor(int corePoolSize,
                              int maximumPoolSize,
                              long keepAliveTime,
                              TimeUnit unit,
                              BlockingQueue<Runnable> workQueue,
                              ThreadFactory threadFactory) {
        this(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue,
             threadFactory, defaultHandler);
    }

    /**
     * Creates a new {@code ThreadPoolExecutor} with the given initial
     * parameters and
     * {@linkplain Executors#defaultThreadFactory default thread factory}.
     *
     * @param corePoolSize the number of threads to keep in the pool, even
     *        if they are idle, unless {@code allowCoreThreadTimeOut} is set
     * @param maximumPoolSize the maximum number of threads to allow in the
     *        pool
     * @param keepAliveTime when the number of threads is greater than
     *        the core, this is the maximum time that excess idle threads
     *        will wait for new tasks before terminating.
     * @param unit the time unit for the {@code keepAliveTime} argument
     * @param workQueue the queue to use for holding tasks before they are
     *        executed.  This queue will hold only the {@code Runnable}
     *        tasks submitted by the {@code execute} method.
     * @param handler the handler to use when execution is blocked
     *        because the thread bounds and queue capacities are reached
     * @throws IllegalArgumentException if one of the following holds:<br>
     *         {@code corePoolSize < 0}<br>
     *         {@code keepAliveTime < 0}<br>
     *         {@code maximumPoolSize <= 0}<br>
     *         {@code maximumPoolSize < corePoolSize}
     * @throws NullPointerException if {@code workQueue}
     *         or {@code handler} is null
     */
    public ThreadPoolExecutor(int corePoolSize,
                              int maximumPoolSize,
                              long keepAliveTime,
                              TimeUnit unit,
                              BlockingQueue<Runnable> workQueue,
                              RejectedExecutionHandler handler) {
        this(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue,
             Executors.defaultThreadFactory(), handler);
    }

    /**
     * Creates a new {@code ThreadPoolExecutor} with the given initial
     * parameters.
     *
     * @param corePoolSize the number of threads to keep in the pool, even
     *        if they are idle, unless {@code allowCoreThreadTimeOut} is set
     * @param maximumPoolSize the maximum number of threads to allow in the
     *        pool
     * @param keepAliveTime when the number of threads is greater than
     *        the core, this is the maximum time that excess idle threads
     *        will wait for new tasks before terminating.
     * @param unit the time unit for the {@code keepAliveTime} argument
     * @param workQueue the queue to use for holding tasks before they are
     *        executed.  This queue will hold only the {@code Runnable}
     *        tasks submitted by the {@code execute} method.
     * @param threadFactory the factory to use when the executor
     *        creates a new thread
     * @param handler the handler to use when execution is blocked
     *        because the thread bounds and queue capacities are reached
     * @throws IllegalArgumentException if one of the following holds:<br>
     *         {@code corePoolSize < 0}<br>
     *         {@code keepAliveTime < 0}<br>
     *         {@code maximumPoolSize <= 0}<br>
     *         {@code maximumPoolSize < corePoolSize}
     * @throws NullPointerException if {@code workQueue}
     *         or {@code threadFactory} or {@code handler} is null
     */
    public ThreadPoolExecutor(int corePoolSize,
                              int maximumPoolSize,
                              long keepAliveTime,
                              TimeUnit unit,
                              BlockingQueue<Runnable> workQueue,
                              ThreadFactory threadFactory,
                              RejectedExecutionHandler handler) {
        // 参数检查
        if (corePoolSize < 0 ||
            maximumPoolSize <= 0 ||
            maximumPoolSize < corePoolSize ||
            keepAliveTime < 0)
            throw new IllegalArgumentException();
        if (workQueue == null || threadFactory == null || handler == null)
            throw new NullPointerException();
        this.corePoolSize = corePoolSize;
        this.maximumPoolSize = maximumPoolSize;
        this.workQueue = workQueue;
        this.keepAliveTime = unit.toNanos(keepAliveTime);
        this.threadFactory = threadFactory;
        this.handler = handler;
    }

    /**
     * 执行给定的 task.该任务可能在已有的线程池中拿出线程来执行，也有可能
     * 创建一个新的线程用于执行。
     * <p>
     * Executes the given task sometime in the future.  The task
     * may execute in a new thread or in an existing pooled thread.
     *
     * If the task cannot be submitted for execution, either because this
     * executor has been shutdown or because its capacity has been reached,
     * the task is handled by the current {@link RejectedExecutionHandler}.
     *
     * @param command the task to execute
     * @throws RejectedExecutionException at discretion of
     *         {@code RejectedExecutionHandler}, if the task
     *         cannot be accepted for execution
     * @throws NullPointerException if {@code command} is null
     */
    public void execute(Runnable command) {
        // 任务不能为 null
        if (command == null)
            throw new NullPointerException();
        /*
         * 三步走
         * Proceed in 3 steps:
         *
         * 1. 如果当前活跃的线程数少于 corePoolSize，则启动新的线程来执行。
         * 并将当前任务作为第一个任务。通过 addWorker 方法会原子地进行检查 runstate
         * 以及 workerCount。
         * 1. If fewer than corePoolSize threads are running, try to
         * start a new thread with the given command as its first
         * task.  The call to addWorker atomically checks runState and
         * workerCount, and so prevents false alarms that would add
         * threads when it shouldn't, by returning false.
         *
         * 2. 如果一个任务成功入队之后，我们仍然需要再次检查是否需要创建一个新的线程。
         * 2. If a task can be successfully queued, then we still need
         * to double-check whether we should have added a thread
         * (because existing ones died since last checking) or that
         * the pool shut down since entry into this method. So we
         * recheck state and if necessary roll back the enqueuing if
         * stopped, or start a new thread if there are none.
         *
         * 3.如果入队失败，那么说明队满了，因此需要再次创建线程来执行任务。
         * 此时线程数应小于 maxPoolSize。如果创建线程失败，只能执行 RejectedExecutionHandler
         * 3. If we cannot queue task, then we try to add a new
         * thread.  If it fails, we know we are shut down or saturated
         * and so reject the task.
         */
        int c = ctl.get();
        // 如果工作的线程数小于 corePoolSize
        if (workerCountOf(c) < corePoolSize) {
            // 创建新的 core thread
            if (addWorker(command, true))
                // 创建成功则直接返回
                return;
            // 创建 core thread 失败，重新获取 ctl
            c = ctl.get();
        }
        // 创建 core thread 失败了，并且当前的工作线程数大于 corePoolSize
        // 检查状态，当前线程池还处于运行状态，并且以非阻塞的方式尝试入队
        if (isRunning(c) && workQueue.offer(command)) {
            // 再次获取 ctl，检查
            int recheck = ctl.get();
            // 如果不是运行状态了
            if (!isRunning(recheck) && remove(command))
                // 拒绝该任务，执行拒绝策略
                reject(command);
            // 这个条件说明 1. 要么线程池处于 Running 状态
            // 或者 remove 失败了，失败的原因就是任务从队列中被获取执行了
            // 如果当前的工作线程数为 0 了，则创建非 core 线程，传入的任务为 null
            else if (workerCountOf(recheck) == 0)
                addWorker(null, false);
        }
        // 线程处于非运行状态或者入队失败
        // 再次尝试创建线程，这次创建线程根据 maxPoolSize 来创建，因此传入 false
        else if (!addWorker(command, false))
            // 创建线程失败，则直接执行拒绝策略
            reject(command);
    }

    /**
     * 关闭线程池
     * <p>
     * Initiates an orderly shutdown in which previously submitted
     * tasks are executed, but no new tasks will be accepted.
     * Invocation has no additional effect if already shut down.
     *
     * <p>This method does not wait for previously submitted tasks to
     * complete execution.  Use {@link #awaitTermination awaitTermination}
     * to do that.
     *
     * @throws SecurityException {@inheritDoc}
     */
    public void shutdown() {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            checkShutdownAccess();
            // 将状态置为 SHUTDOWN
            advanceRunState(SHUTDOWN);
            // 中断所有线程
            interruptIdleWorkers();
            // 回调
            onShutdown(); // hook for ScheduledThreadPoolExecutor
        } finally {
            mainLock.unlock();
        }
        // 尝试终止
        tryTerminate();
    }

    /**
     * Attempts to stop all actively executing tasks, halts the
     * processing of waiting tasks, and returns a list of the tasks
     * that were awaiting execution. These tasks are drained (removed)
     * from the task queue upon return from this method.
     *
     * <p>This method does not wait for actively executing tasks to
     * terminate.  Use {@link #awaitTermination awaitTermination} to
     * do that.
     *
     * <p>There are no guarantees beyond best-effort attempts to stop
     * processing actively executing tasks.  This implementation
     * interrupts tasks via {@link Thread#interrupt}; any task that
     * fails to respond to interrupts may never terminate.
     *
     * @throws SecurityException {@inheritDoc}
     */
    public List<Runnable> shutdownNow() {
        List<Runnable> tasks;
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            checkShutdownAccess();
            // 将状态转为 STOP
            advanceRunState(STOP);
            // 中断 workers
            interruptWorkers();
            // 清空阻塞队列并将任务保存到 tasks 中
            tasks = drainQueue();
        } finally {
            mainLock.unlock();
        }
        // 尝试终止线程池
        tryTerminate();
        return tasks;
    }

    public boolean isShutdown() {
        return runStateAtLeast(ctl.get(), SHUTDOWN);
    }

    /** Used by ScheduledThreadPoolExecutor. */
    boolean isStopped() {
        return runStateAtLeast(ctl.get(), STOP);
    }

    /**
     * 返回是否在终止状态
     * <p>
     * Returns true if this executor is in the process of terminating
     * after {@link #shutdown} or {@link #shutdownNow} but has not
     * completely terminated.  This method may be useful for
     * debugging. A return of {@code true} reported a sufficient
     * period after shutdown may indicate that submitted tasks have
     * ignored or suppressed interruption, causing this executor not
     * to properly terminate.
     *
     * @return {@code true} if terminating but not yet terminated
     */
    public boolean isTerminating() {
        int c = ctl.get();
        // 大于等于 SHUTDOWN 状态，小于 TERMINATED 状态
        // 也就是处于 SHUTDOWN 或者 STOP 或者 TIDYING 状态
        return runStateAtLeast(c, SHUTDOWN) && runStateLessThan(c, TERMINATED);
    }

    /**
     * 返回线程池是否被终止
     */
    public boolean isTerminated() {
        return runStateAtLeast(ctl.get(), TERMINATED);
    }

    /**
     * 超时等待线程池终止
     */
    public boolean awaitTermination(long timeout, TimeUnit unit)
        throws InterruptedException {
        long nanos = unit.toNanos(timeout);
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            // 在不为终止状态上自旋(其实不会自旋，而是会进入阻塞)
            while (runStateLessThan(ctl.get(), TERMINATED)) {
                // 超时了还未终止，则返回 false
                if (nanos <= 0L)
                    return false;
                // 超时阻塞
                nanos = termination.awaitNanos(nanos);
            }
            return true;
        } finally {
            mainLock.unlock();
        }
    }

    // Override without "throws Throwable" for compatibility with subclasses
    // whose finalize method invokes super.finalize() (as is recommended).
    // Before JDK 11, finalize() had a non-empty method body.

    /**
     * @implNote Previous versions of this class had a finalize method
     * that shut down this executor, but in this version, finalize
     * does nothing.
     */
    @Deprecated(since="9")
    protected void finalize() {}

    /**
     * Sets the thread factory used to create new threads.
     *
     * @param threadFactory the new thread factory
     * @throws NullPointerException if threadFactory is null
     * @see #getThreadFactory
     */
    public void setThreadFactory(ThreadFactory threadFactory) {
        if (threadFactory == null)
            throw new NullPointerException();
        this.threadFactory = threadFactory;
    }

    /**
     * Returns the thread factory used to create new threads.
     *
     * @return the current thread factory
     * @see #setThreadFactory(ThreadFactory)
     */
    public ThreadFactory getThreadFactory() {
        return threadFactory;
    }

    /**
     * Sets a new handler for unexecutable tasks.
     *
     * @param handler the new handler
     * @throws NullPointerException if handler is null
     * @see #getRejectedExecutionHandler
     */
    public void setRejectedExecutionHandler(RejectedExecutionHandler handler) {
        if (handler == null)
            throw new NullPointerException();
        this.handler = handler;
    }

    /**
     * Returns the current handler for unexecutable tasks.
     *
     * @return the current handler
     * @see #setRejectedExecutionHandler(RejectedExecutionHandler)
     */
    public RejectedExecutionHandler getRejectedExecutionHandler() {
        return handler;
    }

    /**
     * Sets the core number of threads.  This overrides any value set
     * in the constructor.  If the new value is smaller than the
     * current value, excess existing threads will be terminated when
     * they next become idle.  If larger, new threads will, if needed,
     * be started to execute any queued tasks.
     *
     * @param corePoolSize the new core size
     * @throws IllegalArgumentException if {@code corePoolSize < 0}
     *         or {@code corePoolSize} is greater than the {@linkplain
     *         #getMaximumPoolSize() maximum pool size}
     * @see #getCorePoolSize
     */
    public void setCorePoolSize(int corePoolSize) {
        if (corePoolSize < 0 || maximumPoolSize < corePoolSize)
            throw new IllegalArgumentException();
        int delta = corePoolSize - this.corePoolSize;
        this.corePoolSize = corePoolSize;
        // 当前的线程数大于 corePoolSize 需要处理 idle 线程
        if (workerCountOf(ctl.get()) > corePoolSize)
            interruptIdleWorkers();
        else if (delta > 0) {
            // We don't really know how many new threads are "needed".
            // As a heuristic, prestart enough new workers (up to new
            // core size) to handle the current number of tasks in
            // queue, but stop if queue becomes empty while doing so.
            int k = Math.min(delta, workQueue.size());
            while (k-- > 0 && addWorker(null, true)) {
                if (workQueue.isEmpty())
                    break;
            }
        }
    }

    /**
     * Returns the core number of threads.
     *
     * @return the core number of threads
     * @see #setCorePoolSize
     */
    public int getCorePoolSize() {
        return corePoolSize;
    }

    /**
     * 预先创建并启动线程，一次调用启动一个线程。
     * <p>
     * Starts a core thread, causing it to idly wait for work. This
     * overrides the default policy of starting core threads only when
     * new tasks are executed. This method will return {@code false}
     * if all core threads have already been started.
     *
     * @return {@code true} if a thread was started
     */
    public boolean prestartCoreThread() {
        return workerCountOf(ctl.get()) < corePoolSize &&
            addWorker(null, true);
    }

    /**
     * 确保至少有一个线程被启动。即使当 corePoolSize 等于 0 的时候。
     * <p>
     * Same as prestartCoreThread except arranges that at least one
     * thread is started even if corePoolSize is 0.
     */
    void ensurePrestart() {
        int wc = workerCountOf(ctl.get());
        if (wc < corePoolSize)
            addWorker(null, true);
        else if (wc == 0)
            addWorker(null, false);
    }

    /**
     * 预先创建 corePoolSize 个线程并启动
     * <p>
     * Starts all core threads, causing them to idly wait for work. This
     * overrides the default policy of starting core threads only when
     * new tasks are executed.
     *
     * @return the number of threads started
     */
    public int prestartAllCoreThreads() {
        int n = 0;
        while (addWorker(null, true))
            ++n;
        return n;
    }

    /**
     * Returns true if this pool allows core threads to time out and
     * terminate if no tasks arrive within the keepAlive time, being
     * replaced if needed when new tasks arrive. When true, the same
     * keep-alive policy applying to non-core threads applies also to
     * core threads. When false (the default), core threads are never
     * terminated due to lack of incoming tasks.
     *
     * @return {@code true} if core threads are allowed to time out,
     *         else {@code false}
     *
     * @since 1.6
     */
    public boolean allowsCoreThreadTimeOut() {
        return allowCoreThreadTimeOut;
    }

    /**
     * 设置 core thread 是否能够 timeout
     * <p>Sets the policy governing whether core threads may time out and
     * terminate if no tasks arrive within the keep-alive time, being
     * replaced if needed when new tasks arrive. When false, core
     * threads are never terminated due to lack of incoming
     * tasks. When true, the same keep-alive policy applying to
     * non-core threads applies also to core threads. To avoid
     * continual thread replacement, the keep-alive time must be
     * greater than zero when setting {@code true}. This method
     * should in general be called before the pool is actively used.
     *
     * @param value {@code true} if should time out, else {@code false}
     * @throws IllegalArgumentException if value is {@code true}
     *         and the current keep-alive time is not greater than zero
     *
     * @since 1.6
     */
    public void allowCoreThreadTimeOut(boolean value) {
        if (value && keepAliveTime <= 0)
            throw new IllegalArgumentException("Core threads must have nonzero keep alive times");
        if (value != allowCoreThreadTimeOut) {
            allowCoreThreadTimeOut = value;
            // 如果从 false -> true 则触发一次 interruptIdleWorkers
            if (value)
                // 中断线程
                interruptIdleWorkers();
        }
    }

    /**
     * Sets the maximum allowed number of threads. This overrides any
     * value set in the constructor. If the new value is smaller than
     * the current value, excess existing threads will be
     * terminated when they next become idle.
     *
     * @param maximumPoolSize the new maximum
     * @throws IllegalArgumentException if the new maximum is
     *         less than or equal to zero, or
     *         less than the {@linkplain #getCorePoolSize core pool size}
     * @see #getMaximumPoolSize
     */
    public void setMaximumPoolSize(int maximumPoolSize) {
        if (maximumPoolSize <= 0 || maximumPoolSize < corePoolSize)
            throw new IllegalArgumentException();
        this.maximumPoolSize = maximumPoolSize;
        if (workerCountOf(ctl.get()) > maximumPoolSize)
            interruptIdleWorkers();
    }

    /**
     * Returns the maximum allowed number of threads.
     *
     * @return the maximum allowed number of threads
     * @see #setMaximumPoolSize
     */
    public int getMaximumPoolSize() {
        return maximumPoolSize;
    }

    /**
     * 设置 keepAliveTime
     * <p>
     * Sets the thread keep-alive time, which is the amount of time
     * that threads may remain idle before being terminated.
     * Threads that wait this amount of time without processing a
     * task will be terminated if there are more than the core
     * number of threads currently in the pool, or if this pool
     * {@linkplain #allowsCoreThreadTimeOut() allows core thread timeout}.
     * This overrides any value set in the constructor.
     *
     * @param time the time to wait.  A time value of zero will cause
     *        excess threads to terminate immediately after executing tasks.
     * @param unit the time unit of the {@code time} argument
     * @throws IllegalArgumentException if {@code time} less than zero or
     *         if {@code time} is zero and {@code allowsCoreThreadTimeOut}
     * @see #getKeepAliveTime(TimeUnit)
     */
    public void setKeepAliveTime(long time, TimeUnit unit) {
        if (time < 0)
            throw new IllegalArgumentException();
        if (time == 0 && allowsCoreThreadTimeOut())
            throw new IllegalArgumentException("Core threads must have nonzero keep alive times");
        long keepAliveTime = unit.toNanos(time);
        long delta = keepAliveTime - this.keepAliveTime;
        this.keepAliveTime = keepAliveTime;
        // 如果 keepAliveTime 变少
        if (delta < 0)
            // 需要中断 Idle 线程，可能会回收这些线程
            interruptIdleWorkers();
    }

    /**
     * 返回 keepAliveTime (NANOSECONDS)
     * <p>
     * Returns the thread keep-alive time, which is the amount of time
     * that threads may remain idle before being terminated.
     * Threads that wait this amount of time without processing a
     * task will be terminated if there are more than the core
     * number of threads currently in the pool, or if this pool
     * {@linkplain #allowsCoreThreadTimeOut() allows core thread timeout}.
     *
     * @param unit the desired time unit of the result
     * @return the time limit
     * @see #setKeepAliveTime(long, TimeUnit)
     */
    public long getKeepAliveTime(TimeUnit unit) {
        return unit.convert(keepAliveTime, TimeUnit.NANOSECONDS);
    }

    /* User-level queue utilities */

    /**
     * 返回当前线程池的阻塞队列
     * <p>
     * Returns the task queue used by this executor. Access to the
     * task queue is intended primarily for debugging and monitoring.
     * This queue may be in active use.  Retrieving the task queue
     * does not prevent queued tasks from executing.
     *
     * @return the task queue
     */
    public BlockingQueue<Runnable> getQueue() {
        return workQueue;
    }

    /**
     * Removes this task from the executor's internal queue if it is
     * present, thus causing it not to be run if it has not already
     * started.
     *
     * <p>This method may be useful as one part of a cancellation
     * scheme.  It may fail to remove tasks that have been converted
     * into other forms before being placed on the internal queue.
     * For example, a task entered using {@code submit} might be
     * converted into a form that maintains {@code Future} status.
     * However, in such cases, method {@link #purge} may be used to
     * remove those Futures that have been cancelled.
     *
     * @param task the task to remove
     * @return {@code true} if the task was removed
     */
    public boolean remove(Runnable task) {
        // 移除队列中的 task 任务
        boolean removed = workQueue.remove(task);
        // 尝试终止线程池
        tryTerminate(); // In case SHUTDOWN and now empty
        // 返回移除任务是否成功
        return removed;
    }

    /**
     * 清理阻塞队列中所有的被 cancel 的 Future 任务
     *<p>
     * Tries to remove from the work queue all {@link Future}
     * tasks that have been cancelled. This method can be useful as a
     * storage reclamation operation, that has no other impact on
     * functionality. Cancelled tasks are never executed, but may
     * accumulate in work queues until worker threads can actively
     * remove them. Invoking this method instead tries to remove them now.
     * However, this method may fail to remove tasks in
     * the presence of interference by other threads.
     */
    public void purge() {
        final BlockingQueue<Runnable> q = workQueue;
        try {
            Iterator<Runnable> it = q.iterator();
            while (it.hasNext()) {
                Runnable r = it.next();
                // future 任务且该任务被取消
                if (r instanceof Future<?> && ((Future<?>)r).isCancelled())
                    // 从队列中移除
                    it.remove();
            }
        } catch (ConcurrentModificationException fallThrough) {
            // Take slow path if we encounter interference during traversal.
            // Make copy for traversal and call remove for cancelled entries.
            // The slow path is more likely to be O(N*N).
            for (Object r : q.toArray())
                if (r instanceof Future<?> && ((Future<?>)r).isCancelled())
                    q.remove(r);
        }
        // 尝试终止线程池
        tryTerminate(); // In case SHUTDOWN and now empty
    }

    /* Statistics */

    /**
     * 返回池中的线程数
     * <p>
     * Returns the current number of threads in the pool.
     *
     * @return the number of threads
     */
    public int getPoolSize() {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            // Remove rare and surprising possibility of
            // isTerminated() && getPoolSize() > 0
            return runStateAtLeast(ctl.get(), TIDYING) ? 0
                : workers.size();
        } finally {
            mainLock.unlock();
        }
    }

    /**
     * 返回当前在执行任务的线程数（估计值）
     * <p>
     * Returns the approximate number of threads that are actively
     * executing tasks.
     *
     * @return the number of threads
     */
    public int getActiveCount() {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            int n = 0;
            for (Worker w : workers)
                if (w.isLocked())
                    ++n;
            return n;
        } finally {
            mainLock.unlock();
        }
    }

    /**
     * 返回当前线程池曾经达到过的最大的线程数
     * <p>
     * Returns the largest number of threads that have ever
     * simultaneously been in the pool.
     *
     * @return the number of threads
     */
    public int getLargestPoolSize() {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            return largestPoolSize;
        } finally {
            mainLock.unlock();
        }
    }

    /**
     * 返回所有被调度过的任务数(完成+在执行中)
     * <p>
     * Returns the approximate total number of tasks that have ever been
     * scheduled for execution. Because the states of tasks and
     * threads may change dynamically during computation, the returned
     * value is only an approximation.
     *
     * @return the number of tasks
     */
    public long getTaskCount() {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            long n = completedTaskCount;
            for (Worker w : workers) {
                n += w.completedTasks;
                if (w.isLocked())
                    ++n;
            }
            return n + workQueue.size();
        } finally {
            mainLock.unlock();
        }
    }

    /**
     * 返回所有完成的任务数
     * <p>
     * Returns the approximate total number of tasks that have
     * completed execution. Because the states of tasks and threads
     * may change dynamically during computation, the returned value
     * is only an approximation, but one that does not ever decrease
     * across successive calls.
     *
     * @return the number of tasks
     */
    public long getCompletedTaskCount() {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            long n = completedTaskCount;
            for (Worker w : workers)
                n += w.completedTasks;
            return n;
        } finally {
            mainLock.unlock();
        }
    }

    /**
     * Returns a string identifying this pool, as well as its state,
     * including indications of run state and estimated worker and
     * task counts.
     *
     * @return a string identifying this pool, as well as its state
     */
    public String toString() {
        long ncompleted;
        int nworkers, nactive;
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            ncompleted = completedTaskCount;
            nactive = 0;
            nworkers = workers.size();
            for (Worker w : workers) {
                ncompleted += w.completedTasks;
                if (w.isLocked())
                    ++nactive;
            }
        } finally {
            mainLock.unlock();
        }
        int c = ctl.get();
        String runState =
            isRunning(c) ? "Running" :
            runStateAtLeast(c, TERMINATED) ? "Terminated" :
            "Shutting down";
        return super.toString() +
            "[" + runState +
            ", pool size = " + nworkers +
            ", active threads = " + nactive +
            ", queued tasks = " + workQueue.size() +
            ", completed tasks = " + ncompleted +
            "]";
    }

    /* Extension hooks */

    /**
     * 执行前的 hook
     * <p>
     * Method invoked prior to executing the given Runnable in the
     * given thread.  This method is invoked by thread {@code t} that
     * will execute task {@code r}, and may be used to re-initialize
     * ThreadLocals, or to perform logging.
     *
     * <p>This implementation does nothing, but may be customized in
     * subclasses. Note: To properly nest multiple overridings, subclasses
     * should generally invoke {@code super.beforeExecute} at the end of
     * this method.
     *
     * @param t the thread that will run task {@code r}
     * @param r the task that will be executed
     */
    protected void beforeExecute(Thread t, Runnable r) { }

    /**
     * 执行后的 hook
     * <p>
     * Method invoked upon completion of execution of the given Runnable.
     * This method is invoked by the thread that executed the task. If
     * non-null, the Throwable is the uncaught {@code RuntimeException}
     * or {@code Error} that caused execution to terminate abruptly.
     *
     * <p>This implementation does nothing, but may be customized in
     * subclasses. Note: To properly nest multiple overridings, subclasses
     * should generally invoke {@code super.afterExecute} at the
     * beginning of this method.
     *
     * <p><b>Note:</b> When actions are enclosed in tasks (such as
     * {@link FutureTask}) either explicitly or via methods such as
     * {@code submit}, these task objects catch and maintain
     * computational exceptions, and so they do not cause abrupt
     * termination, and the internal exceptions are <em>not</em>
     * passed to this method. If you would like to trap both kinds of
     * failures in this method, you can further probe for such cases,
     * as in this sample subclass that prints either the direct cause
     * or the underlying exception if a task has been aborted:
     *
     * <pre> {@code
     * class ExtendedExecutor extends ThreadPoolExecutor {
     *   // ...
     *   protected void afterExecute(Runnable r, Throwable t) {
     *     super.afterExecute(r, t);
     *     if (t == null
     *         && r instanceof Future<?>
     *         && ((Future<?>)r).isDone()) {
     *       try {
     *         Object result = ((Future<?>) r).get();
     *       } catch (CancellationException ce) {
     *         t = ce;
     *       } catch (ExecutionException ee) {
     *         t = ee.getCause();
     *       } catch (InterruptedException ie) {
     *         // ignore/reset
     *         Thread.currentThread().interrupt();
     *       }
     *     }
     *     if (t != null)
     *       System.out.println(t);
     *   }
     * }}</pre>
     *
     * @param r the runnable that has completed
     * @param t the exception that caused termination, or null if
     * execution completed normally
     */
    protected void afterExecute(Runnable r, Throwable t) { }

    /**
     * 线程池 terminate 的 hook
     * <p>
     * Method invoked when the Executor has terminated.  Default
     * implementation does nothing. Note: To properly nest multiple
     * overridings, subclasses should generally invoke
     * {@code super.terminated} within this method.
     */
    protected void terminated() { }

    /* Predefined RejectedExecutionHandlers */

    /**
     * 直接在调用线程上执行任务
     * <p>
     * A handler for rejected tasks that runs the rejected task
     * directly in the calling thread of the {@code execute} method,
     * unless the executor has been shut down, in which case the task
     * is discarded.
     */
    public static class CallerRunsPolicy implements RejectedExecutionHandler {
        /**
         * Creates a {@code CallerRunsPolicy}.
         */
        public CallerRunsPolicy() { }

        /**
         * Executes task r in the caller's thread, unless the executor
         * has been shut down, in which case the task is discarded.
         *
         * @param r the runnable task requested to be executed
         * @param e the executor attempting to execute this task
         */
        public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
            // 前提是线程池未关闭
            if (!e.isShutdown()) {
                // 调用 run 方法直接执行任务
                r.run();
            }
        }
    }

    /**
     * 终止策略，直接抛异常。
     * <p>
     * A handler for rejected tasks that throws a
     * {@link RejectedExecutionException}.
     *
     * This is the default handler for {@link ThreadPoolExecutor} and
     * {@link ScheduledThreadPoolExecutor}.
     */
    public static class AbortPolicy implements RejectedExecutionHandler {
        /**
         * Creates an {@code AbortPolicy}.
         */
        public AbortPolicy() { }

        /**
         * Always throws RejectedExecutionException.
         *
         * @param r the runnable task requested to be executed
         * @param e the executor attempting to execute this task
         * @throws RejectedExecutionException always
         */
        public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
            // 直接抛出 RejectedExecutionException 异常
            throw new RejectedExecutionException("Task " + r.toString() +
                                                 " rejected from " +
                                                 e.toString());
        }
    }

    /**
     * 丢弃策略，悄悄的丢弃任务，说明也不做。
     * <p>
     * A handler for rejected tasks that silently discards the
     * rejected task.
     */
    public static class DiscardPolicy implements RejectedExecutionHandler {
        /**
         * Creates a {@code DiscardPolicy}.
         */
        public DiscardPolicy() { }

        /**
         * Does nothing, which has the effect of discarding task r.
         *
         * @param r the runnable task requested to be executed
         * @param e the executor attempting to execute this task
         */
        public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
            // do nothing
        }
    }

    /**
     * 丢弃等待时间最久的任务
     * 也就是丢弃队头任务
     * <p>
     * A handler for rejected tasks that discards the oldest unhandled
     * request and then retries {@code execute}, unless the executor
     * is shut down, in which case the task is discarded.
     */
    public static class DiscardOldestPolicy implements RejectedExecutionHandler {
        /**
         * Creates a {@code DiscardOldestPolicy} for the given executor.
         */
        public DiscardOldestPolicy() { }

        /**
         * Obtains and ignores the next task that the executor
         * would otherwise execute, if one is immediately available,
         * and then retries execution of task r, unless the executor
         * is shut down, in which case task r is instead discarded.
         *
         * @param r the runnable task requested to be executed
         * @param e the executor attempting to execute this task
         */
        public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
            if (!e.isShutdown()) {
                // 丢弃队头任务
                e.getQueue().poll();
                // 尝试执行当前任务
                e.execute(r);
            }
        }
    }
}
