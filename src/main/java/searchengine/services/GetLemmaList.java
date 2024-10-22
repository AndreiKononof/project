package searchengine.services;

import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class GetLemmaList {


    LuceneMorphology luceneMorphology;

    {
        try {
            luceneMorphology = new RussianLuceneMorphology();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    public List<String> getListLemmas(String text){
        List<String> lemmas = new ArrayList<>();
        StringBuilder textInTeg = new StringBuilder();
        List<String> words = new ArrayList<>();
        if(text.contains("<")) {
            Pattern pattern = Pattern.compile(">[«»A-Za-zА-Яа-я.,?!\\-\\[\\]{}()=;:'\"@#№%\\s0-9]+</");
            Matcher matcher = pattern.matcher(text);
            String regex = "[«»A-Za-z.,?!\\-\\[\\]{}()=;:'\"@#№%0-9/]";
            while (matcher.find()) {
                textInTeg.append(matcher.group().substring(1, matcher.group().length() - 2).toLowerCase().replaceAll(regex, " ")).append(" ");
            }
            words = List.of(String.valueOf(textInTeg).split("\\s+"));
        } else {
          words.add(text);
        }
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
