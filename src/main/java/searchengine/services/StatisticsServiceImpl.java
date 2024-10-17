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

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
            "Данная страница находится за пределами сайтов, указанных в конфигурационном файле",
            "Запущена индексация сайта",
            "Запущена индексация страницы"
    };

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
            getStopIndexing();
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
        } catch (Exception ignored) {
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
            } catch (Exception ignored) {
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
                System.out.println(ex);
            }
        }
        return response;
    }

    @Override
    public DataResponse getSearch(String query, String siteQuery) {
        DataResponse dataResponse = new DataResponse();
        List<Data> dataList = new ArrayList<>();
        dataResponse.setResult(false);
        dataResponse.setCount(0);
        dataResponse.setData(dataList);

        GetLemmaList getLemmaList = null;
        SiteDB site = siteRepository.findSiteByUrl(siteQuery).get(0);

        String[] queryWords = query.split("\\s");

        List<List<Index>> listIndex = new ArrayList<>();

        for (String word : queryWords) {
            List<String> lemmaList = new ArrayList<>();
            try {
                lemmaList = getLemmaList.getListLemmas(word);
            } catch (Exception ignored) {
            }
            for (String lemma : lemmaList) {
                List<Lemma> lemmaDB = lemmaRepository.findByLemma(lemma);
                List<Index> indexDB = indexRepository.findByLemma(lemmaDB.get(0));
                List<Index> indexForSearch = new ArrayList<>();
                for (Index index : indexDB) {
                    if (index.getPage().getSite().getUrl().equals(site.getUrl())) {
                        indexForSearch.add(index);
                    }
                }
                listIndex.add(indexForSearch);
            }
        }
        listIndex.sort(Comparator.comparing(List::size));




//        try {
//            site = siteRepository.findSiteByUrl(siteQuery).get(0);
//            GetLemmaList getLemmaList = new GetLemmaList();
//            List<String> lemma = getLemmaList.getListLemmas(query.replaceAll("\\s+", ""));
//            List<Lemma> lemmaOnDB = lemmaRepository.findByLemma(lemma.get(0));
//            List<Index> indexList = indexRepository.findByLemma(lemmaOnDB.get(0));
//            List<Integer> pageListOnSite = pageRepository.findAllIdWhereSite(site);
//            List<Index> indexForQuery = new ArrayList<>();
//
//            for (Index index : indexList) {
//                if (pageListOnSite.contains(index.getPage().getId())) {
//                    indexForQuery.add(index);
//                }
//            }
//
//            indexForQuery.sort(Comparator.comparing(Index::getRank));
//            Collections.reverse(indexForQuery);
//
//            dataResponse.setResult(true);
//            dataResponse.setCount(indexForQuery.size());
//            indexForQuery.forEach(el -> {
//                Data data = mapToData(el);
//                dataList.add(data);
//            });
//        } catch (Exception exception) {
//            System.out.println(exception);
//        }


        return dataResponse;
    }

    private void indexingSait(Site site) {
        long start = System.currentTimeMillis();
        SiteDB siteDB = mapToSaitDB(site.getUrl(), site.getName(), errors[3]);

        if (getCheckInternet(site.getUrl())) {
            siteRepository.saveAndFlush(siteDB);

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

    private synchronized void deleteAllDB() {
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

        StringBuilder snippet = new StringBuilder();
        String content = index.getPage().getContent();
        String title = "";

        try {
            Pattern patternTitle = Pattern.compile("<title[A-Za-z\"=:\\s]*>[«»A-Za-zА-Яа-я.,?\\-!\\[\\]{}()=;:'\"@#№%\\s0-9]+</title>");
            Matcher matcherTitle = patternTitle.matcher(content);
            matcherTitle.find();
            title = matcherTitle.group().substring(matcherTitle.group().indexOf(">") + 1, matcherTitle.group().indexOf("<", matcherTitle.group().indexOf(">")));

            Pattern pattern = Pattern.compile(">[«»A-Za-zА-Яа-я.,?!\\-\\[\\]{}()=;:'\"@#№%\\s0-9]+</");
            Matcher matcher = pattern.matcher(content);
            int i = 0;
            while (matcher.find()) {
                if (matcher.group().toLowerCase().contains(index.getLemma().getLemma().toLowerCase())) {
                    i++;
                    if (i <= 3) {
                        if (matcher.group().length() > 100) {
                            snippet.append("<p><b>").append(matcher.group(), 1, 100).append("....</b></p>");
                        } else {
                            snippet.append("<p><b>").append(matcher.group(), 1, matcher.group().length() - 1).append("</b></p>");
                        }
                        System.out.println(snippet);
                    }
                }
            }
        } catch (IllegalStateException ex) {
            System.out.println(ex);
        }

        String snippetOnSearch = String.valueOf(snippet);
        data.setSite(index.getPage().getSite().getUrl());
        data.setSiteName(index.getPage().getSite().getName());
        data.setUri(index.getPage().getPath());
        data.setTitle(title);
        data.setSnippet(snippetOnSearch);
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
        System.out.println(site.getUrl() + " " + page.getId() + " индексируется");
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
