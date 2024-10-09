package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import searchengine.model.Lemma;
import searchengine.model.SiteDB;

import java.util.List;
import java.util.Optional;

@Repository
public interface LemmaRepository extends JpaRepository<Lemma, Integer> {

    @Query (value = "Select lemma From Lemma Where site = :site")
    List<String> findAllLemmas(@Param("site") SiteDB siteDB);

    @Query (value = "SELECT id From Lemma Where lemma LIKE :word and site =:site ")
    Optional<Integer> findIdLemmaWithSite(@Param("word") String lemmaNormForm, @Param("site") SiteDB site);

    @Query (value = "SELECT frequency From Lemma Where lemma LIKE :word and site =:site ")
    Optional<Integer> findFrequencyLemmaWithSite(@Param("word") String lemmaNormForm, @Param("site") SiteDB site);

    @Query (value = "Select lemma From Lemma Where site=:site")
    List<String> findBySite (@Param("site") SiteDB site);

    List<Lemma> findByLemma (String lemma);
}
