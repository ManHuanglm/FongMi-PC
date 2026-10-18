package android.content.res;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

/**
 * 桌面端 android.content.res.AssetManager 等价实现：从磁盘 assets 目录读取
 */
public class AssetManager {

    private final File assetsDir;

    public AssetManager(File assetsDir) {
        this.assetsDir = assetsDir;
    }

    public InputStream open(String fileName) throws IOException {
        File file = new File(assetsDir, fileName);
        return Files.newInputStream(file.toPath());
    }

    public String[] list(String path) throws IOException {
        File dir = new File(assetsDir, path);
        String[] names = dir.list();
        return names == null ? new String[0] : names;
    }
}
