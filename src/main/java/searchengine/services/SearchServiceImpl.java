package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import searchengine.dto.statistics.Data;
import searchengine.dto.statistics.DataResponse;
import searchengine.model.Index;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.SiteDB;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.SiteRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService{

    private final SiteRepository siteRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;


    public DataResponse getSearch(String query, String siteQuery, Integer limit, Integer offset) {
        DataResponse dataResponse = new DataResponse();
        List<Data> dataList = new ArrayList<>();
        dataResponse.setResult(false);
        dataResponse.setCount(0);
        dataResponse.setData(dataList);
        List<Lemma> LemmaSearch = new ArrayList<>();
        SiteDB site = siteRepository.findSiteByUrl(siteQuery).get(0);

        String[] queryWords = query.toLowerCase().split("\\s");
        List<List<Index>> listIndex = new ArrayList<>();

        for (String word : queryWords) {
            List<String> lemmaList = new ArrayList<>();
            try {
                GetLemmaList lemmaQuery = new GetLemmaList();
                lemmaList = lemmaQuery.getListLemmas(word);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            for (String lemma : lemmaList) {
                List<Lemma> lemmaDB = lemmaRepository.findByLemma(lemma);
                List<Index> indexDB = new ArrayList<>();
                if (!lemmaDB.isEmpty()) {
                    indexDB = indexRepository.findByLemma(lemmaDB.get(0));
                    LemmaSearch.add(lemmaDB.get(0));
                }
                List<Index> indexForSearch = new ArrayList<>();
                for (Index index : indexDB) {
                    if (index.getPage().getSite().getUrl().equals(site.getUrl())) {
                        indexForSearch.add(index);
                    }
                }
                listIndex.add(indexForSearch);
            }
        }
        List<Index> indexListSearch = getIndexListForSearch(listIndex, new ArrayList<>(), new ArrayList<>());
        List<Page> pageSearch = new ArrayList<>();
        float maxAbsRelevant = 0;
        if (!indexListSearch.isEmpty()) {
            indexListSearch.forEach(el -> pageSearch.add(el.getPage()));
            for (Page page : pageSearch) {
                float adsRelevant = 0;
                for (Lemma lemma : LemmaSearch) {
                    adsRelevant = adsRelevant + indexRepository.findByPageAndLemma(page, lemma).get().getRank();
                }
                if (maxAbsRelevant < adsRelevant) maxAbsRelevant = adsRelevant;
            }
            dataList = mapToData(pageSearch, LemmaSearch, maxAbsRelevant);
            dataList.sort(Comparator.comparing(Data::getRelevance));
            Collections.reverse(dataList);
            dataResponse.setData(dataList.stream().skip(offset).limit(limit).toList());
            dataResponse.setCount(pageSearch.size());
            dataResponse.setResult(true);
        }
        return dataResponse;
    }
    @Override
    public DataResponse getSearchAllSite(String query, Integer limit, Integer offset) {
        DataResponse dataResponse = new DataResponse();
        List<Data> dataList = new ArrayList<>();
        dataResponse.setResult(false);
        dataResponse.setCount(0);
        dataResponse.setData(dataList);
        List<Lemma> LemmaSearch = new ArrayList<>();

        String[] queryWords = query.toLowerCase().split("\\s");

        List<List<Index>> listIndex = new ArrayList<>();

        for (String word : queryWords) {
            List<String> lemmaList = new ArrayList<>();
            try {
                GetLemmaList lemmaQuery = new GetLemmaList();
                lemmaList = lemmaQuery.getListLemmas(word);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            for (String lemma : lemmaList) {
                List<Lemma> lemmaDB = lemmaRepository.findByLemma(lemma);
                List<Index> indexDB = new ArrayList<>();
                if (!lemmaDB.isEmpty()) {
                    indexDB = indexRepository.findByLemma(lemmaDB.get(0));
                    LemmaSearch.add(lemmaDB.get(0));

                }
                List<Index> indexForSearch = new ArrayList<>(indexDB);
                listIndex.add(indexForSearch);
            }
        }
        List<Index> indexListSearch = getIndexListForSearch(listIndex, new ArrayList<>(), new ArrayList<>());
        List<Page> pageSearch = new ArrayList<>();
        float maxAbsRelevant = 0;
        if (!indexListSearch.isEmpty()) {
            indexListSearch.forEach(el -> pageSearch.add(el.getPage()));
            for (Page page : pageSearch) {
                float adsRelevant = 0;
                for (Lemma lemma : LemmaSearch) {
                    adsRelevant = adsRelevant + indexRepository.findByPageAndLemma(page, lemma).get().getRank();
                }
                if (maxAbsRelevant < adsRelevant) maxAbsRelevant = adsRelevant;
            }
            dataList = mapToData(pageSearch, LemmaSearch, maxAbsRelevant);
            dataList.sort(Comparator.comparing(Data::getRelevance));
            Collections.reverse(dataList);
            dataResponse.setData(dataList.stream().skip(offset).limit(limit).toList());
            dataResponse.setCount(pageSearch.size());
            dataResponse.setResult(true);
        }
        return dataResponse;
    }

    private List<Index> getIndexListForSearch(List<List<Index>> indexList, List<Index> indexListSerch, List<Index> listIndex) {
        if (!indexList.isEmpty()) {
            if (indexListSerch.isEmpty()) {
                indexListSerch = indexList.get(0);
            }
            for (Index index : indexListSerch) {
                long count;
                if (indexList.size() > 1) {
                    count = indexList.get(1).stream().filter(el -> el.getPage().getId() == index.getPage().getId()).count();
                } else {
                    count = indexList.get(0).stream().filter(el -> el.getPage().getId() == index.getPage().getId()).count();
                }
                if (count != 0) {
                    listIndex.add(index);
                }
            }
            indexList.remove(0);
            getIndexListForSearch(indexList, listIndex, new ArrayList<>());
        }
        return listIndex;
    }

    private List<Data> mapToData(List<Page> pageList, List<Lemma> lemmaList, float maxRelevant) {
        List<Data> dataList = new ArrayList<>();
        for (Page page : pageList) {
            Data data = new Data();
            StringBuilder snippet = new StringBuilder();
            String content = page.getContent();
            String title = "";
            float relevant = 0.0F;

            for (Lemma lemma : lemmaList) {
                relevant += indexRepository.findByPageAndLemma(page, lemma).get().getRank();
            }
            try {
                Pattern patternTitle = Pattern.compile("<title[A-Za-z\"=:\\-+\\s]*>[«»A-Za-zА-Яа-я.,?\\-!\\[\\]{}()=;:'\"@#№%\\s0-9/+]+</title>");
                Matcher matcherTitle = patternTitle.matcher(content);
                title = matcherTitle.group().substring(matcherTitle.group().indexOf(">") + 1, matcherTitle.group().indexOf("<", matcherTitle.group().indexOf(">")));
                Pattern pattern = Pattern.compile(">[«»A-Za-zА-Яа-я.,?!\\-\\[\\]{}()=;:'\"@#№%\\s+0-9]+</");
                Matcher matcher = pattern.matcher(content);
                while (matcher.find()) {
                    for (Lemma lemma : lemmaList) {
                        if (matcher.group().toLowerCase().contains(lemma.getLemma())) {
                            if (matcher.group().length() > 200) {
                                int lemmaIndex = matcher.group().toLowerCase().indexOf(lemma.getLemma().toLowerCase());
                                snippet.append("<p>");
                                if (lemmaIndex > 50) {
                                    snippet.append("<p>...").append(matcher.group(), 50, lemmaIndex);
                                } else {
                                    snippet.append(matcher.group(), 1, lemmaIndex);
                                }
                                snippet.append("<b>")
                                        .append(matcher.group(), lemmaIndex, lemmaIndex + lemma.getLemma().length())
                                        .append("</b>");
                                if (matcher.group().length() > lemmaIndex + lemma.getLemma().length() + 150) {
                                    snippet.append(matcher.group(), lemmaIndex + lemma.getLemma().length(), lemmaIndex + lemma.getLemma().length() + 150)
                                            .append("...</p>");
                                } else {
                                    snippet.append(matcher.group(), lemmaIndex + lemma.getLemma().length(), matcher.group().length())
                                            .append("</p>");
                                }
                            } else {
                                int lemmaIndex = matcher.group().toLowerCase().indexOf(lemma.getLemma().toLowerCase());
                                snippet.append("<p>")
                                        .append(matcher.group(), 1, lemmaIndex)
                                        .append("<b>")
                                        .append(matcher.group(), lemmaIndex, lemmaIndex + lemma.getLemma().length())
                                        .append("</b>")
                                        .append(matcher.group(), lemmaIndex + lemma.getLemma().length(), matcher.group().length())
                                        .append("</p>");
                            }
                        }
                    }
                }
            } catch (IllegalStateException ex) {
                ex.printStackTrace();
            }

            String snippetOnSearch = String.valueOf(snippet);
            data.setSite(page.getSite().getUrl());
            data.setSiteName(page.getSite().getName());
            data.setUri(page.getPath());
            data.setTitle(title);
            data.setSnippet(snippetOnSearch);
            data.setRelevance(relevant / maxRelevant);
            dataList.add(data);
        }
        return dataList;
    }

}
