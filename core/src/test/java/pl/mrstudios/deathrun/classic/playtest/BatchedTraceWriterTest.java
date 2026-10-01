package pl.mrstudios.deathrun.classic.playtest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
class BatchedTraceWriterTest {
    @TempDir Path root;
    @Test void flushAndShutdownPreserveOrderAcrossMaps() throws Exception {
        Path a=root.resolve("a.log"), b=root.resolve("b.log");
        try (var writer=new BatchedTraceWriter()) {
            for(int i=0;i<1000;i++) { writer.append(a,i+"\n"); writer.append(b,"b"+i+"\n"); }
            writer.flush();
            assertEquals(1000,Files.readAllLines(a).size());
            assertEquals("b999",Files.readAllLines(b).get(999));
            writer.append(a,"shutdown\n");
        }
        assertEquals("shutdown",Files.readAllLines(a).get(1000));
    }
    @Test void flushBarrierPreventsOldLinesAppearingAfterClear() throws Exception {
        Path path=root.resolve("trace.log");
        try (var writer=new BatchedTraceWriter()) {
            writer.append(path,"old\n"); writer.flush(); Files.delete(path);
            Files.writeString(path,"new header\n"); writer.append(path,"new\n"); writer.flush();
            assertEquals("new header\nnew\n",Files.readString(path));
        }
    }
    @Test void failedDiskWriteIsReportedToReaders() throws Exception {
        Path missing=root.resolve("missing/trace.log");
        var writer=new BatchedTraceWriter();
        writer.append(missing,"event\n");
        assertThrows(java.io.IOException.class, writer::flush);
        assertThrows(java.io.IOException.class, () -> writer.append(missing,"next\n"));
        assertThrows(java.io.IOException.class, writer::close);
    }
}
