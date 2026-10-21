package app.cleanos.home;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

public class ConfigTest {

    @Test
    public void parseListSplitsOnCommasAndSpaces() {
        List<String> got = Config.parseList("com.netflix.ninja, com.google.android.youtube.tv  com.android.vending");
        assertEquals(Arrays.asList("com.netflix.ninja", "com.google.android.youtube.tv", "com.android.vending"), got);
    }

    @Test
    public void parseListDropsInvalidAndDuplicateEntries() {
        List<String> got = Config.parseList("netflix, com.a.b, ; rm -rf, com.a.b,9bad.pkg,com.c_d.e1");
        assertEquals(Arrays.asList("com.a.b", "com.c_d.e1"), got);
    }

    @Test
    public void parseListHandlesEmptyInput() {
        assertTrue(Config.parseList(null).isEmpty());
        assertTrue(Config.parseList("").isEmpty());
        assertTrue(Config.parseList(" , ,").isEmpty());
    }

    @Test
    public void timeGreetingCoversTheDay() {
        assertEquals("Good evening", Config.timeGreeting(0));
        assertEquals("Good evening", Config.timeGreeting(4));
        assertEquals("Good morning", Config.timeGreeting(5));
        assertEquals("Good morning", Config.timeGreeting(11));
        assertEquals("Good afternoon", Config.timeGreeting(12));
        assertEquals("Good afternoon", Config.timeGreeting(16));
        assertEquals("Good evening", Config.timeGreeting(17));
        assertEquals("Good evening", Config.timeGreeting(23));
    }

    @Test
    public void defaultDockFitsTheDock() {
        assertTrue(Config.DEFAULT_DOCK.size() <= Config.MAX_DOCK_APPS);
    }
}
