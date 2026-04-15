package util;

import jakarta.persistence.Entity;
import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public final class HibernateSessionFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(HibernateSessionFactory.class);
    private static final String ENTITY_PACKAGE = "entities";
    private static SessionFactory sessionFactory;
    private static final ThreadLocal<Session> CURRENT_SESSION = new ThreadLocal<>();

    private HibernateSessionFactory() {
    }

    public static synchronized SessionFactory getInstance() {
        if (sessionFactory == null || sessionFactory.isClosed()) {
            sessionFactory = buildSessionFactory();
        }
        return sessionFactory;
    }

    public static Session getSession() {
        Session session = CURRENT_SESSION.get();

        if (session == null || !session.isOpen()) {
            try {
                session = getInstance().openSession();
                CURRENT_SESSION.set(session);
            } catch (HibernateException exception) {
                LOGGER.error("Unable to open Hibernate session", exception);
                throw new IllegalStateException("Database connection failed while opening Hibernate session", exception);
            }
        }

        return session;
    }

    public static void closeSession() {
        Session session = CURRENT_SESSION.get();
        if (session != null) {
            try {
                if (session.isOpen()) {
                    session.close();
                }
            } catch (HibernateException exception) {
                LOGGER.warn("Error while closing Hibernate session", exception);
            } finally {
                CURRENT_SESSION.remove();
            }
        }
    }

    public static SessionFactory getSessionFactory() {
        return getInstance();
    }

    public static void shutdown() {
        closeSession();
        if (sessionFactory != null && !sessionFactory.isClosed()) {
            sessionFactory.close();
        }
    }

    private static SessionFactory buildSessionFactory() {
        try {
            Properties dbProperties = PropertiesLoader.load("confg/database.properties");
            Configuration configuration = new Configuration();

            configuration.setProperty("hibernate.connection.driver_class", dbProperties.getProperty("db.driver"));
            configuration.setProperty("hibernate.connection.url", dbProperties.getProperty("db.url"));
            configuration.setProperty("hibernate.connection.username", dbProperties.getProperty("db.user"));
            configuration.setProperty("hibernate.connection.password", dbProperties.getProperty("db.password"));
            configuration.setProperty("hibernate.dialect", dbProperties.getProperty("hibernate.dialect", "org.hibernate.dialect.MySQLDialect"));
            configuration.setProperty("hibernate.show_sql", dbProperties.getProperty("hibernate.show_sql", "false"));
            configuration.setProperty("hibernate.format_sql", dbProperties.getProperty("hibernate.format_sql", "true"));
            configuration.setProperty("hibernate.hbm2ddl.auto", dbProperties.getProperty("hibernate.hbm2ddl.auto", "validate"));

            configuration.setProperty("hibernate.connection.provider_class", "org.hibernate.hikaricp.internal.HikariCPConnectionProvider");
            configuration.setProperty("hibernate.hikari.maximumPoolSize", dbProperties.getProperty("hikari.maximumPoolSize", "10"));
            configuration.setProperty("hibernate.hikari.minimumIdle", dbProperties.getProperty("hikari.minimumIdle", "2"));
            configuration.setProperty("hibernate.hikari.idleTimeout", dbProperties.getProperty("hikari.idleTimeoutMs", "300000"));
            configuration.setProperty("hibernate.hikari.connectionTimeout", dbProperties.getProperty("hikari.connectionTimeoutMs", "20000"));

            registerEntities(configuration, ENTITY_PACKAGE);

            StandardServiceRegistryBuilder registryBuilder = new StandardServiceRegistryBuilder()
                    .applySettings(configuration.getProperties());

            return configuration.buildSessionFactory(registryBuilder.build());
        } catch (Exception exception) {
            LOGGER.error("Unable to initialize Hibernate SessionFactory", exception);
            throw new IllegalStateException("Database connection failed while initializing Hibernate SessionFactory", exception);
        }
    }

    private static void registerEntities(Configuration configuration, String packageName) throws IOException, ClassNotFoundException {
        Set<String> classNames = findClassNames(packageName);
        int registered = 0;

        for (String className : classNames) {
            Class<?> candidate = Class.forName(className);
            if (candidate.isAnnotationPresent(Entity.class)) {
                configuration.addAnnotatedClass(candidate);
                registered++;
            }
        }

        if (registered == 0) {
            throw new IllegalStateException("No @Entity classes found in package: " + packageName);
        }

        LOGGER.info("Registered {} entity classes for Hibernate", registered);
    }

    private static Set<String> findClassNames(String packageName) throws IOException {
        String packagePath = packageName.replace('.', '/');
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        Enumeration<URL> resources = classLoader.getResources(packagePath);

        Set<String> classNames = new HashSet<>();
        while (resources.hasMoreElements()) {
            URL url = resources.nextElement();
            String protocol = url.getProtocol();

            if ("file".equals(protocol)) {
                String decodedPath = URLDecoder.decode(url.getPath(), StandardCharsets.UTF_8);
                collectFromDirectory(new File(decodedPath), packageName, classNames);
            } else if ("jar".equals(protocol)) {
                JarURLConnection connection = (JarURLConnection) url.openConnection();
                collectFromJar(connection.getJarFile(), packagePath, classNames);
            }
        }

        return classNames;
    }

    private static void collectFromDirectory(File directory, String packageName, Set<String> classNames) {
        File[] files = directory.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                collectFromDirectory(file, packageName + "." + file.getName(), classNames);
            } else if (file.getName().endsWith(".class") && !file.getName().contains("$")) {
                String simpleName = file.getName().substring(0, file.getName().length() - 6);
                classNames.add(packageName + "." + simpleName);
            }
        }
    }

    private static void collectFromJar(JarFile jarFile, String packagePath, Set<String> classNames) {
        Enumeration<JarEntry> entries = jarFile.entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            String name = entry.getName();

            if (name.startsWith(packagePath) && name.endsWith(".class") && !name.contains("$")) {
                classNames.add(name.substring(0, name.length() - 6).replace('/', '.'));
            }
        }
    }
}
