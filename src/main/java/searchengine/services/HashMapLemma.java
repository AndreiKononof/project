package searchengine.services;

import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;


public class HashMapLemma {


    public HashMapLemma(){
    }

    public HashMap<String, Integer> getMapLemmas(String text) throws IOException {
        LuceneMorphology luceneMorphology = new RussianLuceneMorphology();
        HashMap<String, Integer> lemmas = new HashMap<>();
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

            List<String> lemmasWord = luceneMorphology.getNormalForms(word.toLowerCase());
            String normalForm = lemmasWord.get(0);

            if (lemmas.containsKey(normalForm)) {
                lemmas.put(normalForm, lemmas.get(normalForm) + 1);
            } else {
                lemmas.put(normalForm, 1);
            }
        }
        return lemmas;
    }

    private boolean isParticles(String word) throws IOException {
        LuceneMorphology luceneMorphology = new RussianLuceneMorphology();
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
