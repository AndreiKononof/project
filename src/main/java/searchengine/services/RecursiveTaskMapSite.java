package searchengine.services;


import lombok.AllArgsConstructor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.RecursiveTask;

@AllArgsConstructor
public class RecursiveTaskMapSite extends RecursiveTask<HashSet<String>> {
    private final String url;
    private final IndexingSite linkSait;
    private final HashSet<String> checkLinks;

    public RecursiveTaskMapSite(IndexingSite linkSait, HashSet<String> checkLinks, String url){
        this.linkSait = linkSait;
        this.checkLinks = checkLinks;
        this.url = url;
    }

    @Override
    protected HashSet<String> compute() {
        List<RecursiveTaskMapSite> taskMapSites = new ArrayList<>();
        HashSet<String> links = linkSait.getLinks();
        if(!links.isEmpty()) {
            for (String link : links) {
                if (!checkLinks.contains(link)) {
                    RecursiveTaskMapSite taskMapSite = new RecursiveTaskMapSite(new IndexingSite(url + link, checkLinks), checkLinks, url);
                    taskMapSite.fork();
                    taskMapSites.add(taskMapSite);
                    checkLinks.add(link);
                }
            }
        }

        for (RecursiveTaskMapSite taskMapSite : taskMapSites){
            links.addAll(taskMapSite.join());
        }
        for (String link : links){
            link.concat("/");
        }
        return links;
    }
}
//    IndexingSite mapSait;
//    HashSet<String> checkLinks;
//    @Override
//    protected HashSet<String> compute() {
//        List<RecursiveTaskMapSait> taskList = new ArrayList<>();
//        HashSet<String> links = new HashSet<>(mapSait.getLinks());
//        for (String link : links) {
//            if (!checkLinks.contains(link)) {
//                RecursiveTaskMapSait task = new RecursiveTaskMapSait(
//                        new IndexingSite(
//                               mapSait.getUrl() + link),
//                        checkLinks);
//                task.fork();
//                taskList.add(task);
//                checkLinks.add(link);
//            }
//        }
//        for (RecursiveTaskMapSait task : taskList) {
//            links.addAll(task.join());
//        }
//        return links;
//    }
//}
