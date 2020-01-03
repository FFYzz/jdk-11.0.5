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

import jdk.internal.misc.VM;

import java.util.function.Consumer;

/**
 * Reference queues, to which registered reference objects are appended by the
 * garbage collector after the appropriate reachability changes are detected.
 * <p>
 * 引用队列，垃圾收集器在检测到适当的可达性更改后将已注册的引用对象追加到该队列。
 *
 * @author Mark Reinhold
 * @since 1.2
 */

public class ReferenceQueue<T> {

    /**
     * Constructs a new reference-object queue.
     */
    public ReferenceQueue() {
    }

    /**
     * 内部类 Null 类，继承了 ReferenceQueue，不能入队。
     */
    private static class Null extends ReferenceQueue<Object> {
        // 入队方法直接返回 false
        boolean enqueue(Reference<?> r) {
            return false;
        }
    }

    /**
     * Null 类的实例 NULL
     */
    static final ReferenceQueue<Object> NULL = new Null();
    /**
     * Null 类的实例 ENQUEUED
     */
    static final ReferenceQueue<Object> ENQUEUED = new Null();

    /**
     * 内部静态类
     */
    private static class Lock {
    }

    ;

    /**
     * 对象锁
     */
    private final Lock lock = new Lock();
    /**
     * Reference 对象其实是一个单链表
     * 持有的 Reference 单链表的头结点
     */
    private volatile Reference<? extends T> head;
    /**
     * 记录队列的长度
     */
    private long queueLength = 0;

    /**
     * 入队方法
     *
     * @param r
     * @return
     */
    boolean enqueue(Reference<? extends T> r) { /* Called only by Reference class */
        synchronized (lock) {
            // Check that since getting the lock this reference hasn't already been
            // enqueued (and even then removed)
            // 获取到 Reference 对象持有的 ReferenceQueue
            ReferenceQueue<?> queue = r.queue;
            // 如果引用实例持有的队列是 NULL 队列或者 ENQUEUED 队列，则直接返回 false
            if ((queue == NULL) || (queue == ENQUEUED)) {
                return false;
            }
            assert queue == this;
            // Self-loop end, so if a FinalReference it remains inactive.
            // 如果 Reference 链表没有元素，则此引用实例直接作为头节点，否则把前一个引用实例作为下一个节点
            r.next = (head == null) ? r : head;
            head = r;
            queueLength++;
            // Update r.queue *after* adding to list, to avoid race
            // with concurrent enqueued checks and fast-path poll().
            // Volatiles ensure ordering.
            // 当前引用实例已经入队，那么它本身持有的引用队列实例置为ReferenceQueue.ENQUEUED
            r.queue = ENQUEUED;
            // 特殊处理 FinalReference ，VM 进行计数
            if (r instanceof FinalReference) {
                VM.addFinalRefCount(1);
            }
            // 唤醒所有等待的线程
            lock.notifyAll();
            return true;
        }
    }

    /**
     * 引用队列的 poll 操作，此方法必须在加锁情况下调用
     * 出队
     *
     * @return
     */
    private Reference<? extends T> reallyPoll() {       /* Must hold lock */
        Reference<? extends T> r = head;
        // 如果头结点不为空
        if (r != null) {
            // 出队之后更新当前的 Reference 实例的队列为 NULL 队列
            r.queue = NULL;
            // Update r.queue *before* removing from list, to avoid
            // race with concurrent enqueued checks and fast-path
            // poll().  Volatiles ensure ordering.
            @SuppressWarnings("unchecked")
            Reference<? extends T> rn = r.next;
            // Handle self-looped next as end of list designator.
            // 更新 next 节点为头节点，如果 next 节点为自身，说明已经走过一次出队，则返回 null
            head = (rn == r) ? null : rn;
            // Self-loop next rather than setting to null, so if a
            // FinalReference it remains inactive.
            r.next = r;
            queueLength--;
            if (r instanceof FinalReference) {
                VM.addFinalRefCount(-1);
            }
            return r;
        }
        return null;
    }

    /**
     * Polls this queue to see if a reference object is available.  If one is
     * available without further delay then it is removed from the queue and
     * returned.  Otherwise this method immediately returns {@code null}.
     *
     * @return A reference object, if one was immediately available,
     * otherwise {@code null}
     */
    public Reference<? extends T> poll() {
        if (head == null)
            return null;
        synchronized (lock) {
            return reallyPoll();
        }
    }

    /**
     * 移除引用队列中的下一个引用元素
     * <p>
     * Removes the next reference object in this queue, blocking until either
     * one becomes available or the given timeout period expires.
     *
     * <p> This method does not offer real-time guarantees: It schedules the
     * timeout as if by invoking the {@link Object#wait(long)} method.
     *
     * @param timeout If positive, block for up to {@code timeout}
     *                milliseconds while waiting for a reference to be
     *                added to this queue.  If zero, block indefinitely.
     * @return A reference object, if one was available within the specified
     * timeout period, otherwise {@code null}
     * @throws IllegalArgumentException If the value of the timeout argument is negative
     * @throws InterruptedException     If the timeout wait is interrupted
     */
    public Reference<? extends T> remove(long timeout)
            throws IllegalArgumentException, InterruptedException {
        if (timeout < 0) {
            throw new IllegalArgumentException("Negative timeout value");
        }
        synchronized (lock) {
            Reference<? extends T> r = reallyPoll();
            if (r != null) return r;
            long start = (timeout == 0) ? 0 : System.nanoTime();
            for (; ; ) {
                lock.wait(timeout);
                r = reallyPoll();
                if (r != null) return r;
                if (timeout != 0) {
                    long end = System.nanoTime();
                    timeout -= (end - start) / 1000_000;
                    if (timeout <= 0) return null;
                    start = end;
                }
            }
        }
    }

    /**
     * Removes the next reference object in this queue, blocking until one
     * becomes available.
     *
     * @return A reference object, blocking until one becomes available
     * @throws InterruptedException If the wait is interrupted
     */
    public Reference<? extends T> remove() throws InterruptedException {
        return remove(0);
    }

    /**
     * Iterate queue and invoke given action with each Reference.
     * Suitable for diagnostic purposes.
     * WARNING: any use of this method should make sure to not
     * retain the referents of iterated references (in case of
     * FinalReference(s)) so that their life is not prolonged more
     * than necessary.
     */
    void forEach(Consumer<? super Reference<? extends T>> action) {
        for (Reference<? extends T> r = head; r != null; ) {
            action.accept(r);
            @SuppressWarnings("unchecked")
            Reference<? extends T> rn = r.next;
            if (rn == r) {
                if (r.queue == ENQUEUED) {
                    // still enqueued -> we reached end of chain
                    r = null;
                } else {
                    // already dequeued: r.queue == NULL; ->
                    // restart from head when overtaken by queue poller(s)
                    r = head;
                }
            } else {
                // next in chain
                r = rn;
            }
        }
    }
}
