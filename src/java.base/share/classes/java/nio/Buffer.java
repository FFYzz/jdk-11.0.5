/*
 * Copyright (c) 2000, 2020, Oracle and/or its affiliates. All rights reserved.
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

package java.nio;

import jdk.internal.HotSpotIntrinsicCandidate;
import jdk.internal.misc.JavaNioAccess;
import jdk.internal.misc.SharedSecrets;
import jdk.internal.misc.Unsafe;

import java.util.Spliterator;

/**
 * A container for data of a specific primitive type.
 * <p>
 *     指定数据类型的容器
 *
 * <p> A buffer is a linear, finite sequence of elements of a specific
 * primitive type.  Aside from its content, the essential properties of a
 * buffer are its capacity, limit, and position:
 * <p>
 *     一个 buffer 是一个线性的，优先的指定数据类型的元素序列。撇开 buffer 中的内容不谈，
 *     buffer 的重要属性包括 它的容量，限制，以及位置。
 * </p>
 *
 * <blockquote>
 *
 *   <p> A buffer's <i>capacity</i> is the number of elements it contains.  The
 *   capacity of a buffer is never negative and never changes.
 *   <p>
 *       buffer 的 capacity 是能够容纳元素的个数，capacity 不允许改变也不会为负数。
 *   </p>
 *
 *   <p> A buffer's <i>limit</i> is the index of the first element that should
 *   not be read or written.  A buffer's limit is never negative and is never
 *   greater than its capacity.
 *   <p>
 *       buffer 的 limit 是指 buffer 中不能够读/写的第一个元素的下标，不会为负数，也不会大于 capacity。
 *   </p>
 *
 *   <p> A buffer's <i>position</i> is the index of the next element to be
 *   read or written.  A buffer's position is never negative and is never
 *   greater than its limit.
 *   <p>
 *       buffer 的 position 属性是指下一个将被读写的元素的下标。不会为负数，也不会大于 capacity。
 *   </p>
 *
 * </blockquote>
 *
 * <p> There is one subclass of this class for each non-boolean primitive type.
 * <p>
 *     每个 primitive type 的类型都有一个子类，继承该类，除了 boolean 类型。
 *
 *
 * <h2> Transferring data </h2>
 *
 * <p> Each subclass of this class defines two categories of <i>get</i> and
 * <i>put</i> operations:
 * <p>
 *     当前类的之类都定义了 get/put 操作
 * </p>
 *
 * <blockquote>
 *
 *   <p> <i>Relative</i> operations read or write one or more elements starting
 *   at the current position and then increment the position by the number of
 *   elements transferred.  If the requested transfer exceeds the limit then a
 *   relative <i>get</i> operation throws a {@link BufferUnderflowException}
 *   and a relative <i>put</i> operation throws a {@link
 *   BufferOverflowException}; in either case, no data is transferred.
 *   <p>
 *       相对的操作。如果相对的 get 操作超出了限制，那么会抛出 BufferUnderflowException 异常。
 *       put 操作则会抛出 BufferOverflowException 异常。
 *   </p>
 *
 *   <p> <i>Absolute</i> operations take an explicit element index and do not
 *   affect the position.  Absolute <i>get</i> and <i>put</i> operations throw
 *   an {@link IndexOutOfBoundsException} if the index argument exceeds the
 *   limit.
 *   <p>
 *       绝对的操作。绝对操作中，get/put 方法越界会抛出 IndexOutOfBoundsException 异常。
 *   </p>
 *
 * </blockquote>
 *
 * <p> Data may also, of course, be transferred in to or out of a buffer by the
 * I/O operations of an appropriate channel, which are always relative to the
 * current position.
 *
 *
 * <h2> Marking and resetting </h2>
 *
 * <p> A buffer's <i>mark</i> is the index to which its position will be reset
 * when the {@link #reset reset} method is invoked.  The mark is not always
 * defined, but when it is defined it is never negative and is never greater
 * than the position.  If the mark is defined then it is discarded when the
 * position or the limit is adjusted to a value smaller than the mark.  If the
 * mark is not defined then invoking the {@link #reset reset} method causes an
 * {@link InvalidMarkException} to be thrown.
 * <p>
 *     buffer 的 mark 属性标记了一个位置，在调用 reset 方法的时候，会移动到该位置。
 *     mark 未设置调用了 reset 方法，会抛出 InvalidMarkException 异常。
 *
 * <h2> Invariants </h2>
 *
 * <p> The following invariant holds for the mark, position, limit, and
 * capacity values:
 * <p>
 *     有下面不等式成立:
 *
 * <blockquote>
 *     {@code 0} {@code <=}
 *     <i>mark</i> {@code <=}
 *     <i>position</i> {@code <=}
 *     <i>limit</i> {@code <=}
 *     <i>capacity</i>
 * </blockquote>
 *
 * <p> A newly-created buffer always has a position of zero and a mark that is
 * undefined.  The initial limit may be zero, or it may be some other value
 * that depends upon the type of the buffer and the manner in which it is
 * constructed.  Each element of a newly-allocated buffer is initialized
 * to zero.
 * <p>
 *     新创建的 buffer 的 position 为 0，并且 mark 未初始化。初始化时的 limit 可能为 0，
 *     也可能是其他值，取决于 buffer 的类型以及 buffer 创建的方式。buffer 中的数据初始化为
 *     零值。
 *
 * <h2> Additional operations | 一些操作 </h2>
 *
 * <p> In addition to methods for accessing the position, limit, and capacity
 * values and for marking and resetting, this class also defines the following
 * operations upon buffers:
 * <p>
 *     为了访问 position、limit、capacity，以及 mark 和 reset，定义了如下操作:
 *
 * <ul>
 *
 *   <li><p> {@link #clear} makes a buffer ready for a new sequence of
 *   channel-read or relative <i>put</i> operations: It sets the limit to the
 *   capacity and the position to zero.
 *   <p>
 *       clear 操作将 limit 的值设为 capacity，position 的值设为 0. buffer 可以保存
 *       read 的数据。
 *   </p></li>
 *
 *   <li><p> {@link #flip} makes a buffer ready for a new sequence of
 *   channel-write or relative <i>get</i> operations: It sets the limit to the
 *   current position and then sets the position to zero.
 *   <p>
 *       flip 操作将 limit 的值设为 position 的位置，并将 position 设为 0。可以将 buffer
 *       中的数据 write 到 channel 中去。
 *   </p></li>
 *
 *   <li><p> {@link #rewind} makes a buffer ready for re-reading the data that
 *   it already contains: It leaves the limit unchanged and sets the position
 *   to zero.
 *   <p>
 *       rewind 操作不改变 limit 的值，将 position 的值更新为 0。用于重新读取一段数据。
 *   </p></li>
 *
 *   <li><p> {@link #slice} creates a subsequence of a buffer: It leaves the
 *   limit and the position unchanged.
 *   <p>
 *       slice 操作不改变 limit 和 position 的值。调用该方法将创建 buffer 的子序列。
 *   </p></li>
 *
 *   <li><p> {@link #duplicate} creates a shallow copy of a buffer: It leaves
 *   the limit and the position unchanged.
 *   <p>
 *       duplicate 不改变 limit 和 position 的值，而是将 buffer 进行浅拷贝。
 *   </p></li>
 *
 * </ul>
 *
 *
 * <h2> Read-only buffers | 只读 buffer </h2>
 *
 * <p> Every buffer is readable, but not every buffer is writable.  The
 * mutation methods of each buffer class are specified as <i>optional
 * operations</i> that will throw a {@link ReadOnlyBufferException} when
 * invoked upon a read-only buffer.  A read-only buffer does not allow its
 * content to be changed, but its mark, position, and limit values are mutable.
 * Whether or not a buffer is read-only may be determined by invoking its
 * {@link #isReadOnly isReadOnly} method.
 * <p>
 *     每个 buffer 都是可读的，但不是每个 buffer 都是可写的。对于不同的 buffer 实现类，
 *     调用不支持的改变 buffer 的方法将抛出 ReadOnlyBufferException 异常。只读 buffer
 *     不允许 buffer 中数据的改变。但是 buffer 的 mark、position、limit 的值是允许改变
 *     的。可以通过调用 isReadOnly 方法来判断 buffer 是否是可读的。
 *
 * <h2> Thread safety | 线程安全</h2>
 *
 * <p> Buffers are not safe for use by multiple concurrent threads.  If a
 * buffer is to be used by more than one thread then access to the buffer
 * should be controlled by appropriate synchronization.
 * <p>
 *     buffer 不是线程安全的。需要自己添加同步控制。
 *
 * <h2> Invocation chaining | 调用链</h2>
 *
 * <p> Methods in this class that do not otherwise have a value to return are
 * specified to return the buffer upon which they are invoked.  This allows
 * method invocations to be chained; for example, the sequence of statements
 * <p>
 *     类似于 builder 模式，会返回 Buffer 实例。
 *
 * <blockquote><pre>
 * b.flip();
 * b.position(23);
 * b.limit(42);</pre></blockquote>
 *
 * can be replaced by the single, more compact statement
 *
 * <blockquote><pre>
 * b.flip().position(23).limit(42);</pre></blockquote>
 *
 *
 * @author Mark Reinhold
 * @author JSR-51 Expert Group
 * @since 1.4
 */

public abstract class Buffer {
    // Cached unsafe-access object
    static final Unsafe UNSAFE = Unsafe.getUnsafe();

    /**
     * The characteristics of Spliterators that traverse and split elements
     * maintained in Buffers.
     */
    static final int SPLITERATOR_CHARACTERISTICS =
        Spliterator.SIZED | Spliterator.SUBSIZED | Spliterator.ORDERED;

    // Invariants: mark <= position <= limit <= capacity
    // 有以上不等式成立
    private int mark = -1;
    private int position = 0;
    private int limit;
    private int capacity;

    // Used by heap byte buffers or direct buffers with Unsafe access
    // For heap byte buffers this field will be the address relative to the
    // array base address and offset into that array. The address might
    // not align on a word boundary for slices, nor align at a long word
    // (8 byte) boundary for byte[] allocations on 32-bit systems.
    // For direct buffers it is the start address of the memory region. The
    // address might not align on a word boundary for slices, nor when created
    // using JNI, see NewDirectByteBuffer(void*, long).
    // Should ideally be declared final
    // NOTE: hoisted here for speed in JNI GetDirectBufferAddress
    // heap byte 使用或者通过 unsafe 访问 direct buffers 时会使用到的变量
    long address;

    // Creates a new buffer with the given mark, position, limit, and capacity,
    // after checking invariants.
    Buffer(int mark, int pos, int lim, int cap) {       // package-private
        // cap 不能小于 0
        if (cap < 0)
            throw createCapacityException(cap);
        // 首先设置 capacity
        this.capacity = cap;
        // 设置 limit
        limit(lim);
        // 设置 position
        position(pos);
        // 如果 mark 需要设置
        if (mark >= 0) {
            // mark 不能大于 position
            if (mark > pos)
                throw new IllegalArgumentException("mark > position: ("
                                                   + mark + " > " + pos + ")");
            this.mark = mark;
        }
    }

    /**
     * Returns an {@code IllegalArgumentException} indicating that the source
     * and target are the same {@code Buffer}.  Intended for use in
     * {@code put(src)} when the parameter is the {@code Buffer} on which the
     * method is being invoked.
     * <p>
     *     创建相同的 buffer 的时候抛出的异常。
     *
     * @return  IllegalArgumentException
     *          With a message indicating equal source and target buffers
     */
    static IllegalArgumentException createSameBufferException() {
        return new IllegalArgumentException("The source buffer is this buffer");
    }

    /**
     * Verify that the capacity is nonnegative.
     * <p>
     *     检查 capacity 参数是否合法
     *
     * @param  capacity
     *         The new buffer's capacity, in $type$s
     *
     * @throws  IllegalArgumentException
     *          If the {@code capacity} is a negative integer
     */
    static IllegalArgumentException createCapacityException(int capacity) {
        // capacity 不能小于 0
        assert capacity < 0 : "capacity expected to be negative";
        return new IllegalArgumentException("capacity < 0: ("
            + capacity + " < 0)");
    }

    /**
     * Returns this buffer's capacity.
     * <p>
     *     返回 buffer 的capacity
     *
     * @return  The capacity of this buffer
     */
    public final int capacity() {
        return capacity;
    }

    /**
     * Returns this buffer's position.
     * <p>
     *     返回 buffer 的 position
     *
     * @return  The position of this buffer
     */
    public final int position() {
        return position;
    }

    /**
     * Sets this buffer's position.  If the mark is defined and larger than the
     * new position then it is discarded.
     * <p>
     *     设置 buffer 的position，如果 mark 已经定义了，并且该值比传入的 position 大，
     *     那么 mark 将被丢弃。
     *
     * @param  newPosition
     *         The new position value; must be non-negative
     *         and no larger than the current limit
     *
     * @return  This buffer
     *
     * @throws  IllegalArgumentException
     *          If the preconditions on {@code newPosition} do not hold
     */
    public Buffer position(int newPosition) {
        // position 不能比 limit 大
        if (newPosition > limit | newPosition < 0)
            throw createPositionException(newPosition);
        // 更新 position
        position = newPosition;
        // 检查 mark
        if (mark > position) mark = -1;
        return this;
    }

    /**
     * Verify that {@code 0 < newPosition <= limit}
     * <p>
     *     检验 position 的值
     *
     * @param newPosition
     *        The new position value
     *
     * @throws IllegalArgumentException
     *         If the specified position is out of bounds.
     */
    private IllegalArgumentException createPositionException(int newPosition) {
        String msg = null;

        // position 不能大于 limit
        if (newPosition > limit) {
            msg = "newPosition > limit: (" + newPosition + " > " + limit + ")";
        } else { // assume negative
            // position 不能小于 0
            assert newPosition < 0 : "newPosition expected to be negative";
            msg = "newPosition < 0: (" + newPosition + " < 0)";
        }

        return new IllegalArgumentException(msg);
    }

    /**
     * Returns this buffer's limit.
     * <p>
     *     返回 buffer 的 limit
     *
     * @return  The limit of this buffer
     */
    public final int limit() {
        return limit;
    }

    /**
     * Sets this buffer's limit.  If the position is larger than the new limit
     * then it is set to the new limit.  If the mark is defined and larger than
     * the new limit then it is discarded.
     * <p>
     *     设置 buffer 的limit。如果 position 比传入的 limit 值要大，那么 position 的值
     *     会被设置为新 limit 的值。如果 mark 的已经设置了，并且比传入的 limit 的值要打，
     *     那么 mark 值将会被丢弃。
     *
     * @param  newLimit
     *         The new limit value; must be non-negative
     *         and no larger than this buffer's capacity
     *
     * @return  This buffer
     *
     * @throws  IllegalArgumentException
     *          If the preconditions on {@code newLimit} do not hold
     */
    public Buffer limit(int newLimit) {
        // 检查
        // limit 不能大于 capacity
        if (newLimit > capacity | newLimit < 0)
            throw createLimitException(newLimit);
        // 更新 limit
        limit = newLimit;
        // 检查 position，position 比传入 limit 的值要大，将 position 设为传入的 limit
        // position 不能超过 limit
        if (position > newLimit) position = newLimit;
        // 检查 mark
        if (mark > newLimit) mark = -1;
        return this;
    }

    /**
     * Verify that {@code 0 < newLimit <= capacity}
     * <p>
     *     检查 limit
     *
     * @param newLimit
     *        The new limit value
     *
     * @throws IllegalArgumentException
     *         If the specified limit is out of bounds.
     */
    private IllegalArgumentException createLimitException(int newLimit) {
        String msg = null;
        // limit 不能大于 capacity
        if (newLimit > capacity) {
            msg = "newLimit > capacity: (" + newLimit + " > " + capacity + ")";
        } else { // assume negative
            // limit 不能小于 0
            assert newLimit < 0 : "newLimit expected to be negative";
            msg = "newLimit < 0: (" + newLimit + " < 0)";
        }

        return new IllegalArgumentException(msg);
    }

    /**
     * Sets this buffer's mark at its position.
     * <p>
     *     将 mark 的值设为当前的 position
     *
     * @return  This buffer
     */
    public Buffer mark() {
        mark = position;
        return this;
    }

    /**
     * Resets this buffer's position to the previously-marked position.
     * <p>
     *     将 position 置为之前 mark 的位置，用在重新读取数据的场景
     *
     * <p> Invoking this method neither changes nor discards the mark's
     * value.
     * <p>
     *     调用该方法既不会改变 mark 的值，也不会丢弃 mark 的值
     * </p>
     *
     * @return  This buffer
     *
     * @throws  InvalidMarkException
     *          If the mark has not been set
     */
    public Buffer reset() {
        // 获取 mark 的值
        int m = mark;
        if (m < 0)
            throw new InvalidMarkException();
        // position 设置为 mark 的值
        position = m;
        return this;
    }

    /**
     * Clears this buffer.  The position is set to zero, the limit is set to
     * the capacity, and the mark is discarded.
     * <p>
     *     清空 buffer。position 设为 0，limit 设置为 capacity，并将之前 mark 的值丢弃。
     *     仅仅是更新几个变量。
     *
     * <p> Invoke this method before using a sequence of channel-read or
     * <i>put</i> operations to fill this buffer.  For example:
     *
     * <blockquote><pre>
     * buf.clear();     // Prepare buffer for reading
     * in.read(buf);    // Read data</pre></blockquote>
     *
     * <p> This method does not actually erase the data in the buffer, but it
     * is named as if it did because it will most often be used in situations
     * in which that might as well be the case.
     * <p>
     *     调用该方法并不是实际上将数据从 buffer 中清除。但是，离实际被清除不远了。
     * </p>
     *
     * @return  This buffer
     */
    public Buffer clear() {
        position = 0;
        limit = capacity;
        mark = -1;
        return this;
    }

    /**
     * Flips this buffer.  The limit is set to the current position and then
     * the position is set to zero.  If the mark is defined then it is
     * discarded.
     * <p>
     *     翻转 buffer，主要指翻转 buffer 的读写模式。limit 设置为 position 的值。
     *     position 设置为 0。mark 的值会被抛弃，将被设置为 -1。
     *
     * <p> After a sequence of channel-read or <i>put</i> operations, invoke
     * this method to prepare for a sequence of channel-write or relative
     * <i>get</i> operations.  For example:
     *
     * <blockquote><pre>
     * buf.put(magic);    // Prepend header
     * in.read(buf);      // Read data into rest of buffer
     * buf.flip();        // Flip buffer
     * out.write(buf);    // Write header + data to channel</pre></blockquote>
     *
     * <p> This method is often used in conjunction with the {@link
     * java.nio.ByteBuffer#compact compact} method when transferring data from
     * one place to another.
     * <p>
     *     该方法在转移数据的时候通常与 compact 方法一起用。
     * </p>
     *
     * @return  This buffer
     */
    public Buffer flip() {
        limit = position;
        position = 0;
        mark = -1;
        return this;
    }

    /**
     * Rewinds this buffer.  The position is set to zero and the mark is
     * discarded.
     * <p>
     *     position 将被设置成 0，mark 将被丢弃。
     *
     * <p> Invoke this method before a sequence of channel-write or <i>get</i>
     * operations, assuming that the limit has already been set
     * appropriately.  For example:
     *
     * <blockquote><pre>
     * out.write(buf);    // Write remaining data
     * buf.rewind();      // Rewind buffer
     * buf.get(array);    // Copy data into array</pre></blockquote>
     *
     * @return  This buffer
     */
    public Buffer rewind() {
        position = 0;
        mark = -1;
        return this;
    }

    /**
     * Returns the number of elements between the current position and the
     * limit.
     * <p>
     *     返回 position 到 limit 之间的数据个数
     *
     * @return  The number of elements remaining in this buffer
     */
    public final int remaining() {
        return limit - position;
    }

    /**
     * Tells whether there are any elements between the current position and
     * the limit.
     * <p>
     *     返回 position 到 limit 之间是否有数据
     *
     * @return  {@code true} if, and only if, there is at least one element
     *          remaining in this buffer
     */
    public final boolean hasRemaining() {
        return position < limit;
    }

    /**
     * Tells whether or not this buffer is read-only.
     * <p>
     *     返回当前 buffer 是否可读，具体有子类实现。
     *
     * @return  {@code true} if, and only if, this buffer is read-only
     */
    public abstract boolean isReadOnly();

    /**
     * Tells whether or not this buffer is backed by an accessible
     * array.
     * <p>
     *     返回当前 buffer 是否有数组进行备份
     *
     * <p> If this method returns {@code true} then the {@link #array() array}
     * and {@link #arrayOffset() arrayOffset} methods may safely be invoked.
     * </p>
     *
     * @return  {@code true} if, and only if, this buffer
     *          is backed by an array and is not read-only
     *
     * @since 1.6
     */
    public abstract boolean hasArray();

    /**
     * Returns the array that backs this
     * buffer&nbsp;&nbsp;<i>(optional operation)</i>.
     * <p>
     *     返回备份了 buffer 的array
     *
     * <p> This method is intended to allow array-backed buffers to be
     * passed to native code more efficiently. Concrete subclasses
     * provide more strongly-typed return values for this method.
     * <p>
     *     允许使用 native 内存进行备份，这样会更高效。具体有子类实现。
     *
     * <p> Modifications to this buffer's content will cause the returned
     * array's content to be modified, and vice versa.
     * <p>
     *     修改 buffer 会改变 array 的数据，修改 array 的数据，同时会修改 buffer 的值。
     *
     * <p> Invoke the {@link #hasArray hasArray} method before invoking this
     * method in order to ensure that this buffer has an accessible backing
     * array.  </p>
     * <p>
     *     调用该方法之前一般先调用 hasArray 方法来判断。
     *
     * @return  The array that backs this buffer
     *
     * @throws  ReadOnlyBufferException
     *          If this buffer is backed by an array but is read-only
     *
     * @throws  UnsupportedOperationException
     *          If this buffer is not backed by an accessible array
     *
     * @since 1.6
     */
    public abstract Object array();

    /**
     * Returns the offset within this buffer's backing array of the first
     * element of the buffer&nbsp;&nbsp;<i>(optional operation)</i>.
     * <p>
     *     返回 array 中第一个数据的偏移量。(数据在 array 中的起始位置)
     *
     * <p> If this buffer is backed by an array then buffer position <i>p</i>
     * corresponds to array index <i>p</i>&nbsp;+&nbsp;{@code arrayOffset()}.
     * <p>
     *     buffer 中 p 位置的数据，在 array 中位于 p + arrayOffset() 为主
     *
     * <p> Invoke the {@link #hasArray hasArray} method before invoking this
     * method in order to ensure that this buffer has an accessible backing
     * array.
     * <p>
     *     调用该方法之前调用 hasArray 方法检查一下是否有备份。
     * </p>
     *
     * @return  The offset within this buffer's array
     *          of the first element of the buffer
     *
     * @throws  ReadOnlyBufferException
     *          If this buffer is backed by an array but is read-only
     *
     * @throws  UnsupportedOperationException
     *          If this buffer is not backed by an accessible array
     *
     * @since 1.6
     */
    public abstract int arrayOffset();

    /**
     * Tells whether or not this buffer is
     * <a href="ByteBuffer.html#direct"><i>direct</i></a>.
     * <p>
     *     返回当前 buffer 是否为直接内存
     *
     * @return  {@code true} if, and only if, this buffer is direct
     *
     * @since 1.6
     */
    public abstract boolean isDirect();

    /**
     * Creates a new buffer whose content is a shared subsequence of
     * this buffer's content.
     * <p>
     *     创建一个新的 buffer，新的 buffer 的数据是当前 buffer 的一部分，两者共享一部分数据。
     *
     * <p> The content of the new buffer will start at this buffer's current
     * position.  Changes to this buffer's content will be visible in the new
     * buffer, and vice versa; the two buffers' position, limit, and mark
     * values will be independent.
     * <p>
     *     新的 buffer 的数据将从当前 buffer 的 position 位置开始。两个 buffer 共享数据，
     *     任意修改某一 buffer 的数据都将影响另一个 buffer 中的数据。但是两个 buffer 的
     *     position limit mark 的值是独立的。
     *
     * <p> The new buffer's position will be zero, its capacity and its limit
     * will be the number of elements remaining in this buffer, its mark will be
     * undefined. The new buffer will be direct if, and only if, this buffer is
     * direct, and it will be read-only if, and only if, this buffer is
     * read-only.
     * <p>
     *     新 buffer 的 position 可能为 0。他的 capacity 和 limit 值为当前 buffer 的
     *     remaing 方法返回的值。新 buffer 是否为 direct 或者 只读，与当前 buffer 相关。
     * </p>
     *
     * @return  The new buffer
     *
     * @since 9
     */
    public abstract Buffer slice();

    /**
     * Creates a new buffer that shares this buffer's content.
     * <p>
     *     创建一个新的 buffer，与当前 buffer 的数据是一致的。
     *
     * <p> The content of the new buffer will be that of this buffer.  Changes
     * to this buffer's content will be visible in the new buffer, and vice
     * versa; the two buffers' position, limit, and mark values will be
     * independent.
     * <p>
     *     相当于共享了整块 buffer，但是 position limit mark 是独立的。
     *
     * <p> The new buffer's capacity, limit, position and mark values will be
     * identical to those of this buffer. The new buffer will be direct if, and
     * only if, this buffer is direct, and it will be read-only if, and only if,
     * this buffer is read-only.
     * <p>
     *     初始的时候，新 buffer 的capacity, limit, position, mark 与当前 buffer 是完全相同的。
     * </p>
     *
     * @return  The new buffer
     *
     * @since 9
     */
    public abstract Buffer duplicate();


    // -- Package-private methods for bounds checking, etc. --

    /**
     * @return the base reference, paired with the address
     * field, which in combination can be used for unsafe access into a heap
     * buffer or direct byte buffer (and views of).
     */
    abstract Object base();

    /**
     * Checks the current position against the limit, throwing a {@link
     * BufferUnderflowException} if it is not smaller than the limit, and then
     * increments the position.
     * <p>
     *     首先检查 position 的值是否合法，如果合法，position + 1，否则抛出异常。
     *
     * @return  The current position value, before it is incremented
     */
    final int nextGetIndex() {                          // package-private
        int p = position;
        if (p >= limit)
            throw new BufferUnderflowException();
        position = p + 1;
        return p;
    }

    /**
     * 类似于上面的方法，可以指定 position 的递增数量
     */
    final int nextGetIndex(int nb) {                    // package-private
        int p = position;
        if (limit - p < nb)
            throw new BufferUnderflowException();
        position = p + nb;
        return p;
    }

    /**
     * Checks the current position against the limit, throwing a {@link
     * BufferOverflowException} if it is not smaller than the limit, and then
     * increments the position.
     *
     * @return  The current position value, before it is incremented
     */
    final int nextPutIndex() {                          // package-private
        int p = position;
        if (p >= limit)
            throw new BufferOverflowException();
        position = p + 1;
        return p;
    }

    final int nextPutIndex(int nb) {                    // package-private
        int p = position;
        if (limit - p < nb)
            throw new BufferOverflowException();
        position = p + nb;
        return p;
    }

    /**
     * Checks the given index against the limit, throwing an {@link
     * IndexOutOfBoundsException} if it is not smaller than the limit
     * or is smaller than zero.
     * <p>
     *     检查给定的 position 是否合法
     */
    @HotSpotIntrinsicCandidate
    final int checkIndex(int i) {                       // package-private
        if ((i < 0) || (i >= limit))
            throw new IndexOutOfBoundsException();
        return i;
    }

    final int checkIndex(int i, int nb) {               // package-private
        if ((i < 0) || (nb > limit - i))
            throw new IndexOutOfBoundsException();
        return i;
    }

    /**
     * 返回 mark 的值
     */
    final int markValue() {                             // package-private
        return mark;
    }

    /**
     * 截断数据
     */
    final void truncate() {                             // package-private
        // 丢弃 mark
        mark = -1;
        // position 置为 0
        position = 0;
        // limit 置为 0
        limit = 0;
        // capacity 置为 0
        capacity = 0;
    }

    /**
     * 丢弃 mark 的值
     */
    final void discardMark() {                          // package-private
        mark = -1;
    }

    static void checkBounds(int off, int len, int size) { // package-private
        if ((off | len | (off + len) | (size - (off + len))) < 0)
            throw new IndexOutOfBoundsException();
    }

    static {
        // setup access to this package in SharedSecrets
        SharedSecrets.setJavaNioAccess(
            new JavaNioAccess() {
                @Override
                public JavaNioAccess.BufferPool getDirectBufferPool() {
                    return Bits.BUFFER_POOL;
                }
                @Override
                public ByteBuffer newDirectByteBuffer(long addr, int cap, Object ob) {
                    return new DirectByteBuffer(addr, cap, ob);
                }
                @Override
                public void truncate(Buffer buf) {
                    buf.truncate();
                }
            });
    }

}
