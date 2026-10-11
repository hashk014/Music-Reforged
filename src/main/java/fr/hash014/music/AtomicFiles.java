/* Music Reforged — made by hash014. Rewritten 2026-10-11. GPL-2.0-or-later. */
package fr.hash014.music;

import java.io.IOException;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;

final class AtomicFiles {
    static void write(Path target, String text) throws IOException {
        Files.createDirectories(target.toAbsolutePath().getParent());
        Path temp = Files.createTempFile(target.toAbsolutePath().getParent(), "music-", ".tmp");
        try { Files.write(temp, text.getBytes(StandardCharsets.UTF_8)); move(temp, target); }
        finally { Files.deleteIfExists(temp); }
    }
    static void move(Path temp, Path target) throws IOException {
        // Windows scanners can briefly hold a newly replaced file. Keep the original intact
        // and retry only the rename; never fall back to truncating a live state file.
        for (int attempt = 0; ; attempt++) {
            try {
                try { Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
                catch (AtomicMoveNotSupportedException e) { Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING); }
                return;
            } catch (AccessDeniedException e) {
                if (attempt >= 5) throw e;
                try { Thread.sleep(Math.min(80, 10L << attempt)); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IOException("Sauvegarde interrompue", interrupted); }
            }
        }
    }
}
