package app.cleanos.home;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Current weather from Open-Meteo (free, no API key). The location is whatever the user set with
 * {@code --es city}: a place name ("Lisbon", "Springfield, Illinois") or "lat,lon". No city, no
 * weather. Results are cached so Home and the screensaver paint instantly.
 */
final class Weather {

    interface Listener {
        /** text == null means "hide the weather". */
        void onWeather(int iconRes, String text);
    }

    private static final long MAX_AGE_MS = 30 * 60 * 1000L;
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private Weather() {}

    /** Paints the cached value right away (if any), then refreshes in the background when stale. */
    static void load(Context c, Listener l) {
        Context app = c.getApplicationContext();
        SharedPreferences p = Config.prefs(app);
        String city = Config.city(app);
        if (city.isEmpty()) {
            l.onWeather(0, null);
            return;
        }
        boolean sameCity = city.equalsIgnoreCase(p.getString("w_city", ""));
        if (sameCity && p.contains("w_temp")) deliver(p, l);
        if (sameCity && System.currentTimeMillis() - p.getLong("w_time", 0) < MAX_AGE_MS) return;

        IO.execute(() -> {
            try {
                double[] at = sameCity && p.contains("w_lat")
                        ? new double[]{Double.longBitsToDouble(p.getLong("w_lat", 0)), Double.longBitsToDouble(p.getLong("w_lon", 0))}
                        : locate(city);
                if (at == null) return;
                JSONObject cur = get(String.format(Locale.US,
                        "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&current=temperature_2m,weather_code,is_day%s",
                        at[0], at[1], fahrenheit() ? "&temperature_unit=fahrenheit" : ""))
                        .getJSONObject("current");
                p.edit()
                        .putString("w_city", city)
                        .putLong("w_lat", Double.doubleToLongBits(at[0]))
                        .putLong("w_lon", Double.doubleToLongBits(at[1]))
                        .putInt("w_temp", (int) Math.round(cur.getDouble("temperature_2m")))
                        .putInt("w_code", cur.getInt("weather_code"))
                        .putBoolean("w_day", cur.optInt("is_day", 1) == 1)
                        .putLong("w_time", System.currentTimeMillis())
                        .apply();
                MAIN.post(() -> deliver(p, l));
            } catch (Exception ignored) {
                // Offline or API hiccup: keep showing the cached value.
            }
        });
    }

    /** Forget the cached weather (e.g. after the city changes). */
    static void clear(Context c) {
        Config.prefs(c).edit().remove("w_city").remove("w_lat").remove("w_lon").remove("w_temp")
                .remove("w_code").remove("w_day").remove("w_time").apply();
    }

    /** "lat,lon" directly, otherwise a geocoding lookup by name. */
    private static double[] locate(String city) throws Exception {
        double[] coords = parseCoords(city);
        if (coords != null) return coords;

        // "Name, Region, Country": search by the name, prefer a result whose region/country matches the rest.
        String[] parts = city.split(",");
        String name = parts[0].trim();
        JSONObject geo = get("https://geocoding-api.open-meteo.com/v1/search?count=10&name=" + URLEncoder.encode(name, "UTF-8"));
        JSONArray results = geo.optJSONArray("results");
        if (results == null || results.length() == 0) return null;
        JSONObject best = results.getJSONObject(0);
        if (parts.length > 1) {
            String hint = city.substring(city.indexOf(',') + 1).toLowerCase(Locale.ROOT);
            for (int i = 0; i < results.length(); i++) {
                JSONObject r = results.getJSONObject(i);
                String where = (r.optString("admin1") + " " + r.optString("admin2") + " " + r.optString("country")
                        + " " + r.optString("country_code")).toLowerCase(Locale.ROOT);
                boolean all = true;
                for (String h : hint.split(",")) {
                    String t = h.trim();
                    if (!t.isEmpty() && !where.contains(t)) all = false;
                }
                if (all) {
                    best = r;
                    break;
                }
            }
        }
        return new double[]{best.getDouble("latitude"), best.getDouble("longitude")};
    }

    /** "12.34,-56.78" to {lat, lon}; null if it isn't a coordinate pair. */
    static double[] parseCoords(String s) {
        if (s == null) return null;
        String[] parts = s.trim().split("\\s*,\\s*");
        if (parts.length != 2) return null;
        try {
            double lat = Double.parseDouble(parts[0]), lon = Double.parseDouble(parts[1]);
            if (lat < -90 || lat > 90 || lon < -180 || lon > 180) return null;
            return new double[]{lat, lon};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Fahrenheit where that's the local convention. */
    private static boolean fahrenheit() {
        String c = Locale.getDefault().getCountry();
        return "US".equals(c) || "LR".equals(c) || "MM".equals(c) || "BS".equals(c) || "KY".equals(c) || "PW".equals(c);
    }

    private static void deliver(SharedPreferences p, Listener l) {
        l.onWeather(icon(p.getInt("w_code", 0), p.getBoolean("w_day", true)), p.getInt("w_temp", 0) + "°");
    }

    /** WMO weather code to icon. */
    static int icon(int code, boolean day) {
        if (code <= 1) return day ? R.drawable.ic_sun : R.drawable.ic_moon;
        if (code <= 48) return R.drawable.ic_cloud;
        if (code <= 67 || (code >= 80 && code <= 82)) return R.drawable.ic_rain;
        if (code <= 77 || code == 85 || code == 86) return R.drawable.ic_snow;
        return R.drawable.ic_storm;
    }

    private static JSONObject get(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(8000);
        try (InputStream in = conn.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            return new JSONObject(out.toString("UTF-8"));
        } finally {
            conn.disconnect();
        }
    }
}
