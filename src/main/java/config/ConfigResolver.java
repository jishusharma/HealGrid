package config;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import utils.GenericUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class ConfigResolver {

    private static final Logger LOGGER = LogManager.getLogger(ConfigResolver.class);

    // Sources
    private static final Properties configFile = GenericUtil.getPropertiesFile("config.properties");
    private static final Map<String, String> suiteParams = new HashMap<>();
    private static final ThreadLocal<Map<String, String>> testParams =
            ThreadLocal.withInitial(HashMap::new);

    // ---- Population (called by BaseTest.setUpSuite) ----
    public static void setSuiteParameters(Map<String, String> params) {
        suiteParams.clear();
        suiteParams.putAll(params);
    }

    public static void setTestParameters(Map<String, String> params) {
        testParams.get().clear();
        testParams.get().putAll(params);
    }

    public static void clearTestParameters() {
        testParams.remove();
    }

    // ---- Resolution chain ----
    public static String get(String key, String defaultValue) {
        // 1. System property (-D) – explicit runtime override
        String value = System.getProperty(key);
        if (value != null) return value;

        // 2. TestNG XML test parameter
        value = testParams.get().get(key);
        if (value != null) return value;

        // 3. TestNG XML suite parameter – suite author's intent
        value = suiteParams.get(key);
        if (value != null) return value;

        // 4. Environment variable – infrastructure / secrets
        value = System.getenv(key);
        if (value != null) return value;

        // 5. config.properties – baseline
        value = configFile.getProperty(key);
        if (value != null) return value;

        // 6. Hardcoded default
        return defaultValue;
    }

    // Convenience boolean
    public static boolean getBoolean(String key, boolean defaultValue) {
        return Boolean.parseBoolean(get(key, String.valueOf(defaultValue)));
    }

    public static void logResolved(String key, String defaultValue) {
        String value = System.getProperty(key);
        if (value != null) {
            LOGGER.info("{} = {} (source: -D system property)", key, value);
            return;
        }
        value = testParams.get().get(key);
        if (value != null) {
            LOGGER.info("{} = {} (source: testng.xml test parameter)", key, value);
            return;
        }
        value = suiteParams.get(key);
        if (value != null) {
            LOGGER.info("{} = {} (source: testng.xml suite parameter)", key, value);
            return;
        }
        value = System.getenv(key);
        if (value != null) {
            LOGGER.info("{} = {} (source: environment variable)", key, value);
            return;
        }
        value = configFile.getProperty(key);
        if (value != null) {
            LOGGER.info("{} = {} (source: config.properties)", key, value);
            return;
        }
        LOGGER.info("{} = {} (source: default)", key, defaultValue);
    }
}
