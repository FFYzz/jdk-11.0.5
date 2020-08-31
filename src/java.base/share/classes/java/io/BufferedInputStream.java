/*
 * Copyright (c) 1994, 2016, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.misc.Unsafe;

/**
 * A <code>BufferedInputStream</code> adds
 * functionality to another input stream-namely,
 * the ability to buffer the input and to
 * support the <code>mark</code> and <code>reset</code>
 * methods. When  the <code>BufferedInputStream</code>
 * is created, an internal buffer array is
 * created. As bytes  from the stream are read
 * or skipped, the internal buffer is refilled
 * as necessary  from the contained input stream,
 * many bytes at a time. The <code>mark</code>
 * operation  remembers a point in the input
 * stream and the <code>reset</code> operation
 * causes all the  bytes read since the most
 * recent <code>mark</code> operation to be
 * reread before new bytes are  taken from
 * the contained input stream.
 *
 * @author Arthur van Hoff
 * @since 1.0
 */
public
class BufferedInputStream extends FilterInputStream {

    /**
     * 默认 buffer 数组的大小
     */
    private static int DEFAULT_BUFFER_SIZE = 8192;

    /**
     * buffer 数组最大的分配大小
     * -8 是因为某些 VM 中会保留一个 1 字节大小的 header
     * <p>
     * The maximum size of array to allocate.
     * Some VMs reserve some header words in an array.
     * Attempts to allocate larger arrays may result in
     * OutOfMemoryError: Requested array size exceeds VM limit
     */
    private static int MAX_BUFFER_SIZE = Integer.MAX_VALUE - 8;

    /**
     * As this class is used early during bootstrap, it's motivated to use
     * Unsafe.compareAndSetObject instead of AtomicReferenceFieldUpdater
     * (or VarHandles) to reduce dependencies and improve startup time.
     */
    private static final Unsafe U = Unsafe.getUnsafe();

    private static final long BUF_OFFSET
            = U.objectFieldOffset(BufferedInputStream.class, "buf");

    /**
     * 内部存储数据的数组
     * <p>
     * The internal buffer array where the data is stored. When necessary,
     * it may be replaced by another array of
     * a different size.
     */
    /*
     * We null this out with a CAS on close(), which is necessary since
     * closes can be asynchronous. We use nullness of buf[] as primary
     * indicator that this stream is closed. (The "in" field is also
     * nulled out on close.)
     */
    protected volatile byte[] buf;

    /**
     * buf 数组的实际有效数据个数
     * <p>
     * The index one greater than the index of the last valid byte in
     * the buffer.
     * This value is always
     * in the range <code>0</code> through <code>buf.length</code>;
     * elements <code>buf[0]</code>  through <code>buf[count-1]
     * </code>contain buffered input data obtained
     * from the underlying  input stream.
     */
    protected int count;

    /**
     * 下一个要读到的数据的下标
     * <p>
     * The current position in the buffer. This is the index of the next
     * character to be read from the <code>buf</code> array.
     * <p>
     * This value is always in the range <code>0</code>
     * through <code>count</code>. If it is less
     * than <code>count</code>, then  <code>buf[pos]</code>
     * is the next byte to be supplied as input;
     * if it is equal to <code>count</code>, then
     * the  next <code>read</code> or <code>skip</code>
     * operation will require more bytes to be
     * read from the contained  input stream.
     *
     * @see java.io.BufferedInputStream#buf
     */
    protected int pos;

    /**
     * mark 开始的第一个索引下标
     * <p>
     * The value of the <code>pos</code> field at the time the last
     * <code>mark</code> method was called.
     * <p>
     * This value is always
     * in the range <code>-1</code> through <code>pos</code>.
     * If there is no marked position in  the input
     * stream, this field is <code>-1</code>. If
     * there is a marked position in the input
     * stream,  then <code>buf[markpos]</code>
     * is the first byte to be supplied as input
     * after a <code>reset</code> operation. If
     * <code>markpos</code> is not <code>-1</code>,
     * then all bytes from positions <code>buf[markpos]</code>
     * through  <code>buf[pos-1]</code> must remain
     * in the buffer array (though they may be
     * moved to  another place in the buffer array,
     * with suitable adjustments to the values
     * of <code>count</code>,  <code>pos</code>,
     * and <code>markpos</code>); they may not
     * be discarded unless and until the difference
     * between <code>pos</code> and <code>markpos</code>
     * exceeds <code>marklimit</code>.
     *
     * @see java.io.BufferedInputStream#mark(int)
     * @see java.io.BufferedInputStream#pos
     */
    protected int markpos = -1;

    /**
     * 标记 mark 之后最大能往后读取的个数
     * <p>
     * The maximum read ahead allowed after a call to the
     * <code>mark</code> method before subsequent calls to the
     * <code>reset</code> method fail.
     * Whenever the difference between <code>pos</code>
     * and <code>markpos</code> exceeds <code>marklimit</code>,
     * then the  mark may be dropped by setting
     * <code>markpos</code> to <code>-1</code>.
     *
     * @see java.io.BufferedInputStream#mark(int)
     * @see java.io.BufferedInputStream#reset()
     */
    protected int marklimit;

    /**
     * 获取 inputStream
     * <p>
     * Check to make sure that underlying input stream has not been
     * nulled out due to close; if not return it;
     */
    private InputStream getInIfOpen() throws IOException {
        InputStream input = in;
        if (input == null)
            throw new IOException("Stream closed");
        return input;
    }

    /**
     * 检查 stream 是否已经 close，如果没有则返回 buf 数组
     * <p>
     * Check to make sure that buffer has not been nulled out due to
     * close; if not return it;
     */
    private byte[] getBufIfOpen() throws IOException {
        byte[] buffer = buf;
        if (buffer == null)
            throw new IOException("Stream closed");
        return buffer;
    }

    /**
     * 构造函数，使用默认的 buf size DEFAULT_BUFFER_SIZE
     * <p>
     * Creates a <code>BufferedInputStream</code>
     * and saves its  argument, the input stream
     * <code>in</code>, for later use. An internal
     * buffer array is created and  stored in <code>buf</code>.
     *
     * @param in the underlying input stream.
     */
    public BufferedInputStream(InputStream in) {
        this(in, DEFAULT_BUFFER_SIZE);
    }

    /**
     * 构造函数，指定 buf size
     * <p>
     * Creates a <code>BufferedInputStream</code>
     * with the specified buffer size,
     * and saves its  argument, the input stream
     * <code>in</code>, for later use.  An internal
     * buffer array of length  <code>size</code>
     * is created and stored in <code>buf</code>.
     *
     * @param in   the underlying input stream.
     * @param size the buffer size.
     * @throws IllegalArgumentException if {@code size <= 0}.
     */
    public BufferedInputStream(InputStream in, int size) {
        super(in);
        if (size <= 0) {
            throw new IllegalArgumentException("Buffer size <= 0");
        }
        buf = new byte[size];
    }

    /**
     * 往 buffer 中加入更多的数据
     * 要注意 mark 的处理
     * <p>
     * Fills the buffer with more data, taking into account
     * shuffling and other tricks for dealing with marks.
     * Assumes that it is being called by a synchronized method.
     * This method also assumes that all data has already been read in,
     * hence pos > count.
     */
    private void fill() throws IOException {
        // 获取 buff 数组
        byte[] buffer = getBufIfOpen();
        // 如果没有标记
        if (markpos < 0)
            // 则将 pos 置到 0 的位置
            pos = 0;            /* no mark: throw away the buffer */
            // buf 数组已经填满。
        else if (pos >= buffer.length)  /* no room left in buffer */
            // 有标记，则只能丢弃标记位前面的一段
            if (markpos > 0) {  /* can throw away early part of the buffer */
                // 标记之后读取的数据的长度
                int sz = pos - markpos;
                // 将标记之后读取的数据移动到 buffer 数组的 0 位置
                System.arraycopy(buffer, markpos, buffer, 0, sz);
                // 更新 pos 至 sz
                pos = sz;
                // 更新 markpos 为 0
                markpos = 0;
                // 这种情况下 markpos == 0 成立
                // 这种情况下可以取消标记，因为当 mark 标记为 0 的情况下，标记了等于没标记
            } else if (buffer.length >= marklimit) {
                markpos = -1;   /* buffer got too big, invalidate mark */
                pos = 0;        /* drop buffer contents */
                // 检查 buf 的大小
            } else if (buffer.length >= MAX_BUFFER_SIZE) {
                throw new OutOfMemoryError("Required array size too large");
                // markpos == 0 且 buffer.length < marklimit
                // buf 扩容
            } else {            /* grow buffer */
                // 计算一个 new size
                // new size 要么等于 pos * 2
                // 要么等于 MAX_BUFFER_SIZE
                // 默认情况下可以理解成 2 倍扩容
                int nsz = (pos <= MAX_BUFFER_SIZE - pos) ?
                        pos * 2 : MAX_BUFFER_SIZE;
                // 取 nsz 与 marklimit 中的较小值
                if (nsz > marklimit)
                    nsz = marklimit;
                // 新的 buff 数组
                byte[] nbuf = new byte[nsz];
                // 复制
                System.arraycopy(buffer, 0, nbuf, 0, pos);
                // buf 执行新数组
                if (!U.compareAndSetObject(this, BUF_OFFSET, buffer, nbuf)) {
                    // Can't replace buf if there was an async close.
                    // Note: This would need to be changed if fill()
                    // is ever made accessible to multiple threads.
                    // But for now, the only way CAS can fail is via close.
                    // assert buf == null;
                    throw new IOException("Stream closed");
                }
                buffer = nbuf;
            }
        // 更新 count 为
        count = pos;
        // 向底层 IO 进行读取数据存入到 buffer 中
        int n = getInIfOpen().read(buffer, pos, buffer.length - pos);
        if (n > 0)
            // 计算当前 buf 数组中有效数据的最后一个下标
            count = n + pos;
    }

    /**
     * 读取一个字节的数据
     * <p>
     * See
     * the general contract of the <code>read</code>
     * method of <code>InputStream</code>.
     *
     * @return the next byte of data, or <code>-1</code> if the end of the
     * stream is reached.
     * @throws IOException if this input stream has been closed by
     *                     invoking its {@link #close()} method,
     *                     or an I/O error occurs.
     * @see java.io.FilterInputStream#in
     */
    public synchronized int read() throws IOException {
        // 如果缓存在 buffer 中的数据已经全都读取完毕
        if (pos >= count) {
            // 获取更所的数据
            fill();
            if (pos >= count)
                return -1;
        }
        return getBufIfOpen()[pos++] & 0xff;
    }

    /**
     * Read characters into a portion of an array, reading from the underlying
     * stream at most once if necessary.
     */
    private int read1(byte[] b, int off, int len) throws IOException {
        // 计算缓存中剩余未读的数据长度
        int avail = count - pos;
        // 如果已经读完了
        if (avail <= 0) {
            /* If the requested length is at least as large as the buffer, and
               if there is no mark/reset activity, do not bother to copy the
               bytes into the local buffer.  In this way buffered streams will
               cascade harmlessly. */
            // 要读取的数据的长度比 buf 的长度长 且 没有标记
            if (len >= getBufIfOpen().length && markpos < 0) {
                // 直接调用传入的 InputStream 的 read 方法
                return getInIfOpen().read(b, off, len);
            }
            // 向 buf 中缓冲数据
            fill();
            // 更新可读的数据长度
            avail = count - pos;
            // 如果没有可读的，直接返回 -1
            if (avail <= 0) return -1;
        }
        // 走到这里表明 buf 中有数据可以读
        // 取 len 与 avail 中的较小者
        int cnt = (avail < len) ? avail : len;
        // 将数据拷贝到传入的 b 数组中
        System.arraycopy(getBufIfOpen(), pos, b, off, cnt);
        // 更新 pos
        pos += cnt;
        // 返回实际读取的字节数
        return cnt;
    }

    /**
     * Reads bytes from this byte-input stream into the specified byte array,
     * starting at the given offset.
     *
     * <p> This method implements the general contract of the corresponding
     * <code>{@link InputStream#read(byte[], int, int) read}</code> method of
     * the <code>{@link InputStream}</code> class.  As an additional
     * convenience, it attempts to read as many bytes as possible by repeatedly
     * invoking the <code>read</code> method of the underlying stream.  This
     * iterated <code>read</code> continues until one of the following
     * conditions becomes true: <ul>
     *
     * <li> The specified number of bytes have been read,
     *
     * <li> The <code>read</code> method of the underlying stream returns
     * <code>-1</code>, indicating end-of-file, or
     *
     * <li> The <code>available</code> method of the underlying stream
     * returns zero, indicating that further input requests would block.
     *
     * </ul> If the first <code>read</code> on the underlying stream returns
     * <code>-1</code> to indicate end-of-file then this method returns
     * <code>-1</code>.  Otherwise this method returns the number of bytes
     * actually read.
     *
     * <p> Subclasses of this class are encouraged, but not required, to
     * attempt to read as many bytes as possible in the same fashion.
     *
     * @param b   destination buffer.
     * @param off offset at which to start storing bytes.
     * @param len maximum number of bytes to read.
     * @return the number of bytes read, or <code>-1</code> if the end of
     * the stream has been reached.
     * @throws IOException if this input stream has been closed by
     *                     invoking its {@link #close()} method,
     *                     or an I/O error occurs.
     */
    public synchronized int read(byte b[], int off, int len)
            throws IOException {
        // 检查 stream 是否关闭
        getBufIfOpen(); // Check for closed stream
        // 入参检查
        if ((off | len | (off + len) | (b.length - (off + len))) < 0) {
            throw new IndexOutOfBoundsException();
        } else if (len == 0) {
            return 0;
        }
        // 保存实际读取的字节数
        int n = 0;
        // 无限循环
        for (; ; ) {
            // 调用 read1 方法进行读取
            // read1 读取的长度不一定是传入的 len
            // 所以使用一个无限循环进行读取
            int nread = read1(b, off + n, len - n);
            // 读取结束了
            if (nread <= 0)
                // 判断是否是第一次循环
                // 如果是第一次循环直接返回 nread
                // 否则返回 n
                return (n == 0) ? nread : n;
            // 每次循环累加
            n += nread;
            // 读取的字节数到了传入了 len，直接返回
            if (n >= len)
                return n;
            // if not closed but no bytes available, return
            InputStream input = in;
            // 检查 stream 是否关闭以及是否还有数据可读
            if (input != null && input.available() <= 0)
                return n;
        }
    }

    /**
     * 返回实际 skip 的字节数
     * <p>
     * See the general contract of the <code>skip</code>
     * method of <code>InputStream</code>.
     *
     * @throws IOException if this input stream has been closed by
     *                     invoking its {@link #close()} method,
     *                     {@code in.skip(n)} throws an IOException,
     *                     or an I/O error occurs.
     */
    public synchronized long skip(long n) throws IOException {
        // 检查 stream 是否关闭
        getBufIfOpen(); // Check for closed stream
        if (n <= 0) {
            return 0;
        }
        // 当前 buf 剩余可读数据
        long avail = count - pos;

        // 不可读了
        if (avail <= 0) {
            // If no mark position set then don't keep in buffer
            // 没有标记
            if (markpos < 0)
                // 调用传入的 InputStream 的 skip 方法
                return getInIfOpen().skip(n);

            // Fill in buffer to save bytes for reset
            // 向 buf 中缓存数据
            fill();
            // 计算新的 avail
            avail = count - pos;
            // 不可以跳过
            if (avail <= 0)
                return 0;
        }
        //
        long skipped = (avail < n) ? avail : n;
        // 更新 pos
        pos += skipped;
        // 返回实际 skip 的字节数
        return skipped;
    }

    /**
     * Returns an estimate of the number of bytes that can be read (or
     * skipped over) from this input stream without blocking by the next
     * invocation of a method for this input stream. The next invocation might be
     * the same thread or another thread.  A single read or skip of this
     * many bytes will not block, but may read or skip fewer bytes.
     * <p>
     * This method returns the sum of the number of bytes remaining to be read in
     * the buffer (<code>count&nbsp;- pos</code>) and the result of calling the
     * {@link java.io.FilterInputStream#in in}.available().
     *
     * @return an estimate of the number of bytes that can be read (or skipped
     * over) from this input stream without blocking.
     * @throws IOException if this input stream has been closed by
     *                     invoking its {@link #close()} method,
     *                     or an I/O error occurs.
     */
    public synchronized int available() throws IOException {
        // 计算缓存中还可读的 n
        int n = count - pos;
        // 调用传入的 InputStream 的 available 方法
        int avail = getInIfOpen().available();
        // 返回
        return n > (Integer.MAX_VALUE - avail)
                ? Integer.MAX_VALUE
                : n + avail;
    }

    /**
     * 标记
     * <p>
     * See the general contract of the <code>mark</code>
     * method of <code>InputStream</code>.
     *
     * @param readlimit the maximum limit of bytes that can be read before
     *                  the mark position becomes invalid.
     * @see java.io.BufferedInputStream#reset()
     */
    public synchronized void mark(int readlimit) {
        // 可以传入一个 readlimit
        marklimit = readlimit;
        markpos = pos;
    }

    /**
     * 重置
     * <p>
     * See the general contract of the <code>reset</code>
     * method of <code>InputStream</code>.
     * <p>
     * If <code>markpos</code> is <code>-1</code>
     * (no mark has been set or the mark has been
     * invalidated), an <code>IOException</code>
     * is thrown. Otherwise, <code>pos</code> is
     * set equal to <code>markpos</code>.
     *
     * @throws IOException if this stream has not been marked or,
     *                     if the mark has been invalidated, or the stream
     *                     has been closed by invoking its {@link #close()}
     *                     method, or an I/O error occurs.
     * @see java.io.BufferedInputStream#mark(int)
     */
    public synchronized void reset() throws IOException {
        // 检查
        getBufIfOpen(); // Cause exception if closed
        if (markpos < 0)
            throw new IOException("Resetting to invalid mark");
        // 将 pos 回到 markpos
        pos = markpos;
    }

    /**
     * BufferedInputStream 支持 mark
     * <p>
     * Tests if this input stream supports the <code>mark</code>
     * and <code>reset</code> methods. The <code>markSupported</code>
     * method of <code>BufferedInputStream</code> returns
     * <code>true</code>.
     *
     * @return a <code>boolean</code> indicating if this stream type supports
     * the <code>mark</code> and <code>reset</code> methods.
     * @see java.io.InputStream#mark(int)
     * @see java.io.InputStream#reset()
     */
    public boolean markSupported() {
        return true;
    }

    /**
     * 关闭流
     * <p>
     * Closes this input stream and releases any system resources
     * associated with the stream.
     * Once the stream has been closed, further read(), available(), reset(),
     * or skip() invocations will throw an IOException.
     * Closing a previously closed stream has no effect.
     *
     * @throws IOException if an I/O error occurs.
     */
    public void close() throws IOException {
        byte[] buffer;
        while ((buffer = buf) != null) {
            // 更新 buff 指向 null
            if (U.compareAndSetObject(this, BUF_OFFSET, buffer, null)) {
                InputStream input = in;
                in = null;
                if (input != null)
                    input.close();
                return;
            }
            // Else retry in case a new buf was CASed in fill()
        }
    }
}
