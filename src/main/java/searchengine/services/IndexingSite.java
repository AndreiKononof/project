package searchengine.services;

import lombok.Getter;
import org.jsoup.select.Elements;
import org.jsoup.nodes.Document;

import java.util.*;

import org.jsoup.Jsoup;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.SiteDB;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;

@Getter
public class IndexingSite {
    private final String url;
    private final SiteDB site;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;

    public IndexingSite(String url, SiteDB site, PageRepository pageRepository, LemmaRepository lemmaRepository) {
        this.site = site;
        this.url = url;
        this.pageRepository = pageRepository;
        this.lemmaRepository = lemmaRepository;
    }

    HashSet<String> links = new HashSet<>();

    public HashSet<String> getLinks() {
        try {

            Document document;
            document = Jsoup.connect(url).userAgent("HelionSearchBot").referrer("http://www.google.com").get();
            if (!pageRepository.findAllPath().contains("/")) {
                Page page = mapToPage(site, 200, "/", document.toString());
                pageRepository.save(page);
            }
            Thread.sleep(100);
            Elements elements = document.select("a[href]");
            if (!elements.isEmpty()) {
                elements.forEach(element -> {
                    String link = element.attr("href");
                    if (!link.isEmpty()) {
                        if (checkLink(link)) {
                            if (link.endsWith("/")) {
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
        savePageLemma();
        return links;
    }

    private void savePageLemma() {
        List<Lemma> newLemmas = new ArrayList<>();
        for (String link : links) {
            List<String> pathDataBase = pageRepository.findAllPath();
            if (!pathDataBase.contains(link)) {
                try {
                    Document document = Jsoup.connect(site.getUrl() + link).get();
                    Page page = mapToPage(site, 200, link, document.toString());
                    synchronized (pageRepository) {
                        pageRepository.save(page);
                    }
                    HashMapLemma hashMapLemma = new HashMapLemma();
                    HashMap<String, Integer> lemmas = hashMapLemma.getMapLemmas(document.toString());
                    Set<String> keyLemmas = lemmas.keySet();

                    for (String key : keyLemmas) {
                        List<String> lemmaWord = lemmaRepository.findAllLemmas();
                        if (!lemmaWord.contains(key)) {
                            newLemmas.add(mapToLemma(site, key));
                        } else {
                            List<Integer> lemmaId = lemmaRepository.findLemma(key);
                            Lemma lemma = lemmaRepository.findById(lemmaId.get(0)).get();
                            lemma.setFrequency(lemma.getFrequency()+1);
                            newLemmas.add(lemma);
                        }
                    }
                    synchronized (lemmaRepository) {
                        lemmaRepository.saveAll(newLemmas);
                    }
                } catch (Exception ignored) {
                }
            }
        }
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

