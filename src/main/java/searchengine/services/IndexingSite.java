package searchengine.services;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import java.util.HashSet;

@Getter
@RequiredArgsConstructor
public class IndexingSite {
    private final String url;

    private final HashSet<String> links = new HashSet<>();

    public HashSet<String> getLinks() {
        try {
            Document document;
            document = Jsoup.connect(url).userAgent("HelionSearchBot").referrer("http://www.google.com").get();
            Thread.sleep(50);
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
        boolean checkPicture = !link.contains(".*") || !link.contains("xml")
                || !link.contains("jpg") || !link.contains("png");
        if (checkChar & checkLengthUrl & checkGrid & checkPicture & checkLinks) {
            check = true;
        }

        return check;
    }
}
