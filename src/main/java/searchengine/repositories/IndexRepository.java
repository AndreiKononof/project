package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import searchengine.model.Index;
import searchengine.model.Lemma;
import searchengine.model.Page;

import java.util.List;
import java.util.Optional;


@Repository
public interface IndexRepository extends JpaRepository<Index, Integer> {

    Optional<Index> findByPageAndLemma(Page page, Lemma lemma);

    List<Index> findByPage(Page page);

    List<Index> findByLemma (Lemma lemma);
}
