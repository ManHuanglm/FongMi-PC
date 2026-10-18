package com.fongmi.android.tv.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * 桌面端 com.fongmi.android.tv.utils.FileUtil 替换实现：
 * 仅提供挂载代码（EpgParser/WallConfig 等）用到的压缩与拷贝方法
 */
public class FileUtil {

    private FileUtil() {
    }

    public static void copyAtomically(File source, File target) throws IOException {
        Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    public static boolean gzipCompress(byte[] data, File target) {
        try (GZIPOutputStream output = new GZIPOutputStream(new FileOutputStream(target))) {
            output.write(data);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public static boolean gzipDecompress(File source, File target) {
        try (GZIPInputStream input = new GZIPInputStream(new FileInputStream(source)); FileOutputStream output = new FileOutputStream(target)) {
            byte[] buffer = new byte[16384];
            int read;
            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /** 限制条目与总字节的 zip 解压（对齐安卓端防爆逻辑） */
    public static boolean zipDecompress(File source, File target, long maxEntries, long maxExtractedBytes) {
        try (java.util.zip.ZipInputStream input = new java.util.zip.ZipInputStream(new FileInputStream(source))) {
            java.util.zip.ZipEntry entry;
            long entries = 0;
            long total = 0;
            while ((entry = input.getNextEntry()) != null) {
                if (entries++ >= maxEntries) return false;
                File file = new File(target, entry.getName());
                if (!file.getCanonicalPath().startsWith(target.getCanonicalPath())) return false;
                if (entry.isDirectory()) {
                    file.mkdirs();
                    continue;
                }
                file.getParentFile().mkdirs();
                try (FileOutputStream output = new FileOutputStream(file)) {
                    byte[] buffer = new byte[16384];
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        total += read;
                        if (total > maxExtractedBytes) return false;
                        output.write(buffer, 0, read);
                    }
                }
            }
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public static String readGzip(File file) {
        try (GZIPInputStream input = new GZIPInputStream(new FileInputStream(file))) {
            return new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }
}
