package android.os;

import java.io.File;

/**
 * 桌面端 android.os.StatFs 等价实现（包装 java.io.File 空间查询）
 */
public class StatFs {

    private final File file;

    public StatFs(String path) {
        this.file = new File(path);
    }

    public long getAvailableBlocksLong() {
        return file.getUsableSpace() / Math.max(1, getBlockSizeLong());
    }

    public long getTotalBlocksLong() {
        return file.getTotalSpace() / Math.max(1, getBlockSizeLong());
    }

    public long getBlockSizeLong() {
        return 4096;
    }
}
