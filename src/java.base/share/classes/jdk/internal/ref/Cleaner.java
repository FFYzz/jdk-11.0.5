/*
 * Copyright (c) 2003, 2013, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.ref;

import java.security.AccessController;
import java.security.PrivilegedAction;


/**
 * General-purpose phantom-reference-based cleaners.
 *
 * <p> Cleaners are a lightweight and more robust alternative to finalization.
 * They are lightweight because they are not created by the VM and thus do not
 * require a JNI upcall to be created, and because their cleanup code is
 * invoked directly by the reference-handler thread rather than by the
 * finalizer thread.  They are more robust because they use phantom references,
 * the weakest type of reference object, thereby avoiding the nasty ordering
 * problems inherent to finalization.
 *
 * <p> A cleaner tracks a referent object and encapsulates a thunk of arbitrary
 * cleanup code.  Some time after the GC detects that a cleaner's referent has
 * become phantom-reachable, the reference-handler thread will run the cleaner.
 * Cleaners may also be invoked directly; they are thread safe and ensure that
 * they run their thunks at most once.
 *
 * <p> Cleaners are not a replacement for finalization.  They should be used
 * only when the cleanup code is extremely simple and straightforward.
 * Nontrivial cleaners are inadvisable since they risk blocking the
 * reference-handler thread and delaying further cleanup and finalization.
 *
 * @author Mark Reinhold
 */

/**
 * 虚引用的使用实例
 * 是 PhantomReference 的子类
 * 用于回收堆外内存
 */
public class Cleaner
        extends PhantomReference<Object> {

    // Dummy reference queue, needed because the PhantomReference constructor
    // insists that we pass a queue.  Nothing will ever be placed on this queue
    // since the reference handler invokes cleaners explicitly.
    //
    private static final ReferenceQueue<Object> dummyQueue = new ReferenceQueue<>();

    // Doubly-linked list of live cleaners, which prevents the cleaners
    // themselves from being GC'd before their referents
    /**
     * Cleaner链表的头结点
     */
    private static Cleaner first = null;
    /**
     * 当前节点的后续节点
     */
    private Cleaner next = null,
    /**
     * 当前节点的前驱节点
     */
    prev = null;

    /**
     * 添加 Cleaner 实例
     * 头插法，双向链表
     *
     * @param cl
     * @return
     */
    private static synchronized Cleaner add(Cleaner cl) {
        // 头插法
        if (first != null) {
            cl.next = first;
            first.prev = cl;
        }
        first = cl;
        return cl;
    }

    /**
     * 移除 Cleaner 实例
     * 操作双向链表
     *
     * @param cl
     * @return
     */
    private static synchronized boolean remove(Cleaner cl) {

        // If already removed, do nothing
        if (cl.next == cl)
            return false;

        // Update list
        if (first == cl) {
            if (cl.next != null)
                first = cl.next;
            else
                first = cl.prev;
        }
        if (cl.next != null)
            cl.next.prev = cl.prev;
        if (cl.prev != null)
            cl.prev.next = cl.next;

        // Indicate removal by pointing the cleaner to itself
        cl.next = cl;
        cl.prev = cl;
        return true;

    }

    /**
     * 真正执行清理工作的 Runnable 对象，实际clean内部调用thunk.run()方法
     */
    private final Runnable thunk;

    /**
     * 私有构造方法
     *
     * @param referent
     * @param thunk
     */
    private Cleaner(Object referent, Runnable thunk) {
        super(referent, dummyQueue);
        this.thunk = thunk;
    }

    /**
     * 调用 add 方法
     * Creates a new cleaner.
     *
     * @param ob    the referent object to be cleaned
     * @param thunk The cleanup code to be run when the cleaner is invoked.  The
     *              cleanup code is run directly from the reference-handler thread,
     *              so it should be as simple and straightforward as possible.
     * @return The new cleaner
     */
    public static Cleaner create(Object ob, Runnable thunk) {
        if (thunk == null)
            return null;
        return add(new Cleaner(ob, thunk));
    }

    /**
     * 删除当前 Cleaner 实例
     * Runs this cleaner, if it has not been run before.
     */
    public void clean() {
        // 先从链表中移除当前 Cleaner
        if (!remove(this))
            return;
        try {
            // 执行清理逻辑
            thunk.run();
        } catch (final Throwable x) {
            AccessController.doPrivileged(new PrivilegedAction<>() {
                public Void run() {
                    if (System.err != null)
                        new Error("Cleaner terminated abnormally", x)
                                .printStackTrace();
                    System.exit(1);
                    return null;
                }
            });
        }
    }
}
