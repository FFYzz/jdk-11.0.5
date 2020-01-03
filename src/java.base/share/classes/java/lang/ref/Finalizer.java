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

import java.security.PrivilegedAction;
import java.security.AccessController;
import jdk.internal.misc.JavaLangAccess;
import jdk.internal.misc.SharedSecrets;
import jdk.internal.misc.VM;

/**
 * 有必要执行 finalize() 方法的对象会被放入到 F-Queue 中
 * 由该类中的 FinalizerThread 从队列中取出元素，然后执行 finalize() 方法
 */
final class Finalizer extends FinalReference<Object> { /* Package-private; must be in
                                                          same package as the Reference
                                                          class */

    /**
     * Finalizer 关联的 ReferenceQueue，其实 Finalizer 是一个特殊的 Reference 实现
     */
    private static ReferenceQueue<Object> queue = new ReferenceQueue<>();

    /**
     * 等待 finalization 的所有 Finalizer 实例链表的头节点，这里称此链表为 unfinalized 链表
     * Head of doubly linked list of Finalizers awaiting finalization.
     */
    private static Finalizer unfinalized = null;

    /**
     * 锁对象
     * Lock guarding access to unfinalized list.
     */
    private static final Object lock = new Object();

    private Finalizer next, prev;

    /**
     * 私有的构造方法
     *
     * @param finalizee
     */
    private Finalizer(Object finalizee) {
        super(finalizee, queue);
        // push onto unfinalized
        synchronized (lock) {
            if (unfinalized != null) {
                this.next = unfinalized;
                unfinalized.prev = this;
            }
            unfinalized = this;
        }
    }

    static ReferenceQueue<Object> getQueue() {
        return queue;
    }

    /**
     * Invoked by VM
     * 由 JVM 调用
     */
    static void register(Object finalizee) {
        new Finalizer(finalizee);
    }

    private void runFinalizer(JavaLangAccess jla) {
        synchronized (lock) {
            if (this.next == this)      // already finalized
                return;
            // unlink from unfinalized
            if (unfinalized == this)
                unfinalized = this.next;
            else
                this.prev.next = this.next;
            if (this.next != null)
                this.next.prev = this.prev;
            this.prev = null;
            this.next = this;           // mark as finalized
        }

        try {
            Object finalizee = this.get();
            if (finalizee != null && !(finalizee instanceof java.lang.Enum)) {
                // 调用 finalize() 方法
                jla.invokeFinalize(finalizee);

                // Clear stack slot containing this variable, to decrease
                // the chances of false retention with a conservative GC
                finalizee = null;
            }
        } catch (Throwable x) {
        }
        super.clear();
    }

    /**
     * Create a privileged secondary finalizer thread in the system thread
     * group for the given Runnable, and wait for it to complete.
     * <p>
     * <p>
     * 创建一个第二优先级的 finalizer 线程，执行 runFinalizer() 方法
     * This method is used by runFinalization.
     * <p>
     * It could have been implemented by offloading the work to the
     * regular finalizer thread and waiting for that thread to finish.
     * The advantage of creating a fresh thread, however, is that it insulates
     * invokers of that method from a stalled or deadlocked finalizer thread.
     */
    private static void forkSecondaryFinalizer(final Runnable proc) {
        AccessController.doPrivileged(
                new PrivilegedAction<>() {
                    public Void run() {
                        ThreadGroup tg = Thread.currentThread().getThreadGroup();
                        for (ThreadGroup tgn = tg;
                             tgn != null;
                             tg = tgn, tgn = tg.getParent())
                            ;
                        Thread sft = new Thread(tg, proc, "Secondary finalizer", 0, false);
                        sft.start();
                        try {
                            sft.join();
                        } catch (InterruptedException x) {
                            Thread.currentThread().interrupt();
                        }
                        return null;
                    }
                });
    }

    /**
     * Called by Runtime.runFinalization()
     * 该方法是给 Runtime.runFinalization() 委托调用的
     * 就是我们在主动从 queue 中取出元素，调用其 finalize() 方法
     */
    static void runFinalization() {
        if (VM.initLevel() == 0) {
            return;
        }
        // 新建一个线程，执行 runFinalizer() 方法
        forkSecondaryFinalizer(new Runnable() {
            private volatile boolean running;

            public void run() {
                // in case of recursive call to run()
                if (running)
                    return;
                final JavaLangAccess jla = SharedSecrets.getJavaLangAccess();
                running = true;
                for (Finalizer f; (f = (Finalizer) queue.poll()) != null; )
                    f.runFinalizer(jla);
            }
        });
    }

    /**
     * FinalizerThread 线程
     */
    private static class FinalizerThread extends Thread {
        private volatile boolean running;

        FinalizerThread(ThreadGroup g) {
            super(g, null, "Finalizer", 0, false);
        }

        public void run() {
            // in case of recursive call to run()
            // 防止递归调用 run 方法
            if (running)
                return;

            // Finalizer thread starts before System.initializeSystemClass
            // is called.  Wait until JavaLangAccess is available
            // 等待 VM 初始化完成
            while (VM.initLevel() == 0) {
                // delay until VM completes initialization
                try {
                    VM.awaitInitLevel(1);
                } catch (InterruptedException x) {
                    // ignore and continue
                }
            }
            final JavaLangAccess jla = SharedSecrets.getJavaLangAccess();
            running = true;
            // 死循环
            for (; ; ) {
                try {
                    // 调用 ReferenceQueue 的 remove 方法
                    Finalizer f = (Finalizer) queue.remove();
                    // 实际上就是去执行 finalize() 方法
                    f.runFinalizer(jla);
                } catch (InterruptedException x) {
                    // ignore and continue
                }
            }
        }
    }

    /**
     * 静态代码块
     * 在 JVM 启动的时候会运行该段代码
     */
    static {
        ThreadGroup tg = Thread.currentThread().getThreadGroup();
        for (ThreadGroup tgn = tg;
             tgn != null;
             tg = tgn, tgn = tg.getParent())
            ;

        Thread finalizer = new FinalizerThread(tg);
        // 设置较高优先级
        finalizer.setPriority(Thread.MAX_PRIORITY - 2);
        // 设置为 daemon 线程
        finalizer.setDaemon(true);
        // 启动线程
        finalizer.start();
    }

}
