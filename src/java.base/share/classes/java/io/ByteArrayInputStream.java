/*
 * Copyright (c) 1994, 2018, Oracle and/or its affiliates. All rights reserved.
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

package java.io;

import java.util.Arrays;
import java.util.Objects;

/**
 * ByteArrayInputStream 内部包含一个 buffer
 * 并且有一个 counter 用于标记下一个读取的字节数
 * <p>
 * A {@code ByteArrayInputStream} contains
 * an internal buffer that contains bytes that
 * may be read from the stream. An internal
 * counter keeps track of the next byte to
 * be supplied by the {@code read} method.
 * <p>
 * Closing a {@code ByteArrayInputStream} has no effect. The methods in
 * this class can be called after the stream has been closed without
 * generating an {@code IOException}.
 *
 * @author Arthur van Hoff
 * @see java.io.StringBufferInputStream
 * @since 1.0
 */

/**
 * 继承自 InputStream
 * ByteArrayInputStream 缓冲区中缓存的是内存中的 byte 数组，感觉作用没有那么大
 */
public class ByteArrayInputStream extends InputStream {

    /**
     * 缓冲区
     * <p>
     * An array of bytes that was provided
     * by the creator of the stream. Elements {@code buf[0]}
     * through {@code buf[count-1]} are the
     * only bytes that can ever be read from the
     * stream;  element {@code buf[pos]} is
     * the next byte to be read.
     */
    protected byte buf[];

    /**
     * pos 指向下一个读取的字节数据下标
     * <p>
     * The index of the next character to read from the input stream buffer.
     * This value should always be nonnegative
     * and not larger than the value of {@code count}.
     * The next byte to be read from the input stream buffer
     * will be {@code buf[pos]}.
     */
    protected int pos;

    /**
     * mark 的位置
     * <p>
     * The currently marked position in the stream.
     * ByteArrayInputStream objects are marked at position zero by
     * default when constructed.  They may be marked at another
     * position within the buffer by the {@code mark()} method.
     * The current buffer position is set to this point by the
     * {@code reset()} method.
     * <p>
     * If no mark has been set, then the value of mark is the offset
     * passed to the constructor (or 0 if the offset was not supplied).
     *
     * @since 1.1
     */
    protected int mark = 0;

    /**
     * 有效的字节数据数
     * <p>
     * The index one greater than the last valid character in the input
     * stream buffer.
     * This value should always be nonnegative
     * and not larger than the length of {@code buf}.
     * It  is one greater than the position of
     * the last byte within {@code buf} that
     * can ever be read  from the input stream buffer.
     */
    protected int count;

    /**
     * 构造方法
     * 从内存中传入一个 buf 数组
     * <p>
     * Creates a {@code ByteArrayInputStream}
     * so that it  uses {@code buf} as its
     * buffer array.
     * The buffer array is not copied.
     * The initial value of {@code pos}
     * is {@code 0} and the initial value
     * of  {@code count} is the length of
     * {@code buf}.
     *
     * @param buf the input buffer.
     */
    public ByteArrayInputStream(byte buf[]) {
        this.buf = buf;
        this.pos = 0;
        this.count = buf.length;
    }

    /**
     * 构造一个 ByteArrayInputStream
     * 初始的 pos 位置为 offset
     * 初始的 count 为 offset+length 和 buf.length 中的较小值
     * 初始的 mark 为 offset
     * <p>
     * Creates {@code ByteArrayInputStream}
     * that uses {@code buf} as its
     * buffer array. The initial value of {@code pos}
     * is {@code offset} and the initial value
     * of {@code count} is the minimum of {@code offset+length}
     * and {@code buf.length}.
     * The buffer array is not copied. The buffer's mark is
     * set to the specified offset.
     *
     * @param buf    the input buffer.
     * @param offset the offset in the buffer of the first byte to read.
     * @param length the maximum number of bytes to read from the buffer.
     */
    public ByteArrayInputStream(byte buf[], int offset, int length) {
        this.buf = buf;
        this.pos = offset;
        this.count = Math.min(offset + length, buf.length);
        this.mark = offset;
    }

    /**
     * 从缓冲区中读取一个字节的数据
     * 并返回该数据
     * <p>
     * Reads the next byte of data from this input stream. The value
     * byte is returned as an {@code int} in the range
     * {@code 0} to {@code 255}. If no byte is available
     * because the end of the stream has been reached, the value
     * {@code -1} is returned.
     * <p>
     * This {@code read} method
     * cannot block.
     *
     * @return the next byte of data, or {@code -1} if the end of the
     * stream has been reached.
     */
    public synchronized int read() {
        return (pos < count) ? (buf[pos++] & 0xff) : -1;
    }

    /**
     * 从缓冲区中读取最多 len 长度的数据，并保存到传入的数组中 b[off] -&gt; b[off + len]
     * <p>
     * Reads up to {@code len} bytes of data into an array of bytes from this
     * input stream.  If {@code pos} equals {@code count}, then {@code -1} is
     * returned to indicate end of file.  Otherwise, the  number {@code k} of
     * bytes read is equal to the smaller of {@code len} and {@code count-pos}.
     * If {@code k} is positive, then bytes {@code buf[pos]} through
     * {@code buf[pos+k-1]} are copied into {@code b[off]} through
     * {@code b[off+k-1]} in the manner performed by {@code System.arraycopy}.
     * The value {@code k} is added into {@code pos} and {@code k} is returned.
     * <p>
     * This {@code read} method cannot block.
     *
     * @param b   the buffer into which the data is read.
     * @param off the start offset in the destination array {@code b}
     * @param len the maximum number of bytes read.
     * @return the total number of bytes read into the buffer, or
     * {@code -1} if there is no more data because the end of
     * the stream has been reached.
     * @throws NullPointerException      If {@code b} is {@code null}.
     * @throws IndexOutOfBoundsException If {@code off} is negative,
     *                                   {@code len} is negative, or {@code len} is greater than
     *                                   {@code b.length - off}
     */
    public synchronized int read(byte b[], int off, int len) {
        // 检查边界 off + len 是否在 b 中越界
        Objects.checkFromIndexSize(off, len, b.length);
        // 不可读了
        if (pos >= count) {
            return -1;
        }
        // 计算剩余的可读的字节数
        int avail = count - pos;
        // 要读的字节数大于剩余的字节数
        if (len > avail) {
            // 只能读取未读的数据
            // 更新 len
            len = avail;
        }
        // 没有可读
        if (len <= 0) {
            // 直接返回 0
            return 0;
        }
        // 将数据拷贝到目标数组 b 中
        System.arraycopy(buf, pos, b, off, len);
        // 更新 pos
        pos += len;
        // 返回实际读取的字节数
        return len;
    }

    /**
     * 读取缓冲区中所有的数据
     *
     * @return
     */
    public synchronized byte[] readAllBytes() {
        // 返回 [pos,count) 的数据
        byte[] result = Arrays.copyOfRange(buf, pos, count);
        // 更新 pos
        pos = count;
        // 返回数组
        return result;
    }

    /**
     * 与 read(byte b[], int off, int len) 方法比较类似
     * 只不过该方法不是线程安全的方法
     *
     * @param b
     * @param off
     * @param len
     * @return
     */
    public int readNBytes(byte[] b, int off, int len) {
        int n = read(b, off, len);
        return n == -1 ? 0 : n;
    }

    /**
     * 将缓冲区中未读的数据写入到传入的 OutputStream 中
     * 并调用 OutputStream::write 方法
     *
     * @param out
     * @return
     * @throws IOException
     */
    public synchronized long transferTo(OutputStream out) throws IOException {
        // 可读的字节数
        int len = count - pos;
        // 写操作
        out.write(buf, pos, len);
        // 更新 pos
        pos = count;
        // 返回写了的字节数
        return len;
    }

    /**
     * skip 字节
     * 一次最多 skip 剩余可读字节数
     * 如果想要达到 skip 指定数量的字节数，那么可以在外面套一个无限循环
     * <p>
     * Skips {@code n} bytes of input from this input stream. Fewer
     * bytes might be skipped if the end of the input stream is reached.
     * The actual number {@code k}
     * of bytes to be skipped is equal to the smaller
     * of {@code n} and  {@code count-pos}.
     * The value {@code k} is added into {@code pos}
     * and {@code k} is returned.
     *
     * @param n the number of bytes to be skipped.
     * @return the actual number of bytes skipped.
     */
    public synchronized long skip(long n) {
        // 剩余未读的字节数
        long k = count - pos;
        // 如果要跳过的字节数小于剩余可读的字节数
        if (n < k) {
            // 更新剩余未读的字节数
            k = n < 0 ? 0 : n;
        }
        // 更新 pos，往后移
        pos += k;
        // 返回实际 skip 的字节数
        return k;
    }

    /**
     * 返回 inputStream 中剩余可以读取的字节数
     * count - pos
     * <p>
     * Returns the number of remaining bytes that can be read (or skipped over)
     * from this input stream.
     * <p>
     * The value returned is {@code count - pos},
     * which is the number of bytes remaining to be read from the input buffer.
     *
     * @return the number of remaining bytes that can be read (or skipped
     * over) from this input stream without blocking.
     */
    public synchronized int available() {
        return count - pos;
    }

    /**
     * 支持 mark
     * <p>
     * Tests if this {@code InputStream} supports mark/reset. The
     * {@code markSupported} method of {@code ByteArrayInputStream}
     * always returns {@code true}.
     *
     * @since 1.1
     */
    public boolean markSupported() {
        return true;
    }

    /**
     * 记录 pos，更新 mark
     * <p>
     * Set the current marked position in the stream.
     * ByteArrayInputStream objects are marked at position zero by
     * default when constructed.  They may be marked at another
     * position within the buffer by this method.
     * <p>
     * If no mark has been set, then the value of the mark is the
     * offset passed to the constructor (or 0 if the offset was not
     * supplied).
     *
     * <p> Note: The {@code readAheadLimit} for this class
     * has no meaning.
     *
     * @since 1.1
     */
    public void mark(int readAheadLimit) {
        mark = pos;
    }

    /**
     * 更新 pos
     * <p>
     * Resets the buffer to the marked position.  The marked position
     * is 0 unless another position was marked or an offset was specified
     * in the constructor.
     */
    public synchronized void reset() {
        pos = mark;
    }

    /**
     * Closing a {@code ByteArrayInputStream} has no effect. The methods in
     * this class can be called after the stream has been closed without
     * generating an {@code IOException}.
     */
    public void close() throws IOException {
    }

}
