package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import searchengine.model.SiteDB;

public interface SiteRepository extends JpaRepository<SiteDB, Integer> {
}
