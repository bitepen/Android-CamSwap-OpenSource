package io.github.zensu357.camswap;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Environment;
import android.util.Log;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;

public class SwitchablePreview {
    private static final String TAG = "CamSwap_Switch";
    public static final String ACTION_TOGGLE_CAMERA = "io.github.zensu357.camswap.ACTION_TOGGLE_CAMERA";
    public static final String EXTRA_USE_ORIGINAL = "use_original";

    // 内存缓存，默认 false（使用虚拟相机）
    private static volatile boolean sUseOriginalCamera = false;
    private static volatile boolean sReceiverRegistered = false;

    /**
     * 宿主应用进程中调用：获取当前是否使用原相机
     */
    public static boolean useOriginalCamera() {
        // 双重保障：优先从最新同步状态读取，并定期/按需校验配置文件
        return sUseOriginalCamera;
    }

    public static void setOriginalCamera(boolean original) {
        sUseOriginalCamera = original;
        Log.i(TAG, "Camera source switched to: " + (original ? "ORIGINAL" : "VIRTUAL"));
    }

    /**
     * 在 HookMain 初始化时注入，用于在目标应用进程监听广播和配置
     */
    public static void initInTargetProcess(Context context) {
        if (context == null || sReceiverRegistered) return;

        // 1. 首次启动时主动读取一次磁盘配置文件
        reloadFromConfigFile();

        // 2. 动态注册广播接收器，接收悬浮窗发来的即时切换通知
        try {
            IntentFilter filter = new IntentFilter(ACTION_TOGGLE_CAMERA);
            BroadcastReceiver receiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context ctx, Intent intent) {
                    if (ACTION_TOGGLE_CAMERA.equals(intent.getAction())) {
                        boolean original = intent.getBooleanExtra(EXTRA_USE_ORIGINAL, !sUseOriginalCamera);
                        setOriginalCamera(original);
                        
                        // 触发相机流更新（通知 HookMain 联动）
                        HookMain.onCameraSourceSwitched(original);
                    }
                }
            };

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
            } else {
                context.registerReceiver(receiver, filter);
            }
            sReceiverRegistered = true;
            Log.i(TAG, "Dynamic camera switch receiver registered successfully.");
        } catch (Throwable t) {
            Log.e(TAG, "Failed to register switch receiver", t);
        }
    }

    /**
     * 直接从 CamSwap 的公共配置目录读取状态
     */
    public static void reloadFromConfigFile() {
        try {
            // CamSwap 存储配置的标准路径
            File configFile = new File(Environment.getExternalStorageDirectory(), "Android/data/io.github.zensu357.camswap/files/cs_config.json");
            if (!configFile.exists()) {
                configFile = new File("/sdcard/DCIM/Camera1/cs_config.json"); // 部分版本的兼容路径
            }
            if (configFile.exists() && configFile.canRead()) {
                FileInputStream fis = new FileInputStream(configFile);
                byte[] data = new byte[(int) configFile.length()];
                fis.read(data);
                fis.close();
                String jsonStr = new String(data, StandardCharsets.UTF_8);
                JSONObject json = new JSONObject(jsonStr);
                if (json.has("use_original_camera")) {
                    sUseOriginalCamera = json.getBoolean("use_original_camera");
                }
            }
        } catch (Throwable ignored) {
        }
    }
}