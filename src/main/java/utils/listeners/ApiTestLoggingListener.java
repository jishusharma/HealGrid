package utils.listeners;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.testng.ITestContext;
import org.testng.ITestListener;
import org.testng.ITestResult;

/**
 * Logs API test lifecycle events and a final summary.
 * Safe for API‑only suites — no WebDriver, no screenshots.
 */
public class ApiTestLoggingListener implements ITestListener {

    private static final Logger LOGGER = LogManager.getLogger(ApiTestLoggingListener.class);

    @Override
    public void onTestStart(ITestResult result) {
        LOGGER.info("*** Running test method {} ***", result.getMethod().getMethodName());
    }

    @Override
    public void onTestSuccess(ITestResult result) {
        LOGGER.info("*** Passed: {} ***", result.getMethod().getMethodName());
    }

    @Override
    public void onTestFailure(ITestResult result) {
        LOGGER.info("*** Failed: {} ***", result.getMethod().getMethodName());
    }

    @Override
    public void onTestSkipped(ITestResult result) {
        LOGGER.info("*** Skipped: {} ***", result.getMethod().getMethodName());
    }

    @Override
    public void onFinish(ITestContext context) {
        int passed = context.getPassedTests().size();
        int failed = context.getFailedTests().size();
        int skipped = context.getSkippedTests().size();
        LOGGER.info("===============================================");
        LOGGER.info("API Suite Results: Passed={}, Failed={}, Skipped={}", passed, failed, skipped);
        LOGGER.info("===============================================");
    }
}