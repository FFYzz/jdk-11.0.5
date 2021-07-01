/*
 * Copyright (c) 2000, 2013, Oracle and/or its affiliates. All rights reserved.
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

import java.nio.channels.*;


/**
 * Base implementation class for selection keys.
 * <p> selection keys 的基本实现。
 *
 * <p> This class tracks the validity of the key and implements cancellation.
 * <p> 该类可以跟踪 selection key 的有效性并且实现了 cancel 的方法
 *
 * @author Mark Reinhold
 * @author JSR-51 Expert Group
 * @since 1.4
 */

public abstract class AbstractSelectionKey
    extends SelectionKey
{

    /**
     * Initializes a new instance of this class.
     */
    protected AbstractSelectionKey() { }

    /**
     * 表示当前 SelectionKey 是否有效
     */
    private volatile boolean valid = true;

    public final boolean isValid() {
        return valid;
    }

    /**
     * 使 SelectionKey 失效，直接置为 false
     */
    void invalidate() {                                 // package-private
        valid = false;
    }

    /**
     * Cancels this key.
     * <p> 取消 SelectionKey
     *
     * <p> If this key has not yet been cancelled then it is added to its
     * selector's cancelled-key set while synchronized on that set.
     * 如果当前 key 没有被取消，那么会被加入到 Selector 的 cancelled-key set 中。
     * </p>
     */
    public final void cancel() {
        // Synchronizing "this" to prevent this key from getting canceled
        // multiple times by different threads, which might cause race
        // condition between selector's select() and channel's close().
        synchronized (this) {
            if (valid) {
                valid = false;
                // 调用 AbstractSelector 的 cancel 方法
                ((AbstractSelector)selector()).cancel(this);
            }
        }
    }
}
