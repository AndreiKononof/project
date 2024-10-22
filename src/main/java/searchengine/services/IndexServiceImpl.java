package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.statistics.IndexResponse;
import searchengine.model.*;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@RequiredArgsConstructor
public class IndexServiceImpl implements IndexService{

    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final ExecutorService service = Executors.newFixedThreadPool(10);
    private static final AtomicBoolean indexingStop = new AtomicBoolean();

    private final SitesList sites;
    private final String[] errors = {
            "Ошибка индексации: главная страница сайта не доступна",
            "Ошибка индексации: сайт не доступен",
            "Отсутствует подключение к интернету",
            "Ок",
            "Индексация не закончена",
            "Данная страница находится за пределами сайтов, указанных в конфигурационном файле",
            "Запущена индексация сайта",
            "Запущена индексация страницы",
            "Остановлено пользователем"
    };


    @Override
    public IndexResponse getStartIndexing() {
        indexingStop.set(false);
        IndexResponse response = new IndexResponse();
        response.setResult(true);

        if (getIndexingNow()) {
            response.setResult(false);
            response.setError(errors[4]);
            getStopIndexing();
            return response;
        }

        deleteAllDB();

        List<Site> sitesList = sites.getSites();
        for (Site site : sitesList) {
            if (indexingStop.get()) {
                break;
            }
            service.submit(() -> indexingSait(site));
        }
        service.shutdown();
        return response;
    }

    @Override
    public IndexResponse getStopIndexing() {
        IndexResponse response = new IndexResponse();
        response.setResult(true);

        service.shutdownNow();
        indexingStop.set(true);

        synchronized (siteRepository) {
            siteRepository.findAll().forEach(el -> {
                el.setLastError(errors[8]);
                el.setStatus(StatusSait.FAILED);
                el.setStatusTime(LocalDateTime.now());
                siteRepository.save(el);
            });
        }

        return response;
    }

    @Override
    public IndexResponse getIndexPageOrSite(String url) {
        IndexResponse response = new IndexResponse();
        response.setResult(false);
        response.setError(errors[5]);

        url = url.toLowerCase();
        url = url.replaceAll("\\s+", "");

        String siteUrl;
        String http = "https?://";
        String pageUri;
        Site site = new Site();
        SiteDB siteDB = new SiteDB();
        int slash;

        try {
            slash = url.indexOf("/", http.length());
            siteUrl = url.substring(0, slash);
            pageUri = url.substring(slash);
        } catch (Exception e) {
            siteUrl = url;
            pageUri = "";
        }

        boolean siteContains = false;
        for (Site urlSite : sites.getSites()) {
            if (urlSite.getUrl().equals(siteUrl)) {
                siteContains = true;
                break;
            }
        }
        if (!siteContains) {
            return response;
        }

        List<Integer> siteId = siteRepository.findByUrl(siteUrl);
        if (!siteId.isEmpty()) {
            try {
                siteDB = siteRepository.findById(siteId.get(0)).get();
                site.setUrl(siteDB.getUrl());
                site.setName(siteDB.getName());
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        } else {
            site.setUrl(siteUrl);
            List<Site> siteList = sites.getSites();
            for (Site siteConfig : siteList) {
                if (siteConfig.getUrl().equals(site.getUrl())) {
                    site.setName(siteConfig.getName());
                }
            }
            siteDB = mapToSaitDB(site.getUrl(), site.getName(), errors[3]);
            siteRepository.save(siteDB);
        }
        if (pageUri.isEmpty()) {
            if (!siteId.isEmpty()) {
                siteRepository.deleteById(siteId.get(0));
                indexingSait(site);
            } else {
                indexingSait(site);
            }
            response.setResult(true);
            response.setError(errors[6]);
            return response;
        } else {
            try {
                Optional<Integer> pageOnDB = pageRepository.findIdPage(pageUri, siteDB);
                pageOnDB.ifPresent(pageRepository::deleteById);

                Document doc = Jsoup.connect(url).userAgent("HelionSearchEngine").referrer("google.com").get();
                int code = Jsoup.connect(url).execute().statusCode();

                Page page = mapToPage(siteDB, code, pageUri, doc.toString());
                pageRepository.save(page);

                if (page.getCode() < 299 & page.getCode() >= 200) {
                    saveLemmaAndIndex(siteDB, page);
                    response.setResult(true);
                    response.setError(errors[7]);
                    return response;
                }

            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
        return response;
    }

    private void indexingSait(Site site) {
        SiteDB siteDB = mapToSaitDB(site.getUrl(), site.getName(), errors[3]);

        if (getCheckInternet(site.getUrl())) {
            siteRepository.saveAndFlush(siteDB);

            HashSet<String> checkLinks = new HashSet<>();
            new ForkJoinPool().invoke(new RecursiveTaskMapSite(new IndexingSite(siteDB, siteDB.getUrl(), checkLinks, pageRepository), checkLinks, siteDB.getUrl()));

            List<Integer> pageListId = pageRepository.findAllIdWhereSite(siteDB);
            for (Integer pageId : pageListId) {
                if (indexingStop.get()) {
                    break;
                }
                try {
                    Page page = pageRepository.findById(pageId).get();
                    if (!(page.getCode() > 299)) {
                        saveLemmaAndIndex(siteDB, page);
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        } else {
            siteDB.setLastError(errors[2]);
            siteDB.setStatusTime(LocalDateTime.now());
            siteDB.setStatus(StatusSait.FAILED);
            siteRepository.save(siteDB);
        }
        if (siteRepository.findById(siteDB.getId()).get().getStatus().equals(StatusSait.INDEXING)) {
            siteDB.setStatusTime(LocalDateTime.now());
            siteDB.setStatus(StatusSait.INDEXED);
            siteRepository.saveAndFlush(siteDB);

        }
    }

    private boolean getCheckInternet(String siteUrl) {
        boolean checkInternet = true;
        try {
            URL url = new URL(siteUrl);
            URLConnection connection = url.openConnection();
            connection.connect();
        } catch (Exception ex) {
            checkInternet = false;
        }
        return checkInternet;
    }

    private void deleteAllDB() {
        siteRepository.deleteAll();
        pageRepository.deleteAll();
        lemmaRepository.deleteAll();
        indexRepository.deleteAll();
    }

    private boolean getIndexingNow() {
        boolean indexingNow = false;
        List<SiteDB> siteDBS = siteRepository.findAll();
        for (SiteDB site : siteDBS) {
            if (site.getStatus().equals(StatusSait.INDEXING)) {
                indexingNow = true;
                break;
            }
        }
        return indexingNow;
    }

    private SiteDB mapToSaitDB(String url, String name, String error) {
        SiteDB site = new SiteDB();
        site.setStatus(StatusSait.INDEXING);
        site.setUrl(url);
        site.setName(name);
        site.setStatusTime(LocalDateTime.now());
        site.setLastError(error);
        return site;
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

    private List<Lemma> getLemmaListOnThePage(SiteDB site, Page page) throws IOException {
        List<Lemma> lemmaList = new ArrayList<>();
        GetLemmaList listLemma = new GetLemmaList();
        List<String> lemmaWordList = listLemma.getListLemmas(page.getContent());
        for (String lemmaWord : lemmaWordList) {
            Lemma lemma = mapToLemma(site, lemmaWord);
            lemmaList.add(lemma);
        }
        return lemmaList;
    }

    private void saveLemmaAndIndex(SiteDB site, Page page) throws IOException {
        List<Lemma> lemmaList = getLemmaListOnThePage(site, page);
        List<Lemma> cashLemma = new ArrayList<>();
        List<Lemma> lemmaOnPage = new ArrayList<>();
        Set<String> lemmaWords = new HashSet<>();
        List<String> lemmaWordsFull = new ArrayList<>();

        for (Lemma lemma : lemmaList) {
            if (!lemmaWords.contains(lemma.getLemma())) {
                lemmaOnPage.add(lemma);
                lemmaWords.add(lemma.getLemma());
            }
            lemmaWordsFull.add(lemma.getLemma());
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


        List<Index> indexList = new ArrayList<>();
        for (Lemma lemma : lemmaOnPage) {
            Lemma lemmaDB = lemmaRepository.findByLemma(lemma.getLemma()).get(0);
            Index index = mapToIndex(page, lemmaDB);
            index.setRank(lemmaWordsFull.stream().filter(el -> el.equals(lemma.getLemma())).count());
            indexList.add(index);
        }
        indexRepository.saveAll(indexList);
    }

}
