package searchengine.services;

import lombok.AllArgsConstructor;
import searchengine.model.Index;

import java.util.List;
import java.util.concurrent.RecursiveTask;

@AllArgsConstructor
public class RecursiveTaskFindPage extends RecursiveTask<List<Index>> {
    List<List<Index>> indexList;
    IndexSearch indexSearch;

    @Override
    protected List<Index> compute() {
        indexSearch = new IndexSearch(indexList.get(0),indexList.get(1));
        List<Index> indexListSearch = indexSearch.getListIndex();
        if(!indexListSearch.isEmpty()){

        }


        return List.of();
    }
}
