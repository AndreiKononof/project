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
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class StatisticsServiceImpl implements StatisticsService {

    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private volatile boolean indexingStop;

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
        ExecutorService service = Executors.newScheduledThreadPool(10);

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
            service.submit(() -> {
                threads.add(Thread.currentThread());
                System.out.println(Thread.currentThread() + " - " + site.getUrl());
                indexingSait(site);

            });
        }
        service.shutdown();
        threads.forEach(el -> System.out.println(el.getState() + " " + el.getName()));
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

    @Override
    public DataResponse getSearch(String query, String siteQuery, Integer limit, Integer offset) {
        DataResponse dataResponse = new DataResponse();
        List<Data> dataList = new ArrayList<>();
        dataResponse.setResult(false);
        dataResponse.setCount(0);
        dataResponse.setData(dataList);
        List<Lemma> LemmaSearch = new ArrayList<>();
        SiteDB site = siteRepository.findSiteByUrl(siteQuery).get(0);

        String[] queryWords = query.toLowerCase().split("\\s");
        List<List<Index>> listIndex = new ArrayList<>();

        for (String word : queryWords) {
            List<String> lemmaList = new ArrayList<>();
            try {
                GetLemmaList lemmaQuery = new GetLemmaList();
                lemmaList = lemmaQuery.getListLemmas(word);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            for (String lemma : lemmaList) {
                List<Lemma> lemmaDB = lemmaRepository.findByLemma(lemma);
                List<Index> indexDB = new ArrayList<>();
                if (!lemmaDB.isEmpty()) {
                    indexDB = indexRepository.findByLemma(lemmaDB.get(0));
                    LemmaSearch.add(lemmaDB.get(0));
                }
                List<Index> indexForSearch = new ArrayList<>();
                for (Index index : indexDB) {
                    if (index.getPage().getSite().getUrl().equals(site.getUrl())) {
                        indexForSearch.add(index);
                    }
                }
                listIndex.add(indexForSearch);
            }
        }
        List<Index> indexListSearch = getIndexListForSearch(listIndex, new ArrayList<>(), new ArrayList<>());
        List<Page> pageSearch = new ArrayList<>();
        float maxAbsRelevant = 0;
        if (!indexListSearch.isEmpty()) {
            indexListSearch.forEach(el -> pageSearch.add(el.getPage()));
            for (Page page : pageSearch) {
                float adsRelevant = 0;
                for (Lemma lemma : LemmaSearch) {
                    adsRelevant = adsRelevant + indexRepository.findByPageAndLemma(page, lemma).get().getRank();
                }
                if (maxAbsRelevant < adsRelevant) maxAbsRelevant = adsRelevant;
            }
            dataList = mapToData(pageSearch, LemmaSearch, maxAbsRelevant);
            dataList.sort(Comparator.comparing(Data::getRelevance));
            Collections.reverse(dataList);
            dataResponse.setData(dataList.stream().skip(offset).limit(limit).toList());
            dataResponse.setCount(pageSearch.size());
            dataResponse.setResult(true);
        }
        return dataResponse;
    }

    public DataResponse getSearchAllSite(String query, Integer limit, Integer offset) {
        DataResponse dataResponse = new DataResponse();
        List<Data> dataList = new ArrayList<>();
        dataResponse.setResult(false);
        dataResponse.setCount(0);
        dataResponse.setData(dataList);
        List<Lemma> LemmaSearch = new ArrayList<>();

        String[] queryWords = query.toLowerCase().split("\\s");

        List<List<Index>> listIndex = new ArrayList<>();

        for (String word : queryWords) {
            List<String> lemmaList = new ArrayList<>();
            try {
                GetLemmaList lemmaQuery = new GetLemmaList();
                lemmaList = lemmaQuery.getListLemmas(word);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            for (String lemma : lemmaList) {
                List<Lemma> lemmaDB = lemmaRepository.findByLemma(lemma);
                List<Index> indexDB = new ArrayList<>();
                if (!lemmaDB.isEmpty()) {
                    indexDB = indexRepository.findByLemma(lemmaDB.get(0));
                    LemmaSearch.add(lemmaDB.get(0));

                }
                List<Index> indexForSearch = new ArrayList<>(indexDB);
                listIndex.add(indexForSearch);
            }
        }
        List<Index> indexListSearch = getIndexListForSearch(listIndex, new ArrayList<>(), new ArrayList<>());
        List<Page> pageSearch = new ArrayList<>();
        float maxAbsRelevant = 0;
        if (!indexListSearch.isEmpty()) {
            indexListSearch.forEach(el -> pageSearch.add(el.getPage()));
            for (Page page : pageSearch) {
                float adsRelevant = 0;
                for (Lemma lemma : LemmaSearch) {
                    adsRelevant = adsRelevant + indexRepository.findByPageAndLemma(page, lemma).get().getRank();
                }
                if (maxAbsRelevant < adsRelevant) maxAbsRelevant = adsRelevant;
            }
            dataList = mapToData(pageSearch, LemmaSearch, maxAbsRelevant);
            dataList.sort(Comparator.comparing(Data::getRelevance));
            Collections.reverse(dataList);
            dataResponse.setData(dataList.stream().skip(offset).limit(limit).toList());
            dataResponse.setCount(pageSearch.size());
            dataResponse.setResult(true);
        }
        return dataResponse;
    }

    private void indexingSait(Site site) {
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

    private List<Data> mapToData(List<Page> pageList, List<Lemma> lemmaList, float maxRelevant) {
        List<Data> dataList = new ArrayList<>();
        for (Page page : pageList) {
            Data data = new Data();
            StringBuilder snippet = new StringBuilder();
            String content = page.getContent();
            String title = "";
            float relevant = 0.0F;

            for (Lemma lemma : lemmaList) {
                relevant += indexRepository.findByPageAndLemma(page, lemma).get().getRank();
            }
            try {
                Pattern patternTitle = Pattern.compile("<title[A-Za-z\"=:\\-+\\s]*>[«»A-Za-zА-Яа-я.,?\\-!\\[\\]{}()=;:'\"@#№%\\s0-9/+]+</title>");
                Matcher matcherTitle = patternTitle.matcher(content);
                title = matcherTitle.group().substring(matcherTitle.group().indexOf(">") + 1, matcherTitle.group().indexOf("<", matcherTitle.group().indexOf(">")));
                Pattern pattern = Pattern.compile(">[«»A-Za-zА-Яа-я.,?!\\-\\[\\]{}()=;:'\"@#№%\\s+0-9]+</");
                Matcher matcher = pattern.matcher(content);
                while (matcher.find()) {
                    for (Lemma lemma : lemmaList) {
                        if (matcher.group().toLowerCase().contains(lemma.getLemma())) {
                            if (matcher.group().length() > 200) {
                                int lemmaIndex = matcher.group().toLowerCase().indexOf(lemma.getLemma().toLowerCase());
                                snippet.append("<p>");
                                if (lemmaIndex > 50) {
                                    snippet.append("<p>...").append(matcher.group(), 50, lemmaIndex);
                                } else {
                                    snippet.append(matcher.group(), 1, lemmaIndex);
                                }
                                snippet.append("<b>")
                                        .append(matcher.group(), lemmaIndex, lemmaIndex + lemma.getLemma().length())
                                        .append("</b>");
                                if (matcher.group().length() > lemmaIndex + lemma.getLemma().length() + 150) {
                                    snippet.append(matcher.group(), lemmaIndex + lemma.getLemma().length(), lemmaIndex + lemma.getLemma().length() + 150)
                                            .append("...</p>");
                                } else {
                                    snippet.append(matcher.group(), lemmaIndex + lemma.getLemma().length(), matcher.group().length())
                                            .append("</p>");
                                }
                            } else {
                                int lemmaIndex = matcher.group().toLowerCase().indexOf(lemma.getLemma().toLowerCase());
                                snippet.append("<p>")
                                        .append(matcher.group(), 1, lemmaIndex)
                                        .append("<b>")
                                        .append(matcher.group(), lemmaIndex, lemmaIndex + lemma.getLemma().length())
                                        .append("</b>")
                                        .append(matcher.group(), lemmaIndex + lemma.getLemma().length(), matcher.group().length())
                                        .append("</p>");
                            }
                        }
                    }
                }
            } catch (IllegalStateException ex) {
                ex.printStackTrace();
            }

            String snippetOnSearch = String.valueOf(snippet);
            data.setSite(page.getSite().getUrl());
            data.setSiteName(page.getSite().getName());
            data.setUri(page.getPath());
            data.setTitle(title);
            data.setSnippet(snippetOnSearch);
            data.setRelevance(relevant / maxRelevant);
            dataList.add(data);
        }
        return dataList;
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

    private List<Index> getIndexListForSearch(List<List<Index>> indexList, List<Index> indexListSerch, List<Index> listIndex) {
        if (!indexList.isEmpty()) {
            if (indexListSerch.isEmpty()) {
                indexListSerch = indexList.get(0);
            }
            for (Index index : indexListSerch) {
                long count;
                if (indexList.size() > 1) {
                    count = indexList.get(1).stream().filter(el -> el.getPage().getId() == index.getPage().getId()).count();
                } else {
                    count = indexList.get(0).stream().filter(el -> el.getPage().getId() == index.getPage().getId()).count();
                }
                if (count != 0) {
                    listIndex.add(index);
                }
            }
            indexList.remove(0);
            getIndexListForSearch(indexList, listIndex, new ArrayList<>());
        }
        return listIndex;
    }
}