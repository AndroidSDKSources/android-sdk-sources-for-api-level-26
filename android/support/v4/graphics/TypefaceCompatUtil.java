/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.support.v4.graphics;

import static android.support.annotation.RestrictTo.Scope.LIBRARY_GROUP;

import android.content.Context;
import android.content.res.Resources;
import android.os.Process;
import android.support.annotation.RequiresApi;
import android.support.annotation.RestrictTo;
import android.util.Log;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**
 * Utility methods for TypefaceCompat.
 * @hide
 */
@RestrictTo(LIBRARY_GROUP)
class TypefaceCompatUtil {
    private static final String TAG = "TypefaceCompatUtil";

    private TypefaceCompatUtil() {}  // Do not instantiate.

    private static final String CACHE_FILE_PREFIX = ".font";

    /**
     * Creates a temp file.
     *
     * Returns null if failed to create temp file.
     */
    public static File getTempFile(Context context) {
        final String prefix = CACHE_FILE_PREFIX + Process.myPid() + "-" + Process.myTid() + "-";
        for (int i = 0; i < 100; ++i) {
            final File file = new File(context.getCacheDir(), prefix + i);
            try {
                if (file.createNewFile()) {
                    return file;
                }
            } catch (IOException e) {
                // ignore. Try next file.
            }
        }
        return null;
    }

    /**
     * Copy the file contents to the direct byte buffer.
     */
    @RequiresApi(19)
    private static ByteBuffer mmap(File file) {
        try (FileInputStream fis = new FileInputStream(file)) {
            FileChannel channel = fis.getChannel();
            final long size = channel.size();
            return channel.map(FileChannel.MapMode.READ_ONLY, 0, size);
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Copy the resource contents to the direct byte buffer.
     */
    @RequiresApi(19)
    public static ByteBuffer copyToDirectBuffer(Context context, Resources res, int id) {
        File tmpFile = getTempFile(context);
        if (tmpFile == null) {
            return null;
        }
        try {
            if (!copyToFile(tmpFile, res, id)) {
                return null;
            }
            return mmap(tmpFile);
        } finally {
            tmpFile.delete();
        }
    }

    /**
     * Helper class for reading ByteBuffer as InputStream.
     */
    private static class ByteBufferInputStream extends InputStream {
        private ByteBuffer mBuf;

        ByteBufferInputStream(ByteBuffer buf) {
            mBuf = buf;
        }

        @Override
        public int read() {
            if (!mBuf.hasRemaining()) {
                return -1;
            }
            return mBuf.get() & 0xFF;
        }

        @Override
        public int read(byte[] bytes, int off, int len) {
            if (!mBuf.hasRemaining()) {
                return -1;
            }
            len = Math.min(len, mBuf.remaining());
            mBuf.get(bytes, off, len);
            return len;
        }
    }

    /**
     * Copy the buffer contents to file.
     */
    public static boolean copyToFile(File file, ByteBuffer buffer) {
        return copyToFile(file, new ByteBufferInputStream(buffer));
    }

    /**
     * Copy the input stream contents to file.
     */
    public static boolean copyToFile(File file, InputStream is) {
        FileOutputStream os = null;
        try {
            os = new FileOutputStream(file, false);
            byte[] buffer = new byte[1024];
            int readLen;
            while ((readLen = is.read(buffer)) != -1) {
                os.write(buffer, 0, readLen);
            }
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Error copying resource contents to temp file: " + e.getMessage());
            return false;
        } finally {
            closeQuietly(os);
        }
    }

    /**
     * Copy the resource contents to file.
     */
    public static boolean copyToFile(File file, Resources res, int id) {
        InputStream is = null;
        try {
            is = res.openRawResource(id);
            return copyToFile(file, is);
        } finally {
            closeQuietly(is);
        }
    }

    public static void closeQuietly(Closeable c) {
        if (c != null) {
            try {
                c.close();
            } catch (IOException e) {
            }
        }
    }
}
