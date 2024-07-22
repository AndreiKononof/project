package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.statistics.*;
import searchengine.model.Page;
import searchengine.model.SiteDB;
import searchengine.model.StatusSait;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class StatisticsServiceImpl implements StatisticsService {

    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;

    private final Random random = new Random();
    private final SitesList sites;

    @Override
    public StatisticsResponse getStatistics() {
        String[] statuses = {"INDEXED", "FAILED", "INDEXING"};
        String[] errors = {
                "Ошибка индексации: главная страница сайта не доступна",
                "Ошибка индексации: сайт не доступен",
                ""
        };

        TotalStatistics total = new TotalStatistics();
        total.setSites(sites.getSites().size());
        total.setIndexing(true);

        List<DetailedStatisticsItem> detailed = new ArrayList<>();
        List<Site> sitesList = sites.getSites();
        for (int i = 0; i < sitesList.size(); i++) {
            Site site = sitesList.get(i);
            DetailedStatisticsItem item = new DetailedStatisticsItem();
            item.setName(site.getName());
            item.setUrl(site.getUrl());
            int pages = random.nextInt(1_000);
            int lemmas = pages * random.nextInt(1_000);
            item.setPages(pages);
            item.setLemmas(lemmas);
            item.setStatus(statuses[i % 3]);
            item.setError(errors[i % 3]);
            item.setStatusTime(System.currentTimeMillis() -
                    (random.nextInt(10_000)));
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
    public IndexResponse getIndexing() {
        IndexResponse response = new IndexResponse();
        response.setResult(true);
        response.setError("");
        siteRepository.deleteAll();

        List<Site> sitesList = sites.getSites();

        for (int i = 0; i < sitesList.size(); i++) {

            SiteDB siteDB = new SiteDB();
            siteDB.setStatus(StatusSait.INDEXING);
            siteDB.setUrl(sitesList.get(i).getUrl());
            siteDB.setName(sitesList.get(i).getName());
            siteDB.setStatusTime(LocalDateTime.now());
            siteDB.setLastError("");
            siteRepository.save(siteDB);

            HashSet<String> links = new LinksSait(siteDB.getUrl()).getLinks();
            for (String link : links) {
                Page page = new Page();
                try {
                    Document document = Jsoup.connect(siteDB.getUrl() + link).get();
                    page.setSite(siteDB);
                    page.setCode(200);
                    page.setPath(siteDB.getUrl() + link);
                    page.setContent(document.toString());
                    pageRepository.save(page);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
            siteDB.setStatusTime(LocalDateTime.now());
            siteDB.setStatus(StatusSait.INDEXED);
            siteRepository.save(siteDB);
        }
        return response;
    }
}
