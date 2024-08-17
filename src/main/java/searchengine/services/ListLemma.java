package searchengine.services;

import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


public class ListLemma {

    LuceneMorphology luceneMorphology = new RussianLuceneMorphology();

    public ListLemma() throws IOException {
    }

    public List<String> getListLemmas(String text){
        List<String> lemmas = new ArrayList<>();
        String regex = "[.,:;]";
        String regexRussLang = "[^а-яА-Я]";
        String[] words = text.toLowerCase().replaceAll(regex, "")
                .replaceAll(regexRussLang," ").split("\s+");

        for (String word : words) {
            if (word.isBlank()) {
                continue;
            }

            if (isParticles(word)) {
                continue;
            }

            lemmas.add(word);
        }
        return lemmas;
    }

    private boolean isParticles(String word){

        List<String> wordsMorph = luceneMorphology.getMorphInfo(word.toLowerCase());
        List<String> particles = new ArrayList<>();
        particles.add("МЕЖД");
        particles.add("СОЮЗ");
        particles.add("ПРЕДЛ");
        String regex = "[^МЕЖДСОЮЗПРЛ]";
        for (String wordMorph : wordsMorph) {
            for (String particle : particles) {
                if (wordMorph.replaceAll(regex,"").toUpperCase().matches(particle)) {
                    return true;
                }
            }
        }
        return false;
    }
}
