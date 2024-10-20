package searchengine.services;

import searchengine.dto.statistics.DataResponse;
import searchengine.dto.statistics.IndexResponse;
import searchengine.dto.statistics.StatisticsResponse;

public interface StatisticsService {
    StatisticsResponse getStatistics();
    IndexResponse getStartIndexing();
    IndexResponse getStopIndexing();
    IndexResponse getIndexPageOrSite(String url);
    DataResponse getSearch (String query, String site, Integer limit, Integer offset);
    DataResponse getSearchAllSite (String query, Integer limit, Integer offset);
}
