// SPDX-FileCopyrightText: 2026 Ksleephead
// SPDX-License-Identifier: GPL-3.0-only

package com.tankM6n.hotkey;

import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.NativeHookException;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener;

/** Owns JNativeHook registration and maps global keys to application actions. */
public final class GlobalHotkeyService implements AutoCloseable {
    private static final Object NATIVE_HOOK_LOCK = new Object();
    private static volatile boolean nativeHookRegistered;

    private final Runnable stopAction;
    private final Runnable startTrainingAction;
    private final Runnable startCookingAction;

    public GlobalHotkeyService(
            Runnable stopAction,
            Runnable startTrainingAction,
            Runnable startCookingAction) {
        this.stopAction = stopAction;
        this.startTrainingAction = startTrainingAction;
        this.startCookingAction = startCookingAction;
    }

    public void start() {
        synchronized (NATIVE_HOOK_LOCK) {
            if (nativeHookRegistered) {
                return;
            }
            try {
                GlobalScreen.registerNativeHook();
                nativeHookRegistered = true;
            } catch (NativeHookException e) {
                System.err.println("NativeHook 注册失败: " + e.getMessage());
                return;
            }

            GlobalScreen.addNativeKeyListener(new NativeKeyListener() {
                @Override
                public void nativeKeyPressed(NativeKeyEvent event) {
                    if (event.getKeyCode() == NativeKeyEvent.VC_DOWN
                            || event.getKeyCode() == NativeKeyEvent.VC_PAGE_DOWN) {
                        System.out.println("停止按钮生效");
                        stopAction.run();
                    }
                    if (event.getKeyCode() == NativeKeyEvent.VC_UP
                            || event.getKeyCode() == NativeKeyEvent.VC_PAGE_UP) {
                        System.out.println("pageUp游戏内开始");
                        startTrainingAction.run();
                    }
                    if (event.getKeyCode() == NativeKeyEvent.VC_LEFT) {
                        System.out.println("左方向键执行一次附近物品识别+制作简易米饭");
                        startCookingAction.run();
                    }
                }

                @Override
                public void nativeKeyReleased(NativeKeyEvent event) {
                }

                @Override
                public void nativeKeyTyped(NativeKeyEvent event) {
                }
            });
        }
    }

    @Override
    public void close() {
        synchronized (NATIVE_HOOK_LOCK) {
            if (!nativeHookRegistered) {
                return;
            }
            try {
                GlobalScreen.unregisterNativeHook();
            } catch (Exception e) {
                System.err.println("卸载 NativeHook 失败: " + e.getMessage());
            } finally {
                nativeHookRegistered = false;
            }
        }
    }
}
