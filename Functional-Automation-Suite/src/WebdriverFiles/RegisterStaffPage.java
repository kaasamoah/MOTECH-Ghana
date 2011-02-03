package WebdriverFiles;

import java.util.List;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;


public class RegisterStaffPage {
	
	 private WebDriver driver;

     public RegisterStaffPage(){
            driver = WebDriverBaseClass.getInstance();
     }

	 public WebElement createNewStaff() {

		   // Filling the Register Staff form
            WebElement inputFirstName = driver.findElement(By.id("firstName"));
            WebElement inputLastName = driver.findElement(By.id("lastName"));
            WebElement inputPhoneNumber = driver.findElement(By.id("phone"));
            WebElement btnSubmitQuery = driver.findElement(By.xpath("//input[@type='submit']"));
            WebElement selectStaffType = driver.findElement(By.id("type"));
            inputFirstName.sendKeys("F_Agent=1");
            inputLastName.sendKeys("1");
            inputPhoneNumber.sendKeys("0123456789");

            // selecting the Field Agent option
            List<WebElement> options = selectStaffType.findElements(By.tagName("option"));
            for (Integer i=0;i<options.size();i++){
              if (options.get(i).getText().equals("Field agent")){
            		options.get(i).setSelected();
            	}
            }

            btnSubmitQuery.click();

            // Extracting the staff id
            WebElement stringStaffId = driver.findElement(By.xpath("//div[@id='content']/span"));
            return stringStaffId;

	}

    public String extractStaffId(WebElement staffIdString){
         String tempText[] = staffIdString.getText().trim().split("=");
         Integer len = tempText.length;
         String staffId = tempText[len -1];
         System.out.println(staffId);
         return staffId;
    }

	  
}		
	