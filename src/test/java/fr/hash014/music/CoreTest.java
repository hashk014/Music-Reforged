package fr.hash014.music;

import java.nio.file.*;
import java.util.*;
import java.util.logging.Logger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class CoreTest {
    @TempDir Path temp;
    @Test void fractionalDurationDoesNotRoundToWholeSeconds() {
        assertEquals(422, new Track("Fraction", "musicreforged:test", 21.1).ticks());
        assertEquals(423, new Track("Fraction", "musicreforged:test", 21.101).ticks());
        assertEquals(1, new Track("Short", "test", .05).ticks());
    }
    @Test void legacyTrackFieldsAndComments() {
        Track t = Library.parse("Wind", Arrays.asList("# comment", "Time:11.25", "Songname:wind", "[&&END&&]"));
        assertEquals("minecraft:wind", t.sound); assertEquals(225, t.ticks());
    }
    @Test void rejectsNonFiniteAndUnsafeNames() {
        for (double v : new double[] {Double.NaN, Double.POSITIVE_INFINITY, -1, 0, 86401}) assertThrows(IllegalArgumentException.class, () -> new Track("Test", "x:y", v));
        assertThrows(IllegalArgumentException.class, () -> new Track("../escape", "x:y", 1));
        assertThrows(IllegalArgumentException.class, () -> new Track("Fine", "x:../../y", 1));
    }
    @Test void missingQueueEntriesNeverCrashOrSpin() {
        Station s = new Station(0, UUID.randomUUID()); s.queue.add("missing");
        for (int n = 0; n < 100; n++) assertFalse(s.tick(Collections.emptyMap(), new Random(1)));
        assertNull(s.current()); s.queue.add("real"); s.reset();
        Map<String, Track> tracks = Collections.singletonMap("real", new Track("Real", "x:y", .05));
        assertTrue(s.tick(tracks, new Random(1))); assertEquals("Real", s.current().id);
    }
    @Test void playlistWrapPauseClearAndShuffle() {
        Station s = new Station(4, UUID.randomUUID()); s.queue.addAll(Arrays.asList("A", "B"));
        Map<String, Track> tracks = new HashMap<>(); tracks.put("a", new Track("A", "x:a", .1)); tracks.put("b", new Track("B", "x:b", .1));
        Random r = new Random(3); assertTrue(s.tick(tracks, r)); assertEquals("A", s.current().id);
        assertFalse(s.tick(tracks, r)); assertTrue(s.tick(tracks, r)); assertEquals("B", s.current().id);
        s.skip(); assertTrue(s.tick(tracks, r)); assertEquals("A", s.current().id);
        s.paused = true; assertFalse(s.tick(tracks, r)); assertNull(s.current());
        s.paused = false; s.shuffle = true; for (int i = 0; i < 100; i++) s.tick(tracks, r);
        s.queue.clear(); s.tick(tracks, r); assertNull(s.current());
    }
    @Test void quotedArgumentsAndMalformedQuotes() {
        assertArrayEquals(new String[] {"play", "My Song", "1"}, Commands.quoted(new String[] {"play", "\"My", "Song\"", "1"}));
        assertThrows(IllegalArgumentException.class, () -> Commands.quoted(new String[] {"play", "\"missing"}));
    }
    @Test void catalogueRejectsDuplicateCaseAndInvalidFiles() throws Exception {
        Library l = new Library(temp, Logger.getAnonymousLogger()); l.create(new Track("Song", "x:y", 1));
        if (Files.exists(temp.resolve("song.txt"))) assertThrows(IllegalArgumentException.class, () -> l.create(new Track("song", "x:z", 1)));
        else { l.create(new Track("song", "x:z", 1)); assertThrows(java.io.IOException.class, l::load); }
        AtomicFiles.write(temp.resolve("Invalid.txt"), "Time:NaN\nSoundname:x:y\n");
        assertThrows(java.io.IOException.class, l::load);
    }
    @Test void stateRoundTripIncludesEmptyQueueAndUnloadedWorld() throws Exception {
        StateStore state = new StateStore(temp.resolve("state.yml")); UUID owner = UUID.randomUUID();
        Station s = new Station(2, owner); s.paused = true; s.shuffle = true; state.stations.put(2, s);
        RadioBox box = new RadioBox("unloaded.world", -32, 81, 42, owner); box.station = 2; box.global = true; box.volume = 2.5f; state.boxes.put(box.key(), box);
        state.preferences.put(owner, .5f); state.save(); StateStore read = new StateStore(state.file()); read.load();
        assertTrue(read.stations.get(2).queue.isEmpty()); assertTrue(read.stations.get(2).paused); assertTrue(read.stations.get(2).shuffle);
        assertEquals(2.5f, read.boxes.get(box.key()).volume); assertEquals("unloaded.world", read.boxes.get(box.key()).world); assertEquals(.5f, read.preferences.get(owner));
    }
    @Test void corruptStateIsNeverSilentlyReplaced() throws Exception {
        Path file = temp.resolve("state.yml"); String broken = "schema: 1\nstations:\n  '0':\n    owner: invalid\n"; AtomicFiles.write(file, broken);
        assertThrows(Exception.class, () -> new StateStore(file).load()); assertEquals(broken, new String(Files.readAllBytes(file), java.nio.charset.StandardCharsets.UTF_8));
    }
}
