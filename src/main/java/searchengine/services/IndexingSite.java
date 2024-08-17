package searchengine.services;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jsoup.select.Elements;
import org.jsoup.nodes.Document;

import java.util.*;

import org.jsoup.Jsoup;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.SiteDB;

@Getter
public class IndexingSite {
    private final String url;

   private final HashSet<String> links = new HashSet<>();


   public IndexingSite (String url){
       this.url = url;
   }

    public HashSet<String> getLinks() {
            try {
                Document document;
                document = Jsoup.connect(url).userAgent("HelionSearchBot").referrer("http://www.google.com").get();
                Thread.sleep(100);
                Elements elements = document.select("a[href]");
                if (!elements.isEmpty()) {
                    elements.forEach(element -> {
                        String link = element.attr("href");
                        if (!link.isEmpty()) {
                            if (checkLink(link)) {
                                if (link.endsWith("/")) {
                                    links.add("/");
                                    links.add(link.substring(0, link.length() - 1));
                                } else {
                                    links.add(link);
                                }
                            }
                        }
                    });
                }
            } catch (Exception ignored) {
            }
        return links;
    }

    private boolean checkLink(String link) {
        boolean check = false;

        boolean checkChar = link.charAt(0) == '/';
        boolean checkLengthUrl = link.length() > 1;
        boolean checkLinks = !links.contains(link) & !links.contains(link.substring(0, link.length() - 1));
        boolean checkGrid = !link.contains("#");
        boolean checkPicture = !link.contains(".*") || !link.contains("xml");
        if (checkChar & checkLengthUrl & checkGrid & checkPicture & checkLinks) {
            check = true;
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

    private Lemma mapToLemma(SiteDB site, String lemmaWord) {
        Lemma lemma = new Lemma();
        lemma.setSite(site);
        lemma.setLemma(lemmaWord);
        lemma.setFrequency(1);
        return lemma;
    }

}

