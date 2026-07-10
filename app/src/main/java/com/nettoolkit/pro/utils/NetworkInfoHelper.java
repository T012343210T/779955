package com.nettoolkit.pro.utils;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.text.format.Formatter;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * جمع‌آوری اطلاعات پایه و شبکه دستگاه.
 * توجه: از اندروید ۶ به بعد گوگل MAC واقعی را مخفی می‌کند و مقداری رندوم برمی‌گرداند،
 * و از اندروید ۱۰ به بعد جدول ARP در دسترس اپ‌های عادی نیست؛ این محدودیت‌های پلتفرم است نه باگ.
 */
public class NetworkInfoHelper {

    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();

    public interface PublicIpCallback {
        void onResult(String ip, boolean success);
    }

    public static class NetworkSnapshot {
        public String internalIpv4 = "—";
        public String internalIpv6 = "—";
        public String wifiName = "—";
        public String bssid = "—";
        public int signalDbm = Integer.MIN_VALUE;
        public String frequencyLabel = "—";
        public int channel = -1;
        public int linkSpeedMbps = -1;
        public String connectionType = "—";
        public boolean internetConnected = false;
        public String gateway = "—";
        public String dns = "—";
        public String subnetMask = "—";
        public String dhcpServer = "—";
    }

    public static NetworkSnapshot buildSnapshot(Context ctx) {
        NetworkSnapshot s = new NetworkSnapshot();

        ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
        WifiManager wm = (WifiManager) ctx.getApplicationContext().getSystemService(Context.WIFI_SERVICE);

        Network activeNetwork = cm != null ? cm.getActiveNetwork() : null;
        NetworkCapabilities caps = activeNetwork != null ? cm.getNetworkCapabilities(activeNetwork) : null;

        // نوع اتصال
        if (caps != null) {
            s.internetConnected = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                s.connectionType = "VPN";
            } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                s.connectionType = "Wi-Fi";
            } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                s.connectionType = "موبایل داده";
            } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                s.connectionType = "اترنت";
            }
        }

        // IPv4 / IPv6 داخلی از طریق NetworkInterface (پایدارتر از WifiInfo)
        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface iface : interfaces) {
                if (!iface.isUp() || iface.isLoopback()) continue;
                List<InetAddress> addresses = Collections.list(iface.getInetAddresses());
                for (InetAddress addr : addresses) {
                    if (addr instanceof Inet4Address && s.internalIpv4.equals("—")) {
                        s.internalIpv4 = addr.getHostAddress();
                    } else if (addr instanceof Inet6Address && !addr.isLinkLocalAddress() && s.internalIpv6.equals("—")) {
                        s.internalIpv6 = addr.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) { }

        // LinkProperties: Gateway / DNS / Subnet
        if (cm != null && activeNetwork != null) {
            LinkProperties lp = cm.getLinkProperties(activeNetwork);
            if (lp != null) {
                if (!lp.getDnsServers().isEmpty()) {
                    StringBuilder dnsBuilder = new StringBuilder();
                    for (InetAddress d : lp.getDnsServers()) {
                        if (dnsBuilder.length() > 0) dnsBuilder.append(", ");
                        dnsBuilder.append(d.getHostAddress());
                    }
                    s.dns = dnsBuilder.toString();
                }
                if (lp.getRoutes() != null) {
                    for (android.net.RouteInfo route : lp.getRoutes()) {
                        if (route.isDefaultRoute() && route.getGateway() != null) {
                            s.gateway = route.getGateway().getHostAddress();
                            break;
                        }
                    }
                }
                List<LinkAddress> linkAddresses = lp.getLinkAddresses();
                for (LinkAddress la : linkAddresses) {
                    if (la.getAddress() instanceof Inet4Address) {
                        s.subnetMask = prefixToMask(la.getPrefixLength());
                        break;
                    }
                }
            }
        }

        // اطلاعات Wi-Fi
        if (wm != null && "Wi-Fi".equals(s.connectionType)) {
            WifiInfo info = wm.getConnectionInfo();
            if (info != null) {
                String ssid = info.getSSID();
                if (ssid != null) {
                    s.wifiName = ssid.replace("\"", "");
                }
                s.bssid = info.getBSSID() != null ? info.getBSSID() : "—";
                s.signalDbm = info.getRssi();
                s.linkSpeedMbps = info.getLinkSpeed();

                int freqMhz = -1;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    freqMhz = info.getFrequency();
                }
                if (freqMhz > 0) {
                    if (freqMhz < 2500) {
                        s.frequencyLabel = "۲٫۴ گیگاهرتز";
                    } else if (freqMhz < 5900) {
                        s.frequencyLabel = "۵ گیگاهرتز";
                    } else {
                        s.frequencyLabel = "۶ گیگاهرتز";
                    }
                    s.channel = frequencyToChannel(freqMhz);
                }

                // DHCP Server (فقط API قدیمی‌تر Deprecated ولی هنوز کار می‌کند)
                try {
                    android.net.DhcpInfo dhcp = wm.getDhcpInfo();
                    if (dhcp != null) {
                        s.dhcpServer = Formatter.formatIpAddress(dhcp.serverAddress);
                        if (s.gateway.equals("—")) {
                            s.gateway = Formatter.formatIpAddress(dhcp.gateway);
                        }
                        if (s.subnetMask.equals("—")) {
                            s.subnetMask = Formatter.formatIpAddress(dhcp.netmask);
                        }
                    }
                } catch (Exception ignored) { }
            }
        }

        return s;
    }

    private static String prefixToMask(int prefixLength) {
        int mask = 0xffffffff << (32 - prefixLength);
        return String.format("%d.%d.%d.%d",
                (mask >> 24) & 0xff, (mask >> 16) & 0xff, (mask >> 8) & 0xff, mask & 0xff);
    }

    public static boolean isLocationEnabled(Context ctx) {
        android.location.LocationManager lm = (android.location.LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE);
        if (lm == null) return false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return lm.isLocationEnabled();
        }
        return lm.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)
                || lm.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER);
    }

    public static int frequencyToChannel(int freqMhz) {
        if (freqMhz == 2484) return 14;
        if (freqMhz >= 2412 && freqMhz <= 2472) return (freqMhz - 2412) / 5 + 1;
        if (freqMhz >= 5170 && freqMhz <= 5825) return (freqMhz - 5000) / 5;
        if (freqMhz >= 5955 && freqMhz <= 7115) return (freqMhz - 5950) / 5 + 1; // Wi-Fi 6E
        return -1;
    }

    /** واکشی IP عمومی به‌صورت async از یک سرویس ساده متنی */
    public static void fetchPublicIp(PublicIpCallback callback) {
        EXECUTOR.execute(() -> {
            String result = null;
            boolean ok = false;
            try {
                URL url = new URL("https://api.ipify.org");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(4000);
                conn.setReadTimeout(4000);
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();
                result = sb.toString().trim();
                ok = !result.isEmpty();
            } catch (Exception e) {
                result = null;
                ok = false;
            }
            String finalResult = result;
            boolean finalOk = ok;
            android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
            mainHandler.post(() -> callback.onResult(finalResult, finalOk));
        });
    }
}
