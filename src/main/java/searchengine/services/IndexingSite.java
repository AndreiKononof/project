package searchengine.services;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.jsoup.nodes.Document;

import java.util.*;

import org.jsoup.Jsoup;
@Getter
@RequiredArgsConstructor
public class IndexingSite {
    private final String url;
    private final HashSet<String> checkLink;


    private final HashSet<String> links = new HashSet<>();

    public HashSet<String> getLinks() {
        Document document;
        try {
            document = Jsoup.connect(url).get();
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
                    if (!link.isEmpty()) {
                        if (checkLink(link)) {
                            if (repeatHref != null) {
                                if (link.startsWith(repeatHref)) {
                                    link = link.substring(repeatHref.length());
                                }
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

}
//    private final String url;
//
//   private final HashSet<String> links = new HashSet<>();
//
//
//
//
//    public HashSet<String> getLinks() {
//            try {
//                Document document;
//                document = Jsoup.connect(url).userAgent("HelionSearchBot").referrer("http://www.google.com").get();
//                System.out.println(url + " "+"Добавлена в БД");
//                Elements elements = document.select("a[href]");
//                if (!elements.isEmpty()) {
//                    elements.forEach(element -> {
//                        String link = element.attr("href");
//                        if (!link.isEmpty()) {
//                            if (checkLink(link)) {
//                                if (link.endsWith("/")) {
//                                    links.add("/");
//                                    links.add(link.substring(0, link.length() - 1));
//                                } else {
//                                    links.add(link);
//                                }
//                            }
//                        }
//                    });
//                }
//            } catch (Exception ignored) {
//                System.out.println(url+" " + "Произошла ошибка");
//            }
//        return links;
//    }
//
//    private boolean checkLink(String link) {
//        boolean check = false;
//
//        boolean checkChar = link.charAt(0) == '/';
//        boolean checkLengthUrl = link.length() > 1;
//        boolean checkLinks = !links.contains(link) & !links.contains(link.substring(0, link.length() - 1));
//        boolean checkGrid = !link.contains("#");
//        boolean checkPicture = !link.contains(".*") || !link.contains("xml");
//        if (checkChar & checkLengthUrl & checkGrid & checkPicture & checkLinks ) {
//            check = true;
//        }
//
//        return check;
//    }
//
//}

