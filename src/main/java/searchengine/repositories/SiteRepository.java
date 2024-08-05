package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import searchengine.model.SiteDB;

@Repository
public interface SiteRepository extends JpaRepository<SiteDB, Integer> {
}
