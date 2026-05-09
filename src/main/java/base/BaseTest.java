package base;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.WebDriver;
import org.testng.annotations.*;
import utils.GenericUtil;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Properties;
import java.lang.reflect.Method;

public class BaseTest {
    protected static Properties config;
    protected static final String CONFIG_PROP = "config.properties";
    protected static final Logger LOGGER = LogManager.getLogger(BaseTest.class);

    // Static flag to block @BeforeMethod when environment is fatally broken
    private static boolean fatalEnvironmentError = false;
    private static String fatalErrorMessage = null;

    @BeforeSuite(alwaysRun = true)
    public void setUpSuite() {
        config = GenericUtil.getPropertiesFile(CONFIG_PROP);

        // Use the same property resolution as DriverFactory (system property → env var → config file → default)
        String execution = System.getProperty("execution", config.getProperty("execution", "local"));
        boolean healEnabled = Boolean.parseBoolean(
                System.getProperty("heal.enabled", config.getProperty("heal.enabled", "true")));

        LOGGER.info("Healenium check: execution={}, heal.enabled={}", execution, healEnabled);

        if (!"browserstack".equalsIgnoreCase(execution) && healEnabled) {
            String healeniumHost = System.getProperty("healenium.host", "localhost");
            try {
                URL url = new URL("http://" + healeniumHost + ":7878/healenium/report");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(2000);
                conn.setReadTimeout(2000);
                conn.connect();
                if (conn.getResponseCode() != 200) {
                    throw new RuntimeException("Healenium backend returned HTTP " + conn.getResponseCode());
                }
                LOGGER.info("Healenium backend is reachable.");
            } catch (RuntimeException e) {
                // Re-throw RuntimeExceptions directly (they include our own messages)
                fatalEnvironmentError = true;
                fatalErrorMessage = e.getMessage();
                throw e;
            } catch (Exception e) {
                fatalEnvironmentError = true;
                fatalErrorMessage = "Healenium is not reachable. Please start services: " +
                        "docker-compose up -d postgres-db healenium selector-imitator";
                throw new RuntimeException(fatalErrorMessage, e);
            }
        } else if (!healEnabled) {
            LOGGER.warn("Healenium readiness check SKIPPED because heal.enabled=false. " +
                    "Tests will run without self-healing.");
        }
    }

    @Parameters({"browser"})
    @BeforeMethod(alwaysRun = true)
    public void setUp(@Optional("chrome") String browser, Method method) {
        if (fatalEnvironmentError) {
            throw new RuntimeException("Test execution blocked: " + fatalErrorMessage);
        }

        WebDriver driver = DriverFactory.createDriver(method.getName(), browser);
        DriverManager.setDriver(driver);
        LOGGER.info("Setting up: {} | browser: {} | thread: {}",
                method.getName(), browser, Thread.currentThread().getId());
        boolean isHeadless = Boolean.parseBoolean(System.getProperty("headless", "false"));
        boolean isMobile = browser != null && (browser.equalsIgnoreCase("mobile_android")
                || browser.equalsIgnoreCase("mobile_ios"));
        if (!isHeadless && !isMobile) {
            driver.manage().window().maximize();
        }
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown(Method method) {
        LOGGER.info("Tearing down: {} | thread: {}",
                method.getName(), Thread.currentThread().getId());
        DriverManager.quitDriver();
        BrowserStackSessionContext.clear();
    }
}