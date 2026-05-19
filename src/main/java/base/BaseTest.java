package base;

import config.ConfigResolver;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.WebDriver;
import org.testng.ITestContext;
import org.testng.annotations.*;

import java.net.HttpURLConnection;
import java.net.URL;
import java.lang.reflect.Method;

public class BaseTest {
    protected static final String CONFIG_PROP = "config.properties";
    protected static final Logger LOGGER = LogManager.getLogger(BaseTest.class);

    // Static flag to block @BeforeMethod when environment is fatally broken
    private static boolean fatalEnvironmentError = false;
    private static String fatalErrorMessage = null;

    @BeforeSuite(alwaysRun = true)
    public void setUpSuite(ITestContext context) {
        // Feed suite parameters to the shared ConfigResolver
        ConfigResolver.setSuiteParameters(context.getSuite().getXmlSuite().getParameters());

        // Log suite identity and invocation command
        LOGGER.info("TestNG suite: {}", context.getSuite().getName());
        String cmd = System.getProperty("sun.java.command", "IDE or unknown");
        LOGGER.info("Command: {}", cmd);

        // Resolve execution mode and heal flag through the shared chain
        String execution = ConfigResolver.get("execution", "local");
        boolean healEnabled = ConfigResolver.getBoolean("heal.enabled", true);
        ConfigResolver.logResolved("execution", "local");
        ConfigResolver.logResolved("heal.enabled", "true");
        ConfigResolver.logResolved("grid.url", "http://localhost:4444");

        LOGGER.info("Healenium check: execution={}, heal.enabled={}", execution, healEnabled);

        if (!"browserstack".equalsIgnoreCase(execution) && healEnabled) {
            String healeniumHost = ConfigResolver.get("healenium.host", "localhost");
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
    public void setUp(@Optional("chrome") String browser, Method method, ITestContext context) {
        if (fatalEnvironmentError) {
            throw new RuntimeException("Test execution blocked: " + fatalErrorMessage);
        }

        ConfigResolver.setTestParameters(context.getCurrentXmlTest().getAllParameters());
        WebDriver driver = DriverFactory.createDriver(method.getName(), browser);
        DriverManager.setDriver(driver);
        LOGGER.info("Setting up: {} | browser: {} | thread: {}",
                method.getName(), browser, Thread.currentThread().getId());
        boolean isHeadless = ConfigResolver.getBoolean("headless", false);
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
        ConfigResolver.clearTestParameters();
    }
}
