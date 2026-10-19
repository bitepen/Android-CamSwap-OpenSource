package io.github.zensu357.camswap;

public class SwitchablePreview {
    // 标记当前是否使用原相机，默认 false（即使用虚拟相机）
    private static volatile boolean originalCamera = false;

    public static boolean useOriginalCamera() {
        return originalCamera;
    }

    public static void setOriginalCamera(boolean useOriginal) {
        originalCamera = useOriginal;
    }

    public static void toggle() {
        originalCamera = !originalCamera;
    }
}