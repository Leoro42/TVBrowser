package ru.tvbrowser;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

public class MainActivity extends Activity {

    static final String HOME = "file:///android_asset/start.html";
    static final String HAPP_PKG = "com.happproxy";
    static final String UA = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

    FrameLayout root;
    WebView web;
    CursorView cursor;
    View customView;
    WebChromeClient.CustomViewCallback customCb;
    boolean cursorOn = false;
    boolean backLong = false;
    float cx = 400, cy = 300;

    class CursorView extends View {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        CursorView(Context c) { super(c); setFocusable(false); setClickable(false); }
        @Override protected void onDraw(Canvas c) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xCC000000); c.drawCircle(cx, cy, 15, p);
            p.setColor(0xFFFFFFFF); c.drawCircle(cx, cy, 11, p);
            p.setColor(0xFF3B82F6); c.drawCircle(cx, cy, 5, p);
        }
    }

    class Bridge {
        @JavascriptInterface public void openHapp() {
            runOnUiThread(new Runnable() { public void run() { launchHapp(); } });
        }
        @JavascriptInterface public void exitApp() {
            runOnUiThread(new Runnable() { public void run() { finish(); } });
        }
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);

        root = new FrameLayout(this);
        web = new WebView(this);
        root.addView(web, new FrameLayout.LayoutParams(-1, -1));
        cursor = new CursorView(this);
        root.addView(cursor, new FrameLayout.LayoutParams(-1, -1));
        cursor.setVisibility(View.GONE);
        setContentView(root);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setUserAgentString(UA);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setAllowFileAccess(true);
        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(web, true);

        web.addJavascriptInterface(new Bridge(), "Android");
        web.setWebViewClient(new WebViewClient() {
            @Override public void onPageStarted(WebView v, String url, Bitmap f) {
                setCursor(!url.startsWith("file:///android_asset"));
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public void onShowCustomView(View v, CustomViewCallback cb) {
                customView = v; customCb = cb;
                root.addView(v, new FrameLayout.LayoutParams(-1, -1));
                web.setVisibility(View.GONE);
                cursor.setVisibility(View.GONE);
            }
            @Override public void onHideCustomView() { hideCustom(); }
        });
        web.loadUrl(HOME);
        web.requestFocus();
    }

    void setCursor(boolean on) {
        cursorOn = on;
        if (on && root.getWidth() > 0) { cx = root.getWidth() / 2f; cy = root.getHeight() / 2f; }
        cursor.setVisibility(on && customView == null ? View.VISIBLE : View.GONE);
        cursor.invalidate();
    }

    void hideCustom() {
        if (customView == null) return;
        root.removeView(customView);
        customView = null;
        if (customCb != null) customCb.onCustomViewHidden();
        web.setVisibility(View.VISIBLE);
        setCursor(cursorOn);
        web.requestFocus();
    }

    void launchHapp() {
        PackageManager pm = getPackageManager();
        Intent i = pm.getLeanbackLaunchIntentForPackage(HAPP_PKG);
        if (i == null) i = pm.getLaunchIntentForPackage(HAPP_PKG);
        if (i == null) {
            Toast.makeText(this, "Happ не установлен на ТВ. Установите его и вернитесь.",
                    Toast.LENGTH_LONG).show();
        } else {
            startActivity(i);
        }
    }

    void goBack() {
        if (customView != null) hideCustom();
        else if (web.canGoBack()) web.goBack();
        else if (!HOME.equals(web.getUrl())) web.loadUrl(HOME);
        else finish();
    }

    void sendTouch(int action) {
        long t = SystemClock.uptimeMillis();
        MotionEvent e = MotionEvent.obtain(t, t, action, cx, cy, 0);
        e.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        web.dispatchTouchEvent(e);
        e.recycle();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int k = e.getKeyCode();
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;

        if (k == KeyEvent.KEYCODE_BACK) {
            if (down) {
                if (e.isLongPress() && !backLong) { backLong = true; hideCustom(); web.loadUrl(HOME); }
            } else {
                if (!backLong) goBack();
                backLong = false;
            }
            return true;
        }

        if (k == KeyEvent.KEYCODE_MENU) {
            if (!down) setCursor(!cursorOn);
            return true;
        }

        if (cursorOn && customView == null) {
            int w = root.getWidth(), h = root.getHeight();
            int step = 14 + Math.min(e.getRepeatCount(), 25) * 3;
            switch (k) {
                case KeyEvent.KEYCODE_DPAD_UP:
                case KeyEvent.KEYCODE_DPAD_DOWN:
                case KeyEvent.KEYCODE_DPAD_LEFT:
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    if (down) {
                        int dx = k == KeyEvent.KEYCODE_DPAD_LEFT ? -step : k == KeyEvent.KEYCODE_DPAD_RIGHT ? step : 0;
                        int dy = k == KeyEvent.KEYCODE_DPAD_UP ? -step : k == KeyEvent.KEYCODE_DPAD_DOWN ? step : 0;
                        float nx = cx + dx, ny = cy + dy;
                        if (ny < 0 || ny > h - 1) web.scrollBy(0, dy * 2);
                        if (nx < 0 || nx > w - 1) web.scrollBy(dx * 2, 0);
                        cx = Math.max(0, Math.min(w - 1, nx));
                        cy = Math.max(0, Math.min(h - 1, ny));
                        cursor.invalidate();
                    }
                    return true;
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_BUTTON_A:
                    if (down) { if (e.getRepeatCount() == 0) sendTouch(MotionEvent.ACTION_DOWN); }
                    else sendTouch(MotionEvent.ACTION_UP);
                    return true;
            }
        }
        return super.dispatchKeyEvent(e);
    }

    @Override protected void onPause() { super.onPause(); web.onPause(); }
    @Override protected void onResume() { super.onResume(); web.onResume(); }
}
