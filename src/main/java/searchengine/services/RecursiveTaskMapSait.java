package searchengine.services;


import lombok.AllArgsConstructor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.RecursiveTask;

@AllArgsConstructor
public class RecursiveTaskMapSait extends RecursiveTask<HashSet<String>> {
    LinksSait mapSait;
    HashSet<String> checkLinks;
    String url;

    public RecursiveTaskMapSait(LinksSait mapSait, String url, HashSet<String> checkLinks) {
        this.mapSait = mapSait;
        this.url = url;
        this.checkLinks = checkLinks;
    }

    @Override
    protected HashSet<String> compute() {
        List<RecursiveTaskMapSait> taskList = new ArrayList<>();
        HashSet<String> links = new HashSet<>(mapSait.getLinks());
        for (String link : links) {
            if (!checkLinks.contains(link)) {
                RecursiveTaskMapSait task = new RecursiveTaskMapSait(new LinksSait(url + link),url, checkLinks);
                task.fork();
                taskList.add(task);
                checkLinks.add(link);
            }
        }
        for (RecursiveTaskMapSait task : taskList) {
            links.addAll(task.join());
        }
        return links;
    }
}
