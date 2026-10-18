package android.system;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * 桌面端 android.system.Os 等价实现（仅覆盖挂载代码用到的 rename）
 */
public class Os {

    private Os() {
    }

    public static void rename(String source, String target) throws ErrnoException {
        try {
            Files.move(Path.of(source), Path.of(target), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            try {
                Files.move(Path.of(source), Path.of(target), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ex) {
                throw new ErrnoException("rename", ex);
            }
        }
    }
}
