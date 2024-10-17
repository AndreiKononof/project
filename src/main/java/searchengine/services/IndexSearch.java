package searchengine.services;

import lombok.AllArgsConstructor;
import searchengine.model.Index;

import java.util.ArrayList;
import java.util.List;

@AllArgsConstructor
public class IndexSearch {
    List<Index> indexListOne;
    List<Index> indexListTwo;

    public List<Index> getListIndex(){
        List<Index> listIndex = new ArrayList<>();
        if(indexListOne.size()>indexListTwo.size()){
            for(Index index: indexListTwo){
                long count = indexListOne.stream().filter(el -> el.getPage().getId() == index.getPage().getId()).count();
                if(count!=0){
                    listIndex.add(index);
                }
            }
        }

        return listIndex;
    }



}
