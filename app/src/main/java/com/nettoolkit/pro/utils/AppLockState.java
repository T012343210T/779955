package com.nettoolkit.pro.utils;

/**
 * وضعیت باز/بسته بودن اپ در حافظه (نه دیسک). با کشتن پروسه، دوباره قفل می‌شود.
 * هیچ پسوردی اینجا ذخیره نمی‌شود؛ فقط یک پرچم boolean است.
 */
public class AppLockState {
    public static boolean unlocked = false;
}
