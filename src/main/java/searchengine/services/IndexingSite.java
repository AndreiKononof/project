package searchengine.services;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.jsoup.nodes.Document;

import java.util.*;

import org.jsoup.Jsoup;
import searchengine.model.Page;
import searchengine.model.SiteDB;
import searchengine.repositories.PageRepository;

@Getter
@RequiredArgsConstructor
public class IndexingSite {
    private final SiteDB siteDB;
    private final String url;
    private final HashSet<String> checkLink;
    private final PageRepository pageRepository;

    private final HashSet<String> links = new HashSet<>();

    public HashSet<String> getLinks() {
        Document document;
        try {
            document = Jsoup.connect(url).userAgent("HelionSearchEngine").referrer("google.com").get();
            int code = Jsoup.connect(url).execute().statusCode();
            String uri = url;
            if (uri.equals(siteDB.getUrl())) {
                uri = "/";
            } else {
                uri = url.substring(siteDB.getUrl().length());
            }
            if (!uri.isEmpty()) {
                Page page = mapToPage(siteDB, code, uri, document.toString());
                pageRepository.save(page);
            }
            Thread.sleep(50);
        } catch (Exception ex) {
            document = null;
        }
        if (document != null) {
            String repeatHref;
            try {
                repeatHref = url.substring(url.indexOf('/', 9));
            } catch (Exception ignored) {
                repeatHref = null;
            }
            Elements elements = document.select("[href]");
            if (!elements.isEmpty()) {
                for (Element element : elements) {
                    String link = element.attr("href");
                    if (!link.isEmpty() & checkLink(link)) {
                        if (repeatHref != null) {
                            if (link.startsWith(repeatHref)) link = link.substring(repeatHref.length());
                        }
                        if (link.endsWith("/")) {
                            links.add(link.substring(0, link.length() - 1));
                        } else {
                            links.add(link);
                        }
                    }
                }
            }
        }
        return links;
    }

    private boolean checkLink(String link) {
        boolean check = true;

        if (link.startsWith("#")) {
            check = false;
        }

        if (link.endsWith("png") || link.endsWith("xml") || link.endsWith("pdf") || link.contains("jpg")) {
            check = false;
        }

        if (links.contains(link)) {
            check = false;
        }
        if (checkLink.contains(link)) {
            check = false;
        }

        if (link.contains("http") || link.contains("www") || link.contains("script") || link.contains("tel") || link.contains("@") || link.contains("svg")) {
            check = false;
        }
        if (link.contains("?") || link.contains("//")) {
            check = false;
        }
        if (link.equals("/")) {
            check = false;
        }

        return check;
    }

    private Page mapToPage(SiteDB sait, int code, String path, String content) {
        Page page = new Page();
        page.setSite(sait);
        page.setCode(code);
        page.setPath(path);
        page.setContent(content);
        return page;
    }
}

