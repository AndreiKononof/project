package searchengine.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import searchengine.dto.statistics.DataResponse;
import searchengine.dto.statistics.IndexResponse;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.services.IndexService;
import searchengine.services.SearchService;
import searchengine.services.StatisticsService;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final StatisticsService statisticsService;
    private final SearchService searchService;
    private final IndexService indexService;

    public ApiController(StatisticsService statisticsService, SearchService searchService, IndexService indexService) {
        this.statisticsService = statisticsService;
        this.searchService = searchService;
        this.indexService = indexService;
    }

    @GetMapping("/statistics")
    public ResponseEntity<StatisticsResponse> statistics() {
        return ResponseEntity.ok(statisticsService.getStatistics());
    }

    @GetMapping("/startIndexing")
    public ResponseEntity<IndexResponse> indexStart() {
        return ResponseEntity.ok(indexService.getStartIndexing());
    }

    @GetMapping("/stopIndexing")
    public ResponseEntity<IndexResponse> indexStop (){
        return ResponseEntity.ok(indexService.getStopIndexing());
    }

    @PostMapping("/indexPage")
    public ResponseEntity<IndexResponse> indexPage (@RequestParam String url){
        return ResponseEntity.ok(indexService.getIndexPageOrSite(url));
    }

    @GetMapping("/search")
    public ResponseEntity<DataResponse> search (@RequestParam String query,
                                                @RequestParam (required = false) String site,
                                                @RequestParam(value = "limit", defaultValue = "20") Integer limit,
                                                @RequestParam(value = "offset", defaultValue = "0") Integer offset){
            if(site == null) {
            return ResponseEntity.ok(searchService.getSearchAllSite(query,limit,offset));
            }
            return ResponseEntity.ok(searchService.getSearch(query,site,limit,offset));
    }
}
