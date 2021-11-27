package com.apixandru.scraper.dell;

import com.apixandru.scraper.AbstractScraper;
import com.apixandru.scraper.generic.DiscountedItem;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class DellOutletHomeScraper extends AbstractScraper<DiscountedItem> {

    protected DellOutletHomeScraper(String seedUrl) {
        super(seedUrl);
    }

    public static void main(String[] args) throws IOException {
        DellOutletHomeScraper scraper = new DellOutletHomeScraper("https://outlet.us.dell.com/ARBOnlineSales/Online/InventorySearch.aspx?brandId=2201&c=us&cs=22&l=en&s=dfh&dgc=IR&cid=258996&lid=4635114&~ck=mn");
        List<DiscountedItem> items = scraper.scrapeAll();
        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(items);
        Files.write(Paths.get("latest.json"), json.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    protected Elements extractElements(Document document) {
        Elements elementsByClass = document.getElementsByClass("fl-config-sec")
                .get(0)
                .children();
        elementsByClass.removeIf(e -> e.childrenSize() == 0);
        elementsByClass.removeIf(e -> !e.tagName().equals("div"));
        return elementsByClass;
    }

    @Override
    protected DiscountedItem extractElement(Element element, String baseUrl) {
        DiscountedItem item = new DiscountedItem();
//        Element titleAndUrl = element.getElementsByClass("facetedResults-cta").get(1);
//        item.setUrl(baseUrl + titleAndUrl.attr("href"));
        item.setTitle(element.getElementsByTag("h4").get(0).getElementsByTag("a").text());
        item.setDiscount(extractPrice(element.getElementsByAttributeValue("itemprop", "youSave")));
        item.setPriceAfterDiscount(extractPrice(element.getElementsByClass("pricingSummary-details-final-price")));
        item.setPriceBeforeDiscount(extractPrice(element.getElementsByClass("fl-small-text fl-strike-through")));

        Elements featureList = element.getElementsByClass("facetedResults-feature-list")
                .get(0)
                .getElementsByTag("dl");

        for (Element featureElement : featureList) {
            setField(featureElement, item);
        }
        return item;
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
        Elements a = document.getElementsByClass("fl-page-button");
        for (Element element : a) {
            if ("Next".equals(element.text())) {
                return element.attr("href");
            }
        }
        return null;
    }

}
