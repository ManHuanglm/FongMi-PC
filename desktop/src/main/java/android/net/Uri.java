package android.net;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 桌面端 android.net.Uri 等价实现（基于 java.net.URI 的常用子集）
 */
public class Uri {

    private final String uri;

    private Uri(String uri) {
        this.uri = uri == null ? "" : uri;
    }

    public static Uri parse(String uriString) {
        return new Uri(uriString);
    }

    public static Uri fromFile(File file) {
        try {
            return new Uri(file.toURI().toString());
        } catch (Exception e) {
            return new Uri("file://" + file.getAbsolutePath());
        }
    }

    public static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    /** 对齐安卓语义：仅编码 allow 白名单之外的字符 */
    public static String encode(String s, String allow) {
        StringBuilder sb = new StringBuilder();
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        for (byte b : bytes) {
            char c = (char) (b & 0xFF);
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || "-_.*".indexOf(c) >= 0 || (allow != null && allow.indexOf(c) >= 0)) {
                sb.append(c);
            } else {
                sb.append('%').append(String.format("%02X", b));
            }
        }
        return sb.toString();
    }

    public static String decode(String s) {
        return URLDecoder.decode(s, StandardCharsets.UTF_8);
    }

    public String getScheme() {
        int index = uri.indexOf(':');
        if (index <= 0) return null;
        String scheme = uri.substring(0, index);
        return scheme.matches("[A-Za-z][A-Za-z0-9+.-]*") ? scheme : null;
    }

    public String getHost() {
        URI parsed = tryParse();
        if (parsed == null) return null;
        return parsed.getHost();
    }

    public int getPort() {
        URI parsed = tryParse();
        return parsed == null ? -1 : parsed.getPort();
    }

    public String getPath() {
        URI parsed = tryParse();
        if (parsed != null) return parsed.getRawPath();
        // 非层级 URI（如 mailto:xxx）回退取冒号后内容
        int index = uri.indexOf(':');
        return index >= 0 && index < uri.length() - 1 ? uri.substring(index + 1) : null;
    }

    public String getLastPathSegment() {
        String path = getPath();
        if (path == null) return null;
        int query = path.indexOf('?');
        if (query >= 0) path = path.substring(0, query);
        if (path.isEmpty()) return null;
        int index = path.lastIndexOf('/');
        return index >= 0 ? path.substring(index + 1) : path;
    }

    public String getQuery() {
        URI parsed = tryParse();
        return parsed == null ? null : parsed.getRawQuery();
    }

    public String getUserInfo() {
        URI parsed = tryParse();
        return parsed == null ? null : parsed.getUserInfo();
    }

    public String getQueryParameter(String key) {
        for (NameValuePair pair : parseQuery()) {
            if (pair.name.equals(key)) return pair.value;
        }
        return null;
    }

    public List<String> getQueryParameters(String key) {
        List<String> result = new ArrayList<>();
        for (NameValuePair pair : parseQuery()) {
            if (pair.name.equals(key)) result.add(pair.value);
        }
        return result;
    }

    public boolean isAbsolute() {
        return getScheme() != null;
    }

    public Uri.Builder buildUpon() {
        return new Builder(uri);
    }

    @Override
    public String toString() {
        return uri;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Uri && uri.equals(((Uri) o).uri);
    }

    @Override
    public int hashCode() {
        return uri.hashCode();
    }

    private URI tryParse() {
        try {
            String scheme = getScheme();
            // java.net.URI 无法解析非标准字符，先交由构造器校验
            return scheme != null ? URI.create(uri) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private List<NameValuePair> parseQuery() {
        String query = getQuery();
        List<NameValuePair> pairs = new ArrayList<>();
        if (query == null) return pairs;
        for (String part : query.split("&")) {
            if (part.isEmpty()) continue;
            int eq = part.indexOf('=');
            String name = eq >= 0 ? part.substring(0, eq) : part;
            String value = eq >= 0 ? part.substring(eq + 1) : "";
            pairs.add(new NameValuePair(decode(name), decode(value)));
        }
        return pairs;
    }

    private record NameValuePair(String name, String value) {
    }

    public static class Builder {

        private String scheme;
        private String authority;
        private String path;
        private String encodedQuery;
        private String fragment;

        private Builder(String base) {
            Uri parsed = Uri.parse(base);
            this.scheme = parsed.getScheme();
            this.authority = extractAuthority(base);
            this.path = parsed.getPath();
            this.encodedQuery = parsed.getQuery();
        }

        private String extractAuthority(String base) {
            int schemeEnd = base.indexOf("://");
            if (schemeEnd < 0) return null;
            int start = schemeEnd + 3;
            int end = base.length();
            for (int i = start; i < base.length(); i++) {
                char c = base.charAt(i);
                if (c == '/' || c == '?' || c == '#') {
                    end = i;
                    break;
                }
            }
            return base.substring(start, end);
        }

        public Builder scheme(String scheme) {
            this.scheme = scheme;
            return this;
        }

        public Builder authority(String authority) {
            this.authority = authority;
            return this;
        }

        public Builder path(String path) {
            this.path = path;
            return this;
        }

        public Builder appendQueryParameter(String key, String value) {
            String pair = encode(key) + "=" + encode(value == null ? "" : value);
            this.encodedQuery = encodedQuery == null || encodedQuery.isEmpty() ? pair : encodedQuery + "&" + pair;
            return this;
        }

        public Builder fragment(String fragment) {
            this.fragment = fragment;
            return this;
        }

        public Uri build() {
            StringBuilder sb = new StringBuilder();
            if (scheme != null) sb.append(scheme).append("://");
            if (authority != null) sb.append(authority);
            if (path != null && !path.isEmpty()) sb.append(path.startsWith("/") ? path : "/" + path);
            if (encodedQuery != null && !encodedQuery.isEmpty()) sb.append('?').append(encodedQuery);
            if (fragment != null) sb.append('#').append(fragment);
            return new Uri(sb.toString());
        }
    }
}
