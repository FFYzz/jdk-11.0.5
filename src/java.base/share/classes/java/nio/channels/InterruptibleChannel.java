/*
 * Copyright (c) 2001, Oracle and/or its affiliates. All rights reserved.
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

package java.nio.channels;

import java.io.IOException;


/**
 * A channel that can be asynchronously closed and interrupted.
 * <p> 可被异步关闭以及中断的 channel
 *
 * <p> A channel that implements this interface is <i>asynchronously
 * closeable:</i> If a thread is blocked in an I/O operation on an
 * interruptible channel then another thread may invoke the channel's {@link
 * #close close} method.  This will cause the blocked thread to receive an
 * {@link AsynchronousCloseException}.
 * <p> 实现该接口的 channel 可被异步关闭。如果一个 interruptible channel 线程阻塞在 IO
 * 操作上，那么其他线程可以调用 interruptible channel 的 close 方法进行异步关闭。阻塞线程
 * 会收到一个 AsynchronousCloseException 异常。
 *
 * <p> A channel that implements this interface is also <i>interruptible:</i>
 * If a thread is blocked in an I/O operation on an interruptible channel then
 * another thread may invoke the blocked thread's {@link Thread#interrupt()
 * interrupt} method.  This will cause the channel to be closed, the blocked
 * thread to receive a {@link ClosedByInterruptException}, and the blocked
 * thread's interrupt status to be set.
 * <p> 实现了该接口的 Channel 同样也是可被中断的。如果一个 interruptible channel 线程阻塞在 IO
 *  * 操作上，那么其他线程可以调用 interrupt 方法中断阻塞的线程。阻塞的线程会收到一个
  * ClosedByInterruptException 异常，并且被阻塞线程的中断状态会被设置为 true。
 *
 * <p> If a thread's interrupt status is already set and it invokes a blocking
 * I/O operation upon a channel then the channel will be closed and the thread
 * will immediately receive a {@link ClosedByInterruptException}; its interrupt
 * status will remain set.
 * <p> 如果线程的中断状态已经被设置。并且该线程在 channel 上调用了一个阻塞的 IO 操作，那么
 * channel 会被关闭并且线程会收到一个 ClosedByInterruptException 异常。线程的中断状态
 * 还是为 true 状态。
 *
 * <p> A channel supports asynchronous closing and interruption if, and only
 * if, it implements this interface.  This can be tested at runtime, if
 * necessary, via the {@code instanceof} operator.
 * <p> 运行时可通过 instanceof 关键字判断 channel 是否为 InterruptibleChannel。
 *
 *
 * @author Mark Reinhold
 * @author JSR-51 Expert Group
 * @since 1.4
 */

public interface InterruptibleChannel
    extends Channel
{

    /**
     * 阻塞的时候调用该方法，线程会收到一个 AsynchronousCloseException 异常
     * <p> Closes this channel.
     *
     * <p> Any thread currently blocked in an I/O operation upon this channel
     * will receive an {@link AsynchronousCloseException}.
     *
     * <p> This method otherwise behaves exactly as specified by the {@link
     * Channel#close Channel} interface.  </p>
     *
     * @throws  IOException  If an I/O error occurs
     */
    public void close() throws IOException;

}
