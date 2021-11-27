package com.apixandru.scraper;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public abstract class AbstractScraper<E> implements Scraper {

    private static final Logger log = LoggerFactory.getLogger(AbstractScraper.class);

    private final CloseableHttpClient client = HttpClients.createDefault();

    private final String baseUrl;
    private String nextUrl;
    private String content;
    private Document document;

    protected AbstractScraper(String seedUrl) {
        this.baseUrl = seedUrl.substring(0, seedUrl.indexOf("/", 8));
        this.nextUrl = seedUrl;
    }

    public List<E> scrapeAll() {
        List<E> result = new ArrayList<>();
        do {
            content = doScrape();
            document = Jsoup.parse(content);
            nextUrl = getNextPageUrl(document, baseUrl);
            for (Element element : extractElements(document)) {
                E actualElement = extractElement(element, baseUrl);
                if (actualElement != null) {
                    result.add(actualElement);
                }
            }
        } while (nextUrl != null);
        return result;
    }

    protected abstract E extractElement(Element element, String baseUrl);

    protected abstract Elements extractElements(Document document);

    protected abstract String getNextPageUrl(Document document, String baseUrl);

    public String doScrape() {
        log.info("Visiting {}", nextUrl);
        try (CloseableHttpResponse response = client.execute(new HttpGet(nextUrl))) {
            int code = response.getCode();
            if (code != 200) {
                throw new IllegalStateException("Returned code " + code + " for " + nextUrl);
            }
            return EntityUtils.toString(response.getEntity());
        } catch (IOException | ParseException e) {
            throw new IllegalArgumentException("Failed to scrape " + nextUrl);
        }
    }

    @Override
    public void close() throws IOException {
        client.close();
    }

}
