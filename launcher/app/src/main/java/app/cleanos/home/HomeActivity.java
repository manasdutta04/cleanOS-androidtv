package app.cleanos.home;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;

/** The Home screen. */
public class HomeActivity extends Activity {

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // Home has nowhere to go back to.
        if (keyCode == KeyEvent.KEYCODE_BACK) return true;
        return super.onKeyDown(keyCode, event);
    }
}
