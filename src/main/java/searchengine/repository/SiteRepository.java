package searchengine.repository;

import org.springframework.stereotype.Repository;
import searchengine.config.Site;
import org.springframework.data.jpa.repository.JpaRepository;

@Repository
public interface SiteRepository extends JpaRepository<Site,Long>{
}
