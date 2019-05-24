package io.github.candyeer.k8s;

import io.fabric8.kubernetes.api.model.DoneablePod;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.ExecWatch;
import io.fabric8.kubernetes.client.dsl.PodResource;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.web.socket.*;
import org.springframework.web.socket.adapter.standard.StandardWebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import javax.websocket.Session;
import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PipedOutputStream;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author lican
 */
@Slf4j
public class CommandWebSocketHandler extends TextWebSocketHandler implements InitializingBean {

    private final static Map<WebSocketSession, WatchPayload> SESSION_MAP = new ConcurrentHashMap<>();

    private ScheduledExecutorService scheduledExecutorService = new ScheduledThreadPoolExecutor(2, new BasicThreadFactory.Builder().namingPattern("websocket-thread-%d").build());

    private static final String INIT_FLAG = "___init___";

    private static final String RESIZE_FLAG = "___resize___";

    private static final String SPLITTER = "___";

    private KubernetesClient kubernetesClient;


    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        writeCommand(session, message.getPayload().array());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        if (payload.startsWith(INIT_FLAG)) {
            String[] s = payload.split(SPLITTER);
            int cols = Integer.parseInt(s[2]);
            int rows = Integer.parseInt(s[3]);
            ExecWatch watch = getExecWatch(session, cols, rows);
            SESSION_MAP.put(session, new WatchPayload(watch, cols, rows));
        } else if (payload.startsWith(RESIZE_FLAG)) {
            String[] s = payload.split(SPLITTER);
            int cols = Integer.parseInt(s[2]);
            int rows = Integer.parseInt(s[3]);
            WatchPayload watchPayload = SESSION_MAP.get(session);
            watchPayload.getWatch().resize(cols, rows);
        } else {
            writeCommand(session, message.asBytes());
        }

    }

    private void writeCommand(WebSocketSession session, byte[] payload) {
        writeCommand(session, payload, 0);
    }

    private void writeCommand(WebSocketSession session, byte[] payload, int count) {
        log.debug("message received,sessionId:{},message:{}", session.getId(), payload);
        WatchPayload execWatch = SESSION_MAP.get(session);
        if (execWatch == null) {
            log.error("execWatch is null get from map ,sessionId:{}", session.getId());
            return;
        }
        OutputStream outputStream = execWatch.getWatch().getInput();
        if (Objects.isNull(payload)) {
            return;
        }
        try {
            outputStream.write(payload, 0, payload.length);
            outputStream.flush();
        } catch (IOException e) {
            log.error("write to outputStream error", e);
            if (count < 1) {
                //尝试重新建立连接;
                closeQuietly(execWatch.getWatch());
                ExecWatch ew = getExecWatch(session, execWatch.getCols(), execWatch.getRows());
                SESSION_MAP.put(session, new WatchPayload(ew, execWatch.getCols(), execWatch.getRows()));
                writeCommand(session, payload, ++count);
            }
        }
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        super.afterConnectionEstablished(session);
        log.info("session established,sessionId:{}", session.getId());
    }

    private ExecWatch getExecWatch(WebSocketSession session, Integer cols, Integer rows) {
        String namespace = getParameter(session, "namespace");
        String podName = getParameter(session, "podName");
        PodResource<Pod, DoneablePod> podResource = kubernetesClient.pods().inNamespace(namespace).withName(podName);
        //输入命令到容器中;

        PipedOutputStream outputStream = new PipedOutputStream();
        return podResource
                .writingInput(outputStream)
                .writingOutput(new OutputStream() {
                    @Override
                    public void write(int b) throws IOException {

                    }

                    @Override
                    public void write(byte[] b, int off, int len) throws IOException {
                        session.sendMessage(new BinaryMessage(b, off, len, true));
                    }
                })
                .writingError(new OutputStream() {
                    @Override
                    public void write(int b) throws IOException {

                    }

                    @Override
                    public void write(byte[] b, int off, int len) throws IOException {
                        session.sendMessage(new BinaryMessage(b, off, len, true));
                    }
                })
                .withTTY()
                .exec("env", "TERM%3Dxterm", "COLUMNS%3D" + cols, "LINES%3D" + rows, "/bin/bash", "-i");
    }

    @Override
    protected void handlePongMessage(WebSocketSession session, PongMessage message) throws Exception {
        super.handlePongMessage(session, message);
        log.info("received pong message,{}", session.getId());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("websocket transport error:{}", session.getId(), exception);
        super.handleTransportError(session, exception);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        log.info("session closing:,sessionId:{}", session.getId());
        super.afterConnectionClosed(session, status);
        WatchPayload execWatch = SESSION_MAP.remove(session);
        if (execWatch != null) {
            closeQuietly(execWatch.getWatch());
        }
        closeQuietly(session);
    }

    private static String getParameter(WebSocketSession session, String key) {
        Session nativeSession = ((StandardWebSocketSession) session).getNativeSession();
        Map<String, List<String>> requestParameterMap = nativeSession.getRequestParameterMap();
        List<String> strings = requestParameterMap.get(key);
        if (strings == null || strings.size() == 0) {
            return null;
        }
        return strings.get(0);
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        scheduledExecutorService.scheduleAtFixedRate(() -> {
            try {
                Iterator<Map.Entry<WebSocketSession, WatchPayload>> iterator = SESSION_MAP.entrySet().iterator();
                while (iterator.hasNext()) {
                    Map.Entry<WebSocketSession, WatchPayload> next = iterator.next();
                    WebSocketSession session = next.getKey();
                    WatchPayload watch = next.getValue();
                    boolean open = session.isOpen();
                    log.info("detect session:{} ,status:{}", session.getId(), open);
                    if (!open) {
                        closeQuietly(watch.getWatch());
                        closeQuietly(session);
                        iterator.remove();
                    }
                }
            } catch (Exception e) {
                log.error("monitor websocket session error", e);
            }
        }, 1, 1, TimeUnit.MINUTES);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            scheduledExecutorService.shutdownNow();
            log.info("websocket threadPool is closing");
        }));

        initKubernetesClient();

    }

    private void initKubernetesClient() {
        Config config = new ConfigBuilder().withMasterUrl("https://192.168.99.100:8443").build();
        config.setMaxConcurrentRequests(10);
        config.setMaxConcurrentRequestsPerHost(10);
        config.setWebsocketTimeout(10000);
        config.setWebsocketPingInterval(5000);
        this.kubernetesClient = new DefaultKubernetesClient(config);
    }

    @AllArgsConstructor
    @Data
    private static class WatchPayload {
        private ExecWatch watch;
        private Integer cols;
        private Integer rows;
    }

    public static void closeQuietly(final Closeable closeable) {
        try {
            if (closeable != null) {
                closeable.close();
            }
        } catch (final IOException ioe) {
            log.error("close closeable error", ioe);
        }
    }
}
