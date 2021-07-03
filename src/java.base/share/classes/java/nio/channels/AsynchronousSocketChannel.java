/*
 * Copyright (c) 2007, 2017, Oracle and/or its affiliates. All rights reserved.
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

package java.nio.channels;

import java.nio.channels.spi.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Future;
import java.io.IOException;
import java.net.SocketOption;
import java.net.SocketAddress;
import java.nio.ByteBuffer;

/**
 * An asynchronous channel for stream-oriented connecting sockets.
 * <p>
 *     面向 stream 连接 socket 的异步 channel
 *
 * <p> Asynchronous socket channels are created in one of two ways. A newly-created
 * {@code AsynchronousSocketChannel} is created by invoking one of the {@link
 * #open open} methods defined by this class. A newly-created channel is open but
 * not yet connected. A connected {@code AsynchronousSocketChannel} is created
 * when a connection is made to the socket of an {@link AsynchronousServerSocketChannel}.
 * It is not possible to create an asynchronous socket channel for an arbitrary,
 * pre-existing {@link java.net.Socket socket}.
 * <p>
 *     Asynchronous socket channels 有两种方式创建。
 *     1. 调用该方法定义的 open 方法。
 *
 * <p> A newly-created channel is connected by invoking its {@link #connect connect}
 * method; once connected, a channel remains connected until it is closed.  Whether
 * or not a socket channel is connected may be determined by invoking its {@link
 * #getRemoteAddress getRemoteAddress} method. An attempt to invoke an I/O
 * operation upon an unconnected channel will cause a {@link NotYetConnectedException}
 * to be thrown.
 * <p>
 *     通过 connect 方法建立连接。一旦建立连接，那么，channel 将会保持 connected 状态直到 channel
 *     被关闭。getRemoteAddress 可判断 socket channel 是否已经建立连接。在未连接的 channel
 *     上调用 IO 操作会抛出 NotYetConnectedException。
 *
 * <p> Channels of this type are safe for use by multiple concurrent threads.
 * They support concurrent reading and writing, though at most one read operation
 * and one write operation can be outstanding at any time.
 * If a thread initiates a read operation before a previous read operation has
 * completed then a {@link ReadPendingException} will be thrown. Similarly, an
 * attempt to initiate a write operation before a previous write has completed
 * will throw a {@link WritePendingException}.
 * <p>
 *     线程安全
 *
 * <p> Socket options are configured using the {@link #setOption(SocketOption,Object)
 * setOption} method. Asynchronous socket channels support the following options:
 * <p>
 *     socket 选项可以通过 setOption 方法设置。异步 socket channel 支持以下 option
 * <blockquote>
 * <table class="striped">
 * <caption style="display:none">Socket options</caption>
 * <thead>
 *   <tr>
 *     <th scope="col">Option Name</th>
 *     <th scope="col">Description</th>
 *   </tr>
 * </thead>
 * <tbody>
 *   <tr>
 *     <th scope="row"> {@link java.net.StandardSocketOptions#SO_SNDBUF SO_SNDBUF} </th>
 *     <td> The size of the socket send buffer | socket 发送 buffer 的大小</td>
 *   </tr>
 *   <tr>
 *     <th scope="row"> {@link java.net.StandardSocketOptions#SO_RCVBUF SO_RCVBUF} </th>
 *     <td> The size of the socket receive buffer | socket 接受 buffer 的大小</td>
 *   </tr>
 *   <tr>
 *     <th scope="row"> {@link java.net.StandardSocketOptions#SO_KEEPALIVE SO_KEEPALIVE} </th>
 *     <td> Keep connection alive | 是否开区 keepalive</td>
 *   </tr>
 *   <tr>
 *     <th scope="row"> {@link java.net.StandardSocketOptions#SO_REUSEADDR SO_REUSEADDR} </th>
 *     <td> Re-use address | 重用地址</td>
 *   </tr>
 *   <tr>
 *     <th scope="row"> {@link java.net.StandardSocketOptions#TCP_NODELAY TCP_NODELAY} </th>
 *     <td> Disable the Nagle algorithm | 关闭 Nagle 算法</td>
 *   </tr>
 * </tbody>
 * </table>
 * </blockquote>
 * Additional (implementation specific) options may also be supported.
 *
 * <h2>Timeouts</h2>
 *
 * <p> The {@link #read(ByteBuffer,long,TimeUnit,Object,CompletionHandler) read}
 * and {@link #write(ByteBuffer,long,TimeUnit,Object,CompletionHandler) write}
 * methods defined by this class allow a timeout to be specified when initiating
 * a read or write operation. If the timeout elapses before an operation completes
 * then the operation completes with the exception {@link
 * InterruptedByTimeoutException}. A timeout may leave the channel, or the
 * underlying connection, in an inconsistent state. Where the implementation
 * cannot guarantee that bytes have not been read from the channel then it puts
 * the channel into an implementation specific <em>error state</em>. A subsequent
 * attempt to initiate a {@code read} operation causes an unspecified runtime
 * exception to be thrown. Similarly if a {@code write} operation times out and
 * the implementation cannot guarantee bytes have not been written to the
 * channel then further attempts to {@code write} to the channel cause an
 * unspecified runtime exception to be thrown. When a timeout elapses then the
 * state of the {@link ByteBuffer}, or the sequence of buffers, for the I/O
 * operation is not defined. Buffers should be discarded or at least care must
 * be taken to ensure that the buffers are not accessed while the channel remains
 * open. All methods that accept timeout parameters treat values less than or
 * equal to zero to mean that the I/O operation does not timeout.
 * <p>
 *     在该类中定义的 read/write 方法之处超时请求。在指定时间内操作还未完成，会抛出
 *     InterruptedByTimeoutException 异常。
 *
 * @since 1.7
 */

public abstract class AsynchronousSocketChannel
    implements AsynchronousByteChannel, NetworkChannel
{
    private final AsynchronousChannelProvider provider;

    /**
     * Initializes a new instance of this class.
     * <p>
     *     protected，仅允许子类调用
     *
     * @param  provider
     *         The provider that created this channel
     *         创建该 channel 的 provider
     */
    protected AsynchronousSocketChannel(AsynchronousChannelProvider provider) {
        this.provider = provider;
    }

    /**
     * Returns the provider that created this channel.
     * <p>
     *     返回创建该 channel 的 provider
     *
     * @return  The provider that created this channel
     */
    public final AsynchronousChannelProvider provider() {
        return provider;
    }

    /**
     * Opens an asynchronous socket channel.
     * <p>
     *     开启一个异步的 socket channel
     *
     * <p> The new channel is created by invoking the {@link
     * AsynchronousChannelProvider#openAsynchronousSocketChannel
     * openAsynchronousSocketChannel} method on the {@link
     * AsynchronousChannelProvider} that created the group. If the group parameter
     * is {@code null} then the resulting channel is created by the system-wide
     * default provider, and bound to the <em>default group</em>.
     * <p>
     *     通过调用 openAsynchronousSocketChannel 方法创建一个新的 channel。
     *     如果 group 参数为 null，那么创建的 channel 会被赋值为默认的 provider，
     *     并且绑定到默认的 group 上。
     *
     * @param   group
     *          The group to which the newly constructed channel should be bound,
     *          or {@code null} for the default group
     *
     * @return  A new asynchronous socket channel
     *
     * @throws  ShutdownChannelGroupException
     *          If the channel group is shutdown
     * @throws  IOException
     *          If an I/O error occurs
     */
    public static AsynchronousSocketChannel open(AsynchronousChannelGroup group)
        throws IOException
    {
        AsynchronousChannelProvider provider = (group == null) ?
            AsynchronousChannelProvider.provider() : group.provider();
        return provider.openAsynchronousSocketChannel(group);
    }

    /**
     * Opens an asynchronous socket channel.
     * <p>
     *     打开一个 asynchronous socket channel
     *
     * <p> This method returns an asynchronous socket channel that is bound to
     * the <em>default group</em>.This method is equivalent to evaluating the
     * expression:
     * <blockquote><pre>
     * open((AsynchronousChannelGroup)null);
     * </pre></blockquote>
     * <p>
     *     绑定到默认的 group
     *
     * @return  A new asynchronous socket channel
     *
     * @throws  IOException
     *          If an I/O error occurs
     */
    public static AsynchronousSocketChannel open()
        throws IOException
    {
        return open(null);
    }


    // -- socket options and related --

    /**
     * 绑定到一个地址上
     * <p>
     * @throws  ConnectionPendingException
     *          If a connection operation is already in progress on this channel
     * @throws  AlreadyBoundException               {@inheritDoc}
     * @throws  UnsupportedAddressTypeException     {@inheritDoc}
     * @throws  ClosedChannelException              {@inheritDoc}
     * @throws  IOException                         {@inheritDoc}
     * @throws  SecurityException
     *          If a security manager has been installed and its
     *          {@link SecurityManager#checkListen checkListen} method denies
     *          the operation
     */
    @Override
    public abstract AsynchronousSocketChannel bind(SocketAddress local)
        throws IOException;

    /**
     * 设置 socket option
     * <p>
     * @throws  IllegalArgumentException                {@inheritDoc}
     * @throws  ClosedChannelException                  {@inheritDoc}
     * @throws  IOException                             {@inheritDoc}
     */
    @Override
    public abstract <T> AsynchronousSocketChannel setOption(SocketOption<T> name, T value)
        throws IOException;

    /**
     * Shutdown the connection for reading without closing the channel.
     * <p>
     *     关闭读连接，但是不关闭 channel
     *
     * <p> Once shutdown for reading then further reads on the channel will
     * return {@code -1}, the end-of-stream indication. If the input side of the
     * connection is already shutdown then invoking this method has no effect.
     * The effect on an outstanding read operation is system dependent and
     * therefore not specified. The effect, if any, when there is data in the
     * socket receive buffer that has not been read, or data arrives subsequently,
     * is also system dependent.
     * <p>
     *     一旦关闭了读连接，那么之后的 read 操作都将返回 -1。如果已经没有输入了，那么调用该方法
     *     没有影响。如果还有输入，那么造成的影响将由系统决定。
     *
     * @return  The channel
     *
     * @throws  NotYetConnectedException
     *          If this channel is not yet connected
     * @throws  ClosedChannelException
     *          If this channel is closed
     * @throws  IOException
     *          If some other I/O error occurs
     */
    public abstract AsynchronousSocketChannel shutdownInput() throws IOException;

    /**
     * Shutdown the connection for writing without closing the channel.
     * <p>
     *     关闭写连接，但是不关闭 channel
     *
     * <p> Once shutdown for writing then further attempts to write to the
     * channel will throw {@link ClosedChannelException}. If the output side of
     * the connection is already shutdown then invoking this method has no
     * effect. The effect on an outstanding write operation is system dependent
     * and therefore not specified.
     * <p>
     *     关闭写连接之后，再尝试写会抛出 ClosedChannelException 异常。如果写的输出端
     *     已经关闭，那么调用该方法不会有任何影响。同样如果未关闭，那么影响将由系统决定。
     *
     * @return  The channel
     *
     * @throws  NotYetConnectedException
     *          If this channel is not yet connected
     * @throws  ClosedChannelException
     *          If this channel is closed
     * @throws  IOException
     *          If some other I/O error occurs
     */
    public abstract AsynchronousSocketChannel shutdownOutput() throws IOException;

    // -- state --

    /**
     * Returns the remote address to which this channel's socket is connected.
     * <p>
     *     返回 socket 连接的远程地址
     *
     * <p> Where the channel is bound and connected to an Internet Protocol
     * socket address then the return value from this method is of type {@link
     * java.net.InetSocketAddress}.
     *
     * @return  The remote address; {@code null} if the channel's socket is not
     *          connected
     *
     * @throws  ClosedChannelException
     *          If the channel is closed
     * @throws  IOException
     *          If an I/O error occurs
     */
    public abstract SocketAddress getRemoteAddress() throws IOException;

    // -- asynchronous operations --

    /**
     * Connects this channel.
     * <p>
     *     channel 进行连接
     *
     * <p> This method initiates an operation to connect this channel. The
     * {@code handler} parameter is a completion handler that is invoked when
     * the connection is successfully established or connection cannot be
     * established. If the connection cannot be established then the channel is
     * closed.
     * <p>
     *     该方法初始化一个操作，用于连接该 channel。CompletionHandler 用于定义当连接
     *     建立成功或者建立失败之后的处理。
     *
     * <p> This method performs exactly the same security checks as the {@link
     * java.net.Socket} class.  That is, if a security manager has been
     * installed then this method verifies that its {@link
     * java.lang.SecurityManager#checkConnect checkConnect} method permits
     * connecting to the address and port number of the given remote endpoint.
     *
     * @param   <A>
     *          The type of the attachment
     *          attachment 的类型
     * @param   remote
     *          The remote address to which this channel is to be connected
     *          要连接的远程地址
     * @param   attachment
     *          The object to attach to the I/O operation; can be {@code null}
     *          关联该连接的 attachement
     * @param   handler
     *          The handler for consuming the result
     *          连接成功或者失败的处理
     *
     * @throws  UnresolvedAddressException
     *          If the given remote address is not fully resolved
     * @throws  UnsupportedAddressTypeException
     *          If the type of the given remote address is not supported
     * @throws  AlreadyConnectedException
     *          If this channel is already connected
     * @throws  ConnectionPendingException
     *          If a connection operation is already in progress on this channel
     * @throws  ShutdownChannelGroupException
     *          If the channel group has terminated
     * @throws  SecurityException
     *          If a security manager has been installed
     *          and it does not permit access to the given remote endpoint
     *
     * @see #getRemoteAddress
     */
    public abstract <A> void connect(SocketAddress remote,
                                     A attachment,
                                     CompletionHandler<Void,? super A> handler);

    /**
     * Connects this channel.
     * <p>
     *     对 channel 进行连接
     *
     * <p> This method initiates an operation to connect this channel. This
     * method behaves in exactly the same manner as the {@link
     * #connect(SocketAddress, Object, CompletionHandler)} method except that
     * instead of specifying a completion handler, this method returns a {@code
     * Future} representing the pending result. The {@code Future}'s {@link
     * Future#get() get} method returns {@code null} on successful completion.
     * <p>
     *     连接成功后，future 的 get 方法会返回 null
     *
     * @param   remote
     *          The remote address to which this channel is to be connected
     *
     * @return  A {@code Future} object representing the pending result
     *
     * @throws  UnresolvedAddressException
     *          If the given remote address is not fully resolved
     * @throws  UnsupportedAddressTypeException
     *          If the type of the given remote address is not supported
     * @throws  AlreadyConnectedException
     *          If this channel is already connected
     * @throws  ConnectionPendingException
     *          If a connection operation is already in progress on this channel
     * @throws  SecurityException
     *          If a security manager has been installed
     *          and it does not permit access to the given remote endpoint
     */
    public abstract Future<Void> connect(SocketAddress remote);

    /**
     * Reads a sequence of bytes from this channel into the given buffer.
     * <p>
     *     将 channel 中的数据读到 buffer 中去
     *
     * <p> This method initiates an asynchronous read operation to read a
     * sequence of bytes from this channel into the given buffer. The {@code
     * handler} parameter is a completion handler that is invoked when the read
     * operation completes (or fails). The result passed to the completion
     * handler is the number of bytes read or {@code -1} if no bytes could be
     * read because the channel has reached end-of-stream.
     * <p>
     *     该方法会初始化一个异步的 read 操作，将 channel 中的数据读到 buffer 中去。
     *     当读操作完成或者失败之后，会执行 CompletionHandler。传到 CompletionHandler
     *     中的参数为读取到的 byte 的数量。没有读取到数据则返回 -1。
     *
     * <p> If a timeout is specified and the timeout elapses before the operation
     * completes then the operation completes with the exception {@link
     * InterruptedByTimeoutException}. Where a timeout occurs, and the
     * implementation cannot guarantee that bytes have not been read, or will not
     * be read from the channel into the given buffer, then further attempts to
     * read from the channel will cause an unspecific runtime exception to be
     * thrown.
     * <p>
     *     如果指定了超时时间，那么在指定时间内未未完成读取，那么将抛出 InterruptedByTimeoutException。
     *     如果超时发生了，并不能保证还未读取的数据被读取到 buffer 中去。继续尝试读取会抛出
     *     异常。
     *
     * <p> Otherwise this method works in the same manner as the {@link
     * AsynchronousByteChannel#read(ByteBuffer,Object,CompletionHandler)}
     * method.
     *
     * @param   <A>
     *          The type of the attachment
     * @param   dst
     *          The buffer into which bytes are to be transferred
     * @param   timeout
     *          The maximum time for the I/O operation to complete
     * @param   unit
     *          The time unit of the {@code timeout} argument
     * @param   attachment
     *          The object to attach to the I/O operation; can be {@code null}
     * @param   handler
     *          The handler for consuming the result
     *
     * @throws  IllegalArgumentException
     *          If the buffer is read-only
     * @throws  ReadPendingException
     *          If a read operation is already in progress on this channel
     * @throws  NotYetConnectedException
     *          If this channel is not yet connected
     * @throws  ShutdownChannelGroupException
     *          If the channel group has terminated
     */
    public abstract <A> void read(ByteBuffer dst,
                                  long timeout,
                                  TimeUnit unit,
                                  A attachment,
                                  CompletionHandler<Integer,? super A> handler);

    /**
     * 不带超时时间的 read
     * <p>
     * @throws  IllegalArgumentException        {@inheritDoc}
     * @throws  ReadPendingException            {@inheritDoc}
     * @throws  NotYetConnectedException
     *          If this channel is not yet connected
     * @throws  ShutdownChannelGroupException
     *          If the channel group has terminated
     */
    @Override
    public final <A> void read(ByteBuffer dst,
                               A attachment,
                               CompletionHandler<Integer,? super A> handler)
    {
        read(dst, 0L, TimeUnit.MILLISECONDS, attachment, handler);
    }

    /**
     * 返回一个 future
     * <p>
     * @throws  IllegalArgumentException        {@inheritDoc}
     * @throws  ReadPendingException            {@inheritDoc}
     * @throws  NotYetConnectedException
     *          If this channel is not yet connected
     */
    @Override
    public abstract Future<Integer> read(ByteBuffer dst);

    /**
     * 将数据读到 dsts 数组中。
     * <p>
     * Reads a sequence of bytes from this channel into a subsequence of the
     * given buffers. This operation, sometimes called a <em>scattering read</em>,
     * is often useful when implementing network protocols that group data into
     * segments consisting of one or more fixed-length headers followed by a
     * variable-length body. The {@code handler} parameter is a completion
     * handler that is invoked when the read operation completes (or fails). The
     * result passed to the completion handler is the number of bytes read or
     * {@code -1} if no bytes could be read because the channel has reached
     * end-of-stream.
     * <p>
     *     在 network 的场景下，常常是 scattering read。
     *
     * <p> This method initiates a read of up to <i>r</i> bytes from this channel,
     * where <i>r</i> is the total number of bytes remaining in the specified
     * subsequence of the given buffer array, that is,
     *
     * <blockquote><pre>
     * dsts[offset].remaining()
     *     + dsts[offset+1].remaining()
     *     + ... + dsts[offset+length-1].remaining()</pre></blockquote>
     *
     * at the moment that the read is attempted.
     *
     * <p> Suppose that a byte sequence of length <i>n</i> is read, where
     * {@code 0}&nbsp;{@code <}&nbsp;<i>n</i>&nbsp;{@code <=}&nbsp;<i>r</i>.
     * Up to the first {@code dsts[offset].remaining()} bytes of this sequence
     * are transferred into buffer {@code dsts[offset]}, up to the next
     * {@code dsts[offset+1].remaining()} bytes are transferred into buffer
     * {@code dsts[offset+1]}, and so forth, until the entire byte sequence
     * is transferred into the given buffers.  As many bytes as possible are
     * transferred into each buffer, hence the final position of each updated
     * buffer, except the last updated buffer, is guaranteed to be equal to
     * that buffer's limit. The underlying operating system may impose a limit
     * on the number of buffers that may be used in an I/O operation. Where the
     * number of buffers (with bytes remaining), exceeds this limit, then the
     * I/O operation is performed with the maximum number of buffers allowed by
     * the operating system.
     *
     * <p> If a timeout is specified and the timeout elapses before the operation
     * completes then it completes with the exception {@link
     * InterruptedByTimeoutException}. Where a timeout occurs, and the
     * implementation cannot guarantee that bytes have not been read, or will not
     * be read from the channel into the given buffers, then further attempts to
     * read from the channel will cause an unspecific runtime exception to be
     * thrown.
     *
     * @param   <A>
     *          The type of the attachment
     * @param   dsts
     *          The buffers into which bytes are to be transferred
     * @param   offset
     *          The offset within the buffer array of the first buffer into which
     *          bytes are to be transferred; must be non-negative and no larger than
     *          {@code dsts.length}
     * @param   length
     *          The maximum number of buffers to be accessed; must be non-negative
     *          and no larger than {@code dsts.length - offset}
     * @param   timeout
     *          The maximum time for the I/O operation to complete
     * @param   unit
     *          The time unit of the {@code timeout} argument
     * @param   attachment
     *          The object to attach to the I/O operation; can be {@code null}
     * @param   handler
     *          The handler for consuming the result
     *
     * @throws  IndexOutOfBoundsException
     *          If the pre-conditions for the {@code offset}  and {@code length}
     *          parameter aren't met
     * @throws  IllegalArgumentException
     *          If the buffer is read-only
     * @throws  ReadPendingException
     *          If a read operation is already in progress on this channel
     * @throws  NotYetConnectedException
     *          If this channel is not yet connected
     * @throws  ShutdownChannelGroupException
     *          If the channel group has terminated
     */
    public abstract <A> void read(ByteBuffer[] dsts,
                                  int offset,
                                  int length,
                                  long timeout,
                                  TimeUnit unit,
                                  A attachment,
                                  CompletionHandler<Long,? super A> handler);

    /**
     * Writes a sequence of bytes to this channel from the given buffer.
     *
     * <p> This method initiates an asynchronous write operation to write a
     * sequence of bytes to this channel from the given buffer. The {@code
     * handler} parameter is a completion handler that is invoked when the write
     * operation completes (or fails). The result passed to the completion
     * handler is the number of bytes written.
     *
     * <p> If a timeout is specified and the timeout elapses before the operation
     * completes then it completes with the exception {@link
     * InterruptedByTimeoutException}. Where a timeout occurs, and the
     * implementation cannot guarantee that bytes have not been written, or will
     * not be written to the channel from the given buffer, then further attempts
     * to write to the channel will cause an unspecific runtime exception to be
     * thrown.
     *
     * <p> Otherwise this method works in the same manner as the {@link
     * AsynchronousByteChannel#write(ByteBuffer,Object,CompletionHandler)}
     * method.
     *
     * @param   <A>
     *          The type of the attachment
     * @param   src
     *          The buffer from which bytes are to be retrieved
     * @param   timeout
     *          The maximum time for the I/O operation to complete
     * @param   unit
     *          The time unit of the {@code timeout} argument
     * @param   attachment
     *          The object to attach to the I/O operation; can be {@code null}
     * @param   handler
     *          The handler for consuming the result
     *
     * @throws  WritePendingException
     *          If a write operation is already in progress on this channel
     * @throws  NotYetConnectedException
     *          If this channel is not yet connected
     * @throws  ShutdownChannelGroupException
     *          If the channel group has terminated
     */
    public abstract <A> void write(ByteBuffer src,
                                   long timeout,
                                   TimeUnit unit,
                                   A attachment,
                                   CompletionHandler<Integer,? super A> handler);

    /**
     * @throws  WritePendingException          {@inheritDoc}
     * @throws  NotYetConnectedException
     *          If this channel is not yet connected
     * @throws  ShutdownChannelGroupException
     *          If the channel group has terminated
     */
    @Override
    public final <A> void write(ByteBuffer src,
                                A attachment,
                                CompletionHandler<Integer,? super A> handler)

    {
        write(src, 0L, TimeUnit.MILLISECONDS, attachment, handler);
    }

    /**
     * @throws  WritePendingException       {@inheritDoc}
     * @throws  NotYetConnectedException
     *          If this channel is not yet connected
     */
    @Override
    public abstract Future<Integer> write(ByteBuffer src);

    /**
     * Writes a sequence of bytes to this channel from a subsequence of the given
     * buffers. This operation, sometimes called a <em>gathering write</em>, is
     * often useful when implementing network protocols that group data into
     * segments consisting of one or more fixed-length headers followed by a
     * variable-length body. The {@code handler} parameter is a completion
     * handler that is invoked when the write operation completes (or fails).
     * The result passed to the completion handler is the number of bytes written.
     *
     * <p> This method initiates a write of up to <i>r</i> bytes to this channel,
     * where <i>r</i> is the total number of bytes remaining in the specified
     * subsequence of the given buffer array, that is,
     *
     * <blockquote><pre>
     * srcs[offset].remaining()
     *     + srcs[offset+1].remaining()
     *     + ... + srcs[offset+length-1].remaining()</pre></blockquote>
     *
     * at the moment that the write is attempted.
     *
     * <p> Suppose that a byte sequence of length <i>n</i> is written, where
     * {@code 0}&nbsp;{@code <}&nbsp;<i>n</i>&nbsp;{@code <=}&nbsp;<i>r</i>.
     * Up to the first {@code srcs[offset].remaining()} bytes of this sequence
     * are written from buffer {@code srcs[offset]}, up to the next
     * {@code srcs[offset+1].remaining()} bytes are written from buffer
     * {@code srcs[offset+1]}, and so forth, until the entire byte sequence is
     * written.  As many bytes as possible are written from each buffer, hence
     * the final position of each updated buffer, except the last updated
     * buffer, is guaranteed to be equal to that buffer's limit. The underlying
     * operating system may impose a limit on the number of buffers that may be
     * used in an I/O operation. Where the number of buffers (with bytes
     * remaining), exceeds this limit, then the I/O operation is performed with
     * the maximum number of buffers allowed by the operating system.
     *
     * <p> If a timeout is specified and the timeout elapses before the operation
     * completes then it completes with the exception {@link
     * InterruptedByTimeoutException}. Where a timeout occurs, and the
     * implementation cannot guarantee that bytes have not been written, or will
     * not be written to the channel from the given buffers, then further attempts
     * to write to the channel will cause an unspecific runtime exception to be
     * thrown.
     *
     * @param   <A>
     *          The type of the attachment
     * @param   srcs
     *          The buffers from which bytes are to be retrieved
     * @param   offset
     *          The offset within the buffer array of the first buffer from which
     *          bytes are to be retrieved; must be non-negative and no larger
     *          than {@code srcs.length}
     * @param   length
     *          The maximum number of buffers to be accessed; must be non-negative
     *          and no larger than {@code srcs.length - offset}
     * @param   timeout
     *          The maximum time for the I/O operation to complete
     * @param   unit
     *          The time unit of the {@code timeout} argument
     * @param   attachment
     *          The object to attach to the I/O operation; can be {@code null}
     * @param   handler
     *          The handler for consuming the result
     *
     * @throws  IndexOutOfBoundsException
     *          If the pre-conditions for the {@code offset}  and {@code length}
     *          parameter aren't met
     * @throws  WritePendingException
     *          If a write operation is already in progress on this channel
     * @throws  NotYetConnectedException
     *          If this channel is not yet connected
     * @throws  ShutdownChannelGroupException
     *          If the channel group has terminated
     */
    public abstract <A> void write(ByteBuffer[] srcs,
                                   int offset,
                                   int length,
                                   long timeout,
                                   TimeUnit unit,
                                   A attachment,
                                   CompletionHandler<Long,? super A> handler);

    /**
     * {@inheritDoc}
     * <p>
     * If there is a security manager set, its {@code checkConnect} method is
     * called with the local address and {@code -1} as its arguments to see
     * if the operation is allowed. If the operation is not allowed,
     * a {@code SocketAddress} representing the
     * {@link java.net.InetAddress#getLoopbackAddress loopback} address and the
     * local port of the channel's socket is returned.
     *
     * @return  The {@code SocketAddress} that the socket is bound to, or the
     *          {@code SocketAddress} representing the loopback address if
     *          denied by the security manager, or {@code null} if the
     *          channel's socket is not bound
     *
     * @throws  ClosedChannelException     {@inheritDoc}
     * @throws  IOException                {@inheritDoc}
     */
    public abstract SocketAddress getLocalAddress() throws IOException;
}
