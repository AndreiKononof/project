package searchengine.services;

import searchengine.dto.statistics.DataResponse;

public interface SearchService {
    DataResponse getSearch (String query, String site, Integer limit, Integer offset);
    DataResponse getSearchAllSite (String query, Integer limit, Integer offset);
}
