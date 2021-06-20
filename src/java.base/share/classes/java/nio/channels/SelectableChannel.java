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
import java.nio.channels.spi.AbstractInterruptibleChannel;
import java.nio.channels.spi.SelectorProvider;


/**
 * A channel that can be multiplexed via a {@link Selector}.
 * <p>
 *     该 channel 可以通过 selector 实现多路复用
 *
 * <p> In order to be used with a selector, an instance of this class must
 * first be <i>registered</i> via the {@link #register(Selector,int,Object)
 * register} method.  This method returns a new {@link SelectionKey} object
 * that represents the channel's registration with the selector.
 * <p>
 *     为了与 selector 一起使用，该 channel 的实例必须通过 register 方法进行注册。
 *     register 方法返回一个 SelectionKey 对象，代表 channel 与  selector 的注册信息。
 *
 * <p> Once registered with a selector, a channel remains registered until it
 * is <i>deregistered</i>.  This involves deallocating whatever resources were
 * allocated to the channel by the selector.
 * <p>
 *     一旦注册到了 selector 上，channel 都是处于 registered 状态，直到被反注册。
 *
 * <p> A channel cannot be deregistered directly; instead, the key representing
 * its registration must be <i>cancelled</i>.  Cancelling a key requests that
 * the channel be deregistered during the selector's next selection operation.
 * A key may be cancelled explicitly by invoking its {@link
 * SelectionKey#cancel() cancel} method.  All of a channel's keys are cancelled
 * implicitly when the channel is closed, whether by invoking its {@link
 * Channel#close close} method or by interrupting a thread blocked in an I/O
 * operation upon the channel.
 * <p>
 *     一个 channel 不能被直接反注册。相反地，代表着注册关系的 selection key 必须被取消。
 *     key 可以通过调用 SelectionKey#cancel 方法来取消。无论是通过调用 Channel#close
 *     方法，或者是中断一个阻塞在当前 channel 上的 IO 线程，当 channel 关闭的时候，
 *     与 channel 关联的所有的 key 都会被取消
 *
 * <p> If the selector itself is closed then the channel will be deregistered,
 * and the key representing its registration will be invalidated, without
 * further delay.
 * <p>
 *     如果 selector 被关闭了，那么 channel 也会被反注册，key 也会立刻失效。
 *
 * <p> A channel may be registered at most once with any particular selector.
 * <p>
 *     channel 只能注册到一个 selector 上
 *
 * <p> Whether or not a channel is registered with one or more selectors may be
 * determined by invoking the {@link #isRegistered isRegistered} method.
 * <p>
 *     通过调用 isRegistered 方法可以知道 channel 是否已经被注册。
 *
 * <p> Selectable channels are safe for use by multiple concurrent
 * threads.
 * <p>
 *     Selectable channels 是线程安全的。
 * </p>
 *
 *
 * <a id="bm"></a>
 * <h2>Blocking mode</h2>
 *
 * A selectable channel is either in <i>blocking</i> mode or in
 * <i>non-blocking</i> mode.  In blocking mode, every I/O operation invoked
 * upon the channel will block until it completes.  In non-blocking mode an I/O
 * operation will never block and may transfer fewer bytes than were requested
 * or possibly no bytes at all.  The blocking mode of a selectable channel may
 * be determined by invoking its {@link #isBlocking isBlocking} method.
 * <p>
 *    selectable channel 要么是 blocking 模式，要么是 non-blocking 模式。 blocking 模式中，
 *    每个在该 channel 上的 IO 操作都会阻塞，直到 IO 操作完成。non-blocking 模式中，
 *
 * <p> Newly-created selectable channels are always in blocking mode.
 * Non-blocking mode is most useful in conjunction with selector-based
 * multiplexing.  A channel must be placed into non-blocking mode before being
 * registered with a selector, and may not be returned to blocking mode until
 * it has been deregistered.
 * <p>
 *     新创建的 selectable channels 总是处于阻塞模式。
 *
 *
 * @author Mark Reinhold
 * @author JSR-51 Expert Group
 * @since 1.4
 *
 * @see SelectionKey
 * @see Selector
 */

public abstract class SelectableChannel
    extends AbstractInterruptibleChannel
    implements Channel
{

    /**
     * Initializes a new instance of this class.
     */
    protected SelectableChannel() { }

    /**
     * 返回这个 Channel 注册的 Selector 的 Provider
     * <p>
     * Returns the provider that created this channel.
     *
     * @return  The provider that created this channel
     */
    public abstract SelectorProvider provider();

    /**
     * 返回一个 SelectionKey 的 bit 集合，表示该 Selector 支持的
     * 操作。
     * <p>
     * Returns an <a href="SelectionKey.html#opsets">operation set</a>
     * identifying this channel's supported operations.  The bits that are set
     * in this integer value denote exactly the operations that are valid for
     * this channel.  This method always returns the same value for a given
     * concrete channel class.
     *
     * @return  The valid-operation set
     */
    public abstract int validOps();

    /**
     * 返回当前 channel 是否已经注册到 Selector 上。
     * <p>
     * Tells whether or not this channel is currently registered with any
     * selectors.  A newly-created channel is not registered.
     *
     * <p> Due to the inherent delay between key cancellation and channel
     * deregistration, a channel may remain registered for some time after all
     * of its keys have been cancelled.  A channel may also remain registered
     * for some time after it is closed.  </p>
     *
     * @return {@code true} if, and only if, this channel is registered
     */
    public abstract boolean isRegistered();

    /**
     * 返回 channel 注册到 sel 上的 SelectionKey
     * <p>
     * Retrieves the key representing the channel's registration with the given
     * selector.
     *
     * @param   sel
     *          The selector
     *
     * @return  The key returned when this channel was last registered with the
     *          given selector, or {@code null} if this channel is not
     *          currently registered with that selector
     */
    public abstract SelectionKey keyFor(Selector sel);

    /**
     * Registers this channel with the given selector, returning a selection
     * key.
     * <p> 将 channel 注册到给定的 selector 上，返回一个 selection key。
     *
     * <p> If this channel is currently registered with the given selector then
     * the selection key representing that registration is returned.  The key's
     * interest set will have been changed to {@code ops}, as if by invoking
     * the {@link SelectionKey#interestOps(int) interestOps(int)} method.  If
     * the {@code att} argument is not {@code null} then the key's attachment
     * will have been set to that value.  A {@link CancelledKeyException} will
     * be thrown if the key has already been cancelled.
     * <p> 如果当前 channel 已经注册到给定的 selector 上。那么返回的是当前 channel 注册到
     * 的之前的 selector 上的 selection key。 selection key 的 interest set 将会更新
     * 为传入的 ops，与调用 SelectionKey#interestOps 方法的效果是一样的。同样也会更新 selectionKey
     * 的 attachment。如果 selectionKey 已经被取消了，那么会抛出 CancelledKeyException 异常。
     *
     * <p> Otherwise this channel has not yet been registered with the given
     * selector, so it is registered and the resulting new key is returned.
     * The key's initial interest set will be {@code ops} and its attachment
     * will be {@code att}.
     * <p> 如果 channel 没有注册到给定的 selector 上，那么会注册到 selector 上去。
     *
     * <p> This method may be invoked at any time.  If this method is invoked
     * while a selection operation is in progress then it has no effect upon
     * that operation; the new registration or change to the key's interest set
     * will be seen by the next selection operation.  If this method is invoked
     * while an invocation of {@link #configureBlocking(boolean) configureBlocking}
     * is in progress then it will block until the channel's blocking mode has
     * been adjusted.
     * <p> 该方法可以被调用多次。如果该方法被调用的时候，正在执行 selection operation，不会对
     * selection operation 造成影响。新注册的 channel 或者更新的 interest set 将会在下一次
     * selection operation 的时候被看到。如果该方法调用的时候 configureBlocking 在执行，那么
     * 该方法会阻塞，直到 configureBlocking 方法执行完。
     *
     * <p> If this channel is closed while this operation is in progress then
     * the key returned by this method will have been cancelled and will
     * therefore be invalid. </p>
     *
     * @param  sel
     *         The selector with which this channel is to be registered
     *
     * @param  ops
     *         The interest set for the resulting key
     *
     * @param  att
     *         The attachment for the resulting key; may be {@code null}
     *
     * @throws  ClosedChannelException
     *          If this channel is closed
     *
     * @throws  ClosedSelectorException
     *          If the selector is closed
     *
     * @throws  IllegalBlockingModeException
     *          If this channel is in blocking mode
     *
     * @throws  IllegalSelectorException
     *          If this channel was not created by the same provider
     *          as the given selector
     *
     * @throws  CancelledKeyException
     *          If this channel is currently registered with the given selector
     *          but the corresponding key has already been cancelled
     *
     * @throws  IllegalArgumentException
     *          If a bit in the {@code ops} set does not correspond to an
     *          operation that is supported by this channel, that is, if
     *          {@code set & ~validOps() != 0}
     *
     * @return  A key representing the registration of this channel with
     *          the given selector
     */
    public abstract SelectionKey register(Selector sel, int ops, Object att)
        throws ClosedChannelException;

    /**
     * Registers this channel with the given selector, returning a selection
     * key.
     *
     * <p> An invocation of this convenience method of the form
     *
     * <blockquote>{@code sc.register(sel, ops)}</blockquote>
     *
     * behaves in exactly the same way as the invocation
     *
     * <blockquote>{@code sc.}{@link
     * #register(java.nio.channels.Selector,int,java.lang.Object)
     * register(sel, ops, null)}</blockquote>
     *
     * @param  sel
     *         The selector with which this channel is to be registered
     *
     * @param  ops
     *         The interest set for the resulting key
     *
     * @throws  ClosedChannelException
     *          If this channel is closed
     *
     * @throws  ClosedSelectorException
     *          If the selector is closed
     *
     * @throws  IllegalBlockingModeException
     *          If this channel is in blocking mode
     *
     * @throws  IllegalSelectorException
     *          If this channel was not created by the same provider
     *          as the given selector
     *
     * @throws  CancelledKeyException
     *          If this channel is currently registered with the given selector
     *          but the corresponding key has already been cancelled
     *
     * @throws  IllegalArgumentException
     *          If a bit in {@code ops} does not correspond to an operation
     *          that is supported by this channel, that is, if {@code set &
     *          ~validOps() != 0}
     *
     * @return  A key representing the registration of this channel with
     *          the given selector
     */
    public final SelectionKey register(Selector sel, int ops)
        throws ClosedChannelException
    {
        return register(sel, ops, null);
    }

    /**
     * Adjusts this channel's blocking mode.
     * <p> 改变 channel 的阻塞模式。
     *
     * <p> If this channel is registered with one or more selectors then an
     * attempt to place it into blocking mode will cause an {@link
     * IllegalBlockingModeException} to be thrown.
     * <p> 如果一个 channel 注册了多个 selector，调用该方法会抛出 IllegalBlockingModeException
     * 异常。
     *
     * <p> This method may be invoked at any time.  The new blocking mode will
     * only affect I/O operations that are initiated after this method returns.
     * For some implementations this may require blocking until all pending I/O
     * operations are complete.
     * <p> 该方法可以在任意时刻被调用。
     *
     * <p> If this method is invoked while another invocation of this method or
     * of the {@link #register(Selector, int) register} method is in progress
     * then it will first block until the other operation is complete. </p>
     * <p> 如果调用该方法的时候，其他线程正在调用该方法或者调用 register 方法，那么该方法
     * 会被阻塞。
     *
     * @param  block  If {@code true} then this channel will be placed in
     *                blocking mode; if {@code false} then it will be placed
     *                non-blocking mode
     *
     * @return  This selectable channel
     *
     * @throws  ClosedChannelException
     *          If this channel is closed
     *
     * @throws  IllegalBlockingModeException
     *          If {@code block} is {@code true} and this channel is
     *          registered with one or more selectors
     *
     * @throws IOException
     *         If an I/O error occurs
     */
    public abstract SelectableChannel configureBlocking(boolean block)
        throws IOException;

    /**
     * Tells whether or not every I/O operation on this channel will block
     * until it completes.  A newly-created channel is always in blocking mode.
     *
     * <p> If this channel is closed then the value returned by this method is
     * not specified. </p>
     *
     * @return {@code true} if, and only if, this channel is in blocking mode
     */
    public abstract boolean isBlocking();

    /**
     * Retrieves the object upon which the {@link #configureBlocking
     * configureBlocking} and {@link #register register} methods synchronize.
     * This is often useful in the implementation of adaptors that require a
     * specific blocking mode to be maintained for a short period of time.
     *
     * @return  The blocking-mode lock object
     */
    public abstract Object blockingLock();

}
