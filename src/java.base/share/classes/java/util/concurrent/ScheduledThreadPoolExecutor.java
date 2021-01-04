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

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

import java.util.AbstractQueue;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 在多线程环境下，该类比 Timer 更推荐。
 * <p>
 * A {@link ThreadPoolExecutor} that can additionally schedule
 * commands to run after a given delay, or to execute periodically.
 * This class is preferable to {@link java.util.Timer} when multiple
 * worker threads are needed, or when the additional flexibility or
 * capabilities of {@link ThreadPoolExecutor} (which this class
 * extends) are required.
 *
 * <p>Delayed tasks execute no sooner than they are enabled, but
 * without any real-time guarantees about when, after they are
 * enabled, they will commence. Tasks scheduled for exactly the same
 * execution time are enabled in first-in-first-out (FIFO) order of
 * submission.
 *
 * <p>When a submitted task is cancelled before it is run, execution
 * is suppressed.  By default, such a cancelled task is not
 * automatically removed from the work queue until its delay elapses.
 * While this enables further inspection and monitoring, it may also
 * cause unbounded retention of cancelled tasks.  To avoid this, use
 * {@link #setRemoveOnCancelPolicy} to cause tasks to be immediately
 * removed from the work queue at time of cancellation.
 *
 * <p>Successive executions of a periodic task scheduled via
 * {@link #scheduleAtFixedRate scheduleAtFixedRate} or
 * {@link #scheduleWithFixedDelay scheduleWithFixedDelay}
 * do not overlap. While different executions may be performed by
 * different threads, the effects of prior executions
 * <a href="package-summary.html#MemoryVisibility"><i>happen-before</i></a>
 * those of subsequent ones.
 *
 * <p>While this class inherits from {@link ThreadPoolExecutor}, a few
 * of the inherited tuning methods are not useful for it. In
 * particular, because it acts as a fixed-sized pool using
 * {@code corePoolSize} threads and an unbounded queue, adjustments
 * to {@code maximumPoolSize} have no useful effect. Additionally, it
 * is almost never a good idea to set {@code corePoolSize} to zero or
 * use {@code allowCoreThreadTimeOut} because this may leave the pool
 * without threads to handle tasks once they become eligible to run.
 *
 * <p>As with {@code ThreadPoolExecutor}, if not otherwise specified,
 * this class uses {@link Executors#defaultThreadFactory} as the
 * default thread factory, and {@link ThreadPoolExecutor.AbortPolicy}
 * as the default rejected execution handler.
 *
 * <p><b>Extension notes:</b> This class overrides the
 * {@link ThreadPoolExecutor#execute(Runnable) execute} and
 * {@link AbstractExecutorService#submit(Runnable) submit}
 * methods to generate internal {@link ScheduledFuture} objects to
 * control per-task delays and scheduling.  To preserve
 * functionality, any further overrides of these methods in
 * subclasses must invoke superclass versions, which effectively
 * disables additional task customization.  However, this class
 * provides alternative protected extension method
 * {@code decorateTask} (one version each for {@code Runnable} and
 * {@code Callable}) that can be used to customize the concrete task
 * types used to execute commands entered via {@code execute},
 * {@code submit}, {@code schedule}, {@code scheduleAtFixedRate},
 * and {@code scheduleWithFixedDelay}.  By default, a
 * {@code ScheduledThreadPoolExecutor} uses a task type extending
 * {@link FutureTask}. However, this may be modified or replaced using
 * subclasses of the form:
 *
 * <pre> {@code
 * public class CustomScheduledExecutor extends ScheduledThreadPoolExecutor {
 *
 *   static class CustomTask<V> implements RunnableScheduledFuture<V> { ... }
 *
 *   protected <V> RunnableScheduledFuture<V> decorateTask(
 *                Runnable r, RunnableScheduledFuture<V> task) {
 *       return new CustomTask<V>(r, task);
 *   }
 *
 *   protected <V> RunnableScheduledFuture<V> decorateTask(
 *                Callable<V> c, RunnableScheduledFuture<V> task) {
 *       return new CustomTask<V>(c, task);
 *   }
 *   // ... add constructors, etc.
 * }}</pre>
 *
 * @since 1.5
 * @author Doug Lea
 */
public class ScheduledThreadPoolExecutor
        extends ThreadPoolExecutor
        implements ScheduledExecutorService {

    /*
     * This class specializes ThreadPoolExecutor implementation by
     *
     * 1. Using a custom task type ScheduledFutureTask, even for tasks
     *    that don't require scheduling because they are submitted
     *    using ExecutorService rather than ScheduledExecutorService
     *    methods, which are treated as tasks with a delay of zero.
     *
     * 2. Using a custom queue (DelayedWorkQueue), a variant of
     *    unbounded DelayQueue. The lack of capacity constraint and
     *    the fact that corePoolSize and maximumPoolSize are
     *    effectively identical simplifies some execution mechanics
     *    (see delayedExecute) compared to ThreadPoolExecutor.
     *
     * 3. Supporting optional run-after-shutdown parameters, which
     *    leads to overrides of shutdown methods to remove and cancel
     *    tasks that should NOT be run after shutdown, as well as
     *    different recheck logic when task (re)submission overlaps
     *    with a shutdown.
     *
     * 4. Task decoration methods to allow interception and
     *    instrumentation, which are needed because subclasses cannot
     *    otherwise override submit methods to get this effect. These
     *    don't have any impact on pool control logic though.
     */

    /**
     * 线程池 shutdown 之后是否还允许周期性任务继续执行
     * <p>
     * False if should cancel/suppress periodic tasks on shutdown.
     */
    private volatile boolean continueExistingPeriodicTasksAfterShutdown;

    /**
     * 线程池 shutdown 之后是否还允许延时任务继续执行
     * <p>
     * False if should cancel non-periodic not-yet-expired tasks on shutdown.
     */
    private volatile boolean executeExistingDelayedTasksAfterShutdown = true;

    /**
     * 在 ScheduledFutureTask 调用 cancel 的时候是否将其从 queue 中移除
     * <p>
     * True if ScheduledFutureTask.cancel should remove from queue.
     */
    volatile boolean removeOnCancel;

    /**
     * Sequence number to break scheduling ties, and in turn to
     * guarantee FIFO order among tied entries.
     */
    private static final AtomicLong sequencer = new AtomicLong();

    /**
     * 实现了 RunnableScheduledFuture 接口
     * 继承了 FutureTask
     */
    private class ScheduledFutureTask<V>
            extends FutureTask<V> implements RunnableScheduledFuture<V> {

        /** Sequence number to break ties FIFO */
        private final long sequenceNumber;

        /**
         * 任务开始执行之后的计时
         * <p>
         * The nanoTime-based time when the task is enabled to execute.
         */
        private volatile long time;

        /**
         * > 0: 参数定了之后，固定时间点执行任务
         * < 0: 参数定了会后，两个任务的时间间隔一定
         * 0 值表示一次性任务.
         * <p>
         *
         * Period for repeating tasks, in nanoseconds.
         * A positive value indicates fixed-rate execution.
         * A negative value indicates fixed-delay execution.
         * A value of 0 indicates a non-repeating (one-shot) task.
         */
        private final long period;

        /**
         * 周期性任务的下一个任务<p>
         * The actual task to be re-enqueued by reExecutePeriodic
         */
        RunnableScheduledFuture<V> outerTask = this;

        /**
         * 在 delayQueue 所在的堆中的索引
         * <p>
         * Index into delay queue, to support faster cancellation.
         */
        int heapIndex;

        /**
         * Creates a one-shot action with given nanoTime-based trigger time.
         */
        ScheduledFutureTask(Runnable r, V result, long triggerTime,
                            long sequenceNumber) {
            super(r, result);
            // 执行时间
            this.time = triggerTime;
            // 一次性任务
            this.period = 0;
            this.sequenceNumber = sequenceNumber;
        }

        /**
         * Creates a periodic action with given nanoTime-based initial
         * trigger time and period.
         */
        ScheduledFutureTask(Runnable r, V result, long triggerTime,
                            long period, long sequenceNumber) {
            super(r, result);
            // 执行启动时间
            this.time = triggerTime;
            this.period = period;
            this.sequenceNumber = sequenceNumber;
        }

        /**
         * 一次性任务<p>
         * Creates a one-shot action with given nanoTime-based trigger time.
         */
        ScheduledFutureTask(Callable<V> callable, long triggerTime,
                            long sequenceNumber) {
            super(callable);
            // 执行启动时间
            this.time = triggerTime;
            this.period = 0;
            this.sequenceNumber = sequenceNumber;
        }

        /**
         * 返回剩余时间
         */
        public long getDelay(TimeUnit unit) {
            return unit.convert(time - System.nanoTime(), NANOSECONDS);
        }

        /**
         * 因为是在堆中，定义比较规则，
         * 根据触发任务的时间进行排序，
         * 时间越早，优先级越高。
         */
        public int compareTo(Delayed other) {
            if (other == this) // compare zero if same object
                return 0;
            if (other instanceof ScheduledFutureTask) {
                ScheduledFutureTask<?> x = (ScheduledFutureTask<?>)other;
                long diff = time - x.time;
                if (diff < 0)
                    return -1;
                else if (diff > 0)
                    return 1;
                else if (sequenceNumber < x.sequenceNumber)
                    return -1;
                else
                    return 1;
            }
            long diff = getDelay(NANOSECONDS) - other.getDelay(NANOSECONDS);
            return (diff < 0) ? -1 : (diff > 0) ? 1 : 0;
        }

        /**
         * 返回是否为周期性任务<p>
         * Returns {@code true} if this is a periodic (not a one-shot) action.
         *
         * @return {@code true} if periodic
         */
        public boolean isPeriodic() {
            return period != 0;
        }

        /**
         * 周期性任务设置下一次执行的时间<p>
         * 
         * Sets the next time to run for a periodic task.
         */
        private void setNextRunTime() {
            long p = period;
            // >0 说明是 atFixedRate 执行，也就是固定时间点执行
            if (p > 0)
                // 直接加上 period
                time += p;
            else
                // < 0 说明是 withFixedRate 执行
                time = triggerTime(-p);
        }

        /**
         * 取消 task
         */
        public boolean cancel(boolean mayInterruptIfRunning) {
            // The racy read of heapIndex below is benign:
            // if heapIndex < 0, then OOTA guarantees that we have surely
            // been removed; else we recheck under lock in remove()
            // 取消任务
            boolean cancelled = super.cancel(mayInterruptIfRunning);
            // 如果设置取消的任务
            if (cancelled && removeOnCancel && heapIndex >= 0)
                // 从队列中取消任务
                remove(this);
            // 返回是否取消成功
            return cancelled;
        }

        /**
         * 执行任务
         * <p>
         * Overrides FutureTask version so as to reset/requeue if periodic.
         */
        public void run() {
            // 判断当前状态能否执行任务
            if (!canRunInCurrentRunState(this))
                // 不能执行则取消
                cancel(false);
            // 如果不是周期性任务
            else if (!isPeriodic())
                // 直接执行
                super.run();
            // 周期性任务
            // 运行任务且重置状态为 NEW，其实内部并没有改变 state 的状态
            else if (super.runAndReset()) {
                // 一次执行完之后设置下一次的执行时间
                setNextRunTime();
                // 再次执行任务，传入的任务为 outerTask
                reExecutePeriodic(outerTask);
            }
        }
    }

    /**
     * 返回当前运行状态下，当前任务类型是否支持运行
     * <p>
     * Returns true if can run a task given current run state and
     * run-after-shutdown parameters.
     */
    boolean canRunInCurrentRunState(RunnableScheduledFuture<?> task) {
        // 状态为 < SHUTDOWN 状态
        if (!isShutdown())
            // 可以执行
            return true;
        // 如果状态至少为 STOP 状态
        if (isStopped())
            // 不可以执行
            return false;
        // 走到这里说明当前运行状态为 SHUTDOWN 状态
        // 需要根据控制参数判断
        return task.isPeriodic()
            ? continueExistingPeriodicTasksAfterShutdown
            : (executeExistingDelayedTasksAfterShutdown
                // 已经达到延时时间
               || task.getDelay(NANOSECONDS) <= 0);
    }

    /**
     * 执行任务
     * <p>
     * Main execution method for delayed or periodic tasks.  If pool
     * is shut down, rejects the task. Otherwise adds task to queue
     * and starts a thread, if necessary, to run it.  (We cannot
     * prestart the thread to run the task because the task (probably)
     * shouldn't be run yet.)  If the pool is shut down while the task
     * is being added, cancel and remove it if required by state and
     * run-after-shutdown parameters.
     *
     * @param task the task
     */
    private void delayedExecute(RunnableScheduledFuture<?> task) {
        // 如果线程池状态已经 shutdown
        if (isShutdown())
            // 直接拒绝任务
            reject(task);
        else {
            // 入队
            super.getQueue().add(task);
            // 检查当前状态
            if (!canRunInCurrentRunState(task) && remove(task))
                task.cancel(false);
            else
                // 确保有线程能够执行任务
                ensurePrestart();
        }
    }

    /**
     * 再次执行任务
     * <p>
     * Requeues a periodic task unless current run state precludes it.
     * Same idea as delayedExecute except drops task rather than rejecting.
     *
     * @param task the task
     */
    void reExecutePeriodic(RunnableScheduledFuture<?> task) {
        // 检查是否可以执行
        if (canRunInCurrentRunState(task)) {
            // 再次入队
            super.getQueue().add(task);
            // 检查状态
            if (canRunInCurrentRunState(task) || !remove(task)) {
                ensurePrestart();
                return;
            }
        }
        // 取消任务
        task.cancel(false);
    }

    /**
     * shutdown 的回调
     *
     * Cancels and clears the queue of all tasks that should not be run
     * due to shutdown policy.  Invoked within super.shutdown.
     */
    @Override void onShutdown() {
        BlockingQueue<Runnable> q = super.getQueue();
        boolean keepDelayed =
            getExecuteExistingDelayedTasksAfterShutdownPolicy();
        boolean keepPeriodic =
            getContinueExistingPeriodicTasksAfterShutdownPolicy();
        // Traverse snapshot to avoid iterator exceptions
        // TODO: implement and use efficient removeIf
        // super.getQueue().removeIf(...);
        // 遍历数组
        for (Object e : q.toArray()) {
            if (e instanceof RunnableScheduledFuture) {
                RunnableScheduledFuture<?> t = (RunnableScheduledFuture<?>)e;
                // 是否是周期性任务
                if ((t.isPeriodic()
                        // 不执行周期性任务
                     ? !keepPeriodic
                        // 不执行延时任务且延时时间还未到
                     : (!keepDelayed && t.getDelay(NANOSECONDS) > 0))
                        // 或者 当前任务已经被取消
                    || t.isCancelled()) { // also remove if already cancelled
                    // 从队列中移除该任务
                    if (q.remove(t))
                        // 并取消该任务
                        t.cancel(false);
                }
            }
        }
        // 尝试终止线程池
        tryTerminate();
    }

    /**
     * Modifies or replaces the task used to execute a runnable.
     * This method can be used to override the concrete
     * class used for managing internal tasks.
     * The default implementation simply returns the given task.
     *
     * @param runnable the submitted Runnable
     * @param task the task created to execute the runnable
     * @param <V> the type of the task's result
     * @return a task that can execute the runnable
     * @since 1.6
     */
    protected <V> RunnableScheduledFuture<V> decorateTask(
        Runnable runnable, RunnableScheduledFuture<V> task) {
        return task;
    }

    /**
     * Modifies or replaces the task used to execute a callable.
     * This method can be used to override the concrete
     * class used for managing internal tasks.
     * The default implementation simply returns the given task.
     *
     * @param callable the submitted Callable
     * @param task the task created to execute the callable
     * @param <V> the type of the task's result
     * @return a task that can execute the callable
     * @since 1.6
     */
    protected <V> RunnableScheduledFuture<V> decorateTask(
        Callable<V> callable, RunnableScheduledFuture<V> task) {
        return task;
    }

    /**
     * 默认的 keepalivetime 为 10 毫秒 
     * <p>
     * The default keep-alive time for pool threads.
     *
     * Normally, this value is unused because all pool threads will be
     * core threads, but if a user creates a pool with a corePoolSize
     * of zero (against our advice), we keep a thread alive as long as
     * there are queued tasks.  If the keep alive time is zero (the
     * historic value), we end up hot-spinning in getTask, wasting a
     * CPU.  But on the other hand, if we set the value too high, and
     * users create a one-shot pool which they don't cleanly shutdown,
     * the pool's non-daemon threads will prevent JVM termination.  A
     * small but non-zero value (relative to a JVM's lifetime) seems
     * best.
     */
    private static final long DEFAULT_KEEPALIVE_MILLIS = 10L;

    /**
     * 指定核心线程池的大小,
     * maxPoolSize 为 Integer.MAX_VALUE,
     * 默认的时间为 MILLISECONDS 
     * <p>
     * Creates a new {@code ScheduledThreadPoolExecutor} with the
     * given core pool size.
     *
     * @param corePoolSize the number of threads to keep in the pool, even
     *        if they are idle, unless {@code allowCoreThreadTimeOut} is set
     * @throws IllegalArgumentException if {@code corePoolSize < 0}
     */
    public ScheduledThreadPoolExecutor(int corePoolSize) {
        super(corePoolSize, Integer.MAX_VALUE,
              DEFAULT_KEEPALIVE_MILLIS, MILLISECONDS,
              new DelayedWorkQueue());
    }

    /**
     * 核心线程池的大小以及线程创建工厂
     * <p>
     * Creates a new {@code ScheduledThreadPoolExecutor} with the
     * given initial parameters.
     *
     * @param corePoolSize the number of threads to keep in the pool, even
     *        if they are idle, unless {@code allowCoreThreadTimeOut} is set
     * @param threadFactory the factory to use when the executor
     *        creates a new thread
     * @throws IllegalArgumentException if {@code corePoolSize < 0}
     * @throws NullPointerException if {@code threadFactory} is null
     */
    public ScheduledThreadPoolExecutor(int corePoolSize,
                                       ThreadFactory threadFactory) {
        super(corePoolSize, Integer.MAX_VALUE,
              DEFAULT_KEEPALIVE_MILLIS, MILLISECONDS,
              new DelayedWorkQueue(), threadFactory);
    }

    /**
     * 指定核心线程池的大小以及拒绝策略
     * <p>
     * Creates a new {@code ScheduledThreadPoolExecutor} with the
     * given initial parameters.
     *
     * @param corePoolSize the number of threads to keep in the pool, even
     *        if they are idle, unless {@code allowCoreThreadTimeOut} is set
     * @param handler the handler to use when execution is blocked
     *        because the thread bounds and queue capacities are reached
     * @throws IllegalArgumentException if {@code corePoolSize < 0}
     * @throws NullPointerException if {@code handler} is null
     */
    public ScheduledThreadPoolExecutor(int corePoolSize,
                                       RejectedExecutionHandler handler) {
        super(corePoolSize, Integer.MAX_VALUE,
              DEFAULT_KEEPALIVE_MILLIS, MILLISECONDS,
              new DelayedWorkQueue(), handler);
    }

    /**
     * 指定核心线程池的大小以及线程创建工厂和拒绝处理器
     * <p>
     * Creates a new {@code ScheduledThreadPoolExecutor} with the
     * given initial parameters.
     *
     * @param corePoolSize the number of threads to keep in the pool, even
     *        if they are idle, unless {@code allowCoreThreadTimeOut} is set
     * @param threadFactory the factory to use when the executor
     *        creates a new thread
     * @param handler the handler to use when execution is blocked
     *        because the thread bounds and queue capacities are reached
     * @throws IllegalArgumentException if {@code corePoolSize < 0}
     * @throws NullPointerException if {@code threadFactory} or
     *         {@code handler} is null
     */
    public ScheduledThreadPoolExecutor(int corePoolSize,
                                       ThreadFactory threadFactory,
                                       RejectedExecutionHandler handler) {
        super(corePoolSize, Integer.MAX_VALUE,
              DEFAULT_KEEPALIVE_MILLIS, MILLISECONDS,
              new DelayedWorkQueue(), threadFactory, handler);
    }

    /**
     * 返回任务执行时间
     * <p>
     * Returns the nanoTime-based trigger time of a delayed action.
     */
    private long triggerTime(long delay, TimeUnit unit) {
        return triggerTime(unit.toNanos((delay < 0) ? 0 : delay));
    }

    /**
     * 返回任务执行的时间
     * <p>
     * Returns the nanoTime-based trigger time of a delayed action.
     */
    long triggerTime(long delay) {
        // 当前时间 + 加上 delay 时间
        return System.nanoTime() +
            ((delay < (Long.MAX_VALUE >> 1)) ? delay : overflowFree(delay));
    }

    /**
     * Constrains the values of all delays in the queue to be within
     * Long.MAX_VALUE of each other, to avoid overflow in compareTo.
     * This may occur if a task is eligible to be dequeued, but has
     * not yet been, while some other task is added with a delay of
     * Long.MAX_VALUE.
     */
    private long overflowFree(long delay) {
        Delayed head = (Delayed) super.getQueue().peek();
        if (head != null) {
            long headDelay = head.getDelay(NANOSECONDS);
            if (headDelay < 0 && (delay - headDelay < 0))
                delay = Long.MAX_VALUE + headDelay;
        }
        return delay;
    }

    /**
     * @throws RejectedExecutionException {@inheritDoc}
     * @throws NullPointerException       {@inheritDoc}
     */
    public ScheduledFuture<?> schedule(Runnable command,
                                       long delay,
                                       TimeUnit unit) {
        if (command == null || unit == null)
            throw new NullPointerException();
        RunnableScheduledFuture<Void> t = decorateTask(command,
            new ScheduledFutureTask<Void>(command, null,
                                          triggerTime(delay, unit),
                                          sequencer.getAndIncrement()));
        delayedExecute(t);
        return t;
    }

    /**
     * 调度任务，内部调用的是 delayedExecute 方法
     * <p>
     * @throws RejectedExecutionException {@inheritDoc}
     * @throws NullPointerException       {@inheritDoc}
     */
    public <V> ScheduledFuture<V> schedule(Callable<V> callable,
                                           long delay,
                                           TimeUnit unit) {
        if (callable == null || unit == null)
            throw new NullPointerException();
        RunnableScheduledFuture<V> t = decorateTask(callable,
            // 主要根绝 callable 封装了一个 ScheduledFutureTask
            new ScheduledFutureTask<V>(callable,
                                       triggerTime(delay, unit),
                                       sequencer.getAndIncrement()));
        delayedExecute(t);
        return t;
    }

    /**
     * 固定时间点的周期性执行
     * <p>
     * Submits a periodic action that becomes enabled first after the
     * given initial delay, and subsequently with the given period;
     * that is, executions will commence after
     * {@code initialDelay}, then {@code initialDelay + period}, then
     * {@code initialDelay + 2 * period}, and so on.
     *
     * <p>The sequence of task executions continues indefinitely until
     * one of the following exceptional completions occur:
     * <ul>
     * <li>The task is {@linkplain Future#cancel explicitly cancelled}
     * via the returned future.
     * <li>Method {@link #shutdown} is called and the {@linkplain
     * #getContinueExistingPeriodicTasksAfterShutdownPolicy policy on
     * whether to continue after shutdown} is not set true, or method
     * {@link #shutdownNow} is called; also resulting in task
     * cancellation.
     * <li>An execution of the task throws an exception.  In this case
     * calling {@link Future#get() get} on the returned future will throw
     * {@link ExecutionException}, holding the exception as its cause.
     * </ul>
     * Subsequent executions are suppressed.  Subsequent calls to
     * {@link Future#isDone isDone()} on the returned future will
     * return {@code true}.
     *
     * <p>If any execution of this task takes longer than its period, then
     * subsequent executions may start late, but will not concurrently
     * execute.
     *
     * @throws RejectedExecutionException {@inheritDoc}
     * @throws NullPointerException       {@inheritDoc}
     * @throws IllegalArgumentException   {@inheritDoc}
     */
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable command,
                                                  long initialDelay,
                                                  long period,
                                                  TimeUnit unit) {
        if (command == null || unit == null)
            throw new NullPointerException();
        if (period <= 0L)
            throw new IllegalArgumentException();
        ScheduledFutureTask<Void> sft =
            new ScheduledFutureTask<Void>(command,
                                          null,
                                          triggerTime(initialDelay, unit),
                                          unit.toNanos(period),
                                          sequencer.getAndIncrement());
        RunnableScheduledFuture<Void> t = decorateTask(command, sft);
        sft.outerTask = t;
        delayedExecute(t);
        return t;
    }

    /**
     * 两个任务之间固定延时时间之后执行<p>
     * Submits a periodic action that becomes enabled first after the
     * given initial delay, and subsequently with the given delay
     * between the termination of one execution and the commencement of
     * the next.
     *
     * <p>The sequence of task executions continues indefinitely until
     * one of the following exceptional completions occur:
     * <ul>
     * <li>The task is {@linkplain Future#cancel explicitly cancelled}
     * via the returned future.
     * <li>Method {@link #shutdown} is called and the {@linkplain
     * #getContinueExistingPeriodicTasksAfterShutdownPolicy policy on
     * whether to continue after shutdown} is not set true, or method
     * {@link #shutdownNow} is called; also resulting in task
     * cancellation.
     * <li>An execution of the task throws an exception.  In this case
     * calling {@link Future#get() get} on the returned future will throw
     * {@link ExecutionException}, holding the exception as its cause.
     * </ul>
     * Subsequent executions are suppressed.  Subsequent calls to
     * {@link Future#isDone isDone()} on the returned future will
     * return {@code true}.
     *
     * @throws RejectedExecutionException {@inheritDoc}
     * @throws NullPointerException       {@inheritDoc}
     * @throws IllegalArgumentException   {@inheritDoc}
     */
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command,
                                                     long initialDelay,
                                                     long delay,
                                                     TimeUnit unit) {
        if (command == null || unit == null)
            throw new NullPointerException();
        if (delay <= 0L)
            throw new IllegalArgumentException();
        ScheduledFutureTask<Void> sft =
            new ScheduledFutureTask<Void>(command,
                                          null,
                                          triggerTime(initialDelay, unit),
                                          // 传入为负数 withFixedRate
                                          -unit.toNanos(delay),
                                          sequencer.getAndIncrement());
        RunnableScheduledFuture<Void> t = decorateTask(command, sft);
        sft.outerTask = t;
        delayedExecute(t);
        return t;
    }

    /**
     * 相当于直接执行，没有调度
     * 没有返回值
     * <p>
     * Executes {@code command} with zero required delay.
     * This has effect equivalent to
     * {@link #schedule(Runnable,long,TimeUnit) schedule(command, 0, anyUnit)}.
     * Note that inspections of the queue and of the list returned by
     * {@code shutdownNow} will access the zero-delayed
     * {@link ScheduledFuture}, not the {@code command} itself.
     *
     * <p>A consequence of the use of {@code ScheduledFuture} objects is
     * that {@link ThreadPoolExecutor#afterExecute afterExecute} is always
     * called with a null second {@code Throwable} argument, even if the
     * {@code command} terminated abruptly.  Instead, the {@code Throwable}
     * thrown by such a task can be obtained via {@link Future#get}.
     *
     * @throws RejectedExecutionException at discretion of
     *         {@code RejectedExecutionHandler}, if the task
     *         cannot be accepted for execution because the
     *         executor has been shut down
     * @throws NullPointerException {@inheritDoc}
     */
    public void execute(Runnable command) {
        schedule(command, 0, NANOSECONDS);
    }

    // Override AbstractExecutorService methods

    /**
     * @throws RejectedExecutionException {@inheritDoc}
     * @throws NullPointerException       {@inheritDoc}
     */
    public Future<?> submit(Runnable task) {
        return schedule(task, 0, NANOSECONDS);
    }

    /**
     * @throws RejectedExecutionException {@inheritDoc}
     * @throws NullPointerException       {@inheritDoc}
     */
    public <T> Future<T> submit(Runnable task, T result) {
        return schedule(Executors.callable(task, result), 0, NANOSECONDS);
    }

    /**
     * 重写了 submit，调用 schedule 方法.
     * <p>
     * @throws RejectedExecutionException {@inheritDoc}
     * @throws NullPointerException       {@inheritDoc}
     */
    public <T> Future<T> submit(Callable<T> task) {
        return schedule(task, 0, NANOSECONDS);
    }

    /**
     * 设置 continueExistingPeriodicTasksAfterShutdown
     * <p>
     * Sets the policy on whether to continue executing existing
     * periodic tasks even when this executor has been {@code shutdown}.
     * In this case, executions will continue until {@code shutdownNow}
     * or the policy is set to {@code false} when already shutdown.
     * This value is by default {@code false}.
     *
     * @param value if {@code true}, continue after shutdown, else don't
     * @see #getContinueExistingPeriodicTasksAfterShutdownPolicy
     */
    public void setContinueExistingPeriodicTasksAfterShutdownPolicy(boolean value) {
        continueExistingPeriodicTasksAfterShutdown = value;
        if (!value && isShutdown())
            onShutdown();
    }

    /**
     * Gets the policy on whether to continue executing existing
     * periodic tasks even when this executor has been {@code shutdown}.
     * In this case, executions will continue until {@code shutdownNow}
     * or the policy is set to {@code false} when already shutdown.
     * This value is by default {@code false}.
     *
     * @return {@code true} if will continue after shutdown
     * @see #setContinueExistingPeriodicTasksAfterShutdownPolicy
     */
    public boolean getContinueExistingPeriodicTasksAfterShutdownPolicy() {
        return continueExistingPeriodicTasksAfterShutdown;
    }

    /**
     * Sets the policy on whether to execute existing delayed
     * tasks even when this executor has been {@code shutdown}.
     * In this case, these tasks will only terminate upon
     * {@code shutdownNow}, or after setting the policy to
     * {@code false} when already shutdown.
     * This value is by default {@code true}.
     *
     * @param value if {@code true}, execute after shutdown, else don't
     * @see #getExecuteExistingDelayedTasksAfterShutdownPolicy
     */
    public void setExecuteExistingDelayedTasksAfterShutdownPolicy(boolean value) {
        executeExistingDelayedTasksAfterShutdown = value;
        if (!value && isShutdown())
            onShutdown();
    }

    /**
     * Gets the policy on whether to execute existing delayed
     * tasks even when this executor has been {@code shutdown}.
     * In this case, these tasks will only terminate upon
     * {@code shutdownNow}, or after setting the policy to
     * {@code false} when already shutdown.
     * This value is by default {@code true}.
     *
     * @return {@code true} if will execute after shutdown
     * @see #setExecuteExistingDelayedTasksAfterShutdownPolicy
     */
    public boolean getExecuteExistingDelayedTasksAfterShutdownPolicy() {
        return executeExistingDelayedTasksAfterShutdown;
    }

    /**
     * Sets the policy on whether cancelled tasks should be immediately
     * removed from the work queue at time of cancellation.  This value is
     * by default {@code false}.
     *
     * @param value if {@code true}, remove on cancellation, else don't
     * @see #getRemoveOnCancelPolicy
     * @since 1.7
     */
    public void setRemoveOnCancelPolicy(boolean value) {
        removeOnCancel = value;
    }

    /**
     * Gets the policy on whether cancelled tasks should be immediately
     * removed from the work queue at time of cancellation.  This value is
     * by default {@code false}.
     *
     * @return {@code true} if cancelled tasks are immediately removed
     *         from the queue
     * @see #setRemoveOnCancelPolicy
     * @since 1.7
     */
    public boolean getRemoveOnCancelPolicy() {
        return removeOnCancel;
    }

    /**
     * Initiates an orderly shutdown in which previously submitted
     * tasks are executed, but no new tasks will be accepted.
     * Invocation has no additional effect if already shut down.
     *
     * <p>This method does not wait for previously submitted tasks to
     * complete execution.  Use {@link #awaitTermination awaitTermination}
     * to do that.
     *
     * <p>If the {@code ExecuteExistingDelayedTasksAfterShutdownPolicy}
     * has been set {@code false}, existing delayed tasks whose delays
     * have not yet elapsed are cancelled.  And unless the {@code
     * ContinueExistingPeriodicTasksAfterShutdownPolicy} has been set
     * {@code true}, future executions of existing periodic tasks will
     * be cancelled.
     *
     * @throws SecurityException {@inheritDoc}
     */
    public void shutdown() {
        super.shutdown();
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
     * @return list of tasks that never commenced execution.
     *         Each element of this list is a {@link ScheduledFuture}.
     *         For tasks submitted via one of the {@code schedule}
     *         methods, the element will be identical to the returned
     *         {@code ScheduledFuture}.  For tasks submitted using
     *         {@link #execute execute}, the element will be a
     *         zero-delay {@code ScheduledFuture}.
     * @throws SecurityException {@inheritDoc}
     */
    public List<Runnable> shutdownNow() {
        return super.shutdownNow();
    }

    /**
     * Returns the task queue used by this executor.  Access to the
     * task queue is intended primarily for debugging and monitoring.
     * This queue may be in active use.  Retrieving the task queue
     * does not prevent queued tasks from executing.
     *
     * <p>Each element of this queue is a {@link ScheduledFuture}.
     * For tasks submitted via one of the {@code schedule} methods, the
     * element will be identical to the returned {@code ScheduledFuture}.
     * For tasks submitted using {@link #execute execute}, the element
     * will be a zero-delay {@code ScheduledFuture}.
     *
     * <p>Iteration over this queue is <em>not</em> guaranteed to traverse
     * tasks in the order in which they will execute.
     *
     * @return the task queue
     */
    public BlockingQueue<Runnable> getQueue() {
        return super.getQueue();
    }

    /**
     * 默认使用的阻塞队列,
     * 延时工作队列
     * <p>
     * Specialized delay queue. To mesh with TPE declarations, this
     * class must be declared as a BlockingQueue<Runnable> even though
     * it can only hold RunnableScheduledFutures.
     */
    static class DelayedWorkQueue extends AbstractQueue<Runnable>
        implements BlockingQueue<Runnable> {

        /*
         * 基于堆，类似于 DelayQueue 和 PriorityQueue。
         *
         * A DelayedWorkQueue is based on a heap-based data structure
         * like those in DelayQueue and PriorityQueue, except that
         * every ScheduledFutureTask also records its index into the
         * heap array. This eliminates the need to find a task upon
         * cancellation, greatly speeding up removal (down from O(n)
         * to O(log n)), and reducing garbage retention that would
         * otherwise occur by waiting for the element to rise to top
         * before clearing. But because the queue may also hold
         * RunnableScheduledFutures that are not ScheduledFutureTasks,
         * we are not guaranteed to have such indices available, in
         * which case we fall back to linear search. (We expect that
         * most tasks will not be decorated, and that the faster cases
         * will be much more common.)
         *
         * All heap operations must record index changes -- mainly
         * within siftUp and siftDown. Upon removal, a task's
         * heapIndex is set to -1. Note that ScheduledFutureTasks can
         * appear at most once in the queue (this need not be true for
         * other kinds of tasks or work queues), so are uniquely
         * identified by heapIndex.
         */

        /**
         * 初始容量为 16
         */
        private static final int INITIAL_CAPACITY = 16;
        /**
         * 存储任务的数组
         */
        private RunnableScheduledFuture<?>[] queue =
            new RunnableScheduledFuture<?>[INITIAL_CAPACITY];
        /**
         * 锁
         */
        private final ReentrantLock lock = new ReentrantLock();
        /**
         * 数组中的任务数
         */
        private int size;

        /**
         * 尝试获取队头元素的线程
         * <p>
         * Thread designated to wait for the task at the head of the
         * queue.  This variant of the Leader-Follower pattern
         * (http://www.cs.wustl.edu/~schmidt/POSA/POSA2/) serves to
         * minimize unnecessary timed waiting.  When a thread becomes
         * the leader, it waits only for the next delay to elapse, but
         * other threads await indefinitely.  The leader thread must
         * signal some other thread before returning from take() or
         * poll(...), unless some other thread becomes leader in the
         * interim.  Whenever the head of the queue is replaced with a
         * task with an earlier expiration time, the leader field is
         * invalidated by being reset to null, and some waiting
         * thread, but not necessarily the current leader, is
         * signalled.  So waiting threads must be prepared to acquire
         * and lose leadership while waiting.
         */
        private Thread leader;

        /**
         * Condition signalled when a newer task becomes available at the
         * head of the queue or a new thread may need to become leader.
         */
        private final Condition available = lock.newCondition();

        /**
         * 设置 RunnableScheduledFuture 在堆中的 id,
         * -1 表示已经移出了堆
         * <p>
         * Sets f's heapIndex if it is a ScheduledFutureTask.
         */
        private static void setIndex(RunnableScheduledFuture<?> f, int idx) {
            if (f instanceof ScheduledFutureTask)
                ((ScheduledFutureTask)f).heapIndex = idx;
        }

        /**
         * 堆维护中的向上调整
         * <p>
         * Sifts element added at bottom up to its heap-ordered spot.
         * Call only when holding lock.
         */
        private void siftUp(int k, RunnableScheduledFuture<?> key) {
            while (k > 0) {
                int parent = (k - 1) >>> 1;
                RunnableScheduledFuture<?> e = queue[parent];
                if (key.compareTo(e) >= 0)
                    break;
                queue[k] = e;
                setIndex(e, k);
                k = parent;
            }
            queue[k] = key;
            setIndex(key, k);
        }

        /**
         * 堆维护中的向下调整
         * <p>
         * Sifts element added at top down to its heap-ordered spot.
         * Call only when holding lock.
         */
        private void siftDown(int k, RunnableScheduledFuture<?> key) {
            int half = size >>> 1;
            while (k < half) {
                int child = (k << 1) + 1;
                RunnableScheduledFuture<?> c = queue[child];
                int right = child + 1;
                if (right < size && c.compareTo(queue[right]) > 0)
                    c = queue[child = right];
                if (key.compareTo(c) <= 0)
                    break;
                queue[k] = c;
                setIndex(c, k);
                k = child;
            }
            queue[k] = key;
            setIndex(key, k);
        }

        /**
         * 堆数组扩容
         * <p>
         * Resizes the heap array.  Call only when holding lock.
         */
        private void grow() {
            int oldCapacity = queue.length;
            // 扩容为原来的 1.5
            int newCapacity = oldCapacity + (oldCapacity >> 1); // grow 50%
            if (newCapacity < 0) // overflow
                newCapacity = Integer.MAX_VALUE;
            queue = Arrays.copyOf(queue, newCapacity);
        }

        /**
         * Finds index of given object, or -1 if absent.
         */
        private int indexOf(Object x) {
            if (x != null) {
                // 快速地寻找到相应的位置
                if (x instanceof ScheduledFutureTask) {
                    int i = ((ScheduledFutureTask) x).heapIndex;
                    // Sanity check; x could conceivably be a
                    // ScheduledFutureTask from some other pool.
                    if (i >= 0 && i < size && queue[i] == x)
                        return i;
                    // 遍历数组查找
                } else {
                    for (int i = 0; i < size; i++)
                        if (x.equals(queue[i]))
                            return i;
                }
            }
            // 找不到
            return -1;
        }

        /**
         * 返回是否包含某个对象
         */
        public boolean contains(Object x) {
            final ReentrantLock lock = this.lock;
            lock.lock();
            try {
                return indexOf(x) != -1;
            } finally {
                lock.unlock();
            }
        }

        /**
         * 在堆中删除某个对象
         */
        public boolean remove(Object x) {
            final ReentrantLock lock = this.lock;
            lock.lock();
            try {
                int i = indexOf(x);
                if (i < 0)
                    return false;

                // 设置为 -1，表示移出堆
                setIndex(queue[i], -1);
                int s = --size;
                // 堆中的最后一个元素
                RunnableScheduledFuture<?> replacement = queue[s];
                queue[s] = null;
                // 调整堆
                if (s != i) {
                    siftDown(i, replacement);
                    if (queue[i] == replacement)
                        siftUp(i, replacement);
                }
                return true;
            } finally {
                lock.unlock();
            }
        }

        public int size() {
            final ReentrantLock lock = this.lock;
            lock.lock();
            try {
                return size;
            } finally {
                lock.unlock();
            }
        }

        public boolean isEmpty() {
            return size() == 0;
        }

        /**
         * 说明是一个无界的队列
         */
        public int remainingCapacity() {
            return Integer.MAX_VALUE;
        }

        public RunnableScheduledFuture<?> peek() {
            final ReentrantLock lock = this.lock;
            lock.lock();
            try {
                return queue[0];
            } finally {
                lock.unlock();
            }
        }

        /**
         * 入队
         */
        public boolean offer(Runnable x) {
            if (x == null)
                throw new NullPointerException();
            RunnableScheduledFuture<?> e = (RunnableScheduledFuture<?>)x;
            final ReentrantLock lock = this.lock;
            lock.lock();
            try {
                int i = size;
                if (i >= queue.length)
                    grow();
                size = i + 1;
                // 堆为空则直接放入第一个
                if (i == 0) {
                    // 放入对数组的第一个
                    queue[0] = e;
                    setIndex(e, 0);
                } else {
                    // 放到数组的最后一个，再进行调整
                    siftUp(i, e);
                }
                // 如果成为了新的堆头
                // 更换了新的队头，需要唤醒下一个等待的线程
                if (queue[0] == e) {
                    leader = null;
                    available.signal();
                }
            } finally {
                lock.unlock();
            }
            return true;
        }

        /**
         * 调用了 offer 方法，因为 DelayedWorkQueue 是无界队列
         */
        public void put(Runnable e) {
            offer(e);
        }

        /**
         * 调用了 offer 方法，因为 DelayedWorkQueue 是无界队列
         */
        public boolean add(Runnable e) {
            return offer(e);
        }

        /**
         * 不支持超时入队
         */
        public boolean offer(Runnable e, long timeout, TimeUnit unit) {
            return offer(e);
        }

        /**
         * 出队之后的堆调整
         * <p>
         * Performs common bookkeeping for poll and take: Replaces
         * first element with last and sifts it down.  Call only when
         * holding lock.
         * @param f the task to remove and return
         */
        private RunnableScheduledFuture<?> finishPoll(RunnableScheduledFuture<?> f) {
            int s = --size;
            // 获取最后一个元素
            RunnableScheduledFuture<?> x = queue[s];
            queue[s] = null;
            if (s != 0)
                // 放到队头之后进行调整
                siftDown(0, x);
            setIndex(f, -1);
            return f;
        }

        /**
         * poll 队头出队
         */
        public RunnableScheduledFuture<?> poll() {
            final ReentrantLock lock = this.lock;
            lock.lock();
            try {
                RunnableScheduledFuture<?> first = queue[0];
                // 如果当前队列中的元素还未走完设置的延迟时间
                // 返回 null
                return (first == null || first.getDelay(NANOSECONDS) > 0)
                    ? null
                        // 出队之后的操作，是一个堆调整
                    : finishPoll(first);
            } finally {
                lock.unlock();
            }
        }

        /**
         * take 队头出队
         */
        public RunnableScheduledFuture<?> take() throws InterruptedException {
            final ReentrantLock lock = this.lock;
            lock.lockInterruptibly();
            try {
                // 自旋
                for (;;) {
                    RunnableScheduledFuture<?> first = queue[0];
                    // 队头为空，则阻塞等待
                    if (first == null)
                        // 等待
                        available.await();
                    else {
                        // 获取 delay 时间
                        long delay = first.getDelay(NANOSECONDS);
                        // 延迟检查 ok
                        if (delay <= 0L)
                            // 直接调用 finishPoll 进行出队
                            return finishPoll(first);
                        // delay > 0，需要继续等待
                        // 在等待的不必保持引用
                        first = null; // don't retain ref while waiting
                        // 如果有其他线程已经在尝试获取队头元素了
                        if (leader != null)
                            // 当前线程则阻塞等待
                            available.await();
                        else {
                            // 还没有其他线程在等待队头元素
                            // 将 leader 设置为当前线程
                            Thread thisThread = Thread.currentThread();
                            leader = thisThread;
                            try {
                                // 阻塞 delay 时间
                                available.awaitNanos(delay);
                            } finally {
                                if (leader == thisThread)
                                    // 阻塞醒来之后将 leader 设置为 null
                                    // 自旋尝试再次获取锁
                                    leader = null;
                            }
                        }
                    }
                }
            } finally {
                if (leader == null && queue[0] != null)
                    available.signal();
                lock.unlock();
            }
        }

        /**
         * 出队
         */
        public RunnableScheduledFuture<?> poll(long timeout, TimeUnit unit)
            throws InterruptedException {
            long nanos = unit.toNanos(timeout);
            final ReentrantLock lock = this.lock;
            lock.lockInterruptibly();
            try {
                for (;;) {
                    // 队头元素
                    RunnableScheduledFuture<?> first = queue[0];
                    if (first == null) {
                        // 已经超时
                        if (nanos <= 0L)
                            return null;
                        else
                            // poll 阻塞等待
                            nanos = available.awaitNanos(nanos);
                        // 队头不为 null
                    } else {
                        // 返回距离执行时间的剩余时间
                        long delay = first.getDelay(NANOSECONDS);
                        // 时间已经到了
                        if (delay <= 0L)
                            // 返回出队元素
                            return finishPoll(first);
                        // poll 操作未设置阻塞等待，则直接返回 null
                        if (nanos <= 0L)
                            return null;
                        // 还未到时间
                        first = null; // don't retain ref while waiting
                        // poll 的阻塞等待时间少于任务开始时间
                        // 或者 当前有其他线程在尝试获取队头元素且 leader 指向了那个线程
                        if (nanos < delay || leader != null)
                            // 阻塞等待
                            nanos = available.awaitNanos(nanos);
                        else {
                            Thread thisThread = Thread.currentThread();
                            // 当前线程占有 leader
                            leader = thisThread;
                            try {
                                // 阻塞距离任务开始剩余的时间
                                long timeLeft = available.awaitNanos(delay);
                                // 计算剩余的 poll 获取时间
                                nanos -= delay - timeLeft;
                            } finally {
                                if (leader == thisThread)
                                    // 释放 leader
                                    leader = null;
                            }
                        }
                    }
                }
            } finally {
                // 需要唤醒在当前条件等待的线程
                if (leader == null && queue[0] != null)
                    available.signal();
                lock.unlock();
            }
        }

        /**
         * 清空队列的元素
         */
        public void clear() {
            final ReentrantLock lock = this.lock;
            lock.lock();
            try {
                // 遍历数组
                for (int i = 0; i < size; i++) {
                    RunnableScheduledFuture<?> t = queue[i];
                    if (t != null) {
                        queue[i] = null;
                        setIndex(t, -1);
                    }
                }
                size = 0;
            } finally {
                lock.unlock();
            }
        }

        public int drainTo(Collection<? super Runnable> c) {
            return drainTo(c, Integer.MAX_VALUE);
        }

        /**
         * 将所有元素保存到 Collection 中
         */
        public int drainTo(Collection<? super Runnable> c, int maxElements) {
            Objects.requireNonNull(c);
            if (c == this)
                throw new IllegalArgumentException();
            if (maxElements <= 0)
                return 0;
            final ReentrantLock lock = this.lock;
            lock.lock();
            try {
                int n = 0;
                for (RunnableScheduledFuture<?> first;
                     n < maxElements
                         && (first = queue[0]) != null
                         && first.getDelay(NANOSECONDS) <= 0;) {
                    // 先添加到 collection 中
                    c.add(first);   // In this order, in case add() throws.
                    // 再 poll
                    finishPoll(first);
                    ++n;
                }
                return n;
            } finally {
                lock.unlock();
            }
        }

        public Object[] toArray() {
            final ReentrantLock lock = this.lock;
            lock.lock();
            try {
                return Arrays.copyOf(queue, size, Object[].class);
            } finally {
                lock.unlock();
            }
        }

        @SuppressWarnings("unchecked")
        public <T> T[] toArray(T[] a) {
            final ReentrantLock lock = this.lock;
            lock.lock();
            try {
                if (a.length < size)
                    return (T[]) Arrays.copyOf(queue, size, a.getClass());
                System.arraycopy(queue, 0, a, 0, size);
                if (a.length > size)
                    a[size] = null;
                return a;
            } finally {
                lock.unlock();
            }
        }

        public Iterator<Runnable> iterator() {
            final ReentrantLock lock = this.lock;
            lock.lock();
            try {
                return new Itr(Arrays.copyOf(queue, size));
            } finally {
                lock.unlock();
            }
        }

        /**
         * Snapshot iterator that works off copy of underlying q array.
         */
        private class Itr implements Iterator<Runnable> {
            final RunnableScheduledFuture<?>[] array;
            /**
             * 下一个读取的元素的下标
             */
            int cursor;        // index of next element to return; initially 0
            /**
             * 最近一个读取的元素的下标
             */
            int lastRet = -1;  // index of last element returned; -1 if no such

            Itr(RunnableScheduledFuture<?>[] array) {
                this.array = array;
            }

            public boolean hasNext() {
                return cursor < array.length;
            }

            public Runnable next() {
                if (cursor >= array.length)
                    throw new NoSuchElementException();
                return array[lastRet = cursor++];
            }

            public void remove() {
                if (lastRet < 0)
                    throw new IllegalStateException();
                DelayedWorkQueue.this.remove(array[lastRet]);
                lastRet = -1;
            }
        }
    }
}
