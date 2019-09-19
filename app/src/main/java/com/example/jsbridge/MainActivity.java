package com.example.jsbridge;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.JsResult;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.EditText;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private Button refreshBtn;
    private Button showBtn;
    private Button showBtn2;
    private EditText editText;
    private MainActivity self = this;
    private NativeSDK nativeSDK = new NativeSDK(this);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        webView = findViewById(R.id.webView);
        refreshBtn = findViewById(R.id.refreshBtn);
        showBtn = findViewById(R.id.showBtn);
        showBtn2 = findViewById(R.id.showBtn2);
        editText = findViewById(R.id.editText);

        webView.loadUrl("http://30.208.68.57:8080/?timestamp" + new Date().getTime());

        /**
        * 默认是禁止执行js脚本的，这里要打开
        * */
        webView.getSettings().setJavaScriptEnabled(true);
        // 拦截web端的alert弹窗
//        webView.setWebChromeClient(new WebChromeClient() {
//            @Override
//            public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
//                if (!message.startsWith("jsbridge://")) {
//                    return super.onJsAlert(view, url, message, result);
//                }
//
//                String text = message.substring(message.indexOf("=") + 1);
//                self.showNativeDialog(text);
//
//                result.confirm();
//                return true;
//            }
//        });
        // 注入js api 的方式，这里就不需要拦截了
        webView.setWebChromeClient(new WebChromeClient());
        webView.addJavascriptInterface(new NativeBridge(this), "NativeBridge");


        refreshBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                webView.loadUrl("http://30.208.68.57:8080/?timestamp" + new Date().getTime());
            }
        });

        /**
        * 拿到原生输入框的内容
        * */
        showBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String inputValue = editText.getText().toString();
                self.showWebDialog(inputValue);
            }
        });

        /**
         * 获取web输入框的值，并用原生弹窗显示
         */
        showBtn2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                nativeSDK.getWebEditTextValue(new Callback() {
                    @Override
                    public void invoke(String value) {
                        new AlertDialog.Builder(self)
                                .setMessage("web 输入值：" + value)
                                .create()
                                .show();
                    }
                });
            }
        });
    }

    /**
    * 调用web端js的代码
    * */
    private void showWebDialog (String text) {
        String jsCode = String.format("window.showWebDialog('%s')", text);
        webView.evaluateJavascript(jsCode, null);
    }

    /**
    * 原生端的alert弹窗
    * */
//    private  void showNativeDialog (String text) {
//        new AlertDialog.Builder(this).setMessage(text).create().show();
//    }

    interface Callback {
        void invoke(String value);
    }

    class NativeSDK {
        private Context ctx;
        private int id = 1;
        private Map<Integer, Callback> callbackMap = new HashMap();
        NativeSDK(Context ctx) {
            this.ctx = ctx;
        }

        void getWebEditTextValue(Callback callback) {
            int callbackId = id++;
            callbackMap.put(callbackId, callback);
            final String jsCode = String.format("window.JSSDK.getWebEditTextValue(%s)", callbackId);
            ((MainActivity)ctx).runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    ((MainActivity)ctx).webView.evaluateJavascript(jsCode, null);
                }
            });
        }

        void receiveMessage(int callbackId, String value) {
            if (callbackMap.containsKey(callbackId)) {
                callbackMap.get(callbackId).invoke(value);
            }
        }
    }


    class NativeBridge {
        private Context ctx;
        NativeBridge(Context ctx) {
            this.ctx = ctx;
        }

        @JavascriptInterface
        public void showNativeDialog(String text) {
            new AlertDialog.Builder(ctx).setMessage(text).create().show();
        }

        @JavascriptInterface
        public void getNativeEditTextValue(int callbackId) {
            final MainActivity mainActivity = (MainActivity)ctx;
            String value = mainActivity.editText.getText().toString();
            final String jsCode = String.format("window.JSSDK.receiveMessage(%s, '%s')", callbackId, value);
            mainActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mainActivity.webView.evaluateJavascript(jsCode, null);
                }
            });
        }

        @JavascriptInterface
        public void receiveMessage(int callbackId, String value) {
            ((MainActivity)ctx).nativeSDK.receiveMessage(callbackId, value);
        }
    }
}
