package searchengine.controllers;

import jakarta.persistence.criteria.CriteriaBuilder;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import searchengine.dto.statistics.IndexResponse;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.services.StatisticsService;

import java.util.List;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final StatisticsService statisticsService;

    public ApiController(StatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    @GetMapping("/statistics")
    public ResponseEntity<StatisticsResponse> statistics() {
        return ResponseEntity.ok(statisticsService.getStatistics());
    }

    @GetMapping("/startIndexing")
    public ResponseEntity<IndexResponse> indexStart() {
        return ResponseEntity.ok(statisticsService.getStartIndexing());
    }

    @GetMapping("/stopIndexing")
    public ResponseEntity<IndexResponse> indexStop (){
        return ResponseEntity.ok(statisticsService.getStopIndexing());
    }

    @PostMapping("/indexPage")
    public ResponseEntity<IndexResponse> indexPage (@RequestParam String url){
        return ResponseEntity.ok(statisticsService.getIndexSait(url));
    }
}
