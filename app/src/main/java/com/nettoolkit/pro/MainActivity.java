package com.nettoolkit.pro;

import android.Manifest;
import android.app.KeyguardManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.nettoolkit.pro.fragments.DashboardFragment;
import com.nettoolkit.pro.fragments.MonitoringFragment;
import com.nettoolkit.pro.fragments.NetworkInfoFragment;
import com.nettoolkit.pro.fragments.ScanFragment;
import com.nettoolkit.pro.fragments.ToolsFragment;
import com.nettoolkit.pro.utils.AppLockState;

public class MainActivity extends AppCompatActivity {

    private View lockOverlay;
    private KeyguardManager keyguardManager;
    private boolean authInProgress = false;
    private boolean navReady = false;

    private final ActivityResultLauncher<String> locationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                // چه تایید بشه چه نشه، دوباره فراگمنت فعلی را رفرش می‌کنیم تا مقادیر Wi-Fi به‌روز شوند
                loadFragment(new DashboardFragment());
            });

    private final ActivityResultLauncher<Intent> confirmCredentialLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                authInProgress = false;
                if (result.getResultCode() == RESULT_OK) {
                    AppLockState.unlocked = true;
                    lockOverlay.setVisibility(View.GONE);
                    initNavigationIfNeeded();
                }
                // در صورت لغو/شکست، overlay باز می‌ماند و دکمه «تأیید هویت» دوباره قابل لمس است
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        keyguardManager = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        lockOverlay = findViewById(R.id.lock_overlay);
        MaterialButton btnUnlock = findViewById(R.id.btn_unlock);
        btnUnlock.setOnClickListener(v -> launchConfirmCredential());
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!authInProgress) {
            checkLock();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        // فقط وقتی خودمان صفحه‌ی تأیید هویت سیستم را باز نکرده‌ایم، با خروج از اپ دوباره قفل کن
        if (!authInProgress) {
            AppLockState.unlocked = false;
        }
    }

    private void checkLock() {
        if (keyguardManager == null || !keyguardManager.isDeviceSecure()) {
            // گوشی قفل صفحه ندارد؛ نمی‌توان این محافظت را اجرا کرد، پس آزاد بگذار
            TextView subtitle = findViewById(R.id.tv_lock_subtitle);
            if (!AppLockState.unlocked) {
                subtitle.setText(R.string.lock_no_device_credential);
            }
            AppLockState.unlocked = true;
            lockOverlay.setVisibility(View.GONE);
            initNavigationIfNeeded();
            return;
        }

        if (AppLockState.unlocked) {
            lockOverlay.setVisibility(View.GONE);
            initNavigationIfNeeded();
            return;
        }

        lockOverlay.setVisibility(View.VISIBLE);
        launchConfirmCredential();
    }

    private void launchConfirmCredential() {
        if (keyguardManager == null) return;
        Intent intent = keyguardManager.createConfirmDeviceCredentialIntent(
                getString(R.string.lock_title), getString(R.string.lock_subtitle));
        if (intent != null) {
            authInProgress = true;
            confirmCredentialLauncher.launch(intent);
        }
    }

    private void initNavigationIfNeeded() {
        if (navReady) return;
        navReady = true;

        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);
        loadFragment(new DashboardFragment());
        ensureLocationPermission();

        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_dashboard) {
                loadFragment(new DashboardFragment());
                return true;
            } else if (id == R.id.nav_network) {
                loadFragment(new NetworkInfoFragment());
                return true;
            } else if (id == R.id.nav_monitor) {
                loadFragment(new MonitoringFragment());
                return true;
            } else if (id == R.id.nav_scan) {
                loadFragment(new ScanFragment());
                return true;
            } else if (id == R.id.nav_more) {
                loadFragment(new ToolsFragment());
                return true;
            }
            return false;
        });
    }

    private void ensureLocationPermission() {
        // بدون این مجوز، SSID/BSSID و اطلاعات دقیق Wi-Fi روی اندروید ۸+ در دسترس نیست
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
        }
    }

    private void loadFragment(Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();
    }
}
