package tests;

import base.BaseTest;
import base.DriverManager;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.time.Duration;

public class HealeniumHealingProofTest extends BaseTest {
    private static final String WEB_TABLES_URL = "https://demoqa.com/webtables";
    private static final By ADD_RECORD_BUTTON = By.id("addNewRecordButton");
    private static final String MUTATED_ID = "healenium-proof-add-button";

    @Test(priority = 1, description = "Baseline lookup stores the original locator for Healenium")
    public void baselineLocatorShouldFindAddRecordButton() {
        WebDriver driver = DriverManager.getDriver();
        openWebTables(driver);

        WebElement button = waitForAddRecordButton(driver);

        Assert.assertTrue(button.isDisplayed(), "Baseline Add button should be visible before DOM drift");
    }

    @Test(priority = 2, description = "Healenium should recover the Add button after its id changes")
    public void healedLocatorShouldRecoverAfterDomIdDrift() {
        WebDriver driver = DriverManager.getDriver();
        openWebTables(driver);

        WebElement originalButton = waitForAddRecordButton(driver);
        ((JavascriptExecutor) driver).executeScript(
                "arguments[0].setAttribute('id', arguments[1]);",
                originalButton,
                MUTATED_ID
        );

        Object originalLocatorMissing = ((JavascriptExecutor) driver).executeScript(
                "return document.getElementById('addNewRecordButton') === null;"
        );
        Assert.assertEquals(originalLocatorMissing, Boolean.TRUE,
                "Proof setup failed: original id should be missing after DOM mutation");

        WebElement healedButton = driver.findElement(ADD_RECORD_BUTTON);

        Assert.assertTrue(healedButton.isDisplayed(), "Healed Add button should be visible");
        Assert.assertEquals(healedButton.getAttribute("id"), MUTATED_ID,
                "Healenium should return the DOM-mutated Add button");
    }

    private void openWebTables(WebDriver driver) {
        driver.get(WEB_TABLES_URL);
    }

    private WebElement waitForAddRecordButton(WebDriver driver) {
        return new WebDriverWait(driver, Duration.ofSeconds(20))
                .until(ExpectedConditions.visibilityOfElementLocated(ADD_RECORD_BUTTON));
    }
}
