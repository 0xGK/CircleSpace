package com.teame.promo;

import lombok.extern.log4j.Log4j2;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.FluentWait;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.io.*;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

@Log4j2
@Service
public class PromoDataParser {
    private WebDriver driver;
    private String url = "https://everytime.kr/418760";
    private final PromoRepository promoRepository;
    private FluentWait<WebDriver> wait;
    private FluentWait<WebDriver> shortWait;
    private int parsed_cnt;
    private static final String fileName = "cookies.data";

    @Autowired
    public PromoDataParser(PromoRepository promoRepository) {
        this.promoRepository = promoRepository;
    }

    public int runSeleniumTask() {
        parsed_cnt=0;
        try  {
            initializeDriver();

            int maxPage = 10;
            outerLoop:
            for(int page=1; page<=maxPage; page++){
                navigateToClubPage(url+"/p/"+page);
                wait.until(ExpectedConditions.numberOfElementsToBeMoreThan(By.cssSelector("a.article"), 20));

                List<WebElement> articleLinks = driver.findElements(By.cssSelector("a.article"));
                for (WebElement articleLink : articleLinks) {
                    String linkHref = articleLink.getAttribute("href");
                    if (linkHref != null && linkHref.startsWith(url)) {
                        if(!parseArticle(linkHref)){
                            log.info("Reached the most recent data in the database.");
                            break outerLoop;
                        }
                    }
                }
            }


            log.info("Successfully " + parsed_cnt +" parsed data at " +
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        } catch (Exception e) {
            log.info("Error initializing writer: " + e.getMessage());
        }
        finally {
            if (driver != null) {
                driver.quit();
            }
        }
        return parsed_cnt;
    }

    private void initializeDriver() {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless");
        options.addArguments("--disable-blink-features=AutomationControlled");
        options.addArguments("--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/117.0.5938.132 Safari/537.36");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--disable-gpu");
        options.addArguments("--window-size=1920,1080");
        options.addArguments("Accept-Language=en-US,en;q=0.9");
        options.addArguments("Referer=https://everytime.kr");
        driver = new ChromeDriver(options);

        wait = new FluentWait<>(driver)
                .withTimeout(Duration.ofSeconds(30))
                .pollingEvery(Duration.ofMillis(500))
                .ignoring(NoSuchElementException.class);
        shortWait = new FluentWait<>(driver)
                .withTimeout(Duration.ofSeconds(2))
                .pollingEvery(Duration.ofMillis(500))
                .ignoring(NoSuchElementException.class);


        driver.get("https://everytime.kr");

        try{
            loadCookiesFromStream(driver);
        } catch (Exception e) {
            log.info("Error loading cookies: " + e.getMessage());
        }

        driver.get(url);
        try {
            Thread.sleep(2000);
            shortWait.until(webDriver -> webDriver.getCurrentUrl().equals(url));
        } catch (TimeoutException te ) {
            log.info("You need to log in again to refresh the cookies.");
            WebElement idField = wait.until(ExpectedConditions.presenceOfElementLocated(By.name("id")));
            WebElement passwordField = wait.until(ExpectedConditions.presenceOfElementLocated(By.name("password")));
            WebElement submitButton = wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector("input[type='submit']")));

            boolean loginSuccess = false;
            int attemptCount = 0;

            while (!loginSuccess && attemptCount < 2) { // 최대 2번 시도
                try {
                    attemptCount++;
                    idField.clear();
                    idField.sendKeys("");           // 배포 전에 everytime id 입력
                    passwordField.clear();
                    passwordField.sendKeys("");     // 배포 전에 everytime password 입력
                    submitButton.click();

                    shortWait.until(ExpectedConditions.urlToBe(url));
                    loginSuccess = true;

                } catch (Exception ignored) {
                }
            }
            try {
                saveCookies(driver);
            }catch(Exception e){
                log.info("Error saving cookies " + e.getMessage());
            }
        }catch (Exception e){
            log.info("Error trying new login: " +  e.getMessage());
        }
    }

    private void navigateToClubPage(String pageUrl) {
        try {
            driver.get(pageUrl);
            wait.until(ExpectedConditions.presenceOfAllElementsLocatedBy(By.cssSelector("a.article")));
        } catch (Exception e) {
            log.info("Error navigating to club page: " + e.getMessage());
        }
    }

    private boolean parseArticle(String articleUrl) {
        try {
            driver.get(articleUrl);
            List<WebElement> paragraphBody = wait.until(ExpectedConditions.presenceOfAllElementsLocatedBy(By.cssSelector("p.large")));
            List<WebElement> paragraphTitle = wait.until(ExpectedConditions.presenceOfAllElementsLocatedBy(By.cssSelector("h2.large")));
            List<WebElement> paragraphTime = wait.until(ExpectedConditions.presenceOfAllElementsLocatedBy(By.cssSelector("div.profile time.large")));

            if (!paragraphTitle.isEmpty() && !paragraphTime.isEmpty() && !paragraphBody.isEmpty()) {
                String title = paragraphTitle.get(0).getText();
                String dateTime = paragraphTime.get(0).getText();
                int year = LocalDate.now().getYear();
                LocalDateTime postedTime;

                try {
                    if (dateTime.contains("/")) {
                        // 날짜와 시간이 함께 있는 형식 (예: "11/06 08:44")
                        postedTime = LocalDateTime.of(
                                year,
                                Integer.parseInt(dateTime.substring(0, 2)),  // 월
                                Integer.parseInt(dateTime.substring(3, 5)),  // 일
                                Integer.parseInt(dateTime.substring(6, 8)),  // 시
                                Integer.parseInt(dateTime.substring(9, 11))  // 분
                        );
                    } else if (dateTime.contains("분 전")) {
                        // "XX분 전" 형식 처리
                        int minutesAgo = Integer.parseInt(dateTime.replace("분 전", "").trim());
                        postedTime = LocalDateTime.now().minusMinutes(minutesAgo);
                    } else if (dateTime.contains("방금")) {
                        // "방금"인 경우 현재 시간으로 설정
                        postedTime = LocalDateTime.now();
                    } else {
                        // 예상하지 못한 형식인 경우 로그를 남기고 현재 시간으로 처리
                        log.info("Unknown date format: " + dateTime);
                        postedTime = LocalDateTime.now();
                    }
                } catch (Exception e) {
                    log.info("Error parsing dateTime: " + dateTime + ", " + e.getMessage());
                    postedTime = LocalDateTime.now(); // 오류 발생 시 현재 시간으로 처리
                }


                StringBuilder body = new StringBuilder();
                for (WebElement paragraph : paragraphBody) {
                    String filteredText = paragraph.getText();
                    body.append(filteredText).append("\n");
                }
                if (checkInDB(title, postedTime)) {
                    return false;
                }
                if(!checkDuplicate(title, body.toString())){
                    writeArticleDataToDB(title, postedTime, body.toString());
                    parsed_cnt++;
                }
            }

            driver.navigate().back();
        } catch (Exception e) {
            log.info("Error while processing article: " + e.getMessage());
        }
        return true;
    }

    // DB 중복 데이터 여부 확인
    private boolean checkDuplicate(String title, String body){
        return promoRepository.existsByTitleANDBody(title, body);
    }


    private boolean checkInDB(String title, LocalDateTime postedTime){
        int year = postedTime.getYear();
        int month = postedTime.getMonthValue();
        int day = postedTime.getDayOfMonth();
        return promoRepository.existsByTitleAndDate(title, year, month, day);
    }

    private void writeArticleDataToDB(String title, LocalDateTime postedTime, String body) {
        Promo newPromo = new Promo();
        newPromo.setTitle(title);
        newPromo.setPostedAt(postedTime);
        newPromo.setBody(body);

        promoRepository.save(newPromo);
    }

    // 쿠키 저장 메서드
    private static void saveCookies(WebDriver driver) throws IOException {
        File file = new File(System.getProperty("user.dir"), fileName);

        try (BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(file))) {
            Set<Cookie> cookies = driver.manage().getCookies();
            for (Cookie cookie : cookies) {
                bufferedWriter.write(cookie.getName() + ";" + cookie.getValue() + ";" + cookie.getDomain() + ";"
                        + cookie.getPath() + ";" + cookie.getExpiry() + ";" + cookie.isSecure());
                bufferedWriter.newLine();
            }
        }
    }

    private static void loadCookiesFromStream(WebDriver driver) throws IOException {
        File file = new File(System.getProperty("user.dir"), fileName);
        if (!file.exists()) {
            throw new FileNotFoundException("쿠키 파일이 존재하지 않습니다: " + file.getAbsolutePath());
        }
        try (BufferedReader bufferedReader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                String[] cookieDetails = line.split(";");
                Cookie.Builder cookieBuilder = new Cookie.Builder(cookieDetails[0], cookieDetails[1])
                        .domain(cookieDetails[2])
                        .path(cookieDetails[3])
                        .isSecure(Boolean.parseBoolean(cookieDetails[5]));
                driver.manage().addCookie(cookieBuilder.build());
            }
        }
    }
}
