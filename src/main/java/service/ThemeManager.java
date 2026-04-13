package service;

import javafx.collections.ObservableList;
import javafx.scene.Parent;
import javafx.scene.Scene;

import java.util.prefs.Preferences;

public final class ThemeManager {

    public enum Theme {
        LIGHT("theme-light"),
        DARK("theme-dark");

        private final String cssClass;

        Theme(String cssClass) {
            this.cssClass = cssClass;
        }

        public String cssClass() {
            return cssClass;
        }
    }

    private static final String PREF_NODE = "unilearn.desktop.theme";
    private static final String PREF_THEME_KEY = "activeTheme";

    private static final ThemeManager INSTANCE = new ThemeManager();

    private final Preferences preferences;

    private ThemeManager() {
        this.preferences = Preferences.userRoot().node(PREF_NODE);
    }

    public static ThemeManager getInstance() {
        return INSTANCE;
    }

    public void applySavedTheme(Scene scene) {
        applyTheme(scene, getSavedTheme());
    }

    public Theme getSavedTheme() {
        String persistedTheme = preferences.get(PREF_THEME_KEY, Theme.LIGHT.name());
        try {
            return Theme.valueOf(persistedTheme);
        } catch (IllegalArgumentException exception) {
            return Theme.LIGHT;
        }
    }

    public Theme getActiveTheme(Scene scene) {
        Parent root = scene == null ? null : scene.getRoot();
        if (root == null) {
            return getSavedTheme();
        }

        ObservableList<String> classes = root.getStyleClass();
        if (classes.contains(Theme.DARK.cssClass())) {
            return Theme.DARK;
        }

        if (classes.contains(Theme.LIGHT.cssClass())) {
            return Theme.LIGHT;
        }

        return getSavedTheme();
    }

    public Theme toggleTheme(Scene scene) {
        Theme current = getActiveTheme(scene);
        Theme next = current == Theme.DARK ? Theme.LIGHT : Theme.DARK;
        applyTheme(scene, next);
        return next;
    }

    public void applyTheme(Scene scene, Theme theme) {
        Parent root = scene == null ? null : scene.getRoot();
        if (root == null) {
            return;
        }

        ObservableList<String> classes = root.getStyleClass();
        classes.remove(Theme.LIGHT.cssClass());
        classes.remove(Theme.DARK.cssClass());
        classes.add(theme.cssClass());

        preferences.put(PREF_THEME_KEY, theme.name());
    }
}
