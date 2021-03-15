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

import static java.lang.ref.Reference.reachabilityFence;
import java.security.AccessControlContext;
import java.security.AccessControlException;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import sun.security.util.SecurityConstants;

/**
 * 类似于 Arrays, Collections, Executors 是线程池的工具类
 * Executor 的工厂方法工具类。
 * <p>
 * Factory and utility methods for {@link Executor}, {@link
 * ExecutorService}, {@link ScheduledExecutorService}, {@link
 * ThreadFactory}, and {@link Callable} classes defined in this
 * package. This class supports the following kinds of methods:
 * <p>提供了以下方法:
 *
 * <ul>
 *   <li>Methods that create and return an {@link ExecutorService}
 *       set up with commonly useful configuration settings.
 *   <li>创建并返回一个通用配置的 ExecutorService
 *   <li>Methods that create and return a {@link ScheduledExecutorService}
 *       set up with commonly useful configuration settings.
 *   <li>创建并返回一个通用配置的 ScheduledExecutorService
 *   <li>Methods that create and return a "wrapped" ExecutorService, that
 *       disables reconfiguration by making implementation-specific methods
 *       inaccessible.
 *   <li>创建并返回一个包装的 ExecutorService，
 *   通过使特定方法不可访问使得该 ExecutorService 不能够重新配置
 *   <li>Methods that create and return a {@link ThreadFactory}
 *       that sets newly created threads to a known state.
 *   <li>创建并返回一个 ThreadFactory，该 ThreadFactory
 *   可以设置新创建的线程为指定的状态
 *   <li>Methods that create and return a {@link Callable}
 *       out of other closure-like forms, so they can be used
 *       in execution methods requiring {@code Callable}.
 *   <li>创建并返回一个 Callable
 * </ul>
 *
 * @since 1.5
 * @author Doug Lea
 */
public class Executors {

    /**
     * 创建一个固定数量线程的线程池。<p>
     * 在任意时刻，最多只有 nThreads 个线程活跃能够处理任务。<p>
     * 如果当线程池内的所有 nThreads 个线程都在活跃，有新的任务来时，将会入队。<p>
     * 使用一个无界的队列 LinkedBlockingQueue，说该队列无界的原因是因为未指定其容量，所以默认为整型的最大值<p>
     *
     * Creates a thread pool that reuses a fixed number of threads
     * operating off a shared unbounded queue.  At any point, at most
     * {@code nThreads} threads will be active processing tasks.
     * If additional tasks are submitted when all threads are active,
     * they will wait in the queue until a thread is available.
     * If any thread terminates due to a failure during execution
     * prior to shutdown, a new one will take its place if needed to
     * execute subsequent tasks.  The threads in the pool will exist
     * until it is explicitly {@link ExecutorService#shutdown shutdown}.
     *
     * @param nThreads the number of threads in the pool
     * @return the newly created thread pool
     * @throws IllegalArgumentException if {@code nThreads <= 0}
     */
    public static ExecutorService newFixedThreadPool(int nThreads) {
        return new ThreadPoolExecutor(nThreads, nThreads,
                                      0L, TimeUnit.MILLISECONDS,
                                      new LinkedBlockingQueue<Runnable>());
    }

    /**
     * TODO 创建了一个 ForkJoinPool，ForkJoinPool 的源码还没看明白....
     * <p>
     * Creates a thread pool that maintains enough threads to support
     * the given parallelism level, and may use multiple queues to
     * reduce contention. The parallelism level corresponds to the
     * maximum number of threads actively engaged in, or available to
     * engage in, task processing. The actual number of threads may
     * grow and shrink dynamically. A work-stealing pool makes no
     * guarantees about the order in which submitted tasks are
     * executed.
     *
     * @param parallelism the targeted parallelism level
     * @return the newly created thread pool
     * @throws IllegalArgumentException if {@code parallelism <= 0}
     * @since 1.8
     */
    public static ExecutorService newWorkStealingPool(int parallelism) {
        return new ForkJoinPool
            (parallelism,
             ForkJoinPool.defaultForkJoinWorkerThreadFactory,
             null, true);
    }

    /**
     * TODO 创建了一个 ForkJoinPool，ForkJoinPool 的源码还没看明白....
     * <p>
     * Creates a work-stealing thread pool using the number of
     * {@linkplain Runtime#availableProcessors available processors}
     * as its target parallelism level.
     *
     * @return the newly created thread pool
     * @see #newWorkStealingPool(int)
     * @since 1.8
     */
    public static ExecutorService newWorkStealingPool() {
        return new ForkJoinPool
            (Runtime.getRuntime().availableProcessors(),
             ForkJoinPool.defaultForkJoinWorkerThreadFactory,
             null, true);
    }

    /**
     * 支持指定的 ThreadFactory。
     * 与 {@link Executors#newFixedThreadPool(int)} 类似
     *
     * Creates a thread pool that reuses a fixed number of threads
     * operating off a shared unbounded queue, using the provided
     * ThreadFactory to create new threads when needed.  At any point,
     * at most {@code nThreads} threads will be active processing
     * tasks.  If additional tasks are submitted when all threads are
     * active, they will wait in the queue until a thread is
     * available.  If any thread terminates due to a failure during
     * execution prior to shutdown, a new one will take its place if
     * needed to execute subsequent tasks.  The threads in the pool will
     * exist until it is explicitly {@link ExecutorService#shutdown
     * shutdown}.
     *
     * @param nThreads the number of threads in the pool
     * @param threadFactory the factory to use when creating new threads
     * @return the newly created thread pool
     * @throws NullPointerException if threadFactory is null
     * @throws IllegalArgumentException if {@code nThreads <= 0}
     */
    public static ExecutorService newFixedThreadPool(int nThreads, ThreadFactory threadFactory) {
        return new ThreadPoolExecutor(nThreads, nThreads,
                                      0L, TimeUnit.MILLISECONDS,
                                      new LinkedBlockingQueue<Runnable>(),
                                      threadFactory);
    }

    /**
     * Creates an Executor that uses a single worker thread operating
     * off an unbounded queue. (Note however that if this single
     * thread terminates due to a failure during execution prior to
     * shutdown, a new one will take its place if needed to execute
     * subsequent tasks.)  Tasks are guaranteed to execute
     * sequentially, and no more than one task will be active at any
     * given time. Unlike the otherwise equivalent
     * {@code newFixedThreadPool(1)} the returned executor is
     * guaranteed not to be reconfigurable to use additional threads.
     *
     * @return the newly created single-threaded Executor
     */
    public static ExecutorService newSingleThreadExecutor() {
        // 封装进了 FinalizableDelegatedExecutorService
        return new FinalizableDelegatedExecutorService
                // 只有一个工作线程的线程池
            (new ThreadPoolExecutor(1, 1,
                                    0L, TimeUnit.MILLISECONDS,
                                    // 使用无界的 LinkedBlockingQueue
                                    new LinkedBlockingQueue<Runnable>()));
    }

    /**
     * 支持指定 ThreadFactory
     * 与 {@link Executors#newSingleThreadExecutor()} 类似
     * <p>
     * Creates an Executor that uses a single worker thread operating
     * off an unbounded queue, and uses the provided ThreadFactory to
     * create a new thread when needed. Unlike the otherwise
     * equivalent {@code newFixedThreadPool(1, threadFactory)} the
     * returned executor is guaranteed not to be reconfigurable to use
     * additional threads.
     *
     * @param threadFactory the factory to use when creating new threads
     * @return the newly created single-threaded Executor
     * @throws NullPointerException if threadFactory is null
     */
    public static ExecutorService newSingleThreadExecutor(ThreadFactory threadFactory) {
        // 同样也是封装成 FinalizableDelegatedExecutorService
        return new FinalizableDelegatedExecutorService
            (new ThreadPoolExecutor(1, 1,
                                    0L, TimeUnit.MILLISECONDS,
                                    new LinkedBlockingQueue<Runnable>(),
                                    threadFactory));
    }

    /**
     * 按需创建线程的线程池。该种类型的线程池比较适合于执行 很多短生命周期的异步任务。
     * CorePoolSize 为 0，当线程池中的 idle 线程超过 60 s 后，会被 terminate。
     * 阻塞队列使用的是 SynchronousQueue 说明队列中不暂存任务，当任务来了则使用
     * 还存活的 idle 线程或者创建新的线程来处理。
     * <p>
     * Creates a thread pool that creates new threads as needed, but
     * will reuse previously constructed threads when they are
     * available.  These pools will typically improve the performance
     * of programs that execute many short-lived asynchronous tasks.
     * Calls to {@code execute} will reuse previously constructed
     * threads if available. If no existing thread is available, a new
     * thread will be created and added to the pool. Threads that have
     * not been used for sixty seconds are terminated and removed from
     * the cache. Thus, a pool that remains idle for long enough will
     * not consume any resources. Note that pools with similar
     * properties but different details (for example, timeout parameters)
     * may be created using {@link ThreadPoolExecutor} constructors.
     *
     * @return the newly created thread pool
     */
    public static ExecutorService newCachedThreadPool() {
        return new ThreadPoolExecutor(0, Integer.MAX_VALUE,
                                      60L, TimeUnit.SECONDS,
                                      new SynchronousQueue<Runnable>());
    }

    /**
     * 指定了 ThreadFactory，与 {@link Executors#newCachedThreadPool()} 类似
     * <p>
     * Creates a thread pool that creates new threads as needed, but
     * will reuse previously constructed threads when they are
     * available, and uses the provided
     * ThreadFactory to create new threads when needed.
     *
     * @param threadFactory the factory to use when creating new threads
     * @return the newly created thread pool
     * @throws NullPointerException if threadFactory is null
     */
    public static ExecutorService newCachedThreadPool(ThreadFactory threadFactory) {
        return new ThreadPoolExecutor(0, Integer.MAX_VALUE,
                                      60L, TimeUnit.SECONDS,
                                      new SynchronousQueue<Runnable>(),
                                      threadFactory);
    }

    /**
     * 创建一个只有一个核心线程的线程池。可以在指定延时之后执行任务或者
     * 周期性的执行任务。
     * <p>
     * Creates a single-threaded executor that can schedule commands
     * to run after a given delay, or to execute periodically.
     * (Note however that if this single
     * thread terminates due to a failure during execution prior to
     * shutdown, a new one will take its place if needed to execute
     * subsequent tasks.)  Tasks are guaranteed to execute
     * sequentially, and no more than one task will be active at any
     * given time. Unlike the otherwise equivalent
     * {@code newScheduledThreadPool(1)} the returned executor is
     * guaranteed not to be reconfigurable to use additional threads.
     *
     * @return the newly created scheduled executor
     */
    public static ScheduledExecutorService newSingleThreadScheduledExecutor() {
        // 封装成 DelegatedScheduledExecutorService
        return new DelegatedScheduledExecutorService
                // 使用 DelayedWorkQueue
                // corePoolSize = 1
                // maxPoolSize = Integer.MAX_VALUE
                // keepAliveTime = DEFAULT_KEEPALIVE_MILLIS
                // TimeUnit = MILLISECONDS
            (new ScheduledThreadPoolExecutor(1));
    }

    /**
     * 支持指定 ThreadFactory，
     * 与 {@link Executors#newSingleThreadScheduledExecutor()} 类似
     * <p>
     * Creates a single-threaded executor that can schedule commands
     * to run after a given delay, or to execute periodically.  (Note
     * however that if this single thread terminates due to a failure
     * during execution prior to shutdown, a new one will take its
     * place if needed to execute subsequent tasks.)  Tasks are
     * guaranteed to execute sequentially, and no more than one task
     * will be active at any given time. Unlike the otherwise
     * equivalent {@code newScheduledThreadPool(1, threadFactory)}
     * the returned executor is guaranteed not to be reconfigurable to
     * use additional threads.
     *
     * @param threadFactory the factory to use when creating new threads
     * @return the newly created scheduled executor
     * @throws NullPointerException if threadFactory is null
     */
    public static ScheduledExecutorService newSingleThreadScheduledExecutor(ThreadFactory threadFactory) {
        return new DelegatedScheduledExecutorService
            (new ScheduledThreadPoolExecutor(1, threadFactory));
    }

    /**
     * 支持指定 corePoolSize，
     * 与 {@link Executors#newSingleThreadScheduledExecutor()} 类似
     * <p>
     * Creates a thread pool that can schedule commands to run after a
     * given delay, or to execute periodically.
     * @param corePoolSize the number of threads to keep in the pool,
     * even if they are idle
     * @return the newly created scheduled thread pool
     * @throws IllegalArgumentException if {@code corePoolSize < 0}
     */
    public static ScheduledExecutorService newScheduledThreadPool(int corePoolSize) {
        return new ScheduledThreadPoolExecutor(corePoolSize);
    }

    /**
     * 支持指定 corePoolSize 与 ThreadFactory，
     * 与 {@link Executors#newScheduledThreadPool(int)} 类似
     * <p>
     * Creates a thread pool that can schedule commands to run after a
     * given delay, or to execute periodically.
     * @param corePoolSize the number of threads to keep in the pool,
     * even if they are idle
     * @param threadFactory the factory to use when the executor
     * creates a new thread
     * @return the newly created scheduled thread pool
     * @throws IllegalArgumentException if {@code corePoolSize < 0}
     * @throws NullPointerException if threadFactory is null
     */
    public static ScheduledExecutorService newScheduledThreadPool(
            int corePoolSize, ThreadFactory threadFactory) {
        return new ScheduledThreadPoolExecutor(corePoolSize, threadFactory);
    }

    /**
     * 返回一个不可配置的 ExecutorService。封装成 DelegatedExecutorService
     * <p>
     * Returns an object that delegates all defined {@link
     * ExecutorService} methods to the given executor, but not any
     * other methods that might otherwise be accessible using
     * casts. This provides a way to safely "freeze" configuration and
     * disallow tuning of a given concrete implementation.
     * @param executor the underlying implementation
     * @return an {@code ExecutorService} instance
     * @throws NullPointerException if executor null
     */
    public static ExecutorService unconfigurableExecutorService(ExecutorService executor) {
        if (executor == null)
            throw new NullPointerException();
        return new DelegatedExecutorService(executor);
    }

    /**
     * 返回一个不可配置的 ScheduledExecutorService。封装成 DelegatedScheduledExecutorService
     * <p>
     * Returns an object that delegates all defined {@link
     * ScheduledExecutorService} methods to the given executor, but
     * not any other methods that might otherwise be accessible using
     * casts. This provides a way to safely "freeze" configuration and
     * disallow tuning of a given concrete implementation.
     * @param executor the underlying implementation
     * @return a {@code ScheduledExecutorService} instance
     * @throws NullPointerException if executor null
     */
    public static ScheduledExecutorService unconfigurableScheduledExecutorService(ScheduledExecutorService executor) {
        if (executor == null)
            throw new NullPointerException();
        return new DelegatedScheduledExecutorService(executor);
    }

    /**
     * 返回一个默认的线程创建工厂方法，该线程创建方法默认创建一个新的线程。
     * 创建的所有线程都在同一个 ThreadGroup 中。
     * 创建的线程都不是 daemon 线程并且线程的优先级均为 Thread.NORM_PRIORITY。
     * 线程命名为 pool-N-thread-M。
     * <p>
     * Returns a default thread factory used to create new threads.
     * This factory creates all new threads used by an Executor in the
     * same {@link ThreadGroup}. If there is a {@link
     * java.lang.SecurityManager}, it uses the group of {@link
     * System#getSecurityManager}, else the group of the thread
     * invoking this {@code defaultThreadFactory} method. Each new
     * thread is created as a non-daemon thread with priority set to
     * the smaller of {@code Thread.NORM_PRIORITY} and the maximum
     * priority permitted in the thread group.  New threads have names
     * accessible via {@link Thread#getName} of
     * <em>pool-N-thread-M</em>, where <em>N</em> is the sequence
     * number of this factory, and <em>M</em> is the sequence number
     * of the thread created by this factory.
     * @return a thread factory
     */
    public static ThreadFactory defaultThreadFactory() {
        return new DefaultThreadFactory();
    }

    /**
     * 创建一个与当前线程由相同权限的线程。继承自 DefaultThreadFactory，因此
     * 线程的属性设置与 DefaultThreadFactory 相同。
     * TODO: 既然封装成了 Callable，那么为什么还需要传入一个 T ?
     * <p>
     * Returns a thread factory used to create new threads that
     * have the same permissions as the current thread.
     * This factory creates threads with the same settings as {@link
     * Executors#defaultThreadFactory}, additionally setting the
     * AccessControlContext and contextClassLoader of new threads to
     * be the same as the thread invoking this
     * {@code privilegedThreadFactory} method.  A new
     * {@code privilegedThreadFactory} can be created within an
     * {@link AccessController#doPrivileged AccessController.doPrivileged}
     * action setting the current thread's access control context to
     * create threads with the selected permission settings holding
     * within that action.
     *
     * <p>Note that while tasks running within such threads will have
     * the same access control and class loader settings as the
     * current thread, they need not have the same {@link
     * java.lang.ThreadLocal} or {@link
     * java.lang.InheritableThreadLocal} values. If necessary,
     * particular values of thread locals can be set or reset before
     * any task runs in {@link ThreadPoolExecutor} subclasses using
     * {@link ThreadPoolExecutor#beforeExecute(Thread, Runnable)}.
     * Also, if it is necessary to initialize worker threads to have
     * the same InheritableThreadLocal settings as some other
     * designated thread, you can create a custom ThreadFactory in
     * which that thread waits for and services requests to create
     * others that will inherit its values.
     *
     * @return a thread factory
     * @throws AccessControlException if the current access control
     * context does not have permission to both get and set context
     * class loader
     */
    public static ThreadFactory privilegedThreadFactory() {
        return new PrivilegedThreadFactory();
    }

    /**
     * 将 Runnable 封装成 Callable
     * <p>
     * Returns a {@link Callable} object that, when
     * called, runs the given task and returns the given result.  This
     * can be useful when applying methods requiring a
     * {@code Callable} to an otherwise resultless action.
     * @param task the task to run
     * @param result the result to return
     * @param <T> the type of the result
     * @return a callable object
     * @throws NullPointerException if task null
     */
    public static <T> Callable<T> callable(Runnable task, T result) {
        if (task == null)
            throw new NullPointerException();
        return new RunnableAdapter<T>(task, result);
    }

    /**
     * 将 Runnable 封装成 Callable
     * <p>
     * Returns a {@link Callable} object that, when
     * called, runs the given task and returns {@code null}.
     * @param task the task to run
     * @return a callable object
     * @throws NullPointerException if task null
     */
    public static Callable<Object> callable(Runnable task) {
        if (task == null)
            throw new NullPointerException();
        return new RunnableAdapter<Object>(task, null);
    }

    /**
     * Returns a {@link Callable} object that, when
     * called, runs the given privileged action and returns its result.
     * @param action the privileged action to run
     * @return a callable object
     * @throws NullPointerException if action null
     */
    public static Callable<Object> callable(final PrivilegedAction<?> action) {
        if (action == null)
            throw new NullPointerException();
        return new Callable<Object>() {
            public Object call() { return action.run(); }};
    }

    /**
     * Returns a {@link Callable} object that, when
     * called, runs the given privileged exception action and returns
     * its result.
     * @param action the privileged exception action to run
     * @return a callable object
     * @throws NullPointerException if action null
     */
    public static Callable<Object> callable(final PrivilegedExceptionAction<?> action) {
        if (action == null)
            throw new NullPointerException();
        return new Callable<Object>() {
            public Object call() throws Exception { return action.run(); }};
    }

    /**
     * Returns a {@link Callable} object that will, when called,
     * execute the given {@code callable} under the current access
     * control context. This method should normally be invoked within
     * an {@link AccessController#doPrivileged AccessController.doPrivileged}
     * action to create callables that will, if possible, execute
     * under the selected permission settings holding within that
     * action; or if not possible, throw an associated {@link
     * AccessControlException}.
     * @param callable the underlying task
     * @param <T> the type of the callable's result
     * @return a callable object
     * @throws NullPointerException if callable null
     */
    public static <T> Callable<T> privilegedCallable(Callable<T> callable) {
        if (callable == null)
            throw new NullPointerException();
        return new PrivilegedCallable<T>(callable);
    }

    /**
     * Returns a {@link Callable} object that will, when called,
     * execute the given {@code callable} under the current access
     * control context, with the current context class loader as the
     * context class loader. This method should normally be invoked
     * within an
     * {@link AccessController#doPrivileged AccessController.doPrivileged}
     * action to create callables that will, if possible, execute
     * under the selected permission settings holding within that
     * action; or if not possible, throw an associated {@link
     * AccessControlException}.
     *
     * @param callable the underlying task
     * @param <T> the type of the callable's result
     * @return a callable object
     * @throws NullPointerException if callable null
     * @throws AccessControlException if the current access control
     * context does not have permission to both set and get context
     * class loader
     */
    public static <T> Callable<T> privilegedCallableUsingCurrentClassLoader(Callable<T> callable) {
        if (callable == null)
            throw new NullPointerException();
        return new PrivilegedCallableUsingCurrentClassLoader<T>(callable);
    }

    // Non-public classes supporting the public methods

    /**
     * Runnable 的适配器，将 Runnable 封装成 Callable
     * A callable that runs given task and returns given result.
     */
    private static final class RunnableAdapter<T> implements Callable<T> {
        /**
         * 传入的 Runnable
         */
        private final Runnable task;
        /**
         * 线程执行的结果
         */
        private final T result;
        RunnableAdapter(Runnable task, T result) {
            this.task = task;
            this.result = result;
        }

        /**
         * 线程执行入口
         */
        public T call() {
            task.run();
            // 返回结果
            return result;
        }
        public String toString() {
            return super.toString() + "[Wrapped task = " + task + "]";
        }
    }

    /**
     * A callable that runs under established access control settings.
     */
    private static final class PrivilegedCallable<T> implements Callable<T> {
        /**
         * 封装的 Callable
         */
        final Callable<T> task;
        /**
         * 访问控制上下文
         */
        final AccessControlContext acc;

        PrivilegedCallable(Callable<T> task) {
            this.task = task;
            this.acc = AccessController.getContext();
        }

        /**
         * 调用运行方法
         */
        public T call() throws Exception {
            try {
                return AccessController.doPrivileged(
                    new PrivilegedExceptionAction<T>() {
                        public T run() throws Exception {
                            return task.call();
                        }
                    }, acc);
            } catch (PrivilegedActionException e) {
                throw e.getException();
            }
        }

        public String toString() {
            return super.toString() + "[Wrapped task = " + task + "]";
        }
    }

    /**
     * 封装了 Callable，使用当前的类加载器
     *
     * A callable that runs under established access control settings and
     * current ClassLoader.
     */
    private static final class PrivilegedCallableUsingCurrentClassLoader<T>
            implements Callable<T> {
        final Callable<T> task;
        final AccessControlContext acc;
        final ClassLoader ccl;

        PrivilegedCallableUsingCurrentClassLoader(Callable<T> task) {
            SecurityManager sm = System.getSecurityManager();
            if (sm != null) {
                // Calls to getContextClassLoader from this class
                // never trigger a security check, but we check
                // whether our callers have this permission anyways.
                sm.checkPermission(SecurityConstants.GET_CLASSLOADER_PERMISSION);

                // Whether setContextClassLoader turns out to be necessary
                // or not, we fail fast if permission is not available.
                sm.checkPermission(new RuntimePermission("setContextClassLoader"));
            }
            this.task = task;
            this.acc = AccessController.getContext();
            // 设置为当前线程的上下文类加载器
            this.ccl = Thread.currentThread().getContextClassLoader();
        }

        public T call() throws Exception {
            try {
                return AccessController.doPrivileged(
                    new PrivilegedExceptionAction<T>() {
                        public T run() throws Exception {
                            Thread t = Thread.currentThread();
                            ClassLoader cl = t.getContextClassLoader();
                            if (ccl == cl) {
                                return task.call();
                            } else {
                                t.setContextClassLoader(ccl);
                                try {
                                    return task.call();
                                } finally {
                                    t.setContextClassLoader(cl);
                                }
                            }
                        }
                    }, acc);
            } catch (PrivilegedActionException e) {
                throw e.getException();
            }
        }

        public String toString() {
            return super.toString() + "[Wrapped task = " + task + "]";
        }
    }

    /**
     * 默认的线程创建工厂
     * <p>The default thread factory.
     */
    private static class DefaultThreadFactory implements ThreadFactory {
        /**
         * 线程池的计数
         */
        private static final AtomicInteger poolNumber = new AtomicInteger(1);
        /**
         * 线程组
         * 与 thread 绑定，如果在创建 thread 时没有指定 thread，那么 group 可以为 null
         */
        private final ThreadGroup group;
        /**
         * 线程计数
         */
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        /**
         * 线程名字的前缀
         */
        private final String namePrefix;

        /**
         * 构造方法
         */
        DefaultThreadFactory() {
            SecurityManager s = System.getSecurityManager();
            //  初始化 group
            group = (s != null) ? s.getThreadGroup() :
                                  Thread.currentThread().getThreadGroup();
            // 初始化线程池的名字
            namePrefix = "pool-" +
                          poolNumber.getAndIncrement() +
                         "-thread-";
        }

        /**
         * 返回一个 thread
         */
        public Thread newThread(Runnable r) {
            // 直接通过 new Thread 的方法创建一个 thread
            Thread t = new Thread(group, r,
                                  // 线程名为线程池的名字+线程技计数
                                  namePrefix + threadNumber.getAndIncrement(),
                                  0);
            // 设置为非 daemon 线程
            if (t.isDaemon())
                t.setDaemon(false);
            // 设置线程的优先级为 NORM_PRIORITY
            if (t.getPriority() != Thread.NORM_PRIORITY)
                t.setPriority(Thread.NORM_PRIORITY);
            return t;
        }
    }

    /**
     * Thread factory capturing access control context and class loader.
     */
    private static class PrivilegedThreadFactory extends DefaultThreadFactory {
        final AccessControlContext acc;
        final ClassLoader ccl;

        PrivilegedThreadFactory() {
            super();
            SecurityManager sm = System.getSecurityManager();
            if (sm != null) {
                // Calls to getContextClassLoader from this class
                // never trigger a security check, but we check
                // whether our callers have this permission anyways.
                sm.checkPermission(SecurityConstants.GET_CLASSLOADER_PERMISSION);

                // Fail fast
                sm.checkPermission(new RuntimePermission("setContextClassLoader"));
            }
            this.acc = AccessController.getContext();
            // 获取当前的线程上下文
            this.ccl = Thread.currentThread().getContextClassLoader();
        }

        /**
         * 返回一个线程
         */
        public Thread newThread(final Runnable r) {
            // 调用 DefaultThreadFactory 的 newThread 方法
            return super.newThread(new Runnable() {
                public void run() {
                    AccessController.doPrivileged(new PrivilegedAction<>() {
                        public Void run() {
                            // 设置线程的类加载器为 ccl
                            Thread.currentThread().setContextClassLoader(ccl);
                            r.run();
                            return null;
                        }
                    }, acc);
                }
            });
        }
    }

    /**
     * delegated 线程池，基本上都是委托给内部持有的 ExecutorService 执行。
     * 在 finally 块中调用 reachabilityFence 方法。
     * reachabilityFence 方法: 确保当前 DelegatedExecutorService 是强
     * 可达的，不会被 GC 回收。
     * <p>
     * A wrapper class that exposes only the ExecutorService methods
     * of an ExecutorService implementation.
     */
    private static class DelegatedExecutorService
            implements ExecutorService {
        /**
         * 封装了一个 ExecutorService
         */
        private final ExecutorService e;
        DelegatedExecutorService(ExecutorService executor) { e = executor; }
        public void execute(Runnable command) {
            try {
                e.execute(command);
            } finally { reachabilityFence(this); }
        }
        public void shutdown() { e.shutdown(); }
        public List<Runnable> shutdownNow() {
            try {
                return e.shutdownNow();
            } finally { reachabilityFence(this); }
        }
        public boolean isShutdown() {
            try {
                return e.isShutdown();
            } finally { reachabilityFence(this); }
        }
        public boolean isTerminated() {
            try {
                return e.isTerminated();
            } finally { reachabilityFence(this); }
        }
        public boolean awaitTermination(long timeout, TimeUnit unit)
            throws InterruptedException {
            try {
                return e.awaitTermination(timeout, unit);
            } finally { reachabilityFence(this); }
        }
        public Future<?> submit(Runnable task) {
            try {
                return e.submit(task);
            } finally { reachabilityFence(this); }
        }
        public <T> Future<T> submit(Callable<T> task) {
            try {
                return e.submit(task);
            } finally { reachabilityFence(this); }
        }
        public <T> Future<T> submit(Runnable task, T result) {
            try {
                return e.submit(task, result);
            } finally { reachabilityFence(this); }
        }
        public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks)
            throws InterruptedException {
            try {
                return e.invokeAll(tasks);
            } finally { reachabilityFence(this); }
        }
        public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks,
                                             long timeout, TimeUnit unit)
            throws InterruptedException {
            try {
                return e.invokeAll(tasks, timeout, unit);
            } finally { reachabilityFence(this); }
        }
        public <T> T invokeAny(Collection<? extends Callable<T>> tasks)
            throws InterruptedException, ExecutionException {
            try {
                return e.invokeAny(tasks);
            } finally { reachabilityFence(this); }
        }
        public <T> T invokeAny(Collection<? extends Callable<T>> tasks,
                               long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
            try {
                return e.invokeAny(tasks, timeout, unit);
            } finally { reachabilityFence(this); }
        }
    }

    /**
     * 继承自 DelegatedExecutorService ，重写了 finalize 方法，
     * 在该线程池实例被回收时会触发关闭线程池的操作。
     */
    private static class FinalizableDelegatedExecutorService
            extends DelegatedExecutorService {
        FinalizableDelegatedExecutorService(ExecutorService executor) {
            super(executor);
        }
        @SuppressWarnings("deprecation")
        protected void finalize() {
            super.shutdown();
        }
    }

    /**
     * 继承自 DelegatedExecutorService，方法一般都委托给内部的 ScheduledExecutorService
     * 来执行
     * <p>
     * A wrapper class that exposes only the ScheduledExecutorService
     * methods of a ScheduledExecutorService implementation.
     */
    private static class DelegatedScheduledExecutorService
            extends DelegatedExecutorService
            implements ScheduledExecutorService {
        /**
         * 内部持有一个 ScheduledExecutorService
         */
        private final ScheduledExecutorService e;
        DelegatedScheduledExecutorService(ScheduledExecutorService executor) {
            super(executor);
            e = executor;
        }
        public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
            return e.schedule(command, delay, unit);
        }
        public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
            return e.schedule(callable, delay, unit);
        }
        public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
            return e.scheduleAtFixedRate(command, initialDelay, period, unit);
        }
        public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
            return e.scheduleWithFixedDelay(command, initialDelay, delay, unit);
        }
    }

    /** Cannot instantiate. */
    /**
     * 私有构造函数，不能通过常规方法实例化
     */
    private Executors() {}
}
