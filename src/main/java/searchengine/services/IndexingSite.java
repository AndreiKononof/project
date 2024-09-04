package searchengine.services;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jsoup.select.Elements;
import org.jsoup.nodes.Document;

import java.io.IOException;
import java.util.*;

import org.jsoup.Jsoup;
import searchengine.model.Index;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.SiteDB;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;

@Getter
@RequiredArgsConstructor
public class IndexingSite {
    private final String url;
    private final SiteDB siteDB;
    private final PageRepository pageRepository;
    private final IndexRepository indexRepository;
    private final LemmaRepository lemmaRepository;


   private final HashSet<String> links = new HashSet<>();




    public HashSet<String> getLinks() {
            try {
                Document document;
                document = Jsoup.connect(url).userAgent("HelionSearchBot").referrer("http://www.google.com").get();
                Thread.sleep(100);
                String path = "/";
                if (!url.substring(siteDB.getUrl().length()).isEmpty()){
                    path = url.substring(siteDB.getUrl().length());
                }
                Page page = mapToPage(siteDB,200,path,document.text());
                synchronized (pageRepository){
                    pageRepository.save(page);
                }
                saveLemma(siteDB,document);
                saveIndex(siteDB,page,document);
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

    private Index mapToIndex(Page page, Lemma lemma) {

        Index index = new Index();
        index.setPage(page);
        index.setLemma(lemma);
        index.setRank(1);
        return index;
    }

    private synchronized void saveIndex(SiteDB site, Page page, Document document) throws IOException {
        List<Lemma> lemmaList = getLemmaListOnThePage(site, document);
        for (Lemma lemma : lemmaList) {
            Lemma lemmaDB = lemmaRepository.findByLemma(lemma.getLemma()).get(0);
            Page pageDB = pageRepository.findById(page.getId()).get();
            if (indexRepository.findByPageAndLemma(pageDB, lemmaDB).isPresent()) {
                Index index = indexRepository.findByPageAndLemma(pageDB, lemmaDB).get();
                index.setRank(index.getRank() + 1);
                indexRepository.save(index);
            } else {
                Index index = mapToIndex(pageDB, lemmaDB);
                indexRepository.save(index);
            }
        }
    }

    private List<Lemma> getLemmaListOnThePage(SiteDB site, Document document) throws IOException {
        List<Lemma> lemmaList = new ArrayList<>();
        ListLemma listLemma = new ListLemma();
        List<String> lemmaWordList = listLemma.getListLemmas(document.toString());
        for (String lemmaWord : lemmaWordList) {
            Lemma lemma = mapToLemma(site, lemmaWord);
            lemmaList.add(lemma);
        }
        return lemmaList;
    }

    private synchronized void saveLemma(SiteDB site, Document document) throws IOException {
        List<Lemma> lemmaList = getLemmaListOnThePage(site, document);
        List<Lemma> cashLemma = new ArrayList<>();
        List<Lemma> lemmaOnPage = new ArrayList<>();
        Set<String> lemmaWords = new HashSet<>();
        for (Lemma lemma : lemmaList) {
            if (!lemmaWords.contains(lemma.getLemma())) {
                lemmaOnPage.add(lemma);
                lemmaWords.add(lemma.getLemma());
            }
        }
        for (Lemma lemma : lemmaOnPage) {
            Optional<Integer> idLemma = lemmaRepository.findIdLemmaWithSite(lemma.getLemma(), site);
            if (idLemma.isPresent()) {
                Optional<Lemma> lemmaInBD = lemmaRepository.findById(idLemma.get());
                lemmaInBD.get().setFrequency(lemmaInBD.get().getFrequency() + 1);
                cashLemma.add(lemmaInBD.get());
            } else {
                cashLemma.add(lemma);
            }
        }
        lemmaRepository.saveAll(cashLemma);
    }
}

