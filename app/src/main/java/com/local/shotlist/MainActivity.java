package com.local.shotlist;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.TextView;
import android.widget.Toast;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private static final int REQ_FILE_CHOOSER = 1001;
    private static final int REQ_SAVE_TEXT = 1002;
    private WebView webView;
    private ValueCallback<Uri[]> fileChooserCallback;
    private String pendingSaveText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            initWebView();
        } catch (Throwable t) {
            showFatalError(t);
        }
    }

    private void initWebView() {
        webView = new WebView(this);
        webView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setLoadWithOverviewMode(false);
        s.setUseWideViewPort(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);

        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(
                    WebView webView,
                    ValueCallback<Uri[]> filePathCallback,
                    FileChooserParams fileChooserParams) {
                if (fileChooserCallback != null) {
                    fileChooserCallback.onReceiveValue(null);
                }
                fileChooserCallback = filePathCallback;
                Intent intent;
                try {
                    intent = fileChooserParams.createIntent();
                } catch (Throwable ignored) {
                    intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    intent.setType("application/json");
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                }
                try {
                    startActivityForResult(intent, REQ_FILE_CHOOSER);
                    return true;
                } catch (Throwable e) {
                    fileChooserCallback = null;
                    Toast.makeText(MainActivity.this, "无法打开文件选择器", Toast.LENGTH_LONG).show();
                    return false;
                }
            }
        });

        webView.addJavascriptInterface(new AndroidBridge(), "AndroidBridge");
        setContentView(webView);
        webView.loadUrl("file:///android_asset/index.html");
    }

    public class AndroidBridge {
        @JavascriptInterface
        public void copyText(String text) {
            runOnUiThread(() -> {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (clipboard != null) {
                    clipboard.setPrimaryClip(ClipData.newPlainText("拍摄清单", text));
                    Toast.makeText(MainActivity.this, "已复制", Toast.LENGTH_SHORT).show();
                }
            });
        }

        @JavascriptInterface
        public void saveText(String filename, String content) {
            runOnUiThread(() -> {
                pendingSaveText = content;
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("application/json");
                intent.putExtra(Intent.EXTRA_TITLE, filename);
                try {
                    startActivityForResult(intent, REQ_SAVE_TEXT);
                } catch (Throwable e) {
                    pendingSaveText = null;
                    Toast.makeText(MainActivity.this, "无法打开保存窗口", Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_FILE_CHOOSER) {
            if (fileChooserCallback != null) {
                Uri[] result = null;
                if (resultCode == RESULT_OK && data != null) {
                    if (data.getClipData() != null) {
                        ClipData clip = data.getClipData();
                        result = new Uri[clip.getItemCount()];
                        for (int i = 0; i < clip.getItemCount(); i++) {
                            result[i] = clip.getItemAt(i).getUri();
                        }
                    } else {
                        Uri uri = data.getData();
                        if (uri != null) result = new Uri[]{uri};
                    }
                }
                fileChooserCallback.onReceiveValue(result);
                fileChooserCallback = null;
            }
            return;
        }
        if (requestCode == REQ_SAVE_TEXT) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null && pendingSaveText != null) {
                try (OutputStream os = getContentResolver().openOutputStream(data.getData())) {
                    if (os != null) {
                        os.write(pendingSaveText.getBytes(StandardCharsets.UTF_8));
                        os.flush();
                        Toast.makeText(this, "备份已保存", Toast.LENGTH_SHORT).show();
                    }
                } catch (Throwable e) {
                    Toast.makeText(this, "保存失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            }
            pendingSaveText = null;
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.removeJavascriptInterface("AndroidBridge");
            webView.stopLoading();
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }

    private void showFatalError(Throwable t) {
        TextView tv = new TextView(this);
        tv.setPadding(32, 48, 32, 48);
        tv.setTextSize(16);
        tv.setText("拍摄清单启动失败\n\n" + t.getClass().getName() + "\n" + String.valueOf(t.getMessage()) +
                "\n\n请把这一页截图发给我。\n\nWebView Provider: " + getWebViewProviderInfo());
        setContentView(tv);
    }

    private String getWebViewProviderInfo() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                android.content.pm.PackageInfo info = WebView.getCurrentWebViewPackage();
                if (info != null) return info.packageName + " " + info.versionName;
            }
        } catch (Throwable ignored) {}
        return "无法读取";
    }
}
