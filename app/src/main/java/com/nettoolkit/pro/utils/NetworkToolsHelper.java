package com.nettoolkit.pro.utils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * پیاده‌سازی واقعی ابزارهای شبکه با سوکت خام جاوا و باینری سیستمی ping.
 * همه‌ی متدها async هستند و نتیجه روی Main Thread برمی‌گردد.
 */
public class NetworkToolsHelper {

    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();
    private static final android.os.Handler MAIN = new android.os.Handler(android.os.Looper.getMainLooper());

    public interface TextCallback {
        void onResult(String output, boolean success);
    }

    public interface PortScanCallback {
        void onPortResult(int port, boolean open);
        void onProgress(int scanned, int total);
        void onDone();
    }

    private static void postText(TextCallback cb, String output, boolean success) {
        MAIN.post(() -> cb.onResult(output, success));
    }

    // ---------------- PING (با باینری سیستمی + fallback) ----------------
    public static void ping(String host, int count, TextCallback callback) {
        EXECUTOR.execute(() -> {
            StringBuilder out = new StringBuilder();
            boolean success = false;
            boolean binaryWorked = false;
            try {
                Process process = Runtime.getRuntime().exec("ping -c " + count + " -w " + (count * 3) + " " + host);
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    out.append(line).append("\n");
                }
                int exit = process.waitFor();
                success = exit == 0;
                binaryWorked = true;
                if (out.length() == 0) {
                    BufferedReader err = new BufferedReader(new InputStreamReader(process.getErrorStream()));
                    while ((line = err.readLine()) != null) out.append(line).append("\n");
                }
            } catch (Exception e) {
                out.append("باینری ping سیستم در دسترس نیست (محدودیت این گوشی/رام)\n");
            }

            // اگر باینری سیستمی جواب نداد یا خروجی خالی بود، یک تست ساده جایگزین با سوکت انجام بده
            if (!binaryWorked || out.length() == 0) {
                out.append("در حال تلاش با روش جایگزین (بررسی دسترسی‌پذیری)...\n");
                for (int i = 1; i <= count; i++) {
                    long start = System.currentTimeMillis();
                    boolean reachable = false;
                    try {
                        reachable = InetAddress.getByName(host).isReachable(2000);
                    } catch (Exception ignored) { }
                    long elapsed = System.currentTimeMillis() - start;
                    if (reachable) {
                        out.append("پاسخ از ").append(host).append(": زمان=").append(elapsed).append("ms\n");
                        success = true;
                    } else {
                        out.append("درخواست ").append(i).append(" بدون پاسخ ماند (Timeout)\n");
                    }
                }
            }

            postText(callback, out.toString().trim(), success);
        });
    }

    // ---------------- DNS LOOKUP ----------------
    public static void dnsLookup(String host, TextCallback callback) {
        EXECUTOR.execute(() -> {
            StringBuilder out = new StringBuilder();
            boolean success = false;
            try {
                InetAddress[] addresses = InetAddress.getAllByName(host);
                for (InetAddress addr : addresses) {
                    out.append(addr.getHostAddress()).append("\n");
                }
                success = addresses.length > 0;
            } catch (Exception e) {
                out.append("یافت نشد: ").append(e.getMessage());
            }
            postText(callback, out.toString().trim(), success);
        });
    }

    // ---------------- REVERSE DNS ----------------
    public static void reverseDns(String ip, TextCallback callback) {
        EXECUTOR.execute(() -> {
            String out;
            boolean success;
            try {
                InetAddress addr = InetAddress.getByName(ip);
                String host = addr.getCanonicalHostName();
                success = host != null && !host.equals(ip);
                out = success ? host : "رکورد PTR پیدا نشد";
            } catch (Exception e) {
                out = "خطا: " + e.getMessage();
                success = false;
            }
            postText(callback, out, success);
        });
    }

    // ---------------- PORT SCANNER (TCP Connect) ----------------
    public static void scanPorts(String host, int startPort, int endPort, int timeoutMs, PortScanCallback callback) {
        EXECUTOR.execute(() -> {
            int total = endPort - startPort + 1;
            int scanned = 0;
            for (int port = startPort; port <= endPort; port++) {
                boolean open = false;
                try (Socket socket = new Socket()) {
                    socket.connect(new InetSocketAddress(host, port), timeoutMs);
                    open = true;
                } catch (Exception ignored) { }
                final boolean isOpen = open;
                final int p = port;
                scanned++;
                final int scannedFinal = scanned;
                MAIN.post(() -> {
                    if (isOpen) callback.onPortResult(p, true);
                    callback.onProgress(scannedFinal, total);
                });
            }
            MAIN.post(callback::onDone);
        });
    }

    // ---------------- WHOIS (از طریق RDAP - جایگزین مدرن پروتکل WHOIS) ----------------
    public static void whois(String domain, TextCallback callback) {
        EXECUTOR.execute(() -> {
            StringBuilder out = new StringBuilder();
            boolean success = false;
            try {
                URL url = new URL("https://rdap.org/domain/" + domain);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(6000);
                conn.setReadTimeout(6000);
                conn.setRequestProperty("Accept", "application/rdap+json");
                int code = conn.getResponseCode();
                InputStream is = code < 400 ? conn.getInputStream() : conn.getErrorStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(is));
                StringBuilder raw = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) raw.append(line);

                if (code == 200) {
                    JSONObject json = new JSONObject(raw.toString());
                    out.append("دامنه: ").append(json.optString("ldhName", domain)).append("\n");
                    out.append("وضعیت: ").append(json.optJSONArray("status") != null ? json.optJSONArray("status").join(", ") : "—").append("\n");

                    JSONArray entities = json.optJSONArray("entities");
                    if (entities != null) {
                        for (int i = 0; i < entities.length(); i++) {
                            JSONObject entity = entities.getJSONObject(i);
                            JSONArray roles = entity.optJSONArray("roles");
                            if (roles != null && roles.toString().contains("registrar")) {
                                out.append("ثبت‌کننده: ").append(entity.optString("handle", "—")).append("\n");
                            }
                        }
                    }

                    JSONArray events = json.optJSONArray("events");
                    if (events != null) {
                        for (int i = 0; i < events.length(); i++) {
                            JSONObject ev = events.getJSONObject(i);
                            out.append(ev.optString("eventAction")).append(": ").append(ev.optString("eventDate")).append("\n");
                        }
                    }
                    success = true;
                } else {
                    out.append("اطلاعاتی یافت نشد (کد ").append(code).append(")");
                }
            } catch (Exception e) {
                out.append("خطا: ").append(e.getMessage());
            }
            postText(callback, out.toString().trim(), success);
        });
    }

    // ---------------- WAKE-ON-LAN ----------------
    public static void sendWakeOnLan(String macAddress, String broadcastIp, TextCallback callback) {
        EXECUTOR.execute(() -> {
            String out;
            boolean success;
            try {
                byte[] macBytes = parseMac(macAddress);
                byte[] packet = new byte[6 + 16 * macBytes.length];
                for (int i = 0; i < 6; i++) packet[i] = (byte) 0xff;
                for (int i = 6; i < packet.length; i += macBytes.length) {
                    System.arraycopy(macBytes, 0, packet, i, macBytes.length);
                }
                InetAddress address = InetAddress.getByName(broadcastIp);
                DatagramPacket dp = new DatagramPacket(packet, packet.length, address, 9);
                DatagramSocket socket = new DatagramSocket();
                socket.setBroadcast(true);
                socket.send(dp);
                socket.close();
                out = "پکت Magic Packet ارسال شد به " + macAddress;
                success = true;
            } catch (Exception e) {
                out = "خطا: " + e.getMessage();
                success = false;
            }
            postText(callback, out, success);
        });
    }

    public interface LanScanCallback {
        void onDeviceFound(String ip, long responseTimeMs);
        void onProgress(int scanned, int total);
        void onDone(boolean truncated);
    }

    /**
     * یک ping-sweep روی کل ساب‌نت محلی انجام می‌دهد.
     * به‌خاطر محدودیت‌های اندروید (بدون روت)، هیچ‌کدام از ICMP خام یا ARP table در دسترس نیست؛
     * بنابراین ترکیبی از InetAddress.isReachable و اتصال TCP به چند پورت رایج برای تشخیص "روشن بودن" میزبان استفاده می‌شود.
     */
    public static void scanLan(String localIp, String subnetMask, LanScanCallback callback) {
        EXECUTOR.execute(() -> {
            int ipInt = ipToInt(localIp);
            int maskInt = ipToInt(subnetMask);
            if (ipInt == 0 || maskInt == 0) {
                MAIN.post(() -> callback.onDone(false));
                return;
            }

            int network = ipInt & maskInt;
            int broadcast = network | ~maskInt;
            int hostCount = broadcast - network - 1;

            boolean truncated = false;
            int maxHosts = 512;
            if (hostCount > maxHosts) {
                truncated = true;
                broadcast = network + maxHosts;
            }
            if (hostCount <= 0) {
                MAIN.post(() -> callback.onDone(false));
                return;
            }

            final int total = Math.min(hostCount, maxHosts);
            final boolean finalTruncated = truncated;
            java.util.concurrent.atomic.AtomicInteger scanned = new java.util.concurrent.atomic.AtomicInteger(0);
            java.util.concurrent.ExecutorService pool = Executors.newFixedThreadPool(40);
            List<java.util.concurrent.Future<?>> futures = new ArrayList<>();

            for (int cur = network + 1; cur < broadcast; cur++) {
                final String ip = intToIp(cur);
                if (ip.equals(localIp)) {
                    scanned.incrementAndGet();
                    MAIN.post(() -> {
                        callback.onDeviceFound(ip, 0);
                        callback.onProgress(scanned.get(), total);
                    });
                    continue;
                }
                futures.add(pool.submit(() -> {
                    long start = System.currentTimeMillis();
                    boolean alive = probeHost(ip);
                    long elapsed = System.currentTimeMillis() - start;
                    int done = scanned.incrementAndGet();
                    MAIN.post(() -> {
                        if (alive) callback.onDeviceFound(ip, elapsed);
                        callback.onProgress(done, total);
                    });
                }));
            }

            for (java.util.concurrent.Future<?> f : futures) {
                try { f.get(); } catch (Exception ignored) { }
            }
            pool.shutdown();
            MAIN.post(() -> callback.onDone(finalTruncated));
        });
    }

    private static boolean probeHost(String ip) {
        try {
            InetAddress addr = InetAddress.getByName(ip);
            if (addr.isReachable(250)) return true;
        } catch (Exception ignored) { }

        int[] ports = {80, 443, 22, 445, 139, 8080};
        for (int port : ports) {
            try (Socket s = new Socket()) {
                s.connect(new InetSocketAddress(ip, port), 180);
                return true;
            } catch (java.net.ConnectException refused) {
                return true; // رد شدن اتصال یعنی میزبان روشن است ولی پورت بسته است
            } catch (Exception ignored) { }
        }
        return false;
    }

    /** بهترین تلاش برای گرفتن هاست‌نیم از طریق Reverse DNS؛ اگر شبکه پشتیبانی نکند، خود IP برگردانده می‌شود */
    public static String tryResolveHostname(String ip) {
        try {
            InetAddress addr = InetAddress.getByName(ip);
            String host = addr.getCanonicalHostName();
            return (host == null || host.equals(ip)) ? null : host;
        } catch (Exception e) {
            return null;
        }
    }

    /** نسخه async از tryResolveHostname برای استفاده در UI */
    public static void resolveHostname(String ip, TextCallback callback) {
        EXECUTOR.execute(() -> {
            String host = tryResolveHostname(ip);
            postText(callback, host != null ? host : ip, host != null);
        });
    }

    private static int ipToInt(String ip) {
        try {
            String[] parts = ip.split("\\.");
            if (parts.length != 4) return 0;
            int result = 0;
            for (String part : parts) {
                result = (result << 8) | (Integer.parseInt(part) & 0xff);
            }
            return result;
        } catch (Exception e) {
            return 0;
        }
    }

    private static String intToIp(int ip) {
        return String.format("%d.%d.%d.%d",
                (ip >> 24) & 0xff, (ip >> 16) & 0xff, (ip >> 8) & 0xff, ip & 0xff);
    }

    private static byte[] parseMac(String mac) {
        String[] parts = mac.replace("-", ":").split(":");
        byte[] bytes = new byte[parts.length];
        for (int i = 0; i < parts.length; i++) {
            bytes[i] = (byte) Integer.parseInt(parts[i], 16);
        }
        return bytes;
    }
}
