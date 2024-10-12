package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.statistics.*;
import searchengine.model.*;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

import javax.swing.text.html.HTMLDocument;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.ForkJoinPool;

@Service
@RequiredArgsConstructor
public class StatisticsServiceImpl implements StatisticsService {

    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private boolean indexingStop;

    private final SitesList sites;
    private final List<Thread> threads;
    private final String[] errors = {
            "Ошибка индексации: главная страница сайта не доступна",
            "Ошибка индексации: сайт не доступен",
            "Отсутствует подключение к интернету",
            "Ок",
            "Индексация не закончена",
            "Данная страница находится за пределами сайтов," +
                    "указанных в конфигурационном файле",
            "Запущена индексация сайта",
            "Запущена индексация страницы"
    };
    private static final String TITLE_START = "<title>";
    private static final String TITLE_END = "</title";

    @Override
    public StatisticsResponse getStatistics() {
        ZoneOffset zoneOffset = ZoneOffset.ofHours(3);
        TotalStatistics total = new TotalStatistics();
        total.setSites((int) siteRepository.count());
        total.setIndexing(true);

        List<DetailedStatisticsItem> detailed = new ArrayList<>();

        List<SiteDB> sitesList = siteRepository.findAll();
        for (SiteDB siteDB : sitesList) {
            DetailedStatisticsItem item = new DetailedStatisticsItem();
            item.setName(siteDB.getName());
            item.setUrl(siteDB.getUrl());
            List<String> pageList = pageRepository.findAllPath(siteDB);
            int pages = pageList.size();
            List<String> lemmaList = lemmaRepository.findAllLemmas(siteDB);
            int lemmas = lemmaList.size();
            item.setPages(pages);
            item.setLemmas(lemmas);
            item.setStatus(siteDB.getStatus().toString());
            item.setError(siteDB.getLastError());
            item.setStatusTime(siteDB.getStatusTime().toInstant(zoneOffset).toEpochMilli());
            total.setPages(total.getPages() + pages);
            total.setLemmas(total.getLemmas() + lemmas);
            detailed.add(item);
        }

        StatisticsResponse response = new StatisticsResponse();
        StatisticsData data = new StatisticsData();
        data.setTotal(total);
        data.setDetailed(detailed);
        response.setStatistics(data);
        response.setResult(true);
        return response;
    }

    @Override
    public IndexResponse getStartIndexing() {
        indexingStop = false;
        IndexResponse response = new IndexResponse();
        response.setResult(true);

        if (getIndexingNow()) {
            response.setResult(false);
            response.setError(errors[4]);
            return response;
        }

        deleteAllDB();

        List<Site> sitesList = sites.getSites();
        for (Site site : sitesList) {
            if (indexingStop) {
                break;
            }
            new Thread(() -> {
                threads.add(Thread.currentThread());
                indexingSait(site);
            }).start();
        }
        return response;
    }

    @Override
    public IndexResponse getStopIndexing() {
        IndexResponse response = new IndexResponse();
        response.setResult(true);

        indexingStop = true;
        for (Thread thread : threads) {
            thread.interrupt();
        }
        threads.clear();

        synchronized (siteRepository) {
            siteRepository.findAll().forEach(el -> {
                el.setLastError("Остановлено пользователем");
                el.setStatus(StatusSait.FAILED);
                el.setStatusTime(LocalDateTime.now());
                siteRepository.save(el);
            });
        }

        return response;
    }

    @Override
    public IndexResponse getIndexSait(String url) {
        url = url.toLowerCase();
        url = url.replaceAll("\s", "");
        String siteUrl;
        String http = "http://";
        String urlHttp = "";
        String urlHttps = "";
        String pageString;
        String https = "https://";
        List<String> urlList = new ArrayList<>();
        sites.getSites().forEach(el -> urlList.add(el.getUrl()));
        IndexResponse response = new IndexResponse();
        response.setResult(false);
        response.setError(errors[5]);

        boolean siteContains = false;
        for (String urlSite : urlList) {
            if (urlSite.equals(url)) {
                siteContains = true;
                break;
            }
        }
        System.out.println(siteContains);

        if (siteContains) {
            List<Integer> siteId = siteRepository.findByUrl(url);
            if (!siteId.isEmpty()) {
                try {
                    SiteDB siteDB = siteRepository.findById(siteId.get(0)).get();
                    Site site = new Site();
                    site.setUrl(siteDB.getUrl());
                    site.setName(siteDB.getName());
                    siteRepository.deleteById(siteId.get(0));
                    indexingSait(site);
                } catch (Exception ignored) {
                }
            } else {
                String name = "";
                Site site = new Site();
                site.setUrl(url);
                site.setName(name);
                List<Site> siteList = sites.getSites();
                for (Site siteConfig : siteList) {
                    if (siteConfig.getUrl().equals(site.getUrl())) {
                        site.setName(siteConfig.getName());
                    }
                }
                indexingSait(site);
            }
            response.setResult(true);
            response.setError(errors[6]);
            return response;
        }

        try {
            pageString = url.substring(url.indexOf("/", 9));
            siteUrl = url.substring(0, url.indexOf("/", 9));
        } catch (Exception e) {
            pageString = null;
            siteUrl = url;
        }

        if (!url.startsWith("http")) {
            urlHttp = http.concat(siteUrl);
            urlHttps = https.concat(siteUrl);
        }

        if (!siteUrl.startsWith("http")) {
            if (urlList.contains(urlHttp)) {
                siteUrl = urlHttp;
            } else if (urlList.contains(urlHttps)) {
                siteUrl = urlHttps;
            } else {
                return response;
            }
        } else if (!urlList.contains(siteUrl)) {
            return response;
        }
        System.out.println(siteUrl);
        System.out.println(pageString);

        List<Integer> siteId = siteRepository.findByUrl(siteUrl);
        SiteDB siteDB;
        try {
            siteDB = siteRepository.findById(siteId.get(0)).get();
            System.out.println(siteDB.getName());
            Site site = new Site();
            site.setUrl(siteDB.getUrl());
            site.setName(siteDB.getName());

            if (pageString == null) {
                System.out.println("нет Страницы");
                siteRepository.deleteById(siteId.get(0));
                indexingSait(site);
                response.setError(errors[6]);
                response.setResult(true);
            } else {
                System.out.println("Со страницей");
                Document doc = Jsoup.connect(siteUrl + pageString)
                        .userAgent("HelionSearchEngine").referrer("google.com").get();
                Page page = mapToPage(siteDB, pageString, doc.toString());
                pageRepository.save(page);
                saveLemmaAndIndex(siteDB, page);
                response.setResult(true);
                response.setError(errors[7]);
            }
        } catch (Exception ignored) {
        }
        return response;
    }

    @Override
    public DataResponse getSearch(String query, String site) {
        DataResponse dataResponse = new DataResponse();
        dataResponse.setResult(false);
        dataResponse.setCount(0);

        if (!query.isEmpty()) {
            SiteDB siteDB = siteRepository.findSiteByUrl(site).get(0);
            List<String> lemmaList = new ArrayList<>();
            try {
                ListLemma lemma = new ListLemma();
                lemmaList = lemma.getListLemmas(query);
            } catch (Exception ignored) {
            }

            List<Lemma> lemmaInDB = new ArrayList<>();
            Integer countPage = pageRepository.findCountPageOnSite(siteDB);
            for (String lemma : lemmaList) {
                try {
                    lemmaInDB.add(lemmaRepository.findByLemma(lemma).get(0));
                } catch (Exception ignored) {
                    return dataResponse;
                }
            }

            lemmaInDB.sort(Comparator.comparing(Lemma::getFrequency));

            boolean deleteFrequency = true;
            if (!lemmaInDB.isEmpty()) {
                while (deleteFrequency) {
                    int index = lemmaInDB.size() - 1;
                    int frequency = lemmaInDB.get(index).getFrequency();

                    if (frequency > countPage) {
                        lemmaInDB.remove(index);
                    } else {
                        deleteFrequency = false;
                    }
                }
            }

            List<Index> indexList = new ArrayList<>();
            List<Integer> pageListOnSite = pageRepository.findAllIdWhereSite(siteDB);
            for (Lemma lemma : lemmaInDB) {
                indexList.addAll(indexRepository.findByLemma(lemma));
            }

            List<Index> indexForSearch = new ArrayList<>();
            if (!indexList.isEmpty()) {
                for (Index index : indexList) {
                    if (pageListOnSite.contains(index.getPage().getId())) {
                        indexForSearch.add(index);
                    }
                }
            }
            List<Data> dataList = new ArrayList<>();
            if (!indexForSearch.isEmpty()) {
                for (Index index : indexForSearch) {
                    Data data = mapToData(index);
                    dataList.add(data);
                }
                dataResponse.setResult(true);
                dataResponse.setCount(dataList.size());
                dataResponse.setData(dataList);
            }

        }
        return dataResponse;
    }

    private void indexingSait(Site site) {
        long start = System.currentTimeMillis();
        SiteDB siteDB = mapToSaitDB(site.getUrl(), site.getName(), errors[3]);

        if (getCheckInternet(site.getUrl())) {
            synchronized (siteRepository) {
                siteRepository.saveAndFlush(siteDB);
            }
            HashSet<String> checkLinks = new HashSet<>();
            new ForkJoinPool().invoke(new RecursiveTaskMapSite(new IndexingSite(siteDB, siteDB.getUrl(), checkLinks, pageRepository), checkLinks, siteDB.getUrl()));
            List<Integer> pageListId = pageRepository.findAllIdWhereSite(siteDB);
            for (Integer pageId : pageListId) {
                if (indexingStop) {
                    break;
                }
                try {
                    Page page = pageRepository.findById(pageId).get();
                    saveLemmaAndIndex(siteDB, page);
                } catch (Exception ex) {
                    System.out.println("При сохранение лемм и индексации\n"+ex);
                }
            }
        } else {
            siteDB.setLastError(errors[2]);
            siteDB.setStatusTime(LocalDateTime.now());
            siteDB.setStatus(StatusSait.FAILED);
            synchronized (siteRepository) {
                siteRepository.save(siteDB);
            }
        }
        if (siteRepository.findById(siteDB.getId()).get().getStatus().equals(StatusSait.INDEXING)) {
            siteDB.setStatusTime(LocalDateTime.now());
            siteDB.setStatus(StatusSait.INDEXED);
            synchronized (siteRepository) {
                siteRepository.saveAndFlush(siteDB);
            }
        }
        System.out.println("Индексация сайта " + siteDB.getName() + " завершенна за - " + (System.currentTimeMillis() - start));
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

    private Data mapToData(Index index) {
        Data data = new Data();
        String snippetStart = "<p><b>";
        String snippetEnd = "</p></b>";

        String query = index.getLemma().getLemma();
        String content = index.getPage().getContent();
        int startIndex = content.indexOf(TITLE_START) + TITLE_START.length();
        int endIndex = content.indexOf(TITLE_END);
        String title = content.substring(startIndex, endIndex);
        String text = content.substring(content.indexOf(query), content.indexOf(query) + query.length());
        String regex = "[a-zA-Z]";
        String regexOne = "[<>]";
        String snippet = snippetStart
                .concat(text.replaceAll(regex, "").replaceAll(regexOne, "").replaceAll("\s+", " "))
                .concat(snippetEnd);
        data.setSite(index.getPage().getSite().getUrl());
        data.setSiteName(index.getPage().getSite().getName());
        data.setUri(index.getPage().getPath());
        data.setTitle(title);
        data.setSnippet(snippet);
        data.setRelevance(20.0);

        return data;
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

    private Page mapToPage(SiteDB sait, String path, String content) {
        Page page = new Page();
        page.setSite(sait);
        page.setCode(200);
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
        ListLemma listLemma = new ListLemma();
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

        for (Lemma lemma : lemmaList) {
            if (!lemmaWords.contains(lemma.getLemma())) {
                lemmaOnPage.add(lemma);
                lemmaWords.add(lemma.getLemma());
            }
        }
        long start = System.currentTimeMillis();
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
        System.out.println("Создание леммы или модернизация "+(System.currentTimeMillis()-start));
        long startSave = System.currentTimeMillis();
        System.out.println(cashLemma.size());
        lemmaRepository.saveAll(cashLemma);
        System.out.println("Сохранение лемм "+(System.currentTimeMillis()-startSave));


        List<Index> indexList = new ArrayList<>();
        List<String> indexLemma = new ArrayList<>();
        long startIndex = System.currentTimeMillis();
        for (Lemma lemma : lemmaList) {
            Lemma lemmaDB = lemmaRepository.findByLemma(lemma.getLemma()).get(0);
            Index index = mapToIndex(page, lemmaDB);
            if (indexLemma.contains(index.getLemma().getLemma())) {
                indexList.forEach(el -> {
                    if (el.getLemma().getLemma().equals(index.getLemma().getLemma())) {
                        el.setRank(el.getRank() + 1);
                    }
                });
            } else {
                indexList.add(index);
                indexLemma.add(index.getLemma().getLemma());
            }
        }
        System.out.println("Создание индекса или модернизация "+(System.currentTimeMillis()-startIndex));
        long startSaveIndex = System.currentTimeMillis();
        System.out.println(indexLemma.size());
        indexRepository.saveAll(indexList);
        System.out.println("Сохранение индекса "+(System.currentTimeMillis()-startSaveIndex)+"\n");
    }
}
