package com.apixandru.scraper.lenovo;

import com.apixandru.scraper.AbstractScraper;
import com.apixandru.scraper.generic.DiscountedItem;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import static java.util.stream.Collectors.toList;

public class LenovoOutletScraper extends AbstractScraper<DiscountedItem> {

    private static final Logger log = LoggerFactory.getLogger(LenovoOutletScraper.class);

    protected LenovoOutletScraper(String seedUrl) {
        super(seedUrl);
    }

    public static void main(String[] args) throws IOException {
        LenovoOutletScraper scraper = new LenovoOutletScraper("https://www.lenovo.com/us/en/outletus/laptops/c/LAPTOPS");
//        LenovoOutletScraper scraper = new LenovoOutletScraper("https://www.lenovo.com/us/en/outletus/laptops/c/LAPTOPS");
        List<DiscountedItem> items = scraper.scrapeAll();
        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(items);
        Files.write(Paths.get("latest.json"), json.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    protected Elements extractElements(Document document) {
        return document.getElementsByClass("facetedResults-item");
    }

    @Override
    protected DiscountedItem extractElement(Element element, String baseUrl) {
        DiscountedItem item = new DiscountedItem();
        Element titleAndUrl = element.getElementsByClass("facetedResults-cta").get(1);
        item.setUrl(baseUrl + titleAndUrl.attr("href"));
        item.setTitle(titleAndUrl.text());
        Elements featureList = element.getElementsByClass("facetedResults-feature-list")
                .get(0)
                .getElementsByTag("dl");

        for (Element featureElement : featureList) {
            setField(featureElement, item);
        }

        if (isOutOfStock(element)) {
            log.info("{} - {} is out of stock", item.getTitle(), item.getModel());
            return null;
        }

        item.setDiscount(extractPrice(element.getElementsByAttributeValue("itemprop", "youSave")));
        item.setPriceAfterDiscount(extractPrice(element.getElementsByClass("pricingSummary-details-final-price")));
        item.setPriceBeforeDiscount(extractPrice(element.getElementsByTag("strike")));

        return item;
    }

    private boolean isOutOfStock(Element element) {
//        Elements outStockPro = element.getElementsByClass("outStockPro");
//        return !outStockPro.isEmpty();
        List<String> outOfStockItems = element.getElementsByClass("rci-msg")
                .stream()
                .map(Element::text)
                .filter("Out of Stock"::equals)
                .collect(toList());
        if (outOfStockItems.isEmpty()) {
            return false;
        } else if (outOfStockItems.size() == 1) {
            return true;
        }
        throw new IllegalStateException("Response had " + outOfStockItems);
    }

    private void setField(Element featureElement, DiscountedItem item) {
        String dtName = featureElement.getElementsByTag("dt").text();
        String ddValue = featureElement.getElementsByTag("dd").text();
        switch (dtName) {
            case "Part number:":
                item.setModel(ddValue);
                return;
            case "Processor:":
                item.setCpu(ddValue);
                return;
            case "Hard Drive:":
                item.setStorage(ddValue);
                return;
            case "Memory:":
                item.setRam(ddValue);
                return;
            case "Graphics:":
                item.setGpu(ddValue);
                return;
        }
    }

    private double extractPrice(Elements elementsByClass) {
        if (elementsByClass.size() != 1) {
            throw new IllegalStateException("Expecting unique child: " + elementsByClass.size());
        }
        Element uniqueElement = elementsByClass.get(0);
        String text = uniqueElement.text();
        if (!text.startsWith("$")) {
            throw new IllegalStateException("Bad price " + text);
        }
        String textWithoutDollarSign = text.substring(1);
        String substring = textWithoutDollarSign.replace(",", "");
        return Double.parseDouble(substring);
    }

    @Override
    protected String getNextPageUrl(Document document, String baseUrl) {
        String nextPage = findNextPage(document);
        if (nextPage == null) {
            return null;
        }
        return baseUrl + nextPage;
    }

    protected String findNextPage(Document document) {
        Elements a = document.select("[rel=next]");
        for (Element element : a) {
            if ("Next Page".equals(element.text())) {
                return element.attr("href");
            }
        }
        return null;
    }

}
