/*
 * Copyright (c) 2000, 2018, Oracle and/or its affiliates. All rights reserved.
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
 */

package java.nio.channels.spi;

import java.io.IOException;
import java.nio.channels.*;
import jdk.internal.misc.SharedSecrets;
import sun.nio.ch.Interruptible;


/**
 * Base implementation class for interruptible channels.
 * <p> interruptibleChannel 的抽象实现，提供了一些抽象实现的方法。
 *
 * <p> This class encapsulates the low-level machinery required to implement
 * the asynchronous closing and interruption of channels.  A concrete channel
 * class must invoke the {@link #begin begin} and {@link #end end} methods
 * before and after, respectively, invoking an I/O operation that might block
 * indefinitely.  In order to ensure that the {@link #end end} method is always
 * invoked, these methods should be used within a
 * {@code try}&nbsp;...&nbsp;{@code finally} block:
 * <p> 当前抽象类封装了底层的方法，用以实现异步关闭以及中断 channel。继承自该抽象类的 channel
 * 在执行 blocking 操作的前后，需要分别调用 begin 和 end 方法。为了保证 end 方法 一定能够被
 * 调用到，一般把 end 方法放在 finally 块中。
 *
 * <blockquote><pre id="be">
 * boolean completed = false;
 * try {
 *     begin();
 *     completed = ...;    // Perform blocking I/O operation
 *     return ...;         // Return result
 * } finally {
 *     end(completed);
 * }</pre></blockquote>
 *
 * <p> The {@code completed} argument to the {@link #end end} method tells
 * whether or not the I/O operation actually completed, that is, whether it had
 * any effect that would be visible to the invoker.  In the case of an
 * operation that reads bytes, for example, this argument should be
 * {@code true} if, and only if, some bytes were actually transferred into the
 * invoker's target buffer.
 * <p>
 *     end 方法的参数 completed，指明 channel 是否已经完成。
 *
 * <p> A concrete channel class must also implement the {@link
 * #implCloseChannel implCloseChannel} method in such a way that if it is
 * invoked while another thread is blocked in a native I/O operation upon the
 * channel then that operation will immediately return, either by throwing an
 * exception or by returning normally.  If a thread is interrupted or the
 * channel upon which it is blocked is asynchronously closed then the channel's
 * {@link #end end} method will throw the appropriate exception.
 * <p>
 *     一个具体的 channel 也必须实现 implCloseChannel 方法。这样的话，当该方法被调用的时候，
 *     另一个线程阻塞在该 channel 读取 native IO 上。那个调用该方法的线程会立刻返回，要么抛出一个
 *     异常，要么正常返回。如果线程被中断，或者 channel 被异步关闭，那么 channel 的 end 方法会
 *     抛出一个异常。
 *
 * <p> This class performs the synchronization required to implement the {@link
 * java.nio.channels.Channel} specification.  Implementations of the {@link
 * #implCloseChannel implCloseChannel} method need not synchronize against
 * other threads that might be attempting to close the channel.  </p>
 *
 *
 * @author Mark Reinhold
 * @author JSR-51 Expert Group
 * @since 1.4
 */

public abstract class AbstractInterruptibleChannel
    implements Channel, InterruptibleChannel
{

    /**
     * 锁对象
     */
    private final Object closeLock = new Object();
    /**
     * 当前 channel 是否被关闭
     */
    private volatile boolean closed;

    /**
     * Initializes a new instance of this class.
     */
    protected AbstractInterruptibleChannel() { }

    /**
     * Closes this channel.
     *
     * <p> If the channel has already been closed then this method returns
     * immediately.  Otherwise it marks the channel as closed and then invokes
     * the {@link #implCloseChannel implCloseChannel} method in order to
     * complete the close operation.  </p>
     *
     * @throws  IOException
     *          If an I/O error occurs
     */
    public final void close() throws IOException {
        // 加锁操作
        synchronized (closeLock) {
            // 多次调用无感
            if (closed)
                return;
            closed = true;
            // 关闭之后会调用 implCloseChannel 方法
            // 收尾的一些事情在该方法里
            implCloseChannel();
        }
    }

    /**
     * Closes this channel.
     *
     * <p> This method is invoked by the {@link #close close} method in order
     * to perform the actual work of closing the channel.  This method is only
     * invoked if the channel has not yet been closed, and it is never invoked
     * more than once.
     * <p>
     *     该方法由 close 方法调用执行。该方法执行的是关闭 channel 相关的实际所需工作。
     *     该方法只会调用一次，而且只有当 channel 真的没有关闭的时候才会调用。
     *
     * <p> An implementation of this method must arrange for any other thread
     * that is blocked in an I/O operation upon this channel to return
     * immediately, either by throwing an exception or by returning normally.
     * <p>
     *     该方法的实现需要考虑到对其他阻塞在该 channel 上的线程的影响。
     *     可以是抛出异常或者正常返回。
     * </p>
     *
     *
     * @throws  IOException
     *          If an I/O error occurs while closing the channel
     */
    protected abstract void implCloseChannel() throws IOException;

    /**
     * @return 返回 channel 是否开着
     */
    public final boolean isOpen() {
        return !closed;
    }


    // -- Interruption machinery --

    private Interruptible interruptor;
    private volatile Thread interrupted;

    /**
     * Marks the beginning of an I/O operation that might block indefinitely.
     * <p>
     *     标记 IO 操作的开始。可能会发生阻塞。
     *     调用了 begin 方法就得调用 end 方法。
     *
     * <p> This method should be invoked in tandem with the {@link #end end}
     * method, using a {@code try}&nbsp;...&nbsp;{@code finally} block as
     * shown <a href="#be">above</a>, in order to implement asynchronous
     * closing and interruption for this channel.  </p>
     */
    protected final void begin() {
        if (interruptor == null) {
            interruptor = new Interruptible() {
                // 中断方法其实也是一个会导致关闭 channel 的方法
                    public void interrupt(Thread target) {
                        synchronized (closeLock) {
                            if (closed)
                                return;
                            closed = true;
                            interrupted = target;
                            try {
                                AbstractInterruptibleChannel.this.implCloseChannel();
                            } catch (IOException x) { }
                        }
                    }};
        }
        blockedOn(interruptor);
        // 获取当前线程
        Thread me = Thread.currentThread();
        // 如果当前线程没有被中断
        if (me.isInterrupted())
            // 在中断时会自动执行 channel 的关闭方法
            interruptor.interrupt(me);
    }

    /**
     * Marks the end of an I/O operation that might block indefinitely.
     *
     * <p> This method should be invoked in tandem with the {@link #begin
     * begin} method, using a {@code try}&nbsp;...&nbsp;{@code finally} block
     * as shown <a href="#be">above</a>, in order to implement asynchronous
     * closing and interruption for this channel.  </p>
     *
     * @param  completed
     *         {@code true} if, and only if, the I/O operation completed
     *         successfully, that is, had some effect that would be visible to
     *         the operation's invoker operation 完成的时候传入 true
     *
     * @throws  AsynchronousCloseException
     *          If the channel was asynchronously closed
     *
     * @throws  ClosedByInterruptException
     *          If the thread blocked in the I/O operation was interrupted
     */
    protected final void end(boolean completed)
        throws AsynchronousCloseException
    {
        // 将当前线程的 Interruptible 引用置为 null
        blockedOn(null);
        Thread interrupted = this.interrupted;
        if (interrupted != null && interrupted == Thread.currentThread()) {
            this.interrupted = null;
            throw new ClosedByInterruptException();
        }
        if (!completed && closed)
            throw new AsynchronousCloseException();
    }


    // -- jdk.internal.misc.SharedSecrets --
    static void blockedOn(Interruptible intr) {         // package-private
        SharedSecrets.getJavaLangAccess().blockedOn(intr);
    }
}
