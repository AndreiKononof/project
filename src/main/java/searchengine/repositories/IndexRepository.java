package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import searchengine.model.Index;

public interface IndexRepository extends JpaRepository<Index, Integer> {
}
