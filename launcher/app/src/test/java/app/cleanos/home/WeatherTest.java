package app.cleanos.home;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class WeatherTest {

    @Test
    public void parsesCoordinatePairs() {
        assertArrayEquals(new double[]{38.72, -9.14}, Weather.parseCoords("38.72,-9.14"), 1e-9);
        assertArrayEquals(new double[]{-33.9, 151.2}, Weather.parseCoords(" -33.9 , 151.2 "), 1e-9);
    }

    @Test
    public void rejectsNamesAndOutOfRangeValues() {
        assertNull(Weather.parseCoords("Lisbon"));
        assertNull(Weather.parseCoords("Springfield, Illinois"));
        assertNull(Weather.parseCoords("91,10"));
        assertNull(Weather.parseCoords("10,181"));
        assertNull(Weather.parseCoords(""));
        assertNull(Weather.parseCoords(null));
    }

    @Test
    public void mapsWmoCodesToIcons() {
        assertEquals(R.drawable.ic_sun, Weather.icon(0, true));
        assertEquals(R.drawable.ic_moon, Weather.icon(1, false));
        assertEquals(R.drawable.ic_cloud, Weather.icon(3, true));
        assertEquals(R.drawable.ic_cloud, Weather.icon(45, true));
        assertEquals(R.drawable.ic_rain, Weather.icon(61, true));
        assertEquals(R.drawable.ic_rain, Weather.icon(81, true));
        assertEquals(R.drawable.ic_snow, Weather.icon(73, true));
        assertEquals(R.drawable.ic_snow, Weather.icon(86, true));
        assertEquals(R.drawable.ic_storm, Weather.icon(95, true));
    }
}
