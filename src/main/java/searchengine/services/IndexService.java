package searchengine.services;

import searchengine.dto.statistics.IndexResponse;

public interface IndexService {
    IndexResponse getStartIndexing();
    IndexResponse getStopIndexing();
    IndexResponse getIndexPageOrSite(String url);
}
