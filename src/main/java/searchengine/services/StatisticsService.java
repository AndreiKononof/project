package searchengine.services;

import searchengine.dto.statistics.IndexResponse;
import searchengine.dto.statistics.StatisticsResponse;

public interface StatisticsService {
    StatisticsResponse getStatistics();
    IndexResponse getIndexing();
}
