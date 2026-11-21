package com.qingcheng.ordering;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ImageView;

import java.io.InputStream;
import java.io.InterruptedIOException;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;

/** View bindings belong to the main thread; workers never retain an Activity or ImageView. */
final class ImageLoader {
    static final long FADE_DURATION_MS = 120L;
    private static final int MAX_DOWNLOAD_BYTES = 24 * 1024 * 1024;
    private static final int BINDING_TAG = R.id.qingcheng_image_binding;
    private final ExecutorService downloadExecutor;
    private final ExecutorService decodeExecutor;
    private final Downloader downloader;
    private final Decoder decoder;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Object lock = new Object();
    private final Map<RequestKey, Bitmap> cache = new LinkedHashMap<>(32, 0.75f, true);
    private final Map<RequestKey, Request> inFlight = new LinkedHashMap<>();
    // Binding holds only a WeakReference back to its key, so detached screens can be collected.
    private final Map<ImageView, Binding> bindings = new WeakHashMap<>();
    private final long maxCacheBytes;
    private long cacheBytes;
    private long generation;
    private volatile boolean closed;

    ImageLoader() {
        this(rejectWhenFull(WorkerPools.fixed("image-download", 3, 384)),
                WorkerPools.fixed("image-decode", 2, 1));
    }

    ImageLoader(ExecutorService downloadExecutor, ExecutorService decodeExecutor) {
        this(downloadExecutor, decodeExecutor, ImageLoader::download, ImageLoader::decodeSized,
                Math.max(1024 * 1024L, Math.min(16 * 1024 * 1024L,
                        Runtime.getRuntime().maxMemory() / 16)));
    }

    ImageLoader(ExecutorService downloadExecutor, ExecutorService decodeExecutor,
                Downloader downloader, Decoder decoder, long maxCacheBytes) {
        this.downloadExecutor = Objects.requireNonNull(downloadExecutor);
        this.decodeExecutor = Objects.requireNonNull(decodeExecutor);
        this.downloader = Objects.requireNonNull(downloader);
        this.decoder = Objects.requireNonNull(decoder);
        if (maxCacheBytes <= 0) throw new IllegalArgumentException("Cache budget must be positive");
        this.maxCacheBytes = maxCacheBytes;
    }

    void load(String imageUrl, ImageView imageView) {
        load(imageUrl, imageView, null);
    }

    void load(String imageUrl, ImageView imageView, String authToken) {
        if (closed) return;
        String url = normalize(imageUrl);
        String token = authToken == null || authToken.isEmpty() ? null : authToken;
        // A URL returned by content must never carry the user's bearer token to another origin.
        if (token != null && !sameOrigin(url, ApiClient.BASE_URL)) token = null;
        RequestKey key = new RequestKey(url, requestedSize(imageView), authScope(token));
        Binding previous = binding(imageView);
        if (previous != null && !previous.cancelled && previous.key.equals(key)) {
            if (imageView.isAttachedToWindow()) subscribe(previous);
            return;
        }
        cancel(imageView);
        if (url.isEmpty()) {
            placeholder(imageView);
            return;
        }
        Binding binding = new Binding(imageView, key, token);
        bindings.put(imageView, binding);
        imageView.setTag(BINDING_TAG, binding);
        imageView.addOnAttachStateChangeListener(binding);
        Bitmap cached = cached(key);
        if (cached != null) {
            binding.complete = true;
            binding.authToken = null;
            present(imageView, cached, false);
        } else {
            placeholder(imageView);
            // List holders are commonly bound before insertion; do not fetch until attached.
            if (imageView.isAttachedToWindow()) subscribe(binding);
        }
    }

    /** Permanently unbind a recycled target. Detach alone is reversible on reattachment. */
    void cancel(ImageView imageView) {
        Binding binding = binding(imageView);
        if (binding != null) release(binding);
        imageView.animate().cancel();
        imageView.setAlpha(1f);
    }

    private void subscribe(Binding binding) {
        ImageView view = binding.target.get();
        if (closed || binding.cancelled || binding.complete || view == null
                || !view.isAttachedToWindow() || binding(view) != binding) return;
        binding.attached = true;
        Bitmap cached = cached(binding.key);
        if (cached != null) {
            binding.complete = true;
            binding.authToken = null;
            present(view, cached, false);
            return;
        }
        Request request;
        boolean start = false;
        synchronized (lock) {
            if (binding.request != null) return;
            request = inFlight.get(binding.key);
            if (request == null) {
                request = new Request(binding.key, binding.authToken, generation);
                inFlight.put(binding.key, request);
                start = true;
            }
            request.subscribers.add(binding);
            binding.request = request;
        }
        if (start) startDownload(request);
    }

    private void startDownload(Request request) {
        FutureTask<Void> task = new FutureTask<>(() -> {
            try {
                if (!active(request)) return null;
                byte[] bytes = downloader.download(request.key.url(), request.authToken,
                        request.cancellation);
                request.cancellation.throwIfCancelled();
                if (!active(request)) return null;
                FutureTask<Void> decode = new FutureTask<>(() -> {
                    if (!active(request)) return null;
                    try {
                        finish(request, decoder.decode(bytes, request.key.size()));
                    } catch (Exception ignored) {
                        finish(request, null);
                    }
                    return null;
                });
                synchronized (lock) {
                    if (!active(request)) return null;
                    request.decodeTask = decode;
                }
                decodeExecutor.execute(decode);
            } catch (Exception ignored) {
                finish(request, null);
            } finally {
                request.authToken = null;
            }
            return null;
        });
        synchronized (lock) {
            if (!active(request)) return;
            request.downloadTask = task;
        }
        try {
            downloadExecutor.execute(task);
        } catch (RejectedExecutionException rejected) {
            finish(request, null);
        }
    }

    private boolean active(Request request) {
        synchronized (lock) {
            if (closed || request.generation != generation || request.cancellation.isCancelled()
                    || inFlight.get(request.key) != request) return false;
            for (Binding subscriber : request.subscribers) {
                if (!subscriber.cancelled && subscriber.attached && subscriber.target.get() != null) {
                    return true;
                }
            }
            inFlight.remove(request.key);
            stop(request);
            return false;
        }
    }

    private void finish(Request request, Bitmap bitmap) {
        List<Binding> subscribers;
        synchronized (lock) {
            if (!active(request)) return;
            inFlight.remove(request.key);
            if (bitmap != null && !bitmap.isRecycled()) putCached(request.key, bitmap);
            subscribers = List.copyOf(request.subscribers);
            request.subscribers.clear();
            request.authToken = null;
        }
        main.post(() -> {
            for (Binding subscriber : subscribers) {
                // A detach/rebind may have restarted this binding after decoding finished.
                if (subscriber.request != request) continue;
                subscriber.request = null;
                ImageView view = subscriber.target.get();
                if (closed || request.generation != generation || subscriber.cancelled
                        || subscriber.complete || view == null || !view.isAttachedToWindow()
                        || binding(view) != subscriber) {
                    continue;
                }
                if (bitmap != null && !bitmap.isRecycled()) {
                    subscriber.complete = true;
                    subscriber.authToken = null;
                    present(view, bitmap, true);
                } else {
                    view.setImageDrawable(Ui.stroke(Ui.BG, Ui.dp(view.getContext(), 14),
                            0x22A0A0A0, Ui.dp(view.getContext(), 1)));
                }
            }
        });
    }

    private void unsubscribe(Binding binding) {
        binding.attached = false;
        synchronized (lock) {
            Request request = binding.request;
            binding.request = null;
            if (request == null) return;
            request.subscribers.remove(binding);
            if (request.subscribers.isEmpty() && inFlight.get(request.key) == request) {
                inFlight.remove(request.key);
                stop(request);
            }
        }
    }

    private void release(Binding binding) {
        binding.cancelled = true;
        binding.authToken = null;
        unsubscribe(binding);
        ImageView view = binding.target.get();
        if (view != null) {
            view.removeOnAttachStateChangeListener(binding);
            if (binding(view) == binding) view.setTag(BINDING_TAG, null);
            bindings.remove(view);
            view.animate().cancel();
            view.setAlpha(1f);
        }
        binding.target.clear();
    }

    private void stop(Request request) {
        request.authToken = null;
        request.cancellation.cancel();
        cancelTask(downloadExecutor, request.downloadTask);
        cancelTask(decodeExecutor, request.decodeTask);
        request.subscribers.clear();
    }

    private static void cancelTask(ExecutorService executor, FutureTask<Void> task) {
        if (task == null) return;
        task.cancel(true);
        if (executor instanceof ThreadPoolExecutor pool) pool.remove(task);
    }

    private Binding binding(ImageView view) {
        Object value = view.getTag(BINDING_TAG);
        return value instanceof Binding binding && binding.owner() == this ? binding : null;
    }

    private Bitmap cached(RequestKey key) {
        synchronized (lock) {
            Bitmap bitmap = cache.get(key);
            if (bitmap != null && bitmap.isRecycled()) {
                cache.remove(key);
                recomputeCacheBytes();
                return null;
            }
            return bitmap;
        }
    }

    private void putCached(RequestKey key, Bitmap bitmap) {
        long bytes = bitmap.getAllocationByteCount();
        if (bytes > maxCacheBytes) return;
        Bitmap previous = cache.put(key, bitmap);
        if (previous != null) cacheBytes -= previous.getAllocationByteCount();
        cacheBytes += bytes;
        Iterator<Map.Entry<RequestKey, Bitmap>> iterator = cache.entrySet().iterator();
        while (cacheBytes > maxCacheBytes && iterator.hasNext()) {
            cacheBytes -= iterator.next().getValue().getAllocationByteCount();
            iterator.remove();
        }
        // Evicted bitmaps may still be displayed by another target: never recycle here.
    }

    private void recomputeCacheBytes() {
        cacheBytes = 0;
        for (Bitmap bitmap : cache.values()) {
            if (!bitmap.isRecycled()) cacheBytes += bitmap.getAllocationByteCount();
        }
    }

    private static void present(ImageView view, Bitmap bitmap, boolean animate) {
        view.animate().cancel();
        view.setAlpha(1f);
        view.setImageBitmap(bitmap);
        if (animate && MotionPolicy.systemAnimatorsEnabled(view.getContext())) {
            view.setAlpha(0f);
            view.animate().alpha(1f).setDuration(FADE_DURATION_MS).start();
        }
    }

    private static void placeholder(ImageView view) {
        view.setImageDrawable(Ui.round(Ui.BG, Ui.dp(view.getContext(), 14)));
    }

    /** Also severs view bindings, so logout cannot restart an old authenticated request. */
    void clear() {
        synchronized (lock) {
            generation++;
            cache.clear();
            cacheBytes = 0;
            for (Request request : inFlight.values()) stop(request);
            inFlight.clear();
        }
        for (Binding binding : new ArrayList<>(bindings.values())) {
            ImageView view = binding.target.get();
            release(binding);
            if (view != null) placeholder(view);
        }
        bindings.clear();
    }

    void shutdown() {
        closed = true;
        clear();
        main.removeCallbacksAndMessages(null);
        downloadExecutor.shutdownNow();
        decodeExecutor.shutdownNow();
    }

    CacheSnapshot cacheSnapshot() {
        synchronized (lock) {
            return new CacheSnapshot(cache.size(), cacheBytes, maxCacheBytes, inFlight.size());
        }
    }

    PoolSnapshot poolSnapshot() {
        return new PoolSnapshot(snapshot(downloadExecutor), snapshot(decodeExecutor));
    }

    private static WorkerSnapshot snapshot(ExecutorService executor) {
        if (!(executor instanceof ThreadPoolExecutor pool)) return new WorkerSnapshot(0, 0);
        return new WorkerSnapshot(pool.getCorePoolSize(),
                pool.getQueue().remainingCapacity() + pool.getQueue().size());
    }

    private static ExecutorService rejectWhenFull(ExecutorService executor) {
        // Never create retry threads or execute network work inline when the UI outruns the pool.
        if (executor instanceof ThreadPoolExecutor pool) {
            pool.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        }
        return executor;
    }

    private static byte[] download(String url, String authToken, Cancellation cancellation)
            throws Exception {
        cancellation.throwIfCancelled();
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        cancellation.onCancel(connection::disconnect);
        try {
            cancellation.throwIfCancelled();
            configureConnection(connection, authToken);
            try (InputStream input = connection.getInputStream()) {
                java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    cancellation.throwIfCancelled();
                    if ((long) output.size() + read > MAX_DOWNLOAD_BYTES) {
                        throw new java.io.IOException("Image is too large");
                    }
                    output.write(buffer, 0, read);
                }
                return output.toByteArray();
            }
        } finally {
            cancellation.onCancel(null);
            connection.disconnect();
        }
    }

    static void configureConnection(HttpURLConnection connection, String authToken) {
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(15000);
        connection.setRequestProperty("Accept", "image/*");
        boolean authenticated = authToken != null && !authToken.isEmpty()
                && sameOrigin(connection.getURL().toExternalForm(), ApiClient.BASE_URL);
        // Private media endpoints do not redirect. Fail closed instead of forwarding a credential.
        connection.setInstanceFollowRedirects(!authenticated);
        if (authenticated) connection.setRequestProperty("Authorization", "Bearer " + authToken);
    }

    private static boolean sameOrigin(String imageUrl, String apiUrl) {
        try {
            URL image = new URL(imageUrl);
            URL api = new URL(apiUrl);
            int imagePort = image.getPort() < 0 ? image.getDefaultPort() : image.getPort();
            int apiPort = api.getPort() < 0 ? api.getDefaultPort() : api.getPort();
            return image.getUserInfo() == null && image.getProtocol().equalsIgnoreCase(api.getProtocol())
                    && image.getHost().equalsIgnoreCase(api.getHost()) && imagePort == apiPort;
        } catch (java.net.MalformedURLException invalid) {
            return false;
        }
    }

    private static int requestedSize(ImageView view) {
        int requested = Math.max(view.getWidth(), view.getHeight());
        requested = Math.max(requested, Math.max(view.getMinimumWidth(), view.getMinimumHeight()));
        android.view.ViewGroup.LayoutParams params = view.getLayoutParams();
        if (params != null) requested = Math.max(requested, Math.max(params.width, params.height));
        return Math.max(requested, Ui.dp(view.getContext(), 64));
    }

    private static Bitmap decodeSized(byte[] bytes, int requested) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 1;
        while (Math.max(bounds.outWidth, bounds.outHeight) / (options.inSampleSize * 2) >= requested) {
            options.inSampleSize *= 2;
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
    }

    private static String normalize(String value) {
        if (value == null || value.trim().isEmpty()) return "";
        String trimmed = value.trim();
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed;
        return ApiClient.BASE_URL + (trimmed.startsWith("/") ? "" : "/") + trimmed;
    }

    private static String authScope(String token) {
        if (token == null) return "public";
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(hash.length * 2);
            for (byte value : hash) result.append(Character.forDigit((value & 255) >>> 4, 16))
                    .append(Character.forDigit(value & 15, 16));
            return result.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    @FunctionalInterface
    interface Downloader {
        byte[] download(String url, String authToken, Cancellation cancellation) throws Exception;
    }

    @FunctionalInterface
    interface Decoder {
        Bitmap decode(byte[] bytes, int requestedSize) throws Exception;
    }

    static final class Cancellation {
        private volatile boolean cancelled;
        private Runnable cancelAction;

        boolean isCancelled() { return cancelled; }

        void throwIfCancelled() throws InterruptedIOException {
            if (cancelled || Thread.currentThread().isInterrupted()) {
                throw new InterruptedIOException("Image request cancelled");
            }
        }

        synchronized void onCancel(Runnable action) {
            if (cancelled && action != null) action.run();
            else cancelAction = action;
        }

        synchronized void cancel() {
            cancelled = true;
            Runnable action = cancelAction;
            cancelAction = null;
            if (action != null) action.run();
        }
    }

    private final class Binding implements View.OnAttachStateChangeListener {
        final WeakReference<ImageView> target;
        final RequestKey key;
        String authToken;
        volatile boolean attached;
        volatile boolean cancelled;
        boolean complete;
        Request request;

        Binding(ImageView target, RequestKey key, String authToken) {
            this.target = new WeakReference<>(target);
            this.key = key;
            this.authToken = authToken;
        }

        ImageLoader owner() { return ImageLoader.this; }
        @Override public void onViewAttachedToWindow(View view) { subscribe(this); }

        @Override public void onViewDetachedFromWindow(View view) {
            unsubscribe(this);
            view.animate().cancel();
            view.setAlpha(1f);
        }
    }

    private static final class Request {
        final RequestKey key;
        final long generation;
        final Cancellation cancellation = new Cancellation();
        final List<Binding> subscribers = new ArrayList<>();
        volatile String authToken;
        FutureTask<Void> downloadTask;
        FutureTask<Void> decodeTask;

        Request(RequestKey key, String authToken, long generation) {
            this.key = key;
            this.authToken = authToken;
            this.generation = generation;
        }
    }

    private record RequestKey(String url, int size, String authScope) { }
    record CacheSnapshot(int entries, long bytes, long budget, int inFlight) { }
    record PoolSnapshot(WorkerSnapshot download, WorkerSnapshot decode) { }
    record WorkerSnapshot(int workers, int queueCapacity) { }
}
