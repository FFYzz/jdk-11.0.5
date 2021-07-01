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

package java.nio.channels;

import java.io.IOException;
import java.net.ProtocolFamily;
import java.net.DatagramSocket;
import java.net.SocketOption;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.spi.AbstractSelectableChannel;
import java.nio.channels.spi.SelectorProvider;

/**
 * A selectable channel for datagram-oriented sockets.
 * <p> 面向 datagram-oriented sockets 的 selectable channel（面向 UDP）
 *
 * <p> A datagram channel is created by invoking one of the {@link #open open} methods
 * of this class. It is not possible to create a channel for an arbitrary,
 * pre-existing datagram socket. A newly-created datagram channel is open but not
 * connected. A datagram channel need not be connected in order for the {@link #send
 * send} and {@link #receive receive} methods to be used.  A datagram channel may be
 * connected, by invoking its {@link #connect connect} method, in order to
 * avoid the overhead of the security checks are otherwise performed as part of
 * every send and receive operation.  A datagram channel must be connected in
 * order to use the {@link #read(java.nio.ByteBuffer) read} and {@link
 * #write(java.nio.ByteBuffer) write} methods, since those methods do not
 * accept or return socket addresses.
 * <p> 通过调用该类的 open 方法创建一个 datagram channel。新创建的 channel 处于 open 状态，
 * 但是还连接。datagram channel 不需要按照顺序进行连接。
 *
 * <p> Once connected, a datagram channel remains connected until it is
 * disconnected or closed.  Whether or not a datagram channel is connected may
 * be determined by invoking its {@link #isConnected isConnected} method.
 * <p> 一旦连接成功，一个 datagram channel 就将保持连接状态，直到 channel 断开或者关闭。
 * 调用 isConnected 可以判断 channel 是否处于已连接状态。
 *
 * <p> Socket options are configured using the {@link #setOption(SocketOption,Object)
 * setOption} method. A datagram channel to an Internet Protocol socket supports
 * the following options:
 * <p> 通过调用 setOption 方法可以设置 socket 的 socket 选项。
 * 支持下列 socket 选项
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
 *     <td> The size of the socket send buffer | socket 发送 buffer 大小</td>
 *   </tr>
 *   <tr>
 *     <th scope="row"> {@link java.net.StandardSocketOptions#SO_RCVBUF SO_RCVBUF} </th>
 *     <td> The size of the socket receive buffer | socket 接收 buffer 大小</td>
 *   </tr>
 *   <tr>
 *     <th scope="row"> {@link java.net.StandardSocketOptions#SO_REUSEADDR SO_REUSEADDR} </th>
 *     <td> Re-use address | 重用地址</td>
 *   </tr>
 *   <tr>
 *     <th scope="row"> {@link java.net.StandardSocketOptions#SO_BROADCAST SO_BROADCAST} </th>
 *     <td> Allow transmission of broadcast datagrams | 允许广播数据包传输</td>
 *   </tr>
 *   <tr>
 *     <th scope="row"> {@link java.net.StandardSocketOptions#IP_TOS IP_TOS} </th>
 *     <td> The Type of Service (ToS) octet in the Internet Protocol (IP) header | IP 头中的 Tos
 *     octet 类型 </td>
 *   </tr>
 *   <tr>
 *     <th scope="row"> {@link java.net.StandardSocketOptions#IP_MULTICAST_IF IP_MULTICAST_IF} </th>
 *     <td> The network interface for Internet Protocol (IP) multicast datagrams  |
 *     IP 多播数据包网络接口</td>
 *   </tr>
 *   <tr>
 *     <th scope="row"> {@link java.net.StandardSocketOptions#IP_MULTICAST_TTL
 *       IP_MULTICAST_TTL} </th>
 *     <td> The <em>time-to-live</em> for Internet Protocol (IP) multicast
 *       datagrams | time-to_live</td>
 *   </tr>
 *   <tr>
 *     <th scope="row"> {@link java.net.StandardSocketOptions#IP_MULTICAST_LOOP
 *       IP_MULTICAST_LOOP} </th>
 *     <td> Loopback for Internet Protocol (IP) multicast datagrams | 多播数据包的 loopback</td>
 *   </tr>
 * </tbody>
 * </table>
 * </blockquote>
 * Additional (implementation specific) options may also be supported.
 *
 * <p> Datagram channels are safe for use by multiple concurrent threads.  They
 * support concurrent reading and writing, though at most one thread may be
 * reading and at most one thread may be writing at any given time.
 * <p> 多线程安全。支持并发读写。
 * </p>
 *
 * @author Mark Reinhold
 * @author JSR-51 Expert Group
 * @since 1.4
 */

public abstract class DatagramChannel
    extends AbstractSelectableChannel
    implements ByteChannel, ScatteringByteChannel, GatheringByteChannel, MulticastChannel
{

    /**
     * Initializes a new instance of this class.
     * <p> 初始化一个 DatagramChannel 实例， protected 修饰，只能同包内调用。
     *
     * @param  provider
     *         The provider that created this channel
     */
    protected DatagramChannel(SelectorProvider provider) {
        super(provider);
    }

    /**
     * Opens a datagram channel.
     * <p> 打开一个 datagram channel。
     *
     * <p> The new channel is created by invoking the {@link
     * java.nio.channels.spi.SelectorProvider#openDatagramChannel()
     * openDatagramChannel} method of the system-wide default {@link
     * java.nio.channels.spi.SelectorProvider} object.  The channel will not be
     * connected.
     * <p> 调用 selectorProvider#openDatagramChannel 方法开启。开启之后默认不会建立连接。
     *
     * <p> The {@link ProtocolFamily ProtocolFamily} of the channel's socket
     * is platform (and possibly configuration) dependent and therefore unspecified.
     * The {@link #open(ProtocolFamily) open} allows the protocol family to be
     * selected when opening a datagram channel, and should be used to open
     * datagram channels that are intended for Internet Protocol multicasting.
     * <p> channel socket 的 ProtocolFamily 是依赖与平台的，因此不用指定。在开启 datagram channel
     * 的时候允许选择 ProtocolFamily ，并且选择的 ProtocolFamily 将用于 IP 多播。
     *
     * @return  A new datagram channel
     *
     * @throws  IOException
     *          If an I/O error occurs
     */
    public static DatagramChannel open() throws IOException {
        return SelectorProvider.provider().openDatagramChannel();
    }

    /**
     * Opens a datagram channel.
     * <p> 开启一个 datagram channel，支持配置 ProtocolFamily。
     *
     * <p> The {@code family} parameter is used to specify the {@link
     * ProtocolFamily}. If the datagram channel is to be used for IP multicasting
     * then this should correspond to the address type of the multicast groups
     * that this channel will join.
     *
     * <p> The new channel is created by invoking the {@link
     * java.nio.channels.spi.SelectorProvider#openDatagramChannel(ProtocolFamily)
     * openDatagramChannel} method of the system-wide default {@link
     * java.nio.channels.spi.SelectorProvider} object.  The channel will not be
     * connected.
     *
     * @param   family
     *          The protocol family
     *
     * @return  A new datagram channel
     *
     * @throws  UnsupportedOperationException
     *          If the specified protocol family is not supported. For example,
     *          suppose the parameter is specified as {@link
     *          java.net.StandardProtocolFamily#INET6 StandardProtocolFamily.INET6}
     *          but IPv6 is not enabled on the platform.
     * @throws  IOException
     *          If an I/O error occurs
     *
     * @since   1.7
     */
    public static DatagramChannel open(ProtocolFamily family) throws IOException {
        return SelectorProvider.provider().openDatagramChannel(family);
    }

    /**
     * Returns an operation set identifying this channel's supported
     * operations.
     * <p> 返回当前 channel 支持的 ops
     *
     * <p> Datagram channels support reading and writing, so this method
     * returns {@code (}{@link SelectionKey#OP_READ} {@code |}&nbsp;{@link
     * SelectionKey#OP_WRITE}{@code )}.
     * <p> datagram channel 支持 OP_READ 和 OP_WRITE 操作。
     *
     * @return  The valid-operation set
     */
    public final int validOps() {
        return (SelectionKey.OP_READ
                | SelectionKey.OP_WRITE);
    }


    // -- Socket-specific operations --

    /**
     * 绑定到一个本地地址
     * <p>
     * @throws  AlreadyBoundException               {@inheritDoc}
     * @throws  UnsupportedAddressTypeException     {@inheritDoc}
     * @throws  ClosedChannelException              {@inheritDoc}
     * @throws  IOException                         {@inheritDoc}
     * @throws  SecurityException
     *          If a security manager has been installed and its {@link
     *          SecurityManager#checkListen checkListen} method denies the
     *          operation
     *
     * @since 1.7
     */
    public abstract DatagramChannel bind(SocketAddress local)
        throws IOException;

    /**
     * 设置 socket 选项
     * <p>
     * @throws  UnsupportedOperationException           {@inheritDoc}
     * @throws  IllegalArgumentException                {@inheritDoc}
     * @throws  ClosedChannelException                  {@inheritDoc}
     * @throws  IOException                             {@inheritDoc}
     *
     * @since 1.7
     */
    public abstract <T> DatagramChannel setOption(SocketOption<T> name, T value)
        throws IOException;

    /**
     * Retrieves a datagram socket associated with this channel.
     * <p> 获取与 datagram channel 关联的 datagram socket
     *
     * <p> The returned object will not declare any public methods that are not
     * declared in the {@link java.net.DatagramSocket} class.  </p>
     *
     * @return  A datagram socket associated with this channel
     */
    public abstract DatagramSocket socket();

    /**
     * Tells whether or not this channel's socket is connected.
     * <p> 返回当前 channel 的 socket 是否已连接，只有调用了 open 方法只有才能处于
     * 连接状态。
     *
     * @return  {@code true} if, and only if, this channel's socket
     *          is {@link #isOpen open} and connected
     */
    public abstract boolean isConnected();

    /**
     * Connects this channel's socket.
     * <p> 连接当前 channel 的socket
     *
     * <p> The channel's socket is configured so that it only receives
     * datagrams from, and sends datagrams to, the given remote <i>peer</i>
     * address.  Once connected, datagrams may not be received from or sent to
     * any other address.  A datagram socket remains connected until it is
     * explicitly disconnected or until it is closed.
     * <p> channel 的 socket 是配置过的。仅支持想给定的远程 peer 地址收数据和发数据。
     * 一旦建立了连接，datagrams 不会与其他的地址进行收发数据。在 socket 断开连接或者
     * 关闭之前，datagram socket 都处于建立连接状态。
     *
     * <p> This method performs exactly the same security checks as the {@link
     * java.net.DatagramSocket#connect connect} method of the {@link
     * java.net.DatagramSocket} class.  That is, if a security manager has been
     * installed then this method verifies that its {@link
     * java.lang.SecurityManager#checkAccept checkAccept} and {@link
     * java.lang.SecurityManager#checkConnect checkConnect} methods permit
     * datagrams to be received from and sent to, respectively, the given
     * remote address.
     * <p> 会进行安全检查
     *
     * <p> This method may be invoked at any time.  It will not have any effect
     * on read or write operations that are already in progress at the moment
     * that it is invoked. If this channel's socket is not bound then this method
     * will first cause the socket to be bound to an address that is assigned
     * automatically, as if invoking the {@link #bind bind} method with a
     * parameter of {@code null}.
     * <p> 任意时刻可以被调用，如果已经在 read/write，那么调用该方法无影响。如果当前 channel
     * 的 socket 没有绑定，那么调用该方法首先会使得 socket 绑定到一个自动生成的地址上。就好像调用
     * 了 bind 方法，传入一个 null。
     * </p>
     *
     * @param  remote
     *         The remote address to which this channel is to be connected
     *
     * @return  This datagram channel
     *
     * @throws  AlreadyConnectedException
     *          If this channel is already connected
     *
     * @throws  ClosedChannelException
     *          If this channel is closed
     *
     * @throws  AsynchronousCloseException
     *          If another thread closes this channel
     *          while the connect operation is in progress
     *
     * @throws  ClosedByInterruptException
     *          If another thread interrupts the current thread
     *          while the connect operation is in progress, thereby
     *          closing the channel and setting the current thread's
     *          interrupt status
     *
     * @throws  UnresolvedAddressException
     *          If the given remote address is not fully resolved
     *
     * @throws  UnsupportedAddressTypeException
     *          If the type of the given remote address is not supported
     *
     * @throws  SecurityException
     *          If a security manager has been installed
     *          and it does not permit access to the given remote address
     *
     * @throws  IOException
     *          If some other I/O error occurs
     */
    public abstract DatagramChannel connect(SocketAddress remote)
        throws IOException;

    /**
     * Disconnects this channel's socket.
     * <p> 断开 channel 的 socket 连接
     *
     * <p> The channel's socket is configured so that it can receive datagrams
     * from, and sends datagrams to, any remote address so long as the security
     * manager, if installed, permits it.
     *
     * <p> This method may be invoked at any time.  It will not have any effect
     * on read or write operations that are already in progress at the moment
     * that it is invoked.
     *
     * <p> If this channel's socket is not connected, or if the channel is
     * closed, then invoking this method has no effect.
     * <p> 如果 channel socket 未连接或者 channel 已经关闭，那么调用该方法无效。
     * </p>
     *
     * @return  This datagram channel
     *
     * @throws  IOException
     *          If some other I/O error occurs
     */
    public abstract DatagramChannel disconnect() throws IOException;

    /**
     * Returns the remote address to which this channel's socket is connected.
     * <p> 返回 cahnnel socket 连接的远程地址
     *
     * @return  The remote address; {@code null} if the channel's socket is not
     *          connected
     *
     * @throws  ClosedChannelException
     *          If the channel is closed
     * @throws  IOException
     *          If an I/O error occurs
     *
     * @since 1.7
     */
    public abstract SocketAddress getRemoteAddress() throws IOException;

    /**
     * Receives a datagram via this channel.
     * <p> 通过当前 channel 接收数据
     *
     * <p> If a datagram is immediately available, or if this channel is in
     * blocking mode and one eventually becomes available, then the datagram is
     * copied into the given byte buffer and its source address is returned.
     * If this channel is in non-blocking mode and a datagram is not
     * immediately available then this method immediately returns
     * {@code null}.
     * <p> 如果一个 datagram 马上可读，又或者 channel 处于 blocking 模式，并且最终变得可读，
     * 那么数据报将会被拷贝到 buuffer 中，并且该方法返回数据来源的 address。如果 channel
     * 为非阻塞模式，并且当前不是马上可用的，那么该方法会马上返回 null。
     *
     * <p> The datagram is transferred into the given byte buffer starting at
     * its current position, as if by a regular {@link
     * ReadableByteChannel#read(java.nio.ByteBuffer) read} operation.  If there
     * are fewer bytes remaining in the buffer than are required to hold the
     * datagram then the remainder of the datagram is silently discarded.
     * <p> datagram 会被转成 byte buffer。就好像调用一个常规的 read 方法。如果 buffer
     * 中的剩余容量比需要读取的 datagram 的数量要少，那么 datagram 中多出来的数据将会被丢弃。
     *
     * <p> This method performs exactly the same security checks as the {@link
     * java.net.DatagramSocket#receive receive} method of the {@link
     * java.net.DatagramSocket} class.  That is, if the socket is not connected
     * to a specific remote address and a security manager has been installed
     * then for each datagram received this method verifies that the source's
     * address and port number are permitted by the security manager's {@link
     * java.lang.SecurityManager#checkAccept checkAccept} method.  The overhead
     * of this security check can be avoided by first connecting the socket via
     * the {@link #connect connect} method.
     * <p> 需执行安全检查
     *
     * <p> This method may be invoked at any time.  If another thread has
     * already initiated a read operation upon this channel, however, then an
     * invocation of this method will block until the first operation is
     * complete. If this channel's socket is not bound then this method will
     * first cause the socket to be bound to an address that is assigned
     * automatically, as if invoking the {@link #bind bind} method with a
     * parameter of {@code null}.
     * <p> 当前方法可以在任意时刻调用，如果其他线程已经在该 channel 上调用了 read 操作，
     * 那么再调用该方法会被阻塞，直到那个线程的 read 操作完成并返回。如果当前 channel 的
     * socket 还没有建立连接，那么会先随机绑定一个地址，就像调用 bind 方法。
     * </p>
     *
     * @param  dst
     *         The buffer into which the datagram is to be transferred
     *
     * @return  The datagram's source address,
     *          or {@code null} if this channel is in non-blocking mode
     *          and no datagram was immediately available
     *
     * @throws  ClosedChannelException
     *          If this channel is closed
     *
     * @throws  AsynchronousCloseException
     *          If another thread closes this channel
     *          while the read operation is in progress
     *
     * @throws  ClosedByInterruptException
     *          If another thread interrupts the current thread
     *          while the read operation is in progress, thereby
     *          closing the channel and setting the current thread's
     *          interrupt status
     *
     * @throws  SecurityException
     *          If a security manager has been installed
     *          and it does not permit datagrams to be accepted
     *          from the datagram's sender
     *
     * @throws  IOException
     *          If some other I/O error occurs
     */
    public abstract SocketAddress receive(ByteBuffer dst) throws IOException;

    /**
     * Sends a datagram via this channel.
     * <p> 通过该 channel 发送 datagram 到多播组
     *
     * <p> If this channel is in non-blocking mode and there is sufficient room
     * in the underlying output buffer, or if this channel is in blocking mode
     * and sufficient room becomes available, then the remaining bytes in the
     * given buffer are transmitted as a single datagram to the given target
     * address.
     * <p> 如果当前 channel 是非阻塞模式，并且在输出 buffer 中有足够的容量。或者，当前 channel
     * 是阻塞模式，并且有足够的空间可用，那么 buffer 中的数据将被发送出去。
     *
     * <p> The datagram is transferred from the byte buffer as if by a regular
     * {@link WritableByteChannel#write(java.nio.ByteBuffer) write} operation.
     * <p> 发送数据与常规的 write 操作类似。
     *
     * <p> This method performs exactly the same security checks as the {@link
     * java.net.DatagramSocket#send send} method of the {@link
     * java.net.DatagramSocket} class.  That is, if the socket is not connected
     * to a specific remote address and a security manager has been installed
     * then for each datagram sent this method verifies that the target address
     * and port number are permitted by the security manager's {@link
     * java.lang.SecurityManager#checkConnect checkConnect} method.  The
     * overhead of this security check can be avoided by first connecting the
     * socket via the {@link #connect connect} method.
     * <p> 需要执行安全检查
     *
     * <p> This method may be invoked at any time.  If another thread has
     * already initiated a write operation upon this channel, however, then an
     * invocation of this method will block until the first operation is
     * complete. If this channel's socket is not bound then this method will
     * first cause the socket to be bound to an address that is assigned
     * automatically, as if by invoking the {@link #bind bind} method with a
     * parameter of {@code null}.
     * <p> 该方法可以在任意时候调用。多个线程同时调用 write 操作，会阻塞。如果 channel 的
     * socket 未绑定，那么会先绑定。
     * </p>
     *
     * @param  src
     *         The buffer containing the datagram to be sent
     *
     * @param  target
     *         The address to which the datagram is to be sent
     *
     * @return   The number of bytes sent, which will be either the number
     *           of bytes that were remaining in the source buffer when this
     *           method was invoked or, if this channel is non-blocking, may be
     *           zero if there was insufficient room for the datagram in the
     *           underlying output buffer
     *
     * @throws  AlreadyConnectedException
     *          If this channel is connected to a different address
     *          from that specified by {@code target}
     *
     * @throws  ClosedChannelException
     *          If this channel is closed
     *
     * @throws  AsynchronousCloseException
     *          If another thread closes this channel
     *          while the read operation is in progress
     *
     * @throws  ClosedByInterruptException
     *          If another thread interrupts the current thread
     *          while the read operation is in progress, thereby
     *          closing the channel and setting the current thread's
     *          interrupt status
     *
     * @throws  UnresolvedAddressException
     *          If the given remote address is not fully resolved
     *
     * @throws  UnsupportedAddressTypeException
     *          If the type of the given remote address is not supported
     *
     * @throws  SecurityException
     *          If a security manager has been installed
     *          and it does not permit datagrams to be sent
     *          to the given address
     *
     * @throws  IOException
     *          If some other I/O error occurs
     */
    public abstract int send(ByteBuffer src, SocketAddress target)
        throws IOException;


    // -- ByteChannel operations --

    /**
     * Reads a datagram from this channel.
     * <p> 读取 datagram 中的数据。如果 buffer 的剩余容量要小于 datagram 的数据量，
     * 那么多出来的数据将被丢弃。只有在建立连接之后才能调用该方法。
     *
     * <p> This method may only be invoked if this channel's socket is
     * connected, and it only accepts datagrams from the socket's peer.  If
     * there are more bytes in the datagram than remain in the given buffer
     * then the remainder of the datagram is silently discarded.  Otherwise
     * this method behaves exactly as specified in the {@link
     * ReadableByteChannel} interface.
     * </p>
     *
     * @throws  NotYetConnectedException
     *          If this channel's socket is not connected
     */
    public abstract int read(ByteBuffer dst) throws IOException;

    /**
     * Reads a datagram from this channel.
     *
     * <p> This method may only be invoked if this channel's socket is
     * connected, and it only accepts datagrams from the socket's peer.  If
     * there are more bytes in the datagram than remain in the given buffers
     * then the remainder of the datagram is silently discarded.  Otherwise
     * this method behaves exactly as specified in the {@link
     * ScatteringByteChannel} interface.
     * <p> 只有 socket 连接之后才能调用该方法。
     * 参数应该是指写到第 offset 个到最后一个 ByteBuffer 中去
     * </p>
     *
     * @throws  NotYetConnectedException
     *          If this channel's socket is not connected
     */
    public abstract long read(ByteBuffer[] dsts, int offset, int length)
        throws IOException;

    /**
     * Reads a datagram from this channel.
     *
     * <p> This method may only be invoked if this channel's socket is
     * connected, and it only accepts datagrams from the socket's peer.  If
     * there are more bytes in the datagram than remain in the given buffers
     * then the remainder of the datagram is silently discarded.  Otherwise
     * this method behaves exactly as specified in the {@link
     * ScatteringByteChannel} interface.
     * <p> 数据写到所有的 ByteBuffer 中的剩余的空间中去
     * </p>
     *
     * @throws  NotYetConnectedException
     *          If this channel's socket is not connected
     */
    public final long read(ByteBuffer[] dsts) throws IOException {
        return read(dsts, 0, dsts.length);
    }

    /**
     * Writes a datagram to this channel.
     *
     * <p> This method may only be invoked if this channel's socket is
     * connected, in which case it sends datagrams directly to the socket's
     * peer.  Otherwise it behaves exactly as specified in the {@link
     * WritableByteChannel} interface.  </p>
     *
     * @throws  NotYetConnectedException
     *          If this channel's socket is not connected
     */
    public abstract int write(ByteBuffer src) throws IOException;

    /**
     * Writes a datagram to this channel.
     *
     * <p> This method may only be invoked if this channel's socket is
     * connected, in which case it sends datagrams directly to the socket's
     * peer.  Otherwise it behaves exactly as specified in the {@link
     * GatheringByteChannel} interface.  </p>
     *
     * @return   The number of bytes sent, which will be either the number
     *           of bytes that were remaining in the source buffer when this
     *           method was invoked or, if this channel is non-blocking, may be
     *           zero if there was insufficient room for the datagram in the
     *           underlying output buffer
     *           写出去的字节数
     *
     * @throws  NotYetConnectedException
     *          If this channel's socket is not connected
     */
    public abstract long write(ByteBuffer[] srcs, int offset, int length)
        throws IOException;

    /**
     * Writes a datagram to this channel.
     *
     * <p> This method may only be invoked if this channel's socket is
     * connected, in which case it sends datagrams directly to the socket's
     * peer.  Otherwise it behaves exactly as specified in the {@link
     * GatheringByteChannel} interface.  </p>
     *
     * @return   The number of bytes sent, which will be either the number
     *           of bytes that were remaining in the source buffer when this
     *           method was invoked or, if this channel is non-blocking, may be
     *           zero if there was insufficient room for the datagram in the
     *           underlying output buffer
     *
     * @throws  NotYetConnectedException
     *          If this channel's socket is not connected
     */
    public final long write(ByteBuffer[] srcs) throws IOException {
        return write(srcs, 0, srcs.length);
    }

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
     *          返回 socket 绑定的 SocketAddress。
     *
     * @throws  ClosedChannelException     {@inheritDoc}
     * @throws  IOException                {@inheritDoc}
     */
    @Override
    public abstract SocketAddress getLocalAddress() throws IOException;

}
