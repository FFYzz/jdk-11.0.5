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

package java.nio.channels.spi;

import java.io.IOException;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.IllegalBlockingModeException;
import java.nio.channels.IllegalSelectorException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;


/**
 * Base implementation class for selectable channels.
 * <p> selectable channels 的基本实现
 *
 * <p> This class defines methods that handle the mechanics of channel
 * registration, deregistration, and closing.  It maintains the current
 * blocking mode of this channel as well as its current set of selection keys.
 * It performs all of the synchronization required to implement the {@link
 * java.nio.channels.SelectableChannel} specification.  Implementations of the
 * abstract protected methods defined in this class need not synchronize
 * against other threads that might be engaged in the same operations.
 * <p> 该类中定义了 channel 的注册，反注册，关闭方法。该类维护了 channel 的阻塞状态以及
 * 当前 channel 的 selection keys 集合。
 * </p>
 *
 *
 * @author Mark Reinhold
 * @author Mike McCloskey
 * @author JSR-51 Expert Group
 * @since 1.4
 */

public abstract class AbstractSelectableChannel
    extends SelectableChannel
{

    // The provider that created this channel
    /**
     * 创建 channel 的 provider
     */
    private final SelectorProvider provider;

    // Keys that have been created by registering this channel with selectors.
    // They are saved because if this channel is closed the keys must be
    // deregistered.  Protected by keyLock.
    //
    /**
     * 维护的 SelectionKey
     */
    private SelectionKey[] keys = null;
    /**
     * SelectionKey 的个数
     */
    private int keyCount = 0;

    // Lock for key set and count
    /**
     * 操作 key 的锁对象
     */
    private final Object keyLock = new Object();

    // Lock for registration and configureBlocking operations
    /**
     * 注册和配置 blocking 操作使用的锁
     */
    private final Object regLock = new Object();

    // True when non-blocking, need regLock to change;
    /**
     * 标识符，当前的 channel 是否为非阻塞的
     */
    private volatile boolean nonBlocking;

    /**
     * Initializes a new instance of this class.
     *
     * @param  provider
     *         The provider that created this channel
     */
    protected AbstractSelectableChannel(SelectorProvider provider) {
        this.provider = provider;
    }

    /**
     * Returns the provider that created this channel.
     * <p> 返回当前 channel 绑定的 SelectorProvider
     *
     * @return  The provider that created this channel
     */
    public final SelectorProvider provider() {
        return provider;
    }


    // -- Utility methods for the key set --

    /**
     * 向数组中放入 SelectionKey，并更新 keyCount
     */
    private void addKey(SelectionKey k) {
        // 必须持有 keyLock 锁，说明是一个内部嵌套调用到的方法
        assert Thread.holdsLock(keyLock);
        int i = 0;
        // 数组未满
        if ((keys != null) && (keyCount < keys.length)) {
            // Find empty element of key array
            // 放到数组中空的位置上
            for (i = 0; i < keys.length; i++)
                if (keys[i] == null)
                    break;
        } else if (keys == null) {
            // 数组未初始化，则初始化数组
            keys = new SelectionKey[2];
        } else {
            // Grow key array
            // 两倍扩容数组
            int n = keys.length * 2;
            SelectionKey[] ks =  new SelectionKey[n];
            // 复制操作
            for (i = 0; i < keys.length; i++)
                ks[i] = keys[i];
            keys = ks;
            i = keyCount;
        }
        // 放到位置上
        keys[i] = k;
        // 计数也要增加
        keyCount++;
    }

    /**
     * 在数组中寻找 SelectionKey
     */
    private SelectionKey findKey(Selector sel) {
        // 必须得持有锁，说明是一个内部嵌套调用到的方法
        assert Thread.holdsLock(keyLock);
        // 数组为 null，直接返回
        if (keys == null)
            return null;
        // 寻找
        for (int i = 0; i < keys.length; i++)
            // SelectionKey 绑定的 selector 要为指定的 selector
            if ((keys[i] != null) && (keys[i].selector() == sel))
                return keys[i];
        return null;

    }

    /**
     * 移除 SelectionKey
     */
    void removeKey(SelectionKey k) {                    // package-private
        // 加锁
        synchronized (keyLock) {
            for (int i = 0; i < keys.length; i++)
                if (keys[i] == k) {
                    // 置为 null
                    keys[i] = null;
                    keyCount--;
                }
            // 使 SelectionKey 失效，将标识置为 false
            ((AbstractSelectionKey)k).invalidate();
        }
    }

    /**
     * 返回维护的数组中是否有有效的 SelectionKey
     */
    private boolean haveValidKeys() {
        synchronized (keyLock) {
            if (keyCount == 0)
                return false;
            for (int i = 0; i < keys.length; i++) {
                // 调用 isValid 方法来判断
                if ((keys[i] != null) && keys[i].isValid())
                    return true;
            }
            return false;
        }
    }


    // -- Registration --

    /**
     * 返回当前 channel 是否已经注册到 selector 上
     */
    public final boolean isRegistered() {
        synchronized (keyLock) {
            return keyCount != 0;
        }
    }

    /**
     * 返回 channel 绑定到的 selector 上的 selectionkey
     */
    public final SelectionKey keyFor(Selector sel) {
        synchronized (keyLock) {
            return findKey(sel);
        }
    }

    /**
     * Registers this channel with the given selector, returning a selection key.
     * <p> 将 channel 注册到给定的 selector 上，返回一个 Selection key
     *
     * <p>  This method first verifies that this channel is open and that the
     * given initial interest set is valid.
     * <p> 该方法首先验证 channel 是否处于打开状态，以及给定的 initial interest set 是否
     * 有效。
     *
     * <p> If this channel is already registered with the given selector then
     * the selection key representing that registration is returned after
     * setting its interest set to the given value.
     * <p> 如果当前 channel 已经注册到了给定的 selector 上，那么会更新 selection key
     * 中的 interest set 为传入的 ops。
     *
     * <p> Otherwise this channel has not yet been registered with the given
     * selector, so the {@link AbstractSelector#register register} method of
     * the selector is invoked while holding the appropriate locks.  The
     * resulting key is added to this channel's key set before being returned.
     * <p> 如果 channel 没有注册到给定的 selector 上，那么 channel 会被注册到 selector
     * 上，并且 selection key 会被添加到 channel 的 key set 中去。
     * </p>
     *
     * @throws  ClosedSelectorException {@inheritDoc}
     *
     * @throws  IllegalBlockingModeException {@inheritDoc}
     *
     * @throws  IllegalSelectorException {@inheritDoc}
     *
     * @throws  CancelledKeyException {@inheritDoc}
     *
     * @throws  IllegalArgumentException {@inheritDoc}
     */
    public final SelectionKey register(Selector sel, int ops, Object att)
        throws ClosedChannelException
    {
        // 检查 ops 是否符合该 channel 支持的 ops
        if ((ops & ~validOps()) != 0)
            throw new IllegalArgumentException();
        // 检查 channel 是否处于 open 状态
        if (!isOpen())
            throw new ClosedChannelException();
        // 加注册锁
        synchronized (regLock) {
            // 如果是 blocking mode
            if (isBlocking())
                // 抛异常
                throw new IllegalBlockingModeException();
            // 加 key 锁
            synchronized (keyLock) {
                // re-check if channel has been closed
                // 再次检查 channel 是否打开
                if (!isOpen())
                    throw new ClosedChannelException();
                // 根据 selector 找 SelectionKey
                SelectionKey k = findKey(sel);
                // 找到
                if (k != null) {
                    // 更新 attachment
                    k.attach(att);
                    // 更新 ops
                    k.interestOps(ops);
                    // 找不到，则注册
                } else {
                    // New registration
                    k = ((AbstractSelector)sel).register(this, ops, att);
                    // 将 selection key 加进去
                    addKey(k);
                }
                return k;
            }
        }
    }


    // -- Closing --

    /**
     * Closes this channel.
     * <p> 关闭 channel
     *
     * <p> This method, which is specified in the {@link
     * AbstractInterruptibleChannel} class and is invoked by the {@link
     * java.nio.channels.Channel#close close} method, in turn invokes the
     * {@link #implCloseSelectableChannel implCloseSelectableChannel} method in
     * order to perform the actual work of closing this channel.  It then
     * cancels all of this channel's keys.
     * <p> 该方法会由 Channel#close 方法调用过来。implCloseChannel 执行真正关闭的逻辑。
     * 随后会 cancel 该 channel 的 selection key。
     * </p>
     */
    protected final void implCloseChannel() throws IOException {
        // 真正的逻辑
        implCloseSelectableChannel();

        // clone keys to avoid calling cancel when holding keyLock
        SelectionKey[] copyOfKeys = null;
        synchronized (keyLock) {
            if (keys != null) {
                copyOfKeys = keys.clone();
            }
        }

        if (copyOfKeys != null) {
            for (SelectionKey k : copyOfKeys) {
                if (k != null) {
                    // 取消所有的 key
                    k.cancel();   // invalidate and adds key to cancelledKey set
                }
            }
        }
    }

    /**
     * Closes this selectable channel.
     *
     * <p> This method is invoked by the {@link java.nio.channels.Channel#close
     * close} method in order to perform the actual work of closing the
     * channel.  This method is only invoked if the channel has not yet been
     * closed, and it is never invoked more than once.
     *
     * <p> An implementation of this method must arrange for any other thread
     * that is blocked in an I/O operation upon this channel to return
     * immediately, either by throwing an exception or by returning normally.
     * </p>
     *
     * @throws  IOException
     *          If an I/O error occurs
     */
    protected abstract void implCloseSelectableChannel() throws IOException;


    // -- Blocking --

    public final boolean isBlocking() {
        return !nonBlocking;
    }

    public final Object blockingLock() {
        return regLock;
    }

    /**
     * Adjusts this channel's blocking mode.
     * <p> 调整 channel 的 blocking mode
     *
     * <p> If the given blocking mode is different from the current blocking
     * mode then this method invokes the {@link #implConfigureBlocking
     * implConfigureBlocking} method, while holding the appropriate locks, in
     * order to change the mode.
     * <p> 如果给定的 blocking mode 与当前 channel 的 blocking mode 不同，那么会调用
     * implConfigureBlocking 方法进行调整。
     * </p>
     */
    public final SelectableChannel configureBlocking(boolean block)
        throws IOException
    {
        // 加锁
        synchronized (regLock) {
            // 如果 channel 已经关闭
            if (!isOpen())
                // 抛异常
                throw new ClosedChannelException();
            boolean blocking = !nonBlocking;
            // 模式不同才会调整
            if (block != blocking) {
                // 如果当前处于 非阻塞 模式且有 valid key
                if (block && haveValidKeys())
                    // 不能调整
                    throw new IllegalBlockingModeException();
                // 真正的逻辑
                implConfigureBlocking(block);
                nonBlocking = !block;
            }
        }
        return this;
    }

    /**
     * Adjusts this channel's blocking mode.
     *
     * <p> This method is invoked by the {@link #configureBlocking
     * configureBlocking} method in order to perform the actual work of
     * changing the blocking mode.  This method is only invoked if the new mode
     * is different from the current mode.  </p>
     *
     * @param  block  If {@code true} then this channel will be placed in
     *                blocking mode; if {@code false} then it will be placed
     *                non-blocking mode
     *
     * @throws IOException
     *         If an I/O error occurs
     */
    protected abstract void implConfigureBlocking(boolean block)
        throws IOException;

}
