package searchengine.services;

import org.jsoup.select.Elements;
import org.jsoup.nodes.Document;
import java.util.HashSet;
import org.jsoup.Jsoup;


public class LinksSait {

    private String url;

    public LinksSait(String url) {
        this.url = url;
    }

    HashSet<String> links = new HashSet<>();
    public HashSet<String> getLinks() {
        Document document;
        try {
            document = Jsoup.connect(url).get();
            Thread.sleep(100);
            Elements elements = document.select("a[href]");
            if (!elements.isEmpty()) {
                elements.forEach(element -> {
                    String link = element.attr("href");
                    if (!link.isEmpty()) {

                        boolean checkChar = link.charAt(0) == '/';
                        boolean checkLengthUrl = link.length() > 1;
                        boolean checkLinks = !links.contains(link) & !links.contains(link.substring(0, link.length() - 1));
                        boolean checkGrid = !link.contains("#");
                        boolean checkPicture = !link.contains(".png");

                        if (checkChar & checkLengthUrl & checkGrid & checkPicture & checkLinks) {
                            if (link.endsWith("/")) {
                                links.add(link.substring(0, link.length() - 1));
                            } else {
                                links.add(link);
                            }
                        }
                    }
                });
            }
        } catch (Exception e) {
            e.getStackTrace();
        }
        return links;
    }

}

