package util;

import javafx.application.Platform;
import me.friwi.jcefmaven.CefAppBuilder;
import me.friwi.jcefmaven.EnumProgress;
import org.cef.CefApp;
import org.cef.CefClient;
import org.cef.browser.CefBrowser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

public final class JcefMeetingWindow {

    private static final Logger LOG = LoggerFactory.getLogger(JcefMeetingWindow.class);
    private static CefApp cefApp;

    private JcefMeetingWindow() {
    }

    public static void openAsync(String url, String title, Consumer<Throwable> failureHandler) {
        openAsync(url, title, null, failureHandler);
    }

    public static void openAsync(String url,
                                 String title,
                                 Consumer<String> progressHandler,
                                 Consumer<Throwable> failureHandler) {
        Thread launcher = new Thread(() -> {
            try {
                publishProgress(progressHandler, "Preparing embedded Chromium...");
                CefApp app = getOrCreateCefApp(progressHandler);
                publishProgress(progressHandler, "Opening embedded Chromium window...");
                SwingUtilities.invokeLater(() -> showWindow(app, url, title, progressHandler));
            } catch (Throwable throwable) {
                LOG.warn("Unable to open JCEF meeting window", throwable);
                if (failureHandler != null) {
                    Platform.runLater(() -> failureHandler.accept(throwable));
                }
            }
        }, "jcef-meeting-launcher");
        launcher.setDaemon(true);
        launcher.start();
    }

    public static void embedAsync(javafx.embed.swing.SwingNode target,
                                  String url,
                                  Consumer<String> progressHandler,
                                  Consumer<EmbeddedBrowser> readyHandler,
                                  Consumer<Throwable> failureHandler) {
        Thread launcher = new Thread(() -> {
            try {
                publishProgress(progressHandler, "Preparing embedded Chromium...");
                CefApp app = getOrCreateCefApp(progressHandler);
                publishProgress(progressHandler, "Embedding Chromium in UniLearn...");
                SwingUtilities.invokeLater(() -> embedBrowser(app, target, url, progressHandler, readyHandler));
            } catch (Throwable throwable) {
                LOG.warn("Unable to embed JCEF meeting browser", throwable);
                if (failureHandler != null) {
                    Platform.runLater(() -> failureHandler.accept(throwable));
                }
            }
        }, "jcef-meeting-embedder");
        launcher.setDaemon(true);
        launcher.start();
    }

    private static synchronized CefApp getOrCreateCefApp(Consumer<String> progressHandler) throws Exception {
        if (cefApp != null) {
            publishProgress(progressHandler, "Embedded Chromium is already initialized.");
            return cefApp;
        }

        CefAppBuilder builder = new CefAppBuilder();
        builder.setInstallDir(jcefInstallDir());
        builder.setMirrors(List.of(
                "https://repo.maven.apache.org/maven2/me/friwi/jcef-natives-{platform}/{tag}/jcef-natives-{platform}-{tag}.jar",
                "https://github.com/jcefmaven/jcefmaven/releases/download/{mvn_version}/jcef-natives-{platform}-{tag}.jar"
        ));
        builder.setProgressHandler((state, progress) -> publishProgress(progressHandler, progressText(state, progress)));
        builder.getCefSettings().windowless_rendering_enabled = isWindowlessRenderingEnabled();
        builder.addJcefArgs("--enable-media-stream");
        builder.addJcefArgs("--autoplay-policy=no-user-gesture-required");
        if (configBoolean("JCEF_IGNORE_CERT_ERRORS", false)) {
            builder.addJcefArgs("--ignore-certificate-errors");
        }
        cefApp = builder.build();
        return cefApp;
    }

    private static File jcefInstallDir() {
        String configuredDir = ConfigurationProvider.getProperty("JCEF_INSTALL_DIR", "");
        if (configuredDir != null && !configuredDir.isBlank()) {
            return Path.of(configuredDir.trim()).toFile();
        }
        return Path.of(System.getProperty("user.home"), ".unilearn", "jcef").toFile();
    }

    private static boolean configBoolean(String key, boolean defaultValue) {
        String configuredValue = ConfigurationProvider.getProperty(key, Boolean.toString(defaultValue));
        if (configuredValue == null || configuredValue.isBlank()) {
            return defaultValue;
        }
        return "true".equalsIgnoreCase(configuredValue.trim())
                || "1".equals(configuredValue.trim())
                || "yes".equalsIgnoreCase(configuredValue.trim())
                || "on".equalsIgnoreCase(configuredValue.trim());
    }

    private static boolean isWindowlessRenderingEnabled() {
        String clientMode = ConfigurationProvider.getProperty("JITSI_CLIENT_MODE", "");
        boolean embeddedJcef = clientMode != null
                && ("jcef_embed".equalsIgnoreCase(clientMode.trim())
                || "jcef-embed".equalsIgnoreCase(clientMode.trim()));
        return configBoolean("JCEF_WINDOWLESS_RENDERING_ENABLED", embeddedJcef)
                || configBoolean("JITSI_JCEF_EMBEDDED", false);
    }

    private static String progressText(EnumProgress state, float progress) {
        if (state == null) {
            return "Preparing embedded Chromium...";
        }

        String label = switch (state) {
            case LOCATING -> "Locating embedded Chromium package";
            case DOWNLOADING -> "Downloading embedded Chromium";
            case EXTRACTING -> "Extracting embedded Chromium";
            case INSTALL -> "Installing embedded Chromium";
            case INITIALIZING -> "Initializing embedded Chromium";
            case INITIALIZED -> "Embedded Chromium initialized";
        };

        if (progress == EnumProgress.NO_ESTIMATION || progress < 0) {
            return label + "...";
        }
        return label + " " + Math.round(progress) + "%...";
    }

    private static void publishProgress(Consumer<String> progressHandler, String message) {
        if (progressHandler != null) {
            Platform.runLater(() -> progressHandler.accept(message));
        }
        LOG.info("JCEF meeting: {}", message);
    }

    private static void showWindow(CefApp app, String url, String title, Consumer<String> progressHandler) {
        CefClient client = app.createClient();
        CefBrowser browser = client.createBrowser(url, false, false);
        Component browserUi = browser.getUIComponent();

        JFrame frame = new JFrame(title == null || title.isBlank() ? "UniLearn Meeting" : title);
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setLayout(new BorderLayout());
        frame.add(browserUi, BorderLayout.CENTER);
        frame.setSize(1280, 820);
        frame.setLocationRelativeTo(null);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                browser.close(true);
                client.dispose();
            }
        });
        frame.setVisible(true);
        publishProgress(progressHandler, "Embedded Chromium window is open.");
    }

    private static void embedBrowser(CefApp app,
                                     javafx.embed.swing.SwingNode target,
                                     String url,
                                     Consumer<String> progressHandler,
                                     Consumer<EmbeddedBrowser> readyHandler) {
        CefClient client = app.createClient();
        CefBrowser browser = client.createBrowser(url, true, false);
        Component browserUi = browser.getUIComponent();

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(browserUi, BorderLayout.CENTER);
        target.setContent(panel);

        EmbeddedBrowser handle = new EmbeddedBrowser(client, browser);
        publishProgress(progressHandler, "Embedded Chromium is ready.");
        if (readyHandler != null) {
            Platform.runLater(() -> readyHandler.accept(handle));
        }
    }

    public static final class EmbeddedBrowser {
        private final CefClient client;
        private final CefBrowser browser;
        private boolean closed;

        private EmbeddedBrowser(CefClient client, CefBrowser browser) {
            this.client = client;
            this.browser = browser;
        }

        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            SwingUtilities.invokeLater(() -> {
                browser.close(true);
                client.dispose();
            });
        }
    }
}
