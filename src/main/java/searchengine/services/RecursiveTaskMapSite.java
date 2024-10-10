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
                    RecursiveTaskMapSite taskMapSite = new RecursiveTaskMapSite(new IndexingSite(linkSait.getSiteDB(), url + link, checkLinks, linkSait.getPageRepository()), checkLinks, url);
                    taskMapSite.fork();
                    taskMapSites.add(taskMapSite);
                    checkLinks.add(link);
                }
            }
        }

        for (RecursiveTaskMapSite taskMapSite : taskMapSites){
            links.addAll(taskMapSite.join());
        }
        return links;
    }
}