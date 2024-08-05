package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import searchengine.model.Lemma;

import java.util.List;

@Repository
public interface LemmaRepository extends JpaRepository<Lemma, Integer> {

    @Query ("Select lemma From Lemma")
    List<String> findAllLemmas();
    @Query ("Select id FROM Lemma WHERE lemma like %:word% ")
    List<Integer> findLemma(@Param("word") String word);
}
