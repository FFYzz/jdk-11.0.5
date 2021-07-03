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

import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

/**
 * A token representing the registration of a {@link SelectableChannel} with a
 * {@link Selector}.
 * <p>
 *     SelectionKey 是一个 token，代表 channel 注册在 Selector 上。
 *
 * <p> A selection key is created each time a channel is registered with a
 * selector.  A key remains valid until it is <i>cancelled</i> by invoking its
 * {@link #cancel cancel} method, by closing its channel, or by closing its
 * selector.  Cancelling a key does not immediately remove it from its
 * selector; it is instead added to the selector's <a
 * href="Selector.html#ks"><i>cancelled-key set</i></a> for removal during the
 * next selection operation.  The validity of a key may be tested by invoking
 * its {@link #isValid isValid} method.
 * <p>
 *     当 channel 注册到 selector 上的时候，会创建一个 selectionKey。 selectionKey 一直生效，
 *     直到 channel 被关闭导致 selectionKey 被 cancel，或者 selector 关闭。当 key 被取消的时候，
 *     key 并不会马上从 selector 中被移除。在下次执行 selection operation 的时候才会被移除。
 *     可以通过调用 isValid 方法来检测 key 的有效性。
 *
 * <a id="opsets"></a>
 *
 * <p> A selection key contains two <i>operation sets</i> represented as
 * integer values.  Each bit of an operation set denotes a category of
 * selectable operations that are supported by the key's channel.
 * <p>
 *     selection key 包含两个 operation set。operation set 中的值是 integer。
 *
 * <ul>
 *
 *   <li><p> The <i>interest set</i> determines which operation categories will
 *   be tested for readiness the next time one of the selector's selection
 *   methods is invoked.  The interest set is initialized with the value given
 *   when the key is created; it may later be changed via the {@link
 *   #interestOps(int)} method.
 *   <p>
 *       interest set:当下一个 selector 的任意 select 方法被调用的时候，可以确认哪个操作类别
 *       将会被同来测试可读性。???? TODO 这里看不大明白
 *   </p>
 *   </li>
 *
 *   <li><p> The <i>ready set</i> identifies the operation categories for which
 *   the key's channel has been detected to be ready by the key's selector.
 *   The ready set is initialized to zero when the key is created; it may later
 *   be updated by the selector during a selection operation, but it cannot be
 *   updated directly.
 *   <p>
 *       ready set: 标识了当前 selector 下的 channel 已经 ready。 当 key 被创建的时候，
 *       ready set 为 0。 ready set 不能够直接更新，而是得等到 selector 执行 selection
 *       operation 的时候才会更新。
 *   </p></li>
 *
 * </ul>
 *
 * <p> That a selection key's ready set indicates that its channel is ready for
 * some operation category is a hint, but not a guarantee, that an operation in
 * such a category may be performed by a thread without causing the thread to
 * block.  A ready set is most likely to be accurate immediately after the
 * completion of a selection operation.  It is likely to be made inaccurate by
 * external events and by I/O operations that are invoked upon the
 * corresponding channel.
 * <p>
 *     selection key 的 ready set 表明关联的 channel 已经可以可以执行某个操作了（但是
 *     也仅仅是一个 hint，并不能确保一定可以执行了）。也就是说，channel 上的关联操作在执行的
 *     时候可能不会被阻塞。ready set 只有当 selection operation 真正地执行完之后才是
 *     准确的。
 *
 * <p> This class defines all known operation-set bits, but precisely which
 * bits are supported by a given channel depends upon the type of the channel.
 * Each subclass of {@link SelectableChannel} defines an {@link
 * SelectableChannel#validOps() validOps()} method which returns a set
 * identifying just those operations that are supported by the channel.  An
 * attempt to set or test an operation-set bit that is not supported by a key's
 * channel will result in an appropriate run-time exception.
 * <p>
 *     该类定义了所有的已知的 operation ，以常量的形式表示。支持哪种操作具体由相关的 channel 决定。
 *     每个 SelectableChannel 的子类都定义了 validOps 方法，返回当前 channel 支持的 operation。
 *
 * <p> It is often necessary to associate some application-specific data with a
 * selection key, for example an object that represents the state of a
 * higher-level protocol and handles readiness notifications in order to
 * implement that protocol.  Selection keys therefore support the
 * <i>attachment</i> of a single arbitrary object to a key.  An object can be
 * attached via the {@link #attach attach} method and then later retrieved via
 * the {@link #attachment() attachment} method.
 * <p>
 *     关联特定的数据与 selection key 的关系是有必要的。举个例子，一个对象代表了高层协议的
 *     状态，为了实现该协议，对象还要处理准备就绪的通知。因此， selection key 支持附上一个任意的对象。
 *     对象可以通过 selectionKey 的 attach 方法与 selectionKey 关联，可以通过 attachment
 *     方法获取 selectionKey 关联的 object。
 *
 * <p> Selection keys are safe for use by multiple concurrent threads.  A
 * selection operation will always use the interest-set value that was current
 * at the moment that the operation began.  </p>
 * <p>
 *     selection key 在多线程环境侠士安全的。selection operation 在访问 interest-set 中的
 *     值的时候， 能保证 interest-set 中的值是 operation 开始的时候的状态。
 * </p>
 *
 *
 * @author Mark Reinhold
 * @author JSR-51 Expert Group
 * @since 1.4
 *
 * @see SelectableChannel
 * @see Selector
 */

public abstract class SelectionKey {

    /**
     * Constructs an instance of this class.
     */
    protected SelectionKey() { }


    // -- Channel and selector operations --

    /**
     * 返回创建该 SelectionKey 的 Channel，即使 key 被取消了，
     * 还是能够返回相关联的 channel。
     * <p>
     * Returns the channel for which this key was created.  This method will
     * continue to return the channel even after the key is cancelled.
     *
     * @return  This key's channel
     */
    public abstract SelectableChannel channel();

    /**
     * 返回创建该 key 的 selector。key 被取消了，也还是会返回 Selector
     * <p>
     * Returns the selector for which this key was created.  This method will
     * continue to return the selector even after the key is cancelled.
     *
     * @return  This key's selector
     */
    public abstract Selector selector();

    /**
     * 返回 key 是否有效。有三种情况 key 会失效。
     * 1. key 被 cancel。
     * 2. channel 被 close。
     * 3. selector 被 close。
     * <p>
     * Tells whether or not this key is valid.
     *
     * <p> A key is valid upon creation and remains so until it is cancelled,
     * its channel is closed, or its selector is closed.  </p>
     *
     * @return  {@code true} if, and only if, this key is valid
     */
    public abstract boolean isValid();

    /**
     * 取消 channel 与 selector 的注册关系。selection key 会被 cancel，并且 key 会被放入到
     * cancelled-key set 中。在下一个 selection operation 的时候，被 cancelled 的 key 会从
     *  cancelled-key set 中移除。
     * <p>
     * Requests that the registration of this key's channel with its selector
     * be cancelled.  Upon return the key will be invalid and will have been
     * added to its selector's cancelled-key set.  The key will be removed from
     * all of the selector's key sets during the next selection operation.
     *
     * <p> 多次调用该方法无副作用（无效）</p>
     *
     * <p> If this key has already been cancelled then invoking this method has
     * no effect.  Once cancelled, a key remains forever invalid. </p>
     *
     * <p> This method may be invoked at any time.  It synchronizes on the
     * selector's cancelled-key set, and therefore may block briefly if invoked
     * concurrently with a cancellation or selection operation involving the
     * same selector.  </p>
     */
    public abstract void cancel();


    // -- Operation-set accessors --

    /**
     * 获取 key 的 interest set
     * <p>
     * Retrieves this key's interest set.
     *
     * <p> It is guaranteed that the returned set will only contain operation
     * bits that are valid for this key's channel. </p>
     *
     * @return  This key's interest set
     *
     * @throws  CancelledKeyException
     *          If this key has been cancelled
     */
    public abstract int interestOps();

    /**
     * Sets this key's interest set to the given value.
     * <p>
     *     设置当前 key 的 interest set 为指定的值
     *
     * <p> This method may be invoked at any time.  If this method is invoked
     * while a selection operation is in progress then it has no effect upon
     * that operation; the change to the key's interest set will be seen by the
     * next selection operation.
     *
     * <p>
     *     key 的 interest set 的变动将会在下一个 selection operation 的时候被看到。
     *
     * @param  ops  The new interest set
     *
     * @return  This selection key
     *
     * @throws  IllegalArgumentException
     *          If a bit in the set does not correspond to an operation that
     *          is supported by this key's channel, that is, if
     *          {@code (ops & ~channel().validOps()) != 0}
     *
     * @throws  CancelledKeyException
     *          If this key has been cancelled
     */
    public abstract SelectionKey interestOps(int ops);

    /**
     * 加锁操作，interest set 或上指定的 opsration
     * <p>
     * Atomically sets this key's interest set to the bitwise union ("or") of
     * the existing interest set and the given value. This method is guaranteed
     * to be atomic with respect to other concurrent calls to this method or to
     * {@link #interestOpsAnd(int)}.
     *
     * <p> This method may be invoked at any time.  If this method is invoked
     * while a selection operation is in progress then it has no effect upon
     * that operation; the change to the key's interest set will be seen by the
     * next selection operation.
     *
     * @implSpec The default implementation synchronizes on this key and invokes
     * {@code interestOps()} and {@code interestOps(int)} to retrieve and set
     * this key's interest set.
     *
     * @param  ops  The interest set to apply
     *
     * @return  The previous interest set
     *
     * @throws  IllegalArgumentException
     *          If a bit in the set does not correspond to an operation that
     *          is supported by this key's channel, that is, if
     *          {@code (ops & ~channel().validOps()) != 0}
     *
     * @throws  CancelledKeyException
     *          If this key has been cancelled
     *
     * @since 11
     */
    public int interestOpsOr(int ops) {
        synchronized (this) {
            int oldVal = interestOps();
            interestOps(oldVal | ops);
            return oldVal;
        }
    }

    /**
     * 加锁操作，interest set 与上指定的 opsration
     * <p>
     * Atomically sets this key's interest set to the bitwise intersection ("and")
     * of the existing interest set and the given value. This method is guaranteed
     * to be atomic with respect to other concurrent calls to this method or to
     * {@link #interestOpsOr(int)}.
     *
     * <p> This method may be invoked at any time.  If this method is invoked
     * while a selection operation is in progress then it has no effect upon
     * that operation; the change to the key's interest set will be seen by the
     * next selection operation.
     *
     * @apiNote Unlike the {@code interestOps(int)} and {@code interestOpsOr(int)}
     * methods, this method does not throw {@code IllegalArgumentException} when
     * invoked with bits in the interest set that do not correspond to an
     * operation that is supported by this key's channel. This is to allow
     * operation bits in the interest set to be cleared using bitwise complement
     * values, e.g., {@code interestOpsAnd(~SelectionKey.OP_READ)} will remove
     * the {@code OP_READ} from the interest set without affecting other bits.
     *
     * @implSpec The default implementation synchronizes on this key and invokes
     * {@code interestOps()} and {@code interestOps(int)} to retrieve and set
     * this key's interest set.
     *
     * @param  ops  The interest set to apply
     *
     * @return  The previous interest set
     *
     * @throws  CancelledKeyException
     *          If this key has been cancelled
     *
     * @since 11
     */
    public int interestOpsAnd(int ops) {
        synchronized (this) {
            int oldVal = interestOps();
            interestOps(oldVal & ops);
            return oldVal;
        }
    }

    /**
     * 返回当前 key 的 ready-operation set
     * <p>
     * Retrieves this key's ready-operation set.
     *
     * <p> It is guaranteed that the returned set will only contain operation
     * bits that are valid for this key's channel.  </p>
     *
     * @return  This key's ready-operation set
     *
     * @throws  CancelledKeyException
     *          If this key has been cancelled
     */
    public abstract int readyOps();


    // -- Operation bits and bit-testing convenience methods --

    /**
     * 读操作常量
     * <p>
     * Operation-set bit for read operations.
     *
     * <p> Suppose that a selection key's interest set contains
     * {@code OP_READ} at the start of a <a
     * href="Selector.html#selop">selection operation</a>.  If the selector
     * detects that the corresponding channel is ready for reading, has reached
     * end-of-stream, has been remotely shut down for further reading, or has
     * an error pending, then it will add {@code OP_READ} to the key's
     * ready-operation set.  </p>
     */
    public static final int OP_READ = 1 << 0;

    /**
     * 写操作常量
     * <p>
     *
     * Operation-set bit for write operations.
     *
     * <p> Suppose that a selection key's interest set contains
     * {@code OP_WRITE} at the start of a <a
     * href="Selector.html#selop">selection operation</a>.  If the selector
     * detects that the corresponding channel is ready for writing, has been
     * remotely shut down for further writing, or has an error pending, then it
     * will add {@code OP_WRITE} to the key's ready set.  </p>
     */
    public static final int OP_WRITE = 1 << 2;

    /**
     * 连接操作常量
     * <p>
     *
     * Operation-set bit for socket-connect operations.
     *
     * <p> Suppose that a selection key's interest set contains
     * {@code OP_CONNECT} at the start of a <a
     * href="Selector.html#selop">selection operation</a>.  If the selector
     * detects that the corresponding socket channel is ready to complete its
     * connection sequence, or has an error pending, then it will add
     * {@code OP_CONNECT} to the key's ready set.  </p>
     */
    public static final int OP_CONNECT = 1 << 3;

    /**
     * accept 操作常量
     * <p>
     *
     * Operation-set bit for socket-accept operations.
     *
     * <p> Suppose that a selection key's interest set contains
     * {@code OP_ACCEPT} at the start of a <a
     * href="Selector.html#selop">selection operation</a>.  If the selector
     * detects that the corresponding server-socket channel is ready to accept
     * another connection, or has an error pending, then it will add
     * {@code OP_ACCEPT} to the key's ready set.  </p>
     */
    public static final int OP_ACCEPT = 1 << 4;

    /**
     * 返回当前的 key 绑定的 channel 是否已经可读
     * <p>
     * Tests whether this key's channel is ready for reading.
     *
     * <p> An invocation of this method of the form {@code k.isReadable()}
     * behaves in exactly the same way as the expression
     *
     * <blockquote><pre>{@code
     * k.readyOps() & OP_READ != 0
     * }</pre></blockquote>
     *
     * <p> If this key's channel does not support read operations then this
     * method always returns {@code false}.  </p>
     *
     * @return  {@code true} if, and only if,
                {@code readyOps() & OP_READ} is nonzero
     *
     * @throws  CancelledKeyException
     *          If this key has been cancelled
     */
    public final boolean isReadable() {
        return (readyOps() & OP_READ) != 0;
    }

    /**
     * 返回当前的 key 绑定的 channel 是否已经可写
     * <p>
     *
     * Tests whether this key's channel is ready for writing.
     *
     * <p> An invocation of this method of the form {@code k.isWritable()}
     * behaves in exactly the same way as the expression
     *
     * <blockquote><pre>{@code
     * k.readyOps() & OP_WRITE != 0
     * }</pre></blockquote>
     *
     * <p> If this key's channel does not support write operations then this
     * method always returns {@code false}.  </p>
     *
     * @return  {@code true} if, and only if,
     *          {@code readyOps() & OP_WRITE} is nonzero
     *
     * @throws  CancelledKeyException
     *          If this key has been cancelled
     */
    public final boolean isWritable() {
        return (readyOps() & OP_WRITE) != 0;
    }

    /**
     * 返回当前的 key 绑定的 channel 是否已经可连接
     * <p>
     *
     * Tests whether this key's channel has either finished, or failed to
     * finish, its socket-connection operation.
     *
     * <p> An invocation of this method of the form {@code k.isConnectable()}
     * behaves in exactly the same way as the expression
     *
     * <blockquote><pre>{@code
     * k.readyOps() & OP_CONNECT != 0
     * }</pre></blockquote>
     *
     * <p> If this key's channel does not support socket-connect operations
     * then this method always returns {@code false}.  </p>
     *
     * @return  {@code true} if, and only if,
     *          {@code readyOps() & OP_CONNECT} is nonzero
     *
     * @throws  CancelledKeyException
     *          If this key has been cancelled
     */
    public final boolean isConnectable() {
        return (readyOps() & OP_CONNECT) != 0;
    }

    /**
     * 返回当前的 key 绑定的 channel 是否已经可 accept
     * <p>
     *
     * Tests whether this key's channel is ready to accept a new socket
     * connection.
     *
     * <p> An invocation of this method of the form {@code k.isAcceptable()}
     * behaves in exactly the same way as the expression
     *
     * <blockquote><pre>{@code
     * k.readyOps() & OP_ACCEPT != 0
     * }</pre></blockquote>
     *
     * <p> If this key's channel does not support socket-accept operations then
     * this method always returns {@code false}.  </p>
     *
     * @return  {@code true} if, and only if,
     *          {@code readyOps() & OP_ACCEPT} is nonzero
     *
     * @throws  CancelledKeyException
     *          If this key has been cancelled
     */
    public final boolean isAcceptable() {
        return (readyOps() & OP_ACCEPT) != 0;
    }


    // -- Attachments --

    private volatile Object attachment;

    private static final AtomicReferenceFieldUpdater<SelectionKey,Object>
        attachmentUpdater = AtomicReferenceFieldUpdater.newUpdater(
            SelectionKey.class, Object.class, "attachment"
        );

    /**
     * 为当前 SelectionKey 附上一个 Object，同时只能 attach 一个对象。
     * 通过 attach 一个 null 对象来丢弃目前 attach 的对象。
     * <p>
     * Attaches the given object to this key.
     *
     * <p> An attached object may later be retrieved via the {@link #attachment()
     * attachment} method.  Only one object may be attached at a time; invoking
     * this method causes any previous attachment to be discarded.  The current
     * attachment may be discarded by attaching {@code null}.  </p>
     *
     * @param  ob
     *         The object to be attached; may be {@code null}
     *
     * @return  The previously-attached object, if any,
     *          otherwise {@code null}
     */
    public final Object attach(Object ob) {
        // 原子操作
        return attachmentUpdater.getAndSet(this, ob);
    }

    /**
     * 获取 attach 的对象
     * <p>
     * Retrieves the current attachment.
     *
     * @return  The object currently attached to this key,
     *          or {@code null} if there is no attachment
     */
    public final Object attachment() {
        return attachment;
    }

}
