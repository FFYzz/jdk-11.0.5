/*
 * Copyright (c) 1996, 2010, Oracle and/or its affiliates. All rights reserved.
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
 * ObjectOutput extends the DataOutput interface to include writing of objects.
 * DataOutput includes methods for output of primitive types, ObjectOutput
 * extends that interface to include objects, arrays, and Strings.
 *
 * @author unascribed
 * @see java.io.InputStream
 * @see java.io.ObjectOutputStream
 * @see java.io.ObjectInputStream
 * @since 1.1
 */

/**
 * Q: 写入的目的地可以是哪些？
 */
public interface ObjectOutput extends DataOutput, AutoCloseable {
    /**
     * 该方法定义如何将一个 Object 对象写入 stream
     * <p>
     * Write an object to the underlying storage or stream.  The
     * class that implements this interface defines how the object is
     * written.
     *
     * @param obj the object to be written
     * @throws IOException Any of the usual Input/Output related exceptions.
     */
    public void writeObject(Object obj)
            throws IOException;

    /**
     * 将一个 byte 的数据进行写入 stream，成功写入之前会阻塞
     * <p>
     * Writes a byte. This method will block until the byte is actually
     * written.
     *
     * @param b the byte
     * @throws IOException If an I/O error has occurred.
     */
    public void write(int b) throws IOException;

    /**
     * 将一个字节数组进行写入 stream，成功写入之前会阻塞
     * <p>
     * Writes an array of bytes. This method will block until the bytes
     * are actually written.
     *
     * @param b the data to be written
     * @throws IOException If an I/O error has occurred.
     */
    public void write(byte b[]) throws IOException;

    /**
     * 将字节数组的一部分进行写入 stream，成功写入之前会阻塞
     * <p>
     * Writes a sub array of bytes.
     *
     * @param b   the data to be written
     * @param off the start offset in the data
     * @param len the number of bytes that are written
     * @throws IOException If an I/O error has occurred.
     */
    public void write(byte b[], int off, int len) throws IOException;

    /**
     * 对 ObjectOutput 进行 flush 操作
     * <p>
     * Flushes the stream. This will write any buffered
     * output bytes.
     *
     * @throws IOException If an I/O error has occurred.
     */
    public void flush() throws IOException;

    /**
     * 关闭 ObjectOutput
     * <p>
     * Closes the stream. This method must be called
     * to release any resources associated with the
     * stream.
     *
     * @throws IOException If an I/O error has occurred.
     */
    public void close() throws IOException;
}
