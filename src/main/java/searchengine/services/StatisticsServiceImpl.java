package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.statistics.*;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.SiteDB;
import searchengine.model.StatusSait;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

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

    private volatile List<Thread> threads = new ArrayList<>();
    private final SitesList sites;
    private final String[] errors = {
            "Ошибка индексации: главная страница сайта не доступна",
            "Ошибка индексации: сайт не доступен",
            "Отсутствует подключение к интернету",
            "Ок"
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
            List<Page> pageList = pageRepository.findAll();
            int pages = 0;
            for (Page page : pageList) {
                if (page.getSite().getId() == siteDB.getId()) {
                    pages++;
                }
            }
            List<Lemma> lemmaList = lemmaRepository.findAll();
            int lemmas = 0;
            for (Lemma lemma : lemmaList) {
                if (lemma.getSite().getId() == siteDB.getId()) {
                    lemmas++;
                }
            }
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

        deleteAllDB();

        IndexResponse response = new IndexResponse();
        response.setResult(true);
        response.setError(errors[3]);

        if (!threads.isEmpty()) {
            for (Thread thread : threads) {
                thread.interrupt();
            }
        }

        List<Site> sitesList = sites.getSites();
        for (Site site : sitesList) {
            SiteDB siteDB = mapToSaitDB(StatusSait.INDEXING, site.getUrl(), site.getName(), errors[3]);
            if (getCheckInternet(site.getUrl())) {
                new Thread(() -> {
                    System.out.println(Thread.currentThread().getName());
                    threads.add(Thread.currentThread());
                    synchronized (siteRepository) {
                        siteRepository.save(siteDB);
                    }
                    IndexingSite linksSait = new IndexingSite(site.getUrl(), siteDB, pageRepository, lemmaRepository);
                    HashSet<String> checkLinks = new HashSet<>();

                    HashSet<String> links = new ForkJoinPool()
                            .invoke(new RecursiveTaskMapSait(linksSait, checkLinks));
                    System.out.println(Thread.currentThread().getName() + " количество ссылок " + links.size());

                    if (siteRepository.findById(siteDB.getId()).get().getStatus() != StatusSait.FAILED) {
                        siteDB.setStatusTime(LocalDateTime.now());
                        siteDB.setStatus(StatusSait.INDEXED);
                        synchronized (siteRepository) {
                            siteRepository.save(siteDB);
                        }
                    }
                }).start();
            } else {
                siteDB.setLastError(errors[0]);
                siteDB.setStatusTime(LocalDateTime.now());
                siteDB.setStatus(StatusSait.FAILED);
                synchronized (siteRepository) {
                    siteRepository.save(siteDB);
                }
            }
        }
        return response;
    }

    @Override
    public IndexResponse getStopIndexing() {

        System.out.println("Стоп индексация");
        IndexResponse response = new IndexResponse();
        response.setResult(true);
        response.setError("");
        for (Thread thread : threads) {
            thread.interrupt();
            System.out.println(thread.getName());
        }
        threads.clear();
        synchronized (siteRepository) {
            siteRepository.findAll().forEach(el -> {
                el.setStatusTime(LocalDateTime.now());
                el.setStatus(StatusSait.FAILED);
                el.setLastError("Остановлено пользователем");
                siteRepository.save(el);
            });
        }
        System.out.println("Отработал");
        return response;
    }


    private boolean getCheckInternet(String siteUrl) {
        boolean checkInternet = true;
        try {
            URL url = new URL(siteUrl);
            URLConnection connection = url.openConnection();
            connection.connect();
        } catch (Exception ex) {
            checkInternet = false;
            return checkInternet;
        }
        return checkInternet;
    }

    private synchronized void deleteAllDB() {
        siteRepository.deleteAll();
        pageRepository.deleteAll();
        lemmaRepository.deleteAll();
    }

    private Page mapToPage(SiteDB sait, int code, String path, String content) {
        Page page = new Page();
        page.setSite(sait);
        page.setCode(code);
        page.setPath(path);
        page.setContent(content);
        return page;
    }

    private SiteDB mapToSaitDB(StatusSait status, String url, String name, String error) {
        SiteDB site = new SiteDB();
        site.setStatus(status);
        site.setUrl(url);
        site.setName(name);
        site.setStatusTime(LocalDateTime.now());
        site.setLastError(error);
        return site;
    }

    private Lemma mapToLemma(SiteDB site, String lemmaWord) {
        Lemma lemma = new Lemma();
        lemma.setSite(site);
        lemma.setLemma(lemmaWord);
        lemma.setFrequency(1);
        return lemma;
    }
}
