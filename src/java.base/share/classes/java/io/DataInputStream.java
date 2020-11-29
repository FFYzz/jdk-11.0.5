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

/**
 * A data input stream lets an application read primitive Java data
 * types from an underlying input stream in a machine-independent
 * way. An application uses a data output stream to write data that
 * can later be read by a data input stream.
 * <p>
 * DataInputStream is not necessarily safe for multithreaded access.
 * Thread safety is optional and is the responsibility of users of
 * methods in this class.
 *
 * @author Arthur van Hoff
 * @see java.io.DataOutputStream
 * @since 1.0
 */

/**
 * 继承了 FilterInputStream 并且实现了 DataInput 接口。
 * 能够读取数据并将数据写成 primitive java type
 * 该类中的方法不是线程安全的
 */
public
class DataInputStream extends FilterInputStream implements DataInput {

    /**
     * 构造方法，传入一个 InputStream
     * <p>
     * Creates a DataInputStream that uses the specified
     * underlying InputStream.
     *
     * @param in the specified input stream
     */
    public DataInputStream(InputStream in) {
        super(in);
    }

    /**
     * 工作数组，用于 readUTF 方法
     * <p>
     * working arrays initialized on demand by readUTF
     */
    private byte bytearr[] = new byte[80];
    private char chararr[] = new char[80];

    /**
     * 从 input stream 中读取数据，将读到的数据保存到 b 数组中。
     * 当调用 read 方法时，Java 层缓冲区中还没有将数据准备好的情况下会出现阻塞。
     * 方法返回实际读到的数据个数。该方法是一个阻塞方法，以下情况会从阻塞中唤醒:
     * 1. input stream 中的数据 ready 了
     * 2. 读到了 EOF
     * 3. 读取过程中抛异常了
     * 如果读取到的数据未满又没抛异常，那么说明数据读完了。
     * <p>
     * Reads some number of bytes from the contained input stream and
     * stores them into the buffer array <code>b</code>. The number of
     * bytes actually read is returned as an integer. This method blocks
     * until input data is available, end of file is detected, or an
     * exception is thrown.
     *
     * <p>If <code>b</code> is null, a <code>NullPointerException</code> is
     * thrown. If the length of <code>b</code> is zero, then no bytes are
     * read and <code>0</code> is returned; otherwise, there is an attempt
     * to read at least one byte. If no byte is available because the
     * stream is at end of file, the value <code>-1</code> is returned;
     * otherwise, at least one byte is read and stored into <code>b</code>.
     *
     * <p>The first byte read is stored into element <code>b[0]</code>, the
     * next one into <code>b[1]</code>, and so on. The number of bytes read
     * is, at most, equal to the length of <code>b</code>. Let <code>k</code>
     * be the number of bytes actually read; these bytes will be stored in
     * elements <code>b[0]</code> through <code>b[k-1]</code>, leaving
     * elements <code>b[k]</code> through <code>b[b.length-1]</code>
     * unaffected.
     *
     * <p>The <code>read(b)</code> method has the same effect as:
     * <blockquote><pre>
     * read(b, 0, b.length)
     * </pre></blockquote>
     *
     * @param b the buffer into which the data is read.
     * @return the total number of bytes read into the buffer, or
     * <code>-1</code> if there is no more data because the end
     * of the stream has been reached.
     * @throws IOException if the first byte cannot be read for any reason
     *                     other than end of file, the stream has been closed and the underlying
     *                     input stream does not support reading after close, or another I/O
     *                     error occurs.
     * @see java.io.FilterInputStream#in
     * @see java.io.InputStream#read(byte[], int, int)
     */
    public final int read(byte b[]) throws IOException {
        return in.read(b, 0, b.length);
    }

    /**
     * 最多读取 len 长度个数据。
     * 返回实际读取的数据的长度。
     * <p>
     * Reads up to <code>len</code> bytes of data from the contained
     * input stream into an array of bytes.  An attempt is made to read
     * as many as <code>len</code> bytes, but a smaller number may be read,
     * possibly zero. The number of bytes actually read is returned as an
     * integer.
     *
     * <p> This method blocks until input data is available, end of file is
     * detected, or an exception is thrown.
     *
     * <p> If <code>len</code> is zero, then no bytes are read and
     * <code>0</code> is returned; otherwise, there is an attempt to read at
     * least one byte. If no byte is available because the stream is at end of
     * file, the value <code>-1</code> is returned; otherwise, at least one
     * byte is read and stored into <code>b</code>.
     *
     * <p> The first byte read is stored into element <code>b[off]</code>, the
     * next one into <code>b[off+1]</code>, and so on. The number of bytes read
     * is, at most, equal to <code>len</code>. Let <i>k</i> be the number of
     * bytes actually read; these bytes will be stored in elements
     * <code>b[off]</code> through <code>b[off+</code><i>k</i><code>-1]</code>,
     * leaving elements <code>b[off+</code><i>k</i><code>]</code> through
     * <code>b[off+len-1]</code> unaffected.
     *
     * <p> In every case, elements <code>b[0]</code> through
     * <code>b[off]</code> and elements <code>b[off+len]</code> through
     * <code>b[b.length-1]</code> are unaffected.
     *
     * @param b   the buffer into which the data is read.
     * @param off the start offset in the destination array <code>b</code>
     * @param len the maximum number of bytes read.
     * @return the total number of bytes read into the buffer, or
     * <code>-1</code> if there is no more data because the end
     * of the stream has been reached.
     * @throws NullPointerException      If <code>b</code> is <code>null</code>.
     * @throws IndexOutOfBoundsException If <code>off</code> is negative,
     *                                   <code>len</code> is negative, or <code>len</code> is greater than
     *                                   <code>b.length - off</code>
     * @throws IOException               if the first byte cannot be read for any reason
     *                                   other than end of file, the stream has been closed and the underlying
     *                                   input stream does not support reading after close, or another I/O
     *                                   error occurs.
     * @see java.io.FilterInputStream#in
     * @see java.io.InputStream#read(byte[], int, int)
     */
    public final int read(byte b[], int off, int len) throws IOException {
        return in.read(b, off, len);
    }

    /**
     * 委托给下面的 readFully(byte b[], int off, int len) 方法实现
     * <p>
     * See the general contract of the {@code readFully}
     * method of {@code DataInput}.
     * <p>
     * Bytes
     * for this operation are read from the contained
     * input stream.
     *
     * @param b the buffer into which the data is read.
     * @throws NullPointerException if {@code b} is {@code null}.
     * @throws EOFException         if this input stream reaches the end before
     *                              reading all the bytes.
     * @throws IOException          the stream has been closed and the contained
     *                              input stream does not support reading after close, or
     *                              another I/O error occurs.
     * @see java.io.FilterInputStream#in
     */
    public final void readFully(byte b[]) throws IOException {
        readFully(b, 0, b.length);
    }

    /**
     * 调用该方法比读满传入的数据 b
     * <p>
     * See the general contract of the {@code readFully}
     * method of {@code DataInput}.
     * <p>
     * Bytes
     * for this operation are read from the contained
     * input stream.
     *
     * @param b   the buffer into which the data is read.
     * @param off the start offset in the data array {@code b}.
     * @param len the number of bytes to read.
     * @throws NullPointerException      if {@code b} is {@code null}.
     * @throws IndexOutOfBoundsException if {@code off} is negative,
     *                                   {@code len} is negative, or {@code len} is greater than
     *                                   {@code b.length - off}.
     * @throws EOFException              if this input stream reaches the end before
     *                                   reading all the bytes.
     * @throws IOException               the stream has been closed and the contained
     *                                   input stream does not support reading after close, or
     *                                   another I/O error occurs.
     * @see java.io.FilterInputStream#in
     */
    public final void readFully(byte b[], int off, int len) throws IOException {
        // check
        if (len < 0)
            throw new IndexOutOfBoundsException();
        // 记录读取的字节数
        int n = 0;
        // 如果
        while (n < len) {
            // 记录该次读取的字节数
            int count = in.read(b, off + n, len - n);
            // 如果没有读满 len 长度时输入流就已经读完了，那么会抛出 EOF 异常
            if (count < 0)
                throw new EOFException();
            n += count;
        }
    }

    /**
     * See the general contract of the <code>skipBytes</code>
     * method of <code>DataInput</code>.
     * <p>
     * Bytes for this operation are read from the contained
     * input stream.
     *
     * @param n the number of bytes to be skipped.
     * @return the actual number of bytes skipped.
     * @throws IOException if the contained input stream does not support
     *                     seek, or the stream has been closed and
     *                     the contained input stream does not support
     *                     reading after close, or another I/O error occurs.
     */
    public final int skipBytes(int n) throws IOException {
        // 记录总跳过的字节数
        int total = 0;
        // 档次跳过的字节数
        int cur = 0;
        // 循环 skip，尝试跳过 n 字节数据，如果少于 n，则说明流中的数据读完了
        while ((total < n) && ((cur = (int) in.skip(n - total)) > 0)) {
            total += cur;
        }
        // 返回实际跳过的字节数
        return total;
    }

    /**
     * See the general contract of the <code>readBoolean</code>
     * method of <code>DataInput</code>.
     * <p>
     * Bytes for this operation are read from the contained
     * input stream.
     *
     * @return the <code>boolean</code> value read.
     * @throws EOFException if this input stream has reached the end.
     * @throws IOException  the stream has been closed and the contained
     *                      input stream does not support reading after close, or
     *                      another I/O error occurs.
     * @see java.io.FilterInputStream#in
     */
    public final boolean readBoolean() throws IOException {
        // 读取一个字节
        int ch = in.read();
        // 如果 stream 已经关闭了，则抛出 EOF 异常
        if (ch < 0)
            throw new EOFException();
        return (ch != 0);
    }

    /**
     * See the general contract of the <code>readByte</code>
     * method of <code>DataInput</code>.
     * <p>
     * Bytes
     * for this operation are read from the contained
     * input stream.
     *
     * @return the next byte of this input stream as a signed 8-bit
     * <code>byte</code>.
     * @throws EOFException if this input stream has reached the end.
     * @throws IOException  the stream has been closed and the contained
     *                      input stream does not support reading after close, or
     *                      another I/O error occurs.
     * @see java.io.FilterInputStream#in
     */
    public final byte readByte() throws IOException {
        // 读取一个字节数据
        int ch = in.read();
        if (ch < 0)
            throw new EOFException();
        // 转为 byte 类型
        return (byte) (ch);
    }

    /**
     * 返回的是 int 类型的数据
     * <p>
     * See the general contract of the <code>readUnsignedByte</code>
     * method of <code>DataInput</code>.
     * <p>
     * Bytes
     * for this operation are read from the contained
     * input stream.
     *
     * @return the next byte of this input stream, interpreted as an
     * unsigned 8-bit number.
     * @throws EOFException if this input stream has reached the end.
     * @throws IOException  the stream has been closed and the contained
     *                      input stream does not support reading after close, or
     *                      another I/O error occurs.
     * @see java.io.FilterInputStream#in
     */
    public final int readUnsignedByte() throws IOException {
        // 读取一个字节数据，将其赋值为 int 类型，因为转为 int 型，那么一定满足 Unsigned
        int ch = in.read();
        if (ch < 0)
            throw new EOFException();
        return ch;
    }

    /**
     * See the general contract of the <code>readShort</code>
     * method of <code>DataInput</code>.
     * <p>
     * Bytes
     * for this operation are read from the contained
     * input stream.
     *
     * @return the next two bytes of this input stream, interpreted as a
     * signed 16-bit number.
     * @throws EOFException if this input stream reaches the end before
     *                      reading two bytes.
     * @throws IOException  the stream has been closed and the contained
     *                      input stream does not support reading after close, or
     *                      another I/O error occurs.
     * @see java.io.FilterInputStream#in
     */
    public final short readShort() throws IOException {
        // 第一个字节，高 8 位
        int ch1 = in.read();
        // 第二个字节，低 8 位
        int ch2 = in.read();
        if ((ch1 | ch2) < 0)
            throw new EOFException();
        // 计算方法
        return (short) ((ch1 << 8) + (ch2 << 0));
    }

    /**
     * See the general contract of the <code>readUnsignedShort</code>
     * method of <code>DataInput</code>.
     * <p>
     * Bytes
     * for this operation are read from the contained
     * input stream.
     *
     * @return the next two bytes of this input stream, interpreted as an
     * unsigned 16-bit integer.
     * @throws EOFException if this input stream reaches the end before
     *                      reading two bytes.
     * @throws IOException  the stream has been closed and the contained
     *                      input stream does not support reading after close, or
     *                      another I/O error occurs.
     * @see java.io.FilterInputStream#in
     */
    public final int readUnsignedShort() throws IOException {
        int ch1 = in.read();
        int ch2 = in.read();
        if ((ch1 | ch2) < 0)
            throw new EOFException();
        // 与 readUnsignedByte 套路类似，直接返回 int 类型的值，一定满足 Unsigned
        return (ch1 << 8) + (ch2 << 0);
    }

    /**
     * See the general contract of the <code>readChar</code>
     * method of <code>DataInput</code>.
     * <p>
     * Bytes
     * for this operation are read from the contained
     * input stream.
     *
     * @return the next two bytes of this input stream, interpreted as a
     * <code>char</code>.
     * @throws EOFException if this input stream reaches the end before
     *                      reading two bytes.
     * @throws IOException  the stream has been closed and the contained
     *                      input stream does not support reading after close, or
     *                      another I/O error occurs.
     * @see java.io.FilterInputStream#in
     */
    public final char readChar() throws IOException {
        // 两个字节
        int ch1 = in.read();
        int ch2 = in.read();
        if ((ch1 | ch2) < 0)
            throw new EOFException();
        return (char) ((ch1 << 8) + (ch2 << 0));
    }

    /**
     * See the general contract of the <code>readInt</code>
     * method of <code>DataInput</code>.
     * <p>
     * Bytes
     * for this operation are read from the contained
     * input stream.
     *
     * @return the next four bytes of this input stream, interpreted as an
     * <code>int</code>.
     * @throws EOFException if this input stream reaches the end before
     *                      reading four bytes.
     * @throws IOException  the stream has been closed and the contained
     *                      input stream does not support reading after close, or
     *                      another I/O error occurs.
     * @see java.io.FilterInputStream#in
     */
    public final int readInt() throws IOException {
        int ch1 = in.read();
        int ch2 = in.read();
        int ch3 = in.read();
        int ch4 = in.read();
        if ((ch1 | ch2 | ch3 | ch4) < 0)
            throw new EOFException();
        return ((ch1 << 24) + (ch2 << 16) + (ch3 << 8) + (ch4 << 0));
    }

    /**
     * 8 字节缓冲区
     */
    private byte readBuffer[] = new byte[8];

    /**
     * See the general contract of the <code>readLong</code>
     * method of <code>DataInput</code>.
     * <p>
     * Bytes
     * for this operation are read from the contained
     * input stream.
     *
     * @return the next eight bytes of this input stream, interpreted as a
     * <code>long</code>.
     * @throws EOFException if this input stream reaches the end before
     *                      reading eight bytes.
     * @throws IOException  the stream has been closed and the contained
     *                      input stream does not support reading after close, or
     *                      another I/O error occurs.
     * @see java.io.FilterInputStream#in
     */
    public final long readLong() throws IOException {
        // 读取 8 个字节
        readFully(readBuffer, 0, 8);
        // 为什么不需要 & 255?
        // 因为高位第一位需要保留符号位
        return (((long) readBuffer[0] << 56) +
                // 为什么需要 & 255?
                // 因为 byte 类型是有符号的，所以最高位为 1 的时候会被认为是符号位
                // 因此需要 & 255 ，255 是 int 类型，依旧是将 高于 8 位的值补零
                // 移位运算后返回的是 int 类型的值
                ((long) (readBuffer[1] & 255) << 48) +
                ((long) (readBuffer[2] & 255) << 40) +
                ((long) (readBuffer[3] & 255) << 32) +
                ((long) (readBuffer[4] & 255) << 24) +
                ((readBuffer[5] & 255) << 16) +
                ((readBuffer[6] & 255) << 8) +
                ((readBuffer[7] & 255) << 0));
    }

    /**
     * 委托给 Float 的 static 方法处理，是一个 native 方法，不展开
     * <p>
     * See the general contract of the <code>readFloat</code>
     * method of <code>DataInput</code>.
     * <p>
     * Bytes
     * for this operation are read from the contained
     * input stream.
     *
     * @return the next four bytes of this input stream, interpreted as a
     * <code>float</code>.
     * @throws EOFException if this input stream reaches the end before
     *                      reading four bytes.
     * @throws IOException  the stream has been closed and the contained
     *                      input stream does not support reading after close, or
     *                      another I/O error occurs.
     * @see java.io.DataInputStream#readInt()
     * @see java.lang.Float#intBitsToFloat(int)
     */
    public final float readFloat() throws IOException {
        return Float.intBitsToFloat(readInt());
    }

    /**
     * native 方法，不深入
     * <p>
     * See the general contract of the <code>readDouble</code>
     * method of <code>DataInput</code>.
     * <p>
     * Bytes
     * for this operation are read from the contained
     * input stream.
     *
     * @return the next eight bytes of this input stream, interpreted as a
     * <code>double</code>.
     * @throws EOFException if this input stream reaches the end before
     *                      reading eight bytes.
     * @throws IOException  the stream has been closed and the contained
     *                      input stream does not support reading after close, or
     *                      another I/O error occurs.
     * @see java.io.DataInputStream#readLong()
     * @see java.lang.Double#longBitsToDouble(long)
     */
    public final double readDouble() throws IOException {
        return Double.longBitsToDouble(readLong());
    }

    /**
     * 一行的缓冲区
     */
    private char lineBuffer[];

    /**
     * 被废弃了，就不看了
     * <p>
     * See the general contract of the <code>readLine</code>
     * method of <code>DataInput</code>.
     * <p>
     * Bytes
     * for this operation are read from the contained
     * input stream.
     *
     * @return the next line of text from this input stream.
     * @throws IOException if an I/O error occurs.
     * @see java.io.BufferedReader#readLine()
     * @see java.io.FilterInputStream#in
     * @deprecated This method does not properly convert bytes to characters.
     * As of JDK&nbsp;1.1, the preferred way to read lines of text is via the
     * <code>BufferedReader.readLine()</code> method.  Programs that use the
     * <code>DataInputStream</code> class to read lines can be converted to use
     * the <code>BufferedReader</code> class by replacing code of the form:
     * <blockquote><pre>
     *     DataInputStream d =&nbsp;new&nbsp;DataInputStream(in);
     * </pre></blockquote>
     * with:
     * <blockquote><pre>
     *     BufferedReader d
     *          =&nbsp;new&nbsp;BufferedReader(new&nbsp;InputStreamReader(in));
     * </pre></blockquote>
     */
    @Deprecated
    public final String readLine() throws IOException {
        char buf[] = lineBuffer;

        if (buf == null) {
            buf = lineBuffer = new char[128];
        }
        // 剩余可存字节数
        int room = buf.length;
        // 当前偏移量，偏移量可能大于 buf 长度
        int offset = 0;
        int c;

        loop:
        // 无限循环
        while (true) {
            // 读取一个字节的数据
            switch (c = in.read()) {
                // 读取不到数据或者读到换行时，跳出循环
                case -1:
                case '\n':
                    // 跳到外面的 while 循环再进来
                    break loop;
                // 读到回车符时
                // 说明读够一行了
                case '\r':
                    // 在读取一个字符
                    int c2 = in.read();
                    //
                    if ((c2 != '\n') && (c2 != -1)) {
                        if (!(in instanceof PushbackInputStream)) {
                            this.in = new PushbackInputStream(in);
                        }
                        ((PushbackInputStream) in).unread(c2);
                    }
                    // 跳到外面的 while 循环再进来
                    break loop;
                // 读到正常的字符
                default:
                    // buf 已经没有容量了
                    if (--room < 0) {
                        // 新分配 128 个字节
                        buf = new char[offset + 128];
                        // 更新剩余容量
                        room = buf.length - offset - 1;
                        // lineBuffer 的数据拷贝到 buf 中
                        System.arraycopy(lineBuffer, 0, buf, 0, offset);
                        // lineBuffer 指向最新的 buf
                        lineBuffer = buf;
                    }
                    // 继续放数据
                    buf[offset++] = (char) c;
                    // 跳出 switch
                    break;
            }
        }
        if ((c == -1) && (offset == 0)) {
            return null;
        }
        return String.copyValueOf(buf, 0, offset);
    }

    /**
     * See the general contract of the <code>readUTF</code>
     * method of <code>DataInput</code>.
     * <p>
     * Bytes
     * for this operation are read from the contained
     * input stream.
     *
     * @return a Unicode string.
     * @throws EOFException           if this input stream reaches the end before
     *                                reading all the bytes.
     * @throws IOException            the stream has been closed and the contained
     *                                input stream does not support reading after close, or
     *                                another I/O error occurs.
     * @throws UTFDataFormatException if the bytes do not represent a valid
     *                                modified UTF-8 encoding of a string.
     * @see java.io.DataInputStream#readUTF(java.io.DataInput)
     */
    public final String readUTF() throws IOException {
        return readUTF(this);
    }

    /**
     * UTF-8 的数据是变长的，可以是 1-6 个字节。Java 中只处理变长为 4 字节的情况。
     * 1 字节: 0XXXXXXXX
     * 2 字节: 110XXXXXX 10XXXXXX
     * 3 字节: 1110XXXX 10XXXXXX 10XXXXXX
     * 4 字节: 11110XXX 10XXXXXX 10XXXXXX 10XXXXXX
     * 5 字节: 111110XX 10XXXXXX 10XXXXXX 10XXXXXX 10XXXXXX
     * 6 字节: 1111110X 10XXXXXX 10XXXXXX 10XXXXXX 10XXXXXX 10XXXXXX
     * Reads from the
     * stream <code>in</code> a representation
     * of a Unicode  character string encoded in
     * <a href="DataInput.html#modified-utf-8">modified UTF-8</a> format;
     * this string of characters is then returned as a <code>String</code>.
     * The details of the modified UTF-8 representation
     * are  exactly the same as for the <code>readUTF</code>
     * method of <code>DataInput</code>.
     *
     * @param in a data input stream.
     * @return a Unicode string.
     * @throws EOFException           if the input stream reaches the end
     *                                before all the bytes.
     * @throws IOException            the stream has been closed and the contained
     *                                input stream does not support reading after close, or
     *                                another I/O error occurs.
     * @throws UTFDataFormatException if the bytes do not represent a
     *                                valid modified UTF-8 encoding of a Unicode string.
     * @see java.io.DataInputStream#readUnsignedShort()
     */
    public static final String readUTF(DataInput in) throws IOException {
        // 先读取一个 UnsignedShort，两个无符号字节
        // 数据的长度
        int utflen = in.readUnsignedShort();
        byte[] bytearr = null;
        char[] chararr = null;
        // 下面初始化 bytearr 与 chararr
        // 如果是 DataInputStream 的实例或者子类
        if (in instanceof DataInputStream) {
            DataInputStream dis = (DataInputStream) in;
            // 如果 dis 中 bytearr 的长度不足 数据的长度
            if (dis.bytearr.length < utflen) {
                // 更新 dis 的 bytearr 与 chararr
                dis.bytearr = new byte[utflen * 2];
                dis.chararr = new char[utflen * 2];
            }
            // 指向 dis 的相应属性
            chararr = dis.chararr;
            bytearr = dis.bytearr;
        } else {
            // 创建 bytearr 与 chararr
            bytearr = new byte[utflen];
            chararr = new char[utflen];
        }

        int c, char2, char3;
        int count = 0;
        int chararr_count = 0;
        // 调用 readFully 方法读满 bytearr
        in.readFully(bytearr, 0, utflen);
        // 遍历 bytearr 中的数据
        // 单字节处理
        while (count < utflen) {
            // & 255
            // 高位置 0，保证 bytearr[count] 的最高位不被识别为符号位
            c = (int) bytearr[count] & 0xff;
            // 如果第 8 位为 1，结束循环，数据不正常
            if (c > 127) break;
            count++;
            // 转为 char，保存到 chararr 中
            chararr[chararr_count++] = (char) c;
        }

        // 如果数据没有读完
        while (count < utflen) {
            // 继续取数据
            c = (int) bytearr[count] & 0xff;
            switch (c >> 4) {
                // 单字节，和上面的处理方式一样
                case 0:
                case 1:
                case 2:
                case 3:
                case 4:
                case 5:
                case 6:
                case 7:
                    /* 0xxxxxxx*/
                    count++;
                    chararr[chararr_count++] = (char) c;
                    break;
                // 双字节，可能是 1100 或者 1101
                case 12:
                case 13:
                    /* 110x xxxx   10xx xxxx*/
                    // 更新 count，向后移 2 位
                    count += 2;
                    // count 不能越界，越界了说明数据不对，抛异常
                    if (count > utflen)
                        throw new UTFDataFormatException(
                                "malformed input: partial character at end");
                    // 往前取一位
                    char2 = (int) bytearr[count - 1];
                    // 第一字节的第 8 位必须为 1，格式要求，如果不是则抛出异常。
                    if ((char2 & 0xC0) != 0x80)
                        throw new UTFDataFormatException(
                                "malformed input around byte " + count);
                    // 两个字节组成一个 char
                    chararr[chararr_count++] = (char) (((c & 0x1F) << 6) |
                            (char2 & 0x3F));
                    break;
                // 三字节，只能是 1110
                case 14:
                    /* 1110 xxxx  10xx xxxx  10xx xxxx */
                    count += 3;
                    // 同样检查数据格式是否异常
                    if (count > utflen)
                        throw new UTFDataFormatException(
                                "malformed input: partial character at end");
                    // 往回取值
                    char2 = (int) bytearr[count - 2];
                    char3 = (int) bytearr[count - 1];
                    // 第一字节与第二字节必须以 10 打头，格式要求
                    if (((char2 & 0xC0) != 0x80) || ((char3 & 0xC0) != 0x80))
                        throw new UTFDataFormatException(
                                "malformed input around byte " + (count - 1));
                    // 三字节转成一个 char
                    chararr[chararr_count++] = (char) (((c & 0x0F) << 12) |
                            ((char2 & 0x3F) << 6) |
                            ((char3 & 0x3F) << 0));
                    break;
                default:
                    // 4 字节就是 10 打头的情况，不支持
                    /* 10xx xxxx,  1111 xxxx */
                    throw new UTFDataFormatException(
                            "malformed input around byte " + count);
            }
        }
        // The number of chars produced may be less than utflen
        // 返回 String
        return new String(chararr, 0, chararr_count);
    }
}
